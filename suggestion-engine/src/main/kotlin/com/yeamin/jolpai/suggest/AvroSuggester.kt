package com.yeamin.jolpai.suggest

import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Avro candidates for one Roman word. [selected] is the one to show and commit by default. */
data class AvroCandidates(
    val candidates: List<String>,
    val selected: Int,
    /** The plain rule-based conversion of the whole input. */
    val phonetic: String,
) {
    val selectedText: String get() = candidates.getOrElse(selected) { phonetic }
}

/**
 * Builds the candidate list for Avro typing, following OpenBangla's `riti` (MPL-2.0):
 *
 * 1. Split the input into leading punctuation, the word, and trailing punctuation.
 * 2. Candidates for the word: an autocorrect entry first (riti's list of common Roman
 *    misspellings), then dictionary words ([AvroDictionarySearch]) ranked by edit distance
 *    to the rule-based conversion, then dictionary words with a suffix (গুলো, ের, …) attached.
 * 3. Then the rule-based conversion itself, and finally the input as typed (for English words).
 * 4. The default selection is the candidate the user picked last time for this word.
 *
 * Not thread-safe (it keeps a small cache); use it from one thread.
 */
class AvroSuggester(
    private val phonetic: PhoneticEngine,
    private val search: AvroDictionarySearch,
    /** Roman suffix → Bangla suffix, e.g. "gulo" → "গুলো". */
    private val suffixes: Map<String, String>,
    /** Roman word → corrected Roman word, e.g. common misspellings. */
    private val autocorrect: Map<String, String>,
    /** How common a Bangla word is (0–255): breaks ties between equally close candidates. */
    private val frequency: (String) -> Int = { 0 },
) {
    private sealed class Ranked(val text: String) {
        class First(text: String) : Ranked(text)
        class Other(text: String, val distance: Int) : Ranked(text)
        class Last(text: String, val rank: Int) : Ranked(text)

        fun withText(t: String): Ranked = when (this) {
            is First -> First(t)
            is Other -> Other(t, distance)
            is Last -> Last(t, rank)
        }

        val order: Int get() = when (this) { is First -> 0; is Other -> 1; is Last -> 2 }
        val value: Int get() = when (this) { is First -> 0; is Other -> distance; is Last -> rank }
    }

    /** Dictionary candidates per Roman word, so suffix handling can reuse the base word's list. */
    /** How many dictionary words the Roman spelling [roman] can lead to (for predictive touch). */
    fun matchCount(roman: String): Int = if (roman.isEmpty()) 0 else search.search(roman).size

    private val cache = object : LinkedHashMap<String, List<Ranked>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Ranked>>?) = size > 256
    }

    /**
     * @param selections the user's previous pick for a Roman word, if any.
     * @param includeEnglish also offer the input exactly as typed.
     * @param context how likely a Bangla word is in the sentence being written (natural-log
     *   probability from the next-word model), or null without one: dictionary words are then
     *   ordered by closeness to the conversion and fit to the sentence together (ভাত after আমি,
     *   before খাই), [CONTEXT_POINTS] points per log unit against 10 per letter of difference.
     */
    fun suggest(
        term: String,
        selections: (String) -> String?,
        includeEnglish: Boolean = true,
        context: ((String) -> Float?)? = null,
    ): AvroCandidates {
        val split = SplitTerm.of(term)
        val preceding = phonetic.convert(split.preceding)
        val trailing = phonetic.convert(split.trailing)
        val wordPhonetic = if (split.word.isEmpty()) "" else phonetic.convert(split.word)

        val list = ArrayList<Ranked>()
        if (split.word.isNotEmpty()) {
            for (r in dictionaryWithSuffixes(split.word, wordPhonetic)) pushChecked(list, r)
            pushChecked(list, Ranked.Last(wordPhonetic, 2))
        }
        val wrapped = if (preceding.isEmpty() && trailing.isEmpty()) list
        else ArrayList(list.map { it.withText(preceding + it.text + trailing) })

        pushChecked(wrapped, Ranked.Last(preceding + wordPhonetic + trailing, 2))
        if (includeEnglish && term != split.preceding) wrapped += Ranked.Last(term, 3)

        // Equally close candidates: the more common word first (from the frequency list).
        val sorted = if (context == null) {
            wrapped.sortedWith(compareBy<Ranked> { it.order }.thenBy { it.value }.thenByDescending { frequency(it.text) })
        } else {
            val fit = HashMap<String, Float>()
            fun fit(r: Ranked): Float = fit.getOrPut(r.text) {
                val core = r.text.removePrefix(preceding).removeSuffix(trailing)
                context(core) ?: UNKNOWN_FIT
            }
            wrapped.sortedWith(
                compareBy<Ranked> { it.order }
                    .thenBy { if (it is Ranked.Other) it.distance - CONTEXT_POINTS * fit(it) else it.value.toFloat() }
                    .thenByDescending { frequency(it.text) },
            )
        }
        val candidates = sorted.map { it.text }.distinct()

        val previous = previousSelection(split.word, selections)
        val selectedText = previous?.let { preceding + it + trailing }
        val selected = selectedText?.let { candidates.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        return AvroCandidates(candidates, selected, preceding + wordPhonetic + trailing)
    }

    /** The part of [term] that selections are stored under (the word without punctuation). */
    fun selectionKey(term: String): String = SplitTerm.of(term).word

    /** Strips the leading/trailing punctuation that [suggest] added around a candidate. */
    fun selectionValue(term: String, candidate: String): String {
        val split = SplitTerm.of(term)
        val p = phonetic.convert(split.preceding)
        val t = phonetic.convert(split.trailing)
        return candidate.removePrefix(p).removeSuffix(t)
    }

    private fun dictionaryCandidates(word: String, wordPhonetic: String): List<Ranked> =
        cache.getOrPut(word) {
            val out = ArrayList<Ranked>()
            autocorrect[word]?.let { out += Ranked.First(phonetic.convert(it)) }
            search.search(word).sorted().forEach { out += Ranked.Other(it, levenshtein(wordPhonetic, it) * 10) }
            out
        }

    /** Dictionary candidates for [word], plus base-word candidates with a matching suffix added. */
    private fun dictionaryWithSuffixes(word: String, wordPhonetic: String): List<Ranked> {
        val list = ArrayList(dictionaryCandidates(word, wordPhonetic))
        if (word.length > 2) {
            for (i in 1 until word.length) {
                val suffixKey = word.substring(i)
                val suffix = suffixes[suffixKey] ?: continue
                val base = word.substring(0, i)
                for (b in dictionaryCandidates(base, phonetic.convert(base))) {
                    list += b.withText(joinSuffix(b.text, suffix))
                }
            }
        }
        return list
    }

    private fun previousSelection(word: String, selections: (String) -> String?): String? {
        selections(word)?.let { return it }
        if (word.length < 2) return null
        for (i in 1 until word.length) {
            val test = word.substring(word.length - i)
            val suffix = suffixes[test] ?: continue
            val base = selections(word.substring(0, word.length - test.length)) ?: continue
            return joinSuffix(base, suffix)
        }
        return null
    }

    private fun pushChecked(list: MutableList<Ranked>, item: Ranked) {
        if (list.none { it.text == item.text }) list += item
    }

    companion object {
        /** See [suggest]: points per natural-log unit of in-context probability. */
        const val CONTEXT_POINTS = 3f

        /** Log-probability given to a dictionary word the model does not know. */
        private const val UNKNOWN_FIT = -12f

        /** Joins a base word and a Bangla suffix the way riti does (য় between vowel and kar, ৎ→ত, ং→ঙ). */
        fun joinSuffix(base: String, suffix: String): String {
            if (base.isEmpty() || suffix.isEmpty()) return base + suffix
            val last = base.last()
            val first = suffix.first()
            return when {
                isVowelOrKar(last) && isKar(first) -> base + "য়" + suffix
                last == 'ৎ' -> base.dropLast(1) + "ত" + suffix
                last == 'ং' -> base.dropLast(1) + "ঙ" + suffix
                else -> base + suffix
            }
        }

        private const val VOWELS_AND_KARS =
            "অআইঈউঊঋএঐওঔঌৡ" +
                "ািীুূৃেৈোৌ"
        private const val KARS = "ািীুূৃেৈোৌৄ"

        private fun isVowelOrKar(c: Char) = VOWELS_AND_KARS.indexOf(c) >= 0
        private fun isKar(c: Char) = KARS.indexOf(c) >= 0

        fun load(phonetic: PhoneticEngine, search: AvroDictionarySearch, frequency: (String) -> Int = { 0 }): AvroSuggester =
            AvroSuggester(
                phonetic,
                search,
                readJsonMap("/bn/riti-suffix.json"),
                readJsonMap("/bn/riti-autocorrect.json"),
                frequency,
            )

        private fun readJsonMap(path: String): Map<String, String> =
            Json.parseToJsonElement(readResourceText(path)).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }
}

/**
 * Splits Avro input into leading punctuation, the word, and trailing punctuation, as riti's
 * `SplittedString::split` does. A backtick escapes a trailing colon.
 */
internal data class SplitTerm(val preceding: String, val word: String, val trailing: String) {
    companion object {
        private const val META = "-]~!@#%&*()_=+[{}'\";<>/?|.,।"

        fun of(input: String): SplitTerm {
            val first = input.indexOfFirst { META.indexOf(it) < 0 }
            if (first < 0) return SplitTerm(input, "", "")
            val preceding = input.substring(0, first)
            val rest = input.substring(first)
            var escape = false
            var last = rest.length
            for (i in rest.indices.reversed()) {
                val c = rest[i]
                if (!escape && c == '`') {
                    escape = true
                } else if ((escape && c == ':') || META.indexOf(c) >= 0) {
                    escape = false
                    last = i
                } else {
                    break
                }
            }
            return SplitTerm(preceding, rest.substring(0, last), rest.substring(last))
        }
    }
}
