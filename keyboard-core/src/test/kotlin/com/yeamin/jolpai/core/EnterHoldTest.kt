package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.editor.EditorCommand
import com.yeamin.jolpai.core.editor.TextEditor
import com.yeamin.jolpai.core.field.EnterAction
import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.gesture.EnterHold
import com.yeamin.jolpai.core.gesture.EnterHoldGesture
import com.yeamin.jolpai.core.gesture.SlideSelection
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterHoldGestureTest {
    private fun gesture(emoji: Boolean, select: Boolean) =
        EnterHoldGesture(EnterHold(emoji, select), charStep = 10f, lineStep = 30f, slop = 14f)

    private val emojiPanel = listOf(KeyAction.ShowPanel(Panel.EMOJI))

    @Test fun `both off gives no options`() {
        assertNull(EnterHold.of(emoji = false, select = false))
        assertEquals(EnterHold(true, false), EnterHold.of(emoji = true, select = false))
    }

    @Test fun `a release before the hold time is a normal tap`() {
        val g = gesture(emoji = true, select = true)
        assertTrue(g.onMove(50f, 0f).isEmpty()) // moving before the hold does nothing
        assertFalse(g.heldPastTap)
        assertTrue(g.onRelease().isEmpty())
    }

    @Test fun `emoji only opens the panel as soon as the hold time passes`() {
        val g = gesture(emoji = true, select = false)
        assertEquals(emojiPanel, g.onHold(0f, 0f))
        assertTrue(g.heldPastTap)
        assertTrue(g.onMove(100f, 0f).isEmpty())
        assertTrue(g.onRelease().isEmpty())
    }

    @Test fun `select only slides by characters and lines and ends on release`() {
        val g = gesture(emoji = false, select = true)
        assertTrue(g.onHold(100f, 100f).isEmpty())
        assertTrue(g.onMove(105f, 100f).isEmpty()) // inside the slop: still a hold
        assertTrue(g.onMove(120f, 100f).isEmpty()) // past the slop: selecting starts here
        assertEquals(listOf(KeyAction.SelectSlide(chars = 2, lines = 0)), g.onMove(140f, 100f))
        assertTrue(g.onMove(141f, 100f).isEmpty()) // same step: nothing new
        assertEquals(listOf(KeyAction.SelectSlide(chars = -1, lines = 0)), g.onMove(110f, 100f))
        assertEquals(listOf(KeyAction.SelectSlide(chars = -1, lines = 1)), g.onMove(110f, 131f))
        assertEquals(listOf(KeyAction.SelectSlide(chars = -1, lines = -1)), g.onMove(110f, 69f))
        assertEquals(listOf(KeyAction.EndSelectSlide), g.onRelease())
    }

    @Test fun `select only held still and released does nothing (no new line)`() {
        val g = gesture(emoji = false, select = true)
        g.onHold(0f, 0f)
        g.onMove(5f, 5f)
        assertTrue(g.heldPastTap)
        assertTrue(g.onRelease().isEmpty())
    }

    @Test fun `both on - hold still opens emoji on release`() {
        val g = gesture(emoji = true, select = true)
        assertTrue(g.onHold(0f, 0f).isEmpty()) // not yet: the finger may still slide
        g.onMove(6f, -4f)
        assertEquals(emojiPanel, g.onRelease())
    }

    @Test fun `both on - hold and slide selects and never opens emoji`() {
        val g = gesture(emoji = true, select = true)
        g.onHold(0f, 0f)
        g.onMove(-20f, 0f)
        assertEquals(listOf(KeyAction.SelectSlide(chars = -3, lines = 0)), g.onMove(-50f, 0f))
        assertEquals(listOf(KeyAction.EndSelectSlide), g.onRelease())
    }

    @Test fun `a cancelled slide ends the selection, a cancelled hold opens nothing`() {
        val sliding = gesture(emoji = true, select = true)
        sliding.onHold(0f, 0f)
        sliding.onMove(20f, 0f)
        sliding.onMove(40f, 0f)
        assertEquals(listOf(KeyAction.EndSelectSlide), sliding.onCancel())
        val held = gesture(emoji = true, select = true)
        held.onHold(0f, 0f)
        assertTrue(held.onCancel().isEmpty())
    }
}

class SlideSelectionTest {
    @Test fun `characters move by whole graphemes and stop at the ends`() {
        val text = "abéc"
        assertEquals(1, SlideSelection.moveChars(text, 0, 1))
        assertEquals(4, SlideSelection.moveChars(text, 2, 1)) // e with its accent is one step
        assertEquals(2, SlideSelection.moveChars(text, 4, -1))
        assertEquals(text.length, SlideSelection.moveChars(text, 0, 99))
        assertEquals(0, SlideSelection.moveChars(text, 3, -99))
    }

    @Test fun `a Bangla letter with its kar moves as one`() {
        val text = "আমি" // আ, ম + ি
        assertEquals(1, SlideSelection.moveChars(text, 0, 1))
        assertEquals(3, SlideSelection.moveChars(text, 1, 1))
    }

    @Test fun `lines keep the column where it fits`() {
        val text = "hello\nhi\nworld"
        // From "hel|lo": down to the end of "hi" (shorter), down again to "wor|ld"? Column 3 is kept.
        assertEquals(8, SlideSelection.moveLines(text, 3, 1))
        assertEquals(12, SlideSelection.moveLines(text, 3, 2))
        assertEquals(3, SlideSelection.moveLines(text, 12, -2))
        // Past the first or last line: the start or end of the text.
        assertEquals(0, SlideSelection.moveLines(text, 3, -1))
        assertEquals(text.length, SlideSelection.moveLines(text, 12, 1))
    }

    @Test fun `focus applies lines then characters`() {
        val text = "hello\nworld"
        assertEquals(9, SlideSelection.focus(text, 2, chars = 1, lines = 1)) // "wo|rld" then one right
    }
}

/** A field with a cursor anywhere and a selection, like a real app. */
private class SlideEditor(initial: String, cursor: Int = initial.length) : TextEditor {
    val text = StringBuilder(initial)
    var selStart = cursor
    var selEnd = cursor
    val enters = mutableListOf<EnterAction>()
    val selected: String get() = text.substring(selStart, selEnd)
    override fun textBeforeCursor(maxChars: Int): CharSequence = text.substring(maxOf(0, selStart - maxChars), selStart)
    override fun textAfterCursor(maxChars: Int): CharSequence = text.substring(selEnd, minOf(text.length, selEnd + maxChars))
    override fun hasSelection() = selStart != selEnd
    override fun selectedText(maxChars: Int): CharSequence = text.substring(selStart, selEnd)
    override fun selectionStart() = selStart
    override fun selectionEnd() = selEnd
    override fun setSelection(start: Int, end: Int) { selStart = start; selEnd = end }
    override fun commit(text: CharSequence) {
        this.text.replace(selStart, selEnd, text.toString())
        selStart += text.length
        selEnd = selStart
    }
    override fun setComposing(text: CharSequence) = commit(text)
    override fun finishComposing() = Unit
    override fun replaceBefore(deleteCount: Int, text: CharSequence) { selStart = maxOf(0, selStart - deleteCount); commit(text) }
    override fun deleteBackward() { if (selStart == selEnd && selStart > 0) selStart--; commit("") }
    override fun performEnter(field: FieldInfo) { if (field.enterAction == EnterAction.NEWLINE) commit("\n") else enters += field.enterAction }
    override fun run(command: EditorCommand, extendSelection: Boolean) = Unit
    override fun isCapsModeAtCursor() = false
}

class EnterHoldControllerTest {
    private fun controller(editor: TextEditor, emoji: Boolean = true, select: Boolean = true, field: FieldInfo = FieldInfo.DEFAULT) =
        KeyboardController(editor, clock = { 0L }).apply {
            updateSettings(KeyboardSettings(autoCapitalize = false, enterHoldEmoji = emoji, enterHoldSelect = select))
            onStartInput(field, restarting = false)
        }

    @Test fun `both settings are off by default`() {
        val s = KeyboardSettings()
        assertFalse(s.enterHoldEmoji)
        assertFalse(s.enterHoldSelect)
        assertNull(KeyboardController(SlideEditor(""), clock = { 0L }).apply { updateSettings(s) }.state.value.enterHold)
    }

    @Test fun `the options reach the keyboard view`() {
        assertEquals(EnterHold(emoji = true, select = false), controller(SlideEditor(""), select = false).state.value.enterHold)
        assertEquals(EnterHold(emoji = true, select = true), controller(SlideEditor("")).state.value.enterHold)
    }

    @Test fun `never in password fields`() {
        assertNull(controller(SlideEditor(""), field = FieldInfo(isPassword = true)).state.value.enterHold)
    }

    @Test fun `off while typing into the emoji search box`() {
        val c = controller(SlideEditor(""))
        c.onKey(KeyAction.StartSearch(Panel.EMOJI))
        assertNull(c.state.value.enterHold)
        c.onKey(KeyAction.EndSearch(keepQuery = false))
        assertEquals(EnterHold(emoji = true, select = true), c.state.value.enterHold)
    }

    @Test fun `a tap on Enter still starts a new line or sends`() {
        val field = SlideEditor("hi")
        val c = controller(field)
        c.onKey(KeyAction.Enter)
        assertEquals("hi\n", field.text.toString())
        val chat = SlideEditor("hi")
        controller(chat, field = FieldInfo(enterAction = EnterAction.SEND)).onKey(KeyAction.Enter)
        assertEquals(listOf(EnterAction.SEND), chat.enters)
    }

    @Test fun `sliding left and right selects from the cursor, and grows and shrinks`() {
        val field = SlideEditor("hello world", cursor = 5)
        val c = controller(field)
        c.onKey(KeyAction.SelectSlide(chars = -2))
        assertEquals("lo", field.selected)
        c.onKey(KeyAction.SelectSlide(chars = -5))
        assertEquals("hello", field.selected)
        c.onKey(KeyAction.SelectSlide(chars = 3))
        assertEquals(" wo", field.selected)
        c.onKey(KeyAction.EndSelectSlide)
        assertEquals(" wo", field.selected) // letting go keeps the selection
    }

    @Test fun `sliding down selects to the same column on the next line`() {
        val field = SlideEditor("one\ntwo\nthree", cursor = 1)
        val c = controller(field)
        c.onKey(KeyAction.SelectSlide(chars = 0, lines = 1))
        assertEquals("ne\nt", field.selected)
        c.onKey(KeyAction.SelectSlide(chars = 1, lines = 1))
        assertEquals("ne\ntw", field.selected)
        c.onKey(KeyAction.EndSelectSlide)
    }

    @Test fun `a second slide moves the end of the existing selection`() {
        val field = SlideEditor("hello world", cursor = 0)
        val c = controller(field)
        c.onKey(KeyAction.SelectSlide(chars = 3))
        c.onKey(KeyAction.EndSelectSlide)
        assertEquals("hel", field.selected)
        c.onKey(KeyAction.SelectSlide(chars = 2))
        assertEquals("hello", field.selected)
        c.onKey(KeyAction.SelectSlide(chars = -1))
        assertEquals("he", field.selected)
        c.onKey(KeyAction.EndSelectSlide)
    }

    @Test fun `the selection can then be cut like any other`() {
        val field = SlideEditor("keep this", cursor = 9)
        val c = controller(field)
        c.onKey(KeyAction.SelectSlide(chars = -4))
        c.onKey(KeyAction.EndSelectSlide)
        assertEquals("this", field.selected)
        c.onKey(KeyAction.Delete)
        assertEquals("keep ", field.text.toString())
    }

    @Test fun `hold for emoji opens the emoji panel`() {
        val c = controller(SlideEditor("hi"))
        c.onKey(KeyAction.ShowPanel(Panel.EMOJI)) // what the gesture emits
        assertEquals(Panel.EMOJI, c.state.value.panel)
    }
}

class PanelPlacementTest {
    private fun controller(placement: com.yeamin.jolpai.core.settings.PanelPlacement? = null) =
        KeyboardController(SlideEditor("hi"), clock = { 0L }).apply {
            updateSettings(if (placement == null) KeyboardSettings() else KeyboardSettings(panelPlacement = placement))
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }

    @Test fun `emoji and clipboard open above the keyboard by default`() {
        assertEquals(com.yeamin.jolpai.core.settings.PanelPlacement.ABOVE, KeyboardSettings().panelPlacement)
        assertTrue(controller().state.value.panelsAbove)
    }

    @Test fun `they can open in place of the keys instead`() {
        val c = controller(com.yeamin.jolpai.core.settings.PanelPlacement.KEYS)
        assertFalse(c.state.value.panelsAbove)
        c.onKey(KeyAction.ShowPanel(Panel.CLIPBOARD))
        assertEquals(Panel.CLIPBOARD, c.state.value.panel)
        assertFalse(c.state.value.panelsAbove)
    }

    @Test fun `stored values read back, unknown ones give the default`() {
        com.yeamin.jolpai.core.settings.PanelPlacement.entries.forEach {
            assertEquals(it, com.yeamin.jolpai.core.settings.PanelPlacement.fromStorageKey(it.storageKey))
        }
        assertEquals(com.yeamin.jolpai.core.settings.PanelPlacement.ABOVE, com.yeamin.jolpai.core.settings.PanelPlacement.fromStorageKey(null))
        assertEquals(com.yeamin.jolpai.core.settings.PanelPlacement.ABOVE, com.yeamin.jolpai.core.settings.PanelPlacement.fromStorageKey("sideways"))
    }
}
