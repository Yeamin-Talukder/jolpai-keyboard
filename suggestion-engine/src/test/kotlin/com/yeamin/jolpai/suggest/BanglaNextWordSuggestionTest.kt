package com.yeamin.jolpai.suggest

import org.junit.Assert.assertEquals
import org.junit.Test

class BanglaNextWordSuggestionTest {
    private val dict = BanglaDictionary.of(listOf("আমি", "তুমি", "ভাত", "খাই", "খাবো", "ভালো", "আছি", "আছেন", "কেমন")).also {
        it.nextWords = BanglaNextWords.of(
            "<s>\tআমি:255 তুমি:150\nকেমন\tআছেন:255 আছো:180\nভাত\tখাবো:255 খাই:200\n",
            "আমি ভাত\tখাই:255\n",
        )
    }
    private val history = UserHistory()
    private val suggester = Suggester({ null }, { dict }, { null }, history)

    private fun next(sentence: String): List<String> {
        val words = sentence.split(" ").filter { it.isNotEmpty() }
        return suggester.nextWords(Language.BANGLA, words.lastOrNull(), 3, SuggestOptions(context = words))
    }

    @Test fun `the list gives next words, sentence starts and two-word contexts`() {
        assertEquals(listOf("আমি", "তুমি"), next(""))
        assertEquals("আছেন", next("কেমন").first())
        assertEquals("খাবো", next("তুমি ভাত").first()) // only the one-word context matches
        assertEquals("খাই", next("আমি ভাত").first()) // the two-word context wins
    }

    @Test fun `what the user types there outranks the list`() {
        repeat(2) {
            history.recordWord(Language.BANGLA, "কেমন", null)
            history.recordWord(Language.BANGLA, "আছিস", "কেমন")
        }
        assertEquals("আছিস", next("কেমন").first())
        assertEquals("আছেন", next("কেমন")[1])
    }
}
