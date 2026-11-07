package com.yeamin.jolpai.suggest

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values come from the reference implementations' own tests:
 * upodesh (MIT) for the dictionary search, riti (MPL-2.0) for candidate ranking.
 * riti's emoji candidates are left out (emoji are Phase 5), and riti's default of not
 * offering the English input is used where noted.
 */
class BanglaSuggestionTest {
    companion object {
        private val dict by lazy { BanglaDictionary.load() }
        private val search by lazy { AvroDictionarySearch.load(dict) }
        private val avro by lazy { AvroSuggester.load(PhoneticEngine(AvroGrammar.load()), search) }
    }

    private fun found(input: String) = search.search(input).sorted()

    @Test fun `upodesh search results`() {
        assertEquals(listOf("শারি", "শারী", "শাড়ি", "শাড়ী", "সারি", "সারী", "সাড়ি", "সাড়ী", "স্মঅরী"), found("sari"))
        assertEquals(listOf("ষাঁড়", "সার", "সার্ব", "সাড়"), found("sar"))
        assertEquals(listOf("অমরা", "আমরা", "আমড়া"), found("amra"))
        assertEquals(listOf("লালশাক"), found("lalshak"))
        assertEquals(listOf("লালরং", "লালরঙ"), found("lalrong"))
        assertEquals(listOf("অংশচ্ছেদ"), found("ongshochched"))
        assertEquals(listOf("স্বাধীনতা"), found("shadhinota"))
        assertEquals(listOf("দুঃখ", "দুখ"), found("dukkho"))
        assertEquals(listOf("চুল", "চূল", "চোল", "ছুঁল", "ছুল", "ছোল"), found("cool"))
        assertEquals(listOf("শকতি", "শক্তি", "সক্তি"), found("shokti"))
        assertEquals(listOf("চুপ", "ছুপ"), found("chup"))
        assertEquals(listOf("অশ্ব", "অশ্ম", "আঁশ", "আশ", "আস", "এস"), found("as"))
        assertEquals(listOf("আপনি"), found("apni"))
        assertEquals(listOf("ক্ষেত", "খেঁট", "খেট", "খেত", "খ্যাঁট", "খ্যাঁত", "খ্যাত"), found("kkhet"))
        assertEquals(listOf("এবং"), found("ebong"))
        assertEquals(listOf("শেষ", "সেস"), found("shesh"))
    }

    @Test fun `upodesh empty results`() {
        assertEquals(emptyList<String>(), found("6t``"))
        assertEquals(emptyList<String>(), found("t6th"))
    }

    private fun candidates(term: String, selections: Map<String, String> = emptyMap()) =
        avro.suggest(term, { selections[it] }, includeEnglish = false)

    @Test fun `riti ranking`() {
        assertEquals(listOf("আ", "অ", "আঃ", "এ", "া", "অ্যা", "অ্যাঁ"), candidates("a").candidates)
        assertEquals(listOf("আস", "আশ", "এস", "আঁশ", "অশ্ব", "অশ্ম"), candidates("as").candidates)
        assertEquals(listOf("(আস)", "(আশ)", "(এস)", "(আঁশ)", "(অশ্ব)", "(অশ্ম)"), candidates("(as)").candidates)
        assertEquals(listOf("আপনি", "আপনই", "আপ্নি"), candidates("apni").candidates)
        assertEquals(listOf("আমি", "আমই", "অমি", "এমই"), candidates("ami").candidates)
        assertEquals(listOf("ক্ষেত", "খেত", "খ্যাত", "খেট", "খ্যাঁত", "খেঁট", "খ্যাঁট"), candidates("kkhet").candidates)
        assertEquals(listOf("ফর্ম", "ফরম"), candidates("form").candidates)
        assertEquals(listOf("এটিএম", "আত্ম", "অ্যাটম"), candidates("atm").candidates)
    }

    @Test fun `riti suffixes`() {
        assertEquals(
            listOf("আসগুলো", "আশগুলো", "এসগুলো", "আঁশগুলো", "অশ্বগুলো", "অশ্মগুলো", "আসগুল"),
            candidates("asgulo").candidates,
        )
        assertEquals(listOf("এটিএমে", "আত্মে", "অ্যাটমে"), candidates("atme").candidates)
        assertEquals("কম্পিউটারগুলো", AvroSuggester.joinSuffix("কম্পিউটার", "গুলো"))
        assertEquals("ইয়েই", AvroSuggester.joinSuffix("ই", "েই"))
        assertEquals("হঠাতে", AvroSuggester.joinSuffix("হঠাৎ", "ে"))
        assertEquals("এবঙমালা", AvroSuggester.joinSuffix("এবং", "মালা"))
    }

    @Test fun `previous selection is chosen first`() {
        val c = candidates("sesh", mapOf("sesh" to "শেষ"))
        assertEquals(listOf("সেস", "শেষ", "সেশ"), c.candidates)
        assertEquals("শেষ", c.selectedText)
        val dotted = candidates("sesh.", mapOf("sesh" to "শেষ"))
        assertEquals(listOf("সেস।", "শেষ।", "সেশ।"), dotted.candidates)
        assertEquals("শেষ।", dotted.selectedText)
    }

    @Test fun `brief example picks the dictionary word`() {
        // Both spellings are in the dictionary. The older ভালবাসি is closer to the literal
        // conversion ভালবাশি, so it ranks first (as in riti); ভালোবাসি is offered right after,
        // and once the user picks it, it becomes the default for "bhalobashi".
        val first = candidates("bhalobashi")
        assertEquals("ভালবাসি", first.selectedText)
        assertTrue(first.candidates.toString(), "ভালোবাসি" in first.candidates.take(3))
        assertEquals("ভালোবাসি", candidates("bhalobashi", mapOf("bhalobashi" to "ভালোবাসি")).selectedText)
        assertEquals("ভালোবাসি", candidates("bhalObasi").selectedText)

        assertEquals("আমি", candidates("ami").selectedText)
        assertEquals("বাংলা", candidates("bangla").selectedText)
        assertEquals("তুমি", candidates("tumi").selectedText)
        assertEquals("আমার", candidates("amar").selectedText)
    }

    @Test fun `english input is offered last`() {
        val c = avro.suggest("google", { null }, includeEnglish = true)
        assertEquals("google", c.candidates.last())
    }

    @Test fun `national completions`() {
        val s = Suggester({ null }, { dict }, { null }, UserHistory()).bangla("বাংলাদে")
        assertTrue(s.candidates.toString(), s.candidates.any { it.startsWith("বাংলাদেশ") })
    }

    @Test fun `avro suggestions are fast enough for typing`() {
        candidates("bistari") // warm up
        val start = System.nanoTime()
        listOf("a", "am", "ami", "b", "bh", "bha", "bhal", "bhalo", "bhalob", "bhalobashi").forEach { candidates(it) }
        val ms = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("took $ms ms for 10 key presses", ms < 200)
    }
}
