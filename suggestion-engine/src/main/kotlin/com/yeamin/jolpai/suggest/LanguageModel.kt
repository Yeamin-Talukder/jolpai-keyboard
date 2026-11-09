package com.yeamin.jolpai.suggest

import kotlin.math.exp

/**
 * The next-word model as the suggestion code uses it: "what comes next after these words?",
 * with the answers cached, because the same sentence is asked about on every key press of a
 * word. Thread-safe; returns null while the model is not loaded (or turned off).
 *
 * A sentence is passed as its words so far, oldest first; only the last [MAX_CONTEXT] count,
 * as in the model's training. An empty list means the start of a sentence.
 */
class LanguageModel(private val model: () -> NextWordModel?) {

    private val states = lru<NextWordModel.State>(STATE_CACHE)
    private val predictions = lru<Prediction>(PREDICTION_CACHE)

    /** The loaded model, if any. */
    val loaded: NextWordModel? get() = model()

    /** What the model expects after [context]. */
    fun predict(context: List<String>): Prediction? {
        val m = model() ?: return null
        val words = normalise(context)
        val key = words.joinToString(" ")
        cached(key, m)?.let { return it }
        // One computation at a time: suggestions and touch prediction often ask about the same
        // sentence at once, and the second caller then finds the first one's answer.
        synchronized(computeLock) {
            cached(key, m)?.let { return it }
            val p = Prediction(m, m.logProbs(stateFor(m, words)))
            synchronized(predictions) { predictions[key] = p }
            return p
        }
    }

    private val computeLock = Any()

    private fun cached(key: String, m: NextWordModel): Prediction? =
        synchronized(predictions) { predictions[key] }?.takeIf { it.model === m }

    /**
     * Log-probability of [continuation] (one or more words) following [context]: the sum of
     * each word's log-probability after the words before it. Words the model doesn't know
     * count with the "unknown" probability. Null without a model.
     */
    fun sequenceLogProb(context: List<String>, continuation: List<String>): Float? {
        var total = 0f
        val words = ArrayList(context)
        for (w in continuation) {
            val p = predict(words) ?: return null
            if (w == END_OF_SENTENCE) return total + p.endLogProb()
            total += p.logProbOrUnknown(w)
            words += w
        }
        return total
    }

    private fun stateFor(m: NextWordModel, words: List<String>): NextWordModel.State {
        // Reuse the longest prefix already read (typing adds one word at a time).
        var n = words.size
        var state: NextWordModel.State? = null
        while (n >= 0) {
            state = synchronized(states) { states[words.subList(0, n).joinToString(" ")] }
            if (state != null) break
            n--
        }
        var s = state ?: m.start().also { put("", it) }
        if (state == null) n = 0
        for (i in n until words.size) {
            s = m.step(s, m.token(words[i]))
            put(words.subList(0, i + 1).joinToString(" "), s)
        }
        return s
    }

    private fun put(key: String, s: NextWordModel.State) = synchronized(states) { states[key] = s }

    companion object {
        /** The model reads at most this many words of a sentence (as its keyboard does). */
        const val MAX_CONTEXT = 20

        /** In a [sequenceLogProb] continuation: the sentence ends here. */
        const val END_OF_SENTENCE = "</s>"
        private const val STATE_CACHE = 96
        private const val PREDICTION_CACHE = 12

        /** The last [MAX_CONTEXT] words, lower-cased (words with apostrophes read as "unknown"). */
        internal fun normalise(context: List<String>): List<String> =
            context.takeLast(MAX_CONTEXT).map { it.lowercase().replace('’', '\'') }

        private fun <T> lru(size: Int) = object : LinkedHashMap<String, T>(size, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = this.size > size
        }
    }
}

/** The model's expectations for the next word after one particular context. */
class Prediction internal constructor(val model: NextWordModel, private val logProbs: FloatArray) {

    /** Natural-log probability of [word] coming next, or null if the model doesn't know it. */
    fun logProb(word: String): Float? {
        val t = model.token(word)
        return if (t >= NextWordModel.FIRST_WORD) logProbs[t] else null
    }

    /** As [logProb], with unknown words getting the model's "unknown word" probability. */
    fun logProbOrUnknown(word: String): Float = logProb(word) ?: logProbs[NextWordModel.UNKNOWN]

    /** Log-probability that the sentence ends here. */
    fun endLogProb(): Float = logProbs[NextWordModel.END]

    /**
     * The most likely next words starting with [prefix] (lower case; "" for any), best first,
     * with their log-probabilities. Only words that pass [accept] are counted.
     */
    fun top(limit: Int, prefix: String = "", accept: (String) -> Boolean = { true }): List<Pair<String, Float>> {
        val best = ArrayList<Pair<String, Float>>(limit + 1)
        forPrefix(prefix) { t ->
            val lp = logProbs[t]
            if (best.size == limit && lp <= best[limit - 1].second) return@forPrefix
            val w = model.words[t]
            if (!accept(w)) return@forPrefix
            var at = best.size
            while (at > 0 && best[at - 1].second < lp) at--
            best.add(at, w to lp)
            if (best.size > limit) best.removeAt(limit)
        }
        return best
    }

    /**
     * For predictive touch: adds to [out] the probability of each next character after
     * [prefix] (lower case), summed over the model's words, and returns the probability that
     * the word is exactly [prefix].
     */
    fun nextCharWeights(prefix: String, out: MutableMap<Char, Float>, accept: (String) -> Boolean = { true }): Float {
        var whole = 0f
        forPrefix(prefix) { t ->
            val w = model.words[t]
            if (!accept(w)) return@forPrefix
            val p = exp(logProbs[t])
            if (w.length == prefix.length) {
                whole += p
            } else {
                val c = w[prefix.length]
                out[c] = (out[c] ?: 0f) + p
            }
        }
        return whole
    }

    private inline fun forPrefix(prefix: String, each: (Int) -> Unit) {
        val order = model.alphabetical
        var i = if (prefix.isEmpty()) 0 else lowerBound(order, prefix)
        while (i < order.size) {
            val t = order[i]
            if (prefix.isNotEmpty() && !model.words[t].startsWith(prefix)) break
            each(t)
            i++
        }
    }

    private fun lowerBound(order: IntArray, key: String): Int {
        var lo = 0
        var hi = order.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (model.words[order[mid]] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
