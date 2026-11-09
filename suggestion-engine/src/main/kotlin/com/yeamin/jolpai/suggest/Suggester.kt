package com.yeamin.jolpai.suggest

import kotlin.math.ln

/** Suggestions for the word being typed. */
data class WordSuggestions(
    /** The word as typed. */
    val typed: String,
    /** Other words to offer, best first. Never contains [typed]. */
    val candidates: List<String>,
    /** What pressing space would turn [typed] into, or null to leave it alone. */
    val autoCorrect: String?,
    /** [typed] is a dictionary, personal or often-typed word. */
    val typedIsKnown: Boolean,
    /** Emoji that fit the sentence (from the next-word model's word meanings), best first. */
    val emoji: List<String> = emptyList(),
) {
    companion object {
        val EMPTY = WordSuggestions("", emptyList(), null, false)
    }
}

data class SuggestOptions(
    val autoCorrect: Boolean = true,
    val blockOffensive: Boolean = true,
    val limit: Int = 3,
    /** The sentence's words before the one being typed, oldest first (for the next-word model). */
    val context: List<String> = emptyList(),
    /** Rank by context with the on-device next-word model (when it is loaded). */
    val useModel: Boolean = false,
)

/**
 * The single entry point the keyboard uses for suggestions.
 *
 * Dictionaries load in the background; until a provider returns non-null, the matching
 * suggestions are simply empty and typing works as normal.
 */
class Suggester(
    private val english: () -> EnglishDictionary?,
    private val bangla: () -> BanglaDictionary?,
    private val avro: () -> AvroSuggester?,
    val history: UserHistory,
    /** Edit costs for English corrections (a parameter only so tuning runs can try others). */
    internal val costs: TypingCosts = TypingCosts.DEFAULT,
    /** Autocorrect only when the best fix outranks the next one by this much (see [isConfident]). */
    internal val autoCorrectMargin: Float = AUTOCORRECT_MARGIN,
    /** The English next-word model (loaded in the background; null until then). */
    model: () -> NextWordModel? = { null },
    /** How much the model's in-context probability counts against the word list's frequency. */
    internal val modelWeight: Float = MODEL_WEIGHT,
    /** The Bangla next-word model (trained for Jolpai; null until loaded or if missing). */
    banglaModel: () -> NextWordModel? = { null },
    /** Bangla next words: how much the model's choice counts against the list and history. */
    internal val banglaNextWeight: Float = BANGLA_NEXT_MODEL,
) {
    /** The next-word model with its caches, shared by suggestions, touch and the checker. */
    val lm = LanguageModel(model)

    /** The Bangla next-word model with its caches (Avro, জাতীয়, প্রভাত). */
    val lmBangla = LanguageModel(banglaModel)

    /** Next-character probabilities for predictive touch targets. */
    val nextChars = NextCharPredictor(english, bangla, avro, history, lm)

    /** Offline spelling, punctuation and grammar checking (the ✓ Check tool). */
    val checker = TextChecker(english, bangla, history, costs, lm)

    // ------------------------------------------------------------------ English

    fun english(typed: String, options: SuggestOptions = SuggestOptions()): WordSuggestions {
        val dict = english() ?: return WordSuggestions(typed, emptyList(), null, false)
        if (typed.isEmpty()) return WordSuggestions.EMPTY

        val lower = typed.lowercase()
        val entry = dict.lookup(typed)
        val known = entry != null || history.isKnown(Language.ENGLISH, typed)
        // What the sentence so far makes likely (null without the model).
        val p = if (options.useModel) lm.predict(options.context) else null
        val scored = LinkedHashMap<String, Float>()
        fun add(word: String, score: Float) {
            val w = applyCase(typed, word)
            if (w == typed) return
            val old = scored[w]
            if (old == null || score > old) scored[w] = score
        }

        val shortcut = dict.shortcuts[lower]
        shortcut?.let { add(it, 1000f) }

        var correction: Correction? = null
        var corrections: List<Correction> = emptyList()
        if (!known && typed.length >= 2 && typed.all { it.isLetter() || it == '\'' }) {
            corrections = dict.corrections(typed, if (p != null) 8 else 4, maxCost(typed.length), allowOffensive = !options.blockOffensive, costs = costs)
            // With the model, more candidates are fetched and the sentence picks among them.
            if (p != null) corrections = corrections.sortedByDescending { rank(it, p) }.take(4)
            correction = corrections.firstOrNull()
            corrections.forEachIndexed { i, c -> add(c.word, 600f - i + rank(c, p) / 1000f) }
        }

        // The user's own words (personal dictionary, or typed before) outrank dictionary guesses.
        history.wordsWithPrefix(Language.ENGLISH, typed, 3).forEach { add(it, 700f + history.count(Language.ENGLISH, it)) }
        dict.completions(typed, 6, allowOffensive = !options.blockOffensive).forEach { d ->
            val user = history.count(Language.ENGLISH, d.word)
            add(d.word, blend(d.word, d.frequency, p) + 25f * ln(1f + user))
        }
        // Words the sentence makes likely even if they are rarer overall ("i would like to th" → thank).
        if (p != null && typed.all { it.isLetter() }) {
            val accept = { w: String -> w != lower && dict.contains(w) && (!options.blockOffensive || !dict.isOffensive(w)) }
            p.top(MODEL_COMPLETIONS, lower, accept).forEach { (w, _) ->
                val d = dict.lookup(w) ?: return@forEach
                add(d.word, blend(d.word, d.frequency, p) + 25f * ln(1f + history.count(Language.ENGLISH, d.word)))
            }
        }

        val candidates = scored.entries.sortedByDescending { it.value }.map { it.key }.take(options.limit)

        val autoCorrect = when {
            !options.autoCorrect -> null
            shortcut != null -> applyCase(typed, shortcut)
            // Known word in the wrong case, e.g. "i" → "I".
            entry != null -> applyCase(typed, entry.word).takeIf { it != typed && typed == lower && entry.word != lower }
            known -> null
            correction != null && isConfident(typed, corrections, p) -> applyCase(typed, correction.word)
            else -> null
        }
        return WordSuggestions(typed, candidates, autoCorrect, known)
    }

    /**
     * A word's frequency (0–255, the word list's log scale) blended with how likely the model
     * finds it here. The list's scale is about [FREQ_PER_NAT] points per natural-log unit of
     * probability ("the" 222, "house" 152, "quixotic" 62), so the model's log-probability is put
     * on the same scale first. Words the model doesn't know keep their frequency.
     */
    private fun blend(word: String, frequency: Int, p: Prediction?): Float {
        val lp = p?.logProb(word) ?: return frequency.toFloat()
        val modelFrequency = FREQ_AT_CERTAIN + FREQ_PER_NAT * lp
        return (1 - modelWeight) * frequency + modelWeight * modelFrequency
    }

    /** [EnglishDictionary.rankCorrection] with the frequency blended by context. */
    private fun rank(c: Correction, p: Prediction?): Float = blend(c.word, c.frequency, p) - costs.frequencyWeight * c.cost

    private fun maxCost(length: Int): Float = when {
        length <= 2 -> 0.7f
        length <= 4 -> 1.0f
        length <= 7 -> 1.6f
        else -> 2.0f
    }

    /**
     * Only autocorrect when the fix is close, the corrected word is reasonably common, and no
     * other fix is nearly as likely (then the choice is left to the user: both are offered).
     */
    private fun isConfident(typed: String, corrections: List<Correction>, p: Prediction?): Boolean {
        val c = corrections.firstOrNull() ?: return false
        if (typed.length < 3) return false
        if (typed.drop(1).any { it.isUpperCase() }) return false // acronyms, iPhone-style names
        val limit = when {
            typed.length <= 4 -> 1.0f
            typed.length <= 7 -> 1.5f
            else -> 2.0f
        }
        if (c.cost > limit || c.frequency < 40) return false
        val next = corrections.getOrNull(1)?.takeIf { !it.word.equals(c.word, ignoreCase = true) } ?: return true
        return rank(c, p) - rank(next, p) >= autoCorrectMargin
    }

    // ------------------------------------------------------------------ Bangla (National)

    fun bangla(typed: String, limit: Int = 3, options: SuggestOptions = SuggestOptions()): WordSuggestions {
        val dict = bangla() ?: return WordSuggestions(typed, emptyList(), null, false)
        if (typed.isEmpty()) return WordSuggestions.EMPTY
        val known = dict.contains(typed) || history.isKnown(Language.BANGLA, typed)
        val p = if (options.useModel) lmBangla.predict(options.context) else null
        if (p == null) {
            val out = LinkedHashSet<String>()
            history.wordsWithPrefix(Language.BANGLA, typed, limit).forEach { out += it }
            dict.completions(typed, limit * 2).forEach { out += it }
            out.remove(typed)
            return WordSuggestions(typed, out.take(limit), null, known)
        }
        // With the model: the user's words first, then list completions and words the sentence
        // makes likely, ranked by frequency blended with the model (as for English).
        val scores = LinkedHashMap<String, Float>()
        history.wordsWithPrefix(Language.BANGLA, typed, limit).forEach { scores[it] = USER_WORD + history.count(Language.BANGLA, it) }
        val fromList = dict.completions(typed, limit * 4)
        val fromModel = p.top(MODEL_COMPLETIONS, typed) { dict.contains(it) || history.isKnown(Language.BANGLA, it, minCount = 1) }.map { it.first }
        (fromList + fromModel).forEach { w -> if (w != typed && w !in scores) scores[w] = blendBangla(w, dict.frequency(w), p) }
        scores.remove(typed)
        return WordSuggestions(typed, scores.entries.sortedByDescending { it.value }.map { it.key }.take(limit), null, known)
    }

    /** As [blend], on the Bangla list's scale (12 points per doubling of a word's count). */
    private fun blendBangla(word: String, frequency: Int, p: Prediction): Float {
        val lp = p.logProb(word) ?: return frequency.toFloat()
        return (1 - modelWeight) * frequency + modelWeight * (BN_FREQ_AT_CERTAIN + BN_FREQ_PER_NAT * lp)
    }

    // ------------------------------------------------------------------ Avro

    /**
     * Avro candidates for [term]. With [SuggestOptions.useModel] and the Bangla model loaded,
     * dictionary words are ordered by the sentence too ([SuggestOptions.context]).
     */
    fun avro(term: String, options: SuggestOptions = SuggestOptions()): AvroCandidates? {
        val a = avro() ?: return null
        val p = if (options.useModel) lmBangla.predict(options.context) else null
        return a.suggest(term, history::selection, context = p?.let { pr -> { w: String -> pr.logProb(w) } })
    }

    /** Remembers the user's pick for an Avro word, so it is chosen first next time. */
    fun recordAvroSelection(term: String, chosen: String) {
        val a = avro() ?: return
        history.recordSelection(a.selectionKey(term), a.selectionValue(term, chosen))
    }

    // ------------------------------------------------------------------ next word

    /**
     * Words to offer after a space. From the user's history (words they typed after [previous],
     * and after the two words before it; recent habits count more, see [UserHistory]) and:
     * - English with [SuggestOptions.useModel]: the next-word model reading the whole sentence
     *   ([SuggestOptions.context]); a sequence the user typed before gets a boost.
     * - Bangla: the ranked next-word list counted from open Bangla text ([BanglaNextWords]).
     * With the model or the list this also works at the start of a sentence. [capitalize]
     * starts each with a capital.
     */
    fun nextWords(
        language: Language,
        previous: String?,
        limit: Int = 3,
        options: SuggestOptions = SuggestOptions(),
        capitalize: Boolean = false,
    ): List<String> {
        val previous2 = secondPrevious(previous, options.context)
        val learnedScores = if (previous.isNullOrBlank()) emptyList() else history.nextWordScores(language, previous2, previous, limit * 2)
        if (language == Language.BANGLA) {
            val p = if (options.useModel) lmBangla.predict(options.context) else null
            return banglaNextWords(previous2, previous, learnedScores, limit, p)
        }
        val learned = learnedScores.map { it.first }
        val dict = english()
        val p = if (language == Language.ENGLISH && options.useModel) lm.predict(options.context) else null
        if (p == null || dict == null) return learned.take(limit)
        val scores = LinkedHashMap<String, Float>()
        val accept = { w: String -> dict.contains(w) && (!options.blockOffensive || !dict.isOffensive(w)) }
        p.top(MODEL_NEXT_WORDS, accept = accept).forEach { (w, lp) -> scores[w] = lp }
        val forms = HashMap<String, String>()
        learnedScores.forEach { (w, learnedScore) ->
            val k = w.lowercase()
            forms[k] = w
            val score = (p.logProb(k) ?: LEARNED_FLOOR) + PAIR_BOOST * ln(1f + learnedScore)
            if (score > (scores[k] ?: Float.NEGATIVE_INFINITY)) scores[k] = score
        }
        return scores.entries.sortedByDescending { it.value }.take(limit).map { (k, _) ->
            val form = dict.lookup(k)?.word ?: forms[k] ?: k // the list's spelling: "i" → "I"
            if (capitalize) form.replaceFirstChar { it.uppercaseChar() } else form
        }
    }

    /**
     * Bangla next words: the open-text list after the two previous words (strongest), after the
     * previous word (or a sentence start), and what the user typed in that place before, which
     * outranks the list once it has been typed a couple of times.
     */
    private fun banglaNextWords(
        previous2: String?,
        previous: String?,
        learned: List<Pair<String, Float>>,
        limit: Int,
        model: Prediction? = null,
    ): List<String> {
        val dict = bangla()
        val list = dict?.nextWords
        val scores = LinkedHashMap<String, Float>()
        val forms = HashMap<String, String>()
        fun add(word: String, score: Float) {
            val k = BanglaNextWords.normalize(word)
            forms.putIfAbsent(k, word)
            scores[k] = (scores[k] ?: 0f) + score
        }
        learned.forEach { (w, s) -> add(w, LEARNED_WEIGHT * ln(1f + s)) } // first: keep the user's spelling
        if (list != null) {
            if (previous2 != null && !previous.isNullOrBlank()) {
                list.after(previous2, previous, LIST_TRIGRAMS).forEach { (w, wt) -> add(w, TRIGRAM_LIST_WEIGHT * wt / 255f) }
            }
            list.after(previous, LIST_NEXT_WORDS).forEach { (w, wt) -> add(w, wt / 255f) }
        }
        // The Bangla model reads the whole sentence: its best word adds [banglaNextWeight], others
        // less, down to nothing 3 log units below the best (only words the list knows).
        if (model != null && dict != null) {
            val top = model.top(MODEL_NEXT_WORDS) { dict.contains(it) }
            val best = top.firstOrNull()?.second
            if (best != null) top.forEach { (w, lp) -> add(w, banglaNextWeight * (1f + (lp - best) / 3f).coerceAtLeast(0f)) }
        }
        return scores.entries.sortedByDescending { it.value }.take(limit).map { forms[it.key] ?: it.key }
    }

    /** The word before [previous] in the sentence, when [context] ends with [previous]. */
    private fun secondPrevious(previous: String?, context: List<String>): String? {
        if (previous.isNullOrBlank() || context.size < 2 || !context.last().equals(previous, ignoreCase = true)) return null
        return context[context.size - 2]
    }

    companion object {
        /** See [banglaNextWeight]: the model's best next word counts like a strong list match. */
        internal const val BANGLA_NEXT_MODEL = 1.5f

        /** Bangla list frequencies (12·log2 count): a certain word, and points per log unit. */
        private const val BN_FREQ_AT_CERTAIN = 300f
        private const val BN_FREQ_PER_NAT = 17.3f

        /** Bangla completions with the model: the user's own words stay first. */
        private const val USER_WORD = 1000f

        /** Bangla next words: a learned word scores this × ln(1 + its faded use weight). */
        private const val LEARNED_WEIGHT = 1.0f

        /** Bangla next words: a two-word match in the list counts this much more than a one-word match. */
        private const val TRIGRAM_LIST_WEIGHT = 1.6f

        /** Bangla next words read from the list per context. */
        private const val LIST_NEXT_WORDS = 6
        private const val LIST_TRIGRAMS = 3

        /** See [autoCorrectMargin]: in rank units (frequency 0–255 minus weighted cost). */
        internal const val AUTOCORRECT_MARGIN = 10f

        /** See [modelWeight]: measured in ModelEvalTest (0.6–0.8 best on everyday sentences). */
        internal const val MODEL_WEIGHT = 0.7f

        /** Word-list frequency of a word that is certain (log-probability 0), see [blend]. */
        private const val FREQ_AT_CERTAIN = 259f
        private const val FREQ_PER_NAT = 12.3f

        /** Extra completions taken from the model while typing a word. */
        private const val MODEL_COMPLETIONS = 4

        /** Model words considered for next-word suggestions. */
        private const val MODEL_NEXT_WORDS = 8

        /** Next words: a pair the user typed before counts this much per log of its count. */
        private const val PAIR_BOOST = 2f

        /** Next words: log-probability given to a learned word the model doesn't know. */
        private const val LEARNED_FLOOR = -9f

        /**
         * Gives [word] the capitalisation the user typed: ALL CAPS stays all caps, a capital
         * first letter stays capital; otherwise the dictionary spelling is used ("I", "iPhone").
         */
        fun applyCase(typed: String, word: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}
