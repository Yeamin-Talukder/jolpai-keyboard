package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Emoji, in-keyboard search and voice typing in the controller. */
class MediaControllerTest {
    private var now = 10_000L
    private val editor = FakeEditor().apply { capSentences = false }
    private val used = mutableListOf<String>()
    private val emojiKeywords = mapOf("pizza" to listOf("🍕"), "হাসি" to listOf("😄"))
    private val controller = KeyboardController(
        editor,
        clock = { now },
        emojiFor = { emojiKeywords[it.lowercase()].orEmpty() },
        emojiSearch = { q -> if ("cat".startsWith(q)) listOf("🐱", "😺") else emptyList() },
    ).apply {
        onEmojiUsed = { used += it }
        updateSettings(KeyboardSettings(autoCapitalize = false))
        onStartInput(FieldInfo.DEFAULT, restarting = false)
    }

    private fun key(a: KeyAction) {
        now += 300
        controller.onKey(a)
    }

    private fun type(s: String) = s.forEach { c -> key(if (c == ' ') KeyAction.Space else KeyAction.Text(c.toString())) }
    private val state get() = controller.state.value

    @Test fun `emoji from the panel is inserted and recorded`() {
        key(KeyAction.ShowPanel(Panel.EMOJI))
        key(KeyAction.InsertEmoji("😀"))
        assertEquals("😀", editor.text.toString())
        assertEquals(listOf("😀"), used)
    }

    @Test fun `emoji suggested for a word goes after it with a space`() {
        key(KeyAction.PasteText("pizza"))
        key(KeyAction.InsertEmoji("🍕", afterWord = true))
        assertEquals("pizza 🍕", editor.text.toString())
    }

    @Test fun `search mode types into the panel search box, not the app`() {
        type("hi ")
        key(KeyAction.ShowPanel(Panel.EMOJI))
        key(KeyAction.StartSearch(Panel.EMOJI))
        assertEquals(Panel.EMOJI, state.search)
        assertEquals(Panel.NONE, state.panel) // keys are shown while searching
        type("ca")
        assertEquals("ca", state.searchQuery)
        assertEquals("hi ", editor.text.toString())
        // Live results in the strip, after cancel and the query.
        assertEquals(listOf("✕", "🔍 ca", "🐱", "😺"), state.strip.map { it.label })
        key(KeyAction.Enter)
        assertNull(state.search)
        assertEquals(Panel.EMOJI, state.panel)
        assertEquals("ca", state.panelQuery)
    }

    @Test fun `cancelling a search clears the query`() {
        key(KeyAction.StartSearch(Panel.CLIPBOARD))
        type("abc")
        key(KeyAction.EndSearch(keepQuery = false))
        assertEquals(Panel.CLIPBOARD, state.panel)
        assertEquals("", state.panelQuery)
    }

    @Test fun `avro search types bangla`() {
        key(KeyAction.SelectLayout(LayoutId.AVRO))
        key(KeyAction.StartSearch(Panel.EMOJI))
        type("hasi")
        key(KeyAction.Enter)
        assertEquals("হাসি", state.panelQuery)
        assertEquals("", editor.text.toString())
    }

    @Test fun `voice partial results are composing, final results are committed with a space`() {
        key(KeyAction.VoicePartial("hello"))
        assertEquals("hello", editor.composingText)
        key(KeyAction.VoicePartial("hello world"))
        assertEquals("hello world", editor.composingText)
        key(KeyAction.VoiceFinal("hello world"))
        assertEquals("hello world ", editor.text.toString())
        assertNull(editor.composingText)
    }

    @Test fun `voice language follows the layout unless set`() {
        assertEquals("en-US", state.voiceLanguageTag)
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        assertEquals("bn-BD", state.voiceLanguageTag)
        controller.updateSettings(KeyboardSettings(autoCapitalize = false, voiceLanguage = VoiceLanguage.ENGLISH))
        assertEquals("en-US", state.voiceLanguageTag)
    }

    @Test fun `without word suggestions the strip stays empty`() {
        // Emoji suggestions ride along with word suggestions, which need a suggester.
        type("pizza")
        assertTrue(state.strip.isEmpty())
    }
}

class NumberRowTest {
    @Test fun `number row adds digits above the letters`() {
        val controller = KeyboardController(FakeEditor(), clock = { 0L })
        controller.updateSettings(KeyboardSettings(autoCapitalize = false, numberRow = true))
        controller.onStartInput(FieldInfo.DEFAULT, restarting = false)
        val rows = controller.state.value.layout.rows
        assertEquals("1", rows[0].keys[0].label)
        assertEquals("q", rows[1].keys[0].label)
        assertNull(rows[1].keys[0].hint) // digit hints move to the number row
        controller.onKey(KeyAction.SelectLayout(LayoutId.NATIONAL))
        assertEquals("১", controller.state.value.layout.rows[0].keys[0].label)
    }
}
