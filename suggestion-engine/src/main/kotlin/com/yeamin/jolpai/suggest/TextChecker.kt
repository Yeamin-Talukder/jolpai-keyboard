package com.yeamin.jolpai.suggest

/** What kind of mistake a [TextIssue] is. */
enum class IssueKind(val label: String) { SPELLING("Spelling"), PUNCTUATION("Punctuation"), GRAMMAR("Grammar") }

/**
 * One suggested correction in a piece of text: replace `text[start, end)` ([original]) with
 * [replacement]. [reason] is a short explanation for the user. [alternatives] are other likely
 * words (best first) the user can pick instead, for spelling: "takl" → talk, or tail / tall.
 */
data class TextIssue(
    val start: Int,
    val end: Int,
    val original: String,
    val replacement: String,
    val kind: IssueKind,
    val reason: String,
    val alternatives: List<String> = emptyList(),
) {
    /** [replacement] and [alternatives]: every fix the user may choose. */
    val choices: List<String> get() = listOf(replacement) + alternatives
}

/**
 * Offline spelling, punctuation and grammar checking for English and Bangla.
 *
 * Everything runs on the phone with the bundled word lists and hand-written rules; nothing is
 * sent anywhere. It only reports mistakes it is confident about (a misspelling is reported only
 * when a likely correction exists; grammar rules cover common, unambiguous errors), because a
 * checker that nags about correct text is worse than none.
 *
 * - Spelling: English words missing from the dictionary (and from the user's own words) with a
 *   close dictionary correction; Bangla words missing from the word list whose common
 *   look-alike spelling is in it (ি/ী, ু/ূ, শ/ষ/স, ন/ণ, জ/য, ড়/ঢ়/র …).
 * - Punctuation: spaces before punctuation, missing spaces after it, doubled marks, a Bangla
 *   sentence ended with "." instead of দাঁড়ি, a missing full stop at the end, extra spaces,
 *   missing apostrophes (dont → don't).
 * - Grammar (English): a/an, repeated words, "i" → "I", sentence capitals, "could of",
 *   "better then", "he don't", "its a", "your welcome", run-together words (alot, infront …).
 *
 * Names, acronyms, links, e-mail addresses, numbers and code-like words are left alone.
 */
class TextChecker(
    private val english: () -> EnglishDictionary?,
    private val bangla: () -> BanglaDictionary?,
    private val history: UserHistory?,
    private val costs: TypingCosts = TypingCosts.DEFAULT,
) {
    /**
     * Checks [text]; [ignored] holds originals the user chose to ignore. [endsAtFieldEnd]: the
     * text is the end of what was written (only then is a missing full stop reported).
     * Issues are in text order and never overlap.
     */
    fun check(text: String, ignored: Set<String> = emptySet(), endsAtFieldEnd: Boolean = true, limit: Int = MAX_ISSUES): List<TextIssue> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<TextIssue>()
        val protected = protectedRanges(text)
        punctuation(text, out)
        grammar(text, out)
        spelling(text, protected, out)
        val kept = ArrayList<TextIssue>()
        fun keep(issue: TextIssue) {
            if (issue.original == issue.replacement) return
            if (issue.original in ignored) return
            if (protected.any { it.first < issue.end && issue.start < it.last + 1 }) return
            if (kept.any { it.start < issue.end && issue.start < it.end }) return
            kept += issue
        }
        out.sortedWith(compareBy<TextIssue> { it.start }.thenBy { -it.end }).forEach(::keep)
        // The missing full stop covers the last word, so it comes last: a spelling or grammar fix
        // on that word wins ("i want to takl"), and the full stop is offered once it's fixed.
        if (endsAtFieldEnd) ArrayList<TextIssue>().also { endOfText(text, it) }.forEach(::keep)
        return kept.sortedBy { it.start }.take(limit)
    }

    // ------------------------------------------------------------------ spelling

    private fun spelling(text: String, protected: List<IntRange>, out: MutableList<TextIssue>) {
        var checkedUnknown = 0
        WORD.findAll(text).forEach { m ->
            val word = m.value
            if (protected.any { m.range.first in it }) return@forEach
            if (checkedUnknown >= MAX_UNKNOWN_WORDS) return@forEach
            val issue = if (isBangla(word)) banglaSpelling(text, word, m.range.first) else englishSpelling(text, word, m.range.first)
            if (issue != null) {
                out += issue
                checkedUnknown++
            }
        }
    }

    private fun englishSpelling(text: String, word: String, start: Int): TextIssue? {
        val dict = english() ?: return null
        if (word.length < 2 || word.any { it.isDigit() }) return null
        val lower = word.lowercase()
        // Names and acronyms: a capital inside the word, or a capital that doesn't start a sentence.
        if (word.drop(1).any { it.isUpperCase() }) return null
        if (word[0].isUpperCase() && !startsSentence(text, start)) return null
        val known = dict.lookup(word) != null || dict.lookup(lower) != null ||
            history?.isKnown(Language.ENGLISH, word, minCount = 1) == true ||
            history?.isKnown(Language.ENGLISH, lower, minCount = 1) == true
        if (known) return null
        dict.shortcuts[lower]?.let { fix ->
            val replacement = Suggester.applyCase(word, fix)
            val apostropheOnly = replacement.replace("'", "").equals(word, ignoreCase = true)
            return TextIssue(
                start, start + word.length, word, replacement,
                if (apostropheOnly) IssueKind.PUNCTUATION else IssueKind.SPELLING,
                if (apostropheOnly) "Missing apostrophe" else "Possible misspelling",
            )
        }
        if (word.length < 3) return null
        val maxCost = when {
            word.length <= 4 -> 1.0f
            word.length <= 7 -> 1.6f
            else -> 2.0f
        }
        val previous = previousWord(text, start)
        // Like the prediction strip: several close words, ranked by how likely the slip is and how
        // common the word is, plus what the user writes: a word they've typed after the previous
        // one ("to talk") and words they use often come first.
        val ranked = dict.corrections(word, CANDIDATES, maxCost, costs = costs)
            .sortedByDescending { c ->
                EnglishDictionary.rankCorrection(c.frequency, c.cost, costs) + contextBonus(Language.ENGLISH, previous, c.word)
            }
        val best = ranked.firstOrNull() ?: return null
        // A capitalised first word may be a name: only suggest a very likely fix.
        if (word[0].isUpperCase() && (best.cost > 1.0f || best.frequency < 60)) return null
        val choices = ranked.map { Suggester.applyCase(word, it.word) }.distinct()
        return TextIssue(
            start, start + word.length, word, choices.first(), IssueKind.SPELLING, "Possible misspelling",
            alternatives = choices.drop(1).take(MAX_ALTERNATIVES),
        )
    }

    /**
     * Extra rank for [candidate] from the user's own typing, on the 0–255 frequency scale: having
     * typed it after [previous] matters most, then how often they use it at all.
     */
    private fun contextBonus(language: Language, previous: String?, candidate: String): Float {
        val h = history ?: return 0f
        val pair = if (previous != null) h.bigramCount(language, previous, candidate) else 0
        return minOf(pair, 5) * PAIR_BONUS + minOf(h.count(language, candidate), 20) * USE_BONUS
    }

    /** The word just before [at] in the same sentence (only spaces between), or null. */
    private fun previousWord(text: String, at: Int): String? {
        var i = at - 1
        while (i >= 0 && text[i] == ' ') i--
        if (i < 0 || i == at - 1) return null
        val end = i + 1
        while (i >= 0 && (text[i].isLetter() || text[i] == '\'' || text[i] in 'ঀ'..'৿')) i--
        return text.substring(i + 1, end).takeIf { it.isNotEmpty() }
    }

    private fun banglaSpelling(text: String, word: String, start: Int): TextIssue? {
        val dict = bangla() ?: return null
        if (word.length < 3 || word.any { it in '০'..'৯' } || word in BANGLA_OK) return null
        val w = BanglaSpelling.normalize(word)
        if (history?.isKnown(Language.BANGLA, word, minCount = 1) == true || history?.isKnown(Language.BANGLA, w, minCount = 1) == true) return null
        if (BanglaSpelling.isKnown(w) { dict.contains(it) }) return null
        // Among close fixes: a word the user writes after the previous one wins, then a word they
        // write at all, then the more common word (মানষ → মানুষ, not মানস).
        val previous = previousWord(text, start)
        val fixes = BanglaSpelling.corrections(w, { dict.contains(it) }, 1 + MAX_ALTERNATIVES) {
            val pair = if (previous != null) history?.bigramCount(Language.BANGLA, previous, it) ?: 0 else 0
            minOf(pair, 5) * 5000 + (history?.count(Language.BANGLA, it) ?: 0) * 1000 + dict.frequency(it)
        }
        if (fixes.isEmpty()) return null
        return TextIssue(
            start, start + word.length, word, fixes.first(), IssueKind.SPELLING, "বানান ভুল হতে পারে",
            alternatives = fixes.drop(1),
        )
    }

    // ------------------------------------------------------------------ punctuation

    private fun punctuation(text: String, out: MutableList<TextIssue>) {
        // "word ," → "word,"
        SPACE_BEFORE_MARK.findAll(text).forEach { m ->
            val spaces = m.groups[1]!!
            val mark = m.groups[2]!!
            val after = text.getOrNull(mark.range.last + 1)
            if (mark.value == "." && after?.isDigit() == true) return@forEach // " .5"
            // "word ,next" → "word, next": the space moves to the other side in one fix.
            val spaceAfter = after != null && (after.isLetter() || after in 'ঀ'..'৿') &&
                (mark.value !in ".!?" || after.isUpperCase() || after in 'ঀ'..'৿')
            out += issue(
                spaces.range.first, mark.range.last + 1, text,
                if (spaceAfter) mark.value + " " else mark.value, IssueKind.PUNCTUATION,
                if (spaceAfter) "Space after “${mark.value}”, not before" else "No space before “${mark.value}”",
            )
        }
        // "word,next" → "word, next" (not 1,000 or 10:30)
        MISSING_SPACE_AFTER.findAll(text).forEach { m ->
            val mark = m.groups[1]!!
            val prev = text.getOrNull(mark.range.first - 1)
            val next = text.getOrNull(mark.range.last + 1)
            if (prev == null || next == null) return@forEach
            if (mark.value in ".!?" && !next.isUpperCase()) return@forEach // e.g. "e.g", "file.txt", "x.com"
            if (mark.value == "." && prev.isLetter() && text.getOrNull(mark.range.first - 2)?.let { !it.isLetter() } != false) return@forEach // "U.S"
            out += issue(mark.range.first, mark.range.last + 1, text, mark.value + " ", IssueKind.PUNCTUATION, "Add a space after “${mark.value}”")
        }
        // ",," → ","  "।।" → "।"  ".." → "." (but "..." stays)
        DOUBLED_MARK.findAll(text).forEach { m ->
            if (m.value == ".." && (text.getOrNull(m.range.last + 1) == '.' || text.getOrNull(m.range.first - 1) == '.')) return@forEach
            out += issue(m.range.first, m.range.last + 1, text, m.value.take(1), IssueKind.PUNCTUATION, "Repeated “${m.value.take(1)}”")
        }
        // A Bangla sentence ended with "." → "।" (not short abbreviations like ডা. or মো.)
        BANGLA_FULL_STOP.findAll(text).forEach { m ->
            val word = m.groups[1]!!
            if (word.value.count { it.isBanglaLetter() } <= 2) return@forEach
            val dot = m.groups[2]!!
            out += TextIssue(dot.range.first, dot.range.last + 1, ".", "।", IssueKind.PUNCTUATION, "বাংলা বাক্য দাঁড়ি (।) দিয়ে শেষ হয়")
        }
        // Two or more spaces between words.
        EXTRA_SPACES.findAll(text).forEach { m ->
            out += TextIssue(m.range.first, m.range.last + 1, m.value, " ", IssueKind.PUNCTUATION, "Extra space")
        }
    }

    /** The last sentence has no closing mark: suggest ".", "?" or "।". */
    private fun endOfText(text: String, out: MutableList<TextIssue>) {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty() || trimmed.last() in ".!?।…:;,)\"'”’" || trimmed.last().isDigit()) return
        val sentence = trimmed.substring(lastSentenceStart(trimmed))
        val words = WORD.findAll(sentence).toList()
        if (words.size < MIN_WORDS_FOR_FULL_STOP) return
        val last = words.last()
        if (sentence.length != last.range.last + 1) return // ends with an emoji, link, …
        val start = trimmed.length - sentence.length + last.range.first
        val bangla = isBangla(last.value)
        val question = !bangla && words.first().value.lowercase() in QUESTION_STARTS
        val mark = when {
            bangla -> "।"
            question -> "?"
            else -> "."
        }
        val reason = when {
            bangla -> "বাক্যের শেষে দাঁড়ি (।)"
            question -> "A question ends with “?”"
            else -> "End the sentence with a full stop"
        }
        out += TextIssue(start, start + last.value.length, last.value, last.value + mark, IssueKind.PUNCTUATION, reason)
    }

    // ------------------------------------------------------------------ grammar (English)

    private fun grammar(text: String, out: MutableList<TextIssue>) {
        // "i" on its own → "I"
        LONE_I.findAll(text).forEach { m ->
            out += TextIssue(m.range.first, m.range.last + 1, "i", "I", IssueKind.GRAMMAR, "“I” is always a capital")
        }
        // Sentence starts with a small letter.
        SENTENCE_START.findAll(text).forEach { m ->
            val w = m.groups[1]!!
            if (w.value == "i" || w.value.length < 2 && w.value != "a") return@forEach
            if (m.range.first > 0 && wordBefore(text, m.range.first + 1).lowercase() in ABBREVIATIONS) return@forEach
            if (w.value.any { it.isUpperCase() }) return@forEach
            out += TextIssue(w.range.first, w.range.last + 1, w.value, w.value.replaceFirstChar { it.uppercaseChar() }, IssueKind.GRAMMAR, "Start the sentence with a capital letter")
        }
        // "the the" (not "had had" / "that that", which can be right).
        REPEATED_WORD.findAll(text).forEach { m ->
            val w = m.groups[1]!!.value
            if (w.lowercase() in DOUBLE_OK || w.any { it.isDigit() }) return@forEach
            out += TextIssue(m.range.first, m.range.last + 1, m.value, w, IssueKind.GRAMMAR, if (isBangla(w)) "একই শব্দ দুবার" else "Repeated word")
        }
        // a/an
        ARTICLE.findAll(text).forEach { m ->
            val article = m.groups[1]!!
            val next = m.groups[2]!!.value
            if (next.length < 2 && next.lowercase() != "a") return@forEach
            if (next.length > 1 && next.all { it.isUpperCase() }) return@forEach // acronyms: an FBI, a URL
            val wantsAn = startsWithVowelSound(next)
            val isAn = article.value.equals("an", ignoreCase = true)
            if (wantsAn == isAn) return@forEach
            val fixed = if (wantsAn) "an" else "a"
            val cased = if (article.value[0].isUpperCase()) fixed.replaceFirstChar { it.uppercaseChar() } else fixed
            out += TextIssue(
                article.range.first, article.range.last + 1, article.value, cased, IssueKind.GRAMMAR,
                if (wantsAn) "Use “an” before a vowel sound" else "Use “a” before a consonant sound",
            )
        }
        PHRASES.forEach { (pattern, fix) ->
            pattern.findAll(text).forEach { m ->
                val replacement = matchCase(m.value, m.value.replace(pattern, fix.first))
                val group = if (m.groups.size > 1) m.groups[1]?.value?.lowercase() else null
                val reason = fix.second.replace("$1", group ?: "")
                out += TextIssue(m.range.first, m.range.last + 1, m.value, replacement, IssueKind.GRAMMAR, reason)
            }
        }
    }

    private fun startsWithVowelSound(word: String): Boolean {
        val w = word.lowercase()
        if (SILENT_H.any { w.startsWith(it) }) return true
        if (YOU_SOUND.any { w.startsWith(it) }) return false
        if (w.startsWith("one") || w.startsWith("once")) return false
        return w[0] in "aeiou"
    }

    // ------------------------------------------------------------------ helpers

    private fun issue(start: Int, end: Int, text: String, replacement: String, kind: IssueKind, reason: String) =
        TextIssue(start, end, text.substring(start, end), replacement, kind, reason)

    /** Links, e-mail addresses, @handles, #tags and anything with a digit in it are never "corrected". */
    private fun protectedRanges(text: String): List<IntRange> = PROTECTED.findAll(text).map { it.range }.toList()

    private fun startsSentence(text: String, at: Int): Boolean {
        var i = at - 1
        while (i >= 0 && text[i] == ' ') i--
        return i < 0 || text[i] in ".!?।\n"
    }

    /** The word that ends just before the "." at or before [at] (for "e.g. something"). */
    private fun wordBefore(text: String, at: Int): String {
        var end = at.coerceAtMost(text.length) - 1
        while (end >= 0 && !text[end].isLetter()) end--
        var start = end
        while (start >= 0 && (text[start].isLetter() || text[start] == '.')) start--
        return if (end < 0) "" else text.substring(start + 1, end + 1)
    }

    private fun lastSentenceStart(text: String): Int {
        var i = text.length - 1
        while (i >= 0 && text[i] !in ".!?।\n") i--
        return i + 1
    }

    private fun matchCase(original: String, fixed: String): String =
        if (original.firstOrNull()?.isUpperCase() == true) fixed.replaceFirstChar { it.uppercaseChar() } else fixed

    private fun isBangla(word: String) = word.any { it in 'ঀ'..'৿' }

    private fun Char.isBanglaLetter() = this in 'অ'..'হ' || this in 'ড়'..'য়'

    companion object {
        const val MAX_ISSUES = 30

        /** Words looked up for corrections per check (keeps a long text fast). */
        private const val MAX_UNKNOWN_WORDS = 25

        /** Close English words considered per misspelling, and other choices shown besides the fix. */
        private const val CANDIDATES = 6
        const val MAX_ALTERNATIVES = 2

        /**
         * Context bonuses on the 0–255 frequency scale (one unit of edit cost is worth 90): each
         * time the user typed the word after the previous one (up to 5), and each use (up to 20).
         * One "to talk" lifts talk (68) over tail (74) for "takl".
         */
        private const val PAIR_BONUS = 40f
        private const val USE_BONUS = 2f

        private const val MIN_WORDS_FOR_FULL_STOP = 4

        private val WORD = Regex("[A-Za-z]+(?:'[A-Za-z]+)?|[\\u0980-\\u09FF\\u200C\\u200D]+")
        private val PROTECTED = Regex("(?:https?://|www\\.)\\S+|\\S+@\\S+|[@#][\\w.]+|\\S*\\d\\S*|\\S+\\.(?:com|org|net|io|bd|in|gov|edu)\\S*")
        private val SPACE_BEFORE_MARK = Regex("(?<=\\S)( +)([,.!?;:।])(?![.])")
        private val MISSING_SPACE_AFTER = Regex("(?<=[A-Za-z\\u0980-\\u09FF])([,;!?।]|\\.)(?=[A-Za-z\\u0980-\\u09FF])")
        private val DOUBLED_MARK = Regex(",,+|।।+|;;+|(?<!\\.)\\.\\.(?!\\.)")
        private val BANGLA_FULL_STOP = Regex("([\\u0980-\\u09FF\\u200C\\u200D]+)(\\.)(?=\\s|$)")
        private val EXTRA_SPACES = Regex("(?<=\\S) {2,}(?=\\S)")
        private val LONE_I = Regex("(?<![A-Za-z'.])i(?![A-Za-z']|\\.[A-Za-z])")
        private val ABBREVIATIONS = setOf("e.g", "i.e", "etc", "vs", "mr", "mrs", "ms", "dr", "st", "no", "approx", "a.m", "p.m", "fig", "ca")
        private val SENTENCE_START = Regex("(?:^|[.!?]\\s+)([a-z][a-z']*)")
        private val REPEATED_WORD = Regex("(?<![\\w\\u0980-\\u09FF])([A-Za-z]+|[\\u0980-\\u09FF]+) \\1(?![\\w\\u0980-\\u09FF])", RegexOption.IGNORE_CASE)
        private val ARTICLE = Regex("(?<![A-Za-z])(a|an|A|An)\\s+([A-Za-z]+)")
        /** Common correct Bangla words the word list lacks (never "corrected"). */
        private val BANGLA_OK = setOf("কী", "কি", "নীল", "দিন", "দীন", "শুধু", "সূর্য")

        private val DOUBLE_OK = setOf("had", "that", "is", "do", "bye", "very", "so", "no", "ha", "haha", "knock", "yes", "really", "far", "more", "many", "again", "আরও", "খুব", "চলো", "যাও")
        private val SILENT_H = listOf("hour", "honest", "honor", "honour", "heir")
        private val YOU_SOUND = listOf("uni", "use", "usu", "uti", "ure", "uro", "eu", "ewe", "ufo", "uk")
        private val QUESTION_STARTS = setOf("what", "why", "how", "who", "whom", "whose", "where", "when", "which", "is", "are", "am", "do", "does", "did", "can", "could", "will", "would", "should", "shall", "may", "have", "has")

        private fun ci(p: String) = Regex(p, RegexOption.IGNORE_CASE)

        /** Common, unambiguous English mistakes: pattern → (replacement, reason). */
        private val PHRASES: List<Pair<Regex, Pair<String, String>>> = listOf(
            ci("\\b(could|would|should|must|might) of\\b") to ("$1 have" to "“$1 have”, not “$1 of”"),
            ci("\\b(better|worse|more|less|rather|other|bigger|smaller|faster|slower|higher|lower|greater|larger|older|younger|longer|shorter|easier|harder|cheaper|stronger) then\\b") to ("$1 than" to "Use “than” to compare"),
            ci("\\b(he|she|it) don't\\b") to ("$1 doesn't" to "“$1 doesn't”"),
            ci("\\bits (a|an|the|not|been|going|so|very|too|ok|okay|really|just|time|true|fine|good|great|about|over|all|better|worse|easy|hard|nice|late|raining|cold|hot)\\b") to ("it's $1" to "“it's” = it is"),
            ci("\\byour welcome\\b") to ("you're welcome" to "“you're” = you are"),
            ci("\\balot\\b") to ("a lot" to "Two words: “a lot”"),
            ci("\\binfront\\b") to ("in front" to "Two words: “in front”"),
            ci("\\beachother\\b") to ("each other" to "Two words: “each other”"),
            ci("\\bincase\\b") to ("in case" to "Two words: “in case”"),
            ci("\\batleast\\b") to ("at least" to "Two words: “at least”"),
            ci("\\baswell\\b") to ("as well" to "Two words: “as well”"),
            ci("\\bthankyou\\b") to ("thank you" to "Two words: “thank you”"),
        )
    }
}

/**
 * Bangla spelling for the checker.
 *
 * The bundled word list (~159k words) has most base words but misses many inflected forms
 * (ঢাকায়, স্কুলে, বইগুলো, ছেলেটা …), so a word counts as known when it, or its stem after
 * common endings are removed, is in the list. Text from other keyboards may store য় ড় ঢ় as
 * two characters (য + ়); words are normalised to the list's single-character form first.
 *
 * A correction is searched one edit away, most common mistakes first: look-alike letters
 * (ি/ী, শ/ষ/স …), a wrong / missing / extra vowel sign, a missing হসন্ত, ফলা or রেফ, any other
 * missing / extra / swapped character, then two look-alike swaps.
 */
object BanglaSpelling {
    /** Case endings and classifiers, longest first; stripped (up to twice) to find a known stem. */
    private val SUFFIXES = listOf(
        "গুলোকে", "গুলোর", "গুলোতে", "গুলো", "গুলি", "গুলা", "দেরকে", "দের", "খানা", "খানি", "টুকু",
        "টাকে", "টিকে", "টার", "টির", "টাই", "টা", "টি", "কেই", "কেও", "কে", "তেই", "তেও", "তে",
        "য়ের", "য়ে", "য়", "েরা", "ের", "রা", "রও", "র", "ে", "ও", "ই",
    ).map(::normalize)

    /** Vowel signs, most common first (\u09BE \u09C7 \u09BF \u09CB \u09C1 \u09C0 \u09C2 \u09C3 \u09C8 \u09CC): candidates are tried in this order. */
    private const val VOWEL_SIGNS = "\u09BE\u09C7\u09BF\u09CB\u09C1\u09C0\u09C2\u09C3\u09C8\u09CC"
    private const val HASANTA = '\u09CD'

    /** Clusters that are easy to forget: হসন্ত, য-ফলা, ব-ফলা, র-ফলা, রেফ, ং, ঁ. */
    private val CLUSTERS = listOf("\u09CD", "\u09CD\u09AF", "\u09CD\u09AC", "\u09CD\u09B0", "\u09B0\u09CD", "\u0982", "\u0981")

    /** য + ় → য়, ড + ় → ড়, ঢ + ় → ঢ় (the list uses the single characters). */
    fun normalize(word: String): String =
        word.replace("\u09AF\u09BC", "\u09DF").replace("\u09A1\u09BC", "\u09DC").replace("\u09A2\u09BC", "\u09DD")

    /** [word] (normalised) or its stem after removing common endings is a known word. */
    fun isKnown(word: String, known: (String) -> Boolean): Boolean {
        if (!wellFormed(word)) return false
        if (known(word)) return true
        // Spoken spellings: ছিলো / হলো for ছিল / হল.
        if (word.endsWith("\u09CB") && word.length > 2 && known(word.dropLast(1))) return true
        for (s in SUFFIXES) {
            if (word.length - s.length < 2 || !word.endsWith(s)) continue
            val stem = word.dropLast(s.length)
            if (!fits(stem, s)) continue
            if (known(stem)) return true
            for (s2 in SUFFIXES) {
                if (stem.length - s2.length < 2 || !stem.endsWith(s2)) continue
                val root = stem.dropLast(s2.length)
                if (fits(root, s2) && known(root)) return true
            }
        }
        return false
    }

    /**
     * An ending only attaches the way Bangla grammar allows: "ে / ের / েরা" after a consonant
     * (স্কুলে, বাংলাদেশের), "র / রা / য় / য়ে" after a vowel (ঢাকার, ঢাকায়). Without this,
     * বাংলাদেশর passed as বাংলাদেশ + র.
     */
    private fun fits(stem: String, suffix: String): Boolean {
        val last = stem.lastOrNull() ?: return false
        val first = suffix[0]
        return when {
            first in VOWEL_SIGNS -> isConsonant(last)
            first == '\u09B0' || first == '\u09DF' -> last in VOWEL_SIGNS || last in '\u0985'..'\u0994'
            else -> true
        }
    }

    /** No two vowel signs in a row, and none at the start or after an independent vowel. */
    private fun wellFormed(word: String): Boolean {
        for (i in word.indices) {
            if (word[i] !in VOWEL_SIGNS) continue
            val prev = word.getOrNull(i - 1) ?: return false
            if (prev in VOWEL_SIGNS || prev in '\u0985'..'\u0994') return false
        }
        return true
    }

    /**
     * The likely intended spelling of an unknown [word], or null when nothing close is known.
     * Among the closest candidates, a word the user has typed before ([usage] > 0) wins.
     */
    fun correction(word: String, known: (String) -> Boolean, usage: (String) -> Int = { 0 }): String? =
        corrections(word, known, 1, usage).firstOrNull()

    /**
     * Up to [limit] likely spellings of an unknown [word], best first (empty when nothing close
     * is known). Score = how likely this kind of slip is + how much the word is used (user
     * history and frequency). So ভূল → ভুল (a ূ/ু slip beats the more common ভাল), but
     * মানষ → মানুষ (common) rather than the rare look-alike মানস.
     */
    fun corrections(word: String, known: (String) -> Boolean, limit: Int, usage: (String) -> Int = { 0 }): List<String> {
        val near = closeCandidates(word, known).take(12).toList()
        if (near.isNotEmpty()) {
            return near.groupBy({ it.first }, { it.second })
                .map { (w, bonuses) -> w to bonuses.max() + usage(w) }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(limit)
        }
        return farCandidates(word, known).distinct().take(limit).toList()
    }

    /**
     * Look-alike letters, then vowel-sign mistakes (the most common slips), each with a bonus for
     * how likely that slip is: a vowel-sign look-alike (ি/ী, ু/ূ) most, a consonant look-alike
     * next, then a missing, wrong or extra vowel sign.
     */
    private fun closeCandidates(word: String, known: (String) -> Boolean): Sequence<Pair<String, Int>> = sequence {
        val ok = { w: String -> w != word && w.length >= 2 && isKnown(w, known) }
        for (p in word.indices) {
            for (alt in BanglaLookalikes.alternativesOf(word[p])) {
                val w = word.substring(0, p) + alt + word.substring(p + 1)
                if (ok(w)) yield(w to if (word[p] in VOWEL_SIGNS) BONUS_VOWEL_LOOKALIKE else BONUS_LOOKALIKE)
            }
        }
        for (i in word.indices) {
            if (word[i] in VOWEL_SIGNS) {
                for (v in VOWEL_SIGNS) if (v != word[i]) (word.substring(0, i) + v + word.substring(i + 1)).takeIf(ok)?.let { yield(it to BONUS_VOWEL_SIGN) }
            }
        }
        for (i in 1..word.length) {
            if (!isConsonant(word[i - 1])) continue
            for (v in VOWEL_SIGNS) (word.substring(0, i) + v + word.substring(i)).takeIf(ok)?.let { yield(it to BONUS_VOWEL_SIGN) }
        }
        for (i in word.indices) {
            if (word[i] in VOWEL_SIGNS || word[i] == HASANTA || word[i] == '\u0982' || word[i] == '\u0981') {
                word.removeRange(i, i + 1).takeIf(ok)?.let { yield(it to BONUS_VOWEL_SIGN) }
            }
        }
    }

    // Bonuses on the 0–255 frequency scale (user history adds 1000 per use, so it always wins).
    private const val BONUS_VOWEL_LOOKALIKE = 80
    private const val BONUS_LOOKALIKE = 20
    private const val BONUS_VOWEL_SIGN = 10

    /** Rarer slips: a forgotten হসন্ত / ফলা / রেফ, any missing, extra or swapped character, two look-alikes. */
    private fun farCandidates(word: String, known: (String) -> Boolean): Sequence<String> = sequence {
        val ok = { w: String -> w != word && w.length >= 2 && isKnown(w, known) }
        for (i in 1 until word.length) {
            for (c in CLUSTERS) (word.substring(0, i) + c + word.substring(i)).takeIf(ok)?.let { yield(it) }
        }
        for (i in word.indices) word.removeRange(i, i + 1).takeIf(ok)?.let { yield(it) }
        for (i in 0 until word.length - 1) {
            val chars = word.toCharArray()
            val t = chars[i]; chars[i] = chars[i + 1]; chars[i + 1] = t
            String(chars).takeIf(ok)?.let { yield(it) }
        }
        yieldAll(BanglaLookalikes.doubleSwaps(word).filter(ok))
    }

    private fun isConsonant(c: Char) = c in '\u0995'..'\u09B9' || c in '\u09DC'..'\u09DF' || c == '\u09CE'
}

/** Letters and signs that sound alike and are easily swapped in Bangla. */
object BanglaLookalikes {
    private val groups: List<String> = listOf(
        "িী", "ুূ", "শষস", "নণ", "জয", "ড়রঢ়", "তৎ", "ংঙ", "ঈই", "উঊ", "ছচ",
        // Unaspirated / aspirated pairs (ধন্যবাধ → ধন্যবাদ).
        "দধ", "তথ", "বভ", "পফ", "কখ", "গঘ", "জঝ", "টঠ", "ডঢ",
    )
    private val alternatives: Map<Char, String> = buildMap {
        groups.forEach { g -> g.forEach { c -> put(c, (get(c) ?: "") + g.replace(c.toString(), "")) } }
    }

    /** The letters [c] is easily confused with ("" if none). */
    fun alternativesOf(c: Char): String = alternatives[c] ?: ""

    fun singleSwaps(word: String): Sequence<String> = sequence {
        for (p in word.indices) {
            val alts = alternatives[word[p]] ?: continue
            for (alt in alts) yield(word.substring(0, p) + alt + word.substring(p + 1))
        }
    }

    fun doubleSwaps(word: String): Sequence<String> = sequence {
        val positions = word.indices.filter { word[it] in alternatives }
        if (positions.size > 12) return@sequence
        for (i in positions.indices) for (j in i + 1 until positions.size) {
            val p = positions[i]
            val q = positions[j]
            for (a in alternatives.getValue(word[p])) for (b in alternatives.getValue(word[q])) {
                val chars = word.toCharArray()
                chars[p] = a
                chars[q] = b
                yield(String(chars))
            }
        }
    }

    /** One swap, then two (kept for callers that only want look-alike fixes). */
    fun correction(word: String, known: (String) -> Boolean): String? =
        singleSwaps(word).firstOrNull(known) ?: doubleSwaps(word).firstOrNull(known)
}
