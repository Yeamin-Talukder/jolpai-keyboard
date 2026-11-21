package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.editor.EditorCommand
import com.yeamin.jolpai.core.editor.TextDiff
import com.yeamin.jolpai.core.editor.TextEdit
import com.yeamin.jolpai.core.editor.UndoHistory
import com.yeamin.jolpai.core.editor.UndoHistory.Kind
import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.model.EditCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryTest {
    private val editor = FakeEditor()
    private val history = UndoHistory()
    private var now = 1_000L

    /** Types [s] the way the keyboard does: letters, spaces and punctuation, each marked as a step. */
    private fun type(s: String) = s.forEach { c ->
        now += 150
        val kind = when {
            c == ' ' || c in ".,!?" -> Kind.WORD_END
            else -> Kind.TYPE
        }
        history.before(kind, editor, now)
        editor.commit(c.toString())
        history.after(editor)
    }

    private fun backspace(times: Int) = repeat(times) {
        now += 150
        history.before(Kind.DELETE, editor, now)
        editor.deleteBackward()
        history.after(editor)
    }

    private fun paste(s: String) {
        now += 150
        history.before(Kind.INSERT, editor, now)
        editor.commit(s)
        history.after(editor)
    }

    private val text get() = editor.text.toString()

    @Test fun `undo takes back a word at a time, with its space`() {
        type("hello world again ")
        assertTrue(history.undo(editor))
        assertEquals("hello world ", text)
        assertTrue(history.undo(editor))
        assertEquals("hello ", text)
        assertTrue(history.undo(editor))
        assertEquals("", text)
        assertFalse("nothing left", history.undo(editor))
    }

    @Test fun `a run of backspaces comes back in one undo`() {
        type("good morning ")
        backspace(8)
        assertEquals("good ", text)
        assertTrue(history.undo(editor))
        assertEquals("good morning ", text)
    }

    @Test fun `a paste is its own step`() {
        type("see ")
        paste("https://example.com")
        type(" now")
        assertTrue(history.undo(editor))
        // The space after the paste goes with the paste; the word after it is its own step.
        assertEquals("see https://example.com ", text)
        assertTrue(history.undo(editor))
        assertEquals("see ", text)
    }

    @Test fun `redo goes forward again, and a new edit drops it`() {
        type("one two ")
        history.undo(editor)
        assertEquals("one ", text)
        assertTrue(history.redo(editor))
        assertEquals("one two ", text)
        history.undo(editor)
        type("three ")
        assertFalse("redo is gone after new typing", history.redo(editor))
        assertEquals("one three ", text)
        assertTrue(history.undo(editor))
        assertEquals("one ", text)
    }

    @Test fun `a long pause starts a new step mid-word`() {
        type("incred")
        now += 5_000
        type("ible")
        assertTrue(history.undo(editor))
        assertEquals("incred", text)
    }

    @Test fun `history is forgotten when the cursor moves or the app changes the text`() {
        type("hello world ")
        // The user tapped elsewhere / the app edited: the text before the cursor isn't ours any more.
        editor.text.insert(0, "XX")
        editor.text.setLength(editor.text.length - 3)
        assertFalse(history.undo(editor))
        assertEquals("XXhello wor", text)
        assertFalse(history.canUndo)
    }

    @Test fun `nothing is undone while text is selected`() {
        type("hello ")
        editor.setSelection(0, 3)
        assertFalse(history.undo(editor))
        assertEquals("hello ", text)
    }

    @Test fun `the diff lines up long text that doesn't fit the window`() {
        val long = "a".repeat(100) + (1..300).joinToString(" ") { "w$it" }
        val window = 500
        val before = long.takeLast(window)
        val after = (long + " extra words here").takeLast(window)
        val edit = TextDiff.between(after, before, window)!!
        assertEquals(TextEdit(" extra words here".length, ""), edit)
        // And the other way (redo).
        assertEquals(TextEdit(0, " extra words here"), TextDiff.between(before, after, window))
    }

    @Test fun `the diff never splits an emoji`() {
        val edit = TextDiff.between("hi 😀😃", "hi 😀😄", 1000)!!
        // 😃 and 😄 share their first UTF-16 unit; the edit must replace the whole character.
        assertEquals(TextEdit(2, "😄"), edit)
    }

    @Test fun `text that can't be lined up is left alone`() {
        val window = 64
        assertNull(TextDiff.between("x".repeat(64), "y".repeat(64), window))
    }

    // ------------------------------------------------------------------ through the keyboard

    private var clock = 10_000L
    private val app = FakeEditor()
    private val controller = KeyboardController(app, clock = { clock }).apply {
        updateSettings(KeyboardSettings(autoCorrect = false))
        onStartInput(FieldInfo.DEFAULT, restarting = false)
    }

    private fun keys(s: String) = s.forEach { c ->
        clock += 150
        controller.onKey(if (c == ' ') KeyAction.Space else KeyAction.Text(c.toString()))
    }

    @Test fun `the keyboard's undo and redo work without the app`() {
        keys("hello world ")
        val typed = app.text.toString()
        controller.onKey(KeyAction.Edit(EditCommand.UNDO))
        assertTrue(controller.lastUndoHandled)
        assertEquals("the last word and its space are gone", typed.substring(0, typed.indexOf(' ') + 1), app.text.toString())
        controller.onKey(KeyAction.Edit(EditCommand.REDO))
        assertTrue(controller.lastUndoHandled)
        assertEquals(typed, app.text.toString())
        // Nothing was sent to the app as Ctrl+Z.
        assertTrue(app.commands.none { it.first == EditorCommand.UNDO || it.first == EditorCommand.REDO })
    }

    @Test fun `with nothing of its own to undo, the keyboard asks the app`() {
        controller.onKey(KeyAction.Edit(EditCommand.UNDO))
        assertFalse(controller.lastUndoHandled)
        assertEquals(EditorCommand.UNDO, app.commands.last().first)
    }

    @Test fun `a new field starts with no history`() {
        keys("secret ")
        controller.onStartInput(FieldInfo.DEFAULT, restarting = false)
        controller.onKey(KeyAction.Edit(EditCommand.UNDO))
        assertFalse(controller.lastUndoHandled)
    }
}
