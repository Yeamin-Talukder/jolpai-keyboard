package com.yeamin.jolpai.lmtrainer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The network the app reads (`NextWordModel`): word vectors E (V × D, also the output layer),
 * one LSTM layer of H cells whose output is projected back to D numbers, and an output bias.
 *
 *   g = Wih·E[x] + Whh·h + b          (4H: input, forget, cell, output gates)
 *   c = σ(f)·c + σ(i)·tanh(u);  h = P·(σ(o)·tanh(c))
 *   next-word scores = E·h + bias
 *
 * Training: sentences start with token 1 and predict each next word and then the end (2).
 * The softmax over the vocabulary is sampled ([negatives] words drawn by frequency^0.75,
 * shared by a sentence, with the usual log-expected-count correction); Adagrad updates, with
 * threads updating the shared weights without locks (Hogwild), which suits sparse word vectors.
 */
class Lstm(val vocab: Int, val dim: Int, val hidden: Int, seed: Int = 1) {
    val emb = FloatArray(vocab * dim)
    val outBias = FloatArray(vocab)
    val wih = FloatArray(4 * hidden * dim)
    val whh = FloatArray(4 * hidden * dim)
    val bias = FloatArray(4 * hidden)
    val proj = FloatArray(dim * hidden)

    // Adagrad sums of squared gradients.
    private val aEmb = FloatArray(vocab * dim)
    private val aOutBias = FloatArray(vocab)
    private val aWih = FloatArray(wih.size)
    private val aWhh = FloatArray(whh.size)
    private val aBias = FloatArray(bias.size)
    private val aProj = FloatArray(proj.size)

    init {
        val r = Random(seed)
        fun fill(a: FloatArray, scale: Float) { for (i in a.indices) a[i] = (r.nextFloat() * 2 - 1) * scale }
        fill(emb, 0.1f)
        fill(wih, 1f / sqrt(dim.toFloat()))
        fill(whh, 1f / sqrt(dim.toFloat()))
        fill(proj, 1f / sqrt(hidden.toFloat()))
        for (j in hidden until 2 * hidden) bias[j] = 1f // remember by default
        // Adagrad starts from 0.1, not 0: otherwise the first update moves every weight by the full step.
        for (a in listOf(aEmb, aOutBias, aWih, aWhh, aBias, aProj)) a.fill(0.1f)
    }

    val parameterCount: Long get() = (emb.size + outBias.size + wih.size + whh.size + bias.size + proj.size).toLong()

    /** Per-thread buffers: activations of one sentence and gradients of one batch. */
    inner class Work(maxLen: Int) {
        val n = maxLen + 2
        val hs = FloatArray(n * dim)      // h after step t (index t + 1; index 0 = zeros)
        val cs = FloatArray(n * hidden)   // c after step t (same indexing)
        val gi = FloatArray(n * hidden)
        val gf = FloatArray(n * hidden)
        val gu = FloatArray(n * hidden)
        val go = FloatArray(n * hidden)
        val tc = FloatArray(n * hidden)   // tanh(c)
        val dh = FloatArray(n * dim)      // d loss / d h from the output at each step
        val g = FloatArray(4 * hidden)
        val x = FloatArray(dim)
        val hp = FloatArray(dim)
        val de = FloatArray(dim)
        val dhStep = FloatArray(dim)
        val m = FloatArray(hidden)
        val dm = FloatArray(hidden)
        val dg = FloatArray(4 * hidden)
        val dhNext = FloatArray(dim)
        val dcNext = FloatArray(hidden)
        val scores = FloatArray(vocab)

        val dWih = FloatArray(wih.size)
        val dWhh = FloatArray(whh.size)
        val dBias = FloatArray(bias.size)
        val dProj = FloatArray(proj.size)
        val dEmb = FloatArray(vocab * dim)
        val dOutBias = FloatArray(vocab)
        val touched = BooleanArray(vocab)
        val touchedList = IntArrayList()
        var tokens = 0

        fun touch(k: Int) { if (!touched[k]) { touched[k] = true; touchedList.add(k) } }
    }

    /**
     * Runs one sentence ([words], without start/end) forward and backward, adding gradients to
     * [w]. [negatives] are the sampled words with [logExpected] = ln(K·q(k)); null means the
     * exact softmax over the whole vocabulary (for checks). Returns the summed loss.
     */
    fun sentence(words: IntArray, w: Work, negatives: IntArray?, logExpected: FloatArray?): Double {
        val T = words.size + 1 // inputs: start + words; targets: words + end
        var loss = 0.0
        java.util.Arrays.fill(w.hs, 0, dim, 0f)
        java.util.Arrays.fill(w.cs, 0, hidden, 0f)
        for (t in 0 until T) {
            val input = if (t == 0) 1 else words[t - 1]
            val target = if (t < words.size) words[t] else 2
            forwardStep(input, t, w)
            loss += outputStep(target, t, w, negatives, logExpected)
        }
        backward(words, T, w)
        w.tokens += T
        return loss
    }

    private fun forwardStep(input: Int, t: Int, w: Work) {
        val H = hidden
        val D = dim
        val g = w.g
        System.arraycopy(bias, 0, g, 0, 4 * H)
        val eb = input * D
        val hb = t * D // previous h
        val x = w.x
        System.arraycopy(emb, eb, x, 0, D)
        val hp = w.hp
        System.arraycopy(w.hs, hb, hp, 0, D)
        for (r in 0 until 4 * H) g[r] += dot(wih, r * D, x, D) + dot(whh, r * D, hp, D)
        val cb = (t + 1) * H
        val pcb = t * H
        for (j in 0 until H) {
            val i = sigmoid(g[j]); val f = sigmoid(g[H + j]); val u = tanh(g[2 * H + j]); val o = sigmoid(g[3 * H + j])
            val c = f * w.cs[pcb + j] + i * u
            val tcv = tanh(c)
            w.gi[cb + j] = i; w.gf[cb + j] = f; w.gu[cb + j] = u; w.go[cb + j] = o
            w.cs[cb + j] = c; w.tc[cb + j] = tcv
            w.m[j] = o * tcv
        }
        val ob = (t + 1) * D
        for (d in 0 until D) w.hs[ob + d] = dot(proj, d * H, w.m, H)
    }

    /** Loss and output gradients at step [t]; d loss/d h goes to w.dh[t]. */
    private fun outputStep(target: Int, t: Int, w: Work, negatives: IntArray?, logExpected: FloatArray?): Double {
        val D = dim
        val hb = (t + 1) * D
        val dhb = t * D
        java.util.Arrays.fill(w.dh, dhb, dhb + D, 0f)
        val cand: IntArray
        val n: Int
        if (negatives == null) {
            n = vocab
            cand = IntArray(0)
        } else {
            n = negatives.size + 1
            cand = negatives
        }
        fun word(c: Int) = if (negatives == null) c else if (c == 0) target else cand[c - 1]
        val sc = w.scores
        var max = Float.NEGATIVE_INFINITY
        for (c in 0 until n) {
            val k = word(c)
            if (negatives != null && c > 0 && k == target) { sc[c] = Float.NEGATIVE_INFINITY; continue }
            var s = outBias[k]
            val eb = k * D
            for (d in 0 until D) s += emb[eb + d] * w.hs[hb + d]
            if (logExpected != null) s -= if (c == 0) logExpected[target] else logExpected[k]
            sc[c] = s
            if (s > max) max = s
        }
        var sum = 0.0
        for (c in 0 until n) sum += exp((sc[c] - max).toDouble())
        val logZ = max + ln(sum)
        val targetIndex = if (negatives == null) target else 0
        val loss = logZ - sc[targetIndex]
        for (c in 0 until n) {
            if (sc[c] == Float.NEGATIVE_INFINITY) continue
            val p = exp(sc[c] - logZ).toFloat()
            val gk = p - if (c == targetIndex) 1f else 0f
            if (gk == 0f) continue
            val k = word(c)
            w.touch(k)
            w.dOutBias[k] += gk
            val eb = k * D
            for (d in 0 until D) {
                w.dh[dhb + d] += gk * emb[eb + d]
                w.dEmb[eb + d] += gk * w.hs[hb + d]
            }
        }
        return loss
    }

    private fun backward(words: IntArray, T: Int, w: Work) {
        val H = hidden
        val D = dim
        java.util.Arrays.fill(w.dhNext, 0f)
        java.util.Arrays.fill(w.dcNext, 0f)
        for (t in T - 1 downTo 0) {
            val input = if (t == 0) 1 else words[t - 1]
            val cb = (t + 1) * H
            val pcb = t * H
            // dh at this step = from the output + from the next step.
            val dh = w.dhStep
            for (d in 0 until D) dh[d] = w.dh[t * D + d] + w.dhNext[d]
            // h = P·m
            for (j in 0 until H) w.m[j] = w.go[cb + j] * w.tc[cb + j]
            java.util.Arrays.fill(w.dm, 0f)
            for (d in 0 until D) {
                val g = dh[d]
                if (g == 0f) continue
                val pb = d * H
                axpy(g, w.m, 0, w.dProj, pb, H)
                axpy(g, proj, pb, w.dm, 0, H)
            }
            for (j in 0 until H) {
                val i = w.gi[cb + j]; val f = w.gf[cb + j]; val u = w.gu[cb + j]; val o = w.go[cb + j]; val tcv = w.tc[cb + j]
                val dmj = w.dm[j]
                val dO = dmj * tcv
                val dc = dmj * o * (1 - tcv * tcv) + w.dcNext[j]
                val dI = dc * u
                val dF = dc * w.cs[pcb + j]
                val dU = dc * i
                w.dcNext[j] = dc * f
                w.dg[j] = dI * i * (1 - i)
                w.dg[H + j] = dF * f * (1 - f)
                w.dg[2 * H + j] = dU * (1 - u * u)
                w.dg[3 * H + j] = dO * o * (1 - o)
            }
            // g = Wih·E[input] + Whh·h(t-1) + b
            val eb = input * D
            val hb = t * D
            java.util.Arrays.fill(w.dhNext, 0f)
            w.touch(input)
            System.arraycopy(emb, eb, w.x, 0, D)
            System.arraycopy(w.hs, hb, w.hp, 0, D)
            java.util.Arrays.fill(w.de, 0f)
            for (r in 0 until 4 * H) {
                val g = w.dg[r]
                w.dBias[r] += g
                if (g == 0f) continue
                val rb = r * D
                // Simple loops over one array each: the JIT turns these into vector instructions.
                axpy(g, w.x, 0, w.dWih, rb, D)
                axpy(g, w.hp, 0, w.dWhh, rb, D)
                axpy(g, wih, rb, w.de, 0, D)
                axpy(g, whh, rb, w.dhNext, 0, D)
            }
            axpy(1f, w.de, 0, w.dEmb, eb, D)
        }
    }

    /** Applies and clears [w]'s gradients (averaged per token, clipped) with Adagrad. */
    fun update(w: Work, lr: Float) {
        if (w.tokens == 0) return
        val scale = 1f / w.tokens
        adagrad(wih, aWih, w.dWih, lr, scale)
        adagrad(whh, aWhh, w.dWhh, lr, scale)
        adagrad(bias, aBias, w.dBias, lr, scale)
        adagrad(proj, aProj, w.dProj, lr, scale)
        val D = dim
        for (idx in 0 until w.touchedList.size) {
            val k = w.touchedList[idx]
            w.touched[k] = false
            val g0 = w.dOutBias[k] * scale
            aOutBias[k] += g0 * g0
            outBias[k] -= lr * g0 / (sqrt(aOutBias[k]) + EPS)
            w.dOutBias[k] = 0f
            val eb = k * D
            for (d in 0 until D) {
                val g = clip(w.dEmb[eb + d] * scale)
                aEmb[eb + d] += g * g
                emb[eb + d] -= lr * g / (sqrt(aEmb[eb + d]) + EPS)
                w.dEmb[eb + d] = 0f
            }
        }
        w.touchedList.clear()
        w.tokens = 0
    }

    private fun adagrad(p: FloatArray, a: FloatArray, g: FloatArray, lr: Float, scale: Float) {
        for (i in p.indices) {
            val gi = clip(g[i] * scale)
            if (gi != 0f) {
                a[i] += gi * gi
                p[i] -= lr * gi / (sqrt(a[i]) + EPS)
            }
            g[i] = 0f
        }
    }

    /**
     * Exact log-probabilities of the next word after [words] (from the sentence start), for
     * measuring; [out] receives them. Returns the h vector in [w].
     */
    fun predict(words: IntArray, upTo: Int, w: Work, out: FloatArray) {
        java.util.Arrays.fill(w.hs, 0, dim, 0f)
        java.util.Arrays.fill(w.cs, 0, hidden, 0f)
        for (t in 0..upTo) forwardStep(if (t == 0) 1 else words[t - 1], t, w)
        val hb = (upTo + 1) * dim
        var max = Float.NEGATIVE_INFINITY
        for (k in 0 until vocab) {
            var s = outBias[k]
            val eb = k * dim
            for (d in 0 until dim) s += emb[eb + d] * w.hs[hb + d]
            out[k] = s
            if (s > max) max = s
        }
        var sum = 0.0
        for (k in 0 until vocab) sum += exp((out[k] - max).toDouble())
        val logZ = (max + ln(sum)).toFloat()
        for (k in 0 until vocab) out[k] -= logZ
    }

    /** Runs all steps of a sentence once (the states stay in [w]); then [scoresAt] gives each step's log-probabilities. */
    fun forwardAll(words: IntArray, w: Work) {
        java.util.Arrays.fill(w.hs, 0, dim, 0f)
        java.util.Arrays.fill(w.cs, 0, hidden, 0f)
        for (t in 0..words.size) forwardStep(if (t == 0) 1 else words[t - 1], t, w)
    }

    fun scoresAt(t: Int, w: Work, out: FloatArray) {
        val hb = (t + 1) * dim
        var max = Float.NEGATIVE_INFINITY
        for (k in 0 until vocab) {
            var s = outBias[k]
            val eb = k * dim
            for (d in 0 until dim) s += emb[eb + d] * w.hs[hb + d]
            out[k] = s
            if (s > max) max = s
        }
        var sum = 0.0
        for (k in 0 until vocab) sum += exp((out[k] - max).toDouble())
        val logZ = (max + ln(sum)).toFloat()
        for (k in 0 until vocab) out[k] -= logZ
    }

    // ---------------------------------------------------------------- files

    fun save(file: File) = DataOutputStream(file.outputStream().buffered(1 shl 20)).use { o ->
        o.writeInt(vocab); o.writeInt(dim); o.writeInt(hidden)
        for (a in listOf(emb, outBias, wih, whh, bias, proj, aEmb, aOutBias, aWih, aWhh, aBias, aProj)) {
            val bb = ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            bb.asFloatBuffer().put(a)
            o.write(bb.array())
        }
    }

    fun load(file: File) = DataInputStream(file.inputStream().buffered(1 shl 20)).use { i ->
        require(i.readInt() == vocab && i.readInt() == dim && i.readInt() == hidden) { "checkpoint has another shape" }
        for (a in listOf(emb, outBias, wih, whh, bias, proj, aEmb, aOutBias, aWih, aWhh, aBias, aProj)) {
            val bytes = ByteArray(a.size * 4)
            i.readFully(bytes)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(a)
        }
    }

    /** Writes the app's `SWKBNWP1` int8 format ([words] = the vocabulary from token 3 on). */
    fun export(file: File, words: List<String>) {
        require(words.size == vocab - 3)
        val out = java.io.ByteArrayOutputStream()
        val le = { n: Int -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array() }
        val lef = { f: Float -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(f).array() }
        // Padding as the reader expects: each block (word list, int8 rows) to a multiple of 16 of its own length.
        fun pad(n: Int) { repeat((n + 15) / 16 * 16 - n) { out.write(0) } }
        val wordBytes = words.joinToString("\n").toByteArray(Charsets.UTF_8)
        out.write("SWKBNWP1".toByteArray(Charsets.US_ASCII))
        listOf(1, 1, vocab, dim, hidden, wordBytes.size).forEach { out.write(le(it)) }
        out.write(lef(0.7f)); out.write(lef(-0.5f))
        out.write(wordBytes); pad(wordBytes.size)
        fun matrix(a: FloatArray, rows: Int, cols: Int) {
            val scales = FloatArray(rows)
            for (r in 0 until rows) {
                var m = 0f
                for (c in 0 until cols) m = max(m, abs(a[r * cols + c]))
                val s = if (m > 0f) m / 127f else 1f
                scales[r] = s
                for (c in 0 until cols) out.write((a[r * cols + c] / s).roundToInt().coerceIn(-127, 127))
            }
            pad(rows * cols)
            scales.forEach { out.write(lef(it)) }
        }
        fun floats(a: FloatArray) = a.forEach { out.write(lef(it)) }
        matrix(emb, vocab, dim)
        floats(outBias)
        matrix(wih, 4 * hidden, dim)
        matrix(whh, 4 * hidden, dim)
        floats(bias)
        matrix(proj, dim, hidden)
        val body = out.toByteArray()
        var h = -0x340d631b7bdddcdbL
        for (b in body) h = (h xor (b.toLong() and 0xFF)) * 0x100000001b3L
        file.writeBytes(body + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(h).array())
    }

    companion object {
        private const val EPS = 1e-8f
        private fun sigmoid(x: Float) = 1f / (1f + exp(-x))
        /** y[yo..] += a · x[xo..] for n numbers. */
        private fun axpy(a: Float, x: FloatArray, xo: Int, y: FloatArray, yo: Int, n: Int) {
            for (i in 0 until n) y[yo + i] += a * x[xo + i]
        }

        /** Dot product of a[ao..] and b[0..] over n numbers, with four running sums. */
        private fun dot(a: FloatArray, ao: Int, b: FloatArray, n: Int): Float {
            var s0 = 0f
            var s1 = 0f
            var s2 = 0f
            var s3 = 0f
            var i = 0
            val n4 = n - n % 4
            while (i < n4) {
                s0 += a[ao + i] * b[i]
                s1 += a[ao + i + 1] * b[i + 1]
                s2 += a[ao + i + 2] * b[i + 2]
                s3 += a[ao + i + 3] * b[i + 3]
                i += 4
            }
            while (i < n) { s0 += a[ao + i] * b[i]; i++ }
            return s0 + s1 + s2 + s3
        }

        private fun clip(g: Float) = if (g > 5f) 5f else if (g < -5f) -5f else g
    }
}

/** A growable list of ints without boxing. */
class IntArrayList {
    private var a = IntArray(256)
    var size = 0
        private set
    fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
    operator fun get(i: Int) = a[i]
    fun clear() { size = 0 }
}
