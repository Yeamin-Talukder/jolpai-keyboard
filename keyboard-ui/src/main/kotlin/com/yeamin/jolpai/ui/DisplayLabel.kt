package com.yeamin.jolpai.ui

/**
 * Key labels as drawn. A Bangla vowel sign or hasanta on its own (ি ে ো ্ ং …) has no letter
 * to sit on, so the text shaper draws it on a dotted circle (◌ি). Putting a no-break space in
 * front gives it an invisible base instead, so the key shows just the sign. Only the label
 * changes; the key still types the sign alone.
 */
internal object DisplayLabel {
    private const val INVISIBLE_BASE = ' '

    fun of(text: String): String = if (text.isNotEmpty() && isMark(text[0])) INVISIBLE_BASE + text else text

    private fun isMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }
}
