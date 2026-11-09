package com.yeamin.jolpai.lmtrainer

import com.yeamin.jolpai.suggest.LanguageModel
import com.yeamin.jolpai.suggest.NextWordModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

class LstmTest {

    /** Analytic gradients match finite differences (exact softmax, a tiny network). */
    @Test fun `gradients are right`() {
        val m = Lstm(vocab = 9, dim = 4, hidden = 5, seed = 3)
        val words = intArrayOf(3, 5, 4, 8)
        fun loss(): Double = m.Work(8).let { m.sentence(words, it, null, null) }
        val w = m.Work(8)
        m.sentence(words, w, null, null)
        val checks = listOf(
            Triple("emb", m.emb, w.dEmb), Triple("wih", m.wih, w.dWih), Triple("whh", m.whh, w.dWhh),
            Triple("bias", m.bias, w.dBias), Triple("proj", m.proj, w.dProj), Triple("outBias", m.outBias, w.dOutBias),
        )
        val r = Random(1)
        for ((name, p, g) in checks) {
            repeat(6) {
                val i = r.nextInt(p.size)
                val old = p[i]
                val eps = 1e-3f
                p[i] = old + eps; val up = loss()
                p[i] = old - eps; val down = loss()
                p[i] = old
                val numeric = (up - down) / (2 * eps)
                val analytic = g[i].toDouble()
                assertTrue("$name[$i]: numeric $numeric vs analytic $analytic", abs(numeric - analytic) < 2e-3 + 2e-2 * abs(numeric))
            }
        }
    }

    /** Training on a tiny made-up language lowers the loss, and the export reads back in the app's reader. */
    @Test fun `learns a pattern and exports a file the app can read`() {
        val m = Lstm(vocab = 3 + 6, dim = 8, hidden = 16, seed = 2)
        val w = m.Work(8)
        // "a b c" and "d e f": after a comes b, after d comes e.
        val data = listOf(intArrayOf(3, 4, 5), intArrayOf(6, 7, 8))
        fun loss() = data.sumOf { s -> m.Work(8).let { m.sentence(s, it, null, null) } }
        val before = loss()
        repeat(300) { data.forEach { s -> m.sentence(s, w, null, null); m.update(w, 0.3f) } }
        val after = loss()
        assertTrue("loss $before → $after", after < before / 4)
        val f = File.createTempFile("tiny", ".nwp.bin")
        m.export(f, listOf("a", "b", "c", "d", "e", "f"))
        val model = NextWordModel.read(f.inputStream())
        val lm = LanguageModel { model }
        assertEquals("b", lm.predict(listOf("a"))!!.top(1).single().first)
        assertEquals("e", lm.predict(listOf("d"))!!.top(1).single().first)
        f.delete()
    }

    @Test fun `sampled softmax training lowers the exact loss`() {
        val m = Lstm(vocab = 3 + 6, dim = 8, hidden = 16, seed = 2)
        val w = m.Work(8)
        val data = listOf(intArrayOf(3, 4, 5), intArrayOf(6, 7, 8))
        fun loss() = data.sumOf { s -> m.Work(8).let { m.sentence(s, it, null, null) } }
        val before = loss()
        val r = kotlin.random.Random(4)
        val k = 4
        val logExpected = FloatArray(m.vocab) { kotlin.math.ln(k / 7f) }
        repeat(400) { data.forEach { s ->
            val neg = IntArray(k) { 2 + r.nextInt(7) }
            m.sentence(s, w, neg, logExpected); m.update(w, 0.1f)
        } }
        val after = loss()
        println("sampled: $before -> $after")
        assertTrue("loss $before → $after", after < before / 2)
    }

    @Test fun `bangla text is normalised and split into sentences`() {
        val s = BanglaText.sentences("আমি ভাত খাই। তুমি কোথায় যাও? hello বন্ধু").toList()
        assertEquals(listOf("আমি", "ভাত", "খাই"), s[0])
        assertEquals(listOf("তুমি", "কোথা\u09DF", "যাও"), s[1])
        assertEquals(listOf(BanglaText.UNK, "বন্ধু"), s[2])
        // য + nukta → য়
        assertEquals("\u09DF", BanglaText.normalize("\u09AF\u09BC"))
    }

    @Test fun `broken subtitle text is dropped`() {
        val broken = BanglaText.sentences("যুদ্ধ শষহয়ছঅনকবছরহল").first()
        val good = BanglaText.sentences("তার সন্তানদের হাতে কিছু খেজুর রয়েছে").first()
        assertTrue(!BanglaText.usable(broken))
        assertTrue(BanglaText.usable(good))
    }

    @Test fun `wiki markup is removed`() {
        val text = WikiText.clean("'''ঢাকা''' [[বাংলাদেশ|বাংলাদেশের]] রাজধানী।{{তথ্যছক|নাম=ঢাকা}} এটি [[বড়]] শহর।<ref>সূত্র</ref>\n[[বিষয়শ্রেণী:শহর]]")
        assertEquals("ঢাকা বাংলাদেশের রাজধানী। এটি বড় শহর। ", text.replace(Regex(" +"), " ").trim() + " ")
    }
}
