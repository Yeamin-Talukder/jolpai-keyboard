package com.yeamin.jolpai.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Everything about how the keyboard looks. Colours are ARGB ints.
 *
 * [dynamic] themes (Material You) take their colours from the phone's wallpaper at runtime
 * on Android 12+; the colours stored here are the fallback.
 */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Int,
    /** Bottom colour of a vertical gradient, or null for a flat background. */
    val backgroundEnd: Int? = null,
    val key: Int,
    val keyPressed: Int,
    val functionKey: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val onAccent: Int,
    /** Key outline colour, or null for no outline. */
    val border: Int? = null,
    val shadow: Boolean = true,
    val keyRadiusDp: Float = 7f,
    /** Gap between keys, in dp (half on each side of a key). */
    val keyGapDp: Float = 6f,
    /** 0–1: how opaque key backgrounds are (lower shows the background through). */
    val keyOpacity: Float = 1f,
    val labelScale: Float = 1f,
    val boldLabels: Boolean = false,
    /** A private image file shown behind the keys, or null. */
    val backgroundImage: String? = null,
    /** 0–1: dark overlay over the background image, for readable labels. */
    val imageDim: Float = 0.35f,
    val dynamic: Boolean = false,
    /** Soft lights drawn over the background, behind the keys (ambient themes). */
    val glows: List<Glow> = emptyList(),
    /** How the theme changes by itself (time of day, drifting glows), or null for a still theme. */
    val live: LiveStyle? = null,
    /** An animation where a key is pressed, or null for none. */
    val pressEffect: PressEffect? = null,
    /** Colour of [pressEffect], or null for the [accent]. */
    val effectColor: Int? = null,
) {
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    /** Applies the limits the keyboard can draw sensibly. */
    fun sanitized(): KeyboardTheme = copy(
        keyRadiusDp = keyRadiusDp.coerceIn(0f, 24f),
        keyGapDp = keyGapDp.coerceIn(0f, 16f),
        keyOpacity = keyOpacity.coerceIn(0.2f, 1f),
        labelScale = labelScale.coerceIn(0.7f, 1.4f),
        imageDim = imageDim.coerceIn(0f, 0.9f),
        glows = glows.take(Glow.MAX).map { it.sanitized() },
        live = live?.sanitized(),
    )

    companion object {
        const val BUILT_IN_PREFIX = "builtin:"
        const val CUSTOM_PREFIX = "custom:"

        /** Light or dark following the phone's dark mode. */
        const val SYSTEM_ID = "system"
        /** The theme on a fresh install, and the fallback when a chosen theme is missing. */
        const val DEFAULT_ID = "builtin:olive-night"
    }
}

/** The themes that ship with Jolpai. */
object BuiltInThemes {
    private fun c(hex: Long) = hex.toInt()

    val LIGHT = KeyboardTheme(
        id = "builtin:light", name = "Light", dark = false,
        background = c(0xFFE8EBEF), key = c(0xFFFFFFFF), keyPressed = c(0xFFD3D8DF), functionKey = c(0xFFD0D5DD),
        text = c(0xFF1B1D21), hint = c(0xFF7B818B), accent = c(0xFF0B57D0), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 8f,
    )
    val DARK = KeyboardTheme(
        id = "builtin:dark", name = "Dark", dark = true,
        background = c(0xFF1A1B1E), key = c(0xFF3A3C41), keyPressed = c(0xFF575A61), functionKey = c(0xFF2B2D31),
        text = c(0xFFEDEEF0), hint = c(0xFF9EA3AC), accent = c(0xFFA8C7FA), onAccent = c(0xFF062E6F),
        shadow = false, keyRadiusDp = 8f,
    )
    val AMOLED = KeyboardTheme(
        id = "builtin:amoled", name = "AMOLED black", dark = true,
        background = c(0xFF000000), key = c(0xFF101010), keyPressed = c(0xFF2A2A2A), functionKey = c(0xFF000000),
        text = c(0xFFF1F1F1), hint = c(0xFF8A8A8A), accent = c(0xFF4FC3F7), onAccent = c(0xFF000000),
        border = c(0xFF262626), shadow = false,
    )
    val MATERIAL_YOU_LIGHT = LIGHT.copy(id = "builtin:you-light", name = "Material You (light)", dynamic = true, keyRadiusDp = 12f)
    val MATERIAL_YOU_DARK = DARK.copy(id = "builtin:you-dark", name = "Material You (dark)", dynamic = true, keyRadiusDp = 12f)
    val MINIMAL = KeyboardTheme(
        id = "builtin:minimal", name = "Minimal", dark = false,
        background = c(0xFFF7F7F7), key = c(0xFFF7F7F7), keyPressed = c(0xFFE4E4E4), functionKey = c(0xFFF7F7F7),
        text = c(0xFF222222), hint = c(0xFF9E9E9E), accent = c(0xFF222222), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 4f,
    )
    val OCEAN = KeyboardTheme(
        id = "builtin:ocean", name = "Ocean", dark = true,
        background = c(0xFF0B3C5D), backgroundEnd = c(0xFF0E6B8C), key = c(0xFF1D5F80), keyPressed = c(0xFF2B7DA6),
        functionKey = c(0xFF124A69), text = c(0xFFEAF6FF), hint = c(0xFFA6D4EE), accent = c(0xFFFFC857), onAccent = c(0xFF0B3C5D),
        keyRadiusDp = 10f,
    )
    val SUNSET = KeyboardTheme(
        id = "builtin:sunset", name = "Sunset", dark = true,
        background = c(0xFF7B2D8E), backgroundEnd = c(0xFFB83B6B), key = c(0x33FFFFFF), keyPressed = c(0x55FFFFFF),
        functionKey = c(0x22000000), text = c(0xFFFFFFFF), hint = c(0xFFFFE3EE), accent = c(0xFFFFD166), onAccent = c(0xFF4A1A55),
        shadow = false, keyRadiusDp = 12f,
    )
    val FOREST = KeyboardTheme(
        id = "builtin:forest", name = "Forest", dark = true,
        background = c(0xFF1B3A2F), key = c(0xFF2D5A47), keyPressed = c(0xFF3F7A61), functionKey = c(0xFF234A3B),
        text = c(0xFFEFF7F1), hint = c(0xFFA9CDB8), accent = c(0xFFF4D35E), onAccent = c(0xFF1B3A2F),
    )
    val SLATE = KeyboardTheme(
        id = "builtin:slate", name = "Professional", dark = false,
        background = c(0xFFDDE1E6), key = c(0xFFF4F5F7), keyPressed = c(0xFFCBD2DA), functionKey = c(0xFFB9C1CB),
        text = c(0xFF1C2733), hint = c(0xFF5D6B7A), accent = c(0xFF2F4B6E), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 4f, border = c(0xFFC4CBD3), shadow = false,
    )
    val GLASS = KeyboardTheme(
        id = "builtin:glass", name = "Glass", dark = true,
        background = c(0xFF23395B), backgroundEnd = c(0xFF406E8E), key = c(0x40FFFFFF), keyPressed = c(0x66FFFFFF),
        functionKey = c(0x26FFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFDCE8F5), accent = c(0xFFFFFFFF), onAccent = c(0xFF23395B),
        border = c(0x55FFFFFF), shadow = false, keyRadiusDp = 12f,
    )
    val HIGH_CONTRAST = KeyboardTheme(
        id = "builtin:contrast", name = "High contrast", dark = true,
        background = c(0xFF000000), key = c(0xFF000000), keyPressed = c(0xFF333333), functionKey = c(0xFF000000),
        text = c(0xFFFFFFFF), hint = c(0xFFFFFF00), accent = c(0xFFFFFF00), onAccent = c(0xFF000000),
        border = c(0xFFFFFFFF), shadow = false, labelScale = 1.15f, boldLabels = true,
    )

    // ---------------------------------------------------------------- added 2026-11-07
    val LAVENDER = KeyboardTheme(
        id = "builtin:lavender", name = "Lavender", dark = false,
        background = c(0xFFEDE7F6), key = c(0xFFFFFFFF), keyPressed = c(0xFFD9CCF0), functionKey = c(0xFFDCD0F2),
        text = c(0xFF2A2140), hint = c(0xFF7C6F98), accent = c(0xFF6750A4), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 12f,
    )
    val MINT = KeyboardTheme(
        id = "builtin:mint", name = "Mint", dark = false,
        background = c(0xFFE3F4EC), key = c(0xFFFFFFFF), keyPressed = c(0xFFC6E8D7), functionKey = c(0xFFCBE8D9),
        text = c(0xFF143326), hint = c(0xFF5D7F70), accent = c(0xFF1B7F5A), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 12f,
    )
    val SAKURA = KeyboardTheme(
        id = "builtin:sakura", name = "Sakura", dark = false,
        background = c(0xFFFCE4EC), backgroundEnd = c(0xFFF6CFDC), key = c(0xFFFFFFFF), keyPressed = c(0xFFF5C6D6),
        functionKey = c(0xFFF7D3DF), text = c(0xFF3D1F2B), hint = c(0xFF9C6B7D), accent = c(0xFFC93D67), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val PAPER = KeyboardTheme(
        id = "builtin:paper", name = "Paper", dark = false,
        background = c(0xFFF3EEE4), key = c(0xFFFFFDF8), keyPressed = c(0xFFE6DDCC), functionKey = c(0xFFE7DECF),
        text = c(0xFF2E2A24), hint = c(0xFF8A7F70), accent = c(0xFF8D5A2B), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 8f,
    )
    val MIDNIGHT = KeyboardTheme(
        id = "builtin:midnight", name = "Midnight", dark = true,
        background = c(0xFF0E1525), key = c(0xFF1C2639), keyPressed = c(0xFF2C3B58), functionKey = c(0xFF151D2F),
        text = c(0xFFE6ECF7), hint = c(0xFF8C99B3), accent = c(0xFF7AA2F7), onAccent = c(0xFF0E1525),
        shadow = false, keyRadiusDp = 10f,
    )
    val MOCHA = KeyboardTheme(
        id = "builtin:mocha", name = "Mocha", dark = true,
        background = c(0xFF221A16), key = c(0xFF3A2D26), keyPressed = c(0xFF564236), functionKey = c(0xFF2D231E),
        text = c(0xFFF3E7DC), hint = c(0xFFB59C88), accent = c(0xFFE0A872), onAccent = c(0xFF2A1B10),
        shadow = false, keyRadiusDp = 10f,
    )
    val FROST = KeyboardTheme(
        id = "builtin:frost", name = "Frost", dark = true,
        background = c(0xFF2E3440), key = c(0xFF3B4252), keyPressed = c(0xFF4C566A), functionKey = c(0xFF343A47),
        text = c(0xFFECEFF4), hint = c(0xFFA3ABBA), accent = c(0xFF88C0D0), onAccent = c(0xFF2E3440),
        shadow = false, keyRadiusDp = 10f,
    )
    val TWILIGHT = KeyboardTheme(
        id = "builtin:twilight", name = "Twilight", dark = true,
        background = c(0xFF1E1530), backgroundEnd = c(0xFF3A1F4F), key = c(0x2EFFFFFF), keyPressed = c(0x52FFFFFF),
        functionKey = c(0x1AFFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFD7C6EE), accent = c(0xFFF5A3C7), onAccent = c(0xFF2A1238),
        shadow = false, keyRadiusDp = 14f,
    )
    val AURORA = KeyboardTheme(
        id = "builtin:aurora", name = "Aurora", dark = true,
        background = c(0xFF0F2027), backgroundEnd = c(0xFF1F3F47), key = c(0x26FFFFFF), keyPressed = c(0x4DFFFFFF),
        functionKey = c(0x14FFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFB5E3D8), accent = c(0xFF4FE3B5), onAccent = c(0xFF07261E),
        border = c(0x2EFFFFFF), shadow = false, keyRadiusDp = 12f,
    )
    val NEON = KeyboardTheme(
        id = "builtin:neon", name = "Neon", dark = true,
        background = c(0xFF050508), key = c(0xFF0D0D14), keyPressed = c(0xFF1C1C2B), functionKey = c(0xFF08080D),
        text = c(0xFFE9FBFF), hint = c(0xFF6FE9FF), accent = c(0xFF00E5FF), onAccent = c(0xFF001A1F),
        border = c(0xFF00A8C2), shadow = false, keyRadiusDp = 10f,
    )

    // ---------------------------------------------------------------- soft set, 2026-11-07
    // Low-glare pastels and muted darks with rounder keys, chosen to be easy on the eyes.
    val PEACH = KeyboardTheme(
        id = "builtin:peach", name = "Peach", dark = false,
        background = c(0xFFFBE9E0), key = c(0xFFFFFFFF), keyPressed = c(0xFFF6D3C3), functionKey = c(0xFFF5D9CC),
        text = c(0xFF3B2620), hint = c(0xFFA07A6C), accent = c(0xFFC8553D), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val SKY = KeyboardTheme(
        id = "builtin:sky", name = "Sky", dark = false,
        background = c(0xFFE3F2FD), backgroundEnd = c(0xFFD3E6FA), key = c(0xFFFFFFFF), keyPressed = c(0xFFC5DDF5),
        functionKey = c(0xFFCFE3F7), text = c(0xFF13263A), hint = c(0xFF5F7A94), accent = c(0xFF2F6FB5), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val MATCHA = KeyboardTheme(
        id = "builtin:matcha", name = "Matcha", dark = false,
        background = c(0xFFE6EDD9), key = c(0xFFF8FBF2), keyPressed = c(0xFFD2DFBC), functionKey = c(0xFFD5E0C2),
        text = c(0xFF23301A), hint = c(0xFF6E7E5E), accent = c(0xFF5B7F3A), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 12f,
    )
    val VANILLA = KeyboardTheme(
        id = "builtin:vanilla", name = "Vanilla", dark = false,
        background = c(0xFFFBF4DF), key = c(0xFFFFFDF6), keyPressed = c(0xFFF1E3B8), functionKey = c(0xFFF2E6C2),
        text = c(0xFF33291A), hint = c(0xFF8F7F5C), accent = c(0xFFA86A12), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 12f,
    )
    val CLOUD = KeyboardTheme(
        id = "builtin:cloud", name = "Cloud", dark = false,
        background = c(0xFFF2F4F8), key = c(0xFFFFFFFF), keyPressed = c(0xFFE1E6EF), functionKey = c(0xFFE6EAF1),
        text = c(0xFF1F2430), hint = c(0xFF8A93A6), accent = c(0xFF5B6CF0), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 16f,
    )
    val FJORD = KeyboardTheme(
        id = "builtin:fjord", name = "Fjord", dark = false,
        background = c(0xFFE5E9F0), key = c(0xFFF4F6FA), keyPressed = c(0xFFD8DEE9), functionKey = c(0xFFD8DEE9),
        text = c(0xFF2E3440), hint = c(0xFF6B7385), accent = c(0xFF5E81AC), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 10f,
    )
    val ROSE_GOLD = KeyboardTheme(
        id = "builtin:rose-gold", name = "Rose gold", dark = false,
        background = c(0xFFF7E1DA), backgroundEnd = c(0xFFEBC9C0), key = c(0xFFFFF8F5), keyPressed = c(0xFFF0D2C8),
        functionKey = c(0xFFF3DAD2), text = c(0xFF3E2723), hint = c(0xFF9A7368), accent = c(0xFFA45C67), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val LAGOON = KeyboardTheme(
        id = "builtin:lagoon", name = "Lagoon", dark = false,
        background = c(0xFFD8F3F1), backgroundEnd = c(0xFFBFE6EE), key = c(0xFFF7FDFD), keyPressed = c(0xFFC2E8E4),
        functionKey = c(0xFFCDEBEA), text = c(0xFF0F3036), hint = c(0xFF4E7C80), accent = c(0xFF117C7C), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val SUNRISE = KeyboardTheme(
        id = "builtin:sunrise", name = "Sunrise", dark = false,
        background = c(0xFFFFE8D6), backgroundEnd = c(0xFFFFD1DC), key = c(0xCCFFFFFF), keyPressed = c(0xFFFFFFFF),
        functionKey = c(0x66FFFFFF), text = c(0xFF3A2330), hint = c(0xFF9A6E7E), accent = c(0xFFD6456E), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 16f,
    )
    val LILAC_DUSK = KeyboardTheme(
        id = "builtin:lilac-dusk", name = "Lilac dusk", dark = true,
        background = c(0xFF2A2440), backgroundEnd = c(0xFF46395F), key = c(0xFF3D3459), keyPressed = c(0xFF554A7A),
        functionKey = c(0xFF332B4D), text = c(0xFFF1ECFF), hint = c(0xFFB9AEDB), accent = c(0xFFC6A7FF), onAccent = c(0xFF2A1B4A),
        shadow = false, keyRadiusDp = 14f,
    )
    val DUSK_ROSE = KeyboardTheme(
        id = "builtin:dusk-rose", name = "Dusk rose", dark = true,
        background = c(0xFF2B1D24), key = c(0xFF3F2B35), keyPressed = c(0xFF5A3F4C), functionKey = c(0xFF33232B),
        text = c(0xFFF8E9EF), hint = c(0xFFC49DAE), accent = c(0xFFF4A6C0), onAccent = c(0xFF3B1426),
        shadow = false, keyRadiusDp = 14f,
    )
    val DEEP_SEA = KeyboardTheme(
        id = "builtin:deep-sea", name = "Deep sea", dark = true,
        background = c(0xFF0A1F2E), backgroundEnd = c(0xFF0F3346), key = c(0xFF16384D), keyPressed = c(0xFF22506B),
        functionKey = c(0xFF112C3D), text = c(0xFFE3F4FB), hint = c(0xFF8FC1D6), accent = c(0xFF5ED3F3), onAccent = c(0xFF062330),
        shadow = false, keyRadiusDp = 12f,
    )

    // ---------------------------------------------------------------- third set, 2026-11-07
    val CORAL = KeyboardTheme(
        id = "builtin:coral", name = "Coral", dark = false,
        background = c(0xFFFFE5DF), key = c(0xFFFFFFFF), keyPressed = c(0xFFFFCFC4), functionKey = c(0xFFFFD6CC),
        text = c(0xFF3D211C), hint = c(0xFFA5736A), accent = c(0xFFD0503A), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val HONEY = KeyboardTheme(
        id = "builtin:honey", name = "Honey", dark = false,
        background = c(0xFFFFF1CF), backgroundEnd = c(0xFFFBE2A8), key = c(0xFFFFFCF2), keyPressed = c(0xFFF7DE9A),
        functionKey = c(0xFFF8E3AE), text = c(0xFF3A2C0E), hint = c(0xFF94783A), accent = c(0xFF9A6200), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val PISTACHIO = KeyboardTheme(
        id = "builtin:pistachio", name = "Pistachio", dark = false,
        background = c(0xFFEAF4DC), key = c(0xFFFBFEF6), keyPressed = c(0xFFD6EABD), functionKey = c(0xFFDCEBC8),
        text = c(0xFF1F3311), hint = c(0xFF6B8656), accent = c(0xFF4C7A26), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 16f,
    )
    val SAND = KeyboardTheme(
        id = "builtin:sand", name = "Sand dune", dark = false,
        background = c(0xFFF2E6D6), backgroundEnd = c(0xFFE6D2B8), key = c(0xFFFBF5EC), keyPressed = c(0xFFE8D8C0),
        functionKey = c(0xFFEADBC6), text = c(0xFF3A2E22), hint = c(0xFF8C7660), accent = c(0xFF96603A), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 12f,
    )
    val POLAR = KeyboardTheme(
        id = "builtin:polar", name = "Polar", dark = false,
        background = c(0xFFEAF3F8), key = c(0xFFFFFFFF), keyPressed = c(0xFFD3E5EF), functionKey = c(0xFFDAE8F0),
        text = c(0xFF14222B), hint = c(0xFF6D8796), accent = c(0xFF2B7A9E), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 12f, border = c(0xFFD5E3EB),
    )
    val SEAFOAM = KeyboardTheme(
        id = "builtin:seafoam", name = "Seafoam", dark = false,
        background = c(0xFFDDF7EE), backgroundEnd = c(0xFFC9EBF2), key = c(0xB3FFFFFF), keyPressed = c(0xFFFFFFFF),
        functionKey = c(0x59FFFFFF), text = c(0xFF10322B), hint = c(0xFF4F8076), accent = c(0xFF167A66), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 16f,
    )
    val GRAPHITE = KeyboardTheme(
        id = "builtin:graphite", name = "Graphite", dark = true,
        background = c(0xFF1C1D1F), key = c(0xFF2E3033), keyPressed = c(0xFF46494D), functionKey = c(0xFF252629),
        text = c(0xFFECECEC), hint = c(0xFF9A9DA2), accent = c(0xFFFF9F43), onAccent = c(0xFF2B1600),
        shadow = false, keyRadiusDp = 10f,
    )
    val BERRY = KeyboardTheme(
        id = "builtin:berry", name = "Berry", dark = true,
        background = c(0xFF26132A), key = c(0xFF3B2141), keyPressed = c(0xFF57325F), functionKey = c(0xFF301A35),
        text = c(0xFFF8E8FB), hint = c(0xFFC79ED0), accent = c(0xFFE58BF0), onAccent = c(0xFF34103A),
        shadow = false, keyRadiusDp = 14f,
    )
    val MOSS = KeyboardTheme(
        id = "builtin:moss", name = "Moss", dark = true,
        background = c(0xFF1A221B), key = c(0xFF2A362B), keyPressed = c(0xFF3E4F40), functionKey = c(0xFF222C23),
        text = c(0xFFE7F0E4), hint = c(0xFF9DB39A), accent = c(0xFFA8D58C), onAccent = c(0xFF16280C),
        shadow = false, keyRadiusDp = 12f,
    )
    val COSMIC = KeyboardTheme(
        id = "builtin:cosmic", name = "Cosmic", dark = true,
        background = c(0xFF0D0B26), backgroundEnd = c(0xFF2A1450), key = c(0x29FFFFFF), keyPressed = c(0x4DFFFFFF),
        functionKey = c(0x14FFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFC7BDF2), accent = c(0xFF9D7BFF), onAccent = c(0xFF14073A),
        border = c(0x24FFFFFF), shadow = false, keyRadiusDp = 14f,
    )
    val EMBER = KeyboardTheme(
        id = "builtin:ember", name = "Ember", dark = true,
        background = c(0xFF1F120E), backgroundEnd = c(0xFF3A1A10), key = c(0xFF35221B), keyPressed = c(0xFF52342A),
        functionKey = c(0xFF2A1A14), text = c(0xFFFBEDE6), hint = c(0xFFCF9F8A), accent = c(0xFFFF8A5B), onAccent = c(0xFF2E0F02),
        shadow = false, keyRadiusDp = 12f,
    )

    // ---------------------------------------------------------------- colourful set, 2026-11-07
    // Brighter, saturated colours (still with readable labels and a soft feel).
    val TANGERINE = KeyboardTheme(
        id = "builtin:tangerine", name = "Tangerine", dark = false,
        background = c(0xFFFFD8B5), key = c(0xFFFFF6EE), keyPressed = c(0xFFFFC08A), functionKey = c(0xFFFFC79A),
        text = c(0xFF3D1E05), hint = c(0xFFA0602A), accent = c(0xFFE8590C), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val LEMONADE = KeyboardTheme(
        id = "builtin:lemonade", name = "Lemonade", dark = false,
        background = c(0xFFFFF59D), backgroundEnd = c(0xFFE6F59A), key = c(0xFFFFFDE7), keyPressed = c(0xFFFFF176),
        functionKey = c(0xFFF7EE8A), text = c(0xFF2F2A05), hint = c(0xFF857A20), accent = c(0xFF5E8C12), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 16f,
    )
    val BUBBLEGUM = KeyboardTheme(
        id = "builtin:bubblegum", name = "Bubblegum", dark = false,
        background = c(0xFFFFC2DF), backgroundEnd = c(0xFFF9A8D4), key = c(0xFFFFF0F7), keyPressed = c(0xFFFFB0D5),
        functionKey = c(0xFFFFBCDB), text = c(0xFF45102C), hint = c(0xFFA8507D), accent = c(0xFFD6247A), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 18f,
    )
    val AZURE = KeyboardTheme(
        id = "builtin:azure", name = "Azure", dark = false,
        background = c(0xFFB3E0FF), key = c(0xFFF2FAFF), keyPressed = c(0xFF8FD0FF), functionKey = c(0xFF9ED6FF),
        text = c(0xFF062A45), hint = c(0xFF3A7099), accent = c(0xFF0A6FD1), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 14f,
    )
    val CANDY = KeyboardTheme(
        id = "builtin:candy", name = "Cotton candy", dark = false,
        background = c(0xFFFFC6E5), backgroundEnd = c(0xFFB9D9FF), key = c(0xCCFFFFFF), keyPressed = c(0xFFFFFFFF),
        functionKey = c(0x66FFFFFF), text = c(0xFF2A1A3A), hint = c(0xFF7A5C93), accent = c(0xFF8E44D9), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 18f,
    )
    val GRAPE = KeyboardTheme(
        id = "builtin:grape", name = "Grape", dark = true,
        background = c(0xFF2D0F4A), backgroundEnd = c(0xFF4A1673), key = c(0xFF4B2470), keyPressed = c(0xFF6A3796), functionKey = c(0xFF3B1A5B),
        text = c(0xFFF6EDFF), hint = c(0xFFC9A8EC), accent = c(0xFFFFC94D), onAccent = c(0xFF3A2000),
        shadow = false, keyRadiusDp = 14f,
    )
    val EMERALD = KeyboardTheme(
        id = "builtin:emerald", name = "Emerald", dark = true,
        background = c(0xFF04342A), key = c(0xFF0B5244), keyPressed = c(0xFF14705E), functionKey = c(0xFF074437),
        text = c(0xFFE6FFF7), hint = c(0xFF88D9C0), accent = c(0xFF2EE6A8), onAccent = c(0xFF00281C),
        shadow = false, keyRadiusDp = 12f,
    )
    val SAPPHIRE = KeyboardTheme(
        id = "builtin:sapphire", name = "Sapphire", dark = true,
        background = c(0xFF0A1A4F), backgroundEnd = c(0xFF10287A), key = c(0xFF1A3A8C), keyPressed = c(0xFF2A52B5), functionKey = c(0xFF132D6E),
        text = c(0xFFEAF0FF), hint = c(0xFFA3B8F0), accent = c(0xFF5CC8FF), onAccent = c(0xFF001E33),
        shadow = false, keyRadiusDp = 12f,
    )
    val RUBY = KeyboardTheme(
        id = "builtin:ruby", name = "Ruby", dark = true,
        background = c(0xFF3A0812), key = c(0xFF5C1222), keyPressed = c(0xFF7D1D31), functionKey = c(0xFF4A0D19),
        text = c(0xFFFFECEF), hint = c(0xFFF0A3B1), accent = c(0xFFFF5A79), onAccent = c(0xFF3A0010),
        shadow = false, keyRadiusDp = 12f,
    )
    val TEAL = KeyboardTheme(
        id = "builtin:teal", name = "Teal", dark = true,
        background = c(0xFF003B40), key = c(0xFF00575E), keyPressed = c(0xFF00747D), functionKey = c(0xFF00494F),
        text = c(0xFFE0FCFF), hint = c(0xFF7FD3DB), accent = c(0xFFFFB86B), onAccent = c(0xFF3A1E00),
        shadow = false, keyRadiusDp = 12f,
    )
    val MANGO = KeyboardTheme(
        id = "builtin:mango", name = "Mango tango", dark = true,
        background = c(0xFFB3360B), backgroundEnd = c(0xFFD9206B), key = c(0x33FFFFFF), keyPressed = c(0x59FFFFFF),
        functionKey = c(0x1F000000), text = c(0xFFFFFFFF), hint = c(0xFFFFE0CC), accent = c(0xFFFFE066), onAccent = c(0xFF4A1500),
        shadow = false, keyRadiusDp = 14f,
    )
    val LIME = KeyboardTheme(
        id = "builtin:lime", name = "Lime", dark = true,
        background = c(0xFF151A12), key = c(0xFF252D1F), keyPressed = c(0xFF374430), functionKey = c(0xFF1D2318),
        text = c(0xFFF1FBE8), hint = c(0xFFA6C48E), accent = c(0xFFB6F23A), onAccent = c(0xFF1C2A00),
        border = c(0xFF3B4A2E), shadow = false, keyRadiusDp = 10f,
    )

    // Soft dark: cool, muted colours with gentle contrast, for low light and long typing.
    // Text stays well above the 3:1 test; accents are pastel instead of saturated.
    val MISTY_BLUE = KeyboardTheme(
        id = "builtin:misty-blue", name = "Misty blue", dark = true,
        background = c(0xFF1C2230), backgroundEnd = c(0xFF232B3C), key = c(0xFF2A3344), keyPressed = c(0xFF3A4659),
        functionKey = c(0xFF232A39), text = c(0xFFE4EAF5), hint = c(0xFF98A6BF), accent = c(0xFF9BB8E8), onAccent = c(0xFF14223A),
        shadow = false, keyRadiusDp = 12f,
    )
    val ARCTIC_NIGHT = KeyboardTheme(
        id = "builtin:arctic-night", name = "Arctic night", dark = true,
        background = c(0xFF182327), key = c(0xFF24343A), keyPressed = c(0xFF33484F), functionKey = c(0xFF1E2B30),
        text = c(0xFFE2F1F3), hint = c(0xFF93B2B8), accent = c(0xFF8FD9D9), onAccent = c(0xFF0D2E30),
        shadow = false, keyRadiusDp = 12f,
    )
    val STORM = KeyboardTheme(
        id = "builtin:storm", name = "Storm", dark = true,
        background = c(0xFF22262B), key = c(0xFF30363D), keyPressed = c(0xFF424A53), functionKey = c(0xFF292E34),
        text = c(0xFFE6EAEE), hint = c(0xFF9AA3AD), accent = c(0xFF8EB3D1), onAccent = c(0xFF142636),
        shadow = false, keyRadiusDp = 10f,
    )
    val MOONLIGHT = KeyboardTheme(
        id = "builtin:moonlight", name = "Moonlight", dark = true,
        background = c(0xFF161A2B), backgroundEnd = c(0xFF252B45), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFF0F2FA), hint = c(0xFFB4BAD6), accent = c(0xFFC9D3FF), onAccent = c(0xFF1A2048),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
    )
    val SAGE_NIGHT = KeyboardTheme(
        id = "builtin:sage-night", name = "Sage night", dark = true,
        background = c(0xFF1C2421), key = c(0xFF2A3531), keyPressed = c(0xFF3B4943), functionKey = c(0xFF232C29),
        text = c(0xFFE5EFEA), hint = c(0xFF9DB2A8), accent = c(0xFF9FD3BE), onAccent = c(0xFF12302A),
        shadow = false, keyRadiusDp = 12f,
    )
    val PERIWINKLE = KeyboardTheme(
        id = "builtin:periwinkle", name = "Periwinkle", dark = true,
        background = c(0xFF1E2033), key = c(0xFF2C2F4A), keyPressed = c(0xFF3D4163), functionKey = c(0xFF25273D),
        text = c(0xFFE9EAFB), hint = c(0xFFA4A7D6), accent = c(0xFFA9B1FF), onAccent = c(0xFF1B1F52),
        shadow = false, keyRadiusDp = 14f,
    )
    /** Jolpai's own dark theme: the olive of the app icon. */
    val OLIVE_NIGHT = KeyboardTheme(
        id = "builtin:olive-night", name = "Olive night", dark = true,
        background = c(0xFF1B1E16), key = c(0xFF2B3024), keyPressed = c(0xFF3E4534), functionKey = c(0xFF23271D),
        text = c(0xFFEEF0E4), hint = c(0xFFA5AD8E), accent = c(0xFFB5C94A), onAccent = c(0xFF1F2608),
        shadow = false, keyRadiusDp = 12f,
    )

    // ---------------------------------------------------------------- cool dark, 2026-11-21
    // Blue, teal and slate darks with opaque keys, gentle contrast and pastel accents.
    val OBSIDIAN = KeyboardTheme(
        id = "builtin:obsidian", name = "Obsidian", dark = true,
        background = c(0xFF0F1216), key = c(0xFF1C2128), keyPressed = c(0xFF2A313B), functionKey = c(0xFF161A20),
        text = c(0xFFE3E8EF), hint = c(0xFF8C96A3), accent = c(0xFF7FB8E6), onAccent = c(0xFF0B1F30),
        shadow = false, keyRadiusDp = 12f,
    )
    val GLACIER = KeyboardTheme(
        id = "builtin:glacier", name = "Glacier", dark = true,
        background = c(0xFF13222A), backgroundEnd = c(0xFF0F1C23), key = c(0xFF1F333D), keyPressed = c(0xFF2C4652),
        functionKey = c(0xFF182A33), text = c(0xFFE0F2F7), hint = c(0xFF8DB5C2), accent = c(0xFFA5E4F2), onAccent = c(0xFF0A2E38),
        shadow = false, keyRadiusDp = 14f,
    )
    val INDIGO_INK = KeyboardTheme(
        id = "builtin:indigo-ink", name = "Indigo ink", dark = true,
        background = c(0xFF14152A), key = c(0xFF22243F), keyPressed = c(0xFF323559), functionKey = c(0xFF1A1C33),
        text = c(0xFFE6E7FA), hint = c(0xFF9A9CC8), accent = c(0xFF8F9BFF), onAccent = c(0xFF141A52),
        shadow = false, keyRadiusDp = 12f,
    )
    val BLUE_STEEL = KeyboardTheme(
        id = "builtin:blue-steel", name = "Blue steel", dark = true,
        background = c(0xFF1A1F26), key = c(0xFF28303A), keyPressed = c(0xFF3A4450), functionKey = c(0xFF20262E),
        text = c(0xFFE4E9EF), hint = c(0xFF97A2AF), accent = c(0xFFB0C4DE), onAccent = c(0xFF1A2838),
        border = c(0xFF323B46), shadow = false, keyRadiusDp = 8f,
    )
    val NORDIC_PINE = KeyboardTheme(
        id = "builtin:nordic-pine", name = "Nordic pine", dark = true,
        background = c(0xFF121C1C), key = c(0xFF1E2C2B), keyPressed = c(0xFF2D403E), functionKey = c(0xFF182424),
        text = c(0xFFE2F0EC), hint = c(0xFF8FABA5), accent = c(0xFF7FD1B9), onAccent = c(0xFF0C2E25),
        shadow = false, keyRadiusDp = 12f,
    )
    /** Near-black for OLED screens, with faint outlines so the keys still read as keys. */
    val DEEP_SPACE = KeyboardTheme(
        id = "builtin:deep-space", name = "Deep space", dark = true,
        background = c(0xFF05070C), key = c(0xFF10141C), keyPressed = c(0xFF1D2430), functionKey = c(0xFF0A0D13),
        text = c(0xFFE8EDF5), hint = c(0xFF7D8899), accent = c(0xFF6EC1FF), onAccent = c(0xFF002238),
        border = c(0xFF1A202B), shadow = false, keyRadiusDp = 12f,
    )

    // ---------------------------------------------------------------- ambient, 2026-11-21
    // Dark gradients with one or two soft glows behind lightly see-through keys. Labels stay at
    // least 4.5:1 even where a glow is brightest (tested in LiveThemesTest).
    private val NEBULA_GLOWS = listOf(Glow(c(0x5C9D7BFF), 0.15f, 0f, 0.75f), Glow(c(0x4D4FA3FF), 0.9f, 1f, 0.65f))
    private val NORTHERN_GLOWS = listOf(Glow(c(0x4D3DDC97), 0.25f, -0.05f, 0.75f), Glow(c(0x388B6BFF), 0.9f, 0.35f, 0.55f))
    private val TIDE_GLOWS = listOf(Glow(c(0x5232E0C4), 0.5f, 1.15f, 0.85f), Glow(c(0x382F7BFF), 0.05f, -0.05f, 0.55f))

    val NEBULA = KeyboardTheme(
        id = "builtin:nebula", name = "Nebula", dark = true,
        background = c(0xFF0E0B1F), backgroundEnd = c(0xFF181233), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFF3F0FF), hint = c(0xFFC9C0EE), accent = c(0xFFB9A4FF), onAccent = c(0xFF22145A),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f, glows = NEBULA_GLOWS,
    )
    val BIOLUMINESCENCE = KeyboardTheme(
        id = "builtin:bioluminescence", name = "Bioluminescence", dark = true,
        background = c(0xFF051A1F), backgroundEnd = c(0xFF072630), key = c(0x1AFFFFFF), keyPressed = c(0x3DFFFFFF),
        functionKey = c(0x0FFFFFFF), text = c(0xFFE8FFFB), hint = c(0xFFA6DCD2), accent = c(0xFF4FF0CF), onAccent = c(0xFF00241D),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f, glows = TIDE_GLOWS,
    )
    val MOON_HALO = KeyboardTheme(
        id = "builtin:moon-halo", name = "Moon halo", dark = true,
        background = c(0xFF0F1424), backgroundEnd = c(0xFF0B0F1B), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFF2F4FB), hint = c(0xFFB3BCD9), accent = c(0xFFD3DCFF), onAccent = c(0xFF1A2147),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x4DB8C8FF), 0.78f, -0.1f, 0.7f), Glow(c(0x1F6E7BFF), 0.1f, 1.1f, 0.6f)),
    )
    val NORTHERN_LIGHTS = KeyboardTheme(
        id = "builtin:northern-lights", name = "Northern lights", dark = true,
        background = c(0xFF08141A), backgroundEnd = c(0xFF0C1C24), key = c(0x1AFFFFFF), keyPressed = c(0x3DFFFFFF),
        functionKey = c(0x0FFFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFB5E3D0), accent = c(0xFF6BF0B4), onAccent = c(0xFF04291A),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f, glows = NORTHERN_GLOWS,
    )
    val DUSK_HAZE = KeyboardTheme(
        id = "builtin:dusk-haze", name = "Dusk haze", dark = true,
        background = c(0xFF171329), backgroundEnd = c(0xFF221A33), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFE0CBE0), accent = c(0xFFFFB3C9), onAccent = c(0xFF40142A),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x45FF8FB1), 0.5f, 1.15f, 0.85f), Glow(c(0x334F7BFF), 0f, -0.05f, 0.6f)),
    )

    // ---------------------------------------------------------------- dynamic, 2026-11-21
    // Colours that follow the clock, and slow drifting glows (see LiveThemes).
    private val NIGHT_LOOK = KeyboardTheme(
        id = "look:night", name = "Night", dark = true,
        background = c(0xFF0E1525), backgroundEnd = c(0xFF121B30), key = c(0xFF1C2639), keyPressed = c(0xFF2C3B58),
        functionKey = c(0xFF151D2F), text = c(0xFFE6ECF7), hint = c(0xFF8C99B3), accent = c(0xFF7AA2F7), onAccent = c(0xFF0E1525),
        shadow = false, keyRadiusDp = 14f,
    )
    private val DAWN_LOOK = NIGHT_LOOK.copy(
        background = c(0xFF231D3A), backgroundEnd = c(0xFF3D2A4A), key = c(0xFF342B4F), keyPressed = c(0xFF4A3E6B),
        functionKey = c(0xFF2B2443), text = c(0xFFF3ECFA), hint = c(0xFFBFAFD6), accent = c(0xFFF5B3C8), onAccent = c(0xFF3B1426),
    )
    private val MORNING_LOOK = NIGHT_LOOK.copy(
        dark = false, background = c(0xFFFDEEE4), backgroundEnd = c(0xFFE6EEFA), key = c(0xFFFFFFFF), keyPressed = c(0xFFF2DCCF),
        functionKey = c(0xFFF0E1D8), text = c(0xFF2B2230), hint = c(0xFF8C7A85), accent = c(0xFFC4532F), onAccent = c(0xFFFFFFFF),
    )
    private val NOON_LOOK = NIGHT_LOOK.copy(
        dark = false, background = c(0xFFE3F2FD), backgroundEnd = c(0xFFD3E6FA), key = c(0xFFFFFFFF), keyPressed = c(0xFFC5DDF5),
        functionKey = c(0xFFCFE3F7), text = c(0xFF13263A), hint = c(0xFF5F7A94), accent = c(0xFF2F6FB5), onAccent = c(0xFFFFFFFF),
    )
    private val AFTERNOON_LOOK = NIGHT_LOOK.copy(
        dark = false, background = c(0xFFFFF1DC), backgroundEnd = c(0xFFF9DFC4), key = c(0xFFFFFCF6), keyPressed = c(0xFFF4DDBA),
        functionKey = c(0xFFF3E0C6), text = c(0xFF33261A), hint = c(0xFF8F7458), accent = c(0xFFAD5A16), onAccent = c(0xFFFFFFFF),
    )
    private val DUSK_LOOK = NIGHT_LOOK.copy(
        background = c(0xFF261A36), backgroundEnd = c(0xFF452645), key = c(0xFF3A2A4D), keyPressed = c(0xFF533D69),
        functionKey = c(0xFF2F2340), text = c(0xFFF8ECF4), hint = c(0xFFC9A9C9), accent = c(0xFFFFB58A), onAccent = c(0xFF3A1A08),
    )

    /** Light by day, dark by night: dawn violet, morning peach, noon sky, golden afternoon, dusk plum, night blue. */
    val DAY_AND_NIGHT = NIGHT_LOOK.copy(
        id = "builtin:live-day-night", name = "Day & night",
        live = LiveStyle.TimeOfDay(
            listOf(
                TimeStop.at(0, 0, NIGHT_LOOK), TimeStop.at(5, 30, DAWN_LOOK), TimeStop.at(8, 0, MORNING_LOOK),
                TimeStop.at(12, 30, NOON_LOOK), TimeStop.at(16, 30, AFTERNOON_LOOK), TimeStop.at(18, 30, DUSK_LOOK),
                TimeStop.at(21, 0, NIGHT_LOOK),
            ),
        ),
    )

    private val SHIFT_DAY = KeyboardTheme(
        id = "look:shift-day", name = "Day", dark = true,
        background = c(0xFF1D232B), key = c(0xFF2B333D), keyPressed = c(0xFF3D4753), functionKey = c(0xFF242B34),
        text = c(0xFFE6EBF0), hint = c(0xFF97A3B0), accent = c(0xFF8EC0E8), onAccent = c(0xFF0F2638),
        shadow = false, keyRadiusDp = 12f,
    )
    private val SHIFT_DAWN = SHIFT_DAY.copy(
        background = c(0xFF1C2226), key = c(0xFF2A3237), keyPressed = c(0xFF3B464C), functionKey = c(0xFF232A2E),
        text = c(0xFFE8EEEE), hint = c(0xFF9AA8AA), accent = c(0xFF9DCFC8), onAccent = c(0xFF10302C),
    )
    private val SHIFT_EVENING = SHIFT_DAY.copy(
        background = c(0xFF221F2C), key = c(0xFF312C3E), keyPressed = c(0xFF453E56), functionKey = c(0xFF292535),
        text = c(0xFFEDE6F2), hint = c(0xFFA89DB8), accent = c(0xFFC7A6E8), onAccent = c(0xFF2A1640),
    )
    private val SHIFT_LATE = SHIFT_DAY.copy(
        background = c(0xFF1C1612), key = c(0xFF2C231C), keyPressed = c(0xFF40342A), functionKey = c(0xFF241D17),
        text = c(0xFFF0E2CF), hint = c(0xFFB09A80), accent = c(0xFFE6A867), onAccent = c(0xFF2E1A05),
    )

    /** Always dark: cool slate by day, violet in the evening, warm and low in blue light late at night. */
    val NIGHT_SHIFT = SHIFT_DAY.copy(
        id = "builtin:live-night-shift", name = "Night shift",
        live = LiveStyle.TimeOfDay(
            listOf(
                TimeStop.at(5, 0, SHIFT_LATE), TimeStop.at(6, 30, SHIFT_DAWN), TimeStop.at(8, 0, SHIFT_DAY),
                TimeStop.at(17, 0, SHIFT_DAY), TimeStop.at(20, 0, SHIFT_EVENING), TimeStop.at(22, 30, SHIFT_LATE),
            ),
        ),
    )

    val AURORA_DRIFT = NORTHERN_LIGHTS.copy(id = "builtin:live-aurora-drift", name = "Aurora drift", live = LiveStyle.Drift(periodSeconds = 52))
    val NEBULA_DRIFT = NEBULA.copy(id = "builtin:live-nebula-drift", name = "Nebula drift", live = LiveStyle.Drift(periodSeconds = 44))
    val TIDE_DRIFT = BIOLUMINESCENCE.copy(id = "builtin:live-tide-drift", name = "Tide drift", live = LiveStyle.Drift(periodSeconds = 60))

    // ---------------------------------------------------------------- chameleon, 2026-11-21
    // Colours from the app being typed in (AppTint). The colours stored here are the fallback
    // blue, used where no app colour is known.
    private fun chameleon(
        id: String,
        name: String,
        style: AppTintStyle,
        radius: Float = 14f,
        effect: PressEffect? = null,
        driftSeconds: Int = 0,
    ): KeyboardTheme {
        val shape = KeyboardTheme(
            id = id, name = name, dark = style != AppTintStyle.LIGHT && style != AppTintStyle.PASTEL,
            background = 0, key = 0, keyPressed = 0, functionKey = 0, text = 0, hint = 0, accent = 0, onAccent = 0,
            shadow = false, keyRadiusDp = radius, pressEffect = effect, live = LiveStyle.AppColour(style, driftSeconds),
        )
        return AppTint.apply(shape, AppTint.FALLBACK).copy(live = shape.live)
    }
    val CHAMELEON = chameleon("builtin:chameleon", "Chameleon", AppTintStyle.GLOW)
    val CHAMELEON_MIST = chameleon("builtin:chameleon-mist", "Chameleon mist", AppTintStyle.MIST)
    val CHAMELEON_LIGHT = chameleon("builtin:chameleon-light", "Chameleon light", AppTintStyle.LIGHT)
    val CHAMELEON_DUO = chameleon("builtin:chameleon-duo", "Chameleon duo", AppTintStyle.DUO)
    val CHAMELEON_SPOTLIGHT = chameleon("builtin:chameleon-spotlight", "Chameleon spotlight", AppTintStyle.SPOTLIGHT)
    val CHAMELEON_GLASS = chameleon("builtin:chameleon-glass", "Chameleon glass", AppTintStyle.GLASS)
    val CHAMELEON_BLACK = chameleon("builtin:chameleon-black", "Chameleon black", AppTintStyle.BLACK, radius = 12f)
    val CHAMELEON_NEON = chameleon("builtin:chameleon-neon", "Chameleon neon", AppTintStyle.NEON, radius = 10f)
    val CHAMELEON_PASTEL = chameleon("builtin:chameleon-pastel", "Chameleon pastel", AppTintStyle.PASTEL, radius = 16f)
    // Moving: drifting app-coloured glows, or a press effect in the app's colour.
    val CHAMELEON_DRIFT = chameleon("builtin:chameleon-drift", "Chameleon drift", AppTintStyle.GLOW, driftSeconds = 50)
    val CHAMELEON_RIPPLE = chameleon("builtin:chameleon-ripple", "Chameleon ripple", AppTintStyle.GLOW, effect = PressEffect.DROPLET)
    val CHAMELEON_SPARKLE = chameleon("builtin:chameleon-sparkle", "Chameleon sparkle", AppTintStyle.DUO, effect = PressEffect.SPARKLE)
    val CHAMELEON_INK = chameleon("builtin:chameleon-ink", "Chameleon ink", AppTintStyle.MIST, effect = PressEffect.INK)
    val CHAMELEON_HALO = chameleon("builtin:chameleon-halo", "Chameleon halo", AppTintStyle.NEON, radius = 10f, effect = PressEffect.HALO)
    val CHAMELEON_RUBBER = chameleon("builtin:chameleon-rubber", "Chameleon rubber", AppTintStyle.MIST, radius = 18f, effect = PressEffect.RUBBER)
    val CHAMELEON_JELLY = chameleon("builtin:chameleon-jelly", "Chameleon jelly", AppTintStyle.PASTEL, radius = 20f, effect = PressEffect.JELLY)
    val CHAMELEON_SQUISH = chameleon("builtin:chameleon-squish", "Chameleon squish", AppTintStyle.BLACK, radius = 16f, effect = PressEffect.SQUISH)

    // ---------------------------------------------------------------- key press effects (dynamic), 2026-11-21
    val NEON_HALO = KeyboardTheme(
        id = "builtin:fx-neon-ripple", name = "Neon halo", dark = true,
        background = c(0xFF04060A), key = c(0xFF0B0F16), keyPressed = c(0xFF16202C), functionKey = c(0xFF070A10),
        text = c(0xFFE6FBFF), hint = c(0xFF6FD8EE), accent = c(0xFF00D9F5), onAccent = c(0xFF00191E),
        border = c(0xFF0E7C8F), shadow = false, keyRadiusDp = 10f,
        pressEffect = PressEffect.HALO, effectColor = c(0xFF39E6FF),
    )
    val STARDUST = KeyboardTheme(
        id = "builtin:fx-stardust", name = "Stardust", dark = true,
        background = c(0xFF0B1026), backgroundEnd = c(0xFF151B3D), key = c(0xFF1A2147), keyPressed = c(0xFF283166),
        functionKey = c(0xFF131939), text = c(0xFFF1F3FF), hint = c(0xFFA9B2E0), accent = c(0xFFFFD27A), onAccent = c(0xFF2B1D00),
        shadow = false, keyRadiusDp = 12f,
        pressEffect = PressEffect.TRAIL, effectColor = c(0xFFFFE3A3),
    )
    val FIREFLIES = KeyboardTheme(
        id = "builtin:fx-fireflies", name = "Fireflies", dark = true,
        background = c(0xFF0B1610), backgroundEnd = c(0xFF102018), key = c(0xFF18291F), keyPressed = c(0xFF243B2D),
        functionKey = c(0xFF122019), text = c(0xFFEAF5E6), hint = c(0xFF9DBB98), accent = c(0xFFE8D36A), onAccent = c(0xFF2A2300),
        shadow = false, keyRadiusDp = 12f,
        pressEffect = PressEffect.MOTES, effectColor = c(0xFFFFE680),
    )
    val BACKLIT = KeyboardTheme(
        id = "builtin:fx-pulse", name = "Backlit", dark = true,
        background = c(0xFF140F22), backgroundEnd = c(0xFF1D1430), key = c(0xFF231A38), keyPressed = c(0xFF35284F),
        functionKey = c(0xFF1B142C), text = c(0xFFF5EEFF), hint = c(0xFFBBA8DE), accent = c(0xFFD58BFF), onAccent = c(0xFF2E0A45),
        shadow = false, keyRadiusDp = 14f,
        pressEffect = PressEffect.BACKLIGHT, effectColor = c(0xFFE7A6FF),
    )
    val TEAL_INK = KeyboardTheme(
        id = "builtin:fx-sonar", name = "Teal ink", dark = true,
        background = c(0xFF061A1C), key = c(0xFF0F2A2D), keyPressed = c(0xFF1A3E42), functionKey = c(0xFF0B2124),
        text = c(0xFFE2FAF7), hint = c(0xFF8CC7C0), accent = c(0xFF5EEAD4), onAccent = c(0xFF00291F),
        border = c(0xFF15403F), shadow = false, keyRadiusDp = 10f,
        pressEffect = PressEffect.INK, effectColor = c(0xFF5EEAD4),
    )
    val FLOAT = KeyboardTheme(
        id = "builtin:fx-float", name = "Float", dark = true,
        background = c(0xFF15191F), backgroundEnd = c(0xFF1A1F27), key = c(0xFF242A33), keyPressed = c(0xFF333B47),
        functionKey = c(0xFF1C2128), text = c(0xFFE8ECF2), hint = c(0xFF98A2B0), accent = c(0xFFA9C7F0), onAccent = c(0xFF14263F),
        shadow = false, keyRadiusDp = 12f,
        pressEffect = PressEffect.FLOAT, effectColor = c(0xFFCFE0FA),
    )

    // ---------------------------------------------------------------- more ambient, 2026-11-21
    val SAPPHIRE_GLOW = KeyboardTheme(
        id = "builtin:sapphire-glow", name = "Sapphire glow", dark = true,
        background = c(0xFF070D24), backgroundEnd = c(0xFF0B1433), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFF0F4FF), hint = c(0xFFB3C2EE), accent = c(0xFF8FB0FF), onAccent = c(0xFF0B1A4A),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x5C3D6BFF), 0.15f, 0f, 0.8f), Glow(c(0x3D3FD6FF), 0.95f, 1.05f, 0.6f)),
    )
    val ROSE_QUARTZ = KeyboardTheme(
        id = "builtin:rose-quartz", name = "Rose quartz", dark = true,
        background = c(0xFF1C1018), backgroundEnd = c(0xFF261522), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFE8C4D6), accent = c(0xFFFFA8C8), onAccent = c(0xFF45102A),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x4DFF7FAE), 0.85f, -0.05f, 0.75f), Glow(c(0x339B6BFF), 0.05f, 1.05f, 0.55f)),
    )
    val EMERALD_MIST = KeyboardTheme(
        id = "builtin:emerald-mist", name = "Emerald mist", dark = true,
        background = c(0xFF06160F), backgroundEnd = c(0xFF0A2016), key = c(0x1AFFFFFF), keyPressed = c(0x3DFFFFFF),
        functionKey = c(0x0FFFFFFF), text = c(0xFFEAFFF4), hint = c(0xFFA8D9BF), accent = c(0xFF5BE3A2), onAccent = c(0xFF032617),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x4D2FD08A), 0.3f, 1.1f, 0.85f), Glow(c(0x2E7FE0C0), 0.9f, -0.05f, 0.5f)),
    )
    val AMBER_HEARTH = KeyboardTheme(
        id = "builtin:amber-hearth", name = "Amber hearth", dark = true,
        background = c(0xFF17100A), backgroundEnd = c(0xFF21160D), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFFFF4E8), hint = c(0xFFE4C6A6), accent = c(0xFFFFB866), onAccent = c(0xFF3A1F00),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x4DFF9F43), 0.5f, 1.15f, 0.85f), Glow(c(0x26FF6B4A), 0.05f, -0.05f, 0.5f)),
    )
    val OCEAN_DEPTH = KeyboardTheme(
        id = "builtin:ocean-depth", name = "Ocean depth", dark = true,
        background = c(0xFF041420), backgroundEnd = c(0xFF06101A), key = c(0x1AFFFFFF), keyPressed = c(0x3DFFFFFF),
        functionKey = c(0x0FFFFFFF), text = c(0xFFE6F8FF), hint = c(0xFF9CCFE3), accent = c(0xFF5FD4F5), onAccent = c(0xFF002A38),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x4D2EC4E6), 0.6f, -0.1f, 0.85f), Glow(c(0x262E6BE6), 0f, 0.9f, 0.5f)),
    )
    val LAVENDER_HAZE = KeyboardTheme(
        id = "builtin:lavender-haze", name = "Lavender haze", dark = true,
        background = c(0xFF16122A), backgroundEnd = c(0xFF1F1938), key = c(0x1FFFFFFF), keyPressed = c(0x40FFFFFF),
        functionKey = c(0x12FFFFFF), text = c(0xFFFFFFFF), hint = c(0xFFD4C8F5), accent = c(0xFFCBB8FF), onAccent = c(0xFF261655),
        border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x4DB79CFF), 0.5f, 1.1f, 0.85f), Glow(c(0x2EFF9CE0), 0.9f, 0f, 0.5f)),
    )
    /** Light ambient: soft pastel glows behind frosted keys. */
    val MORNING_MIST = KeyboardTheme(
        id = "builtin:morning-mist", name = "Morning mist", dark = false,
        background = c(0xFFEEF3F9), backgroundEnd = c(0xFFE6EDF6), key = c(0xE6FFFFFF), keyPressed = c(0xFFDCE6F2),
        functionKey = c(0xB3FFFFFF), text = c(0xFF1C2633), hint = c(0xFF6B7A8C), accent = c(0xFF3D7BD9), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x66A8D4FF), 0.1f, 0f, 0.8f), Glow(c(0x4DFFC8A8), 0.9f, 1.05f, 0.7f)),
    )
    val PETAL = KeyboardTheme(
        id = "builtin:petal-glow", name = "Petal glow", dark = false,
        background = c(0xFFFBEFF4), backgroundEnd = c(0xFFF5E8F2), key = c(0xE6FFFFFF), keyPressed = c(0xFFF3D9E4),
        functionKey = c(0xB3FFFFFF), text = c(0xFF33202A), hint = c(0xFF94707F), accent = c(0xFFC2457A), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x59FFB3CF), 0.85f, -0.05f, 0.8f), Glow(c(0x40C9B3FF), 0.1f, 1.05f, 0.7f)),
    )
    val MINT_BREEZE = KeyboardTheme(
        id = "builtin:mint-breeze", name = "Mint breeze", dark = false,
        background = c(0xFFEAF7F2), backgroundEnd = c(0xFFE2F2EC), key = c(0xE6FFFFFF), keyPressed = c(0xFFD5EEE4),
        functionKey = c(0xB3FFFFFF), text = c(0xFF15302A), hint = c(0xFF5F8579), accent = c(0xFF1E8A6A), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x59A8F0D0), 0.15f, -0.05f, 0.8f), Glow(c(0x40A8D8FF), 0.9f, 1.05f, 0.7f)),
    )
    val PEACH_HAZE = KeyboardTheme(
        id = "builtin:peach-haze", name = "Peach haze", dark = false,
        background = c(0xFFFDF1EA), backgroundEnd = c(0xFFF9E9E2), key = c(0xE6FFFFFF), keyPressed = c(0xFFF6DDD0),
        functionKey = c(0xB3FFFFFF), text = c(0xFF3A251C), hint = c(0xFF9A7566), accent = c(0xFFC8553D), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 14f,
        glows = listOf(Glow(c(0x59FFC2A0), 0.85f, -0.05f, 0.8f), Glow(c(0x40FFD9A8), 0.1f, 1.05f, 0.7f)),
    )

    // ---------------------------------------------------------------- spotlight (ambient), 2026-11-21
    // Near-black with one strong light from an edge: a stage-lit look.
    private fun spotlight(id: String, name: String, glow: Glow, top: Long, bottom: Long, text: Long, hint: Long, accent: Long, onAccent: Long) =
        KeyboardTheme(
            id = id, name = name, dark = true,
            background = c(top), backgroundEnd = c(bottom), key = c(0x1AFFFFFF), keyPressed = c(0x3DFFFFFF),
            functionKey = c(0x0FFFFFFF), text = c(text), hint = c(hint), accent = c(accent), onAccent = c(onAccent),
            border = c(0x1AFFFFFF), shadow = false, keyRadiusDp = 14f, glows = listOf(glow),
        )
    val BLUE_SPOTLIGHT = spotlight(
        "builtin:spotlight-blue", "Blue spotlight", Glow(c(0x6640A0FF), 0.5f, -0.2f, 0.9f),
        0xFF06080F, 0xFF080B14, 0xFFF0F6FF, 0xFFA8C4EC, 0xFF7DB8FF, 0xFF04203F,
    )
    val VIOLET_SPOTLIGHT = spotlight(
        "builtin:spotlight-violet", "Violet spotlight", Glow(c(0x66A070FF), 0.15f, -0.15f, 0.9f),
        0xFF0A0710, 0xFF0D0916, 0xFFFFFFFF, 0xFFD0BCF2, 0xFFC9A6FF, 0xFF2A1250,
    )
    val TEAL_SPOTLIGHT = spotlight(
        "builtin:spotlight-teal", "Teal spotlight", Glow(c(0x5C2EE0C8), 0.85f, 1.2f, 0.95f),
        0xFF050C0D, 0xFF071012, 0xFFEAFFFC, 0xFFA0D8D0, 0xFF4FE8D0, 0xFF002A24,
    )
    val SUNBEAM = spotlight(
        "builtin:spotlight-sunbeam", "Sunbeam", Glow(c(0x52FFC36B), 0.2f, -0.15f, 0.9f),
        0xFF0E0A06, 0xFF120D08, 0xFFFFF6EA, 0xFFE8CBA4, 0xFFFFC46B, 0xFF3A2400,
    )

    // ---------------------------------------------------------------- more key press effects, 2026-11-21
    val BOUNCE = KeyboardTheme(
        id = "builtin:fx-bounce", name = "Bounce", dark = true,
        background = c(0xFF161B24), key = c(0xFF232B38), keyPressed = c(0xFF334052), functionKey = c(0xFF1C222D),
        text = c(0xFFE8EEF6), hint = c(0xFF97A4B6), accent = c(0xFF7FE3C4), onAccent = c(0xFF06291F),
        shadow = false, keyRadiusDp = 16f,
        pressEffect = PressEffect.POP, effectColor = c(0xFF9FF0D6),
    )
    val GLASS_SHEEN = KeyboardTheme(
        id = "builtin:fx-glass-sheen", name = "Glass sheen", dark = true,
        background = c(0xFF14202E), backgroundEnd = c(0xFF1C2D40), key = c(0x24FFFFFF), keyPressed = c(0x45FFFFFF),
        functionKey = c(0x14FFFFFF), text = c(0xFFF4F8FF), hint = c(0xFFC0D0E6), accent = c(0xFFBFD8FF), onAccent = c(0xFF13243D),
        border = c(0x2EFFFFFF), shadow = false, keyRadiusDp = 14f,
        pressEffect = PressEffect.SHEEN, effectColor = c(0xFFFFFFFF),
    )
    val TWINKLE = KeyboardTheme(
        id = "builtin:fx-twinkle", name = "Twinkle", dark = true,
        background = c(0xFF0A0F22), backgroundEnd = c(0xFF101833), key = c(0xFF18213F), keyPressed = c(0xFF253058),
        functionKey = c(0xFF121A33), text = c(0xFFF0F2FF), hint = c(0xFFA8B0DA), accent = c(0xFFFFE08A), onAccent = c(0xFF2E2400),
        shadow = false, keyRadiusDp = 12f,
        pressEffect = PressEffect.SPARKLE, effectColor = c(0xFFFFF0B8),
    )
    val RAINDROP = KeyboardTheme(
        id = "builtin:fx-raindrop", name = "Raindrop", dark = true,
        background = c(0xFF161D24), key = c(0xFF222C36), keyPressed = c(0xFF31404D), functionKey = c(0xFF1B242C),
        text = c(0xFFE6EEF4), hint = c(0xFF96A8B6), accent = c(0xFF8CC8F0), onAccent = c(0xFF0E2A3D),
        shadow = false, keyRadiusDp = 12f,
        pressEffect = PressEffect.DROPLET, effectColor = c(0xFFA8D8FF),
    )
    val HEARTBEAT = KeyboardTheme(
        id = "builtin:fx-heartbeat", name = "Heartbeat", dark = true,
        background = c(0xFF1E1018), backgroundEnd = c(0xFF2A1422), key = c(0xFF33202B), keyPressed = c(0xFF4A2E3E),
        functionKey = c(0xFF281820), text = c(0xFFFBEAF1), hint = c(0xFFD1A3B6), accent = c(0xFFFF8FB3), onAccent = c(0xFF3D0A1E),
        shadow = false, keyRadiusDp = 14f,
        pressEffect = PressEffect.HEARTS, effectColor = c(0xFFFF9EC0),
    )
    val NEON_LINE = KeyboardTheme(
        id = "builtin:fx-neon-line", name = "Neon line", dark = true,
        background = c(0xFF07060B), key = c(0xFF100E17), keyPressed = c(0xFF1E1A2B), functionKey = c(0xFF0A0910),
        text = c(0xFFF6EEFF), hint = c(0xFFB79CE8), accent = c(0xFFD36BFF), onAccent = c(0xFF1E0030),
        border = c(0xFF3A2A55), shadow = false, keyRadiusDp = 10f,
        pressEffect = PressEffect.UNDERLINE, effectColor = c(0xFFE08BFF),
    )

    // Soft rubber keys: the keys squash when pressed and wobble back when let go (RubberSpring).
    val RUBBER = KeyboardTheme(
        id = "builtin:fx-rubber", name = "Rubber", dark = true,
        background = c(0xFF1A1D22), key = c(0xFF2C3038), keyPressed = c(0xFF3A404A), functionKey = c(0xFF23272D),
        text = c(0xFFEDEFF3), hint = c(0xFF9AA0AB), accent = c(0xFF8FB8FF), onAccent = c(0xFF0F2347),
        shadow = true, keyRadiusDp = 18f,
        pressEffect = PressEffect.RUBBER, effectColor = c(0xFFFFFFFF),
    )
    val GUMMY = KeyboardTheme(
        id = "builtin:fx-gummy", name = "Gummy", dark = true,
        background = c(0xFF0F1E1C), key = c(0xFF1D3532), keyPressed = c(0xFF2A4A46), functionKey = c(0xFF162926),
        text = c(0xFFE6FBF6), hint = c(0xFF94C9BE), accent = c(0xFF6FE6C8), onAccent = c(0xFF032B22),
        shadow = true, keyRadiusDp = 20f,
        pressEffect = PressEffect.RUBBER, effectColor = c(0xFF9FF5DF),
    )
    val JELLY = KeyboardTheme(
        id = "builtin:fx-jelly", name = "Jelly", dark = false,
        background = c(0xFFEDE6FA), key = c(0xFFFFFFFF), keyPressed = c(0xFFE2D6F7), functionKey = c(0xFFE4DAF6),
        text = c(0xFF2A2140), hint = c(0xFF7C6F98), accent = c(0xFF7A5CD6), onAccent = c(0xFFFFFFFF),
        shadow = true, keyRadiusDp = 20f,
        pressEffect = PressEffect.JELLY, effectColor = c(0xFFB9A4FF),
    )

    // More bouncy keys, 2026-11-21: three spring personalities (Squish, Rubber, Jelly), dark and light.
    val BLUEBERRY = KeyboardTheme(
        id = "builtin:fx-blueberry", name = "Blueberry", dark = true,
        background = c(0xFF0F1530), key = c(0xFF1E2A52), keyPressed = c(0xFF2B3A6E), functionKey = c(0xFF172042),
        text = c(0xFFEEF1FF), hint = c(0xFFA3AEDD), accent = c(0xFF8FA8FF), onAccent = c(0xFF0F1A4A),
        shadow = true, keyRadiusDp = 20f,
        pressEffect = PressEffect.JELLY, effectColor = c(0xFFB8C8FF),
    )
    val GUM_DROP = KeyboardTheme(
        id = "builtin:fx-gum-drop", name = "Gum drop", dark = true,
        background = c(0xFF1C1020), key = c(0xFF341E3C), keyPressed = c(0xFF4A2C55), functionKey = c(0xFF28162E),
        text = c(0xFFFBEFFF), hint = c(0xFFD2A9DC), accent = c(0xFFF08BD8), onAccent = c(0xFF3D0A33),
        shadow = true, keyRadiusDp = 20f,
        pressEffect = PressEffect.RUBBER, effectColor = c(0xFFFFB3EA),
    )
    val LIME_JELLY = KeyboardTheme(
        id = "builtin:fx-lime-jelly", name = "Lime jelly", dark = true,
        background = c(0xFF101A0E), key = c(0xFF1F3119), keyPressed = c(0xFF2E4725), functionKey = c(0xFF182714),
        text = c(0xFFF0FBE8), hint = c(0xFFA8CC92), accent = c(0xFFB6F23A), onAccent = c(0xFF1C2A00),
        shadow = true, keyRadiusDp = 20f,
        pressEffect = PressEffect.JELLY, effectColor = c(0xFFD6FF8A),
    )
    /** OLED black with dark rubber keys, like a rubber keypad. */
    val RUBBER_BLACK = KeyboardTheme(
        id = "builtin:fx-rubber-black", name = "Rubber black", dark = true,
        background = c(0xFF000000), key = c(0xFF151515), keyPressed = c(0xFF262626), functionKey = c(0xFF0C0C0C),
        text = c(0xFFF2F2F2), hint = c(0xFF8C8C8C), accent = c(0xFFFF8A3D), onAccent = c(0xFF2B1100),
        border = c(0xFF222222), shadow = false, keyRadiusDp = 16f,
        pressEffect = PressEffect.RUBBER, effectColor = c(0xFFFFB27A),
    )
    val SOFT_SLATE = KeyboardTheme(
        id = "builtin:fx-soft-slate", name = "Soft slate", dark = true,
        background = c(0xFF1B1F26), key = c(0xFF2A303A), keyPressed = c(0xFF38404C), functionKey = c(0xFF222730),
        text = c(0xFFE9EDF2), hint = c(0xFF98A2AF), accent = c(0xFFA6C8E0), onAccent = c(0xFF12283A),
        shadow = true, keyRadiusDp = 18f,
        pressEffect = PressEffect.SQUISH, effectColor = c(0xFFD0E4F2),
    )
    /** Nebula's glows with jelly keys. */
    val BOUNCY_NEBULA = NEBULA.copy(
        id = "builtin:fx-bouncy-nebula", name = "Bouncy nebula", keyRadiusDp = 18f,
        pressEffect = PressEffect.JELLY, effectColor = c(0xFFD8C8FF),
    )
    val MARSHMALLOW = KeyboardTheme(
        id = "builtin:fx-marshmallow", name = "Marshmallow", dark = false,
        background = c(0xFFF8F1EC), key = c(0xFFFFFFFF), keyPressed = c(0xFFF1E3DA), functionKey = c(0xFFF0E6E0),
        text = c(0xFF3A2C28), hint = c(0xFF9C877E), accent = c(0xFFC95A6E), onAccent = c(0xFFFFFFFF),
        shadow = true, keyRadiusDp = 22f,
        pressEffect = PressEffect.SQUISH, effectColor = c(0xFFFFC4CF),
    )
    val CLOUD_PUFF = KeyboardTheme(
        id = "builtin:fx-cloud-puff", name = "Cloud puff", dark = false,
        background = c(0xFFEAF2FC), key = c(0xFFFFFFFF), keyPressed = c(0xFFDCE8F7), functionKey = c(0xFFE1EBF8),
        text = c(0xFF18263A), hint = c(0xFF6A7C94), accent = c(0xFF3F7CD6), onAccent = c(0xFFFFFFFF),
        shadow = true, keyRadiusDp = 22f,
        pressEffect = PressEffect.SQUISH, effectColor = c(0xFFBFD8FF),
    )
    val MOCHI = KeyboardTheme(
        id = "builtin:fx-mochi", name = "Mochi", dark = false,
        background = c(0xFFEEF2E4), key = c(0xFFFDFEF8), keyPressed = c(0xFFE1E9CF), functionKey = c(0xFFE4EAD6),
        text = c(0xFF23301A), hint = c(0xFF6E7E5E), accent = c(0xFF5B7F3A), onAccent = c(0xFFFFFFFF),
        shadow = true, keyRadiusDp = 24f,
        pressEffect = PressEffect.RUBBER, effectColor = c(0xFFD6E8B8),
    )
    val PEACH_JELLY = KeyboardTheme(
        id = "builtin:fx-peach-jelly", name = "Peach jelly", dark = false,
        background = c(0xFFFDEDE4), key = c(0xFFFFFFFF), keyPressed = c(0xFFF8D9C9), functionKey = c(0xFFF7DFD2),
        text = c(0xFF3B2620), hint = c(0xFFA07A6C), accent = c(0xFFC2533A), onAccent = c(0xFFFFFFFF),
        shadow = true, keyRadiusDp = 22f,
        pressEffect = PressEffect.JELLY, effectColor = c(0xFFFFC9AE),
    )

    // Glow backgrounds with a press effect on top.
    val GALAXY_SPARKLE = NEBULA.copy(id = "builtin:fx-galaxy-sparkle", name = "Galaxy sparkle", pressEffect = PressEffect.SPARKLE, effectColor = c(0xFFFFF0C8))
    val LAGOON_RIPPLE = BIOLUMINESCENCE.copy(id = "builtin:fx-lagoon-ripple", name = "Lagoon ripple", pressEffect = PressEffect.DROPLET, effectColor = c(0xFF7FF5DD))
    val ROSE_HEARTS = ROSE_QUARTZ.copy(id = "builtin:fx-rose-hearts", name = "Rose hearts", pressEffect = PressEffect.HEARTS, effectColor = c(0xFFFFA8C8))
    /** Drifting aurora glows and a light trail as you type. */
    val LIVING_AURORA = NORTHERN_LIGHTS.copy(
        id = "builtin:live-living-aurora", name = "Living aurora",
        live = LiveStyle.Drift(periodSeconds = 56), pressEffect = PressEffect.TRAIL, effectColor = c(0xFF9FF7CF),
    )

    // ---------------------------------------------------------------- more time of day and drift, 2026-11-21
    private val CYCLE_DAY = KeyboardTheme(
        id = "look:cycle-day", name = "Day", dark = true,
        background = c(0xFF1A1F33), key = c(0xFF272E4A), keyPressed = c(0xFF37406A), functionKey = c(0xFF20263E),
        text = c(0xFFE8EBFA), hint = c(0xFF9CA3C9), accent = c(0xFF9DB0FF), onAccent = c(0xFF141E52),
        shadow = false, keyRadiusDp = 14f,
    )
    private val CYCLE_DAWN = CYCLE_DAY.copy(
        background = c(0xFF1E2230), backgroundEnd = c(0xFF2E2536), key = c(0xFF2B2F42), keyPressed = c(0xFF3D4259),
        functionKey = c(0xFF242838), text = c(0xFFF1EDF5), hint = c(0xFFB5ABC4), accent = c(0xFFF7B8A8), onAccent = c(0xFF3D1A10),
    )
    private val CYCLE_EVENING = CYCLE_DAY.copy(
        background = c(0xFF2A1A35), backgroundEnd = c(0xFF3A1F45), key = c(0xFF3A2849), keyPressed = c(0xFF523A66),
        functionKey = c(0xFF31213E), text = c(0xFFF8EAFA), hint = c(0xFFCDA8D6), accent = c(0xFFF3A0E0), onAccent = c(0xFF3D0E33),
    )
    private val CYCLE_NIGHT = CYCLE_DAY.copy(
        background = c(0xFF0A0F1F), backgroundEnd = c(0xFF0E1428), key = c(0xFF161E35), keyPressed = c(0xFF232E4E),
        functionKey = c(0xFF10172A), text = c(0xFFE2E8F6), hint = c(0xFF8390B0), accent = c(0xFF7E9CE8), onAccent = c(0xFF0A1530),
    )

    /** Always dark: rose dawn, indigo day, magenta evening, deep navy night. */
    val TWILIGHT_CYCLE = CYCLE_DAY.copy(
        id = "builtin:live-twilight-cycle", name = "Twilight cycle",
        live = LiveStyle.TimeOfDay(
            listOf(
                TimeStop.at(4, 0, CYCLE_NIGHT), TimeStop.at(6, 0, CYCLE_DAWN), TimeStop.at(8, 0, CYCLE_DAY),
                TimeStop.at(16, 0, CYCLE_DAY), TimeStop.at(18, 30, CYCLE_EVENING), TimeStop.at(22, 0, CYCLE_NIGHT),
            ),
        ),
    )
    val EMBER_DRIFT = AMBER_HEARTH.copy(id = "builtin:live-ember-drift", name = "Ember drift", live = LiveStyle.Drift(periodSeconds = 50))
    val LAVENDER_DRIFT = LAVENDER_HAZE.copy(id = "builtin:live-lavender-drift", name = "Lavender drift", live = LiveStyle.Drift(periodSeconds = 54))

    /** The original gallery, before the grouped sets below. */
    val classic: List<KeyboardTheme> = listOf(
        LIGHT, DARK, AMOLED, MATERIAL_YOU_LIGHT, MATERIAL_YOU_DARK,
        CLOUD, POLAR, PEACH, CORAL, SKY, LAVENDER, MINT, PISTACHIO, MATCHA, VANILLA, HONEY, SAND,
        SAKURA, ROSE_GOLD, LAGOON, SEAFOAM, SUNRISE, PAPER, FJORD, MINIMAL, SLATE,
        TANGERINE, LEMONADE, BUBBLEGUM, AZURE, CANDY,
        OLIVE_NIGHT, MISTY_BLUE, ARCTIC_NIGHT, STORM, MOONLIGHT, SAGE_NIGHT, PERIWINKLE,
        GRAPHITE, LILAC_DUSK, BERRY, DUSK_ROSE, DEEP_SEA, MIDNIGHT, MOCHA, EMBER, MOSS, FROST, OCEAN, FOREST,
        GRAPE, EMERALD, SAPPHIRE, RUBY, TEAL, LIME, MANGO,
        TWILIGHT, COSMIC, AURORA, SUNSET, GLASS, NEON, HIGH_CONTRAST,
    )
    val coolDark: List<KeyboardTheme> = listOf(OBSIDIAN, GLACIER, INDIGO_INK, BLUE_STEEL, NORDIC_PINE, DEEP_SPACE)
    /** Themes that take the colour of the app being typed in. */
    val chameleon: List<KeyboardTheme> = listOf(
        CHAMELEON, CHAMELEON_MIST, CHAMELEON_DUO, CHAMELEON_SPOTLIGHT, CHAMELEON_GLASS,
        CHAMELEON_BLACK, CHAMELEON_NEON,
        CHAMELEON_LIGHT, CHAMELEON_PASTEL,
        CHAMELEON_DRIFT, CHAMELEON_RIPPLE, CHAMELEON_SPARKLE, CHAMELEON_INK, CHAMELEON_HALO, CHAMELEON_RUBBER, CHAMELEON_JELLY, CHAMELEON_SQUISH,
    )
    val ambient: List<KeyboardTheme> = listOf(
        NEBULA, BIOLUMINESCENCE, MOON_HALO, NORTHERN_LIGHTS, DUSK_HAZE,
        SAPPHIRE_GLOW, ROSE_QUARTZ, EMERALD_MIST, AMBER_HEARTH, OCEAN_DEPTH, LAVENDER_HAZE,
        MORNING_MIST, PETAL, MINT_BREEZE, PEACH_HAZE,
        BLUE_SPOTLIGHT, VIOLET_SPOTLIGHT, TEAL_SPOTLIGHT, SUNBEAM,
    )
    val dynamicThemes: List<KeyboardTheme> = listOf(
        NEON_HALO, STARDUST, FIREFLIES, BACKLIT, TEAL_INK, FLOAT,
        BOUNCE, GLASS_SHEEN, TWINKLE, RAINDROP, HEARTBEAT, NEON_LINE,
        RUBBER, GUMMY, JELLY, BLUEBERRY, GUM_DROP, LIME_JELLY, RUBBER_BLACK, SOFT_SLATE, BOUNCY_NEBULA,
        MARSHMALLOW, CLOUD_PUFF, MOCHI, PEACH_JELLY,
        GALAXY_SPARKLE, LAGOON_RIPPLE, ROSE_HEARTS, LIVING_AURORA,
        DAY_AND_NIGHT, NIGHT_SHIFT, TWILIGHT_CYCLE,
        AURORA_DRIFT, NEBULA_DRIFT, TIDE_DRIFT, EMBER_DRIFT, LAVENDER_DRIFT,
    )

    /** Every built-in theme except the festival ones. */
    val all: List<KeyboardTheme> = classic + coolDark + chameleon + ambient + dynamicThemes

    // ---------------------------------------------------------------- festivals (2026-11-08)
    // Shown on their own in Themes, and used for the day when "Festival themes" is on (see [Festivals]).

    /** পহেলা বৈশাখ: the red and white of the new year. */
    val BOISHAKH = KeyboardTheme(
        id = "builtin:festival-boishakh", name = "পহেলা বৈশাখ", dark = false,
        background = c(0xFFFFF6EE), backgroundEnd = c(0xFFFFE3D8), key = c(0xFFFFFFFF), keyPressed = c(0xFFF8CFC6),
        functionKey = c(0xFFF7D8CE), text = c(0xFF3A0F0A), hint = c(0xFFA8473B), accent = c(0xFFD7261E), onAccent = c(0xFFFFFFFF),
        keyRadiusDp = 12f,
    )

    /** ঈদ মোবারক: deep green and gold. */
    val EID = KeyboardTheme(
        id = "builtin:festival-eid", name = "ঈদ মোবারক", dark = true,
        background = c(0xFF0B3D2E), backgroundEnd = c(0xFF145A43), key = c(0xFF1B5A45), keyPressed = c(0xFF2A7259),
        functionKey = c(0xFF123F31), text = c(0xFFF7F0DA), hint = c(0xFFD9BC63), accent = c(0xFFD4AF37), onAccent = c(0xFF1F1600),
        shadow = false, keyRadiusDp = 12f,
    )

    /** একুশে ফেব্রুয়ারি (শহীদ দিবস): solemn charcoal with the red of the Shaheed Minar. */
    val EKUSHEY = KeyboardTheme(
        id = "builtin:festival-ekushey", name = "একুশে ফেব্রুয়ারি", dark = true,
        background = c(0xFF121212), backgroundEnd = c(0xFF1F1F1F), key = c(0xFF282828), keyPressed = c(0xFF3A3A3A),
        functionKey = c(0xFF1B1B1B), text = c(0xFFF3F3F3), hint = c(0xFFBDBDBD), accent = c(0xFFC62828), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 10f,
    )

    /** বিজয় দিবস and স্বাধীনতা দিবস: the green and red of the flag. */
    val BIJOY = KeyboardTheme(
        id = "builtin:festival-bijoy", name = "বিজয় দিবস", dark = true,
        background = c(0xFF006A4E), backgroundEnd = c(0xFF00553F), key = c(0xFF0F7B5D), keyPressed = c(0xFF1B9572),
        functionKey = c(0xFF005A42), text = c(0xFFFFFFFF), hint = c(0xFFCDEBDC), accent = c(0xFFF42A41), onAccent = c(0xFFFFFFFF),
        shadow = false, keyRadiusDp = 12f,
    )

    /** রমজান: night blue and lantern gold. */
    val RAMADAN = KeyboardTheme(
        id = "builtin:festival-ramadan", name = "রমজান", dark = true,
        background = c(0xFF0E1630), backgroundEnd = c(0xFF1B2754), key = c(0xFF202C57), keyPressed = c(0xFF2D3C72),
        functionKey = c(0xFF151F43), text = c(0xFFF5EFDD), hint = c(0xFFCDB66E), accent = c(0xFFE3C565), onAccent = c(0xFF1C1500),
        shadow = false, keyRadiusDp = 12f,
    )

    val festivals: List<KeyboardTheme> = listOf(BOISHAKH, EKUSHEY, BIJOY, RAMADAN, EID)

    fun byId(id: String): KeyboardTheme? = all.firstOrNull { it.id == id } ?: festivals.firstOrNull { it.id == id }

    /**
     * What the "System" theme (the default on a fresh install) draws: Material You in the phone's
     * light or dark mode. Wallpaper colours on Android 12+, the plain Light/Dark colours before that.
     */
    fun forSystem(dark: Boolean): KeyboardTheme = if (dark) MATERIAL_YOU_DARK else MATERIAL_YOU_LIGHT

    /** Jolpai's default theme ([KeyboardTheme.DEFAULT_ID]): Olive night, matching the app icon. */
    val DEFAULT: KeyboardTheme get() = OLIVE_NIGHT
}

/** Theme import/export as JSON, so themes can be shared and backed up. */
object ThemeCodec {
    // "lipi" is the app's first name; kept so themes shared from older versions still import.
    private const val FORMAT = "lipi-theme-1"

    fun encode(t: KeyboardTheme): String = encodeObject(t, withLive = true).toString()

    /**
     * [t] as JSON. With [withLive], how the theme changes by itself is included too: wallpaper
     * colours (`dynamic`), and `live`: app colour (Chameleon), drifting glows, or time-of-day
     * stops (each stop's colours written the same way, without a `live` of their own).
     */
    private fun encodeObject(t: KeyboardTheme, withLive: Boolean): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("name", t.name)
        put("dark", t.dark)
        put("background", hex(t.background))
        t.backgroundEnd?.let { put("backgroundEnd", hex(it)) }
        put("key", hex(t.key))
        put("keyPressed", hex(t.keyPressed))
        put("functionKey", hex(t.functionKey))
        put("text", hex(t.text))
        put("hint", hex(t.hint))
        put("accent", hex(t.accent))
        put("onAccent", hex(t.onAccent))
        t.border?.let { put("border", hex(it)) }
        put("shadow", t.shadow)
        put("keyRadiusDp", t.keyRadiusDp)
        put("keyGapDp", t.keyGapDp)
        put("keyOpacity", t.keyOpacity)
        put("labelScale", t.labelScale)
        put("boldLabels", t.boldLabels)
        put("imageDim", t.imageDim)
        if (t.glows.isNotEmpty()) putJsonArray("glows") {
            t.glows.forEach { g ->
                addJsonObject {
                    put("color", hex(g.color))
                    put("x", g.x)
                    put("y", g.y)
                    put("radius", g.radius)
                }
            }
        }
        t.pressEffect?.let { put("pressEffect", it.name) }
        t.effectColor?.let { put("effectColor", hex(it)) }
        if (withLive) {
            if (t.dynamic) put("dynamic", true)
            when (val live = t.live) {
                is LiveStyle.AppColour -> putJsonObject("live") {
                    put("type", "appColour")
                    put("style", live.style.name)
                    if (live.driftSeconds > 0) put("drift", live.driftSeconds)
                }
                is LiveStyle.Drift -> putJsonObject("live") {
                    put("type", "drift")
                    put("period", live.periodSeconds)
                }
                is LiveStyle.TimeOfDay -> putJsonObject("live") {
                    put("type", "timeOfDay")
                    putJsonArray("stops") {
                        live.stops.forEach { s ->
                            addJsonObject {
                                put("minute", s.minute)
                                put("look", encodeObject(s.look, withLive = false))
                            }
                        }
                    }
                }
                null -> Unit
            }
        }
    }

    /**
     * Reads a theme exported by [encode]. The background image is not part of the export
     * (it is a private file). Throws [IllegalArgumentException] for anything that isn't a theme.
     */
    fun decode(json: String, id: String): KeyboardTheme {
        val o: JsonObject = runCatching { Json.parseToJsonElement(json.trim()).jsonObject }
            .getOrElse { throw IllegalArgumentException("That isn't a Jolpai theme.") }
        require(o["format"]?.jsonPrimitive?.content == FORMAT) { "That isn't a Jolpai theme." }
        return decodeObject(o, id, withLive = true)
    }

    private fun decodeObject(o: JsonObject, id: String, withLive: Boolean): KeyboardTheme {
        fun color(k: String): Int = parseHex(o[k]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing colour \"$k\"."))
        fun optColor(k: String): Int? = o[k]?.jsonPrimitive?.content?.let(::parseHex)
        fun num(k: String, d: Float): Float = (o[k] as? JsonPrimitive)?.floatOrNull ?: d
        fun flag(k: String, d: Boolean): Boolean = (o[k] as? JsonPrimitive)?.booleanOrNull ?: d
        return KeyboardTheme(
            id = id,
            name = o["name"]?.jsonPrimitive?.content?.take(40)?.ifBlank { null } ?: "Imported theme",
            dark = flag("dark", false),
            background = color("background"),
            backgroundEnd = optColor("backgroundEnd"),
            key = color("key"),
            keyPressed = color("keyPressed"),
            functionKey = color("functionKey"),
            text = color("text"),
            hint = color("hint"),
            accent = color("accent"),
            onAccent = color("onAccent"),
            border = optColor("border"),
            shadow = flag("shadow", true),
            keyRadiusDp = num("keyRadiusDp", 7f),
            keyGapDp = num("keyGapDp", 6f),
            keyOpacity = num("keyOpacity", 1f),
            labelScale = num("labelScale", 1f),
            boldLabels = flag("boldLabels", false),
            imageDim = num("imageDim", 0.35f),
            glows = glows(o["glows"]),
            // An effect this version doesn't know is dropped rather than failing the import.
            pressEffect = o["pressEffect"]?.jsonPrimitive?.content?.let { n -> PressEffect.entries.firstOrNull { it.name == n } },
            effectColor = runCatching { optColor("effectColor") }.getOrNull(),
            dynamic = withLive && flag("dynamic", false),
            // Something this version can't read (a newer kind, a broken stop) leaves the theme still.
            live = if (withLive) runCatching { live(o["live"]) }.getOrNull() else null,
        ).sanitized()
    }

    /** How the theme changes by itself, from an export; null for a still theme. */
    private fun live(e: kotlinx.serialization.json.JsonElement?): LiveStyle? {
        val o = e as? JsonObject ?: return null
        fun int(k: String) = (o[k] as? JsonPrimitive)?.content?.toIntOrNull()
        return when (o["type"]?.jsonPrimitive?.content) {
            "appColour" -> LiveStyle.AppColour(
                AppTintStyle.valueOf(o["style"]!!.jsonPrimitive.content),
                driftSeconds = int("drift") ?: 0,
            )
            "drift" -> LiveStyle.Drift(int("period") ?: 48)
            "timeOfDay" -> {
                val stops = (o["stops"] as JsonArray).map { s ->
                    val so = s.jsonObject
                    TimeStop((so["minute"] as JsonPrimitive).content.toInt(), decodeObject(so["look"]!!.jsonObject, "", withLive = false))
                }
                if (stops.isEmpty()) null else LiveStyle.TimeOfDay(stops)
            }
            else -> null
        }
    }

    /** Glows from an export; anything that isn't a well-formed glow is skipped. */
    private fun glows(e: kotlinx.serialization.json.JsonElement?): List<Glow> = (e as? JsonArray).orEmpty().mapNotNull { g ->
        runCatching {
            val o = g.jsonObject
            fun f(k: String) = (o[k] as JsonPrimitive).floatOrNull!!
            Glow(parseHex(o["color"]!!.jsonPrimitive.content), f("x"), f("y"), f("radius"))
        }.getOrNull()
    }

    fun hex(color: Int): String = "#%08X".format(color)

    fun parseHex(s: String): Int {
        val h = s.trim().removePrefix("#")
        require(h.length == 6 || h.length == 8) { "\"$s\" isn't a colour like #RRGGBB." }
        val v = h.toLongOrNull(16) ?: throw IllegalArgumentException("\"$s\" isn't a colour like #RRGGBB.")
        return if (h.length == 6) (0xFF000000 or v).toInt() else v.toInt()
    }
}
