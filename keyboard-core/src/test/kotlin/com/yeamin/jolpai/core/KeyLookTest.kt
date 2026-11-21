package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.layout.LayoutFactory
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.settings.KeyLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom row and letter hints of the Ridmik and Gboard looks (from screenshots of both, 2026-11-21). */
class KeyLookTest {

    private val two = listOf(LayoutId.ENGLISH, LayoutId.AVRO)

    private fun bottom(look: KeyLook, layouts: List<LayoutId> = two) =
        LayoutFactory(layouts, false, look).build(LayoutId.ENGLISH, KeyboardPage.LETTERS, false, FieldInfo.DEFAULT).rows.last().keys

    private fun key(look: KeyLook, letter: String) =
        LayoutFactory(two, false, look).build(LayoutId.ENGLISH, KeyboardPage.LETTERS, false, FieldInfo.DEFAULT)
            .rows.flatMap { it.keys }.first { it.label == letter }

    @Test fun `jolpai and ridmik put emoji before the comma`() {
        for (look in listOf(KeyLook.JOLPAI, KeyLook.RIDMIK)) {
            val b = bottom(look)
            assertEquals(KeyAction.ShowPanel(Panel.EMOJI), b[1].action)
            assertEquals(",", b[2].label)
        }
    }

    @Test fun `gboard puts the comma before emoji`() {
        val b = bottom(KeyLook.GBOARD)
        assertEquals(",", b[1].label)
        assertEquals(KeyAction.ShowPanel(Panel.EMOJI), b[2].action)
    }

    @Test fun `ridmik space bar shows arrows when there is a language to slide to`() {
        assertEquals("◂  English  ▸", bottom(KeyLook.RIDMIK).first { it.action == KeyAction.Space }.label)
        assertEquals("English", bottom(KeyLook.RIDMIK, listOf(LayoutId.ENGLISH)).first { it.action == KeyAction.Space }.label)
        assertEquals("English", bottom(KeyLook.JOLPAI).first { it.action == KeyAction.Space }.label)
    }

    @Test fun `ridmik letters show symbol hints and holding types them`() {
        val a = key(KeyLook.RIDMIK, "a")
        assertEquals("@", a.hint)
        assertEquals("@", a.popup.first().label)
        assertEquals("/", key(KeyLook.RIDMIK, "m").hint)
        assertNull(key(KeyLook.JOLPAI, "a").hint)
        // The top row keeps its digits.
        assertEquals("1", key(KeyLook.RIDMIK, "q").hint)
    }

    @Test fun `ridmik look keeps the copy and paste shortcuts on x c v`() {
        assertTrue(key(KeyLook.RIDMIK, "c").longPress != null)
    }
}
