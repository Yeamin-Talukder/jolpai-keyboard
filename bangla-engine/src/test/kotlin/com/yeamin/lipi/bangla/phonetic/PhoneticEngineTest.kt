package com.yeamin.lipi.bangla.phonetic

import com.yeamin.lipi.bangla.phonetic.PhoneticMatch.Scope
import com.yeamin.lipi.bangla.phonetic.PhoneticMatch.Side
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the rule engine with a small hand-written grammar. The real Avro rule table is
 * tested separately once it is added (with the brief's corpus: ami, amar, tumi, bangla, bhalobashi).
 */
class PhoneticEngineTest {

    private val notAfterConsonant = PhoneticMatch(Side.PREFIX, Scope.CONSONANT, negative = true)

    private val grammar = PhoneticGrammar(
        patterns = listOf(
            PhoneticPattern("b", "ব"),
            PhoneticPattern("bh", "ভ"),
            PhoneticPattern("m", "ম"),
            PhoneticPattern("l", "ল"),
            PhoneticPattern("t", "ত"),
            PhoneticPattern("T", "ট"),
            PhoneticPattern("a", "া", listOf(PhoneticRule(listOf(notAfterConsonant), "আ"))),
            PhoneticPattern("i", "ি", listOf(PhoneticRule(listOf(notAfterConsonant), "ই"))),
            PhoneticPattern("o", "", listOf(PhoneticRule(listOf(notAfterConsonant), "অ"))),
            PhoneticPattern(
                "x", "ক্স",
                listOf(PhoneticRule(listOf(PhoneticMatch(Side.SUFFIX, Scope.EXACT, value = "y")), "X")),
            ),
            PhoneticPattern(
                "n", "ন",
                listOf(PhoneticRule(listOf(PhoneticMatch(Side.SUFFIX, Scope.PUNCTUATION)), "ন্")),
            ),
        ),
        vowels = "aeiou",
        consonants = "bcdfghjklmnpqrstvwxyz",
        caseSensitive = "oiudgjnrstyz",
    )

    private val engine = PhoneticEngine(grammar)

    @Test fun `vowel at word start is independent, after consonant is kar`() =
        assertEquals("আমি", engine.convert("ami"))

    @Test fun `longest pattern wins`() = assertEquals("ভাল", engine.convert("bhalo"))

    @Test fun `case sensitive letters keep their case`() = assertEquals("টা", engine.convert("Ta"))

    @Test fun `other letters are lower-cased`() = assertEquals("আমি", engine.convert("AMi"))

    @Test fun `upper case of a case sensitive letter is a different key`() =
        assertEquals("আমI", engine.convert("amI"))

    @Test fun `inherent o after consonant is silent`() = assertEquals("অ", engine.convert("o"))

    @Test fun `exact suffix rule`() {
        assertEquals("Xyব", engine.convert("xyb"))
        assertEquals("ক্স", engine.convert("x"))
    }

    @Test fun `exact suffix ending at the end of input does not match, as in the reference parser`() =
        assertEquals("ক্সy", engine.convert("xy"))

    @Test fun `end of input counts as punctuation`() {
        assertEquals("ন্", engine.convert("n"))
        assertEquals("না", engine.convert("na"))
    }

    @Test fun `unknown characters pass through`() = assertEquals("আ1?", engine.convert("a1?"))

    @Test fun `empty input`() = assertEquals("", engine.convert(""))
}
