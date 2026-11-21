package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.theme.ThemeRepository
import com.yeamin.jolpai.theme.AppTint
import com.yeamin.jolpai.theme.AppTintStyle
import com.yeamin.jolpai.theme.BuiltInThemes
import com.yeamin.jolpai.theme.LiveStyle
import com.yeamin.jolpai.theme.PressEffect
import com.yeamin.jolpai.theme.ThemeCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/** A customised Chameleon keeps following the app once saved and chosen (user report, 2026-11-21). */
class CustomLiveThemeTest {
    @Test fun `a saved custom chameleon with an effect resolves with its app colour intact`() {
        // What the repository stores and reads back (it saves through ThemeCodec).
        val edited = BuiltInThemes.CHAMELEON.copy(id = "", name = "Chameleon copy", pressEffect = PressEffect.SQUISH)
        val stored = ThemeCodec.decode(ThemeCodec.encode(edited), "custom:7")
        val chosen = ThemeRepository.resolve("custom:7", listOf(stored), systemDark = true)
        assertEquals(LiveStyle.AppColour(AppTintStyle.GLOW), chosen.live)
        assertEquals(PressEffect.SQUISH, chosen.pressEffect)
        // The keyboard then colours it for the app being typed in, as for the built-in one.
        val inBlueApp = AppTint.apply(chosen, 0xFF1877F2.toInt())
        assertEquals("custom:7", inBlueApp.id)
        assertEquals(PressEffect.SQUISH, inBlueApp.pressEffect)
    }
}
