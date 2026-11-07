package com.yeamin.jolpai.suggest

/**
 * An immutable sorted list of strings packed into a single [CharArray].
 *
 * About 2 bytes per character plus 4 bytes per word, instead of the ~40+ bytes of object
 * overhead a `String` costs on Android. With ~160k words per language this keeps each
 * dictionary at a few megabytes. Order is plain UTF-16 code-unit order (`String.compareTo`).
 */
class SortedWords private constructor(
    private val chars: CharArray,
    /** starts[i] is where word i begins; starts[size] is the end of the last word. */
    private val starts: IntArray,
) {
    val size: Int get() = starts.size - 1

    fun wordAt(i: Int): String = String(chars, starts[i], starts[i + 1] - starts[i])

    fun lengthAt(i: Int): Int = starts[i + 1] - starts[i]

    fun charAt(i: Int, k: Int): Char = chars[starts[i] + k]

    /** A reusable, allocation-free view of one word; point it at a word with [View.index]. */
    inner class View : CharSequence {
        var index = 0
        override val length: Int get() = lengthAt(index)
        override fun get(index: Int): Char = charAt(this.index, index)
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            wordAt(index).subSequence(startIndex, endIndex)
        override fun toString(): String = wordAt(index)
    }

    /** Compares word [i] with [key], like `wordAt(i).compareTo(key)` but without allocating. */
    fun compare(i: Int, key: CharSequence): Int {
        val s = starts[i]
        val len = starts[i + 1] - s
        val n = minOf(len, key.length)
        for (k in 0 until n) {
            val d = chars[s + k] - key[k]
            if (d != 0) return d
        }
        return len - key.length
    }

    fun equalsAt(i: Int, key: CharSequence): Boolean = lengthAt(i) == key.length && compare(i, key) == 0

    fun startsWith(i: Int, prefix: CharSequence): Boolean {
        val s = starts[i]
        if (starts[i + 1] - s < prefix.length) return false
        for (k in prefix.indices) if (chars[s + k] != prefix[k]) return false
        return true
    }

    /** First index in [from, to) whose word is >= [key]; [to] if none. */
    fun lowerBound(key: CharSequence, from: Int = 0, to: Int = size): Int {
        var lo = from
        var hi = to
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compare(mid, key) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * The index range [start, end) of words that start with [prefix], searched within [from, to).
     * Returned packed as `start.toLong() shl 32 or end` to avoid allocating on the typing path;
     * use [rangeStart] and [rangeEnd] to unpack.
     */
    fun prefixRange(prefix: CharSequence, from: Int = 0, to: Int = size): Long {
        val start = lowerBound(prefix, from, to)
        var lo = start
        var hi = to
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (startsWith(mid, prefix)) lo = mid + 1 else hi = mid
        }
        return (start.toLong() shl 32) or lo.toLong()
    }

    companion object {
        fun rangeStart(range: Long): Int = (range ushr 32).toInt()
        fun rangeEnd(range: Long): Int = (range and 0xFFFFFFFFL).toInt()

        /** Packs [words], sorting them first if they are not already sorted. Duplicates are kept. */
        fun of(words: List<String>): SortedWords {
            val sorted = if (isSorted(words)) words else words.sorted()
            var total = 0
            for (w in sorted) total += w.length
            val chars = CharArray(total)
            val starts = IntArray(sorted.size + 1)
            var pos = 0
            sorted.forEachIndexed { i, w ->
                starts[i] = pos
                w.toCharArray(chars, pos)
                pos += w.length
            }
            starts[sorted.size] = pos
            return SortedWords(chars, starts)
        }

        fun isSorted(words: List<String>): Boolean {
            for (i in 1 until words.size) if (words[i - 1] > words[i]) return false
            return true
        }
    }
}

/** Reads a UTF-8 text resource bundled with this module, line by line. */
internal fun readResourceLines(path: String): List<String> {
    val stream = SortedWords::class.java.getResourceAsStream(path) ?: error("Missing resource $path")
    return stream.bufferedReader(Charsets.UTF_8).use { r -> r.readLines().filter { it.isNotEmpty() } }
}

internal fun readResourceText(path: String): String {
    val stream = SortedWords::class.java.getResourceAsStream(path) ?: error("Missing resource $path")
    return stream.use { it.readBytes().decodeToString() }
}
