package com.yeamin.jolpai.bangla

import org.junit.Assert.assertEquals
import org.junit.Test

class NationalComposerTest {

    private val composer = NationalComposer(autoVowels = true)
    private val ridmik = NationalComposer()

    /** Types [keys] one by one into an empty field and returns the resulting text. */
    private fun type(vararg keys: String, c: NationalComposer = composer): String {
        val text = StringBuilder()
        for (key in keys) {
            val edit = c.compose(text, key)
            text.setLength(text.length - edit.deleteBefore)
            text.append(edit.insert)
        }
        return text.toString()
    }

    @Test fun `kar after consonant attaches`() = assertEquals("কি", type("ক", "ি"))

    @Test fun `hasanta plus kar makes the independent vowel`() {
        assertEquals("আ", type("্", "া"))
        assertEquals("ই", type("্", "ি"))
        assertEquals("ঈ", type("্", "ী"))
        assertEquals("উ", type("্", "ু"))
        assertEquals("ঊ", type("্", "ূ"))
        assertEquals("ঋ", type("্", "ৃ"))
        assertEquals("এ", type("্", "ে"))
        assertEquals("ঐ", type("্", "ৈ"))
        assertEquals("ঔ", type("্", "ৗ"))
    }

    @Test fun `standalone kar becomes a vowel`() {
        assertEquals("ই", type("ি"))
        assertEquals("আমি এ", type("্", "া", "ম", "ি", " ", "ে"))
    }

    /** By default every key goes in as typed, exactly like Ridmik (checked on a phone, 2026-11-21). */
    @Test fun `by default keys go in as typed, like Ridmik`() {
        assertEquals("ি", type("ি", c = ridmik))
        assertEquals("্া", type("্", "া", c = ridmik))
        assertEquals("িক", type("ি", "ক", c = ridmik))
        assertEquals("কাি", type("ক", "া", "ি", c = ridmik))
        assertEquals("ক্ি", type("ক", "্", "ি", c = ridmik))
        assertEquals("ক া", type("ক", " ", "া", c = ridmik))
        // ে + া stays two characters (U+09C7 U+09BE), as Ridmik types it.
        assertEquals("\u0995\u09C7\u09BE", type("ক", "ে", "া", c = ridmik))
        // The explicit hasanta still works.
        assertEquals("ক্\u200Cষ", type("ক", "্", "্", "ষ", c = ridmik))
        assertEquals("ক্ষ", type("ক", "্", "ষ", c = ridmik))
        assertEquals("র্কি", type("র্", "ক", "ি", c = ridmik))
    }

    @Test fun `e-kar plus aa-kar makes o-kar`() = assertEquals("কো", type("ক", "ে", "া"))

    @Test fun `e-kar plus au length mark makes au-kar`() = assertEquals("কৌ", type("ক", "ে", "ৗ"))

    @Test fun `conjunct through hasanta`() = assertEquals("ক্ষ", type("ক", "্", "ষ"))

    @Test fun `double hasanta gives explicit hasanta`() =
        assertEquals("ক্‌", type("ক", "্", "্"))

    @Test fun `ra-fola and ya-fola keys`() {
        assertEquals("প্র", type("প", "্র"))
        assertEquals("ব্য", type("ব", "্য"))
    }

    @Test fun `reph is inserted where typed by default`() = assertEquals("কর্ম", type("ক", "র্", "ম"))

    @Test fun `reph moves before the cluster when reordering is on`() {
        val reorder = NationalComposer(autoVowels = true, reorderReph = true)
        assertEquals("কর্ম", type("ক", "ম", "র্", c = reorder))
        assertEquals("র্ক্ষ", type("ক", "্", "ষ", "র্", c = reorder))
        assertEquals("আর্", type("্", "া", "র্", c = reorder))
    }

    @Test fun `plain letters pass through`() = assertEquals("বাংলা", type("ব", "া", "ং", "ল", "া"))
}
