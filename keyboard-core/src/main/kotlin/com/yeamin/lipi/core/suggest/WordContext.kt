package com.yeamin.lipi.core.suggest

import com.yeamin.lipi.bangla.BanglaChars

/**
 * The word being typed ([current], directly before the cursor) and the word before it
 * ([previous], null at the start of a sentence), read from the text before the cursor.
 */
data class WordContext(val previous: String?, val current: String) {
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

            // Skip spaces back to the previous word; any other character breaks the context.
            var i = start
            while (i > 0 && before[i - 1] == ' ') i--
            if (i == start && start > 0 && current.isNotEmpty()) return WordContext(null, current)
            if (i == 0 || SENTENCE_END.indexOf(before[i - 1]) >= 0 || !isWordChar(before[i - 1])) {
                return WordContext(null, current)
            }
            end = i
            while (i > 0 && isWordChar(before[i - 1])) i--
            return WordContext(before.subSequence(i, end).toString(), current)
        }
    }
}
