package com.yeamin.jolpai.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class NextWordModelTest {

    companion object {
        val model: NextWordModel by lazy { assertNotNull(NextWordModel.loadEnglish()); NextWordModel.loadEnglish()!! }
    }

    private val lm = LanguageModel { model }

    @Test fun `reads the bundled model`() {
        assertEquals(23_778, model.vocabSize)
        assertEquals(128, model.dim)
        assertEquals(1024, model.hidden)
        assertTrue(model.knows("the") && model.knows("thank"))
        assertTrue(!model.knows("don't")) // letters only
    }

    @Test fun `matches the model card's example`() {
        // README of youcoded/next-word-english: "i would like to" → ['thank', 'know', 'see']
        val top = lm.predict("i would like to".split(" "))!!.top(3).map { it.first }
        assertEquals(listOf("thank", "know", "see"), top)
    }

    @Test fun `probabilities add up to one`() {
        val p = lm.predict(listOf("how", "are"))!!
        var sum = 0.0
        for (t in 0 until model.vocabSize) sum += exp(p.logProbOrUnknown(model.words[t].ifEmpty { "\u0000" }).toDouble()).takeIf { model.words[t].isNotEmpty() } ?: 0.0
        assertTrue("sum of word probabilities $sum", sum in 0.5..1.0001)
        assertTrue(p.top(1).first().first == "you")
    }

    @Test fun `context changes the prediction`() {
        val afterEat = lm.predict(listOf("i", "want", "to", "eat"))!!
        val afterDrive = lm.predict(listOf("i", "want", "to", "drive"))!!
        assertTrue(afterEat.logProb("pizza")!! > afterDrive.logProb("pizza")!!)
    }

    @Test fun `cached states give the same answer as reading from scratch`() {
        val a = lm.predict(listOf("see", "you", "tomorrow", "at"))!!.logProb("the")!!
        val fresh = LanguageModel { model }.predict(listOf("see", "you", "tomorrow", "at"))!!.logProb("the")!!
        assertTrue(abs(a - fresh) < 1e-5f)
    }

    @Test fun `prefix search and next letters`() {
        val p = lm.predict("thank".split(" "))!!
        assertEquals("you", p.top(1, "yo").single().first)
        val out = HashMap<Char, Float>()
        p.nextCharWeights("y", out)
        assertEquals('o', out.maxByOrNull { it.value }!!.key)
    }

    @Test fun `sequence scores prefer the grammatical word`() {
        val ctx = listOf("i", "think")
        val good = lm.sequenceLogProb(ctx, listOf("they", "are", "going"))!!
        val bad = lm.sequenceLogProb(ctx, listOf("their", "going"))!!
        assertTrue("$good vs $bad", good > bad)
    }

    @Test fun `one step and a full prediction are fast enough`() {
        val m = LanguageModel { model }
        m.predict(listOf("warm", "up"))
        val t0 = System.nanoTime()
        repeat(20) { i -> m.predict(listOf("this", "is", "word$i")) }
        val ms = (System.nanoTime() - t0) / 1e6 / 20
        println("LM one word + full prediction: %.2f ms".format(ms))
        assertTrue("took $ms ms", ms < 50)
    }
}
