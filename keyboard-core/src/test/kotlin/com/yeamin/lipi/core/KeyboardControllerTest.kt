package com.yeamin.lipi.core

import com.yeamin.lipi.core.field.EnterAction
import com.yeamin.lipi.core.field.FieldInfo
import com.yeamin.lipi.core.field.FieldKind
import com.yeamin.lipi.core.model.KeyAction
import com.yeamin.lipi.core.model.KeyboardPage
import com.yeamin.lipi.core.model.LayoutId
import com.yeamin.lipi.core.model.ShiftState
import com.yeamin.lipi.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardControllerTest {

    private var now = 10_000L
    private val editor = FakeEditor()
    private val chosen = mutableListOf<LayoutId>()
    private val controller = KeyboardController(editor, clock = { now }).apply {
        onLayoutChosen = { chosen += it }
        updateSettings(KeyboardSettings())
        onStartInput(FieldInfo.DEFAULT, restarting = false)
    }

    private fun type(s: String) = s.forEach { c ->
        now += 200
        controller.onKey(if (c == ' ') KeyAction.Space else KeyAction.Text(c.toString()))
    }

    private fun key(a: KeyAction) {
        now += 200
        controller.onKey(a)
    }

    private val shift get() = controller.state.value.shift

    @Test fun `auto capitalises at the start of a field`() {
        assertEquals(ShiftState.ON, shift)
        assertEquals("Q", controller.state.value.layout.rows[0].keys[0].label)
    }

    @Test fun `auto shift turns off after the first letter`() {
        type("h")
        assertEquals(ShiftState.OFF, shift)
        assertEquals("q", controller.state.value.layout.rows[0].keys[0].label)
    }

    @Test fun `auto capitalises after a sentence ends`() {
        type("hi. ")
        assertEquals(ShiftState.ON, shift)
    }

    @Test fun `auto capitalise can be turned off`() {
        controller.updateSettings(KeyboardSettings(autoCapitalize = false))
        assertEquals(ShiftState.OFF, shift)
    }

    @Test fun `one tap shift capitalises one letter`() {
        type("a")
        key(KeyAction.Shift)
        assertEquals(ShiftState.ON, shift)
        controller.onKey(KeyAction.Text("B"))
        assertEquals(ShiftState.OFF, shift)
    }

    @Test fun `double tap shift locks caps`() {
        type("a")
        now += 200
        controller.onKey(KeyAction.Shift)
        now += 150
        controller.onKey(KeyAction.Shift)
        assertEquals(ShiftState.LOCKED, shift)
        controller.onKey(KeyAction.Text("B"))
        assertEquals(ShiftState.LOCKED, shift)
        key(KeyAction.Shift)
        assertEquals(ShiftState.OFF, shift)
    }

    @Test fun `slow second shift tap turns shift off instead of locking`() {
        type("a")
        key(KeyAction.Shift)
        now += 1000
        controller.onKey(KeyAction.Shift)
        assertEquals(ShiftState.OFF, shift)
    }

    @Test fun `double space inserts a full stop`() {
        type("Hi  ")
        assertEquals("Hi. ", editor.text.toString())
    }

    @Test fun `slow double space does not insert a full stop`() {
        type("Hi ")
        now += 2000
        controller.onKey(KeyAction.Space)
        assertEquals("Hi  ", editor.text.toString())
    }

    @Test fun `next layout cycles and is reported`() {
        key(KeyAction.NextLayout)
        assertEquals(LayoutId.AVRO, controller.state.value.layoutId)
        key(KeyAction.NextLayout)
        assertEquals(LayoutId.NATIONAL, controller.state.value.layoutId)
        assertEquals(listOf(LayoutId.AVRO, LayoutId.NATIONAL), chosen)
        key(KeyAction.NextLayout)
        assertEquals(LayoutId.ENGLISH, controller.state.value.layoutId)
    }

    @Test fun `next layout skips disabled layouts`() {
        controller.updateSettings(KeyboardSettings(enabledLayouts = listOf(LayoutId.ENGLISH, LayoutId.NATIONAL)))
        key(KeyAction.NextLayout)
        assertEquals(LayoutId.NATIONAL, controller.state.value.layoutId)
        key(KeyAction.NextLayout)
        assertEquals(LayoutId.ENGLISH, controller.state.value.layoutId)
    }

    @Test fun `disabling the active layout switches away from it`() {
        key(KeyAction.SelectLayout(LayoutId.AVRO))
        controller.updateSettings(KeyboardSettings(enabledLayouts = listOf(LayoutId.NATIONAL)))
        assertEquals(LayoutId.NATIONAL, controller.state.value.layoutId)
    }

    @Test fun `both bangla layouts type the same precomposed ya, rra and rha`() {
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        val shiftRow = com.yeamin.lipi.core.layout.NationalKeyMap.SHIFT[0]
        val normalRow = com.yeamin.lipi.core.layout.NationalKeyMap.NORMAL[0]
        assertEquals("য়", com.yeamin.lipi.core.layout.NationalKeyMap.HINTS[0][1]) // য় is W's hint and long-press (Ridmik)
        assertEquals("ড়", normalRow[9]) // ড়
        assertEquals("ঢ়", shiftRow[9]) // ঢ়
        key(KeyAction.SelectLayout(LayoutId.AVRO))
        type("ay ")
        assertEquals("আয় ", editor.text.toString())
    }

    // ------------------------------------------------------------------ Avro

    private fun avro() = key(KeyAction.SelectLayout(LayoutId.AVRO))

    @Test fun `avro shows the converted word as composing text`() {
        avro()
        type("ami")
        assertEquals("আমি", editor.composingText)
        assertEquals("ami", controller.state.value.composition?.roman)
        assertEquals("আমি", controller.state.value.composition?.bangla)
    }

    @Test fun `avro space commits the word`() {
        avro()
        type("ami banglay gan gai ")
        assertEquals("আমি বাংলায় গান গাই ", editor.text.toString())
        assertEquals(null, editor.composingText)
        assertEquals(null, controller.state.value.composition)
    }

    @Test fun `avro backspace removes the last roman key`() {
        avro()
        type("amr")
        assertEquals("আম্র", editor.text.toString())
        key(KeyAction.Delete)
        assertEquals("আম", editor.text.toString())
        key(KeyAction.Delete)
        key(KeyAction.Delete)
        assertEquals("", editor.text.toString())
        assertEquals(null, controller.state.value.composition)
    }

    @Test fun `avro backspace after commit deletes text`() {
        avro()
        type("ami ")
        key(KeyAction.Delete)
        key(KeyAction.Delete)
        assertEquals("আম", editor.text.toString())
    }

    @Test fun `avro candidate keeps english and adds a space`() {
        avro()
        type("google")
        key(KeyAction.CommitCandidate("google"))
        assertEquals("google ", editor.text.toString())
        assertEquals(null, controller.state.value.composition)
    }

    @Test fun `avro enter commits then runs enter`() {
        avro()
        type("ami")
        key(KeyAction.Enter)
        assertEquals("আমি\n", editor.text.toString())
    }

    @Test fun `avro case matters and there is no auto caps`() {
        avro()
        assertEquals(ShiftState.OFF, shift)
        key(KeyAction.Shift)
        key(KeyAction.Text("T"))
        type("a")
        assertEquals("টা", editor.composingText)
    }

    @Test fun `avro double space types danda`() {
        avro()
        type("ami  ")
        assertEquals("আমি। ", editor.text.toString())
    }

    @Test fun `avro digits become bangla digits`() {
        avro()
        type("2026 ")
        assertEquals("২০২৬ ", editor.text.toString())
    }

    @Test fun `switching layout commits the avro word`() {
        avro()
        type("ami")
        key(KeyAction.SelectLayout(LayoutId.ENGLISH))
        assertEquals("আমি", editor.text.toString())
        assertEquals(null, editor.composingText)
    }

    @Test fun `stale selection update does not drop the word`() {
        avro()
        type("ami")
        // An old update from before the word was composed: no composing region yet.
        controller.onSelectionChanged(0, 0, -1, -1)
        assertEquals("ami", controller.state.value.composition?.roman)
    }

    @Test fun `cursor moved away ends composing and keeps the text`() {
        avro()
        type("ami")
        editor.text.append(" tail") // the user moved the cursor: text before it no longer ends with the word
        controller.onSelectionChanged(8, 8, 0, 3)
        assertEquals(null, controller.state.value.composition)
    }

    @Test fun `national layout composes bangla and uses danda for double space`() {
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        listOf("ক", "ে", "া", "থ", "া", "য়").forEach { key(KeyAction.Text(it)) }
        key(KeyAction.Space)
        key(KeyAction.Space)
        assertEquals("কোথায়। ", editor.text.toString())
        assertEquals(ShiftState.OFF, shift) // no auto-caps in Bangla
    }

    @Test fun `national shift shows the shifted layer for one key`() {
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        key(KeyAction.Shift)
        assertEquals("ং", controller.state.value.layout.rows[0].keys[0].label)
        key(KeyAction.Text("ং"))
        assertEquals("ঙ", controller.state.value.layout.rows[0].keys[0].label)
    }

    @Test fun `remembered layout is used at start`() {
        val c = KeyboardController(FakeEditor(), clock = { now })
        c.updateSettings(KeyboardSettings(lastLayout = LayoutId.NATIONAL))
        c.onStartInput(FieldInfo.DEFAULT, restarting = false)
        assertEquals(LayoutId.NATIONAL, c.state.value.layoutId)
    }

    @Test fun `default layout is used when remembering is off`() {
        val c = KeyboardController(FakeEditor(), clock = { now })
        c.updateSettings(KeyboardSettings(rememberLastLayout = false, defaultLayout = LayoutId.NATIONAL, lastLayout = LayoutId.ENGLISH))
        c.onStartInput(FieldInfo.DEFAULT, restarting = false)
        assertEquals(LayoutId.NATIONAL, c.state.value.layoutId)
    }

    @Test fun `password and email fields open in english without changing the user's layout`() {
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        controller.onStartInput(FieldInfo(isPassword = true), restarting = false)
        assertEquals(LayoutId.ENGLISH, controller.state.value.layoutId)
        assertEquals(ShiftState.OFF, shift) // no auto-caps in password fields
        controller.onStartInput(FieldInfo(kind = FieldKind.EMAIL), restarting = false)
        assertEquals(LayoutId.ENGLISH, controller.state.value.layoutId)
        controller.onStartInput(FieldInfo.DEFAULT, restarting = false)
        assertEquals(LayoutId.NATIONAL, controller.state.value.layoutId)
    }

    @Test fun `number and phone fields open the pads`() {
        controller.onStartInput(FieldInfo(kind = FieldKind.NUMBER), restarting = false)
        assertEquals(KeyboardPage.NUMBER, controller.state.value.page)
        controller.onStartInput(FieldInfo(kind = FieldKind.PHONE), restarting = false)
        assertEquals(KeyboardPage.PHONE, controller.state.value.page)
    }

    @Test fun `email field puts at sign on the bottom row`() {
        controller.onStartInput(FieldInfo(kind = FieldKind.EMAIL), restarting = false)
        val labels = controller.state.value.layout.rows.last().keys.map { it.label }
        assertTrue("@" in labels)
    }

    @Test fun `enter runs the editor action or a newline`() {
        controller.onStartInput(FieldInfo(enterAction = EnterAction.SEND), restarting = false)
        key(KeyAction.Enter)
        assertEquals(listOf(EnterAction.SEND), editor.editorActions)
        controller.onStartInput(FieldInfo.DEFAULT, restarting = false)
        key(KeyAction.Enter)
        assertEquals("\n", editor.text.toString())
    }

    @Test fun `symbols page and back`() {
        key(KeyAction.SwitchPage(KeyboardPage.SYMBOLS))
        assertEquals("1", controller.state.value.layout.rows[0].keys[0].label)
        key(KeyAction.SwitchPage(KeyboardPage.LETTERS))
        assertEquals(KeyboardPage.LETTERS, controller.state.value.page)
    }

    @Test fun `bangla symbols page uses bangla digits`() {
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        key(KeyAction.SwitchPage(KeyboardPage.SYMBOLS))
        assertEquals("১", controller.state.value.layout.rows[0].keys[0].label)
    }
}
