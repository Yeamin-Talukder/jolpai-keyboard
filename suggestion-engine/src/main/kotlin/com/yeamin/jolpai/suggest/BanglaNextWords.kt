package com.yeamin.jolpai.suggest

/**
 * Ranked Bangla next words, counted from freely licensed Bangla text (see bn/NOTICE.txt and
 * tools/prepare-bangla-nextwords.pl): for a previous word (bn/bangla-nextwords.tsv) or the two
 * previous words (bn/bangla-trigrams.tsv), the words that most often come next, with a weight
 * 1–255 (255 = the most likely after that context). `<s>` is the start of a sentence.
 *
 * File lines: `context<TAB>word:weight word:weight …`, best first; lines starting with `#` are
 * comments. The files stay as UTF-8 bytes in memory, searched by binary search over sorted line
 * offsets, so a few MB of data cost about their own size (no strings per entry).
 */
class BanglaNextWords private constructor(private val pairs: Lines, private val triples: Lines?) {

    /** The words most likely after [previous] (null or blank: a sentence start), best first. */
    fun after(previous: String?, limit: Int = 6): List<Pair<String, Int>> =
        pairs.find(if (previous.isNullOrBlank()) START else normalize(previous), limit)

    /** The words most likely after [previous2] [previous]; empty when that context isn't listed. */
    fun after(previous2: String, previous: String, limit: Int = 3): List<Pair<String, Int>> =
        triples?.find(normalize(previous2) + " " + normalize(previous), limit) ?: emptyList()

    val contexts: Int get() = pairs.size

    /** One TSV file kept as bytes; [starts]/[tabs]/[ends] are byte offsets of lines sorted by context. */
    private class Lines(private val bytes: ByteArray) {
        private val starts: IntArray
        private val tabs: IntArray
        private val ends: IntArray

        val size: Int get() = starts.size

        init {
            val s = ArrayList<Int>(); val t = ArrayList<Int>(); val e = ArrayList<Int>()
            var i = 0
            while (i < bytes.size) {
                var end = i
                while (end < bytes.size && bytes[end] != '\n'.code.toByte()) end++
                var lineEnd = end
                if (lineEnd > i && bytes[lineEnd - 1] == '\r'.code.toByte()) lineEnd--
                if (lineEnd > i && bytes[i] != '#'.code.toByte()) {
                    var tab = i
                    while (tab < lineEnd && bytes[tab] != '\t'.code.toByte()) tab++
                    if (tab in (i + 1) until lineEnd) { s += i; t += tab; e += lineEnd }
                }
                i = end + 1
            }
            val order = s.indices.sortedWith { a, b -> compare(s[a], t[a], s[b], t[b]) }
            starts = IntArray(order.size) { s[order[it]] }
            tabs = IntArray(order.size) { t[order[it]] }
            ends = IntArray(order.size) { e[order[it]] }
        }

        fun find(context: String, limit: Int): List<Pair<String, Int>> {
            val key = context.toByteArray(Charsets.UTF_8)
            var lo = 0
            var hi = starts.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val c = compareKey(starts[mid], tabs[mid], key)
                when {
                    c < 0 -> lo = mid + 1
                    c > 0 -> hi = mid - 1
                    else -> return parse(tabs[mid] + 1, ends[mid], limit)
                }
            }
            return emptyList()
        }

        private fun parse(from: Int, to: Int, limit: Int): List<Pair<String, Int>> {
            val out = ArrayList<Pair<String, Int>>(limit)
            for (item in String(bytes, from, to - from, Charsets.UTF_8).split(' ')) {
                if (out.size >= limit) break
                val colon = item.lastIndexOf(':')
                if (colon <= 0) continue
                val w = item.substring(colon + 1).toIntOrNull() ?: continue
                val word = item.substring(0, colon)
                if (NEVER_OFFERED.any { word.startsWith(it) }) continue
                out += word to w.coerceIn(1, 255)
            }
            return out
        }

        private fun compare(aFrom: Int, aTo: Int, bFrom: Int, bTo: Int): Int {
            val n = minOf(aTo - aFrom, bTo - bFrom)
            for (k in 0 until n) {
                val d = (bytes[aFrom + k].toInt() and 0xFF) - (bytes[bFrom + k].toInt() and 0xFF)
                if (d != 0) return d
            }
            return (aTo - aFrom) - (bTo - bFrom)
        }

        private fun compareKey(from: Int, to: Int, key: ByteArray): Int {
            val n = minOf(to - from, key.size)
            for (k in 0 until n) {
                val d = (bytes[from + k].toInt() and 0xFF) - (key[k].toInt() and 0xFF)
                if (d != 0) return d
            }
            return (to - from) - key.size
        }
    }

    companion object {
        const val START = "<s>"

        /**
         * Killing and rape words are common in the news text the lists were counted from
         * ("আমি তোমাকে" → হত্যা), but a keyboard shouldn't offer them unasked. Words starting with
         * these are skipped (they still come from the user's own typing).
         */
        private val NEVER_OFFERED = listOf("হত্যা", "খুন", "ধর্ষ", "জবাই")

        /** Loads the bundled lists, or null when they aren't in the app. Call off the main thread. */
        fun load(): BanglaNextWords? {
            val pairs = resource("/bn/bangla-nextwords.tsv") ?: return null
            return BanglaNextWords(Lines(pairs), resource("/bn/bangla-trigrams.tsv")?.let { Lines(it) })
        }

        /** From in-memory text (tests). */
        fun of(pairs: String, triples: String? = null): BanglaNextWords =
            BanglaNextWords(Lines(pairs.toByteArray(Charsets.UTF_8)), triples?.let { Lines(it.toByteArray(Charsets.UTF_8)) })

        private fun resource(path: String): ByteArray? =
            BanglaNextWords::class.java.getResourceAsStream(path)?.use { it.readBytes() }

        /** As the lists were built: precomposed য় ড় ঢ়, no zero-width joiners. */
        fun normalize(word: String): String {
            if (word.none { it == '়' || it == '‌' || it == '‍' }) return word
            return word.replace("য়", "য়").replace("ড়", "ড়").replace("ঢ়", "ঢ়")
                .replace("‌", "").replace("‍", "")
        }
    }
}
