package com.yeamin.lipi.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.yeamin.lipi.core.settings.SoundPack
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Jolpai's own key sounds. Nothing is bundled: each sound is a few dozen milliseconds of audio
 * made in code (decaying tones and filtered noise), written once to the app's cache as a WAV
 * file and played through a [SoundPool] for low delay.
 *
 * Every pack has four sounds: letters, space (lower and fuller), backspace (a little lower) and
 * enter (lowest and longest), like the phone's own click set.
 */
class SoundPackPlayer(context: Context) {
    enum class Kind { STANDARD, SPACE, DELETE, ENTER }

    private val dir = File(context.cacheDir, "key-sounds").apply { mkdirs() }
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = HashMap<Pair<SoundPack, Kind>, Int>()
    private val ready = HashSet<Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
    }

    /** Makes and loads [pack]'s sounds (cheap; done once per pack). */
    fun prepare(pack: SoundPack) {
        if (pack == SoundPack.SYSTEM) return
        Kind.entries.forEach { kind ->
            ids.getOrPut(pack to kind) {
                val file = File(dir, "${pack.storageKey}-${kind.name.lowercase()}-v1.wav")
                if (!file.exists()) writeWav(file, synthesize(pack, kind))
                pool.load(file.path, 1)
            }
        }
    }

    /** Plays [kind] from [pack] at [volume] (0–1); quietly does nothing until it has loaded. */
    fun play(pack: SoundPack, kind: Kind, volume: Float) {
        prepare(pack)
        val id = ids[pack to kind] ?: return
        if (id !in ready) return
        val v = volume.coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() = pool.release()

    // ------------------------------------------------------------------ synthesis

    private fun synthesize(pack: SoundPack, kind: Kind): FloatArray {
        // Lower and longer for the bigger keys.
        val pitch = when (kind) {
            Kind.STANDARD -> 1f
            Kind.DELETE -> 0.88f
            Kind.SPACE -> 0.72f
            Kind.ENTER -> 0.62f
        }
        val length = when (kind) {
            Kind.STANDARD, Kind.DELETE -> 1f
            Kind.SPACE -> 1.3f
            Kind.ENTER -> 1.6f
        }
        val random = Random(pack.ordinal * 31 + kind.ordinal)
        return when (pack) {
            SoundPack.SYSTEM -> FloatArray(0)
            // A gentle, muffled tap: a low tone with a soft noise attack.
            SoundPack.SOFT -> render(0.045f * length) { t, i ->
                val tone = sin(2 * PI * 520 * pitch * t).toFloat() * env(t, 0.012f * length)
                val noise = lowNoise(random, i) * env(t, 0.004f)
                0.55f * tone + 0.25f * noise
            }
            // A sharp mechanical click with a short metallic ring.
            SoundPack.TYPEWRITER -> render(0.07f * length) { t, _ ->
                val click = (random.nextFloat() * 2 - 1) * env(t, 0.0025f)
                val ring = sin(2 * PI * 3100 * pitch * t).toFloat() * env(t, 0.018f) * 0.35f
                val body = sin(2 * PI * 180 * pitch * t).toFloat() * env(t, 0.03f * length) * 0.4f
                0.7f * click + ring + body
            }
            // A round "plop": a tone sliding upward quickly.
            SoundPack.BUBBLE -> render(0.08f * length) { t, _ ->
                val f = (380 + 1600 * t / (0.08f * length)) * pitch
                sin(2 * PI * f * t).toFloat() * env(t, 0.025f * length) * attack(t, 0.003f) * 0.8f
            }
            // A warm wooden knock: two damped tones and a tiny tick.
            SoundPack.WOOD -> render(0.06f * length) { t, _ ->
                val a = sin(2 * PI * 720 * pitch * t).toFloat() * env(t, 0.016f * length)
                val b = sin(2 * PI * 1790 * pitch * t).toFloat() * env(t, 0.007f)
                val tick = (random.nextFloat() * 2 - 1) * env(t, 0.0015f)
                0.6f * a + 0.25f * b + 0.2f * tick
            }
        }
    }

    private inline fun render(seconds: Float, sample: (t: Float, i: Int) -> Float): FloatArray {
        val n = (RATE * seconds).toInt()
        return FloatArray(n) { i -> sample(i / RATE.toFloat(), i).coerceIn(-1f, 1f) }
    }

    /** Exponential decay with time constant [tau] seconds. */
    private fun env(t: Float, tau: Float): Float = exp(-t / tau)

    /** A fast fade-in, so a sound never starts with a click of its own. */
    private fun attack(t: Float, seconds: Float): Float = (t / seconds).coerceAtMost(1f)

    private var lowState = 0f
    private fun lowNoise(random: Random, i: Int): Float {
        if (i == 0) lowState = 0f
        lowState += 0.25f * ((random.nextFloat() * 2 - 1) - lowState)
        return lowState
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort((it * 0.9f * Short.MAX_VALUE).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        FileOutputStream(file).use { out ->
            out.write(header.array())
            out.write(data.array())
        }
    }

    private companion object {
        const val RATE = 44_100
    }
}
