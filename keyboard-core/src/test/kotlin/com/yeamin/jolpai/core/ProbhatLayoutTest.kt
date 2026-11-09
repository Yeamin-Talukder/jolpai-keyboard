package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.layout.LayoutFactory
import com.yeamin.jolpai.core.model.Key
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.KeyStyle
import com.yeamin.jolpai.core.model.KeyboardLayout
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.LayoutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Probhat (প্রভাত) as the user's reference phone keyboard lays it out (12 / 9 / shift 9 delete),
 * letters from xkeyboard-config's `in(ben_probhat)`. ড় ঢ় য় must be the precomposed characters
 * U+09DC, U+09DD and U+09DF.
 */
class ProbhatLayoutTest {

    private val factory = LayoutFactory(LayoutId.entries, numberRow = false)

    private fun probhat(shifted: Boolean = false, page: KeyboardPage = KeyboardPage.LETTERS, field: FieldInfo = FieldInfo.DEFAULT) =
        factory.build(LayoutId.PROBHAT, page, shifted, field)

    private fun KeyboardLayout.labels(row: Int) = rows[row].keys.map { it.label }
    private fun KeyboardLayout.hints(row: Int) = rows[row].keys.map { it.hint }
    private fun KeyboardLayout.key(label: String): Key = rows.flatMap { it.keys }.first { it.label == label }

    @Test fun `letters match the reference keyboard`() {
        val l = probhat()
        assertEquals(listOf("দ", "ূ", "ী", "র", "ট", "এ", "ু", "ি", "ও", "প", "ে", "ো"), l.labels(0))
        assertEquals(listOf("া", "স", "ড", "ত", "গ", "হ", "জ", "ক", "ল"), l.labels(1))
        assertEquals(listOf("⇧", "য়", "শ", "চ", "আ", "ব", "ন", "ম", "ৃ", "্", "⌫"), l.labels(2))
    }

    @Test fun `shifted letters match the reference keyboard`() {
        val l = probhat(shifted = true)
        assertEquals(listOf("ধ", "ঊ", "ঈ", "ড়", "ঠ", "ঐ", "উ", "ই", "ঔ", "ফ", "ৈ", "ৌ"), l.labels(0))
        assertEquals(listOf("অ", "ষ", "ঢ", "থ", "ঘ", "ঃ", "ঝ", "খ", "ং"), l.labels(1))
        assertEquals(listOf("⇧", "য", "ঢ়", "ছ", "ঋ", "ভ", "ণ", "ঙ", "ৃ", "ঁ", "⌫"), l.labels(2))
    }

    @Test fun `corners show the shifted letter, with the reference keyboard's exceptions`() {
        val l = probhat()
        assertEquals(listOf("ধ", "ঊ", "ঈ", "ড়", "ঠ", "ঐ", "উ", "ই", "ঔ", "ফ", "ৈ", "ৌ"), l.hints(0))
        assertEquals(listOf("অ", "ষ", "ঢ", "থ", "ঘ", "ঃ", "ঝ", "খ", "ং"), l.hints(1))
        // য় shows য-ফলা, ৃ nothing (it is ৃ shifted too), ্ shows ঁ.
        assertEquals(listOf(null, "্য", "ঢ়", "ছ", "ঋ", "ভ", "ণ", "ঙ", null, "ঁ", null), l.hints(2))
    }

    @Test fun `shifted keys show only reph on rra and ya-phala on ya`() {
        val l = probhat(shifted = true)
        val hinted = l.rows.flatMap { it.keys }.filter { it.hint != null }.associate { it.label to it.hint }
        assertEquals(mapOf("ড়" to "র্", "য" to "্য"), hinted)
    }

    @Test fun `ya, rra and rha are the precomposed characters`() {
        val all = (probhat().rows + probhat(shifted = true).rows).flatMap { it.keys }.flatMap { k -> listOf(k.label) + k.popup.map { it.label } }
        assertTrue(all.none { '়' in it }) // no separate nukta
        assertTrue("য়" in all && "ড়" in all && "ঢ়" in all)
    }

    @Test fun `holding a key types its corner letter first`() {
        assertEquals("ই", probhat().key("ি").popup.first().label)
        assertEquals("ঁ", probhat().key("্").popup.first().label)
        assertEquals("্য", probhat().key("য়").popup.first().label)
        assertEquals("র্", probhat(shifted = true).key("ড়").popup.first().label)
        // Shifted keys without a corner letter offer the unshifted one.
        assertEquals("ি", probhat(shifted = true).key("ই").popup.first().label)
        assertTrue(probhat().key("ৃ").popup.isEmpty())
    }

    @Test fun `top row holds the Bangla digits on long-press, but not the last two keys`() {
        val row = probhat().rows[0].keys
        assertTrue("১" in row[0].popup.map { it.label })
        assertTrue("০" in row[9].popup.map { it.label })
        assertTrue(row.drop(10).all { k -> k.popup.none { it.label.single() in '০'..'৯' } })
    }

    @Test fun `letters with no key of their own are on long-press`() {
        assertTrue("ঞ" in probhat().key("জ").popup.map { it.label })
        assertTrue("ৎ" in probhat().key("ত").popup.map { it.label })
        assertTrue("ৎ" in probhat().key("্").popup.map { it.label })
        val r = probhat().key("র").popup.map { it.label }
        assertTrue("্র" in r && "র্" in r)
    }

    @Test fun `the grid is 12 keys wide, the middle row has wider keys`() {
        val l = probhat()
        assertEquals(12f, l.units)
        assertEquals(12f, l.rows[0].units)
        assertEquals(10.8f, l.rows[1].units, 0.001f) // 9 × 1.2, centred
        assertTrue(l.rows[1].keys.all { it.width == 1.2f })
        assertEquals(12f, l.rows[2].units)
        assertEquals(12f, l.rows[3].units)
        val space = l.rows[3].keys.first { it.style == KeyStyle.SPACE }
        assertEquals(6f, space.width)
        assertEquals("প্রভাত", space.label)
    }

    @Test fun `symbols, number pad and labels are the Bangla ones, like the National layout`() {
        assertEquals("?১২৩", probhat().rows[3].keys[0].label)
        assertEquals("কখগ", probhat(page = KeyboardPage.SYMBOLS).rows.last().keys[0].label)
        val symbols = probhat(page = KeyboardPage.SYMBOLS).rows.flatMap { it.keys }
        assertTrue(symbols.any { it.action == KeyAction.Text("১") })
    }

    @Test fun `a number row shows Bangla digits above the letters`() {
        val withRow = LayoutFactory(LayoutId.entries, numberRow = true).build(LayoutId.PROBHAT, KeyboardPage.LETTERS, false, FieldInfo.DEFAULT)
        assertEquals("১২৩৪৫৬৭৮৯০".map { it.toString() }, withRow.labels(0))
        assertTrue(withRow.rows[1].keys[0].popup.none { it.label == "১" })
    }

    @Test fun `probhat is off by default but can be turned on`() {
        assertTrue(LayoutId.PROBHAT !in LayoutId.DEFAULT_ENABLED)
        assertTrue(LayoutId.PROBHAT.isFixedBangla && LayoutId.NATIONAL.isFixedBangla)
        assertEquals(LayoutId.PROBHAT, LayoutId.fromStorageKey("bn-probhat"))
    }

    @Test fun `the danda key is the same as on the National layout`() {
        assertEquals(",", probhat().key("।").popup.first().label)
        assertNull(probhat().key("।").hint)
    }
}
