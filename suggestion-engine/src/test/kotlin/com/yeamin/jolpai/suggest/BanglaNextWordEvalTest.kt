package com.yeamin.jolpai.suggest

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Next-word accuracy of the Bangla lists on held-out text. Runs only when BN_EVAL_FILE names a
 * file of sentences in the format of tools/prepare-bangla-nextwords.pl (words split by spaces,
 * " | " where the word run breaks), e.g. Leipzig ben_newscrawl_2015_10K minus sentences that are
 * also in the training text.
 */
class BanglaNextWordEvalTest {
    @Test fun `next word in the top 3`() {
        val file = System.getenv("BN_EVAL_FILE")?.let(::File)
        assumeTrue(file != null && file.exists())
        val sentences = file!!.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        val dict = BanglaDictionary.load()!!
        fun text(path: String) = javaClass.getResourceAsStream(path)!!.use { String(it.readBytes(), Charsets.UTF_8) }
        val pairs = text("/bn/bangla-nextwords.tsv")
        val triples = text("/bn/bangla-trigrams.tsv")

        fun run(name: String, list: BanglaNextWords?, learn: Boolean) {
            dict.nextWords = list
            val history = UserHistory()
            val s = Suggester({ null }, { dict }, { null }, history)
            var n = 0
            var hit = 0
            var startN = 0
            var startHit = 0
            val t0 = System.nanoTime()
            for (line in sentences) {
                line.split(" | ").forEachIndexed { f, frag ->
                    val all = frag.split(" ").filter { it.isNotEmpty() }
                    val start = all.firstOrNull() == BanglaNextWords.START // the file marks real sentence starts
                    val w = all.filter { it != BanglaNextWords.START }
                    for (i in w.indices) {
                        if (i == 0 && !start) continue // a word run after a break is not a sentence start
                        val prev = w.getOrNull(i - 1)
                        val got = s.nextWords(Language.BANGLA, prev, 3, SuggestOptions(context = w.subList(0, i)))
                        val ok = got.any { BanglaNextWords.normalize(it) == BanglaNextWords.normalize(w[i]) }
                        n++; if (ok) hit++
                        if (i == 0) { startN++; if (ok) startHit++ }
                        if (learn) history.recordWord(Language.BANGLA, w[i], prev, w.getOrNull(i - 2))
                    }
                }
            }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("%-36s next word in top 3: %5.1f %% of %d   sentence start: %5.1f %%   %.3f ms/word".format(
                name, 100.0 * hit / n, n, 100.0 * startHit / startN, ms))
        }

        println("Held-out sentences: ${sentences.size}")
        run("nothing (empty history)", null, learn = false)
        run("learning only", null, learn = true)
        run("pair list", BanglaNextWords.of(pairs), learn = false)
        run("pair + three-word list", BanglaNextWords.of(pairs, triples), learn = false)
        run("pair + three-word list + learning", BanglaNextWords.of(pairs, triples), learn = true)
        dict.nextWords = BanglaNextWords.of(pairs, triples)
    }
}
