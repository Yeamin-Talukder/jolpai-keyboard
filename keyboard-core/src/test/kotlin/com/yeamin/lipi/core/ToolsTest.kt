package com.yeamin.lipi.core

import com.yeamin.lipi.core.editor.EditorCommand
import com.yeamin.lipi.core.field.FieldInfo
import com.yeamin.lipi.core.model.DateTimeKind
import com.yeamin.lipi.core.model.EditCommand
import com.yeamin.lipi.core.model.KeyAction
import com.yeamin.lipi.core.model.LayoutId
import com.yeamin.lipi.core.model.Panel
import com.yeamin.lipi.core.settings.KeyboardSettings
import com.yeamin.lipi.core.tools.Calculator
import com.yeamin.lipi.core.tools.TextUnits
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {
    private fun r(s: String) = Calculator.resultFor(s)?.value

    @Test fun basics() {
        assertEquals("40", r("12*3+4="))
        assertEquals("2.5", r("5/2="))
        assertEquals("-1", r("2-3="))
        assertEquals("14", r("2*(3+4)="))
        assertEquals("6", r("2 × 3="))
        assertEquals("4", r("8÷2="))
    }

    @Test fun `expression after words`() = assertEquals("15", r("total is 10+5="))

    @Test fun `bangla digits stay bangla`() = assertEquals("১৫", r("১২+৩="))

    @Test fun `not a calculation`() {
        assertNull(r("2025="))
        assertNull(r("a=b"))
        assertNull(r("1/0="))
        assertNull(r("3+="))
        assertNull(r("hello"))
    }
}

class TextUnitsTest {
    @Test fun `word before cursor`() {
        assertEquals(5, TextUnits.wordLengthBefore("say hello"))
        assertEquals(6, TextUnits.wordLengthBefore("say hello "))
        assertEquals(3, TextUnits.wordLengthBefore("ok..."))
        assertEquals(5, TextUnits.wordLengthBefore("আমি বাংলা")) // kars and signs count as letters
    }

    @Test fun `word after cursor`() = assertEquals(6, TextUnits.wordLengthAfter(" hello world"))

    @Test fun `last token`() = assertEquals("@addr", TextUnits.lastToken("my @addr"))
}

class ToolsControllerTest {
    private var now = 10_000L
    private val editor = FakeEditor().apply { capSentences = false }
    private val snippets = mapOf("@addr" to "House 12, Road 5, Dhaka", "sig" to "— Yeamin")
    private val wall = Calendar.getInstance(TimeZone.getDefault()).apply { set(2026, Calendar.NOVEMBER, 6, 14, 30) }.timeInMillis
    private val controller = KeyboardController(
        editor,
        clock = { now },
        snippetFor = { snippets[it] },
        wallClock = { wall },
    ).apply {
        updateSettings(KeyboardSettings(autoCapitalize = false))
        onStartInput(FieldInfo.DEFAULT, restarting = false)
    }

    private fun key(a: KeyAction) {
        now += 300
        controller.onKey(a)
    }

    private fun type(s: String) = s.forEach { c -> key(if (c == ' ') KeyAction.Space else KeyAction.Text(c.toString())) }

    @Test fun `snippet shortcut expands on space`() {
        type("at @addr ")
        assertEquals("at House 12, Road 5, Dhaka ", editor.text.toString())
    }

    @Test fun `snippet is offered in the strip`() {
        type("sig")
        val item = controller.state.value.strip.single()
        assertTrue(item.label.startsWith("✎"))
        key(item.action)
        assertEquals("— Yeamin ", editor.text.toString())
    }

    @Test fun `snippet shortcut also expands in avro`() {
        key(KeyAction.SelectLayout(LayoutId.AVRO))
        type("sig ")
        assertEquals("— Yeamin ", editor.text.toString())
    }

    @Test fun `calculator result in strip`() {
        type("12*3=")
        val item = controller.state.value.strip.single()
        assertEquals("= 36", item.label)
        key(item.action)
        assertEquals("12*3=36", editor.text.toString())
    }

    @Test fun `date and time insertion`() {
        key(KeyAction.InsertDateTime(DateTimeKind.DATE))
        assertTrue(editor.text.toString(), editor.text.contains("2026"))
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        key(KeyAction.InsertDateTime(DateTimeKind.DATE))
        assertTrue(editor.text.toString(), editor.text.contains("২০২৬"))
    }

    @Test fun `editing commands go to the app, extending the selection in select mode`() {
        key(KeyAction.Edit(EditCommand.LEFT))
        key(KeyAction.Edit(EditCommand.TOGGLE_SELECT))
        assertTrue(controller.state.value.selecting)
        key(KeyAction.Edit(EditCommand.LEFT))
        key(KeyAction.Edit(EditCommand.COPY))
        assertEquals(
            listOf(EditorCommand.LEFT to false, EditorCommand.LEFT to true, EditorCommand.COPY to false),
            editor.commands,
        )
        assertTrue(!controller.state.value.selecting) // copying ends selection mode
    }

    @Test fun `delete word`() {
        type("hello world")
        key(KeyAction.Edit(EditCommand.DELETE_WORD))
        assertEquals("hello ", editor.text.toString())
    }

    @Test fun `select previous word`() {
        type("hello world")
        key(KeyAction.Edit(EditCommand.SELECT_WORD_LEFT))
        assertEquals(6 to 11, editor.selection)
    }

    @Test fun `space swipe moves the cursor`() {
        key(KeyAction.MoveCursor(-3))
        assertEquals(3, editor.commands.count { it.first == EditorCommand.LEFT })
    }

    @Test fun `panel hides the strip and closes again`() {
        type("12*3=")
        key(KeyAction.ShowPanel(Panel.CLIPBOARD))
        assertEquals(Panel.CLIPBOARD, controller.state.value.panel)
        assertTrue(controller.state.value.strip.isEmpty())
        key(KeyAction.ShowPanel(Panel.NONE))
        assertEquals(Panel.NONE, controller.state.value.panel)
    }

    @Test fun `paste text is inserted as is`() {
        key(KeyAction.SelectLayout(LayoutId.AVRO))
        type("ami")
        key(KeyAction.PasteText("copied"))
        assertEquals("আমিcopied", editor.text.toString())
    }

    @Test fun `no tools in password fields`() {
        controller.onStartInput(FieldInfo(isPassword = true), restarting = false)
        type("@addr ")
        assertEquals("@addr ", editor.text.toString())
    }
}
