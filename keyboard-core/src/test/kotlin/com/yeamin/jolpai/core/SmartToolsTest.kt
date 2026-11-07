package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.layout.LayoutFactory
import com.yeamin.jolpai.core.model.EditCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.tools.Calculator
import com.yeamin.jolpai.data.ClipCategory
import com.yeamin.jolpai.data.SnippetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Long-press editing shortcuts, the calculator without "=", and clipboard/snippet categories. */
class SmartToolsTest {

    // ------------------------------------------------------------------ x / c / v shortcuts

    @Test fun `holding x c v cuts copies and pastes on English and Avro`() {
        listOf(LayoutId.ENGLISH, LayoutId.AVRO).forEach { id ->
            val keys = LayoutFactory().build(id, KeyboardPage.LETTERS, false, FieldInfo.DEFAULT).rows.flatMap { it.keys }
            fun hold(label: String) = keys.first { it.label == label }.longPress
            assertEquals(KeyAction.Edit(EditCommand.CUT), hold("x"))
            assertEquals(KeyAction.Edit(EditCommand.COPY), hold("c"))
            assertEquals(KeyAction.Edit(EditCommand.PASTE), hold("v"))
            assertNull(hold("e"))
        }
        val shifted = LayoutFactory().build(LayoutId.ENGLISH, KeyboardPage.LETTERS, true, FieldInfo.DEFAULT).rows.flatMap { it.keys }
        assertEquals(KeyAction.Edit(EditCommand.PASTE), shifted.first { it.label == "V" }.longPress)
    }

    // ------------------------------------------------------------------ number and phone pads

    @Test fun `number fields get a calculator-style pad`() {
        val l = LayoutFactory().build(LayoutId.ENGLISH, KeyboardPage.NUMBER, false, FieldInfo.DEFAULT)
        assertEquals(listOf("+", "1", "2", "3", "⌫"), l.rows[0].keys.map { it.label })
        assertEquals(listOf("ABC", ",", "0", ".", "↵"), l.rows[3].keys.map { it.label })
        assertEquals(KeyAction.SwitchPage(KeyboardPage.LETTERS), l.rows[3].keys[0].action)
        // From the letters in a number field, the page key returns to the number pad.
        val letters = LayoutFactory().build(LayoutId.ENGLISH, KeyboardPage.LETTERS, false, FieldInfo(kind = com.yeamin.jolpai.core.field.FieldKind.NUMBER))
        assertEquals(KeyAction.SwitchPage(KeyboardPage.NUMBER), letters.rows.last().keys[0].action)
        assertEquals("123", letters.rows.last().keys[0].label)
        assertTrue(l.rows.all { kotlin.math.abs(it.units - l.rows[0].units) < 0.01f })
        assertEquals("১", l.rows[0].keys[1].popup.first().label) // hold 1 for ১
    }

    @Test fun `phone fields get a dial pad with letters`() {
        val l = LayoutFactory().build(LayoutId.ENGLISH, KeyboardPage.PHONE, false, FieldInfo.DEFAULT)
        assertEquals("ABC", l.rows[0].keys[2].hint)
        val zero = l.rows[3].keys.first { it.label == "0" }
        assertEquals("+", zero.popup.first().label)
    }

    // ------------------------------------------------------------------ calculator

    @Test fun `a sum shows its answer without typing equals`() {
        val r = Calculator.resultFor("price 250*4")!!
        assertEquals("1000", r.value)
        assertEquals(5, r.replace) // "250*4" is replaced by the answer
        assertEquals("15", Calculator.resultFor("১২+৩")?.value?.let { it.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("") })
        assertEquals("7", Calculator.resultFor("10 - 3")?.value)
        assertEquals("2.5", Calculator.resultFor("5 / 2")?.value)
        assertEquals("40", Calculator.resultFor("12*3+4=")?.value) // with "=" as before
        assertEquals(0, Calculator.resultFor("12*3+4=")?.replace)
    }

    @Test fun `phone numbers, dates and words are not sums`() {
        assertNull(Calculator.resultFor("01711-234567"))
        assertNull(Calculator.resultFor("call 017 11+2"))
        assertNull(Calculator.resultFor("12/05/2024"))
        assertNull(Calculator.resultFor("10-3"))
        assertNull(Calculator.resultFor("abc12+3"))
        assertNull(Calculator.resultFor("250*"))
        assertNull(Calculator.resultFor("2024"))
    }

    @Test fun `tapping the answer replaces the sum`() {
        val editor = FakeEditor("total 250*4")
        val controller = KeyboardController(editor, clock = { 0L }).apply {
            updateSettings(KeyboardSettings(autoCapitalize = false))
            onStartInput(FieldInfo.DEFAULT, restarting = false)
        }
        val item = controller.state.value.strip.single()
        assertEquals("= 1000", item.label)
        controller.onKey(item.action)
        assertEquals("total 1000", editor.text.toString())
    }

    // ------------------------------------------------------------------ categories

    @Test fun `clips are categorised automatically`() {
        assertEquals(ClipCategory.LINK, ClipCategory.classify("https://example.com/a?b=1"))
        assertEquals(ClipCategory.LINK, ClipCategory.classify("github.com/yeamin"))
        assertEquals(ClipCategory.EMAIL, ClipCategory.classify("someone@example.com"))
        assertEquals(ClipCategory.OTP, ClipCategory.classify("482913"))
        assertEquals(ClipCategory.PHONE, ClipCategory.classify("+880 1711-234567"))
        assertEquals(ClipCategory.PHONE, ClipCategory.classify("01711234567"))
        assertEquals(ClipCategory.ADDRESS, ClipCategory.classify("House 12, Road 5, Dhanmondi, Dhaka 1205"))
        assertEquals(ClipCategory.ADDRESS, ClipCategory.classify("বাসা ১২, রোড ৫, ধানমন্ডি, ঢাকা"))
        assertEquals(ClipCategory.CODE, ClipCategory.classify("fun main() {\n    println(\"hi\")\n}"))
        assertEquals(ClipCategory.CODE, ClipCategory.classify("const x = (a, b) => a + b;"))
        assertEquals(ClipCategory.TEXT, ClipCategory.classify("See you at 5 tomorrow, ok?"))
        assertEquals(ClipCategory.TEXT, ClipCategory.classify("আমি ভালো আছি"))
        assertEquals(ClipCategory.TEXT, ClipCategory.classify("3.5"))
    }

    @Test fun `snippet categories are suggested from the text`() {
        assertEquals(SnippetCategory.PHONE, SnippetCategory.suggest("+880 1711-234567"))
        assertEquals(SnippetCategory.ADDRESS, SnippetCategory.suggest("House 12, Road 5, Dhaka 1205"))
        assertEquals(SnippetCategory.SIGNATURE, SnippetCategory.suggest("Best regards,\nYeamin\nyeamin@example.com"))
        assertEquals(SnippetCategory.CODE, SnippetCategory.suggest("for (i in 0..9) {\n  print(i);\n}"))
        assertEquals(SnippetCategory.REPLY, SnippetCategory.suggest("On my way!"))
        assertEquals(SnippetCategory.MESSAGE, SnippetCategory.suggest("Thanks for your order. It will be delivered within three working days. Call us if anything is wrong."))
        assertEquals(SnippetCategory.OTHER, SnippetCategory.fromStorageKey("nonsense"))
        assertTrue(SnippetCategory.entries.all { SnippetCategory.fromStorageKey(it.storageKey) == it })
    }
}
