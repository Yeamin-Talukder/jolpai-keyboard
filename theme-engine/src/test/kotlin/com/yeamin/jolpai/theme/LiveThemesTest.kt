package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class LiveThemesTest {
    private val newThemes = BuiltInThemes.coolDark + BuiltInThemes.chameleon + BuiltInThemes.ambient + BuiltInThemes.dynamicThemes

    @Test fun `the new groups are in the gallery with unique ids`() {
        val ids = BuiltInThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        newThemes.forEach { t ->
            assertTrue(t.name, t in BuiltInThemes.all)
            assertEquals(t, BuiltInThemes.byId(t.id))
        }
        assertEquals(BuiltInThemes.classic.size + newThemes.size, BuiltInThemes.all.size)
        BuiltInThemes.coolDark.forEach { assertTrue("${it.name} is dark", it.dark); assertTrue(it.glows.isEmpty()) }
        BuiltInThemes.ambient.forEach {
            assertTrue("${it.name} glows", it.glows.isNotEmpty())
            assertTrue("${it.name} is still or app-coloured", it.live == null || it.live is LiveStyle.AppColour)
        }
        BuiltInThemes.dynamicThemes.forEach { assertTrue("${it.name} moves", it.live != null || it.pressEffect != null) }
    }

    @Test fun `new themes keep labels clearly readable even under the brightest glow`() {
        // Stricter than the 3:1 the older themes are held to: 4.5:1, the normal-text level.
        newThemes.forEach { t ->
            LiveThemes.brightestBackgrounds(t).forEach { bg ->
                val ratio = ThemeColors.contrast(t.text, ThemeColors.over(t.key, bg))
                assertTrue("${t.name}: contrast %.2f".format(ratio), ratio >= 4.5)
            }
        }
    }

    @Test fun `time-of-day themes stay readable at every step of the day`() {
        BuiltInThemes.dynamicThemes.filter { it.live is LiveStyle.TimeOfDay }.forEach { theme ->
            for (minute in 0 until 24 * 60 step LiveThemes.STEP_MINUTES) {
                val t = LiveThemes.at(theme, LocalTime.of(minute / 60, minute % 60))
                val label = "${theme.name} at ${minute / 60}:%02d".format(minute % 60)
                listOfNotNull(t.background, t.backgroundEnd).forEach { bg ->
                    val ratio = ThemeColors.contrast(t.text, ThemeColors.over(t.key, bg or OPAQUE))
                    assertTrue("$label: label contrast %.2f".format(ratio), ratio >= 4.5)
                }
                val accent = ThemeColors.contrast(t.onAccent, t.accent)
                assertTrue("$label: accent contrast %.2f".format(accent), accent >= 3.0)
                assertTrue("$label: popup", ThemeColors.contrast(t.text, ThemeColors.popup(t)) >= ThemeColors.MIN_POPUP_CONTRAST)
            }
        }
    }

    @Test fun `day and night is light at noon and dark at midnight`() {
        val noon = LiveThemes.at(BuiltInThemes.DAY_AND_NIGHT, LocalTime.NOON)
        val midnight = LiveThemes.at(BuiltInThemes.DAY_AND_NIGHT, LocalTime.MIDNIGHT)
        assertFalse(noon.dark)
        assertTrue(midnight.dark)
        // It keeps its own identity whatever the hour.
        assertEquals(BuiltInThemes.DAY_AND_NIGHT.id, noon.id)
        assertEquals(BuiltInThemes.DAY_AND_NIGHT.name, noon.name)
        assertEquals(BuiltInThemes.DAY_AND_NIGHT.live, noon.live)
    }

    @Test fun `night shift is always dark and warmer late at night than by day`() {
        for (hour in 0 until 24) {
            assertTrue("$hour:00", LiveThemes.at(BuiltInThemes.NIGHT_SHIFT, LocalTime.of(hour, 0)).dark)
        }
        fun warmth(c: Int) = ((c shr 16) and 0xFF) - (c and 0xFF) // red minus blue
        val day = LiveThemes.at(BuiltInThemes.NIGHT_SHIFT, LocalTime.of(13, 0))
        val late = LiveThemes.at(BuiltInThemes.NIGHT_SHIFT, LocalTime.of(1, 0))
        assertTrue(warmth(late.background) > warmth(day.background))
        assertTrue(warmth(late.accent) > warmth(day.accent))
    }

    @Test fun `colours change in steps, so the keyboard is rebuilt at most every few minutes`() {
        // Morning to noon: two light looks, blended.
        val a = LiveThemes.at(BuiltInThemes.DAY_AND_NIGHT, LocalTime.of(10, 0))
        val b = LiveThemes.at(BuiltInThemes.DAY_AND_NIGHT, LocalTime.of(10, 9, 59))
        val c = LiveThemes.at(BuiltInThemes.DAY_AND_NIGHT, LocalTime.of(10, 10))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test fun `stops are hit exactly and blending is smooth between them`() {
        val dark1 = BuiltInThemes.DARK
        val dark2 = BuiltInThemes.MIDNIGHT
        val live = LiveStyle.TimeOfDay(listOf(TimeStop.at(6, 0, dark1), TimeStop.at(18, 0, dark2)))
        assertEquals(dark1.background, LiveThemes.lookAt(live, 6 * 60).background)
        assertEquals(dark2.background, LiveThemes.lookAt(live, 18 * 60).background)
        // Half-way (noon) is the even mix; the night wraps round midnight from the 18:00 stop.
        assertEquals(ThemeColors.lerp(dark1.background, dark2.background, 0.5f), LiveThemes.lookAt(live, 12 * 60).background)
        assertEquals(ThemeColors.lerp(dark2.background, dark1.background, 0.5f), LiveThemes.lookAt(live, 0).background)
        // Each step of the day moves each channel only a little.
        var prev = LiveThemes.lookAt(live, 0).background
        for (m in 10 until 24 * 60 step 10) {
            val now = LiveThemes.lookAt(live, m).background
            for (shift in listOf(0, 8, 16)) {
                val d = kotlin.math.abs(((now shr shift) and 0xFF) - ((prev shr shift) and 0xFF))
                assertTrue("minute $m moved $d", d <= 6)
            }
            prev = now
        }
    }

    @Test fun `light and dark looks switch half-way instead of blending into unreadable grey`() {
        val live = LiveStyle.TimeOfDay(listOf(TimeStop.at(6, 0, BuiltInThemes.DARK), TimeStop.at(8, 0, BuiltInThemes.LIGHT)))
        assertSame(BuiltInThemes.DARK, LiveThemes.lookAt(live, 6 * 60 + 50))
        assertSame(BuiltInThemes.LIGHT, LiveThemes.lookAt(live, 7 * 60 + 10))
    }

    @Test fun `a single stop or a still theme is returned as it is`() {
        val one = LiveStyle.TimeOfDay(listOf(TimeStop.at(9, 0, BuiltInThemes.FROST)))
        assertSame(BuiltInThemes.FROST, LiveThemes.lookAt(one, 3 * 60))
        assertSame(BuiltInThemes.NEBULA, LiveThemes.at(BuiltInThemes.NEBULA, LocalTime.NOON))
        assertSame(BuiltInThemes.AURORA_DRIFT, LiveThemes.at(BuiltInThemes.AURORA_DRIFT, LocalTime.NOON))
    }

    @Test fun `a frozen copy keeps the colours of the moment and stops changing`() {
        val t = LiveThemes.frozen(BuiltInThemes.DAY_AND_NIGHT, LocalTime.NOON)
        assertEquals(null, t.live)
        assertEquals(LiveThemes.at(BuiltInThemes.DAY_AND_NIGHT, LocalTime.NOON).background, t.background)
        assertSame(t, LiveThemes.at(t, LocalTime.MIDNIGHT))
    }

    @Test fun `drifting glows loop seamlessly, stay near their place and never get brighter`() {
        BuiltInThemes.dynamicThemes.filter { it.live is LiveStyle.Drift }.forEach { theme ->
            val rest = theme.glows
            assertTrue(theme.name, rest.isNotEmpty())
            assertEquals(LiveThemes.drift(rest, 0f), LiveThemes.drift(rest, 1f))
            for (i in 0..100) {
                val moved = LiveThemes.drift(rest, i / 100f)
                moved.zip(rest).forEach { (m, r) ->
                    assertTrue(kotlin.math.abs(m.x - r.x) <= LiveThemes.DRIFT_X + 1e-4f)
                    assertTrue(kotlin.math.abs(m.y - r.y) <= LiveThemes.DRIFT_Y + 1e-4f)
                    assertTrue(m.color ushr 24 <= r.color ushr 24)
                    assertTrue(m.color ushr 24 >= ((r.color ushr 24) * (1 - LiveThemes.BREATH)).toInt() - 1)
                    assertEquals(r.color and 0xFFFFFF, m.color and 0xFFFFFF)
                    assertEquals(r.radius, m.radius)
                }
            }
        }
    }

    @Test fun `drifting glows move out of step with each other`() {
        val g = Glow(0x40FFFFFF, 0.5f, 0.5f, 0.5f)
        val moved = LiveThemes.drift(listOf(g, g), 0.1f)
        assertNotEquals(moved[0].x, moved[1].x)
    }

    @Test fun `lerp blends alpha too`() {
        assertEquals(0x80808080.toInt(), ThemeColors.lerp(0x00000000, 0xFFFFFFFF.toInt(), 0.5f))
        assertEquals(0x00FFFFFF, ThemeColors.lerp(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), ThemeColors.lerp(0x00FFFFFF, 0xFFFFFFFF.toInt(), 1f))
    }

    @Test fun `glows survive export and import, and silly glows are clamped`() {
        val t = BuiltInThemes.MOON_HALO.copy(id = "custom:3")
        assertEquals(t.glows, ThemeCodec.decode(ThemeCodec.encode(t), "custom:3").glows)
        val wild = BuiltInThemes.NEBULA.copy(glows = List(5) { Glow(0x40FFFFFF, 9f, -9f, 0f) })
        val back = ThemeCodec.decode(ThemeCodec.encode(wild), "custom:4").glows
        assertEquals(Glow.MAX, back.size)
        back.forEach {
            assertEquals(Glow.MAX_POS, it.x)
            assertEquals(Glow.MIN_POS, it.y)
            assertEquals(0.05f, it.radius)
        }
        // A theme exported before glows existed still imports, without any.
        val old = ThemeCodec.encode(BuiltInThemes.DARK)
        assertTrue(ThemeCodec.decode(old, "custom:5").glows.isEmpty())
        // Being live is exported too (since 2026-11-21): a custom copy keeps drifting.
        assertEquals(BuiltInThemes.AURORA_DRIFT.live, ThemeCodec.decode(ThemeCodec.encode(BuiltInThemes.AURORA_DRIFT), "custom:6").live)
    }

    private companion object {
        const val OPAQUE = 0xFF shl 24
    }
}
