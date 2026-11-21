package com.yeamin.jolpai.theme

import kotlin.math.abs

/**
 * Bouncy keys ([PressEffect.RUBBER], [PressEffect.SQUISH], [PressEffect.JELLY]): each key is a
 * little damped spring. Pressing squashes it (wider and lower, pressed in from the top) with a
 * quick, firm spring; letting go releases a looser spring, so the key springs back past its rest
 * shape (a bit taller and narrower) and wobbles before it settles. The three effects are the same
 * spring with different personalities ([Springiness]).
 *
 * Plain Kotlin, unit-tested on the PC. [step] works in place on the caller's arrays, so the
 * keyboard can run it for every key, every frame, without garbage.
 */
object RubberSpring {
    /**
     * One kind of bounce: how far a fully pressed key squashes ([squashX] wider, [squashY]
     * lower) and the press (down) and release (up) springs' stiffness and damping.
     */
    data class Springiness(
        val squashX: Float,
        val squashY: Float,
        val downStiffness: Float,
        val downDamping: Float,
        val upStiffness: Float,
        val upDamping: Float,
    )

    /** Rubber: firm press (damping ratio ≈ 0.55), bouncy release (≈ 0.29, ~3 Hz, swings under 40 %). */
    val RUBBER = Springiness(0.07f, 0.14f, 1400f, 41f, 420f, 12f)

    /** Squish: soft and calm, a smaller squash and one gentle bounce back (release ≈ 0.46). */
    val SQUISH = Springiness(0.05f, 0.10f, 1200f, 45f, 380f, 18f)

    /** Jelly: a deep squash and a slower, wobblier release (≈ 0.25, ~2.5 Hz). */
    val JELLY = Springiness(0.09f, 0.18f, 1100f, 30f, 260f, 8f)

    /** The springiness of a bouncy-key effect, or null for effects that don't move the keys. */
    fun forEffect(effect: PressEffect?): Springiness? = when (effect) {
        PressEffect.RUBBER -> RUBBER
        PressEffect.SQUISH -> SQUISH
        PressEffect.JELLY -> JELLY
        else -> null
    }

    /** Squash never goes beyond these, whatever the timing. */
    const val MIN = -0.8f
    const val MAX = 1.2f

    private const val SUBSTEP_MS = 4f

    /**
     * Moves key [i]'s spring on by [dtMs] towards [target] (1 = held down, 0 = released).
     * [pos] is the squash (0 at rest, 1 fully squashed, negative = stretched), [vel] its speed
     * per second.
     */
    fun step(pos: FloatArray, vel: FloatArray, i: Int, target: Float, dtMs: Float, sp: Springiness = RUBBER) {
        val down = target > 0.5f
        val k = if (down) sp.downStiffness else sp.upStiffness
        val c = if (down) sp.downDamping else sp.upDamping
        var p = pos[i]
        var v = vel[i]
        var left = dtMs.coerceIn(0f, 100f)
        // Small fixed steps keep the spring stable however uneven the frames are.
        while (left > 0f) {
            val h = minOf(SUBSTEP_MS, left) / 1000f
            v += (-k * (p - target) - c * v) * h
            p += v * h
            left -= SUBSTEP_MS
        }
        if (p < MIN) { p = MIN; v = 0f }
        if (p > MAX) { p = MAX; v = 0f }
        pos[i] = p
        vel[i] = v
    }

    /**
     * Whether the spring has come to rest at [target] (no more frames needed): within 0.01 of a
     * squash, a fraction of a pixel on a key.
     */
    fun settled(pos: Float, vel: Float, target: Float): Boolean = abs(pos - target) < 0.01f && abs(vel) < 0.1f

    /** Width scale for squash [s]. */
    fun scaleX(s: Float, sp: Springiness = RUBBER): Float = 1f + sp.squashX * s

    /** Height scale for squash [s] (the key is pressed in from the top; its base stays put). */
    fun scaleY(s: Float, sp: Springiness = RUBBER): Float = 1f - sp.squashY * s
}
