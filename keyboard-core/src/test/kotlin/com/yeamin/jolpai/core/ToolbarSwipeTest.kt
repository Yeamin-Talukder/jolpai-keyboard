package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.gesture.SwipeDirection
import com.yeamin.jolpai.core.gesture.ToolbarSwipe
import com.yeamin.jolpai.core.gesture.ToolbarSwipe.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarSwipeTest {
    private var actions = setOf(SwipeDirection.LEFT, SwipeDirection.RIGHT)
    private val swipe = ToolbarSwipe(slop = 10f, armDistance = 100f, dominance = 1.7f) { it in actions }

    private fun drag(vararg xs: Float, y: Float = 0f): Phase {
        swipe.down(200f, 20f)
        var p: Phase = Phase.Idle
        xs.forEach { p = swipe.move(200f + it, 20f + y) }
        return p
    }

    @Test fun `a tap is left to the button`() {
        drag(3f, -4f, 6f)
        assertFalse(swipe.claimed)
        assertEquals(Phase.Idle, swipe.phase)
        assertNull(swipe.up())
    }

    @Test fun `a slanted move isn't taken as a swipe`() {
        drag(30f, y = 25f)
        assertFalse(swipe.claimed)
    }

    @Test fun `the pill fills up, arms, and runs on release`() {
        val early = drag(-30f) as Phase.Tracking
        assertTrue(swipe.claimed)
        assertEquals(SwipeDirection.LEFT, early.direction)
        assertEquals(0.3f, early.progress, 0.001f)
        assertEquals(Phase.Armed(SwipeDirection.LEFT), swipe.move(200f - 110f, 20f))
        assertEquals(SwipeDirection.LEFT, swipe.up())
        assertEquals(Phase.Idle, swipe.phase)
    }

    @Test fun `letting go before it is full does nothing`() {
        drag(-20f, -60f, -95f)
        assertNull(swipe.up())
    }

    @Test fun `sliding back cancels, but small wobbles keep it armed`() {
        drag(-120f)
        assertEquals(Phase.Armed(SwipeDirection.LEFT), swipe.phase)
        swipe.move(200f - 90f, 20f) // 0.9: still armed
        assertEquals(Phase.Armed(SwipeDirection.LEFT), swipe.phase)
        swipe.move(200f - 60f, 20f) // 0.6: disarmed
        assertTrue(swipe.phase is Phase.Tracking)
        assertNull(swipe.up())
    }

    @Test fun `changing your mind switches to the other side`() {
        drag(-50f, 30f, 120f)
        assertEquals(Phase.Armed(SwipeDirection.RIGHT), swipe.phase)
        assertEquals(SwipeDirection.RIGHT, swipe.up())
    }

    @Test fun `a side with nothing on it never fires`() {
        actions = setOf(SwipeDirection.LEFT)
        drag(40f, 150f)
        assertFalse("a right swipe isn't even claimed", swipe.claimed)
        drag(-40f, 150f)
        assertEquals(Phase.Tracking(SwipeDirection.RIGHT, 0f), swipe.phase)
        assertNull(swipe.up())
    }

    @Test fun `down swipes work when bound, up never`() {
        actions = setOf(SwipeDirection.DOWN)
        swipe.down(100f, 10f)
        swipe.move(100f, 10f - 150f)
        assertFalse("up isn't a top-bar swipe", swipe.claimed)
        swipe.down(100f, 10f)
        swipe.move(100f, 10f + 120f)
        assertEquals(Phase.Armed(SwipeDirection.DOWN), swipe.phase)
        assertEquals(SwipeDirection.DOWN, swipe.up())
    }

    @Test fun `a cancelled touch does nothing`() {
        drag(-150f)
        swipe.cancel()
        assertNull(swipe.up())
    }
}
