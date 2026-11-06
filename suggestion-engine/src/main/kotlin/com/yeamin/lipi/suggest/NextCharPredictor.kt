package com.yeamin.lipi.suggest

/** Which typing system a prediction is for. */
enum class TypingScript { ENGLISH, AVRO, NATIONAL }

/**
 * How likely each character is to be typed next, after [prefix]. [probability] is over
 * single characters (lower case for English and Avro); whatever is left over (1 − the sum)
 * is the chance the word ends here or continues with something unusual.
 */
class NextChars(val script: TypingScript, val prefix: String, private val probabilities: Map<Char, Float>) {
    fun probability(c: Char): Float = probabilities[c] ?: 0f

    /** True when nothing is known (no dictionary yet, or a prefix no word starts with). */
    val isEmpty: Boolean get() = probabilities.isEmpty()

    override fun toString(): String =
        probabilities.entries.sortedByDescending { it.value }.take(5).joinToString { "${it.key}=${"%.2f".format(it.value)}" }
}

/**
 * Predicts the next character from the word typed so far, for predictive touch targets.
 *
 * - English: every dictionary word continuing the prefix, weighted by frequency, plus the
 *   user's learned and personal words.
 * - National (জাতীয়): the Bangla word list (no frequencies, so each word counts once) plus
 *   learned words.
 * - Avro: for each next Roman letter, how many Bangla dictionary words the longer spelling
 *   can still reach (via the Avro dictionary search), plus Roman spellings the user picked
 *   before.
 *
 * Results are cached per prefix. English and National take well under a millisecond for
 * typical prefixes; Avro runs 26 dictionary searches, so callers run this off the main thread.
 * Pure Kotlin; thread-safe.
 */
class NextCharPredictor(
    private val english: () -> EnglishDictionary?,
    private val bangla: () -> BanglaDictionary?,
    private val avro: () -> AvroSuggester?,
    private val history: UserHistory,
) {
    private val cache = object : LinkedHashMap<String, NextChars>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NextChars>?) = size > 128
    }

    @Synchronized
    private fun cached(key: String): NextChars? = cache[key]

    @Synchronized
    private fun store(key: String, value: NextChars) {
        if (!value.isEmpty) cache[key] = value // not cached while dictionaries are still loading
    }

    fun predict(script: TypingScript, prefix: String): NextChars {
        val key = "${script.ordinal}:$prefix"
        cached(key)?.let { return it }
        val weights = HashMap<Char, Float>()
        val whole = when (script) {
            TypingScript.ENGLISH -> englishWeights(prefix, weights)
            TypingScript.NATIONAL -> banglaWeights(prefix, weights)
            TypingScript.AVRO -> avroWeights(prefix, weights)
        }
        val result = normalise(script, prefix, weights, whole)
        store(key, result)
        return result
    }

    private fun englishWeights(prefix: String, out: HashMap<Char, Float>): Float {
        val dict = english() ?: return 0f
        val p = prefix.lowercase()
        var whole = dict.nextCharWeights(p, out)
        history.wordsWithPrefix(Language.ENGLISH, p, LEARNED_LIMIT).forEach { w ->
            val k = w.lowercase()
            if (k.length > p.length) out.add(k[p.length], LEARNED_ENGLISH)
        }
        if (history.isKnown(Language.ENGLISH, p, minCount = 1)) whole += LEARNED_ENGLISH
        return whole
    }

    private fun banglaWeights(prefix: String, out: HashMap<Char, Float>): Float {
        val dict = bangla() ?: return 0f
        var whole = dict.nextCharWeights(prefix, out)
        history.wordsWithPrefix(Language.BANGLA, prefix, LEARNED_LIMIT).forEach { w ->
            if (w.length > prefix.length) out.add(w[prefix.length], LEARNED_BANGLA)
        }
        if (history.isKnown(Language.BANGLA, prefix, minCount = 1)) whole += LEARNED_BANGLA
        return whole
    }

    private fun avroWeights(prefix: String, out: HashMap<Char, Float>): Float {
        val a = avro() ?: return 0f
        val p = prefix.lowercase()
        for (c in 'a'..'z') {
            val n = a.matchCount(p + c)
            if (n > 0) out[c] = kotlin.math.ln(1f + n)
        }
        history.selectionsWithPrefix(p, LEARNED_LIMIT).forEach { r -> out.add(r[p.length].lowercaseChar(), LEARNED_AVRO) }
        // A complete spelling may well end here (space follows); give it a share like a strong letter.
        return if (p.isNotEmpty() && a.matchCount(p) > 0) (out.values.maxOrNull() ?: 0f) else 0f
    }

    private fun normalise(script: TypingScript, prefix: String, weights: Map<Char, Float>, whole: Float): NextChars {
        val total = weights.values.sum() + whole
        if (total <= 0f) return NextChars(script, prefix, emptyMap())
        return NextChars(script, prefix, weights.mapValues { it.value / total })
    }

    private fun HashMap<Char, Float>.add(c: Char, w: Float) {
        this[c] = (this[c] ?: 0f) + w
    }

    private companion object {
        const val LEARNED_LIMIT = 40

        /** A learned English word weighs like a fairly common dictionary word (frequency ~190). */
        const val LEARNED_ENGLISH = 4096f
        const val LEARNED_BANGLA = 20f
        const val LEARNED_AVRO = 3f
    }
}
