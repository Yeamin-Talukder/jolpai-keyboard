package com.yeamin.jolpai.bangla.phonetic

/**
 * A phonetic grammar in the format used by Avro Phonetic and its ports:
 * an ordered list of [PhoneticPattern]s plus the letter classes the rules refer to.
 *
 * The grammar is data. The rule table for Avro itself will be loaded from a resource file
 * (kept under its own licence, see docs/ARCHITECTURE.md §5.2); this engine only runs it.
 */
data class PhoneticGrammar(
    val patterns: List<PhoneticPattern>,
    /** Roman letters treated as vowels by `vowel` rules. */
    val vowels: String,
    /** Roman letters treated as consonants by `consonant` rules. */
    val consonants: String,
    /** Letters whose upper case means something different (t → ত, T → ট). Others are lower-cased. */
    val caseSensitive: String,
    /** Characters treated as digits by `number` rules. */
    val numbers: String = "1234567890",
)

/** Replace [find] with [replace], unless one of [rules] matches its context first. */
data class PhoneticPattern(
    val find: String,
    val replace: String,
    val rules: List<PhoneticRule> = emptyList(),
)

/** Use [replace] when every one of [matches] holds. */
data class PhoneticRule(
    val matches: List<PhoneticMatch>,
    val replace: String,
)

/**
 * One context condition.
 * [side] says whether it looks at the text before (PREFIX) or after (SUFFIX) the pattern.
 * [negative] inverts the result. [value] is only used by [Scope.EXACT].
 */
data class PhoneticMatch(
    val side: Side,
    val scope: Scope,
    val negative: Boolean = false,
    val value: String = "",
) {
    enum class Side { PREFIX, SUFFIX }
    enum class Scope { VOWEL, CONSONANT, PUNCTUATION, NUMBER, EXACT }
}

/**
 * Converts Roman input to Bangla with a [PhoneticGrammar].
 *
 * Algorithm (the one Avro Phonetic uses):
 * 1. Normalise case: letters not in [PhoneticGrammar.caseSensitive] are lower-cased.
 * 2. Scan left to right. At each position try the patterns that start with the current
 *    letter, longest `find` first.
 * 3. For the first pattern that matches, check its rules in order. The first rule whose
 *    conditions all hold gives the output; if none holds, the pattern's own `replace` is used.
 * 4. A letter no pattern matches is copied through unchanged.
 *
 * Context checks look at the *Roman* input, not the Bangla output, which is what lets
 * `a` become আ at the start of a word but া after a consonant.
 *
 * Thread-safe and allocation-light: patterns are indexed by first letter once, up front.
 */
class PhoneticEngine(private val grammar: PhoneticGrammar) {

    private val patternsByFirstChar: Map<Char, List<PhoneticPattern>> =
        grammar.patterns
            .filter { it.find.isNotEmpty() }
            .groupBy { it.find[0] }
            .mapValues { (_, list) -> list.sortedByDescending { it.find.length } }

    fun convert(input: String): String {
        if (input.isEmpty()) return input
        val text = normalise(input)
        val out = StringBuilder(text.length * 2)
        var cur = 0
        while (cur < text.length) {
            val candidates = patternsByFirstChar[text[cur]]
            var consumed = 0
            if (candidates != null) {
                for (pattern in candidates) {
                    val end = cur + pattern.find.length
                    if (end > text.length || !text.regionMatches(cur, pattern.find, 0, pattern.find.length)) continue
                    val rule = pattern.rules.firstOrNull { rule -> rule.matches.all { holds(it, text, cur, end) } }
                    out.append(rule?.replace ?: pattern.replace)
                    consumed = pattern.find.length
                    break
                }
            }
            if (consumed == 0) {
                out.append(text[cur])
                consumed = 1
            }
            cur += consumed
        }
        return out.toString()
    }

    private fun normalise(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            val lower = c.lowercaseChar()
            sb.append(if (grammar.caseSensitive.indexOf(lower) >= 0) c else lower)
        }
        return sb.toString()
    }

    private fun holds(match: PhoneticMatch, text: String, start: Int, end: Int): Boolean {
        val prefix = match.side == PhoneticMatch.Side.PREFIX
        val result = when (match.scope) {
            PhoneticMatch.Scope.EXACT -> {
                val s = if (prefix) start - match.value.length else end
                val e = if (prefix) start else end + match.value.length
                // `e < length` (not `<=`) matches the reference Avro parsers: an exact suffix that
                // ends right at the end of the input does not count. The Avro rule table is written
                // for this behaviour, and the rupantor corpus test depends on it.
                s >= 0 && e < text.length && text.regionMatches(s, match.value, 0, match.value.length)
            }
            else -> {
                val index = if (prefix) start - 1 else end
                val outside = index < 0 || index >= text.length
                when (match.scope) {
                    // The start or end of the input counts as punctuation.
                    PhoneticMatch.Scope.PUNCTUATION -> outside || isPunctuation(text[index])
                    PhoneticMatch.Scope.VOWEL -> !outside && isVowel(text[index])
                    PhoneticMatch.Scope.CONSONANT -> !outside && isConsonant(text[index])
                    PhoneticMatch.Scope.NUMBER -> !outside && grammar.numbers.indexOf(text[index]) >= 0
                    PhoneticMatch.Scope.EXACT -> error("handled above")
                }
            }
        }
        return result != match.negative
    }

    private fun isVowel(c: Char) = grammar.vowels.indexOf(c.lowercaseChar()) >= 0
    private fun isConsonant(c: Char) = grammar.consonants.indexOf(c.lowercaseChar()) >= 0
    private fun isPunctuation(c: Char) = !isVowel(c) && !isConsonant(c)
}
