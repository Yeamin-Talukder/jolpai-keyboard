package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.HapticLevel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.KeyboardStyle
import com.yeamin.jolpai.core.settings.KeyLook
import com.yeamin.jolpai.core.settings.PanelPlacement
import com.yeamin.jolpai.core.settings.PreviousKeyboard
import com.yeamin.jolpai.core.settings.WelcomeChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeChoicesTest {

    @Test
    fun ridmikUsersStartOnClassic() {
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.RIDMIK, KeyboardSettings())
        assertEquals(KeyboardStyle.CLASSIC, c.style)
        assertFalse(c.autoCorrect)
        // English ⇄ one Bangla layout, like Ridmik's language key.
        assertEquals(listOf(LayoutId.ENGLISH, LayoutId.AVRO), c.layouts)
    }

    @Test
    fun classicLooksLikeRidmik() {
        assertEquals(KeyLook.RIDMIK, KeyboardStyle.CLASSIC.look)
        assertEquals(PanelPlacement.KEYS, KeyboardStyle.CLASSIC.panelPlacement)
        assertTrue(ToolbarItem.EDIT in KeyboardStyle.CLASSIC.toolbar)
    }

    @Test
    fun gboardUsersGetTheGboardLook() {
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.GBOARD, KeyboardSettings())
        assertEquals(KeyboardStyle.GBOARD, c.style)
        assertEquals(KeyLook.GBOARD, c.style.look)
        assertEquals(PanelPlacement.KEYS, c.style.panelPlacement)
    }

    @Test
    fun modernIsJolpaisOwnDefaults() {
        val defaults = KeyboardSettings()
        assertEquals(defaults.toolbar, KeyboardStyle.MODERN.toolbar)
        assertEquals(defaults.panelPlacement, KeyboardStyle.MODERN.panelPlacement)
        assertEquals(KeyboardStyle.MODERN, KeyboardStyle.of(defaults))
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.NONE, defaults)
        assertEquals(KeyboardStyle.MODERN, c.style)
        assertEquals(listOf(LayoutId.ENGLISH, LayoutId.NATIONAL), c.layouts)
        assertEquals(defaults.autoCorrect, c.autoCorrect)
    }

    @Test
    fun gboardUsersKeepAutocorrect() {
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.GBOARD, KeyboardSettings())
        assertTrue(c.autoCorrect)
    }

    @Test
    fun switchingStyleResetsItsUsualLayouts() {
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.RIDMIK, KeyboardSettings()).withStyle(KeyboardStyle.MODERN)
        assertEquals(listOf(LayoutId.ENGLISH, LayoutId.NATIONAL), c.layouts)
    }

    @Test
    fun styleIsRecognisedFromToolbarAndPlacement() {
        for (s in KeyboardStyle.entries) assertEquals(s, KeyboardStyle.matching(s.toolbar, s.panelPlacement, s.look))
        assertNull(KeyboardStyle.matching(listOf(ToolbarItem.EMOJI), PanelPlacement.KEYS, KeyLook.RIDMIK))
        // The Classic toolbar with Jolpai's own bottom row is a custom mix.
        assertNull(KeyboardStyle.matching(KeyboardStyle.CLASSIC.toolbar, PanelPlacement.KEYS, KeyLook.JOLPAI))
    }

    @Test
    fun soundAndVibrationFollowCurrentSettings() {
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.NONE, KeyboardSettings(keySound = false, haptics = HapticLevel.OFF, numberRow = true))
        assertFalse(c.keySound)
        assertFalse(c.vibrate)
        assertTrue(c.numberRow)
    }

    @Test
    fun layoutsFollowTheLanguageKeyOrderAndStartInBangla() {
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.RIDMIK, KeyboardSettings())
            .copy(bangla = listOf(LayoutId.NATIONAL, LayoutId.AVRO))
        assertEquals(listOf(LayoutId.ENGLISH, LayoutId.AVRO, LayoutId.NATIONAL), c.layouts)
        assertEquals(LayoutId.AVRO, c.startLayout)
    }

    @Test
    fun englishOnlyStartsInEnglish() {
        val c = WelcomeChoices.suggestedFor(PreviousKeyboard.NONE, KeyboardSettings()).copy(bangla = emptyList())
        assertEquals(listOf(LayoutId.ENGLISH), c.layouts)
        assertEquals(LayoutId.ENGLISH, c.startLayout)
    }
}
