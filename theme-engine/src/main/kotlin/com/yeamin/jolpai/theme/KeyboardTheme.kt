package com.yeamin.jolpai.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
) {
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    /** Applies the limits the keyboard can draw sensibly. */
    fun sanitized(): KeyboardTheme = copy(
        keyRadiusDp = keyRadiusDp.coerceIn(0f, 24f),
        keyGapDp = keyGapDp.coerceIn(0f, 16f),
        keyOpacity = keyOpacity.coerceIn(0.2f, 1f),
        labelScale = labelScale.coerceIn(0.7f, 1.4f),
        imageDim = imageDim.coerceIn(0f, 0.9f),
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

    val all: List<KeyboardTheme> = listOf(
        LIGHT, DARK, AMOLED, MATERIAL_YOU_LIGHT, MATERIAL_YOU_DARK,
        CLOUD, POLAR, PEACH, CORAL, SKY, LAVENDER, MINT, PISTACHIO, MATCHA, VANILLA, HONEY, SAND,
        SAKURA, ROSE_GOLD, LAGOON, SEAFOAM, SUNRISE, PAPER, FJORD, MINIMAL, SLATE,
        TANGERINE, LEMONADE, BUBBLEGUM, AZURE, CANDY,
        OLIVE_NIGHT, MISTY_BLUE, ARCTIC_NIGHT, STORM, MOONLIGHT, SAGE_NIGHT, PERIWINKLE,
        GRAPHITE, LILAC_DUSK, BERRY, DUSK_ROSE, DEEP_SEA, MIDNIGHT, MOCHA, EMBER, MOSS, FROST, OCEAN, FOREST,
        GRAPE, EMERALD, SAPPHIRE, RUBY, TEAL, LIME, MANGO,
        TWILIGHT, COSMIC, AURORA, SUNSET, GLASS, NEON, HIGH_CONTRAST,
    )

    fun byId(id: String): KeyboardTheme? = all.firstOrNull { it.id == id }

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

    fun encode(t: KeyboardTheme): String = buildJsonObject {
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
    }.toString()

    /**
     * Reads a theme exported by [encode]. The background image is not part of the export
     * (it is a private file). Throws [IllegalArgumentException] for anything that isn't a theme.
     */
    fun decode(json: String, id: String): KeyboardTheme {
        val o: JsonObject = runCatching { Json.parseToJsonElement(json.trim()).jsonObject }
            .getOrElse { throw IllegalArgumentException("That isn't a Jolpai theme.") }
        require(o["format"]?.jsonPrimitive?.content == FORMAT) { "That isn't a Jolpai theme." }
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
        ).sanitized()
    }

    fun hex(color: Int): String = "#%08X".format(color)

    fun parseHex(s: String): Int {
        val h = s.trim().removePrefix("#")
        require(h.length == 6 || h.length == 8) { "\"$s\" isn't a colour like #RRGGBB." }
        val v = h.toLongOrNull(16) ?: throw IllegalArgumentException("\"$s\" isn't a colour like #RRGGBB.")
        return if (h.length == 6) (0xFF000000 or v).toInt() else v.toInt()
    }
}
