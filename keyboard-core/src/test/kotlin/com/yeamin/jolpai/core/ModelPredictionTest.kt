package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.suggest.EnglishDictionary
import com.yeamin.jolpai.suggest.NextWordModel
import com.yeamin.jolpai.suggest.Suggester
import com.yeamin.jolpai.suggest.UserHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keyboard with the English next-word model: next words, context ranking, emoji, check card. */
class ModelPredictionTest {
    companion object {
        private val english by lazy { EnglishDictionary.load() }
        private val model by lazy { NextWordModel.loadEnglish() }
    }

    private var now = 10_000L
    private val editor = FakeEditor().apply { capSentences = false }
    private fun controller(settings: KeyboardSettings = KeyboardSettings(autoCapitalize = false, autoCorrect = true)) = KeyboardController(
        editor,
        clock = { now },
        suggester = Suggester({ english }, { null }, { null }, UserHistory(), model = { model }),
        emojiPredict = { words -> if ("coffee" in words) listOf("☕") else emptyList() },
    ).apply {
        updateSettings(settings)
        onStartInput(FieldInfo.DEFAULT, restarting = false)
    }

    private val c = controller()

    private fun type(s: String, k: KeyboardController = c) = s.forEach { ch ->
        now += 300
        k.onKey(if (ch == ' ') KeyAction.Space else KeyAction.Text(ch.toString()))
    }

    private fun strip(k: KeyboardController = c) = k.state.value.strip.map { it.label }

    @Test fun `next words come from the sentence without any history`() {
        type("i would like to ")
        assertEquals(listOf("thank", "know", "see"), strip())
    }

    @Test fun `the start of a sentence gets predictions too`() {
        assertTrue(strip().isNotEmpty())
        assertTrue("I" in strip()) // the word list's spelling, not "i"
    }

    @Test fun `completions are ranked by the sentence`() {
        type("i would like to th")
        assertTrue(strip().toString(), "thank" in strip())
    }

    @Test fun `autocorrect picks the fix that fits the sentence`() {
        type("i want to takl ")
        assertEquals("I want to talk ", editor.text.toString())
    }

    @Test fun `an emoji that fits the sentence is offered after a space`() {
        type("coffee ")
        assertEquals("☕", strip().last())
    }

    @Test fun `turning the model off brings back history-only next words`() {
        val off = controller(KeyboardSettings(autoCapitalize = false, smartPredictions = false))
        type("i would like to ", off)
        assertTrue(strip(off).isEmpty())
    }

    @Test fun `the check card catches a wrong word choice`() {
        type("i think their going home now. ")
        now += 300
        c.onKey(KeyAction.StartCheck)
        val issue = c.state.value.check!!.issues.firstOrNull { it.original == "their" }
        assertEquals("they're", issue?.replacement)
    }

    @Test fun `correct text gets no word choice issues`() {
        listOf(
            "they left their bags over there. ",
            "it is bigger than i thought and then it fell. ",
            "your dog is quite quiet. ",
            "whose phone is this? ",
        ).forEach { s ->
            val k = controller()
            editor.text.clear()
            type(s, k)
            now += 300
            k.onKey(KeyAction.StartCheck)
            val grammar = k.state.value.check!!.issues.filter { it.reason.contains("fits this sentence") }
            assertTrue("$s → $grammar", grammar.isEmpty())
        }
    }
}
