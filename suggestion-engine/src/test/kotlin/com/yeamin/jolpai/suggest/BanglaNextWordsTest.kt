package com.yeamin.jolpai.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaNextWordsTest {
    // Deliberately unsorted, with a comment line and Windows line ends, like a hand-edited file.
    private val list = BanglaNextWords.of(
        "# test data\r\nকেমন\tআছেন:255 আছো:180 করে:40\r\n<s>\tআমি:255 আমার:120\r\nআমি\tতোমাকে:255 জানি:200 আজ:90\r\n",
        "আমি তোমাকে\tভালোবাসি:255 বলছি:60\n",
    )

    @Test fun `ranked next words for a word, a sentence start and two words`() {
        assertEquals(listOf("আছেন" to 255, "আছো" to 180), list.after("কেমন", 2))
        assertEquals("আমি", list.after(null).first().first)
        assertEquals("ভালোবাসি", list.after("আমি", "তোমাকে").first().first)
        assertTrue(list.after("অজানা").isEmpty())
        assertTrue(list.after("আমি", "অজানা").isEmpty())
        assertEquals(3, list.contexts)
    }

    @Test fun `violent words from the news are never offered`() {
        val l = BanglaNextWords.of("", "আমি তোমাকে\tভালোবাসি:255 হত্যা:90 বলছি:60\n")
        assertEquals(listOf("ভালোবাসি", "বলছি"), l.after("আমি", "তোমাকে").map { it.first })
    }

    @Test fun `typed spellings are matched like the list's`() {
        // The list has precomposed য় (U+09DF); the user typed য + nukta (U+09AF U+09BC).
        val precomposed = "ভয়"
        val typed = "ভয়"
        val l = BanglaNextWords.of(precomposed + "\tপাই:255\n")
        assertEquals("পাই", l.after(typed).single().first)
    }
}
