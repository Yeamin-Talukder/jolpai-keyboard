package com.yeamin.jolpai.suggest

import java.util.BitSet

/** A dictionary word with its frequency (0–255, from the AOSP list). */
data class DictWord(val word: String, val frequency: Int)

/** A possible correction for a typed word: [cost] is the [TypingDistance], lower is closer. */
data class Correction(val word: String, val frequency: Int, val cost: Float)

/**
 * The English word list (AOSP LatinIME, Apache-2.0): ~160k words with frequencies.
 *
 * Words are indexed by their lower-cased form so lookups ignore case; the dictionary's own
 * spelling ("I", "iPhone", "Monday") is kept for display.
 */
class EnglishDictionary private constructor(
    private val keys: SortedWords,
    /** The dictionary spelling when it differs from the lower-cased key, else null. */
    private val forms: Array<String?>,
    private val frequencies: ByteArray,
    private val offensive: BitSet,
    /** Common misspellings and contractions from the AOSP list, e.g. "alot" → "a lot". */
    val shortcuts: Map<String, String>,
) {
    val size: Int get() = keys.size

    private fun formAt(i: Int): String = forms[i] ?: keys.wordAt(i)
    private fun frequencyAt(i: Int): Int = frequencies[i].toInt() and 0xFF

    /** Index of the most frequent entry spelled [word] (ignoring case), or -1. */
    private fun indexOf(word: String): Int {
        val key = word.lowercase()
        val i = keys.lowerBound(key)
        return if (i < keys.size && keys.equalsAt(i, key)) i else -1
    }

    fun contains(word: String): Boolean = indexOf(word) >= 0

    /** The dictionary's spelling and frequency of [word], if it is a known word. */
    fun lookup(word: String): DictWord? {
        val i = indexOf(word)
        return if (i < 0) null else DictWord(formAt(i), frequencyAt(i))
    }

    /**
     * For predictive touch: adds to [out] the weight of every word that continues [prefix]
     * (ignoring case), keyed by its next character, and returns the weight of [prefix] itself
     * as a whole word. Weights grow exponentially with the list's log-scale frequency.
     */
    fun nextCharWeights(prefix: String, out: MutableMap<Char, Float>): Float {
        val p = prefix.lowercase()
        val range = keys.prefixRange(p)
        var whole = 0f
        for (i in SortedWords.rangeStart(range) until SortedWords.rangeEnd(range)) {
            if (offensive[i]) continue
            val w = Math.pow(2.0, frequencyAt(i) / 16.0).toFloat()
            if (keys.lengthAt(i) == p.length) {
                whole += w
            } else {
                val c = keys.charAt(i, p.length)
                out[c] = (out[c] ?: 0f) + w
            }
        }
        return whole
    }

    fun isOffensive(word: String): Boolean {
        val i = indexOf(word)
        return i >= 0 && offensive[i]
    }

    /**
     * The most frequent words starting with [prefix] (ignoring case), best first.
     * Offensive words are left out unless [allowOffensive].
     */
    fun completions(prefix: String, limit: Int, allowOffensive: Boolean = false): List<DictWord> {
        if (prefix.isEmpty()) return emptyList()
        val range = keys.prefixRange(prefix.lowercase())
        val top = TopN<DictWord>(limit)
        for (i in SortedWords.rangeStart(range) until SortedWords.rangeEnd(range)) {
            if (!allowOffensive && offensive[i]) continue
            val f = frequencyAt(i)
            if (top.wouldAccept(f.toFloat())) top.offer(DictWord(formAt(i), f), f.toFloat())
        }
        return top.result()
    }

    /**
     * Dictionary words within typing distance [maxCost] of [typed], ranked by closeness and
     * frequency.
     *
     * To stay fast enough to run on every key press, only these words are checked:
     * - words starting with the typed first letter;
     * - words starting with a neighbouring key followed by the typed second letter
     *   (the first letter was a fat-finger slip);
     * - words starting with the first two typed letters swapped ("hte" → "the").
     */
    fun corrections(
        typed: String,
        limit: Int,
        maxCost: Float,
        allowOffensive: Boolean = false,
        costs: TypingCosts = TypingCosts.DEFAULT,
    ): List<Correction> {
        val t = typed.lowercase()
        if (t.length < 2) return emptyList()
        val prefixes = buildSet {
            add(t.substring(0, 1))
            KeyProximity.neighbourhood(t[0]).forEach { if (it != t[0]) add("$it${t[1]}") }
            add("${t[1]}${t[0]}")
        }
        val maxLengthDiff = if (t.length <= 4) 1 else 2
        val top = TopN<Correction>(limit)
        val walk = PrefixSharingWalk(t, maxCost, costs)
        // A lower-case typo is rarely a name: "blaim" → "blame", not "Blair".
        val lowerTyped = typed.none { it.isUpperCase() }
        for (prefix in prefixes) {
            val range = keys.prefixRange(prefix)
            walk.run(SortedWords.rangeStart(range), SortedWords.rangeEnd(range)) { i, cost ->
                if (kotlin.math.abs(keys.lengthAt(i) - t.length) <= maxLengthDiff && (allowOffensive || !offensive[i])) {
                    val f = frequencyAt(i)
                    val form = formAt(i)
                    val c = if (lowerTyped && form[0].isUpperCase()) cost + costs.name else cost
                    if (c <= maxCost) top.offer(Correction(form, f, c), rankCorrection(f, c, costs))
                }
            }
        }
        return top.result()
    }

    /**
     * Computes [TypingDistance]-style costs from [typed] to every word in a range of the sorted
     * list, sharing work between words: neighbouring words share prefixes, and the table rows
     * for a shared prefix are computed once. When every entry of a row (and the row before it,
     * for swaps) is over the limit, no word with that prefix can match, so the whole block of
     * words is skipped with one binary search. This turns ~10k independent comparisons into
     * a walk over a few hundred prefixes.
     */
    private inner class PrefixSharingWalk(
        private val typed: String,
        private val limit: Float,
        private val costs: TypingCosts,
    ) {
        private val n = typed.length
        private val rows = Array(MAX_WORD + 1) { FloatArray(n + 1) }
        private val rowMin = FloatArray(MAX_WORD + 1)
        private val chars = CharArray(MAX_WORD)
        private val deleteCosts = FloatArray(n) { j -> costs.extra(typed[j], typed.getOrNull(j - 1)) }

        init {
            for (j in 1..n) rows[0][j] = rows[0][j - 1] + deleteCosts[j - 1]
            rowMin[0] = 0f
        }

        fun run(from: Int, to: Int, onMatch: (index: Int, cost: Float) -> Unit) {
            var valid = 0 // rows[0..valid] are correct for chars[0 until valid]
            var i = from
            while (i < to) {
                val len = keys.lengthAt(i)
                if (len > MAX_WORD) { i++; continue }
                var shared = 0
                val lim = minOf(len, valid)
                while (shared < lim && keys.charAt(i, shared) == chars[shared]) shared++
                var pruneAt = -1
                for (r in shared + 1..len) {
                    chars[r - 1] = keys.charAt(i, r - 1)
                    computeRow(r)
                    valid = r
                    if (rowMin[r] > limit && rowMin[r - 1] > limit) { pruneAt = r; break }
                }
                if (pruneAt >= 0) {
                    val blocked = String(chars, 0, pruneAt)
                    i = SortedWords.rangeEnd(keys.prefixRange(blocked, i, to))
                    continue
                }
                val cost = rows[len][n]
                if (cost <= limit) onMatch(i, cost)
                i++
            }
        }

        private fun computeRow(r: Int) {
            val c = chars[r - 1]
            val prev = rows[r - 1]
            val row = rows[r]
            val ins = costs.missing(c, if (r >= 2) chars[r - 2] else null)
            row[0] = prev[0] + ins
            var mn = row[0]
            for (j in 1..n) {
                val a = typed[j - 1]
                var best = minOf(prev[j - 1] + costs.substitute(a, c), prev[j] + ins, row[j - 1] + deleteCosts[j - 1])
                if (r >= 2 && j >= 2 && a != c && c == typed[j - 2] && chars[r - 2] == a) {
                    best = minOf(best, rows[r - 2][j - 2] + costs.swap)
                }
                row[j] = best
                if (best < mn) mn = best
            }
            rowMin[r] = mn
        }
    }

    companion object {
        /** Longest dictionary word the correction search considers. */
        private const val MAX_WORD = 48

        /** Higher is better: frequency matters, but each unit of typing distance costs a lot. */
        fun rankCorrection(frequency: Int, cost: Float, costs: TypingCosts = TypingCosts.DEFAULT): Float =
            frequency - costs.frequencyWeight * cost

        /** Loads the bundled list. Takes a few hundred milliseconds: call off the main thread. */
        fun load(): EnglishDictionary = parse(
            readResourceLines("/en/en_US_words.tsv"),
            readResourceLines("/en/en_US_shortcuts.tsv"),
        )

        /** [wordLines]: `word<TAB>freq<TAB>flags`; [shortcutLines]: `typed<TAB>replacement`. */
        fun parse(wordLines: List<String>, shortcutLines: List<String>): EnglishDictionary {
            class Entry(val key: String, val form: String, val freq: Int, val offensive: Boolean)
            val entries = wordLines.map { line ->
                val parts = line.split('\t')
                val form = parts[0]
                Entry(form.lowercase(), form, parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2)?.contains('o') == true)
            }.let { list ->
                // Sorted by key, most frequent spelling first among equal keys.
                if (list.zipWithNext().all { (a, b) -> a.key <= b.key }) list
                else list.sortedWith(compareBy<Entry> { it.key }.thenByDescending { it.freq })
            }
            val keys = SortedWords.of(entries.map { it.key })
            val forms = Array(entries.size) { i -> entries[i].form.takeIf { it != entries[i].key } }
            val freqs = ByteArray(entries.size) { i -> entries[i].freq.coerceIn(0, 255).toByte() }
            val offensive = BitSet(entries.size).apply { entries.forEachIndexed { i, e -> if (e.offensive) set(i) } }
            val shortcuts = shortcutLines.associate { line ->
                val (typed, replacement) = line.split('\t', limit = 2)
                typed.lowercase() to replacement
            }
            return EnglishDictionary(keys, forms, freqs, offensive, shortcuts)
        }
    }
}

/** Keeps the [limit] best items by score without sorting everything. Ties keep the earlier item. */
internal class TopN<T>(private val limit: Int) {
    private val items = ArrayList<Pair<T, Float>>(limit + 1)

    fun wouldAccept(score: Float): Boolean = items.size < limit || score > items.last().second

    fun offer(item: T, score: Float) {
        if (limit <= 0 || !wouldAccept(score)) return
        var pos = items.size
        while (pos > 0 && items[pos - 1].second < score) pos--
        items.add(pos, item to score)
        if (items.size > limit) items.removeAt(items.size - 1)
    }

    fun result(): List<T> = items.map { it.first }
}
