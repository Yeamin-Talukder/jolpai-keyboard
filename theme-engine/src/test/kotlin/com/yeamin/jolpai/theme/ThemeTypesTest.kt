package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTypesTest {
    @Test fun `every built-in theme is in exactly one type`() {
        val listed = ThemeTypes.all.flatMap { it.themes }.map { it.id }
        assertEquals("no theme twice", listed.size, listed.toSet().size)
        assertEquals("every theme has a type", BuiltInThemes.all.map { it.id }.toSet(), listed.toSet())
    }

    @Test fun `types have unique names, aren't empty, and every family has some`() {
        val names = ThemeTypes.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        ThemeTypes.all.forEach { assertTrue(it.name, it.themes.isNotEmpty()) }
        ThemeFamily.entries.forEach { assertTrue(it.label, ThemeTypes.inFamily(it).isNotEmpty()) }
    }

    @Test fun `types say what their themes are`() {
        ThemeTypes.inFamily(ThemeFamily.LIGHT).flatMap { it.themes }.forEach { assertFalse("${it.name} is light", it.dark) }
        ThemeTypes.inFamily(ThemeFamily.DARK).flatMap { it.themes }.forEach { assertTrue("${it.name} is dark", it.dark) }
        ThemeTypes.inFamily(ThemeFamily.DYNAMIC).flatMap { it.themes }.forEach {
            assertTrue("${it.name} moves", it.live != null || it.pressEffect != null)
        }
        ThemeTypes.typeOf(BuiltInThemes.STARDUST.id)!!.themes.forEach { assertTrue(it.pressEffect != null) }
        ThemeTypes.inFamily(ThemeFamily.CHAMELEON).flatMap { it.themes }.forEach { assertTrue("${it.name} follows the app", it.live is LiveStyle.AppColour) }
        assertEquals(BuiltInThemes.chameleon.toSet(), ThemeTypes.inFamily(ThemeFamily.CHAMELEON).flatMap { it.themes }.toSet())
        assertTrue("no Chameleon left under Ambient", ThemeTypes.inFamily(ThemeFamily.AMBIENT).flatMap { it.themes }.none { it.live is LiveStyle.AppColour })
        ThemeTypes.typeOf(BuiltInThemes.NEBULA.id)!!.themes.forEach { assertTrue(it.glows.isNotEmpty() && it.live == null) }
        ThemeTypes.typeOf(BuiltInThemes.SUNBEAM.id)!!.themes.forEach { assertTrue("${it.name}: one spotlight", it.glows.size == 1 && it.dark) }
        ThemeTypes.typeOf(BuiltInThemes.MINT_BREEZE.id)!!.themes.forEach { assertTrue("${it.name}: light glow", it.glows.isNotEmpty() && !it.dark) }
        ThemeTypes.typeOf(BuiltInThemes.GALAXY_SPARKLE.id)!!.themes.forEach { assertTrue("${it.name}: glow and effect", it.glows.isNotEmpty() && it.pressEffect != null) }
        ThemeTypes.typeOf(BuiltInThemes.TWILIGHT_CYCLE.id)!!.themes.forEach { assertTrue(it.live is LiveStyle.TimeOfDay) }
        ThemeTypes.typeOf(BuiltInThemes.EMBER_DRIFT.id)!!.themes.forEach { assertTrue(it.live is LiveStyle.Drift) }
        ThemeTypes.typeOf(BuiltInThemes.RUBBER.id)!!.themes.forEach { assertTrue("${it.name}: bouncy and dark", RubberSpring.forEffect(it.pressEffect) != null && it.dark) }
        ThemeTypes.typeOf(BuiltInThemes.MARSHMALLOW.id)!!.themes.forEach { assertTrue("${it.name}: bouncy and light", RubberSpring.forEffect(it.pressEffect) != null && !it.dark) }
        // Every effect is used by at least one built-in theme.
        val used = BuiltInThemes.all.mapNotNull { it.pressEffect }.toSet()
        assertEquals(PressEffect.entries.toSet(), used)
    }

    @Test fun `a theme's type can be looked up`() {
        assertEquals("Soft dark", ThemeTypes.typeOf(KeyboardTheme.DEFAULT_ID)?.name)
        assertEquals("Cool dark", ThemeTypes.typeOf(BuiltInThemes.OBSIDIAN.id)?.name)
        assertEquals(ThemeFamily.DYNAMIC, ThemeTypes.typeOf(BuiltInThemes.DAY_AND_NIGHT.id)?.family)
        assertNull(ThemeTypes.typeOf(KeyboardTheme.SYSTEM_ID))
        assertNull(ThemeTypes.typeOf("custom:1"))
        assertNull(ThemeTypes.typeOf(BuiltInThemes.EID.id))
    }
}
