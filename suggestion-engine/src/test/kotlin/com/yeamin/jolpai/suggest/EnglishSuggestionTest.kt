package com.yeamin.jolpai.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortedWordsTest {
    private val words = SortedWords.of(listOf("cat", "car", "cart", "dog", "ca"))

    @Test fun `sorted and searchable`() {
        assertEquals(listOf("ca", "car", "cart", "cat", "dog"), (0 until words.size).map { words.wordAt(it) })
        val r = words.prefixRange("car")
        assertEquals(1, SortedWords.rangeStart(r))
        assertEquals(3, SortedWords.rangeEnd(r))
        val none = words.prefixRange("x")
        assertEquals(SortedWords.rangeStart(none), SortedWords.rangeEnd(none))
    }
}

class TypingDistanceTest {
    private val d = TypingDistance()

    @Test fun `swap is cheap`() = assertEquals(0.7f, d.compute("hte", "the", 3f), 0.001f)
    @Test fun `missing apostrophe is very cheap`() = assertEquals(0.3f, d.compute("dont", "don't", 3f), 0.001f)
    @Test fun `neighbouring key is cheaper than a far key`() {
        assertEquals(0.6f, d.compute("tge", "the", 3f), 0.001f) // g is next to h
        assertEquals(1f, d.compute("tpe", "the", 3f), 0.001f)
    }
    @Test fun `gives up above the limit`() = assertTrue(d.compute("abcdef", "uvwxyz", 1f) > 1f)
    @Test fun `spelling by sound is cheaper than a random edit`() {
        assertEquals(0.4f, d.compute("ocurred", "occurred", 3f), 0.001f) // doubled letter typed once
        assertEquals(0.7f, d.compute("seperate", "separate", 3f), 0.001f) // vowel for vowel
        assertEquals(0.8f, d.compute("arguement", "argument", 3f), 0.001f) // extra vowel
        assertEquals(0.7f, d.compute("skool", "scool", 3f), 0.001f) // k for c
        assertEquals(1f, d.compute("bat", "bxt", 3f), 0.001f)
    }
}

class EnglishSuggestionTest {
    companion object {
        private val dict by lazy { EnglishDictionary.load() }
    }

    private val history = UserHistory()
    private val suggester = Suggester({ dict }, { null }, { null }, history)

    @Test fun `dictionary loads`() {
        assertTrue(dict.size > 150_000)
        assertTrue(dict.contains("the"))
        assertTrue(dict.contains("The"))
    }

    @Test fun `completions are frequent words`() {
        val words = dict.completions("th", 3).map { it.word }
        assertTrue(words.toString(), "the" in words)
    }

    @Test fun `typo gets autocorrected`() {
        assertEquals("the", suggester.english("teh").autoCorrect)
        assertEquals("the", suggester.english("hte").autoCorrect)
        assertEquals("because", suggester.english("becuase").autoCorrect)
    }

    @Test fun `common misspellings are fixed`() {
        assertEquals("accused", suggester.english("acused").autoCorrect)
        assertEquals("separate", suggester.english("seperate").autoCorrect)
        assertEquals("argument", suggester.english("arguement").autoCorrect)
    }

    @Test fun `a lower-case typo prefers a word over a name`() {
        assertTrue(suggester.english("blaim").candidates.first() != "Blair")
        assertEquals("Blair", suggester.english("Blaim").candidates.first())
    }

    @Test fun `two equally likely fixes are offered, not forced`() {
        fun tiny(vararg words: String) = EnglishDictionary.parse(words.toList(), emptyList()).let { d ->
            Suggester({ d }, { null }, { null }, UserHistory())
        }
        val even = tiny("cat\t200", "cot\t200").english("cet") // a vowel off from both
        assertTrue(even.toString(), "cat" in even.candidates && "cot" in even.candidates)
        assertNull(even.autoCorrect)
        assertEquals("cat", tiny("cat\t200", "cot\t100").english("cet").autoCorrect)
    }

    @Test fun `capital letter is kept`() = assertEquals("The", suggester.english("Teh").autoCorrect)

    @Test fun `lower case i becomes I`() = assertEquals("I", suggester.english("i").autoCorrect)

    @Test fun `aosp shortcut`() = assertEquals("a lot", suggester.english("alot").autoCorrect)

    @Test fun `known words are left alone`() {
        val s = suggester.english("hello")
        assertNull(s.autoCorrect)
        assertTrue(s.typedIsKnown)
    }

    @Test fun `autocorrect can be turned off`() =
        assertNull(suggester.english("teh", SuggestOptions(autoCorrect = false)).autoCorrect)

    @Test fun `personal dictionary words are not corrected`() {
        history.addPersonal(Language.ENGLISH, "Lipi")
        assertNull(suggester.english("lipi").autoCorrect)
    }

    @Test fun `word typed twice is learned and not corrected`() {
        history.recordWord(Language.ENGLISH, "yeamin", null)
        history.recordWord(Language.ENGLISH, "yeamin", null)
        assertNull(suggester.english("yeamin").autoCorrect)
        val s = suggester.english("yea")
        assertTrue(s.toString(), "yeamin" in s.candidates)
    }

    @Test fun `offensive words are not suggested`() {
        assertTrue(dict.isOffensive("rape"))
        assertFalse(dict.completions("rap", 50).any { it.word == "rape" })
    }

    @Test fun `next word comes from what the user typed`() {
        history.recordWord(Language.ENGLISH, "morning", "good")
        history.recordWord(Language.ENGLISH, "morning", "good")
        history.recordWord(Language.ENGLISH, "night", "good")
        assertEquals(listOf("morning", "night"), suggester.nextWords(Language.ENGLISH, "Good"))
    }

    @Test fun `suggestions never repeat the typed word`() {
        val s = suggester.english("thr")
        assertFalse("thr" in s.candidates)
        assertTrue(s.candidates.isNotEmpty())
    }

    @Test fun `correction search is fast enough for typing`() {
        val words = listOf("becuase", "recieve", "teh", "thier", "wierd", "acommodate", "definately", "seperate")
        repeat(30) { words.forEach { suggester.english(it) } } // let the JIT warm up, as on a phone after a while
        val start = System.nanoTime()
        repeat(10) { words.forEach { suggester.english(it) } }
        val perCallMs = (System.nanoTime() - start) / (10.0 * words.size) / 1_000_000.0
        println("English suggestion with correction search: %.2f ms per call".format(perCallMs))
        assertTrue("took $perCallMs ms per call", perCallMs < 5)
    }
}
