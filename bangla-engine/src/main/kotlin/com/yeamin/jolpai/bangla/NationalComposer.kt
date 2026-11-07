package com.yeamin.jolpai.bangla

/**
 * An edit to apply at the cursor: delete [deleteBefore] UTF-16 chars before it, then insert [insert].
 */
data class TextEdit(val deleteBefore: Int, val insert: String) {
    companion object {
        fun insert(text: String) = TextEdit(0, text)
    }
}

/**
 * Turns key presses on the National (জাতীয়, BDS 1738) layout into correct Unicode text.
 *
 * The National layout types text in Unicode (logical) order: consonant first, then the kar.
 * A few key sequences combine with the text before the cursor:
 *
 * - hasanta `্` followed by a kar → the independent vowel (`্` + `া` → `আ`, `্` + `ি` → `ই`, …)
 * - a kar with no consonant to attach to → the independent vowel ([standaloneKarToVowel])
 * - `ে` + `া` → `ো`, and `ে` + `ৗ` → `ৌ`
 * - `্` + `্` → `্` + ZWNJ (an explicit, visible hasanta that blocks a conjunct)
 * - reph `র্` typed after a consonant cluster can be moved in front of it ([reorderReph]).
 *   This is off by default: the layout the user confirmed inserts reph where it is typed.
 *
 * This class is pure logic with no Android dependency; see `NationalComposerTest`.
 */
class NationalComposer(
    private val standaloneKarToVowel: Boolean = true,
    private val reorderReph: Boolean = false,
) {

    /** Returns the edit for typing [input] when [before] is the text just before the cursor. */
    fun compose(before: CharSequence, input: String): TextEdit {
        if (input.length == 1) {
            val c = input[0]
            if (BanglaChars.isKar(c)) return composeKar(before.lastOrNull(), c)
            if (c == BanglaChars.HASANTA && before.lastOrNull() == BanglaChars.HASANTA) {
                return TextEdit(1, "${BanglaChars.HASANTA}${BanglaChars.ZWNJ}")
            }
        }
        if (input == REPH && reorderReph) return composeReph(before)
        return TextEdit.insert(input)
    }

    private fun composeKar(prev: Char?, kar: Char): TextEdit {
        if (prev == BanglaChars.HASANTA) {
            KAR_TO_VOWEL[kar]?.let { return TextEdit(1, it.toString()) }
        }
        if (prev == E_KAR) {
            if (kar == AA_KAR) return TextEdit(1, O_KAR.toString())
            if (kar == AU_LENGTH_MARK) return TextEdit(1, AU_KAR.toString())
        }
        if (standaloneKarToVowel && !canCarryKar(prev)) {
            KAR_TO_VOWEL[kar]?.let { return TextEdit.insert(it.toString()) }
        }
        return TextEdit.insert(kar.toString())
    }

    private fun canCarryKar(prev: Char?): Boolean =
        prev != null && (BanglaChars.isConsonant(prev) || prev == NUKTA || prev == BanglaChars.ZWJ)

    /** Moves reph in front of the consonant cluster (C(্C)*) that ends at the cursor. */
    private fun composeReph(before: CharSequence): TextEdit {
        val end = before.length
        if (end == 0 || !BanglaChars.isConsonant(before[end - 1])) return TextEdit.insert(REPH)
        var start = end - 1
        while (start >= 2 && before[start - 1] == BanglaChars.HASANTA && BanglaChars.isConsonant(before[start - 2])) {
            start -= 2
        }
        val cluster = before.subSequence(start, end).toString()
        return TextEdit(end - start, REPH + cluster)
    }

    companion object {
        const val REPH = "র্" // র্
        private const val NUKTA = '়'
        private const val AA_KAR = 'া' // া
        private const val E_KAR = 'ে' // ে
        private const val O_KAR = 'ো' // ো
        private const val AU_KAR = 'ৌ' // ৌ
        private const val AU_LENGTH_MARK = 'ৗ' // ৗ

        private val KAR_TO_VOWEL: Map<Char, Char> = mapOf(
            'া' to 'আ', // া → আ
            'ি' to 'ই', // ি → ই
            'ী' to 'ঈ', // ী → ঈ
            'ু' to 'উ', // ু → উ
            'ূ' to 'ঊ', // ূ → ঊ
            'ৃ' to 'ঋ', // ৃ → ঋ
            'ে' to 'এ', // ে → এ
            'ৈ' to 'ঐ', // ৈ → ঐ
            'ো' to 'ও', // ো → ও
            'ৌ' to 'ঔ', // ৌ → ঔ
            'ৗ' to 'ঔ', // ৗ → ঔ
        )
    }
}
