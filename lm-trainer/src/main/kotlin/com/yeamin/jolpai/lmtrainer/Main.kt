package com.yeamin.jolpai.lmtrainer

import com.yeamin.jolpai.suggest.LanguageModel
import com.yeamin.jolpai.suggest.NextWordModel
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Trains a next-word model for the keyboard. Steps (all files in DIR):
 *
 *   prepare DIR --subtitles bn.txt.gz --wiki bnwiki.xml   sentences-*.txt, valid-*.txt
 *   encode  DIR --vocab 30000                             vocab.tsv, train.bin, valid-*.bin
 *   train   DIR --dim 96 --hidden 512 --minutes 120       model.ckpt (resumes if present)
 *   eval    DIR                                           perplexity and next-word accuracy
 *   export  DIR OUT.nwp.bin                               the app's int8 file, checked by its reader
 */
fun main(args: Array<String>) {
    val cmd = args.getOrNull(0) ?: return usage()
    val dir = File(args.getOrNull(1) ?: return usage())
    val opts = args.drop(2).chunked(2).filter { it.size == 2 && it[0].startsWith("--") }.associate { it[0].removePrefix("--") to it[1] }
    fun int(name: String, default: Int) = opts[name]?.toInt() ?: default
    fun float(name: String, default: Float) = opts[name]?.toFloat() ?: default
    if (cmd != "xlsx-peek") dir.mkdirs()
    when (cmd) {
        "prepare" -> {
            val p = Prepare(dir, 0)
            opts["subtitles"]?.let { p.subtitles(File(it)) }
            opts["wiki"]?.let { p.wikipedia(File(it)) }
            // Everyday text: --lines source=file[:column], --blocklist file (labelled comments), --xlsx source=file:J,N
            opts["blocklist"]?.let { p.learnBlocklist(File(it), "A", "E", setOf("troll", "sexual", "threat", "religious"), clean = opts["clean"]?.split(",")?.map(::File).orEmpty()) }
            opts["lines"]?.split(",")?.forEach { spec ->
                val (source, rest) = spec.split("=", limit = 2)
                val col = rest.substringAfterLast("@", "").toIntOrNull() ?: -1
                p.lines(File(rest.substringBeforeLast("@")), source, col)
            }
            opts["xlsx"]?.split(";")?.forEach { spec ->
                val (source, rest) = spec.split("=", limit = 2)
                val file = rest.substringBeforeLast(":")
                val cols = rest.substringAfterLast(":").split(",")
                val label = opts["label-" + source]
                p.xlsx(File(file), source, cols, label?.substringBefore("="), label?.substringAfter("=")?.split("|")?.toSet() ?: emptySet())
            }
        }
        "xlsx-peek" -> Xlsx.rows(dir).take(args.getOrNull(2)?.toInt() ?: 4).forEach { row ->
            println(row.entries.sortedBy { it.key.length * 100 + it.key.first().code }.joinToString(" | ") { "${it.key}: ${it.value.take(160).replace(Regex("\\s+"), " ")}" })
        }
        "encode" -> Prepare(dir, int("vocab", 30000), int("repeat-everyday", 2), opts["wiki-words"]?.toLong() ?: Long.MAX_VALUE).encode()
        "train" -> Training(dir, int("dim", 96), int("hidden", 512)).train(
            minutes = int("minutes", 60), threads = int("threads", Runtime.getRuntime().availableProcessors()),
            lr = float("lr", 0.05f), negatives = int("negatives", 256), batch = int("batch", 16),
        )
        "eval" -> Training(dir, int("dim", 96), int("hidden", 512)).evaluate(int("sentences", 3000))
        "export" -> Training(dir, int("dim", 96), int("hidden", 512)).export(File(args.getOrNull(2) ?: return usage()))
        else -> usage()
    }
}

private fun usage() = println("usage: prepare|encode|train|eval|export DIR [options] (see Main.kt)")

class Training(private val dir: File, dim: Int, hidden: Int) {
    private val vocabWords: List<String> = File(dir, "vocab.tsv").readLines().map { it.substringBefore('\t') }
    private val counts: List<Long> = File(dir, "vocab.tsv").readLines().map { it.substringAfter('\t').toLong() }
    val model = Lstm(vocabWords.size + 3, dim, hidden)
    private val checkpoint = File(dir, "model-$dim-$hidden.ckpt")

    init {
        if (checkpoint.exists()) {
            model.load(checkpoint)
            println("resumed from ${checkpoint.name}")
        }
    }

    fun train(minutes: Int, threads: Int, lr: Float, negatives: Int, batch: Int) {
        val corpus = Corpus.read(File(dir, "train.bin"))
        val totalWords = corpus.tokens.size.toLong()
        println("training: ${corpus.sentences} sentences, $totalWords words; model ${model.parameterCount} numbers; $threads threads")
        // Sampling distribution for negatives: count^0.75 (unknown and end get their real counts).
        val unk = corpus.tokens.count { it.toInt() == 0 }.toLong()
        val freq = DoubleArray(model.vocab)
        freq[0] = Math.pow(unk.toDouble(), 0.75)
        freq[2] = Math.pow(corpus.sentences.toDouble(), 0.75)
        counts.forEachIndexed { i, c -> freq[i + 3] = Math.pow(c.toDouble(), 0.75) }
        val total = freq.sum()
        val q = DoubleArray(model.vocab) { freq[it] / total }
        val logExpected = FloatArray(model.vocab) { if (q[it] > 0) ln(negatives * q[it]).toFloat() else 0f }
        val alias = AliasTable(q)

        val deadline = System.currentTimeMillis() + minutes * 60_000L
        val order = (0 until corpus.sentences).shuffled(Random(System.nanoTime().toInt())).toIntArray()
        val done = AtomicLong()
        val lossSum = DoubleArray(threads)
        val lossTokens = LongArray(threads)
        val workers = (0 until threads).map { id ->
            thread(name = "train-$id") {
                val w = model.Work(64)
                val r = Random(id * 7919 + System.nanoTime().toInt())
                val neg = IntArray(negatives)
                var inBatch = 0
                var i = id
                while (i < order.size && System.currentTimeMillis() < deadline) {
                    val s = order[i]
                    val len = corpus.length(s)
                    val words = IntArray(len) { corpus.token(s, it) }
                    for (k in neg.indices) neg[k] = alias.sample(r)
                    lossSum[id] += model.sentence(words, w, neg, logExpected)
                    lossTokens[id] += len + 1L
                    done.addAndGet(len + 1L)
                    if (++inBatch == batch) { model.update(w, lr); inBatch = 0 }
                    i += threads
                }
                model.update(w, lr)
            }
        }
        val start = System.currentTimeMillis()
        var lastSave = start
        while (workers.any { it.isAlive }) {
            Thread.sleep((System.getenv("LM_REPORT_SECONDS")?.toLong() ?: 30L) * 1000)
            val secs = (System.currentTimeMillis() - start) / 1000.0
            val tokens = done.get()
            val loss = lossSum.sum() / maxOf(1, lossTokens.sum())
            println("  %.0f min: %,d words (%.1f%% of one pass), %,.0f words/s, sampled loss %.3f".format(secs / 60, tokens, 100.0 * tokens / totalWords, tokens / secs, loss))
            for (t in lossSum.indices) { lossSum[t] = 0.0; lossTokens[t] = 0 }
            if (System.currentTimeMillis() - lastSave > 10 * 60_000) {
                model.save(checkpoint); lastSave = System.currentTimeMillis()
            }
        }
        model.save(checkpoint)
        println("saved ${checkpoint.name}")
    }

    /** Perplexity and next-word top-3 accuracy (exact softmax) on each valid-*.bin. */
    fun evaluate(limit: Int) {
        dir.listFiles { f -> f.name.startsWith("valid-") && f.name.endsWith(".bin") }!!.sortedBy { it.name }.forEach { f ->
            val corpus = Corpus.read(f, limit)
            val threads = Runtime.getRuntime().availableProcessors()
            val nll = DoubleArray(threads)
            val targets = LongArray(threads)
            val wordTargets = LongArray(threads)
            val top3 = LongArray(threads)
            (0 until threads).map { id ->
                thread {
                    val w = model.Work(64)
                    val out = FloatArray(model.vocab)
                    var s = id
                    while (s < corpus.sentences) {
                        val words = IntArray(corpus.length(s)) { corpus.token(s, it) }
                        model.forwardAll(words, w)
                        for (t in 0..words.size) {
                            val target = if (t < words.size) words[t] else 2
                            model.scoresAt(t, w, out)
                            nll[id] -= out[target]
                            targets[id]++
                            if (target >= 3) {
                                wordTargets[id]++
                                var better = 0
                                val v = out[target]
                                for (k in 3 until model.vocab) if (out[k] > v && ++better >= 3) break
                                if (better < 3) top3[id]++
                            }
                        }
                        s += threads
                    }
                }
            }.forEach { it.join() }
            println("${f.name}: perplexity %.1f; next word (known words) in top 3 %.1f%%".format(exp(nll.sum() / targets.sum()), 100.0 * top3.sum() / wordTargets.sum()))
        }
    }

    fun export(out: File) {
        model.export(out, vocabWords)
        val m = NextWordModel.read(out.inputStream())
        val lm = LanguageModel { m }
        val sample = vocabWords.take(3)
        println("exported ${out.name} (${out.length()} bytes): ${m.vocabSize} tokens; after '${sample[0]}': " +
            lm.predict(listOf(sample[0]))!!.top(5).joinToString { it.first })
    }
}

/** Walker's alias method: draws a token with probability q in constant time. */
class AliasTable(q: DoubleArray) {
    private val n = q.size
    private val prob = DoubleArray(n)
    private val alias = IntArray(n)

    init {
        val scaled = DoubleArray(n) { q[it] * n }
        val small = ArrayDeque<Int>()
        val large = ArrayDeque<Int>()
        for (i in 0 until n) if (scaled[i] < 1.0) small.add(i) else large.add(i)
        while (small.isNotEmpty() && large.isNotEmpty()) {
            val s = small.removeLast(); val l = large.removeLast()
            prob[s] = scaled[s]; alias[s] = l
            scaled[l] = scaled[l] + scaled[s] - 1.0
            if (scaled[l] < 1.0) small.add(l) else large.add(l)
        }
        while (large.isNotEmpty()) prob[large.removeLast()] = 1.0
        while (small.isNotEmpty()) prob[small.removeLast()] = 1.0
    }

    fun sample(r: Random): Int {
        val i = r.nextInt(n)
        return if (r.nextDouble() < prob[i]) i else alias[i]
    }
}
