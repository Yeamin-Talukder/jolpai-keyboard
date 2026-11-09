package com.yeamin.jolpai.core.suggest

import com.yeamin.jolpai.bangla.BanglaChars

/**
 * The word being typed ([current], directly before the cursor) and the word before it
 * ([previous], null at the start of a sentence), read from the text before the cursor.
 * [sentence] holds the words of the sentence before [current], oldest first (for the next-word
 * model); punctuation inside the sentence is skipped, a sentence end (. ! ? । line break) starts
 * it afresh. It is limited to what was read, so very long sentences give their last words only.
 */
data class WordContext(val previous: String?, val current: String, val sentence: List<String> = emptyList()) {
    companion object {
        val EMPTY = WordContext(null, "")

        private const val SENTENCE_END = ".!?।\n"

        /** Reads the context for English (Latin letters and apostrophes form words). */
        fun english(before: CharSequence): WordContext = parse(before) { it.isLetter() && it.code < 0x0980 || it == '\'' || it == '’' }

        /** Reads the context for Bangla (letters, signs and joiners of the Bangla block form words). */
        fun bangla(before: CharSequence): WordContext =
            parse(before) { (BanglaChars.isBangla(it) && it != '।' && it != '॥' && it !in '০'..'৯') || it == BanglaChars.ZWNJ || it == BanglaChars.ZWJ }

        private inline fun parse(before: CharSequence, isWordChar: (Char) -> Boolean): WordContext {
            var end = before.length
            var start = end
            while (start > 0 && isWordChar(before[start - 1])) start--
            val current = before.subSequence(start, end).toString()
            val sentence = sentenceWords(before, start, isWordChar)

            // Skip spaces back to the previous word; any other character breaks the context.
            var i = start
            while (i > 0 && before[i - 1] == ' ') i--
            if (i == start && start > 0 && current.isNotEmpty()) return WordContext(null, current, sentence)
            if (i == 0 || SENTENCE_END.indexOf(before[i - 1]) >= 0 || !isWordChar(before[i - 1])) {
                return WordContext(null, current, sentence)
            }
            end = i
            while (i > 0 && isWordChar(before[i - 1])) i--
            return WordContext(before.subSequence(i, end).toString(), current, sentence)
        }

        /** The words between the last sentence end before [end] and [end]. */
        private inline fun sentenceWords(before: CharSequence, end: Int, isWordChar: (Char) -> Boolean): List<String> {
            var from = end
            while (from > 0 && SENTENCE_END.indexOf(before[from - 1]) < 0) from--
            // A word cut off at the start of what was read is not a whole word.
            val cut = from == 0 && before.length >= READ_LIMIT_HINT
            val words = ArrayList<String>()
            var i = from
            while (i < end) {
                if (!isWordChar(before[i])) { i++; continue }
                val s = i
                while (i < end && isWordChar(before[i])) i++
                if (!(cut && s == 0)) words += before.subSequence(s, i).toString()
            }
            return words
        }

        /** When at least this much text was read, its first characters may start mid-word. */
        private const val READ_LIMIT_HINT = 100
    }
}
