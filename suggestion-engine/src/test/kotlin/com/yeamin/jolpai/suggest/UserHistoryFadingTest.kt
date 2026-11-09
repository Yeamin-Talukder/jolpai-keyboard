package com.yeamin.jolpai.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserHistoryFadingTest {
    private var now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun history() = UserHistory(clock = { now })

    /** Types [sentence] word by word, as the keyboard does (with the two words before each one). */
    private fun UserHistory.type(sentence: String, language: Language = Language.BANGLA) {
        val w = sentence.split(" ")
        w.forEachIndexed { i, word -> recordWord(language, word, w.getOrNull(i - 1), w.getOrNull(i - 2)) }
    }

    @Test fun `three-word sequences beat pairs`() {
        val h = history()
        repeat(3) { h.type("আমি ভাত খাই") }
        repeat(4) { h.type("তুমি ভাত রান্না") }
        // After "ভাত" alone, রান্না was typed more often…
        assertEquals("রান্না", h.nextWords(Language.BANGLA, "ভাত", 2).first())
        // …but after "আমি ভাত" the user always wrote খাই.
        assertEquals("খাই", h.nextWordScores(Language.BANGLA, "আমি", "ভাত", 2).first().first)
    }

    @Test fun `old habits fade and recent ones win`() {
        val h = history()
        repeat(10) { h.type("good morning", Language.ENGLISH) }
        now += 90 * day // three months later the user switches
        repeat(3) { h.type("good night", Language.ENGLISH) }
        assertEquals(listOf("night", "morning"), h.nextWords(Language.ENGLISH, "good", 2))
        // Counts don't fade: "morning" is still the user's own word.
        assertEquals(10, h.count(Language.ENGLISH, "morning"))
    }

    @Test fun `a weight halves every half-life and long-unused pairs drop out`() {
        val h = history()
        h.type("শুভ সকাল")
        val fresh = h.pairWeight(Language.BANGLA, "শুভ", "সকাল")
        now += UserHistory.HALF_LIFE_MS
        assertEquals(fresh / 2, h.pairWeight(Language.BANGLA, "শুভ", "সকাল"), 0.001f)
        now += 6 * UserHistory.HALF_LIFE_MS
        assertTrue(h.nextWords(Language.BANGLA, "শুভ", 3).isEmpty())
    }

    @Test fun `learning survives a reload, older saves without times count as fresh`() {
        val saved = ArrayList<LearnedTrigram>()
        val store = object : HistoryStore {
            override fun saveWord(word: LearnedWord) = Unit
            override fun saveBigram(bigram: LearnedBigram) = Unit
            override fun saveTrigram(trigram: LearnedTrigram) { saved += trigram }
            override fun saveSelection(roman: String, bangla: String) = Unit
            override fun savePersonal(word: PersonalWord, present: Boolean) = Unit
            override fun deleteLearned(language: Language, word: String) = Unit
            override fun clearLearned() = Unit
        }
        UserHistory(store, clock = { now }).type("আমি তোমাকে ভালোবাসি")
        assertEquals(1, saved.size)
        val reloaded = history()
        reloaded.load(
            HistorySnapshot(
                bigrams = listOf(LearnedBigram(Language.BANGLA, "তোমাকে", "দেখি", 5)), // saved before fading existed
                trigrams = saved,
            ),
        )
        val next = reloaded.nextWordScores(Language.BANGLA, "আমি", "তোমাকে", 3).map { it.first }
        assertEquals(listOf("দেখি", "ভালোবাসি"), next) // 5 old uses vs one three-word use (×2)
        now += 10 * day
        reloaded.type("আমি তোমাকে ভালোবাসি")
        reloaded.type("আমি তোমাকে ভালোবাসি")
        assertEquals("ভালোবাসি", reloaded.nextWordScores(Language.BANGLA, "আমি", "তোমাকে", 3).first().first)
    }

    @Test fun `forgetting a word removes it from sequences too`() {
        val h = history()
        h.type("আমি ভাত খাই")
        h.forget(Language.BANGLA, "খাই")
        assertTrue(h.nextWordScores(Language.BANGLA, "আমি", "ভাত", 3).isEmpty())
    }
}
