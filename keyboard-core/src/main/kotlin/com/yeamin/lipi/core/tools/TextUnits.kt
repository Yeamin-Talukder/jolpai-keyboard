package com.yeamin.lipi.core.tools

/** Word boundaries for "delete word" and "select word" editing commands. */
object TextUnits {

    private fun isWordChar(c: Char): Boolean {
        if (Character.isLetterOrDigit(c) || c == '‌' || c == '‍') return true
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    /**
     * How many chars before the cursor make up the previous "word": trailing spaces, then
     * either a run of letters (Bangla signs and joiners count as letters) or a run of
     * punctuation.
     */
    fun wordLengthBefore(before: CharSequence): Int {
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i == 0) return before.length
        if (isWordChar(before[i - 1])) {
            while (i > 0 && isWordChar(before[i - 1])) i--
        } else {
            while (i > 0 && !before[i - 1].isWhitespace() && !isWordChar(before[i - 1])) i--
        }
        return before.length - i
    }

    /** The same, forwards from the cursor. */
    fun wordLengthAfter(after: CharSequence): Int {
        var i = 0
        while (i < after.length && after[i].isWhitespace()) i++
        if (i == after.length) return after.length
        if (isWordChar(after[i])) {
            while (i < after.length && isWordChar(after[i])) i++
        } else {
            while (i < after.length && !after[i].isWhitespace() && !isWordChar(after[i])) i++
        }
        return i
    }

    /** The run of non-space characters right before the cursor (a possible snippet shortcut). */
    fun lastToken(before: CharSequence): String {
        var i = before.length
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        return before.subSequence(i, before.length).toString()
    }
}
