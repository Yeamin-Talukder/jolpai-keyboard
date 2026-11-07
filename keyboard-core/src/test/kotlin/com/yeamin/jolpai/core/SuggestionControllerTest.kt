package com.yeamin.jolpai.core

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.field.FieldKind
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.suggest.WordContext
import com.yeamin.jolpai.suggest.AvroDictionarySearch
import com.yeamin.jolpai.suggest.AvroSuggester
import com.yeamin.jolpai.suggest.BanglaDictionary
import com.yeamin.jolpai.suggest.EnglishDictionary
import com.yeamin.jolpai.suggest.Language
import com.yeamin.jolpai.suggest.Suggester
import com.yeamin.jolpai.suggest.UserHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The controller with real dictionaries: autocorrect, undo, candidates, learning, privacy. */
class SuggestionControllerTest {
    companion object {
        private val phonetic = lazy { PhoneticEngine(AvroGrammar.load()) }
        private val english by lazy { EnglishDictionary.load() }
        private val bangla by lazy { BanglaDictionary.load() }
        private val avro by lazy { AvroSuggester.load(phonetic.value, AvroDictionarySearch.load(bangla)) }
    }

    private var now = 10_000L
    private val editor = FakeEditor().apply { capSentences = false }
    private val history = UserHistory()
    private val controller = KeyboardController(
        editor,
        clock = { now },
        suggester = Suggester({ english }, { bangla }, { avro }, history),
        avroEngine = phonetic,
    ).apply {
        updateSettings(KeyboardSettings(autoCapitalize = false))
        onStartInput(FieldInfo.DEFAULT, restarting = false)
    }

    private fun key(a: KeyAction) {
        now += 300
        controller.onKey(a)
    }

    private fun type(s: String) = s.forEach { c -> key(if (c == ' ') KeyAction.Space else KeyAction.Text(c.toString())) }
    private val strip get() = controller.state.value.strip.map { it.label }
    private val text get() = editor.text.toString()

    @Test fun `strip shows typed word, correction and alternatives`() {
        type("teh")
        assertEquals("“teh”", strip[0])
        assertEquals("the", strip[1])
        assertTrue(controller.state.value.strip[1].emphasized)
    }

    @Test fun `space autocorrects and backspace undoes it`() {
        type("teh ")
        assertEquals("the ", text)
        key(KeyAction.Delete)
        assertEquals("teh", text)
        type(" ")
        assertEquals("teh ", text) // not corrected a second time
    }

    @Test fun `punctuation also autocorrects`() {
        type("teh.")
        assertEquals("the.", text)
    }

    @Test fun `autocorrect off leaves typos`() {
        controller.updateSettings(KeyboardSettings(autoCapitalize = false, autoCorrect = false))
        type("teh ")
        assertEquals("teh ", text)
    }

    @Test fun `tapping a suggestion replaces the word and adds a space`() {
        type("hel")
        key(KeyAction.CommitCandidate("hello"))
        assertEquals("hello ", text)
    }

    @Test fun `keeping an unknown word offers to add it`() {
        type("lipiz")
        key(KeyAction.CommitCandidate("lipiz", verbatim = true))
        assertEquals("lipiz ", text)
        val item = controller.state.value.strip.single()
        assertTrue(item.label.contains("lipiz"))
        key(item.action)
        assertTrue(history.isPersonal(Language.ENGLISH, "lipiz"))
        type("lipiz ")
        assertEquals("lipiz lipiz ", text)
    }

    @Test fun `next word prediction after learning`() {
        type("good morning ")
        type("good ")
        assertTrue(strip.toString(), "morning" in strip)
        key(KeyAction.CommitCandidate("morning"))
        assertEquals("good morning good morning ", text)
    }

    @Test fun `nothing is learned or suggested in a password field`() {
        controller.onStartInput(FieldInfo(isPassword = true), restarting = false)
        type("secretword ")
        assertTrue(controller.state.value.strip.isEmpty())
        assertEquals(0, history.count(Language.ENGLISH, "secretword"))
        assertEquals("secretword ", text) // and never autocorrected
    }

    @Test fun `incognito fields get suggestions but nothing is learned`() {
        controller.onStartInput(FieldInfo(noPersonalizedLearning = true), restarting = false)
        type("hel")
        assertTrue(strip.isNotEmpty())
        type("lo ")
        assertEquals(0, history.count(Language.ENGLISH, "hello"))
    }

    @Test fun `learning can be turned off`() {
        controller.updateSettings(KeyboardSettings(autoCapitalize = false, personalizedLearning = false))
        type("hello ")
        assertEquals(0, history.count(Language.ENGLISH, "hello"))
    }

    @Test fun `email fields get no suggestions`() {
        controller.onStartInput(FieldInfo(kind = FieldKind.EMAIL), restarting = false)
        type("teh")
        assertTrue(controller.state.value.strip.isEmpty())
    }

    @Test fun `avro shows dictionary candidates and remembers the pick`() {
        key(KeyAction.SelectLayout(LayoutId.AVRO))
        type("bhalobashi")
        assertEquals("ভালবাসি", editor.composingText)
        assertTrue(strip.toString(), "ভালোবাসি" in strip)
        assertEquals("bhalobashi", strip.last()) // English as typed is always offered
        key(KeyAction.CommitCandidate("ভালোবাসি"))
        assertEquals("ভালোবাসি ", text)
        type("bhalobashi")
        assertEquals("ভালোবাসি", editor.composingText)
    }

    @Test fun `avro space commits the default candidate`() {
        key(KeyAction.SelectLayout(LayoutId.AVRO))
        type("ami banglay gan gai ")
        assertEquals("আমি বাংলায় গান গাই ", text)
    }

    @Test fun `national layout suggests completions`() {
        key(KeyAction.SelectLayout(LayoutId.NATIONAL))
        listOf("ব", "া", "ং", "ল", "া", "দ", "ে").forEach { key(KeyAction.Text(it)) }
        assertTrue(strip.toString(), strip.any { it.startsWith("বাংলাদেশ") })
    }

    @Test fun `suggestions can be turned off`() {
        controller.updateSettings(KeyboardSettings(autoCapitalize = false, showSuggestions = false))
        type("teh ")
        assertTrue(controller.state.value.strip.isEmpty())
        assertEquals("teh ", text)
    }
}

class WordContextTest {
    @Test fun english() {
        assertEquals(WordContext("good", "mor"), WordContext.english("good mor"))
        assertEquals(WordContext("good", ""), WordContext.english("good "))
        assertEquals(WordContext(null, "the"), WordContext.english("end. the"))
        assertEquals(WordContext(null, "don't"), WordContext.english("don't"))
        assertEquals(WordContext.EMPTY, WordContext.english(""))
    }

    @Test fun bangla() {
        assertEquals(WordContext("আমি", "বাং"), WordContext.bangla("আমি বাং"))
        assertEquals(WordContext(null, ""), WordContext.bangla("আমি। "))
        assertNull(WordContext.bangla("hello আমি").previous)
        assertFalse(WordContext.bangla("২০২৬").current.isNotEmpty())
    }
}
