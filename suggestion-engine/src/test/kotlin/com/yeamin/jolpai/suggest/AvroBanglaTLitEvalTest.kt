package com.yeamin.jolpai.suggest

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Avro on real Banglish with its intended Bangla: BanglaTLit's held-out val and test sentences
 * (MIT; romanized comments written out in Bangla by the dataset's annotators). Each sentence
 * whose two sides have the same number of words is typed word by word (lower case, as typed on a
 * phone); a word counts when space writes the intended Bangla word (Avro's first candidate, empty
 * history), when it is among the first 3 candidates, or anywhere in the strip. The words before
 * are the intended ones (as if each earlier word was picked right).
 *
 * Sweeps how much the Bangla model ([AvroSuggester.contextPoints]) and word frequency
 * ([AvroSuggester.frequencyPoints]) count against closeness to the spelling (10 per letter) on the
 * val sentences, then reports the best and today's settings on the test sentences.
 *
 * Runs when BANGLISH_TEST points at BanglaTLiT_test.csv (BanglaTLiT_val.csv next to it).
 */
class AvroBanglaTLitEvalTest {
    private fun pairs(file: File): List<List<Pair<String, String>>> {
        val rows = Regex("^[^,]*,(\"(?:[^\"]|\"\")*\"|[^,]*),(\"(?:[^\"]|\"\")*\"|[^,]*)\\s*$")
        return file.readLines().drop(1).mapNotNull { line ->
            val m = rows.find(line) ?: return@mapNotNull null
            val r = m.groupValues[1].trim('"').replace("\"\"", "\"").trim().split(Regex("\\s+"))
            val b = m.groupValues[2].trim('"').replace("\"\"", "\"").trim().split(Regex("\\s+"))
            if (r.size != b.size) return@mapNotNull null
            r.indices.mapNotNull { k ->
                val roman = r[k].trim { !it.isLetter() }.lowercase()
                val bangla = BanglaNextWords.normalize(b[k].trim { it !in 'ঀ'..'৿' || it in "।॥" })
                if (roman.isEmpty() || !roman.all { it in 'a'..'z' } || bangla.isEmpty() || !bangla.all { it in 'ঀ'..'৿' }) null
                else roman to bangla
            }.takeIf { it.isNotEmpty() }
        }
    }

    private class Score { var n = 0; var space = 0; var top3 = 0; var any = 0
        override fun toString() = "space %.1f%%, top 3 %.1f%%, on the strip %.1f%%".format(100.0 * space / n, 100.0 * top3 / n, 100.0 * any / n)
    }

    @Test fun `avro on banglatlit`() {
        val test = System.getenv("BANGLISH_TEST")?.let(::File)
        assumeTrue("set BANGLISH_TEST to BanglaTLiT_test.csv", test != null && test.exists())
        val valid = File(test!!.parentFile, "BanglaTLiT_val.csv")
        val dict = BanglaDictionary.load()
        val avro = AvroSuggester.load(PhoneticEngine(AvroGrammar.load()), AvroDictionarySearch.load(dict), dict::frequency)
        val model = NextWordModel.loadBangla()
        assumeTrue(model != null)
        val s = Suggester({ null }, { dict }, { avro }, UserHistory(), banglaModel = { model })

        // (name, useModel, contextPoints, frequencyPoints)
        fun run(sentences: List<List<Pair<String, String>>>, ways: List<Triple<Boolean, Float, Float>>): List<Score> {
            val scores = ways.map { Score() }
            for (sentence in sentences) sentence.forEachIndexed { k, (r, b) ->
                val ctx = sentence.take(k).map { it.second }
                ways.forEachIndexed { i, (useModel, cp, fp) ->
                    avro.contextPoints = cp
                    avro.frequencyPoints = fp
                    val c = s.avro(r, SuggestOptions(context = ctx, useModel = useModel))!!
                    val norm = c.candidates.map(BanglaNextWords::normalize)
                    val sc = scores[i]
                    sc.n++
                    if (BanglaNextWords.normalize(c.selectedText) == b) sc.space++
                    if (b in norm.take(3)) sc.top3++
                    if (b in norm) sc.any++
                }
            }
            avro.contextPoints = AvroSuggester.CONTEXT_POINTS
            avro.frequencyPoints = AvroSuggester.FREQUENCY_POINTS
            return scores
        }

        val v = pairs(valid)
        println("AVRO val: ${v.size} sentences, ${v.sumOf { it.size }} words")
        val sweep = buildList {
            add(Triple(false, 0f, 0f))
            for (fp in listOf(0f, 0.2f, 0.5f, 1f)) add(Triple(false, 0f, fp))
            for (cp in listOf(3f, 6f, 10f, 15f, 25f)) for (fp in listOf(0f, 0.2f, 0.5f)) add(Triple(true, cp, fp))
        }.distinct()
        val vs = run(v, sweep)
        sweep.zip(vs).forEach { (w, sc) -> println("AVRO val model=${w.first} context=${w.second} frequency=${w.third}: $sc") }
        val best = sweep.zip(vs).filter { it.first.first }.maxBy { it.second.space }.first
        val bestNoModel = sweep.zip(vs).filter { !it.first.first }.maxBy { it.second.space }.first

        val t = pairs(test)
        println("AVRO test: ${t.size} sentences, ${t.sumOf { it.size }} words")
        // Before (3 points, frequency for ties only), the defaults now, and the best on val.
        val final = listOf(
            Triple(false, 0f, 0f), Triple(true, 3f, 0f),
            Triple(false, 0f, AvroSuggester.FREQUENCY_POINTS), Triple(true, AvroSuggester.CONTEXT_POINTS, AvroSuggester.FREQUENCY_POINTS),
            bestNoModel, best,
        ).distinct()
        run(t, final).zip(final).forEach { (sc, w) -> println("AVRO test model=${w.first} context=${w.second} frequency=${w.third}: $sc") }
    }
}
