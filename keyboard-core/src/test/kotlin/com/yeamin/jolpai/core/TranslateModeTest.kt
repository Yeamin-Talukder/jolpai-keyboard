package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateModeTest {
    private val app = FakeEditor()
    private val controller = KeyboardController(app, clock = { 0L }).apply {
        updateSettings(KeyboardSettings(autoCapitalize = false))
        onStartInput(FieldInfo.DEFAULT, restarting = false)
    }

    private fun type(s: String) = s.forEach { controller.onKey(KeyAction.Text(it.toString())) }
    private val translate get() = controller.state.value.translate

    @Test fun `keys type into the translate box, not the app, with English keys`() {
        controller.onKey(KeyAction.SelectLayout(LayoutId.NATIONAL))
        controller.onKey(KeyAction.StartTranslate(toBangla = true))
        assertEquals(LayoutId.ENGLISH, controller.state.value.layoutId)
        type("good morning")
        assertEquals("good morning", translate!!.source)
        assertEquals("", app.text.toString())
        assertTrue(controller.state.value.strip.isEmpty()) // no suggestions or learning in the box
        assertNull(translate!!.translation)
    }

    @Test fun `enter inserts the translation of exactly the typed text`() {
        controller.onKey(KeyAction.StartTranslate())
        type("thanks")
        controller.setTranslation("thank", "ধন্যবাদ") // an older answer: not shown, not inserted
        assertNull(translate!!.translation)
        controller.onKey(KeyAction.Enter)
        assertEquals("", app.text.toString())

        controller.setTranslation("thanks", "ধন্যবাদ")
        assertEquals("ধন্যবাদ", translate!!.translation)
        controller.onKey(KeyAction.Enter)
        assertEquals("ধন্যবাদ ", app.text.toString())
        assertEquals("", translate!!.source) // box emptied, still open
    }

    @Test fun `typing more drops the old translation`() {
        controller.onKey(KeyAction.StartTranslate())
        type("hi")
        controller.setTranslation("hi", "হাই")
        type("!")
        assertNull(translate!!.translation)
    }

    @Test fun `swap translates back and closing restores the layout`() {
        controller.onKey(KeyAction.SelectLayout(LayoutId.AVRO))
        controller.onKey(KeyAction.StartTranslate())
        type("water")
        controller.setTranslation("water", "জল")
        controller.onKey(KeyAction.SwapTranslate)
        val t = translate!!
        assertEquals(false, t.toBangla)
        assertEquals("জল", t.source)
        assertEquals(LayoutId.AVRO, controller.state.value.layoutId) // the user's Bangla layout

        controller.onKey(KeyAction.EndTranslate)
        assertNull(translate)
        assertEquals(LayoutId.AVRO, controller.state.value.layoutId)
        type("a")
        assertTrue(app.text.isNotEmpty()) // keys type into the app again
    }

    @Test fun `insert button, clear, and opening a panel`() {
        app.commit("Hello")
        controller.onKey(KeyAction.StartTranslate())
        type("yes")
        controller.onKey(KeyAction.CommitTranslation("হ্যাঁ"))
        assertEquals("Hello হ্যাঁ ", app.text.toString())
        type("no")
        controller.onKey(KeyAction.ClearTranslate)
        assertEquals("", translate!!.source)
        controller.onKey(KeyAction.ShowPanel(Panel.EMOJI))
        assertNull(translate)
    }

    @Test fun `cut, copy and paste work in the translate box`() {
        var clip: String? = "good night"
        controller.boxClipboard = object : BoxClipboard {
            override fun copy(text: String) { clip = text }
            override fun paste(): String? = clip
        }
        controller.onKey(KeyAction.StartTranslate())
        controller.onKey(KeyAction.Edit(com.yeamin.jolpai.core.model.EditCommand.PASTE))
        assertEquals("good night", translate!!.source)

        type(" all")
        controller.onKey(KeyAction.Edit(com.yeamin.jolpai.core.model.EditCommand.COPY))
        assertEquals("good night all", clip)

        controller.onKey(KeyAction.Edit(com.yeamin.jolpai.core.model.EditCommand.CUT))
        assertEquals("good night all", clip)
        assertEquals("", translate!!.source)
        assertEquals("", app.text.toString()) // the app's own text is never touched
    }

    @Test fun `a new text field closes translate`() {
        controller.onKey(KeyAction.StartTranslate())
        controller.onStartInput(FieldInfo.DEFAULT, restarting = false)
        assertNull(translate)
    }

    @Test fun `translate is a default toolbar tool`() {
        assertTrue(ToolbarItem.TRANSLATE in ToolbarItem.DEFAULT)
    }
}
