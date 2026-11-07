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
 * The edit costs used to find corrections. Fat-finger slips and the mistakes people make
 * when spelling by sound both cost less than a random edit:
 * - a neighbouring key ("tge" → "the")
 * - two letters swapped ("hte" → "the")
 * - a missing or extra apostrophe ("dont" → "don't")
 * - a doubled letter typed once, or a single one typed twice ("ocured" → "occurred")
 * - one vowel for another, or a vowel left out or added ("seperate" → "separate")
 * - letters that sound alike: c/k/s, s/z, g/j, i/y ("concious" → "conscious")
 *
 * The values were tuned on public misspelling lists (see SpellingEvalTest).
 */
class TypingCosts(
    val neighbour: Float = 0.6f,
    val swap: Float = 0.7f,
    val apostrophe: Float = 0.3f,
    val doubled: Float = 0.4f,
    val vowel: Float = 0.7f,
    val vowelGap: Float = 0.8f,
    val sound: Float = 0.7f,
    /** Added for a capitalised word (a name) when the typed word is lower case. */
    val name: Float = 0.5f,
    /** How much one unit of cost is worth in frequency (0–255) when ranking corrections. */
    val frequencyWeight: Float = 90f,
) {
    /** 27×27: 'a'..'z' and one slot for everything else. Read millions of times per search. */
    private val subTable = FloatArray(27 * 27).also { t ->
        for (x in 0 until 27) for (y in 0 until 27) {
            val a = if (x < 26) 'a' + x else '?'
            val b = if (y < 26) 'a' + y else '?'
            t[x * 27 + y] = when {
                x == y && x < 26 -> 0f
                x == 26 || y == 26 -> 1f
                KeyProximity.areNeighbours(a, b) -> minOf(neighbour, if (isVowel(a) && isVowel(b)) vowel else 1f)
                isVowel(a) && isVowel(b) -> vowel
                soundsAlike(a, b) -> sound
                else -> 1f
            }
        }
    }

    private fun slot(c: Char): Int = if (c in 'a'..'z') c - 'a' else 26

    /** Cost of typing [typed] where the word has [wanted]. */
    fun substitute(typed: Char, wanted: Char): Float =
        if (typed == wanted) 0f else subTable[slot(typed) * 27 + slot(wanted)]

    /** Cost of a character that's in the word but wasn't typed ([before] is the word's previous one). */
    fun missing(c: Char, before: Char?): Float = gap(c, before)

    /** Cost of a typed character that isn't in the word ([before] is the typed previous one). */
    fun extra(c: Char, before: Char?): Float = gap(c, before)

    private fun gap(c: Char, before: Char?): Float = when {
        c == '\'' -> apostrophe
        before == c -> doubled
        isVowel(c) -> vowelGap
        else -> 1f
    }

    companion object {
        // Before DEFAULT: building it reads this.
        private val SOUND_PAIRS = setOf("ck", "cs", "sz", "gj", "iy", "kq", "fv")

        val DEFAULT = TypingCosts()

        private fun isVowel(c: Char) = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'
        private fun soundsAlike(a: Char, b: Char) = "$a$b" in SOUND_PAIRS || "$b$a" in SOUND_PAIRS
    }
}

/**
 * Weighted Damerau–Levenshtein distance for typo correction, with the costs of [TypingCosts].
 *
 * Keeps three reusable rows, so comparing against thousands of words allocates nothing.
 * Not thread-safe: use one instance per thread.
 */
class TypingDistance(maxLength: Int = 48, private val costs: TypingCosts = TypingCosts.DEFAULT) {
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
        fun ins(j: Int) = costs.missing(word[j], word.getOrNull(j - 1))
        fun del(i: Int) = costs.extra(typed[i], typed.getOrNull(i - 1))
        r1[0] = 0f
        for (j in 1..m) r1[j] = r1[j - 1] + ins(j - 1)
        for (i in 1..n) {
            r0[0] = r1[0] + del(i - 1)
            var rowMin = r0[0]
            val a = typed[i - 1]
            for (j in 1..m) {
                val b = word[j - 1]
                var best = minOf(
                    r1[j - 1] + costs.substitute(a, b),
                    r1[j] + del(i - 1),
                    r0[j - 1] + ins(j - 1),
                )
                if (i > 1 && j > 1 && a == word[j - 2] && typed[i - 2] == b && a != b) {
                    best = minOf(best, r2[j - 2] + costs.swap)
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
