package com.yeamin.jolpai.theme

/** The broad kinds of theme the gallery can be filtered by. */
enum class ThemeFamily(val label: String) {
    BASICS("Basics"),
    /** Themes that take the colour of the app you're typing in. */
    CHAMELEON("Chameleon"),
    DARK("Dark"),
    LIGHT("Light"),
    AMBIENT("Ambient"),
    DYNAMIC("Dynamic"),
}

/**
 * A named type of theme ("Soft dark", "Key press effects"…): similar themes shown together in
 * the gallery under this [name], so it's easy to see what kind of theme each one is.
 */
data class ThemeType(
    val name: String,
    val family: ThemeFamily,
    /** One line about what these themes have in common, or null. */
    val description: String?,
    val themes: List<KeyboardTheme>,
)

/** Every built-in theme (except festivals) sorted into exactly one [ThemeType]. */
object ThemeTypes {
    private val b = BuiltInThemes

    val all: List<ThemeType> = listOf(
        ThemeType(
            "Essentials", ThemeFamily.BASICS, "Plain, wallpaper-coloured and high-contrast",
            listOf(b.LIGHT, b.DARK, b.AMOLED, b.MATERIAL_YOU_LIGHT, b.MATERIAL_YOU_DARK, b.MINIMAL, b.SLATE, b.HIGH_CONTRAST),
        ),
        ThemeType(
            "Tinted dark", ThemeFamily.CHAMELEON, "Dark glows, washes and glass in the app's colour",
            listOf(b.CHAMELEON, b.CHAMELEON_MIST, b.CHAMELEON_DUO, b.CHAMELEON_SPOTLIGHT, b.CHAMELEON_GLASS),
        ),
        ThemeType(
            "Black & neon", ThemeFamily.CHAMELEON, "Black keys; the app's colour in the outlines and Enter",
            listOf(b.CHAMELEON_BLACK, b.CHAMELEON_NEON),
        ),
        ThemeType(
            "Tinted light", ThemeFamily.CHAMELEON, "Light, with a soft wash or pastel of the app's colour",
            listOf(b.CHAMELEON_LIGHT, b.CHAMELEON_PASTEL),
        ),
        ThemeType(
            "Moving chameleon", ThemeFamily.CHAMELEON, "App-coloured glows that drift, or a press effect in the app's colour",
            listOf(b.CHAMELEON_DRIFT, b.CHAMELEON_RIPPLE, b.CHAMELEON_SPARKLE, b.CHAMELEON_INK, b.CHAMELEON_HALO, b.CHAMELEON_RUBBER, b.CHAMELEON_JELLY, b.CHAMELEON_SQUISH),
        ),
        ThemeType(
            "Soft dark", ThemeFamily.DARK, "Muted darks with gentle contrast, for low light",
            listOf(b.OLIVE_NIGHT, b.MISTY_BLUE, b.ARCTIC_NIGHT, b.STORM, b.MOONLIGHT, b.SAGE_NIGHT, b.PERIWINKLE),
        ),
        ThemeType(
            "Cool dark", ThemeFamily.DARK, "Blue, teal and slate darks with pastel accents",
            b.coolDark,
        ),
        ThemeType(
            "Classic dark", ThemeFamily.DARK, "Warm and deep darks",
            listOf(b.GRAPHITE, b.LILAC_DUSK, b.BERRY, b.DUSK_ROSE, b.DEEP_SEA, b.MIDNIGHT, b.MOCHA, b.EMBER, b.MOSS, b.FROST, b.OCEAN, b.FOREST),
        ),
        ThemeType(
            "Jewel tones", ThemeFamily.DARK, "Rich, saturated darks",
            listOf(b.GRAPE, b.EMERALD, b.SAPPHIRE, b.RUBY, b.TEAL, b.LIME, b.MANGO),
        ),
        ThemeType(
            "Light & pastel", ThemeFamily.LIGHT, "Soft, low-glare light colours",
            listOf(
                b.CLOUD, b.POLAR, b.PEACH, b.CORAL, b.SKY, b.LAVENDER, b.MINT, b.PISTACHIO, b.MATCHA, b.VANILLA,
                b.HONEY, b.SAND, b.SAKURA, b.ROSE_GOLD, b.LAGOON, b.SEAFOAM, b.SUNRISE, b.PAPER, b.FJORD,
            ),
        ),
        ThemeType(
            "Bright & colourful", ThemeFamily.LIGHT, "Saturated light colours",
            listOf(b.TANGERINE, b.LEMONADE, b.BUBBLEGUM, b.AZURE, b.CANDY),
        ),
        ThemeType(
            "Gradient & glass", ThemeFamily.AMBIENT, "Colour gradients, see-through and glowing keys",
            listOf(b.TWILIGHT, b.COSMIC, b.AURORA, b.SUNSET, b.GLASS, b.NEON),
        ),
        ThemeType(
            "Glow", ThemeFamily.AMBIENT, "Soft lights behind lightly see-through keys",
            listOf(
                b.NEBULA, b.BIOLUMINESCENCE, b.MOON_HALO, b.NORTHERN_LIGHTS, b.DUSK_HAZE,
                b.SAPPHIRE_GLOW, b.ROSE_QUARTZ, b.EMERALD_MIST, b.AMBER_HEARTH, b.OCEAN_DEPTH, b.LAVENDER_HAZE,
            ),
        ),
        ThemeType(
            "Spotlight", ThemeFamily.AMBIENT, "Near-black with one strong light from an edge, like a lit stage",
            listOf(b.BLUE_SPOTLIGHT, b.VIOLET_SPOTLIGHT, b.TEAL_SPOTLIGHT, b.SUNBEAM),
        ),
        ThemeType(
            "Light glow", ThemeFamily.AMBIENT, "Pastel glows behind frosted keys, for daylight",
            listOf(b.MORNING_MIST, b.PETAL, b.MINT_BREEZE, b.PEACH_HAZE),
        ),
        ThemeType(
            "Key glow effects", ThemeFamily.DYNAMIC,
            "The key you press lights up, ripples, glints or bounces, quickly and softly, never covering the next key. Try them on the keyboard above",
            listOf(b.NEON_HALO, b.BACKLIT, b.TEAL_INK, b.BOUNCE, b.GLASS_SHEEN, b.RAINDROP, b.NEON_LINE),
        ),
        ThemeType(
            "Bouncy dark", ThemeFamily.DYNAMIC,
            "Soft rubber keys that squash under your finger and spring back: Squish is gentle, Rubber bouncy, Jelly wobbliest",
            listOf(b.RUBBER, b.GUMMY, b.GUM_DROP, b.BLUEBERRY, b.LIME_JELLY, b.SOFT_SLATE, b.RUBBER_BLACK, b.BOUNCY_NEBULA),
        ),
        ThemeType(
            "Bouncy light", ThemeFamily.DYNAMIC, "The same soft, springy keys on light, pastel colours",
            listOf(b.JELLY, b.MARSHMALLOW, b.CLOUD_PUFF, b.MOCHI, b.PEACH_JELLY),
        ),
        ThemeType(
            "Playful effects", ThemeFamily.DYNAMIC,
            "Little things rise from the key you press: your letter, a heart, motes, twinkles, or a trail between keys",
            listOf(b.FLOAT, b.HEARTBEAT, b.FIREFLIES, b.TWINKLE, b.STARDUST),
        ),
        ThemeType(
            "Glow + effects", ThemeFamily.DYNAMIC, "Ambient glows with a press effect on top",
            listOf(b.GALAXY_SPARKLE, b.LAGOON_RIPPLE, b.ROSE_HEARTS, b.LIVING_AURORA),
        ),
        ThemeType(
            "Time of day", ThemeFamily.DYNAMIC, "Colours follow the clock; they change when the keyboard opens",
            listOf(b.DAY_AND_NIGHT, b.NIGHT_SHIFT, b.TWILIGHT_CYCLE),
        ),
        ThemeType(
            "Drifting glow", ThemeFamily.DYNAMIC, "Glows that drift slowly while the keyboard is on screen; still with battery saver",
            listOf(b.AURORA_DRIFT, b.NEBULA_DRIFT, b.TIDE_DRIFT, b.EMBER_DRIFT, b.LAVENDER_DRIFT),
        ),
    )

    /** The types in [family], in gallery order. */
    fun inFamily(family: ThemeFamily): List<ThemeType> = all.filter { it.family == family }

    /** The type of the built-in theme [id], or null (custom, festival, "System"). */
    fun typeOf(id: String): ThemeType? = all.firstOrNull { t -> t.themes.any { it.id == id } }
}
