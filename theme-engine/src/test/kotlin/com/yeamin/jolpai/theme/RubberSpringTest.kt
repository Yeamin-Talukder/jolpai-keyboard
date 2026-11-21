package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RubberSpringTest {
    private val pos = FloatArray(1)
    private val vel = FloatArray(1)
    private val all = listOf(RubberSpring.RUBBER, RubberSpring.SQUISH, RubberSpring.JELLY)

    private fun reset() { pos[0] = 0f; vel[0] = 0f }

    /** Runs the spring at 60 fps for [ms] towards [target], returning the squash after each frame. */
    private fun run(target: Float, ms: Int, sp: RubberSpring.Springiness = RubberSpring.RUBBER, frameMs: Float = 16f): List<Float> {
        val out = ArrayList<Float>()
        var t = 0f
        while (t < ms) {
            RubberSpring.step(pos, vel, 0, target, frameMs, sp)
            out += pos[0]
            t += frameMs
        }
        return out
    }

    private fun settleMs(sp: RubberSpring.Springiness): Int {
        var frames = 0
        while (!RubberSpring.settled(pos[0], vel[0], 0f) && frames < 200) {
            RubberSpring.step(pos, vel, 0, 0f, 16f, sp)
            frames++
        }
        return frames * 16
    }

    private fun crossings(xs: List<Float>) = xs.zipWithNext().count { (a, b) -> (a > 0f) != (b > 0f) }

    @Test fun `pressing squashes the key quickly, in every style`() {
        all.forEach { sp ->
            reset()
            val down = run(1f, 120, sp)
            assertTrue("$sp well squashed within 120 ms: ${down.last()}", down.last() > 0.8f)
            assertTrue("$sp half way within ~50 ms", down[2] > 0.45f)
            assertTrue(RubberSpring.scaleX(1f, sp) > 1f && RubberSpring.scaleY(1f, sp) < 1f)
        }
    }

    @Test fun `rubber bounces past rest and wobbles a few times`() {
        reset(); run(1f, 300)
        val up = run(0f, 1200)
        assertTrue("overshoots: ${up.min()}", up.min() < -0.2f)
        assertTrue(RubberSpring.scaleY(up.min()) > 1f && RubberSpring.scaleX(up.min()) < 1f)
        assertTrue("wobbles: ${crossings(up)}", crossings(up) >= 3)
        // Each swing is smaller than the last.
        val peaks = up.windowed(3).filter { (a, b, c) -> abs(b) > abs(a) && abs(b) >= abs(c) }.map { abs(it[1]) }
        assertEquals(peaks.sortedDescending(), peaks)
    }

    @Test fun `squish gives one gentle bounce, jelly the deepest and wobbliest`() {
        reset(); run(1f, 300, RubberSpring.SQUISH)
        val squish = run(0f, 1200, RubberSpring.SQUISH)
        assertTrue("squish bounces a little: ${squish.min()}", squish.min() < -0.08f && squish.min() > -0.35f)
        reset(); run(1f, 300, RubberSpring.RUBBER)
        val rubber = run(0f, 1200, RubberSpring.RUBBER)
        reset(); run(1f, 300, RubberSpring.JELLY)
        val jelly = run(0f, 1500, RubberSpring.JELLY)
        assertTrue("jelly swings furthest", jelly.min() < rubber.min() && rubber.min() < squish.min())
        assertTrue("jelly wobbles at least as often", crossings(jelly) >= crossings(rubber))
        assertTrue("jelly squashes deepest", RubberSpring.JELLY.squashY > RubberSpring.RUBBER.squashY && RubberSpring.RUBBER.squashY > RubberSpring.SQUISH.squashY)
    }

    @Test fun `every style settles soon enough to keep fast typing crisp`() {
        mapOf(RubberSpring.SQUISH to 700, RubberSpring.RUBBER to 900, RubberSpring.JELLY to 1250).forEach { (sp, limit) ->
            reset(); run(1f, 300, sp)
            val ms = settleMs(sp)
            assertTrue("$sp settled after $ms ms", ms <= limit)
        }
    }

    @Test fun `a quick tap still bounces`() {
        all.forEach { sp ->
            reset(); run(1f, 48, sp) // finger up after ~three frames
            assertTrue("$sp", run(0f, 600, sp).min() < -0.05f)
        }
    }

    @Test fun `uneven or huge frames stay stable and within limits`() {
        all.forEach { sp ->
            for (frame in listOf(1f, 7f, 33f, 64f, 100f)) {
                reset()
                (run(1f, 400, sp, frame) + run(0f, 2000, sp, frame)).forEach {
                    assertTrue("$sp frame $frame: $it", it in RubberSpring.MIN..RubberSpring.MAX)
                }
                assertEquals("$sp frame $frame settles", 0f, pos[0], 0.02f)
            }
        }
    }

    @Test fun `the squash keeps keys looking like keys`() {
        all.forEach { sp ->
            for (s in listOf(RubberSpring.MIN, -0.5f, 0f, 0.5f, 1f, RubberSpring.MAX)) {
                assertTrue("$sp x", RubberSpring.scaleX(s, sp) in 0.85f..1.15f)
                assertTrue("$sp y", RubberSpring.scaleY(s, sp) in 0.75f..1.18f)
            }
        }
    }

    @Test fun `bouncy effects map to their springs and others don't move keys`() {
        assertEquals(RubberSpring.RUBBER, RubberSpring.forEffect(PressEffect.RUBBER))
        assertEquals(RubberSpring.SQUISH, RubberSpring.forEffect(PressEffect.SQUISH))
        assertEquals(RubberSpring.JELLY, RubberSpring.forEffect(PressEffect.JELLY))
        assertNull(RubberSpring.forEffect(PressEffect.INK))
        assertNull(RubberSpring.forEffect(null))
    }
}
