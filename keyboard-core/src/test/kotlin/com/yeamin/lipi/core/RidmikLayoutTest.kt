package com.yeamin.lipi.core

import com.yeamin.lipi.core.field.FieldInfo
import com.yeamin.lipi.core.layout.LayoutFactory
import com.yeamin.lipi.core.model.Key
import com.yeamin.lipi.core.model.KeyAction
import com.yeamin.lipi.core.model.KeyIcon
import com.yeamin.lipi.core.model.KeyboardLayout
import com.yeamin.lipi.core.model.KeyboardPage
import com.yeamin.lipi.core.model.LayoutId
import com.yeamin.lipi.core.model.Panel
import com.yeamin.lipi.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The National layout and bottom row as Ridmik Keyboard has them (user's screenshots, 2026-11-06). */
class RidmikLayoutTest {

    private val factory = LayoutFactory(LayoutId.entries, numberRow = false)

    private fun national(shifted: Boolean = false, page: KeyboardPage = KeyboardPage.LETTERS, field: FieldInfo = FieldInfo.DEFAULT) =
        factory.build(LayoutId.NATIONAL, page, shifted, field)

    private fun KeyboardLayout.labels(row: Int) = rows[row].keys.map { it.label }
    private fun KeyboardLayout.key(label: String): Key = rows.flatMap { it.keys }.first { it.label == label }

    @Test fun `letters match Ridmik's National page`() {
        val l = national()
        assertEquals(listOf("ঙ", "য", "ড", "প", "ট", "চ", "জ", "হ", "গ", "ড়"), l.labels(0))
        assertEquals(listOf("ৃ", "ু", "ি", "ব", "্", "া", "ক", "ত", "দ"), l.labels(1))
        assertEquals(listOf("⇧", "ঁ", "ো", "ে", "র", "ন", "স", "ম", "⌫"), l.labels(2))
    }

    @Test fun `shifted letters match Ridmik's shifted page`() {
        val l = national(shifted = true)
        assertEquals(listOf("ং", "্য", "ঢ", "ফ", "ঠ", "ছ", "ঝ", "ঞ", "ঘ", "ঢ়"), l.labels(0))
        assertEquals(listOf("ঋ", "ূ", "ী", "ভ", "র্", "অ", "খ", "থ", "ধ"), l.labels(1))
        assertEquals(listOf("⇧", "ঃ", "ৌ", "ৈ", "্র", "ণ", "ষ", "শ", "⌫"), l.labels(2))
    }

    @Test fun `corner hints show the full vowel and long-press types it first`() {
        val u = national().key("ু")
        assertEquals("উ", u.hint)
        assertEquals("উ", u.popup.first().label)
        assertEquals("ৎ", national().key("ত").hint)
        assertEquals("ঊ", national(shifted = true).key("ূ").hint)
        assertEquals(null, national().key("্").hint)
    }

    @Test fun `ra-phala and reph are on র's long-press`() {
        val choices = national().key("র").popup.map { it.label }
        assertTrue("্র" in choices)
        assertTrue("র্" in choices)
    }

    @Test fun `bottom row has emoji, comma, wide space and dari`() {
        val bottom = national().rows.last().keys
        assertEquals(KeyAction.ShowPanel(Panel.EMOJI), bottom[1].action)
        assertEquals(KeyIcon.EMOJI, bottom[1].icon)
        assertEquals(",", bottom[2].label)
        assertEquals("জাতীয়", bottom[3].label)
        assertTrue("space has no long-press list: holding it is trackpad mode", bottom[3].popup.isEmpty())
        assertEquals("।", bottom[4].label)
        assertTrue("the language button is in the top bar", bottom.none { it.action == KeyAction.NextLayout })
    }

    @Test fun `password fields have no emoji key`() {
        val bottom = national(field = FieldInfo(isPassword = true)).rows.last().keys
        assertTrue(bottom.none { it.action == KeyAction.ShowPanel(Panel.EMOJI) })
    }

    @Test fun `symbol page has Bangla digits with English hints`() {
        val l = national(page = KeyboardPage.SYMBOLS)
        assertEquals("১", l.labels(0).first())
        assertEquals("1", l.rows[0].keys.first().hint)
        assertEquals(listOf("@", "#", "৳", "%", "&", "*", "-", "+", "(", ")"), l.labels(1))
        assertEquals("$", l.key("৳").hint)
        assertEquals("ALT", l.labels(2).first())
        assertEquals(listOf(",", "."), listOf(l.rows.last().keys[2].label, l.rows.last().keys[4].label))
    }

    @Test fun `ALT page has tab and r-zwj-ya`() {
        val l = national(page = KeyboardPage.SYMBOLS_MORE)
        assertEquals(KeyAction.Text("\t"), l.rows[1].keys.first().action)
        assertTrue(l.rows[2].keys.any { it.action == KeyAction.Text("র‍্য") })
        assertEquals("Π", l.key("π").hint)
    }

    @Test fun `every row fills the same width`() {
        listOf(KeyboardPage.LETTERS, KeyboardPage.SYMBOLS, KeyboardPage.SYMBOLS_MORE).forEach { page ->
            listOf(false, true).forEach { shifted ->
                val l = national(shifted, page)
                assertEquals("$page", 10f, l.rows.last().units, 0.01f)
            }
        }
    }

    @Test fun `backspace swipe range grows, shrinks and deletes`() {
        val editor = FakeEditor("one two three")
        val controller = KeyboardController(editor, clock = { 0L }).apply {
            updateSettings(KeyboardSettings())
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }
        controller.onKey(KeyAction.SelectBack(1))
        assertEquals(8 to 13, editor.selection) // "three"
        controller.onKey(KeyAction.SelectBack(2))
        assertEquals(4 to 13, editor.selection) // "two three"
        controller.onKey(KeyAction.SelectBack(1))
        assertEquals(8 to 13, editor.selection) // back to "three"
        controller.onKey(KeyAction.EndSelectBack(delete = true))
        assertEquals("one two ", editor.text.toString())
    }

    @Test fun `backspace swipe slid back to the start deletes nothing`() {
        val editor = FakeEditor("one two")
        val controller = KeyboardController(editor, clock = { 0L }).apply {
            updateSettings(KeyboardSettings())
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }
        controller.onKey(KeyAction.SelectBack(2))
        controller.onKey(KeyAction.SelectBack(0))
        controller.onKey(KeyAction.EndSelectBack(delete = false))
        assertEquals("one two", editor.text.toString())
        assertEquals(7 to 7, editor.selection)
    }

    @Test fun `hold time for hidden characters follows the setting, within limits`() {
        val controller = KeyboardController(FakeEditor(), clock = { 0L }).apply { onStartInput(FieldInfo.DEFAULT, restarting = false) }
        assertEquals(350L, controller.state.value.longPressMs)
        controller.updateSettings(KeyboardSettings(longPressMs = 200))
        assertEquals(200L, controller.state.value.longPressMs)
        assertTrue(com.yeamin.lipi.core.settings.LONG_PRESS_RANGE.first >= 100)
    }
}
