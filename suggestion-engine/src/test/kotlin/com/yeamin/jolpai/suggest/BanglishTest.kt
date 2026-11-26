package com.yeamin.jolpai.suggest

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class BanglishFoldTest {
    private fun same(vararg words: String) {
        val keys = words.map(BanglishFold::fold).toSet()
        assertEquals(words.joinToString() + " → " + keys, 1, keys.size)
    }

    @Test fun `spellings of one word meet`() {
        same("valo", "bhalo", "bhaalo", "vhalo")
        same("achi", "asi", "achhi", "assi")
        same("kichu", "kisu", "kichhu")
        same("jabo", "zabo")
        same("keo", "kew")
        same("amay", "amai")
        same("korte", "korrte")
        same("valobashi", "bhalobasi", "valobasi")
    }

    @Test fun `different words stay apart`() {
        assertNotEquals(BanglishFold.fold("kal"), BanglishFold.fold("kol"))
        assertNotEquals(BanglishFold.fold("ami"), BanglishFold.fold("tumi"))
        // h is kept at the start and after a vowel.
        assertEquals("hobe", BanglishFold.fold("hobe"))
        assertEquals("kaha", BanglishFold.fold("kaha"))
    }

    @Test fun `a word's start folds to the start of its fold`() {
        for (w in listOf("bhalobashi", "achhi", "kichhu", "shotti", "khub", "thakbo", "dhonnobad")) {
            val full = BanglishFold.fold(w)
            for (n in 1..w.length) assertTrue("$w[0,$n)", full.startsWith(BanglishFold.fold(w.substring(0, n))))
        }
    }
}

class BanglishWordsTest {
    private val list = BanglishWords.parse(
        listOf(
            "# test",
            "valo\t196\tভালো\t",
            "bhalo\t170\tভালো\t",
            "valobashi\t160\tভালোবাসি\t",
            "achi\t180\tআছি\t",
            "asi\t175\tআছি\t",
            "kmn\t165\tকেমন\t",
            "magi\t120\t\to",
        ),
    )

    @Test fun `completions match by sound and give one entry per word`() {
        val c = list.completions("bh", 5).map { it.spelling }
        assertEquals(listOf("valo", "valobashi"), c)
        assertEquals(listOf("achi"), list.completions("ass", 5).map { it.spelling })
    }

    @Test fun `offensive words only when allowed`() {
        assertTrue(list.completions("mag", 5).isEmpty())
        assertEquals(listOf("magi"), list.completions("mag", 5, allowOffensive = true).map { it.spelling })
    }

    @Test fun `the user's own spelling is shown`() {
        val valo = list.completions("v", 1).single()
        assertEquals("valo", list.bestSpelling(valo) { 0 })
        assertEquals("bhalo", list.bestSpelling(valo) { if (it == "bhalo") 3 else 0 })
    }

    @Test fun `lookup ignores case`() {
        assertEquals("কেমন", list.lookup("KMN")?.bangla)
        assertFalse(list.contains("kemon"))
    }
}

/** The bundled list with the real dictionaries: Banglish typed on the English layout. */
class BanglishSuggestionTest {
    companion object {
        private val en by lazy { EnglishDictionary.load() }
        private val bn by lazy { BanglaDictionary.load() }
        private val avro by lazy { AvroSuggester.load(PhoneticEngine(AvroGrammar.load()), AvroDictionarySearch.load(bn), bn::frequency) }
        private val words by lazy { BanglishWords.load() }
    }

    private val history = UserHistory()
    private val suggester = Suggester({ en }, { bn }, { avro }, history, banglish = { words })
    private val on = SuggestOptions(autoCorrect = true)

    @Test fun `the bundled list loads with its common words`() {
        assertTrue(words.size > 10_000)
        for (w in listOf("ami", "valo", "kemon", "kichu", "korbo", "tmr")) assertTrue(w, words.contains(w))
        assertEquals("আমি", words.lookup("ami")?.bangla)
    }

    @Test fun `banglish words are completed in a banglish sentence`() {
        val ctx = on.copy(context = listOf("ami", "tomake"))
        assertTrue(suggester.english("valob", ctx).candidates.toString(), suggester.english("valob", ctx).candidates.any { it.startsWith("valobas") })
        assertTrue(suggester.english("bhalob", ctx).candidates.toString(), suggester.english("bhalob", ctx).candidates.any { BanglishFold.fold(it).startsWith("balobas") })
    }

    @Test fun `shorthand and spelling variants are not autocorrected`() {
        for (w in listOf("kmn", "tmr", "valo", "kisu", "khaiso")) {
            assertNull(w, suggester.english(w, on.copy(context = listOf("ami"))).autoCorrect)
        }
    }

    @Test fun `the spelling the user writes is offered`() {
        repeat(3) { history.recordWord(Language.ENGLISH, "bhalobashi", null) }
        val c = suggester.english("valob", on.copy(context = listOf("ami", "tomake"))).candidates
        assertTrue(c.toString(), "bhalobashi" in c)
        assertFalse(c.toString(), "valobashi" in c)
    }

    @Test fun `english typing is not taken over`() {
        val ctx = on.copy(context = listOf("I", "would", "like", "to"))
        assertEquals("received", suggester.english("recieved", ctx).autoCorrect)
        assertEquals("the", suggester.english("th", on.copy(context = listOf("in"))).candidates.first())
        val off = Suggester({ en }, { bn }, { avro }, history, banglish = { words })
        assertTrue(off.english("val", on.copy(context = listOf("the"), banglish = false)).candidates.none { it == "valo" })
    }

    @Test fun `banglish suggestions are fast enough for typing`() {
        repeat(20) { history.recordWord(Language.ENGLISH, "word$it", null) }
        val typed = listOf("v", "va", "val", "valob", "k", "kmn", "ach", "kis", "tom", "bhalob")
        val ctx = on.copy(context = listOf("ami", "tomake"))
        repeat(30) { typed.forEach { suggester.english(it, ctx) } }
        val start = System.nanoTime()
        repeat(10) { typed.forEach { suggester.english(it, ctx) } }
        val perCallMs = (System.nanoTime() - start) / (10.0 * typed.size) / 1_000_000.0
        println("Banglish suggestion: %.2f ms per call".format(perCallMs))
        assertTrue("took $perCallMs ms per call", perCallMs < 5)
    }

    @Test fun `the banglish model reads banglish as written`() {
        val model = NextWordModel.loadBanglish()
        assertTrue("bundled lm/banglish.nwp.bin", model != null)
        val s = Suggester({ en }, { bn }, { avro }, UserHistory(), banglish = { words }, banglishModel = { model })
        val o = SuggestOptions(useModel = true)
        val afterAmiTomake = s.nextWords(Language.ENGLISH, "tomake", 3, o.copy(context = listOf("ami", "tomake")))
        assertTrue(afterAmiTomake.toString(), afterAmiTomake.any { BanglishFold.fold(it).startsWith("balobas") })
        // Spelled another way, the same words: bhai = vai.
        val a = s.nextWords(Language.ENGLISH, "apnar", 3, o.copy(context = listOf("vai", "apnar")))
        val b = s.nextWords(Language.ENGLISH, "apnar", 3, o.copy(context = listOf("bhai", "apnar")))
        assertEquals(a, b)
    }

    @Test fun `the bangla model reads a banglish sentence`() {
        val model = NextWordModel.loadBangla()
        assumeTrue(model != null)
        val s = Suggester({ en }, { bn }, { avro }, UserHistory(), banglish = { words }, banglaModel = { model })
        val o = SuggestOptions(useModel = true)
        val afterAmiTomake = s.nextWords(Language.ENGLISH, "tomake", 3, o.copy(context = listOf("ami", "tomake")))
        assertTrue(afterAmiTomake.toString(), afterAmiTomake.any { BanglishFold.fold(it).startsWith("balobas") })
        val afterKemon = s.nextWords(Language.ENGLISH, "kemon", 3, o.copy(context = listOf("kemon")))
        assertTrue(afterKemon.toString(), afterKemon.any { BanglishFold.fold(it) in setOf("aso", "asen", "asis") })
        // English after English is unchanged: the English model (absent here) or what was typed.
        assertTrue(s.nextWords(Language.ENGLISH, "the", 3, o.copy(context = listOf("the"))).none { it == "ami" })
    }

    /**
     * Next words and completions in Banglish on held-out sentences, three ways: no model, the
     * Bangla model reading the sentence turned into Bangla, and the Banglish model (BANGLISH_MODEL,
     * a .nwp.bin, or the bundled one). The right word in the top 3, by exact spelling and by sound
     * (valo = bhalo). Sets: BanglaTLit's test sentences (BANGLISH_TEST, the csv) and held-out
     * YouTube comments (BANGLISH_YT, one sentence per line, from banglish_prepare.py).
     */
    @Test fun `held out banglish sentences with the models`() {
        val bangla = NextWordModel.loadBangla()
        val banglishModel = System.getenv("BANGLISH_MODEL")?.let { NextWordModel.read(File(it).inputStream()) } ?: NextWordModel.loadBanglish()
        val sets = buildList {
            System.getenv("BANGLISH_TEST")?.let(::File)?.takeIf { it.exists() }?.let { f ->
                add("BanglaTLit test" to f.readLines().drop(1).mapNotNull { line ->
                    Regex("^[^,]*,(\"(?:[^\"]|\"\")*\"|[^,]*),").find(line)?.groupValues?.get(1)?.trim('"')
                })
            }
            System.getenv("BANGLISH_YT")?.let(::File)?.takeIf { it.exists() }?.let { add("YouTube comments" to it.readLines()) }
        }
        assumeTrue("set BANGLISH_TEST and/or BANGLISH_YT", sets.isNotEmpty() && bangla != null)
        val viaBangla = Suggester({ en }, { bn }, { avro }, UserHistory(), banglish = { words }, banglaModel = { bangla })
        val direct = Suggester({ en }, { bn }, { avro }, UserHistory(), banglish = { words }, banglaModel = { bangla }, banglishModel = { banglishModel })
        val ways = buildList {
            add(Triple("no model", viaBangla, false))
            add(Triple("Bangla model", viaBangla, true))
            if (banglishModel != null) add(Triple("Banglish model", direct, true))
        }
        for ((setName, sentences) in sets) for ((name, s, useModel) in ways) {
            val o = SuggestOptions(autoCorrect = false, useModel = useModel)
            val next = IntArray(3) // words, exact, by sound
            val after = Array(2) { IntArray(3) }
            val t0 = System.nanoTime()
            for (sentence in sentences) {
                val ws = sentence.split(Regex("\\s+")).map { it.trim { c -> !c.isLetter() }.lowercase() }.filter { it.isNotEmpty() }
                for (i in 1 until ws.size) {
                    val w = ws[i]
                    // Banglish targets after a Banglish word (where these suggestions apply).
                    if (!w.all { it in 'a'..'z' } || !s.isBanglish(ws[i - 1]) || !words.contains(w)) continue
                    val ctx = ws.subList(maxOf(0, i - 8), i)
                    val got = s.nextWords(Language.ENGLISH, ws[i - 1], 3, o.copy(context = ctx))
                    next[0]++
                    if (w in got) next[1]++
                    if (got.any { BanglishFold.fold(it) == BanglishFold.fold(w) }) next[2]++
                    for (k in 1..2) {
                        if (w.length <= k) continue
                        val c = s.english(w.take(k), o.copy(context = ctx, limit = 3)).candidates
                        after[k - 1][0]++
                        if (w in c) after[k - 1][1]++
                        if (c.any { BanglishFold.fold(it) == BanglishFold.fold(w) }) after[k - 1][2]++
                    }
                }
            }
            fun pct(a: IntArray) = "%.1f%% exact, %.1f%% by sound (of %d)".format(100.0 * a[1] / a[0], 100.0 * a[2] / a[0], a[0])
            println("BANGLISH $setName, $name: next word ${pct(next)}; after 1 letter ${pct(after[0])}; after 2 letters ${pct(after[1])}; ${"%.1f".format((System.nanoTime() - t0) / 1e6 / next[0])} ms per word")
        }
    }

    /**
     * BanglaTLit's held-out test sentences (BANGLISH_TEST=…/BanglaTLiT_test.csv): how often the
     * word being typed is offered after its first two letters, and how often autocorrect would
     * change a Banglish word, with and without the Banglish list.
     */
    @Test fun `held out banglish sentences`() {
        val path = System.getenv("BANGLISH_TEST")
        assumeTrue("set BANGLISH_TEST to BanglaTLiT_test.csv", path != null && File(path).exists())
        val sentences = File(path!!).readLines().drop(1).mapNotNull { line ->
            Regex("^[^,]*,(\"(?:[^\"]|\"\")*\"|[^,]*),").find(line)?.groupValues?.get(1)?.trim('"')
        }.take(2500)
        for ((name, s) in listOf("without" to Suggester({ en }, { bn }, { avro }, UserHistory()), "with" to Suggester({ en }, { bn }, { avro }, UserHistory(), banglish = { words }))) {
            var typedWords = 0
            var offered = 0
            var changed = 0
            for (sentence in sentences) {
                val ws = sentence.split(Regex("\\s+")).map { it.trim { c -> !c.isLetter() }.lowercase() }
                ws.forEachIndexed { i, w ->
                    if (w.length < 3 || !w.all { it in 'a'..'z' } || en.lookup(w)?.let { it.frequency >= 100 } == true) return@forEachIndexed
                    typedWords++
                    val ctx = on.copy(context = ws.subList(maxOf(0, i - 4), i).filter { it.isNotEmpty() })
                    if (s.english(w.take(2), ctx).candidates.any { it.equals(w, ignoreCase = true) }) offered++
                    val fix = s.english(w, ctx).autoCorrect
                    if (fix != null && !fix.equals(w, ignoreCase = true)) changed++
                }
            }
            println("Banglish $name list: $typedWords words; offered after 2 letters %.1f%%; autocorrect changed %.1f%%".format(100.0 * offered / typedWords, 100.0 * changed / typedWords))
        }
    }
}
