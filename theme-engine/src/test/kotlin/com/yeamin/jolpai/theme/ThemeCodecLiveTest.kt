package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** Customised copies keep what makes a theme change by itself (user request, 2026-11-21). */
class ThemeCodecLiveTest {
    private fun roundTrip(t: KeyboardTheme) = ThemeCodec.decode(ThemeCodec.encode(t), "custom:1")

    @Test fun `a chameleon copy with a new press effect still takes the app's colour`() {
        // The reported case: edit Chameleon, add the Squish effect.
        val edited = BuiltInThemes.CHAMELEON.copy(id = "custom:1", name = "My chameleon", pressEffect = PressEffect.SQUISH)
        val back = roundTrip(edited)
        assertEquals(LiveStyle.AppColour(AppTintStyle.GLOW), back.live)
        assertEquals(PressEffect.SQUISH, back.pressEffect)
        // And it still follows the app: green app, green keyboard; effect in the app's colour.
        val green = AppTint.apply(back, 0xFF25D366.toInt())
        assertEquals("custom:1", green.id)
        assertEquals("My chameleon", green.name)
        assertEquals(PressEffect.SQUISH, green.pressEffect)
        assertEquals(green.accent, green.effectColor)
        val hue = ThemeColors.toHsl(green.accent)[0]
        assertTrue("green accent, hue $hue", hue in 120f..160f)
    }

    @Test fun `every chameleon style and its drift survive`() {
        BuiltInThemes.chameleon.forEach { t -> assertEquals(t.name, t.live, roundTrip(t).live) }
        assertEquals(LiveStyle.AppColour(AppTintStyle.GLOW, driftSeconds = 50), roundTrip(BuiltInThemes.CHAMELEON_DRIFT).live)
    }

    @Test fun `time-of-day copies keep their stops and change through the day`() {
        listOf(BuiltInThemes.DAY_AND_NIGHT, BuiltInThemes.NIGHT_SHIFT, BuiltInThemes.TWILIGHT_CYCLE).forEach { t ->
            val back = roundTrip(t)
            // Same stops and colours (the stops' internal ids aren't saved).
            fun stops(x: KeyboardTheme) = (x.live as LiveStyle.TimeOfDay).stops.map { it.minute to it.look.copy(id = "") }
            assertEquals(t.name, stops(t), stops(back))
            for (h in listOf(0, 6, 9, 13, 18, 22)) {
                val time = LocalTime.of(h, 0)
                assertEquals("${t.name} at $h:00", LiveThemes.at(t, time).background, LiveThemes.at(back, time).background)
            }
        }
    }

    @Test fun `drifting glows and wallpaper colours survive`() {
        assertEquals(LiveStyle.Drift(52), roundTrip(BuiltInThemes.AURORA_DRIFT).live)
        val you = roundTrip(BuiltInThemes.MATERIAL_YOU_DARK)
        assertTrue("Material You copy keeps wallpaper colours", you.dynamic)
        assertNull(you.live)
    }

    @Test fun `still themes stay still, and older exports still import`() {
        val back = roundTrip(BuiltInThemes.OBSIDIAN)
        assertNull(back.live)
        assertFalse(back.dynamic)
        // Written before live themes existed: no "live", no "dynamic".
        val old = """{"format":"lipi-theme-1","name":"Old","dark":true,"background":"#FF101010","key":"#FF202020",
            "keyPressed":"#FF303030","functionKey":"#FF181818","text":"#FFFFFFFF","hint":"#FFAAAAAA",
            "accent":"#FF4FC3F7","onAccent":"#FF000000"}"""
        val t = ThemeCodec.decode(old, "custom:2")
        assertNull(t.live)
        assertFalse(t.dynamic)
    }

    @Test fun `unknown or broken live parts are dropped, not an error`() {
        val base = ThemeCodec.encode(BuiltInThemes.OBSIDIAN).removeSuffix("}")
        assertNull(ThemeCodec.decode("""$base,"live":{"type":"warp"}}""", "custom:3").live)
        assertNull(ThemeCodec.decode("""$base,"live":{"type":"appColour","style":"RAINBOW"}}""", "custom:3").live)
        assertNull(ThemeCodec.decode("""$base,"live":{"type":"timeOfDay","stops":[{"minute":"x"}]}}""", "custom:3").live)
        assertNull(ThemeCodec.decode("""$base,"live":"oops"}""", "custom:3").live)
    }

    @Test fun `silly live values are clamped`() {
        val base = ThemeCodec.encode(BuiltInThemes.OBSIDIAN).removeSuffix("}")
        assertEquals(LiveStyle.Drift(10), ThemeCodec.decode("""$base,"live":{"type":"drift","period":1}}""", "custom:4").live)
        assertEquals(LiveStyle.Drift(600), ThemeCodec.decode("""$base,"live":{"type":"drift","period":99999}}""", "custom:4").live)
        val tod = ThemeCodec.encode(BuiltInThemes.DAY_AND_NIGHT).replace("\"minute\":0", "\"minute\":-50")
        val stops = (ThemeCodec.decode(tod, "custom:5").live as LiveStyle.TimeOfDay).stops
        assertTrue(stops.all { it.minute in 0 until 24 * 60 })
    }
}
