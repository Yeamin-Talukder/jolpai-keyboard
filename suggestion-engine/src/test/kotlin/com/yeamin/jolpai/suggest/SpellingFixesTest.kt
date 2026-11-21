package com.yeamin.jolpai.suggest

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the spelling bugs found on 2026-11-21 (user: "the spell checker is not
 * working properly"): Bangla typed in English letters corrected to English, জাতীয় text typed
 * key by key (ো as ে + া, ি before its consonant) misread, no spelling fixes in the জাতীয় strip,
 * and slow checks of long texts with many unknown words.
 *
 * Bangla literals with ো / ৌ / ি are written with escapes where the exact characters matter.
 */
class SpellingFixesTest {
    companion object {
        private val en by lazy { EnglishDictionary.load() }
        private val bn by lazy { BanglaDictionary.load() }
        private val avro by lazy { AvroSuggester.load(PhoneticEngine(AvroGrammar.load()), AvroDictionarySearch.load(bn), bn::frequency) }

        private const val E = "ে" // ে
        private const val AA = "া" // া
        private const val I = "ি" // ি
        private const val O = "ো" // ো
    }

    private val history = UserHistory()
    private val roman = RomanBangla({ en }, { bn }, { avro })
    private val checker = TextChecker({ en }, { bn }, history, roman = roman)
    private val suggester = Suggester({ en }, { bn }, { avro }, history)

    private fun spelling(text: String) = checker.check(text).filter { it.kind == IssueKind.SPELLING }

    // ------------------------------------------------------------------ Roman Bangla

    @Test fun `bangla typed in english letters is left alone by the checker`() {
        for (text in listOf(
            "ami tomake onek bhalobashi",
            "kemon acho? ami valo achi",
            "kal ki tumi bari jabe naki",
            "bhai ekta kotha bolbo, kichu mone korish na",
            "ami ajke onek busy chilam tai phone dhorte parini, kal sokale kotha bolbo inshallah.",
        )) {
            assertEquals(text, emptyList<TextIssue>(), checker.check(text))
        }
        // Mixed: the English half is still English, the Bangla half is left alone.
        assertEquals(emptyList<TextIssue>(), checker.check("I am going to the market, tumi ki kichu lagbe?"))
        // A lone Roman Bangla word in English ("bhai") isn't a typo either.
        assertTrue(spelling("ok bhai see you tomorrow.").isEmpty())
    }

    @Test fun `english typos are still caught next to the roman bangla rule`() {
        assertEquals(listOf("send", "address"), spelling("Please sned me the adress.").map { it.replacement })
        assertEquals(listOf("don't", "she's"), spelling("He dont know, shes busy.").map { it.replacement })
        assertTrue("talk" in spelling("i want to takl to you").single().choices)
    }

    @Test fun `the detector tells roman bangla from english typos`() {
        for (w in listOf("tomake", "onek", "kemon", "bhalobashi", "kichu", "lagbe", "thik", "kintu", "ami")) assertTrue(w, roman.isRomanBangla(w))
        for (w in listOf("recieved", "adress", "takl", "dont", "shes", "the", "office", "busy", "to")) assertFalse(w, roman.isRomanBangla(w))
    }

    @Test fun `autocorrect never turns roman bangla into english words`() {
        val on = SuggestOptions(autoCorrect = true)
        for (w in listOf("tomake", "kemon", "kintu", "thik", "hobe", "amake", "tumi")) {
            assertNull(w, suggester.english(w, on).autoCorrect)
        }
        // A short one after another Roman Bangla word: "tumi kal" is not "tumi mall".
        assertNull(suggester.english("kal", on.copy(context = listOf("tumi"))).autoCorrect)
        // English slips are still corrected.
        assertEquals("received", suggester.english("recieved", on).autoCorrect)
        assertEquals("don't", suggester.english("dont", on).autoCorrect)
    }

    // ------------------------------------------------------------------ জাতীয় typed key by key

    @Test fun `o-kar typed as e-kar plus aa-kar is a correct word`() {
        // ভালো and তোমার with ো as two characters, as জাতীয় (like Ridmik) types them.
        assertEquals(emptyList<TextIssue>(), spelling("আমি ভাল$E$AA আছি"))
        assertEquals(emptyList<TextIssue>(), spelling("ত$E${AA}মার বই"))
        assertEquals("ভাল$O", BanglaSpelling.normalize("ভাল$E$AA"))
    }

    @Test fun `vowel signs typed before their consonant are put in order`() {
        assertEquals("ক${I}ছু", spelling("${I}কছু হবে না").single().replacement)
        assertEquals("ক${O}থায়".let(BanglaSpelling::normalize), spelling("${E}কাথায় যাবে").single().replacement.let(BanglaSpelling::normalize))
        assertEquals("শ${I}ক্ষা", spelling("${I}শক্ষা").single().replacement)
        assertEquals("ত${O}মাকে", spelling("আমি ${E}তামাকে বলব").single().replacement)
        // হসন্ত + vowel sign → the vowel; the same sign twice → once.
        assertEquals("আমি", spelling("্আমি যাব").single().replacement)
        assertEquals("আম$I", spelling("আম$I$I যাব").single().replacement)
    }

    @Test fun `the national strip offers spelling fixes for a misspelt word`() {
        val fixes = suggester.bangla("পর${I}ক্ষা").candidates
        assertTrue(fixes.toString(), "পরীক্ষা" in fixes)
        assertTrue("মুহূর্ত" in suggester.bangla("মুহুর্ত").candidates)
        // A word still being typed gets completions, not "fixes".
        assertTrue(suggester.bangla("বাংলাদে").candidates.any { it.startsWith("বাংলাদেশ") })
        // ো typed as ে + া still finds the word: তোমা… completes to তোমার.
        assertTrue(suggester.bangla("ত$E${AA}মা").candidates.any { it.startsWith("ত${O}মা") })
        assertTrue(suggester.bangla("ভাল$E$AA").typedIsKnown)
    }

    @Test fun `learned bangla words are stored in the list's spelling`() {
        history.recordWord(Language.BANGLA, "ভাল$E$AA", "আমি")
        assertEquals(1, history.count(Language.BANGLA, "ভাল$O"))
        assertEquals(1, history.bigramCount(Language.BANGLA, "আমি", "ভাল$O"))
    }

    // ------------------------------------------------------------------ word choice (model)

    @Test fun `common sound-alike mix-ups are caught, correct sentences are left alone`() {
        val withModel = TextChecker({ en }, { bn }, history, lm = LanguageModel { NextWordModelTest.model }, roman = roman)
        fun choice(text: String) = withModel.check(text, useModel = true).filter { it.reason.contains("fits this sentence") }.map { it.original to it.replacement }
        assertEquals(listOf("to" to "too"), choice("She was to tired to finish her homework."))
        assertEquals(listOf("by" to "buy"), choice("I need to by some milk."))
        assertEquals(listOf("here" to "hear"), choice("Can you here me now?"))
        assertEquals(listOf("no" to "know"), choice("I no what you mean."))
        assertEquals(listOf("Were" to "Where"), choice("Were are you going?"))
        for (ok in listOf(
            "It is too late to call him now.", "We drove to the city by car.", "Did you buy a new phone?",
            "Come here and sit with me.", "No, I don't want to go.", "Where were you last night?", "I went there too.",
        )) assertEquals(ok, emptyList<Pair<String, String>>(), choice(ok))
        // Roman Bangla "to" (তো) is not English "to".
        assertEquals(emptyList<Pair<String, String>>(), choice("ami to jani na, tumi bolo"))
    }

    // ------------------------------------------------------------------ speed

    @Test fun `a long text full of unknown words looks up only the ones nearest the cursor`() {
        val noise = (1..60).joinToString(" ") { "qz${"x".repeat(it % 5)}vk${('a' + it % 26)}" }
        val text = "$noise I recieved it"
        val issues = checker.check(text, searchLimit = 3, focus = text.length)
        assertTrue(issues.toString(), issues.any { it.replacement == "received" })
        assertTrue(issues.count { it.kind == IssueKind.SPELLING } <= 3)
    }
}
