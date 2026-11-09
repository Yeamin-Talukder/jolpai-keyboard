package com.yeamin.jolpai.suggest

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tanh

/**
 * A small neural next-word model: given the words of a sentence so far, how likely each of its
 * words is to come next. Reads the `SWKBNWP1` format of the Swipe Keyboard models
 * (huggingface.co/youcoded/next-word-english): one LSTM layer with a projection, word vectors
 * shared between input and output, 8-bit weights with one float scale per row.
 *
 * Words are lower-case letters only; anything else (capitals are lowered first) is "unknown".
 * Pure Kotlin, no machine-learning library. Immutable after loading, so thread-safe.
 */
class NextWordModel private constructor(
    /** Token → word; tokens 0–2 (unknown, sentence start, sentence end) map to "". */
    val words: Array<String>,
    private val index: HashMap<String, Int>,
    val dim: Int,
    val hidden: Int,
    private val emb: QMatrix,
    private val outBias: FloatArray,
    private val wih: QMatrix,
    private val whh: QMatrix,
    private val gateBias: FloatArray,
    private val proj: QMatrix,
) {
    val vocabSize: Int get() = words.size

    /** The token for [word] (any case), or [UNKNOWN]. */
    fun token(word: String): Int = index[word] ?: index[word.lowercase()] ?: UNKNOWN

    /** True if [word] is one of the model's words. */
    fun knows(word: String): Boolean = token(word) >= FIRST_WORD

    /** The word tokens in alphabetical order of their words (for prefix searches). */
    val alphabetical: IntArray by lazy {
        (FIRST_WORD until words.size).sortedBy { words[it] }.toIntArray()
    }

    /** The memory of the network after reading some words. Immutable. */
    class State internal constructor(internal val h: FloatArray, internal val c: FloatArray)

    /** The state after the sentence-start token: ready to predict a sentence's first word. */
    fun start(): State = step(State(FloatArray(dim), FloatArray(hidden)), START)

    /** The state after reading [token]. */
    fun step(state: State, token: Int): State {
        val x = FloatArray(dim)
        emb.row(token, x)
        val g = gateBias.copyOf()
        wih.timesAdd(x, g)
        whh.timesAdd(state.h, g)
        val c = FloatArray(hidden)
        val o = FloatArray(hidden)
        val h = hidden
        for (j in 0 until h) {
            val i = sigmoid(g[j])
            val f = sigmoid(g[h + j])
            val cand = tanh(g[2 * h + j])
            c[j] = f * state.c[j] + i * cand
            o[j] = sigmoid(g[3 * h + j]) * tanh(c[j])
        }
        val out = FloatArray(dim)
        proj.timesAdd(o, out)
        return State(out, c)
    }

    /** Natural-log probabilities of every token coming next (a log-softmax over the vocabulary). */
    fun logProbs(state: State): FloatArray {
        val logits = outBias.copyOf()
        emb.timesAdd(state.h, logits)
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        var sum = 0.0
        for (v in logits) sum += exp((v - max).toDouble())
        val norm = max + ln(sum).toFloat()
        for (k in logits.indices) logits[k] -= norm
        return logits
    }

    /** The word vector of [token] (as the model reads it). */
    fun vector(token: Int, out: FloatArray = FloatArray(dim)): FloatArray {
        emb.row(token, out)
        return out
    }

    companion object {
        const val UNKNOWN = 0
        const val START = 1
        const val END = 2
        const val FIRST_WORD = 3

        private const val MAGIC = "SWKBNWP1"

        /** Loads the bundled English model, or null if it is missing or damaged. */
        fun loadEnglish(): NextWordModel? = load("/lm/en.nwp.bin")

        /** Loads the bundled Bangla model (trained for Jolpai with lm-trainer), or null. */
        fun loadBangla(): NextWordModel? = load("/lm/bn.nwp.bin")

        fun load(resource: String): NextWordModel? =
            NextWordModel::class.java.getResourceAsStream(resource)?.use { runCatching { read(it) }.getOrNull() }

        /** Reads a model file; throws if it is not a valid int8 `SWKBNWP1` file. */
        fun read(input: InputStream): NextWordModel {
            val bytes = input.readBytes()
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(String(bytes, 0, 8, Charsets.US_ASCII) == MAGIC) { "not a next-word model" }
            b.position(8)
            val version = b.int
            val dtype = b.int
            val vocab = b.int
            val dim = b.int
            val hidden = b.int
            val wordBytes = b.int
            b.float // blend weight (for the upstream n-gram blend; not used here)
            b.float // blend offset
            require(version == 1 && dtype == 1) { "unsupported model version $version / type $dtype" }
            val list = String(bytes, 40, wordBytes, Charsets.UTF_8).split('\n')
            require(list.size == vocab - FIRST_WORD) { "word list has ${list.size} words, expected ${vocab - FIRST_WORD}" }
            var at = 40 + pad16(wordBytes)

            fun matrix(rows: Int, cols: Int): QMatrix {
                val q = bytes.copyOfRange(at, at + rows * cols)
                at += pad16(rows * cols)
                val s = FloatArray(rows)
                b.position(at)
                for (r in 0 until rows) s[r] = b.float
                at += rows * 4
                return QMatrix(rows, cols, q, s)
            }

            fun floats(n: Int): FloatArray {
                b.position(at)
                val out = FloatArray(n) { b.float }
                at += n * 4
                return out
            }

            val emb = matrix(vocab, dim)
            val outBias = floats(vocab)
            val wih = matrix(4 * hidden, dim)
            val whh = matrix(4 * hidden, dim)
            val gateBias = floats(4 * hidden)
            val proj = matrix(dim, hidden)
            require(at + 8 == bytes.size) { "unexpected file length ${bytes.size}, layout ends at ${at + 8}" }

            val words = Array(vocab) { if (it < FIRST_WORD) "" else list[it - FIRST_WORD] }
            val index = HashMap<String, Int>(vocab * 2)
            for (t in FIRST_WORD until vocab) index[words[t]] = t
            return NextWordModel(words, index, dim, hidden, emb, outBias, wih, whh, gateBias, proj)
        }

        private fun pad16(n: Int) = (n + 15) / 16 * 16

        private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
    }
}

/** An 8-bit matrix with one scale per row (value = q × scale). */
internal class QMatrix(val rows: Int, val cols: Int, private val q: ByteArray, private val scale: FloatArray) {
    /** out += this × x */
    fun timesAdd(x: FloatArray, out: FloatArray) {
        val c = cols
        val c4 = c - c % 4
        val q = q
        var base = 0
        for (r in 0 until rows) {
            // Four running sums: shorter dependency chains, so the CPU overlaps the work.
            var s0 = 0f
            var s1 = 0f
            var s2 = 0f
            var s3 = 0f
            var k = 0
            while (k < c4) {
                s0 += q[base + k] * x[k]
                s1 += q[base + k + 1] * x[k + 1]
                s2 += q[base + k + 2] * x[k + 2]
                s3 += q[base + k + 3] * x[k + 3]
                k += 4
            }
            while (k < c) {
                s0 += q[base + k] * x[k]
                k++
            }
            out[r] += (s0 + s1 + s2 + s3) * scale[r]
            base += c
        }
    }

    /** Row [r] as floats. */
    fun row(r: Int, out: FloatArray) {
        val s = scale[r]
        val base = r * cols
        for (k in 0 until cols) out[k] = q[base + k] * s
    }

    /** Dot product of row [r] with [x]. */
    fun rowDot(r: Int, x: FloatArray): Float {
        val base = r * cols
        var sum = 0f
        for (k in 0 until cols) sum += q[base + k] * x[k]
        return sum * scale[r]
    }
}
