package com.yeamin.jolpai.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * A short, calm animation where a key is pressed (dynamic themes). Designed to feel like good
 * physical feedback rather than fireworks: quick (a third to two thirds of a second), soft,
 * and kept on or right around the pressed key, so it never covers the next key you aim for.
 */
enum class PressEffect(val label: String, val durationMs: Long) {
    /** A soft wash of colour spreads from your fingertip across the key, then fades. */
    INK("Ink", 380),
    /** The key lights up from within, like a backlit keyboard key, and gently dims. */
    BACKLIGHT("Backlight", 420),
    /** The letter you typed floats up out of the key and fades. */
    FLOAT("Letter float", 520),
    /** A thin outline lifts off the key's edge and fades. */
    HALO("Halo", 360),
    /** A soft light trail joins each key to the one before it, so typing flows. */
    TRAIL("Light trail", 460),
    /** One or two small glowing motes rise gently from the key. */
    MOTES("Motes", 640),
    /** The key gives a small springy bounce of light, like jelly. */
    POP("Pop", 340),
    /** A glint of light sweeps across the key, like light on glass. */
    SHEEN("Sheen", 420),
    /** Three little stars twinkle at the key's edges. */
    SPARKLE("Sparkle", 560),
    /** Two rings spread from your fingertip inside the key, like a drop on water. */
    DROPLET("Droplet", 520),
    /** A small heart floats up from the key. */
    HEARTS("Hearts", 600),
    /** A glowing line grows under the key from its middle, then fades. */
    UNDERLINE("Glow line", 380),
    /**
     * Soft rubber keys: the key itself squashes when pressed and wobbles back, bouncy, when let
     * go ([RubberSpring], drawn by the keys themselves); this layer only adds a faint glossy shine.
     */
    RUBBER("Rubber", 360),
    /** Bouncy keys, soft and calm: a gentle squash and one soft bounce back. */
    SQUISH("Squish", 360),
    /** Bouncy keys, extra wobbly: a deep squash and a slow jelly wobble. */
    JELLY("Jelly", 360),
}

/** What one effect shape is; how [EffectSink.shape]'s numbers are read depends on it. */
enum class EffectShape {
    /** Soft round light: centre (x, y), radius a. */
    GLOW,
    /** Circle filling only the pressed key's shape: centre (x, y), radius a. */
    INK,
    /** Solid key-shaped fill: centre (x, y), size a × b, corner radius extra. */
    KEY_FILL,
    /** Key-shaped outline: centre (x, y), size a × b, line width extra (corners follow the key's, grown with it). */
    KEY_OUTLINE,
    /** The typed letter: centre (x, y), text size a. */
    LETTER,
    /** A soft line with round ends: from (x, y) to (a, b), width extra. */
    LINE,
    /** Small solid dot: centre (x, y), radius a. */
    DOT,
    /** Ring inside the pressed key's shape only: centre (x, y), radius a, line width extra. */
    KEY_RING,
    /** A slanted band of light across the pressed key, clipped to it: band centre x, width a. */
    SHEEN,
    /** A four-pointed twinkle: centre (x, y), point length a. */
    STAR,
    /** A small heart: centre (x, y), size a. */
    HEART,
}

/** Receives the shapes of one frame; [alpha] (0–1) scales the effect colour. */
fun interface EffectSink {
    fun shape(kind: EffectShape, x: Float, y: Float, a: Float, b: Float, alpha: Float, extra: Float)
}

/**
 * One key press, as the effects need it: the key's drawn bounds (centre and size), its corner
 * radius, where the finger touched, and where the previous press was ([fromX] is NaN when
 * there was none close in time). Made once per press.
 */
data class KeyPress(
    val cx: Float,
    val cy: Float,
    val keyW: Float,
    val keyH: Float,
    val corner: Float,
    val touchX: Float,
    val touchY: Float,
    val seed: Int,
    val fromX: Float = Float.NaN,
    val fromY: Float = Float.NaN,
) {
    val hasFrom: Boolean get() = !fromX.isNaN() && !fromY.isNaN()
}

/**
 * The maths of key press effects (plain Kotlin, unit-tested on the PC). Sizes come from the
 * pressed key, so effects look the same on any screen; shapes go straight to an [EffectSink],
 * so drawing a frame allocates nothing.
 */
object PressEffects {
    /** The most shapes any effect draws in one frame. */
    const val MAX_SHAPES = 6

    /** A trail joins presses at most this many key widths apart (further is a jump, not a flow). */
    const val TRAIL_MAX_KEYS = 6f

    /**
     * Draws [effect] at [t] (0 = the moment of the press, 1 = finished) for [press].
     * [hasLetter] is false for keys without a single typed character (Space, Shift…), which
     * get a quiet light instead of a floating letter.
     */
    fun frame(effect: PressEffect, t: Float, press: KeyPress, hasLetter: Boolean, out: EffectSink) {
        if (t < 0f || t >= 1f) return
        val p = press
        when (effect) {
            PressEffect.INK -> {
                // Spreads over the first 60 % from the touch point to the key's far corner, then fades.
                val reach = farCorner(p)
                val spread = easeOut((t / 0.6f).coerceAtMost(1f))
                val fade = if (t < 0.55f) 1f else (1 - t) / 0.45f
                out.shape(EffectShape.INK, p.touchX, p.touchY, reach * (0.15f + 0.85f * spread), 0f, 0.34f * fade, 0f)
            }
            PressEffect.BACKLIGHT -> {
                val fade = (1 - t).pow(2)
                val grow = 1f + 0.10f * easeOut(t)
                out.shape(EffectShape.GLOW, p.cx, p.cy, max(p.keyW, p.keyH) * 0.75f * grow, 0f, 0.45f * fade, 0f)
                out.shape(EffectShape.KEY_FILL, p.cx, p.cy, p.keyW, p.keyH, 0.28f * fade, p.corner)
            }
            PressEffect.FLOAT -> {
                if (hasLetter) {
                    val rise = easeOut(t)
                    out.shape(EffectShape.LETTER, p.cx, p.cy - p.keyH * 0.7f * rise, p.keyH * 0.42f * (1f + 0.2f * rise), 0f, 0.85f * (1 - t).pow(1.4f), 0f)
                } else {
                    out.shape(EffectShape.KEY_FILL, p.cx, p.cy, p.keyW, p.keyH, 0.22f * (1 - t).pow(2), p.corner)
                }
            }
            PressEffect.HALO -> {
                val lift = p.keyH * 0.16f * easeOut(t)
                out.shape(
                    EffectShape.KEY_OUTLINE, p.cx, p.cy, p.keyW + 2 * lift, p.keyH + 2 * lift,
                    0.65f * (1 - t).pow(1.5f), p.keyH * 0.045f * (1f - 0.5f * t),
                )
            }
            PressEffect.TRAIL -> {
                val fade = (1 - t).pow(1.6f)
                if (p.hasFrom && hypot(p.cx - p.fromX, p.cy - p.fromY) <= p.keyW * TRAIL_MAX_KEYS) {
                    out.shape(EffectShape.LINE, p.fromX, p.fromY, p.cx, p.cy, 0.30f * fade, p.keyH * 0.13f * (1f - 0.4f * t))
                }
                out.shape(EffectShape.GLOW, p.cx, p.cy, max(p.keyW, p.keyH) * 0.55f, 0f, 0.42f * (1 - t).pow(2), 0f)
            }
            PressEffect.MOTES -> {
                val count = if (rand(p.seed, 3) < 0.5f) 1 else 2
                for (i in 0 until count) {
                    val life = ((t - 0.12f * i) / (1 - 0.12f * i)).coerceAtMost(1f)
                    if (life <= 0f) continue
                    val x = p.cx + p.keyW * (0.45f * (rand(p.seed, i) - 0.5f) + 0.12f * sin(2 * PI * (life * 0.7 + rand(p.seed, i + 5))).toFloat())
                    val y = p.cy - p.keyH * (0.1f + 1.1f * easeOut(life))
                    val alpha = (life / 0.1f).coerceAtMost(1f) * (1 - life)
                    out.shape(EffectShape.GLOW, x, y, p.keyH * 0.26f, 0f, 0.55f * alpha, 0f)
                    out.shape(EffectShape.DOT, x, y, p.keyH * 0.045f, 0f, 0.9f * alpha, 0f)
                }
            }
            PressEffect.POP -> {
                // Starts a touch small, springs a little past full size, settles.
                val s = 1f + 0.07f * sin(PI * 2.2 * t).toFloat() * (1 - t).pow(1.2f) - 0.06f * (1 - t).pow(6)
                out.shape(EffectShape.KEY_FILL, p.cx, p.cy, p.keyW * s, p.keyH * s, 0.30f * (1 - t).pow(1.5f), p.corner * s)
                out.shape(EffectShape.KEY_OUTLINE, p.cx, p.cy, p.keyW * s, p.keyH * s, 0.5f * (1 - t).pow(2), p.keyH * 0.035f)
            }
            PressEffect.SHEEN -> {
                out.shape(EffectShape.KEY_FILL, p.cx, p.cy, p.keyW, p.keyH, 0.25f * (1 - t).pow(2), p.corner)
                val x = p.cx - p.keyW * 0.75f + p.keyW * 1.5f * easeInOut(t)
                val alpha = 0.55f * (t / 0.1f).coerceAtMost(1f) * (1 - t)
                out.shape(EffectShape.SHEEN, x, p.cy, p.keyW * 0.3f, 0f, alpha, 0f)
            }
            PressEffect.SPARKLE -> {
                for (i in 0 until 3) {
                    val start = 0.14f * i
                    val life = (t - start) / (1 - start)
                    if (life <= 0f) continue
                    // Each star sits near a different edge of the key.
                    val sideX = if ((p.seed + i) % 2 == 0) -1f else 1f
                    val x = p.cx + sideX * p.keyW * (0.35f + 0.2f * rand(p.seed, i))
                    val y = p.cy + p.keyH * (0.45f * (rand(p.seed, i + 11) * 2 - 1))
                    val size = p.keyH * 0.13f * (0.3f + 0.7f * sin(PI * life).toFloat())
                    val alpha = (life / 0.08f).coerceAtMost(1f) * (1 - life)
                    out.shape(EffectShape.STAR, x, y, size, 0f, alpha, 0f)
                }
            }
            PressEffect.DROPLET -> {
                val reach = farCorner(p)
                for (i in 0 until 2) {
                    val start = 0.2f * i
                    val life = (t - start) / (1 - start)
                    if (life <= 0f) continue
                    out.shape(
                        EffectShape.KEY_RING, p.touchX, p.touchY, reach * (0.1f + 0.85f * easeOut(life)), 0f,
                        0.55f * (1 - life).pow(1.5f), p.keyH * 0.032f * (1f - 0.4f * life),
                    )
                }
            }
            PressEffect.HEARTS -> {
                val rise = easeOut(t)
                val x = p.cx + p.keyW * 0.12f * sin(2 * PI * (t * 0.8 + rand(p.seed, 2))).toFloat()
                val y = p.cy - p.keyH * (0.2f + 0.85f * rise)
                val size = p.keyH * 0.2f * (0.75f + 0.3f * (t / 0.2f).coerceAtMost(1f))
                out.shape(EffectShape.HEART, x, y, size, 0f, (t / 0.06f).coerceAtMost(1f) * (1 - t).pow(1.2f), 0f)
            }
            PressEffect.RUBBER, PressEffect.SQUISH, PressEffect.JELLY -> {
                // A soft gloss, like light on a rubber dome; the bounce itself is the key's own.
                out.shape(EffectShape.GLOW, p.cx, p.cy - p.keyH * 0.18f, maxOf(p.keyW, p.keyH) * 0.45f, 0f, 0.26f * (1 - t).pow(2), 0f)
            }
            PressEffect.UNDERLINE -> {
                val y = p.cy + p.keyH * 0.42f
                val half = p.keyW * 0.38f * easeOut((t / 0.5f).coerceAtMost(1f))
                val fade = if (t < 0.5f) 1f else (1 - t) / 0.5f
                out.shape(EffectShape.GLOW, p.cx, y, p.keyW * 0.5f, 0f, 0.3f * (1 - t).pow(2), 0f)
                out.shape(EffectShape.LINE, p.cx - half, y, p.cx + half, y, 0.85f * fade, p.keyH * 0.05f)
            }
        }
    }

    /** Distance from the touch to the key's farthest corner. */
    private fun farCorner(p: KeyPress): Float {
        val dx = max(abs(p.touchX - (p.cx - p.keyW / 2)), abs(p.touchX - (p.cx + p.keyW / 2)))
        val dy = max(abs(p.touchY - (p.cy - p.keyH / 2)), abs(p.touchY - (p.cy + p.keyH / 2)))
        return hypot(dx, dy)
    }

    private fun easeInOut(t: Float): Float = t * t * (3 - 2 * t)

    /** Fast at first, then slowing down. */
    private fun easeOut(t: Float): Float = 1 - (1 - t).pow(3)

    /** A repeatable 0–1 number from [seed] and [i]. */
    internal fun rand(seed: Int, i: Int): Float {
        var h = seed * 0x27D4EB2D + i * 0x165667B1
        h = h xor (h ushr 15)
        h *= 0x2C1B3C6D
        h = h xor (h ushr 12)
        return (h ushr 8) / 16_777_216f
    }
}
