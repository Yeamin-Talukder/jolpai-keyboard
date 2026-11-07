package com.yeamin.lipi.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMeterTest {
    private val bursts = ArrayList<Triple<Int, Int, Long>>()
    private val meter = SpeedMeter { t, d, ms -> bursts += Triple(t, d, ms) }

    @Test fun `steady typing is one burst, timed without the pauses`() {
        var now = 1_000L
        repeat(30) { meter.typed(now); now += 200 } // 5 presses a second = 60 wpm
        now += 10_000 // reading something
        repeat(20) { meter.typed(now); now += 200 }
        meter.flush()
        assertEquals(2, bursts.size)
        assertEquals(Triple(30, 0, 6_000L), bursts[0])
        assertEquals(60.0, SpeedMeter.wpm(30, 0, 6_000), 0.01)
    }

    @Test fun `deletions count against speed`() {
        var now = 0L
        repeat(20) { meter.typed(now); now += 200 }
        repeat(5) { meter.deleted(now); now += 200 }
        meter.flush()
        val (typed, deleted, ms) = bursts.single()
        assertEquals(20, typed)
        assertEquals(5, deleted)
        assertTrue(SpeedMeter.wpm(typed.toLong(), deleted.toLong(), ms) < SpeedMeter.wpm(20, 0, ms))
    }

    @Test fun `a few letters say nothing about speed`() {
        var now = 0L
        repeat(3) { meter.typed(now); now += 200 }
        meter.flush()
        assertTrue(bursts.isEmpty())
    }

    @Test fun `a suggestion tap types several letters at once`() {
        var now = 0L
        repeat(6) { meter.typed(now); now += 250 }
        meter.typed(now, count = 6)
        now += 250
        repeat(6) { meter.typed(now); now += 250 }
        meter.flush()
        assertEquals(18, bursts.single().first)
    }
}
