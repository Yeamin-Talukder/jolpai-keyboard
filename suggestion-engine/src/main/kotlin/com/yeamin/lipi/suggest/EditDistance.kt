package com.yeamin.lipi.suggest

/** Plain Levenshtein distance (insert, delete, substitute all cost 1). */
fun levenshtein(a: CharSequence, b: CharSequence): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val sub = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
        }
        val t = prev
        prev = cur
        cur = t
    }
    return prev[b.length]
}

/**
 * Where keys sit on a QWERTY phone keyboard, so a typo on a neighbouring key costs less
 * than a random substitution. Rows are offset like the on-screen keyboard (A row by half a key).
 */
object KeyProximity {
    private val positions: Map<Char, Pair<Float, Float>> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, i.toFloat() to 0f) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, i + 0.5f to 1f) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, i + 1.5f to 2f) }
    }

    /** Precomputed 26×26 table: the distance check runs millions of times per correction search. */
    private val table = BooleanArray(26 * 26).also { t ->
        for (a in 'a'..'z') for (b in 'a'..'z') {
            if (a == b) continue
            val pa = positions.getValue(a)
            val pb = positions.getValue(b)
            val dx = pa.first - pb.first
            val dy = pa.second - pb.second
            t[(a - 'a') * 26 + (b - 'a')] = dx * dx + dy * dy <= 1.6f
        }
    }

    /** True when [a] and [b] are adjacent keys (including diagonals). Lower-case letters only. */
    fun areNeighbours(a: Char, b: Char): Boolean {
        if (a !in 'a'..'z' || b !in 'a'..'z') return false
        return table[(a - 'a') * 26 + (b - 'a')]
    }

    /** The letters next to [c], including [c] itself. */
    fun neighbourhood(c: Char): String = buildString {
        append(c)
        positions.keys.forEach { if (areNeighbours(c, it)) append(it) }
    }
}

/**
 * Weighted Damerau–Levenshtein distance for typo correction, tuned for touch typing:
 * - a neighbouring key costs 0.6 instead of 1 (fat-finger typos)
 * - swapping two letters costs 0.7 ("hte" → "the")
 * - a missing or extra apostrophe costs 0.3 ("dont" → "don't")
 * - a doubled or undoubled letter costs 0.6 ("ocured" → "occurred")
 *
 * Keeps three reusable rows, so comparing against thousands of words allocates nothing.
 * Not thread-safe: use one instance per thread.
 */
class TypingDistance(maxLength: Int = 48) {
    private var r2 = FloatArray(maxLength + 1) // row i-2
    private var r1 = FloatArray(maxLength + 1) // row i-1
    private var r0 = FloatArray(maxLength + 1) // row i

    /** Returns the distance, or a value above [limit] as soon as it is known to exceed it. */
    fun compute(typed: CharSequence, word: CharSequence, limit: Float): Float {
        val n = typed.length
        val m = word.length
        if (kotlin.math.abs(n - m) > limit + 0.5f) return limit + 1f
        if (m + 1 > r0.size) {
            r2 = FloatArray(m + 1); r1 = FloatArray(m + 1); r0 = FloatArray(m + 1)
        }
        r1[0] = 0f
        for (j in 1..m) r1[j] = r1[j - 1] + insertCost(word, j - 1)
        for (i in 1..n) {
            r0[0] = r1[0] + deleteCost(typed, i - 1)
            var rowMin = r0[0]
            val a = typed[i - 1]
            for (j in 1..m) {
                val b = word[j - 1]
                val sub = when {
                    a == b -> 0f
                    KeyProximity.areNeighbours(a, b) -> 0.6f
                    else -> 1f
                }
                var best = minOf(
                    r1[j - 1] + sub,
                    r1[j] + deleteCost(typed, i - 1),
                    r0[j - 1] + insertCost(word, j - 1),
                )
                if (i > 1 && j > 1 && a == word[j - 2] && typed[i - 2] == b && a != b) {
                    best = minOf(best, r2[j - 2] + 0.7f)
                }
                r0[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > limit) return limit + 1f
            val t = r2
            r2 = r1
            r1 = r0
            r0 = t
        }
        return r1[m]
    }
}

/** Cost of having typed an extra character at [index] of [s]. */
private fun deleteCost(s: CharSequence, index: Int): Float = when {
    index < 0 -> 1f
    s[index] == '\'' -> 0.3f
    index > 0 && s[index - 1] == s[index] -> 0.6f
    else -> 1f
}

/** Cost of having missed the character at [index] of the dictionary word [s]. */
private fun insertCost(s: CharSequence, index: Int): Float = when {
    index < 0 -> 0f
    s[index] == '\'' -> 0.3f
    index > 0 && s[index - 1] == s[index] -> 0.6f
    else -> 1f
}
