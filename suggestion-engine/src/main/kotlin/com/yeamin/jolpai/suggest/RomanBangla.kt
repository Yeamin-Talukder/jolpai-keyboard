package com.yeamin.jolpai.suggest

/**
 * Recognises Bangla written in English letters ("ami tomake onek bhalobashi", "kemon acho"),
 * which is how much everyday chat in Bangladesh is typed. Without this, the English checker
 * and autocorrect treat every such word as a typo (tomake → tamale, kemon → lemon,
 * kintu → king).
 *
 * A word reads as Roman Bangla when it isn't a common English word and the Avro dictionary
 * search turns it into a common Bangla word (frequency ≥ [STRONG] on the list's 0–255 scale):
 * tomake → তোমাকে (158), kemon → কেমন (130), bhalobashi → ভালোবাসি. Real English typos either
 * reach nothing (recieved, definately) or only a rare loanword spelling (adress → অ্যাড্রেস 35,
 * takl → ট্যাকল 0). Measured on 2,792 random slips of the 3,000 most common English words,
 * 39 (1.4 %) reached a common Bangla word, nearly all two or three letters long (se → সে,
 * hy → হয়); callers therefore ask for a longer word or a second Roman Bangla word nearby.
 */
class RomanBangla(
    private val english: () -> EnglishDictionary?,
    private val bangla: () -> BanglaDictionary?,
    private val avro: () -> AvroSuggester?,
) {
    private val cache = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 512
    }

    /** True once the dictionaries it needs are loaded. */
    val ready: Boolean get() = english() != null && bangla() != null && avro() != null

    /**
     * How common (0–255) the most common Bangla word is that the Roman spelling [word] stands
     * for, or -1 when it stands for none (or the dictionaries aren't loaded yet).
     */
    fun banglaFrequency(word: String): Int {
        val key = word.lowercase()
        if (key.length < 2 || !key.all { it in 'a'..'z' }) return -1
        synchronized(cache) { cache[key] }?.let { return it }
        val search = avro() ?: return -1
        val dict = bangla() ?: return -1
        val f = search.matches(key).maxOfOrNull { dict.frequency(it) } ?: -1
        synchronized(cache) { cache[key] = f }
        return f
    }

    /**
     * [word] reads as a common Bangla word in English letters and is not a common English word
     * (rare English words such as ami, bari, jodi still count: they are names or loanwords in
     * the English list). A word whose fix only adds an apostrophe (shes → she's, dont → don't)
     * is English.
     */
    fun isRomanBangla(word: String): Boolean {
        if (word.length < 2 || !word.all { it in 'a'..'z' || it in 'A'..'Z' }) return false
        val dict = english() ?: return false
        val entry = dict.lookup(word)
        if (entry != null && entry.frequency >= RARE_ENGLISH) return false
        if (banglaFrequency(word) < STRONG) return false
        return entry != null || apostropheForm(dict, word) == null
    }

    /** [word] with an apostrophe put in, if that makes an English word (dont → don't), else null. */
    private fun apostropheForm(dict: EnglishDictionary, word: String): String? {
        for (back in 1..3) {
            val at = word.length - back
            if (at < 1) break
            val w = word.substring(0, at) + "'" + word.substring(at)
            if (dict.contains(w)) return w
        }
        return null
    }

    companion object {
        /** A Bangla word this common (seen about 180 times in the subtitle counts behind the list) counts as meant. */
        const val STRONG = 90

        /** English words at least this common are English, whatever they spell in Avro. */
        const val RARE_ENGLISH = 100

        /** A lone Roman Bangla word (no other one in its sentence) needs at least this many letters. */
        const val MIN_LONE_LENGTH = 4
    }
}
