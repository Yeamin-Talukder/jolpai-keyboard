package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.gesture.GestureAction
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The number pad in any field (user request, 2026-11-21: "a dedicated number pad"). */
class NumberPadTest {
    private val editor = FakeEditor()
    private val controller = KeyboardController(editor, clock = { 10_000L }).apply {
        updateSettings(KeyboardSettings())
        onStartInput(FieldInfo.DEFAULT, restarting = false) // an ordinary text field
    }
    private val state get() = controller.state.value
    private fun labels() = state.layout.rows.flatMap { r -> r.keys.map { it.label } }

    @Test fun `the number pad opens in an ordinary text field and closes again`() {
        assertEquals(KeyboardPage.LETTERS, state.page)
        controller.onKey(KeyAction.ToggleNumberPad)
        assertEquals(KeyboardPage.NUMBER, state.page)
        assertTrue("digits 0-9 on the pad", ('0'..'9').all { it.toString() in labels() })
        controller.onKey(KeyAction.Text("4"))
        controller.onKey(KeyAction.Text("2"))
        assertEquals("42", editor.text.toString())
        controller.onKey(KeyAction.ToggleNumberPad)
        assertEquals(KeyboardPage.LETTERS, state.page)
    }

    @Test fun `it opens over an open panel`() {
        controller.onKey(KeyAction.ShowPanel(Panel.EMOJI))
        assertEquals(Panel.EMOJI, state.panel)
        controller.onKey(KeyAction.ToggleNumberPad)
        assertEquals(Panel.NONE, state.panel)
        assertEquals(KeyboardPage.NUMBER, state.page)
    }

    @Test fun `it opens from the symbols page too`() {
        controller.onKey(KeyAction.SwitchPage(KeyboardPage.SYMBOLS))
        controller.onKey(KeyAction.ToggleNumberPad)
        assertEquals(KeyboardPage.NUMBER, state.page)
    }

    @Test fun `holding ?123 opens the number pad, in English and Bangla`() {
        for (layout in listOf(LayoutId.ENGLISH, LayoutId.NATIONAL, LayoutId.AVRO)) {
            controller.onKey(KeyAction.SelectLayout(layout))
            controller.onKey(KeyAction.SwitchPage(KeyboardPage.LETTERS))
            val pageKey = state.layout.rows.last().keys.first()
            assertEquals("$layout: the ?123 key", KeyAction.SwitchPage(KeyboardPage.SYMBOLS), pageKey.action)
            assertEquals("$layout: hold for the pad", KeyAction.ToggleNumberPad, pageKey.longPress)
        }
    }

    @Test fun `it is in the top bar and can be a gesture`() {
        assertTrue(ToolbarItem.NUMBER_PAD in ToolbarItem.DEFAULT)
        assertEquals(ToolbarItem.NUMBER_PAD, ToolbarItem.fromStorageKey("numpad"))
        assertEquals(listOf(KeyAction.ToggleNumberPad), GestureAction.NUMBER_PAD.toKeyActions(null, LayoutId.ENGLISH))
    }
}
