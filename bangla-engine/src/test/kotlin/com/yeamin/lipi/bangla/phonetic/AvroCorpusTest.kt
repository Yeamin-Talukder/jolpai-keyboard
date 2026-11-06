package com.yeamin.lipi.bangla.phonetic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real Avro grammar against:
 * 1. the 413 input/expected pairs from the reference parser's tests (rupantor, MPL-2.0;
 *    see test resources avro/NOTICE.txt), and
 * 2. the examples from the project brief.
 */
class AvroCorpusTest {

    private val engine = PhoneticEngine(AvroGrammar.load())

    @Test fun `matches the reference parser on its whole test corpus`() {
        val lines = javaClass.getResourceAsStream("/avro/rupantor-corpus.tsv")!!
            .bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotEmpty() }
        assertEquals(413, lines.size)
        val failures = lines.mapNotNull { line ->
            val (input, expected) = line.split('\t', limit = 2)
            val actual = engine.convert(input)
            if (actual == expected) null else "'$input' → '$actual' (expected '$expected')"
        }
        assertTrue("${failures.size} mismatches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `examples from the project brief`() {
        assertEquals("আমি", engine.convert("ami"))
        assertEquals("বাংলা", engine.convert("bangla"))
        assertEquals("আমার", engine.convert("amar"))
        assertEquals("তুমি", engine.convert("tumi"))
        // Rules alone are literal: "sh" is শ and a lower-case "o" after a consonant is silent.
        // The brief's "bhalobashi → ভালোবাসি" comes from Avro's dictionary candidates (Phase 3).
        assertEquals("ভালবাশি", engine.convert("bhalobashi"))
        assertEquals("ভালোবাসি", engine.convert("bhalObasi"))
    }

    @Test fun `conjuncts, fola and reph`() {
        assertEquals("ক্ষ", engine.convert("kkh"))
        assertEquals("প্র", engine.convert("pr"))
        assertEquals("ব্য", engine.convert("by"))
        // Reph is typed "rr" before a consonant; a single "r" is a plain র.
        assertEquals("কর্ম", engine.convert("korrm"))
        assertEquals("করম", engine.convert("korm"))
    }

    @Test fun `digits and punctuation`() {
        assertEquals("২০২৬", engine.convert("2026"))
        assertEquals("।", engine.convert("."))
        assertEquals("৳", engine.convert("$"))
    }
}
