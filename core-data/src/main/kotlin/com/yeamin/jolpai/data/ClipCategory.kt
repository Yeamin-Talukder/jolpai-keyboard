package com.yeamin.jolpai.data

/**
 * Automatic clipboard categories, worked out from the text itself (nothing is sent anywhere).
 * Order matters: the first match wins.
 */
enum class ClipCategory(val label: String) {
    IMAGE("Images"),
    LINK("Links"),
    EMAIL("Emails"),
    OTP("Codes & OTPs"),
    PHONE("Phone numbers"),
    ADDRESS("Addresses"),
    CODE("Code"),
    TEXT("Text"),
    ;

    companion object {
        fun of(clip: ClipEntity): ClipCategory = if (clip.isImage) IMAGE else classify(clip.text.orEmpty())

        private val URL = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
        private val DOMAIN = Regex("""^[\w-]+(\.[\w-]+)*\.[a-z]{2,24}(/\S*)?$""", RegexOption.IGNORE_CASE)
        private val MAIL = Regex("""^[\w.+-]+@[\w-]+(\.[\w-]+)+$""")
        private val PHONE_CHARS = Regex("""^\+?[\d০-৯\s().-]+$""")
        private val CODE_START = Regex(
            // Keywords need a word boundary after them; symbols don't. (Android's regex engine rejects
            // an optional "\b?", which the PC's accepts, so the two kinds are kept apart.)
            """^\s*(?:(?:fun|def|class|import|package|const|let|var|val|function|return|public|private|select|insert|update)\b|#include|<\?|<!doctype|<html|\{|\[)""",
            RegexOption.IGNORE_CASE,
        )
        private val ADDRESS_WORDS = listOf(
            "road", "rd", "street", "st", "avenue", "ave", "lane", "house", "flat", "apt", "block", "sector", "floor",
            "village", "upazila", "district", "dhaka", "chattogram", "chittagong", "sylhet", "khulna", "rajshahi",
            "barishal", "rangpur", "mymensingh", "bangladesh", "zip", "postcode", "p.o",
            "রোড", "সড়ক", "বাসা", "বাড়ি", "ফ্ল্যাট", "ব্লক", "সেক্টর", "গ্রাম", "থানা", "উপজেলা", "জেলা", "ঢাকা", "পোস্ট",
        )

        fun classify(raw: String): ClipCategory {
            val text = raw.trim()
            if (text.isEmpty()) return TEXT
            val oneToken = text.none { it.isWhitespace() }
            if (oneToken && (URL.matches(text) || (DOMAIN.matches(text) && !text.all { it.isDigit() || it == '.' }))) return LINK
            if (oneToken && MAIL.matches(text)) return EMAIL
            val digits = text.count { it.isDigit() }
            if (text.all { it.isDigit() } && digits in 4..8) return OTP
            if (PHONE_CHARS.matches(text) && digits in 7..15) return PHONE
            if (looksLikeCode(text)) return CODE
            if (looksLikeAddress(text)) return ADDRESS
            return TEXT
        }

        private fun looksLikeCode(text: String): Boolean {
            val symbols = text.count { it in "{}();=<>[]" }
            val lines = text.lines()
            if (CODE_START.containsMatchIn(lines.first()) && symbols >= 2) return true
            val codeLines = lines.count { l -> val t = l.trimEnd(); t.endsWith(";") || t.endsWith("{") || t.endsWith("}") || "=>" in t || "</" in t }
            return symbols >= 4 && codeLines >= 1 && (lines.size > 1 || symbols * 8 >= text.length)
        }

        private fun looksLikeAddress(text: String): Boolean {
            if (text.length > 300 || text.none { it.isDigit() }) return false
            val words = text.lowercase().split(Regex("""[\s,/#:-]+""")).map { it.trimEnd('.') }.toSet()
            val hits = ADDRESS_WORDS.count { it in words }
            val separated = ',' in text || '\n' in text || '،' in text
            return hits >= 2 || (hits == 1 && separated)
        }
    }
}

/** What a snippet is for; chosen when saving (suggested from the text), used to group and filter. */
enum class SnippetCategory(val storageKey: String, val label: String) {
    MESSAGE("message", "Messages"),
    ISLAMIC("islamic", "Islamic"),
    REPLY("reply", "Quick replies"),
    SIGNATURE("signature", "Signatures"),
    ADDRESS("address", "Addresses"),
    PHONE("phone", "Phone numbers"),
    EMAIL("email", "Emails"),
    CODE("code", "Code"),
    OTHER("other", "Other"),
    ;

    companion object {
        fun fromStorageKey(key: String?): SnippetCategory = entries.firstOrNull { it.storageKey == key } ?: OTHER

        /** Greetings and phrases that make a snippet "Islamic" (Bangla and English spellings). */
        private val ISLAMIC_WORDS = listOf(
            "আসসালামু", "আলাইকুম", "আলহামদুলিল্লাহ", "ইনশাআল্লাহ", "মাশাআল্লাহ", "সুবহানাল্লাহ", "বিসমিল্লাহ",
            "আস্তাগফিরুল্লাহ", "জাযাকাল্লাহ", "ইন্না লিল্লাহ", "ঈদ মোবারক", "রমজান মোবারক", "জুমা মোবারক",
            "assalam", "alaikum", "alhamdulillah", "inshaallah", "insha allah", "mashaallah", "masha allah",
            "subhanallah", "bismillah", "astaghfirullah", "jazakallah", "eid mubarak", "ramadan mubarak", "jumma mubarak",
        )

        /** A sensible category for new snippet text. */
        fun suggest(text: String): SnippetCategory {
            val t = text.trim()
            return when (ClipCategory.classify(t)) {
                ClipCategory.PHONE -> PHONE
                ClipCategory.EMAIL -> EMAIL
                ClipCategory.ADDRESS -> ADDRESS
                ClipCategory.CODE -> CODE
                else -> when {
                    ISLAMIC_WORDS.any { t.contains(it, ignoreCase = true) } -> ISLAMIC
                    t.lines().size >= 2 && (t.contains('@') || t.count { it.isDigit() } >= 7) && t.length < 400 -> SIGNATURE
                    t.length <= 60 && t.lines().size == 1 -> REPLY
                    else -> MESSAGE
                }
            }
        }
    }
}
