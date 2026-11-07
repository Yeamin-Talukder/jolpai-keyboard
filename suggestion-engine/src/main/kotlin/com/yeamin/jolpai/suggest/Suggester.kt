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
) {
    companion object {
        val EMPTY = WordSuggestions("", emptyList(), null, false)
    }
}

data class SuggestOptions(
    val autoCorrect: Boolean = true,
    val blockOffensive: Boolean = true,
    val limit: Int = 3,
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
) {
    /** Next-character probabilities for predictive touch targets. */
    val nextChars = NextCharPredictor(english, bangla, avro, history)

    /** Offline spelling, punctuation and grammar checking (the ✓ Check tool). */
    val checker = TextChecker(english, bangla, history, costs)

    // ------------------------------------------------------------------ English

    fun english(typed: String, options: SuggestOptions = SuggestOptions()): WordSuggestions {
        val dict = english() ?: return WordSuggestions(typed, emptyList(), null, false)
        if (typed.isEmpty()) return WordSuggestions.EMPTY

        val lower = typed.lowercase()
        val entry = dict.lookup(typed)
        val known = entry != null || history.isKnown(Language.ENGLISH, typed)
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
            corrections = dict.corrections(typed, 4, maxCost(typed.length), allowOffensive = !options.blockOffensive, costs = costs)
            correction = corrections.firstOrNull()
            corrections.forEachIndexed { i, c ->
                add(c.word, 600f - i + EnglishDictionary.rankCorrection(c.frequency, c.cost, costs) / 1000f)
            }
        }

        // The user's own words (personal dictionary, or typed before) outrank dictionary guesses.
        history.wordsWithPrefix(Language.ENGLISH, typed, 3).forEach { add(it, 700f + history.count(Language.ENGLISH, it)) }
        dict.completions(typed, 6, allowOffensive = !options.blockOffensive).forEach { d ->
            val user = history.count(Language.ENGLISH, d.word)
            add(d.word, d.frequency + 25f * ln(1f + user))
        }

        val candidates = scored.entries.sortedByDescending { it.value }.map { it.key }.take(options.limit)

        val autoCorrect = when {
            !options.autoCorrect -> null
            shortcut != null -> applyCase(typed, shortcut)
            // Known word in the wrong case, e.g. "i" → "I".
            entry != null -> applyCase(typed, entry.word).takeIf { it != typed && typed == lower && entry.word != lower }
            known -> null
            correction != null && isConfident(typed, corrections) -> applyCase(typed, correction.word)
            else -> null
        }
        return WordSuggestions(typed, candidates, autoCorrect, known)
    }

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
    private fun isConfident(typed: String, corrections: List<Correction>): Boolean {
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
        return EnglishDictionary.rankCorrection(c.frequency, c.cost, costs) -
            EnglishDictionary.rankCorrection(next.frequency, next.cost, costs) >= autoCorrectMargin
    }

    // ------------------------------------------------------------------ Bangla (National)

    fun bangla(typed: String, limit: Int = 3): WordSuggestions {
        val dict = bangla() ?: return WordSuggestions(typed, emptyList(), null, false)
        if (typed.isEmpty()) return WordSuggestions.EMPTY
        val known = dict.contains(typed) || history.isKnown(Language.BANGLA, typed)
        val out = LinkedHashSet<String>()
        history.wordsWithPrefix(Language.BANGLA, typed, limit).forEach { out += it }
        dict.completions(typed, limit * 2).forEach { out += it }
        out.remove(typed)
        return WordSuggestions(typed, out.take(limit), null, known)
    }

    // ------------------------------------------------------------------ Avro

    fun avro(term: String): AvroCandidates? = avro()?.suggest(term, history::selection)

    /** Remembers the user's pick for an Avro word, so it is chosen first next time. */
    fun recordAvroSelection(term: String, chosen: String) {
        val a = avro() ?: return
        history.recordSelection(a.selectionKey(term), a.selectionValue(term, chosen))
    }

    // ------------------------------------------------------------------ next word

    fun nextWords(language: Language, previous: String?, limit: Int = 3): List<String> =
        if (previous.isNullOrBlank()) emptyList() else history.nextWords(language, previous, limit)

    companion object {
        /** See [autoCorrectMargin]: in rank units (frequency 0–255 minus weighted cost). */
        internal const val AUTOCORRECT_MARGIN = 10f

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
