package com.yeamin.lipi.core

import com.yeamin.lipi.core.editor.EditorCommand
import com.yeamin.lipi.core.editor.TextEditor
import com.yeamin.lipi.core.field.FieldInfo
import com.yeamin.lipi.core.model.KeyAction
import com.yeamin.lipi.core.model.Panel
import com.yeamin.lipi.core.settings.KeyboardSettings
import com.yeamin.lipi.suggest.BanglaDictionary
import com.yeamin.lipi.suggest.EnglishDictionary
import com.yeamin.lipi.suggest.Suggester
import com.yeamin.lipi.suggest.UserHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A text field with a real cursor: committing replaces the selection, like an app's field. */
private class CursorEditor(initial: String) : TextEditor {
    val text = StringBuilder(initial)
    var selStart = initial.length
    var selEnd = initial.length

    override fun textBeforeCursor(maxChars: Int): CharSequence = text.substring(maxOf(0, selStart - maxChars), selStart)
    override fun textAfterCursor(maxChars: Int): CharSequence = text.substring(selEnd, minOf(text.length, selEnd + maxChars))
    override fun hasSelection() = selStart != selEnd
    override fun selectionStart() = selStart
    override fun selectionEnd() = selEnd
    override fun setSelection(start: Int, end: Int) {
        selStart = start.coerceIn(0, text.length)
        selEnd = end.coerceIn(0, text.length)
    }
    override fun commit(text: CharSequence) {
        this.text.replace(selStart, selEnd, text.toString())
        selStart += text.length
        selEnd = selStart
    }
    override fun setComposing(text: CharSequence) = commit(text)
    override fun finishComposing() = Unit
    override fun replaceBefore(deleteCount: Int, text: CharSequence) {
        selStart = maxOf(0, selStart - deleteCount)
        commit(text)
    }
    override fun deleteBackward() {
        if (selStart == selEnd && selStart > 0) selStart--
        commit("")
    }
    override fun performEnter(field: FieldInfo) = commit("\n")
    override fun run(command: EditorCommand, extendSelection: Boolean) = Unit
    override fun isCapsModeAtCursor() = false
}

class CheckModeTest {
    companion object {
        private val en by lazy { EnglishDictionary.load() }
        private val bn by lazy { BanglaDictionary.load() }
    }

    private fun controllerFor(editor: TextEditor) =
        KeyboardController(editor, clock = { 0L }, suggester = Suggester({ en }, { bn }, { null }, UserHistory())).apply {
            updateSettings(KeyboardSettings(autoCapitalize = false))
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }

    @Test fun `check finds issues in the field and fixes one, keeping the cursor`() {
        val field = CursorEditor("i recieved it ,thanks")
        field.setSelection(13, 13) // cursor after "it"
        val c = controllerFor(field)
        c.onKey(KeyAction.StartCheck)
        val issues = c.state.value.check!!.issues
        assertTrue(issues.map { it.replacement }.containsAll(listOf("I", "received")))

        c.onKey(KeyAction.FixIssue(issues.first { it.replacement == "received" }))
        assertTrue(field.text.startsWith("i received it"))
        assertEquals(13, field.selStart) // same place in the text ("it" moved by nothing before it… +0)
        assertEquals(1, c.state.value.check!!.fixed)
        assertTrue(c.state.value.check!!.issues.none { it.original == "recieved" })
    }

    @Test fun `fix all corrects everything, even fixes that uncover the next one`() {
        val field = CursorEditor("She don't care, its better then nothing")
        val c = controllerFor(field)
        c.onKey(KeyAction.StartCheck)
        c.onKey(KeyAction.FixAllIssues)
        assertEquals("She doesn't care, it's better than nothing.", field.text.toString())
        assertTrue(c.state.value.check!!.issues.isEmpty())
        assertEquals(field.text.length, field.selStart)
    }

    @Test fun `ignore hides a word, typing marks the card stale, recheck refreshes`() {
        val field = CursorEditor("we met at wnet today")
        val c = controllerFor(field)
        c.onKey(KeyAction.StartCheck)
        val wnet = c.state.value.check!!.issues.first { it.original == "wnet" }
        c.onKey(KeyAction.IgnoreIssue(wnet))
        assertTrue(c.state.value.check!!.issues.none { it.original == "wnet" })

        c.onKey(KeyAction.Text("x"))
        assertTrue(c.state.value.check!!.stale)
        c.onKey(KeyAction.Recheck)
        assertEquals(false, c.state.value.check!!.stale)
        assertTrue(c.state.value.check!!.issues.none { it.original == "wnet" }) // still ignored

        // "Add word" on a spelling flag keeps it for good: closing and reopening doesn't bring it back.
        c.onKey(KeyAction.EndCheck)
        c.onKey(KeyAction.StartCheck)
        assertTrue(c.state.value.check!!.issues.none { it.original == "wnet" })
    }

    @Test fun `the translate box suggests fixes as chips instead of changing words`() {
        val field = CursorEditor("")
        val c = controllerFor(field)
        c.updateSettings(KeyboardSettings(autoCapitalize = false, autoCorrect = true))
        c.onKey(KeyAction.StartTranslate())
        "I recieved it ".forEach { c.onKey(if (it == ' ') KeyAction.Space else KeyAction.Text(it.toString())) }
        val t = c.state.value.translate!!
        assertEquals("I recieved it ", t.source) // never autocorrected in the box
        val chip = t.issues.single { it.original == "recieved" }
        assertEquals("received", chip.replacement)
        assertEquals("", field.text.toString())

        c.onKey(KeyAction.FixTranslateIssue(chip))
        assertEquals("I received it ", c.state.value.translate!!.source)
        assertTrue(c.state.value.translate!!.issues.isEmpty())

        // Dismissed chips stay away; Fix all applies the rest (Bangla too).
        c.onKey(KeyAction.ClearTranslate)
        c.onKey(KeyAction.PasteText("teh শিক্ষাথী "))
        val issues = c.state.value.translate!!.issues
        c.onKey(KeyAction.IgnoreTranslateIssue(issues.first { it.original == "teh" }))
        assertTrue(c.state.value.translate!!.issues.none { it.original == "teh" })
        c.onKey(KeyAction.ApplyTranslateSpelling)
        assertEquals("teh শিক্ষার্থী ", c.state.value.translate!!.source)
    }

    @Test fun `an AI rewrite applies only to the text it was made for`() {
        val field = CursorEditor("Me and him goes to school.")
        val c = controllerFor(field)
        c.onKey(KeyAction.StartCheck)
        c.onKey(KeyAction.ApplyRewrite("text that isn't there", "Something else."))
        assertEquals("Me and him goes to school.", field.text.toString())
        c.onKey(KeyAction.ApplyRewrite("Me and him goes to school.", "He and I go to school."))
        assertEquals("He and I go to school.", field.text.toString())
        assertEquals(1, c.state.value.check!!.fixed)
    }

    @Test fun `typing stats count words and corrections, never in private fields or boxes`() {
        val events = object : com.yeamin.lipi.core.stats.TypingEvents {
            var words = 0; var bangla = 0; var corrected = 0; var picked = 0; var emoji = 0
            override fun word(bangla: Boolean) { words++; if (bangla) this.bangla++ }
            override fun autocorrected() { corrected++ }
            override fun suggestionPicked(lettersSaved: Int) { picked++ }
            override fun emoji() { emoji++ }
        }
        val field = CursorEditor("")
        val c = controllerFor(field).apply { stats = events; updateSettings(KeyboardSettings(autoCapitalize = false, autoCorrect = true)) }
        fun type(s: String) = s.forEach { c.onKey(if (it == ' ') KeyAction.Space else KeyAction.Text(it.toString())) }
        type("I recieved it ")
        assertEquals(3, events.words)
        assertEquals(1, events.corrected)
        c.onKey(KeyAction.InsertEmoji("😀"))
        assertEquals(1, events.emoji)

        c.onKey(KeyAction.StartTranslate())
        type("hello there ")
        assertEquals(3, events.words) // the translate box isn't counted
        c.onKey(KeyAction.EndTranslate)

        c.onStartInput(FieldInfo(isPassword = true), restarting = false)
        type("secret words ")
        assertEquals(3, events.words) // nor password fields
    }

    @Test fun `typing speed is measured from key presses, outside boxes and private fields`() {
        val bursts = ArrayList<Pair<Int, Long>>()
        val events = object : com.yeamin.lipi.core.stats.TypingEvents {
            override fun word(bangla: Boolean) = Unit
            override fun autocorrected() = Unit
            override fun suggestionPicked(lettersSaved: Int) = Unit
            override fun emoji() = Unit
            override fun typingBurst(typed: Int, deleted: Int, millis: Long) { bursts += typed to millis }
        }
        var now = 0L
        val c = KeyboardController(CursorEditor(""), clock = { now }, suggester = Suggester({ en }, { bn }, { null }, UserHistory())).apply {
            updateSettings(KeyboardSettings(autoCapitalize = false))
            onStartInput(FieldInfo.DEFAULT, restarting = false)
            stats = events
        }
        fun type(s: String) = s.forEach { now += 200; c.onKey(if (it == ' ') KeyAction.Space else KeyAction.Text(it.toString())) }
        type("hello there friend ")
        c.onFinishInput()
        assertEquals(1, bursts.size)
        assertEquals(19, bursts[0].first)
        assertEquals(19 * 200L, bursts[0].second)

        c.onKey(KeyAction.StartTranslate())
        type("hello there friend ")
        c.onKey(KeyAction.EndTranslate)
        c.onStartInput(FieldInfo(isPassword = true), restarting = false)
        type("secret words here ")
        c.onFinishInput()
        assertEquals(1, bursts.size)
    }

    @Test fun `empty field, closing, and other tools`() {
        val c = controllerFor(CursorEditor(""))
        c.onKey(KeyAction.StartCheck)
        assertTrue(c.state.value.check!!.empty)
        c.onKey(KeyAction.ShowPanel(Panel.EMOJI))
        assertNull(c.state.value.check)
        c.onKey(KeyAction.StartCheck)
        c.onKey(KeyAction.StartTranslate())
        assertNull(c.state.value.check)
        c.onKey(KeyAction.StartCheck)
        c.onKey(KeyAction.EndCheck)
        assertNull(c.state.value.check)
    }
}
