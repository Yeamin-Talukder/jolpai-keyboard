package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PressEffectsTest {
    private data class Shape(val kind: EffectShape, val x: Float, val y: Float, val a: Float, val b: Float, val alpha: Float, val extra: Float)

    private val press = KeyPress(cx = CX, cy = CY, keyW = KEY_W, keyH = KEY_H, corner = 18f, touchX = CX + 20f, touchY = CY + 30f, seed = 7)

    private fun frame(effect: PressEffect, t: Float, p: KeyPress = press, letter: Boolean = true): List<Shape> {
        val out = ArrayList<Shape>()
        PressEffects.frame(effect, t, p, letter) { k, x, y, a, b, al, e -> out += Shape(k, x, y, a, b, al, e) }
        return out
    }

    @Test fun `every effect draws sane shapes from start to finish`() {
        PressEffect.entries.forEach { e ->
            for (letter in listOf(true, false)) for (i in 0 until 100) {
                val shapes = frame(e, i / 100f, letter = letter)
                assertTrue("$e at $i: ${shapes.size} shapes", shapes.size <= PressEffects.MAX_SHAPES)
                shapes.forEach { s ->
                    assertTrue("$e alpha ${s.alpha}", s.alpha in 0f..1f)
                    assertTrue("$e size", s.a >= 0f && s.extra >= 0f)
                }
            }
        }
    }

    @Test fun `effects stay on or right around the pressed key`() {
        // The complaint about the first set was effects flying across the keyboard. Now nothing
        // reaches past about one key around the pressed one (the trail's line excepted: it joins
        // the previous key on purpose).
        PressEffect.entries.forEach { e ->
            for (i in 0 until 100) frame(e, i / 100f).filter { it.kind != EffectShape.LINE }.forEach { s ->
                assertTrue("$e x", abs(s.x - CX) <= KEY_W * 0.8f)
                assertTrue("$e y", abs(s.y - CY) <= KEY_H * 1.3f)
                when (s.kind) {
                    EffectShape.GLOW, EffectShape.INK, EffectShape.KEY_RING -> assertTrue("$e radius ${s.a}", s.a <= maxOf(KEY_W, KEY_H) * 0.9f)
                    EffectShape.STAR, EffectShape.HEART -> assertTrue("$e size ${s.a}", s.a <= KEY_H * 0.3f)
                    EffectShape.SHEEN -> assertTrue("$e band ${s.a}", s.a <= KEY_W * 0.4f)
                    EffectShape.KEY_FILL, EffectShape.KEY_OUTLINE -> {
                        assertTrue("$e width", s.a <= KEY_W + KEY_H * 0.35f)
                        assertTrue("$e height", s.b <= KEY_H * 1.35f)
                    }
                    else -> {}
                }
            }
        }
    }

    @Test fun `effects show at once, fade out, and draw nothing once finished`() {
        PressEffect.entries.forEach { e ->
            assertTrue("$e starts visible", frame(e, 0.04f).any { it.alpha > 0.2f })
            assertTrue("$e is faint at the end", frame(e, 0.995f).all { it.alpha < 0.05f })
            assertTrue(frame(e, 1f).isEmpty())
            assertTrue(frame(e, -0.1f).isEmpty())
        }
    }

    @Test fun `effects are short, so fast typing never piles them up`() {
        PressEffect.entries.forEach { assertTrue("$it", it.durationMs in 300..700) }
    }

    @Test fun `ink spreads from the fingertip and fills the key`() {
        val early = frame(PressEffect.INK, 0.05f).single()
        val full = frame(PressEffect.INK, 0.6f).single()
        assertEquals(EffectShape.INK, early.kind)
        assertEquals(press.touchX, early.x)
        assertEquals(press.touchY, early.y)
        assertTrue(full.a > early.a)
        // By then it reaches the key's farthest corner from the touch.
        val farCorner = hypot(press.touchX - (CX - KEY_W / 2), press.touchY - (CY - KEY_H / 2))
        assertTrue(full.a >= farCorner * 0.99f)
    }

    @Test fun `the typed letter floats up, and keys without a letter just light`() {
        val early = frame(PressEffect.FLOAT, 0.1f).single()
        val late = frame(PressEffect.FLOAT, 0.7f).single()
        assertEquals(EffectShape.LETTER, early.kind)
        assertTrue(late.y < early.y)
        assertTrue(frame(PressEffect.FLOAT, 0.3f, letter = false).all { it.kind == EffectShape.KEY_FILL })
    }

    @Test fun `the halo lifts off the key's edge`() {
        val early = frame(PressEffect.HALO, 0.05f).single()
        val late = frame(PressEffect.HALO, 0.8f).single()
        assertEquals(EffectShape.KEY_OUTLINE, early.kind)
        assertTrue(early.a >= KEY_W && late.a > early.a)
    }

    @Test fun `the light trail joins nearby presses only`() {
        val near = press.copy(fromX = CX - KEY_W * 2, fromY = CY)
        val far = press.copy(fromX = CX - KEY_W * 9, fromY = CY)
        assertTrue(frame(PressEffect.TRAIL, 0.2f, near).any { it.kind == EffectShape.LINE && it.x == near.fromX && it.a == CX })
        assertTrue(frame(PressEffect.TRAIL, 0.2f, far).none { it.kind == EffectShape.LINE })
        assertTrue(frame(PressEffect.TRAIL, 0.2f).none { it.kind == EffectShape.LINE })
    }

    @Test fun `motes rise gently, one or two per press, the same for the same press`() {
        val early = frame(PressEffect.MOTES, 0.25f).filter { it.kind == EffectShape.DOT }
        val late = frame(PressEffect.MOTES, 0.8f).filter { it.kind == EffectShape.DOT }
        assertTrue(late.size in 1..2)
        assertTrue(late.map { it.y }.average() < early.map { it.y }.average())
        assertEquals(frame(PressEffect.MOTES, 0.4f), frame(PressEffect.MOTES, 0.4f))
        val counts = (1..40).map { s -> frame(PressEffect.MOTES, 0.5f, press.copy(seed = s)).count { it.kind == EffectShape.DOT } }.toSet()
        assertEquals(setOf(1, 2), counts)
        assertNotEquals(frame(PressEffect.MOTES, 0.4f, press.copy(seed = 3)), frame(PressEffect.MOTES, 0.4f, press.copy(seed = 4)))
        for (i in 0 until 1000) assertTrue(PressEffects.rand(i, i * 3) in 0f..1f)
    }

    @Test fun `pop springs past full size and settles`() {
        fun size(t: Float) = frame(PressEffect.POP, t).first { it.kind == EffectShape.KEY_FILL }.a
        assertTrue("starts a little small", size(0.01f) < KEY_W)
        assertTrue("overshoots", (1..60).any { size(it / 100f) > KEY_W * 1.02f })
        assertEquals(KEY_W, size(0.98f), KEY_W * 0.01f)
    }

    @Test fun `the sheen sweeps left to right across the key`() {
        val xs = (5..95 step 10).map { t -> frame(PressEffect.SHEEN, t / 100f).first { it.kind == EffectShape.SHEEN }.x }
        assertEquals(xs.sorted(), xs)
        assertTrue(xs.first() < CX - KEY_W * 0.3f && xs.last() > CX + KEY_W * 0.3f)
    }

    @Test fun `sparkles twinkle at the key's sides, a few at a time`() {
        val stars = frame(PressEffect.SPARKLE, 0.5f)
        assertEquals(3, stars.size)
        stars.forEach {
            assertEquals(EffectShape.STAR, it.kind)
            assertTrue("near a side, not the middle", abs(it.x - CX) >= KEY_W * 0.3f)
        }
    }

    @Test fun `droplet rings start at the fingertip and spread`() {
        val early = frame(PressEffect.DROPLET, 0.1f).first()
        val late = frame(PressEffect.DROPLET, 0.7f).first()
        assertEquals(EffectShape.KEY_RING, early.kind)
        assertEquals(press.touchX, early.x)
        assertTrue(late.a > early.a)
        assertEquals(2, frame(PressEffect.DROPLET, 0.5f).size)
    }

    @Test fun `a heart floats up, and the glow line grows from the middle`() {
        val h1 = frame(PressEffect.HEARTS, 0.1f).single()
        val h2 = frame(PressEffect.HEARTS, 0.7f).single()
        assertEquals(EffectShape.HEART, h1.kind)
        assertTrue(h2.y < h1.y && h2.y < CY)
        val l1 = frame(PressEffect.UNDERLINE, 0.1f).first { it.kind == EffectShape.LINE }
        val l2 = frame(PressEffect.UNDERLINE, 0.45f).first { it.kind == EffectShape.LINE }
        assertTrue(l2.a - l2.x > l1.a - l1.x)
        assertEquals(CX, (l1.x + l1.a) / 2, 0.01f)
        assertTrue("under the key", l1.y > CY)
    }

    @Test fun `press effects survive export and import`() {
        val t = BuiltInThemes.STARDUST.copy(id = "custom:1")
        val back = ThemeCodec.decode(ThemeCodec.encode(t), "custom:1")
        assertEquals(PressEffect.TRAIL, back.pressEffect)
        assertEquals(t.effectColor, back.effectColor)
        // An effect this version doesn't know (from a newer one, or the first effects set) is dropped, not an error.
        val unknown = ThemeCodec.encode(t).replace("\"TRAIL\"", "\"SHOCKWAVE\"")
        assertEquals(null, ThemeCodec.decode(unknown, "custom:2").pressEffect)
    }

    private companion object {
        const val CX = 500f
        const val CY = 300f
        const val KEY_W = 96f
        const val KEY_H = 140f
    }
}
