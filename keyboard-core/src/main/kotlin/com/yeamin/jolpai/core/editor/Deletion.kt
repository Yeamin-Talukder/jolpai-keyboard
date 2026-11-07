package com.yeamin.jolpai.core.editor

import com.yeamin.jolpai.bangla.BanglaChars
import java.text.BreakIterator

/** Decides how much text one press of Backspace removes. */
object Deletion {

    /**
     * Returns the number of UTF-16 chars to delete before the cursor, given [before].
     *
     * - Bangla: one code point at a time, so `কি` → `ক` (removes only the kar), which is
     *   what Bangla typists expect. An explicit hasanta (`্` + ZWNJ) is removed as one unit.
     * - Everything else: one whole grapheme cluster, so an emoji like 👨‍👩‍👧 or a letter with a
     *   combining accent goes in one press instead of leaving broken pieces behind.
     */
    fun lengthToDelete(before: CharSequence): Int {
        val len = before.length
        if (len == 0) return 0

        val last = before[len - 1]
        if ((last == BanglaChars.ZWNJ || last == BanglaChars.ZWJ) && len >= 2 && before[len - 2] == BanglaChars.HASANTA) {
            return 2
        }

        val text = before.toString()
        val graphemes = BreakIterator.getCharacterInstance()
        graphemes.setText(text)
        val end = graphemes.last()
        val start = graphemes.previous().let { if (it == BreakIterator.DONE) 0 else it }
        val clusterHasBangla = (start until end).any { i -> BanglaChars.isBangla(text[i]) }
        return if (clusterHasBangla) Character.charCount(text.codePointBefore(len)) else end - start
    }
}
