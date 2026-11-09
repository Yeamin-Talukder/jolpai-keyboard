package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.editor.EditorCommand
import com.yeamin.jolpai.core.editor.TextEditor
import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.model.DateTimeKind
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.tools.BanglaCalendar
import com.yeamin.jolpai.core.tools.SnippetVariables
import com.yeamin.jolpai.core.tools.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class BanglaCalendarTest {
    private fun bn(y: Int, m: Int, d: Int) = BanglaCalendar.format(LocalDate.of(y, m, d))

    @Test fun `national days fall on their fixed Bangla dates`() {
        assertEquals("১ বৈশাখ ১৪৩৩", bn(2026, 4, 14))
        assertEquals("৩০ চৈত্র ১৪৩২", bn(2026, 4, 13))
        assertEquals("৮ ফাল্গুন ১৪৩২", bn(2026, 2, 21))
        assertEquals("১২ চৈত্র ১৪৩২", bn(2026, 3, 26))
        assertEquals("১ পৌষ ১৪৩৩", bn(2026, 12, 16))
        assertEquals("২৩ কার্তিক ১৪৩৩", bn(2026, 11, 8))
    }

    @Test fun `falgun gets the leap day`() {
        assertEquals("১৬ ফাল্গুন ১৪৩৪", bn(2028, 2, 29))
        assertEquals("৩০ ফাল্গুন ১৪৩৪", bn(2028, 3, 14))
        assertEquals("১ চৈত্র ১৪৩৪", bn(2028, 3, 15))
        assertEquals("১ চৈত্র ১৪৩২", bn(2026, 3, 15)) // 29-day ফাল্গুন in a common year
    }

    @Test fun `weekday and season`() {
        assertEquals("রবিবার, ২৩ কার্তিক ১৪৩৩ বঙ্গাব্দ", BanglaCalendar.format(LocalDate.of(2026, 11, 8), weekday = true))
        assertEquals("হেমন্ত", BanglaCalendar.of(LocalDate.of(2026, 11, 8)).season)
    }
}

class SnippetVariablesTest {
    // 8 November 2026, 10:30 in Dhaka
    private val now = LocalDate.of(2026, 11, 8).atTime(10, 30).toInstant(ZoneOffset.ofHours(6)).toEpochMilli()
    private val dhaka = ZoneId.of("Asia/Dhaka")

    @Test fun `bangla date, day and clipboard are filled in`() {
        val e = SnippetVariables.expand("আজ {বার}, {bangla_date}. কোড: {clipboard}", now, dhaka) { "482913" }
        assertEquals("আজ রবিবার, ২৩ কার্তিক ১৪৩৩. কোড: 482913", e.text)
        assertNull(e.cursor)
    }

    @Test fun `cursor marks where the cursor goes, unknown names stay`() {
        val e = SnippetVariables.expand("Dear {cursor}, see {nothing}", now, dhaka)
        assertEquals("Dear , see {nothing}", e.text)
        assertEquals(5, e.cursor)
    }

    @Test fun `english date for an english snippet`() {
        val e = SnippetVariables.expand("Today is {day}", now, dhaka)
        assertEquals("Today is Sunday", e.text)
    }
}

class TextStyleTest {
    @Test fun `letters and digits change, other characters stay`() {
        assertEquals("𝐇𝐢 𝟓!", TextStyle.apply(TextStyle.BOLD, "Hi 5!"))
        assertEquals("ℎ𝑖", TextStyle.apply(TextStyle.ITALIC, "hi")) // italic h lives elsewhere in Unicode
        assertEquals("ℬℴ𝓎", TextStyle.apply(TextStyle.SCRIPT, "Boy")) // script B and o live elsewhere too
        assertEquals("Ⓐ①", TextStyle.apply(TextStyle.CIRCLED, "A1"))
        assertEquals("Sᴍᴀʟʟ", TextStyle.apply(TextStyle.SMALL_CAPS, "Small"))
    }

    @Test fun `restyling starts from plain letters`() {
        val bold = TextStyle.apply(TextStyle.BOLD, "Hello")
        assertEquals(TextStyle.apply(TextStyle.ITALIC, "Hello"), TextStyle.apply(TextStyle.ITALIC, bold))
        assertEquals("Hello", TextStyle.plain(bold))
        assertEquals("Hello", TextStyle.plain(TextStyle.apply(TextStyle.STRIKE, "Hello")))
    }

    @Test fun `strike keeps Bangla letters whole`() {
        // The mark goes after a whole cluster (consonant + vowel sign; not after the hasanta).
        assertEquals("আ̶মি̶", TextStyle.apply(TextStyle.STRIKE, "আমি"))
        assertEquals("ক্ষ̶", TextStyle.apply(TextStyle.STRIKE, "ক্ষ"))
    }
}

/** A field with a real cursor and selection, that can report the selected text. */
private class SelectEditor(initial: String) : TextEditor {
    val text = StringBuilder(initial)
    var selStart = initial.length
    var selEnd = initial.length
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
    override fun performEnter(field: FieldInfo) = commit("\n")
    override fun run(command: EditorCommand, extendSelection: Boolean) = Unit
    override fun isCapsModeAtCursor() = false
}

class StyledTypingTest {
    private fun controller(editor: TextEditor, snippets: Map<String, String> = emptyMap()) =
        KeyboardController(editor, clock = { 0L }, snippetFor = { snippets[it] }).apply {
            updateSettings(KeyboardSettings(autoCapitalize = false))
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }

    @Test fun `with nothing selected a style turns styled typing on, and the strip can turn it off`() {
        val field = SelectEditor("")
        val c = controller(field)
        c.onKey(KeyAction.SetTextStyle(TextStyle.BOLD))
        assertEquals(TextStyle.BOLD, c.state.value.textStyle)
        "Hi 2".forEach { c.onKey(if (it == ' ') KeyAction.Space else KeyAction.Text(it.toString())) }
        assertEquals("𝐇𝐢 𝟐", field.text.toString())
        val off = c.state.value.strip.first()
        assertTrue(off.label.endsWith("✕"))
        c.onKey(off.action)
        assertNull(c.state.value.textStyle)
        c.onKey(KeyAction.Text("x"))
        assertTrue(field.text.endsWith("x"))
    }

    @Test fun `a selection is restyled and stays selected`() {
        val field = SelectEditor("say hello now")
        field.setSelection(4, 9)
        val c = controller(field)
        c.onKey(KeyAction.SetTextStyle(TextStyle.BOLD))
        assertEquals("say 𝐡𝐞𝐥𝐥𝐨 now", field.text.toString())
        assertNull(c.state.value.textStyle) // restyling a selection doesn't turn styled typing on
        c.onKey(KeyAction.SetTextStyle(TextStyle.ITALIC))
        assertEquals("say ℎ𝑒𝑙𝑙𝑜 now", field.text.toString())
        c.onKey(KeyAction.SetTextStyle(null))
        assertEquals("say hello now", field.text.toString())
    }

    @Test fun `bangla date button and snippet variables`() {
        val field = SelectEditor("")
        val c = controller(field, mapOf("@sig" to "Thanks,\n{cursor}\nYeamin"))
        c.onKey(KeyAction.InsertDateTime(DateTimeKind.BANGLA_DATE))
        assertTrue(field.text.toString().matches(Regex("[০-৯]+ [^ ]+ [০-৯]{4}")))
        field.text.clear(); field.setSelection(0, 0)
        "@sig".forEach { c.onKey(KeyAction.Text(it.toString())) }
        c.onKey(KeyAction.Space)
        assertEquals("Thanks,\n\nYeamin", field.text.toString())
        assertEquals(8, field.selStart) // on the empty line, ready to type
    }
}
