package com.yeamin.jolpai.lmtrainer

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import com.yeamin.jolpai.suggest.AvroDictionarySearch
import com.yeamin.jolpai.suggest.AvroSuggester
import com.yeamin.jolpai.suggest.BanglaDictionary
import com.yeamin.jolpai.suggest.BanglaNextWords
import com.yeamin.jolpai.suggest.EnglishDictionary
import java.io.File
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Builds the app's Banglish word list (bn/banglish-words.tsv, read by BanglishWords) from
 * BanglaTLit (MIT): BanglaTLit-PT.txt (246k romanized Bangla comments and posts) and
 * BanglaTLit_train.csv (42.9k of them written out in Bangla). The val and test sentences are
 * left out of the counts, so they stay a fair test.
 *
 *   banglish TLIT_DIR OUT.tsv [--min-count 3]
 *
 * Each spelling seen at least --min-count times is kept unless it is a common English word
 * (the English list offers those). Its Bangla word comes from the paired sentences when they
 * agree word for word, else from the Avro dictionary search (the most common Bangla word it can
 * spell). Its frequency is on the English list's scale, 259 + 12.3·ln(share of all words), so
 * the keyboard can rank both lists together.
 */
object Banglish {
    fun build(dir: File, out: File, minCount: Int) {
        val held = HashSet<String>()
        for (name in listOf("BanglaTLiT_val.csv", "BanglaTLiT_test.csv")) readCsv(File(dir, name)).forEach { held += it[1].trim() }

        val counts = HashMap<String, Int>()
        var total = 0L
        File(dir, "BanglaTLit-PT.txt").forEachLine { line ->
            if (line.trim() in held) return@forEachLine
            for (w in latinWords(line)) { counts.merge(w, 1, Int::plus); total++ }
        }

        // Word-for-word pairs: spelling → Bangla word → count.
        val pairs = HashMap<String, HashMap<String, Int>>()
        var aligned = 0
        readCsv(File(dir, "BanglaTLit_train.csv")).forEach { row ->
            val roman = row[1].trim().split(Regex("\\s+"))
            val bangla = row.getOrNull(2)?.trim()?.split(Regex("\\s+")) ?: return@forEach
            if (roman.size != bangla.size || roman.size < 2 || row[1].trim() in held) return@forEach
            aligned++
            for (k in roman.indices) {
                val r = cleanLatin(roman[k]) ?: continue
                val b = cleanBangla(bangla[k]) ?: continue
                pairs.getOrPut(r) { HashMap() }.merge(b, 1, Int::plus)
            }
        }

        val english = EnglishDictionary.load()
        val bangla = BanglaDictionary.load()
        val avro = AvroSuggester.load(PhoneticEngine(AvroGrammar.load()), AvroDictionarySearch.load(bangla), bangla::frequency)

        var englishSkipped = 0
        var unmapped = 0
        val rows = ArrayList<String>()
        for ((w, c) in counts.entries.sortedByDescending { it.value }) {
            if (c < minCount) break
            val en = english.lookup(w)
            if (en != null && en.frequency >= COMMON_ENGLISH) { englishSkipped++; continue }
            if (w in WEB_JUNK) continue
            val paired = pairs[w]?.maxByOrNull { it.value }
            val pairedTotal = pairs[w]?.values?.sum() ?: 0
            val bn = when {
                paired != null && paired.value >= 2 && paired.value * 10 >= pairedTotal * 3 -> paired.key
                else -> avro.matches(w).maxByOrNull { bangla.frequency(it) }?.takeIf { bangla.frequency(it) >= AVRO_MIN_FREQUENCY }
            }
            // Unmapped spellings are kept only when common (shorthand such as "tnx"; typos and names are rare).
            if (bn == null) { if (c < UNMAPPED_MIN_COUNT) continue; unmapped++ }
            val f = (259 + 12.3 * ln(c.toDouble() / total)).roundToInt().coerceIn(1, 255)
            val flags = if (isOffensive(w, bn)) "o" else ""
            rows += "$w\t$f\t${bn ?: ""}\t$flags"
        }
        out.parentFile?.mkdirs()
        out.writeText(
            "# Banglish spellings from BanglaTLit (MIT, Fahim et al. 2024): spelling, frequency (0-255), Bangla word, flags (o = offensive).\n" +
                "# Built by lm-trainer banglish; see BanglishWords.kt.\n" + rows.joinToString("\n", postfix = "\n"),
        )
        println("${counts.size} spellings in $total words ($aligned paired sentences); kept ${rows.size} " +
            "(${rows.count { it.endsWith("\to") }} offensive, $unmapped without a Bangla word), $englishSkipped common English left out → $out")
    }

    /** Lower-case a–z words of a line; tokens with digits or inner punctuation (indicator.apk) are left out. */
    private fun latinWords(line: String): List<String> = line.split(Regex("\\s+")).mapNotNull(::cleanLatin)

    private fun cleanLatin(token: String): String? {
        val t = token.trim { !it.isLetterOrDigit() }.lowercase()
        return t.takeIf { it.isNotEmpty() && it.all { c -> c in 'a'..'z' } }
    }

    private fun cleanBangla(token: String): String? {
        val t = token.trim { it !in 'ঀ'..'৿' }
        if (t.isEmpty() || !t.all { it in 'ঀ'..'৿' || it == '‌' || it == '‍' }) return null
        return BanglaNextWords.normalize(t)
    }

    /** Swear words, by their Bangla (any form) or by Banglish spelling; never suggested unless the user turns blocking off. */
    private fun isOffensive(roman: String, bangla: String?): Boolean {
        if (bangla != null) {
            val b = bangla.replace("চোদ্দ", "") // fourteen
            if (BANGLA_PARTS.any { it in b }) return true
            if (BANGLA_STARTS.any { b.startsWith(it) }) return true
        }
        val r = roman.replace("kichud", "").replace("kisud", "").replace("choddo", "") // kichudin (a few days), choddo (fourteen)
        if (ROMAN_PARTS.any { it in r }) return true
        return ROMAN_STARTS.any { roman.startsWith(it) } || roman in ROMAN_WORDS
    }

    private val BANGLA_PARTS = listOf("চোদ", "চুদ", "চোঁদ", "খানকি", "খানকী", "হারামজাদ", "হারামখোর", "বেশ্যা", "ভোদা", "লেওড়া", "লেওরা", "গান্ডু", "বাইনচোদ", "মাদারচোদ")
    private val BANGLA_STARTS = listOf("মাগি", "মাগী", "বালের", "বালছাল", "চুত", "পোঁদ", "ধোন", "হারামি", "হারামী", "শুয়োরের", "কুত্তার")
    private val ROMAN_PARTS = listOf("chod", "chud", "khanki", "haramjad", "haramzad", "haramkhor", "beshya", "bessa", "leora", "gandu", "bainchod", "madarchod")
    private val ROMAN_STARTS = listOf("magir", "maggir", "baler", "balchal", "vuda", "voda", "bhoda", "chutmar", "kuttar", "shuorer", "suorer", "shuar")
    private val ROMAN_WORDS = setOf("magi", "maggi", "bal", "baal", "dhon", "pod", "haramzada", "haramjada", "harami", "chut", "fokinni", "fokinnir")

    /** Leftovers of web pages in the comments (&quot;, &amp;, URLs). */
    private val WEB_JUNK = setOf("quot", "amp", "nbsp", "gt", "lt", "http", "https", "www", "com")

    /** English words this common (on the list's 0–255 scale) are left to the English list. */
    private const val COMMON_ENGLISH = 100

    /** An Avro reading counts as the word's Bangla only for a Bangla word at least this common. */
    private const val AVRO_MIN_FREQUENCY = 60

    /** A spelling with no Bangla word is kept from this many uses. */
    private const val UNMAPPED_MIN_COUNT = 15

    /** A minimal CSV reader (quoted fields with "" escapes and newlines inside quotes); skips the header. */
    private fun readCsv(file: File): List<List<String>> {
        val text = file.readText()
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row += field.toString(); field.setLength(0) }
                '\n' -> { row += field.toString(); field.setLength(0); rows += row; row = ArrayList() }
                '\r' -> {}
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows.drop(1)
    }
}
