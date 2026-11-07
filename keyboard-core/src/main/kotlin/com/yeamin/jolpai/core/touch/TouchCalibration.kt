package com.yeamin.jolpai.core.touch

import kotlin.math.abs

/**
 * Where this user actually touches each letter key, as an offset from the key's centre in key
 * sizes (e.g. 0.1 = a tenth of a key to the right). Learned slowly from ordinary taps, and
 * faster from corrections (typed a letter, deleted it, typed its neighbour). Offsets are
 * limited to [LIMIT] so a key's region can only drift a little.
 *
 * Stores nothing typed: only one average offset per key. Not thread-safe (main thread only).
 */
class TouchCalibration {
    private val offsets = HashMap<Char, FloatArray>()

    fun offsetX(c: Char): Float = offsets[c]?.get(0) ?: 0f
    fun offsetY(c: Char): Float = offsets[c]?.get(1) ?: 0f

    val isEmpty: Boolean get() = offsets.isEmpty()

    /** Moves [c]'s offset towards a touch at ([dx], [dy]) key sizes from its centre, by [rate] (0–1). */
    fun learn(c: Char, dx: Float, dy: Float, rate: Float) {
        if (abs(dx) > 1f || abs(dy) > 1f) return // far off: not a real attempt at this key
        val o = offsets.getOrPut(c) { FloatArray(2) }
        o[0] = (o[0] + (dx - o[0]) * rate).coerceIn(-LIMIT, LIMIT)
        o[1] = (o[1] + (dy - o[1]) * rate).coerceIn(-LIMIT, LIMIT)
    }

    fun clear() = offsets.clear()

    /** "c:dx:dy;…" for storage. */
    fun encode(): String = offsets.entries.joinToString(";") { (c, o) -> "${c.code}:${o[0]}:${o[1]}" }

    fun decode(text: String) {
        offsets.clear()
        text.split(';').forEach { part ->
            val f = part.split(':')
            if (f.size != 3) return@forEach
            val code = f[0].toIntOrNull() ?: return@forEach
            val x = f[1].toFloatOrNull() ?: return@forEach
            val y = f[2].toFloatOrNull() ?: return@forEach
            offsets[code.toChar()] = floatArrayOf(x.coerceIn(-LIMIT, LIMIT), y.coerceIn(-LIMIT, LIMIT))
        }
    }

    companion object {
        const val LIMIT = 0.3f

        /** Learning rate for an ordinary tap that was kept. */
        const val TAP_RATE = 0.03f

        /** Learning rate for a correction (deleted a letter and typed its neighbour). */
        const val CORRECTION_RATE = 0.15f
    }
}
