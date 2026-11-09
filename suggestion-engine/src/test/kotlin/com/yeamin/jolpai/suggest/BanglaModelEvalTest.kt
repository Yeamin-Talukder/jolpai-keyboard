package com.yeamin.jolpai.suggest

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Bangla next-word model inside the real suggestion code, on held-out sentences the model
 * was not trained on (valid-*.txt written by lm-trainer: one normalised sentence per line).
 * Runs when LM_BN_DIR points at that folder; the model is LM_BN_MODEL (a .nwp.bin file) or the
 * bundled one. Without vs. with the model, same dictionary, list and empty history:
 * next word in the top 3, the right word in the 2 suggestions after 1 and 2 letters, and a
 * sweep of how much the model counts for next words. Also prints Avro candidates in context.
 */
class BanglaModelEvalTest {
    private val dir = System.getenv("LM_BN_DIR")?.let(::File)
    private val model by lazy {
        System.getenv("LM_BN_MODEL")?.let { NextWordModel.read(File(it).inputStream()) } ?: NextWordModel.loadBangla()
    }
    private val dict by lazy { BanglaDictionary.load() }
    private val avro by lazy { AvroSuggester.load(PhoneticEngine(AvroGrammar.load()), AvroDictionarySearch.load(dict), dict::frequency) }

    private fun suggester(weight: Float = Suggester.BANGLA_NEXT_MODEL) =
        Suggester({ null }, { dict }, { avro }, UserHistory(), banglaModel = { model }, banglaNextWeight = weight)

    private class Score(var hit: Int = 0, var total: Int = 0) {
        fun add(ok: Boolean) { total++; if (ok) hit++ }
        override fun toString() = "%.1f%%".format(100.0 * hit / maxOf(1, total))
    }

    private fun sentences(f: File, n: Int = 300) =
        f.readLines().map { it.split(' ') }.filter { it.size >= 3 && it.none { w -> w == "<unk>" } }.shuffled(kotlin.random.Random(3)).take(n)

    @Test fun `bangla model on held-out sentences`() {
        assumeTrue(dir != null && model != null)
        val files = dir!!.listFiles { f -> f.name.startsWith("valid-") && f.name.endsWith(".txt") }!!.sortedBy { it.name }
        for (f in files) {
            val data = sentences(f)
            if (data.size < 50) continue
            val base = suggester()
            fun nextHits(s: Suggester, useModel: Boolean): Score {
                val sc = Score()
                data.forEach { w -> for (i in w.indices) sc.add(w[i] in s.nextWords(Language.BANGLA, w.getOrNull(i - 1), 3, SuggestOptions(context = w.take(i), useModel = useModel))) }
                return sc
            }
            val without = nextHits(base, false)
            val with = nextHits(base, true)
            val after = (1..2).map { k ->
                val a = Score()
                val b = Score()
                data.forEach { w ->
                    for (i in w.indices) {
                        if (w[i].length <= k) continue
                        val o = SuggestOptions(context = w.take(i), limit = 2)
                        a.add(w[i] in base.bangla(w[i].take(k), 2, o).candidates)
                        b.add(w[i] in base.bangla(w[i].take(k), 2, o.copy(useModel = true)).candidates)
                    }
                }
                "after $k letter(s) $a → $b"
            }
            println("BN ${f.name} (${data.size} sentences): next word top-3 $without → $with | ${after.joinToString(" | ")}")
            if (System.getenv("LM_BN_SWEEP") != null) {
                for (wt in listOf(0.5f, 1f, 1.5f, 2.5f, 4f, 8f)) println("BN   sweep weight $wt: next word ${nextHits(suggester(wt), true)}")
            }
        }
        // Avro: the same Roman spelling in different sentences.
        val s = suggester()
        listOf(
            listOf("আমি") to "bhat", listOf("আমি", "ভাত") to "khai", listOf("আজ") to "kal", listOf("গতকাল") to "rat",
            listOf("তুমি", "কি") to "korcho", listOf("আমার") to "nam", listOf("আমি", "তোমাকে") to "valobashi",
            listOf("সে") to "ashbe", listOf("একটু") to "pore",
        ).forEach { (ctx, term) ->
            val plain = s.avro(term)!!.candidates.take(4)
            val withModel = s.avro(term, SuggestOptions(context = ctx, useModel = true))!!.candidates.take(4)
            println("BN avro ${ctx.joinToString(" ")} + $term: $plain → $withModel")
        }
    }
}
