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
    /** Offer Banglish words (Bangla in English letters) on the English layout. */
    val banglish: Boolean = true,
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
    /** Banglish spellings (Bangla in English letters), offered on the English layout; null until loaded. */
    private val banglish: () -> BanglishWords? = { null },
    /** The Banglish next-word model (trained on real Banglish; null until loaded or if missing). */
    banglishModel: () -> NextWordModel? = { null },
) {
    /** The next-word model with its caches, shared by suggestions, touch and the checker. */
    val lm = LanguageModel(model)

    /** The Bangla next-word model with its caches (Avro, জাতীয়, প্রভাত). */
    val lmBangla = LanguageModel(banglaModel)

    /** The Banglish next-word model with its caches (Banglish on the English layout). */
    val lmBanglish = LanguageModel(banglishModel)

    /** Next-character probabilities for predictive touch targets. */
    val nextChars = NextCharPredictor(english, bangla, avro, history, lm)

    /** Recognises Bangla typed in English letters (kept away from English autocorrect and the checker). */
    val romanBangla = RomanBangla(english, bangla, avro, banglish)

    /** Offline spelling, punctuation and grammar checking (the ✓ Check tool). */
    val checker = TextChecker(english, bangla, history, costs, lm, romanBangla)

    // ------------------------------------------------------------------ English

    fun english(typed: String, options: SuggestOptions = SuggestOptions()): WordSuggestions {
        val dict = english() ?: return WordSuggestions(typed, emptyList(), null, false)
        if (typed.isEmpty()) return WordSuggestions.EMPTY

        val lower = typed.lowercase()
        val entry = dict.lookup(typed)
        // Banglish: words that sound like [typed] in the Banglish list, and whether the sentence is Banglish.
        val bw = if (options.banglish) banglish() else null
        val banglishHere = bw != null && isBanglishSentence(options.context)
        // In a Banglish sentence the Bangla model reads it (as Bangla) and ranks more Banglish words.
        val pBanglish = if (bw != null && banglishHere) banglishGuess(bw, options.context, options.useModel) else null
        val banglishFound = if (bw != null && lower.all { it in 'a'..'z' }) {
            bw.completions(lower, if (pBanglish != null) BANGLISH_COMPLETIONS * 3 else BANGLISH_COMPLETIONS, allowOffensive = !options.blockOffensive)
        } else emptyList()
        val banglishWord = bw?.lookup(lower)?.takeIf { (entry == null || entry.frequency < RomanBangla.RARE_ENGLISH) && (banglishHere || typed.length >= RomanBangla.MIN_LONE_LENGTH && it.frequency >= LONE_BANGLISH) }
        val known = entry != null || banglishWord != null || history.isKnown(Language.ENGLISH, typed)
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
            // In a Banglish sentence a word that may be Banglish isn't treated as an English typo:
            // English fixes rank by frequency with everything else instead of first.
            val fixesFirst = !(banglishHere && banglishFound.isNotEmpty())
            corrections.forEachIndexed { i, c -> add(c.word, if (fixesFirst) 600f - i + rank(c, p) / 1000f else rank(c, p) - BANGLISH_FIX_PENALTY) }
        }

        // Banglish words, in the spelling the user writes them (valo or bhalo). Ranked with English
        // words in a Banglish sentence, below them in an English one.
        if (bw != null && banglishFound.isNotEmpty()) {
            val penalty = when {
                banglishHere -> -BANGLISH_SENTENCE_BONUS
                options.context.isEmpty() -> BANGLISH_NEUTRAL_PENALTY
                else -> BANGLISH_ENGLISH_PENALTY
            }
            // The user's own Banglish words that sound like this (typed "valob", writes bhalobashi),
            // by their folded form: they replace the list's spelling of the same word.
            val key = BanglishFold.fold(lower)
            val mine = HashMap<String, String>()
            history.wordsMatching(Language.ENGLISH, BANGLISH_COMPLETIONS * 2) { w ->
                BanglishFold.fold(w).startsWith(key) && dict.lookup(w).let { it == null || it.frequency < RomanBangla.RARE_ENGLISH }
            }.forEach { (w, _) -> mine.putIfAbsent(BanglishFold.fold(w), w) }
            banglishFound.forEach { e ->
                val shown = mine[BanglishFold.fold(e.spelling)] ?: bw.bestSpelling(e) { history.count(Language.ENGLISH, it) }
                add(shown, blendBanglish(e, pBanglish) - penalty + 25f * ln(1f + history.count(Language.ENGLISH, shown)))
            }
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
            // Bangla in English letters ("tumi kemon acho"): never turned into English words.
            isRomanBanglaHere(typed, options.context) -> null
            banglishWord != null -> null
            // Possibly a Banglish word still being typed, in a Banglish sentence.
            banglishHere && banglishFound.isNotEmpty() -> null
            // Known word in the wrong case, e.g. "i" → "I".
            entry != null -> applyCase(typed, entry.word).takeIf { it != typed && typed == lower && entry.word != lower }
            known -> null
            correction != null && isConfident(typed, corrections, p) -> applyCase(typed, correction.word)
            else -> null
        }
        return WordSuggestions(typed, candidates, autoCorrect, known)
    }

    /**
     * [typed] is Bangla in English letters: it reads as a common Bangla word and is long enough
     * not to be a short English slip (se → সে), or the sentence already has Roman Bangla in it.
     */
    private fun isRomanBanglaHere(typed: String, context: List<String>): Boolean {
        if (!romanBangla.isRomanBangla(typed)) return false
        return typed.length >= RomanBangla.MIN_LONE_LENGTH || context.takeLast(4).any { romanBangla.isRomanBangla(it) }
    }

    /**
     * The last few words of the sentence include Banglish: a word the Avro search reads as common
     * Bangla ([RomanBangla]) or a common Banglish spelling that isn't a common English word
     * (kmn, tmr, valo).
     */
    private fun isBanglishSentence(context: List<String>): Boolean = context.takeLast(4).any(romanBangla::isRomanBangla)

    /** [word] is Banglish (for callers outside suggestions, e.g. next words): see [RomanBangla.isRomanBangla]. */
    fun isBanglish(word: String): Boolean = romanBangla.isRomanBangla(word)

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
        // জাতীয় types keys as pressed (ো as ে + া): look words up in the list's spelling.
        val key = BanglaSpelling.normalize(typed)
        val known = dict.contains(key) || history.isKnown(Language.BANGLA, key) || history.isKnown(Language.BANGLA, typed)
        val p = if (options.useModel) lmBangla.predict(options.context.map(BanglaNextWords::normalize)) else null
        val candidates: List<String>
        if (p == null) {
            val out = LinkedHashSet<String>()
            history.wordsWithPrefix(Language.BANGLA, key, limit).forEach { out += it }
            dict.completions(key, limit * 2).forEach { out += it }
            out.remove(typed)
            out.remove(key)
            candidates = out.take(limit)
        } else {
            // With the model: the user's words first, then list completions and words the sentence
            // makes likely, ranked by frequency blended with the model (as for English).
            val scores = LinkedHashMap<String, Float>()
            history.wordsWithPrefix(Language.BANGLA, key, limit).forEach { scores[it] = USER_WORD + history.count(Language.BANGLA, it) }
            val fromList = dict.completions(key, limit * 4)
            val fromModel = p.top(MODEL_COMPLETIONS, key) { dict.contains(it) || history.isKnown(Language.BANGLA, it, minCount = 1) }.map { it.first }
            (fromList + fromModel).forEach { w -> if (w != key && w !in scores) scores[w] = blendBangla(w, dict.frequency(w), p) }
            scores.remove(typed)
            scores.remove(key)
            candidates = scores.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
        }
        // No word starts like this: it is probably misspelt (পরিক্ষা, মুহুর্ত). Offer the checker's
        // fixes first (never applied by themselves: Bangla has no autocorrect).
        val fixes = if (!known && candidates.isEmpty()) banglaFixes(dict, key, options.context.lastOrNull(), limit) else emptyList()
        return WordSuggestions(typed, (fixes + candidates).distinct().take(limit), null, known)
    }

    /** Spelling fixes for an unknown Bangla word, as the checker finds them (ranked by the user's words, then frequency). */
    private fun banglaFixes(dict: BanglaDictionary, word: String, previous: String?, limit: Int): List<String> {
        if (word.length < 3 || BanglaSpelling.isKnown(word) { dict.contains(it) }) return emptyList()
        return BanglaSpelling.corrections(word, { dict.contains(it) }, limit) {
            val pair = if (previous != null) history.bigramCount(Language.BANGLA, previous, it) else 0
            minOf(pair, 5) * 5000 + history.count(Language.BANGLA, it) * 1000 + dict.frequency(it)
        }
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
        // Words as the model reads them (য় ড় ঢ় as single characters, no ZWNJ/ZWJ), as for জাতীয়.
        val p = if (options.useModel) lmBangla.predict(options.context.map(BanglaNextWords::normalize)) else null
        return a.suggest(term, history::selection, context = p?.let { pr -> { w: String -> pr.logProb(BanglaNextWords.normalize(w)) } })
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
            val p = if (options.useModel) lmBangla.predict(options.context.map(BanglaNextWords::normalize)) else null
            return banglaNextWords(previous2, previous, learnedScores, limit, p)
        }
        val learned = learnedScores.map { it.first }
        val dict = english()
        // After a Banglish word the English model only guesses English: the Bangla model reads the
        // sentence as Bangla instead, and its guesses are offered in Banglish.
        val afterBanglish = options.banglish && previous != null && isBanglish(previous)
        if (afterBanglish && language == Language.ENGLISH) {
            banglish()?.let { bw -> return banglishNextWords(bw, learnedScores, options, limit, capitalize) }
        }
        val p = if (language == Language.ENGLISH && options.useModel && !afterBanglish) lm.predict(options.context) else null
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

    // ------------------------------------------------------------------ Banglish with a model

    /**
     * What a model expects next in a Banglish sentence, in Banglish words: [logProb] of a Banglish
     * spelling, and the [top] guesses as Banglish spellings with their log-probabilities.
     */
    private class BanglishGuess(
        val logProb: (String) -> Float?,
        val top: (Int, (String) -> Boolean) -> List<Pair<String, Float>>,
    )

    /**
     * The Banglish model when it is loaded (it reads Banglish as written, English words and
     * shorthand included), else the Bangla model reading the sentence turned into Bangla. Null
     * without either, or with predictions off.
     */
    private fun banglishGuess(bw: BanglishWords, context: List<String>, useModel: Boolean): BanglishGuess? {
        if (!useModel) return null
        val direct = lmBanglish.loaded
        if (direct != null && banglishSource != BanglishSource.VIA_BANGLA) {
            val p = lmBanglish.predict(context.map { banglishToken(direct, it) }) ?: return null
            return BanglishGuess(
                logProb = { w -> p.logProb(banglishToken(direct, w)) },
                top = { k, accept -> p.top(k, accept = accept) },
            )
        }
        val p = lmBangla.predict(banglishContext(bw, context)) ?: return null
        return BanglishGuess(
            logProb = { w -> banglaFor(bw, w)?.let { p.logProb(it) } },
            top = { k, accept ->
                p.top(k) { b -> bw.forBangla(b)?.let { accept(it.spelling) } == true }.map { (b, lp) -> bw.forBangla(b)!!.spelling to lp }
            },
        )
    }

    /** Which model reads Banglish (a setting only for measuring). */
    internal enum class BanglishSource { AUTO, VIA_BANGLA }
    internal var banglishSource = BanglishSource.AUTO

    /** The Banglish model's word for [word]: the model's spelling of the word that sounds the same (bhalo → valo). */
    private fun banglishToken(m: NextWordModel, word: String): String {
        val l = word.lowercase()
        val map = synchronized(this) {
            if (spellingModel !== m) {
                val byFold = HashMap<String, String>(m.words.size * 2)
                for (i in NextWordModel.FIRST_WORD until m.words.size) byFold.putIfAbsent(BanglishFold.fold(m.words[i]).ifEmpty { m.words[i] }, m.words[i])
                spellingByFold = byFold
                spellingModel = m
            }
            spellingByFold
        }
        return map[BanglishFold.fold(l).ifEmpty { l }] ?: l
    }
    private var spellingModel: NextWordModel? = null
    private var spellingByFold: Map<String, String> = emptyMap()

    /**
     * The sentence as the Bangla model reads it: each Banglish word as its Bangla word (ami → আমি,
     * from the list, else the Avro search's most common reading), other words as typed (the model
     * reads them as unknown).
     */
    private fun banglishContext(bw: BanglishWords, context: List<String>): List<String> = context.map { banglaFor(bw, it) ?: it }

    private fun banglaFor(bw: BanglishWords, word: String): String? {
        val l = word.lowercase()
        if (l.isEmpty() || !l.all { it in 'a'..'z' }) return null
        bw.banglaOf(l)?.let { return BanglaNextWords.normalize(it) }
        if (!romanBangla.isRomanBangla(l)) return null
        val dict = bangla() ?: return null
        return avro()?.matches(l)?.maxByOrNull { dict.frequency(it) }?.let(BanglaNextWords::normalize)
    }

    /** A Banglish word's frequency blended with how likely the model finds it here (as [blend]). */
    private fun blendBanglish(e: BanglishWords.Entry, g: BanglishGuess?): Float {
        val lp = g?.logProb?.invoke(e.spelling) ?: return e.frequency.toFloat()
        return (1 - modelWeight) * e.frequency + modelWeight * (FREQ_AT_CERTAIN + FREQ_PER_NAT * lp)
    }

    /** A swear word by the Banglish list (any spelling of it). */
    private fun isOffensiveBanglish(bw: BanglishWords, w: String): Boolean =
        bw.lookup(w)?.offensive == true || bw.variants(w).any { bw.lookup(it)?.offensive == true }

    /** How the user writes [spelling]: their most-typed spelling of the same word (valo or bhalo), else [spelling]. */
    private fun usersSpelling(bw: BanglishWords, spelling: String): String {
        var best = spelling
        var bestCount = history.count(Language.ENGLISH, spelling)
        for (v in bw.variants(spelling)) {
            val c = history.count(Language.ENGLISH, v)
            if (c > bestCount) { best = v; bestCount = c }
        }
        return best
    }

    /**
     * Next words after a Banglish word: the model's guesses (see [banglishGuess]) in the user's
     * spelling, with what the user typed there before boosted as for English. Without a model,
     * only what the user typed there.
     */
    private fun banglishNextWords(
        bw: BanglishWords,
        learned: List<Pair<String, Float>>,
        options: SuggestOptions,
        limit: Int,
        capitalize: Boolean,
    ): List<String> {
        val g = banglishGuess(bw, options.context, options.useModel)
        val scores = HashMap<String, Float>() // by sound, so valo and bhalo count once
        val forms = LinkedHashMap<String, String>()
        fun put(spelling: String, score: Float) {
            val k = BanglishFold.fold(spelling).ifEmpty { spelling }
            forms.putIfAbsent(k, spelling) // learned words come first: the user's spelling stays
            if (score > (scores[k] ?: Float.NEGATIVE_INFINITY)) scores[k] = score
        }
        learned.forEach { (w, s) -> put(w, (g?.logProb?.invoke(w) ?: LEARNED_FLOOR) + PAIR_BOOST * ln(1f + s)) }
        g?.top?.invoke(MODEL_NEXT_WORDS * 2) { w -> !options.blockOffensive || !isOffensiveBanglish(bw, w) }
            ?.forEach { (w, lp) -> put(usersSpelling(bw, w), lp) }
        return scores.entries.sortedByDescending { it.value }.take(limit).map { (k, _) ->
            val form = forms.getValue(k)
            if (capitalize) form.replaceFirstChar { it.uppercaseChar() } else form
        }
    }

    /** The word before [previous] in the sentence, when [context] ends with [previous]. */
    private fun secondPrevious(previous: String?, context: List<String>): String? {
        if (previous.isNullOrBlank() || context.size < 2 || !context.last().equals(previous, ignoreCase = true)) return null
        return context[context.size - 2]
    }

    companion object {
        /** Banglish words fetched per typed word (variants of one word count once). */
        private const val BANGLISH_COMPLETIONS = 4

        /** A Banglish word typed alone (no other in the sentence) is left alone from this frequency (onek, kintu, valo). */
        private const val LONE_BANGLISH = 140

        /** Banglish words' score against English ones: in a Banglish sentence, at its start, in an English one. */
        private const val BANGLISH_SENTENCE_BONUS = 20f
        private const val BANGLISH_NEUTRAL_PENALTY = 25f
        private const val BANGLISH_ENGLISH_PENALTY = 60f

        /** In a Banglish sentence, English fixes for a possibly Banglish word rank this far below their frequency. */
        private const val BANGLISH_FIX_PENALTY = 30f

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
