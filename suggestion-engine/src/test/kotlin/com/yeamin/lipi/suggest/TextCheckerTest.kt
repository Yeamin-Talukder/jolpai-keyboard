package com.yeamin.lipi.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class TextCheckerTest {
    companion object {
        private val en by lazy { EnglishDictionary.load() }
        private val bn by lazy { BanglaDictionary.load() }
    }

    private val history = UserHistory()
    private val checker = TextChecker({ en }, { bn }, history)

/** Applies every fix, last first, and checks again (up to 3 passes), as "Fix all" does. */
    private fun fixAll(text: String): String {
        var current = text
        repeat(3) {
            val issues = checker.check(current)
            if (issues.isEmpty()) return current
            val sb = StringBuilder(current)
            issues.sortedByDescending { it.start }.forEach { sb.replace(it.start, it.end, it.replacement) }
            current = sb.toString()
        }
        return current
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    @Test fun `correct English text has no issues`() {
        val ok = "I think it's a good idea. Can you send me the file at 10:30? Thanks, Rahim."
        assertEquals(emptyList<TextIssue>(), checker.check(ok))
    }

    @Test fun `spelling mistakes get the likely word`() {
        val issues = checker.check("I recieved your mesage yesterday.")
        assertEquals(listOf("received", "message"), issues.filter { it.kind == IssueKind.SPELLING }.map { it.replacement })
    }

    @Test fun `missing apostrophes`() {
        assertEquals("I don't know, I'm tired.", fixAll("i dont know, im tired."))
    }

    @Test fun `punctuation spacing and doubled marks`() {
        assertEquals("Hello, world. Fine, thanks.", fixAll("Hello ,world. Fine,, thanks ."))
        assertEquals("Wait. Really?", fixAll("Wait.Really?"))
    }

    @Test fun `numbers, links, mail and abbreviations are left alone`() {
        val text = "Pay 1,000 taka by 10:30 at www.example.com or mail me at rahim@mail.com, e.g. tomorrow."
        assertEquals(emptyList<TextIssue>(), checker.check(text))
    }

    @Test fun `a and an follow the sound`() {
        assertEquals("It was an apple and a university and an hour.", fixAll("It was a apple and an university and a hour."))
    }

    @Test fun `common grammar slips`() {
        assertEquals("You could have asked. She doesn't care, it's better than nothing.", fixAll("You could of asked. She don't care, its better then nothing."))
        assertEquals("I like it a lot.", fixAll("i like it alot."))
        assertEquals("This is the end.", fixAll("This is the the end."))
    }

    @Test fun `sentence capitals`() {
        assertEquals("Hello there. How are you?", fixAll("hello there. how are you?"))
    }

    @Test fun `missing full stop or question mark at the end`() {
        assertEquals("I will call you tomorrow.", fixAll("I will call you tomorrow"))
        assertEquals("Where are you going now?", fixAll("Where are you going now"))
        assertEquals(emptyList<TextIssue>(), checker.check("I will call you tomorrow", endsAtFieldEnd = false))
        assertEquals(emptyList<TextIssue>(), checker.check("Okay")) // too short to insist
    }

    @Test fun `bangla punctuation`() {
        assertEquals(nfc("আমি ভাত খাই। তুমি কী খাও?"), nfc(fixAll("আমি ভাত খাই ।তুমি কী খাও?")))
        assertEquals(nfc("আমি বাড়ি যাচ্ছি।"), nfc(fixAll("আমি বাড়ি যাচ্ছি।।")))
        assertEquals(nfc("আমি এখন বাড়ি যাচ্ছি।"), nfc(fixAll("আমি এখন বাড়ি যাচ্ছি.")))
        assertEquals(nfc("আমি এখন বাড়ি যাচ্ছি।"), nfc(fixAll("আমি এখন বাড়ি যাচ্ছি")))
    }

    @Test fun `bangla spelling look-alikes`() {
        val issue = checker.check(nfc("পরিক্ষা")).single()
        assertEquals(IssueKind.SPELLING, issue.kind)
        assertEquals(nfc("পরীক্ষা"), nfc(issue.replacement))
        assertEquals(nfc("ভুল"), nfc(checker.check(nfc("ভূল")).single().replacement))
    }

    @Test fun `correct Bangla with endings and spoken spellings is not flagged`() {
        listOf(
            "আমি ঢাকায় থাকি। বইগুলো টেবিলে আছে। ছেলেটা স্কুলে গেছে।",
            "তোমাদের সবাইকে শুভেচ্ছা। আজকে অফিসের কাজ অনেক ছিলো।",
            "মেয়েটি রাস্তায় দাঁড়িয়ে ছিল। আমরা বাসায় ফিরে খাবার খেয়েছি।",
            "ফোনে কথা বলেছিলাম। মোবাইলটা চার্জ দিতে হবে। কালকে দেখা হবে।",
            "বাংলাদেশের মানুষ খুব অতিথিপরায়ণ। শিক্ষার্থীরা পরীক্ষার জন্য পড়ছে।",
            "আমি তোমাকে ভালোবাসি। তুমি কেমন আছো? আমি ভালো আছি।",
        ).forEach { text ->
            assertEquals(text, emptyList<TextIssue>(), checker.check(nfc(text)).filter { it.kind == IssueKind.SPELLING })
        }
        // য় written as য + ় (other keyboards) is the same word.
        assertTrue(checker.check("আমি বাড়ি যাই।").none { it.kind == IssueKind.SPELLING })
    }

    @Test fun `bangla misspellings get the likely word`() {
        mapOf(
            "বাংলাদেশর" to "বাংলাদেশের", // missing vowel sign
            "শিক্ষাথী" to "শিক্ষার্থী", // missing রেফ
            "উজ্জল" to "উজ্জ্বল", // missing ব-ফলা
            "শুভেচছা" to "শুভেচ্ছা", // missing হসন্ত
            "মুহুর্ত" to "মুহূর্ত",
            "কর্মচারি" to "কর্মচারী",
            "দুরত্ব" to "দূরত্ব",
            "ধন্যবাধ" to "ধন্যবাদ", // aspirated pair
            "আমরাা" to "আমরা", // doubled vowel sign
        ).forEach { (typo, fix) ->
            val issue = checker.check(nfc(typo), endsAtFieldEnd = false).single()
            assertEquals(typo, nfc(fix), nfc(issue.replacement))
        }
    }

    @Test fun `word frequencies pick the common word and rank completions`() {
        // No typing history: frequency alone chooses between equally close fixes.
        assertEquals("মানুষ", checker.check("মানষ", endsAtFieldEnd = false).single().replacement)
        assertEquals(nfc("স্কুলে"), nfc(checker.check(nfc("স্কলে"), endsAtFieldEnd = false).single().replacement))
        // A vowel-sign look-alike still beats a more common but different word (ভূল → ভুল, not ভাল).
        assertEquals("ভুল", checker.check("ভূল", endsAtFieldEnd = false).single().replacement)
        assertTrue(bn.frequency("আমি") > bn.frequency("আমিষ"))
        assertEquals("আমার", bn.completions("আমা", 1).single())
    }

    @Test fun `among close bangla fixes, the word the user writes wins`() {
        repeat(3) { history.recordWord(Language.BANGLA, "মানুষ", null) }
        assertEquals("মানুষ", checker.check("মানষ", endsAtFieldEnd = false).single().replacement)
    }

    @Test fun `the user's own words and ignored ones are not flagged`() {
        assertTrue(checker.check("we met jolpai").any { it.original == "jolpai" })
        history.addPersonal(Language.ENGLISH, "jolpai")
        assertTrue(checker.check("we met jolpai").none { it.original == "jolpai" })
        assertTrue(checker.check("we wnet home", ignored = setOf("wnet")).none { it.original == "wnet" })
    }

    @Test fun `names in the middle of a sentence are not spelling mistakes`() {
        assertTrue(checker.check("I met Tanvir and Nusrat today.").isEmpty())
    }

    @Test fun `issues never overlap and keep their place in the text`() {
        val text = "i recieved a apple ,thanks"
        val issues = checker.check(text)
        issues.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start) }
        issues.forEach { assertEquals(it.original, text.substring(it.start, it.end)) }
    }
}
