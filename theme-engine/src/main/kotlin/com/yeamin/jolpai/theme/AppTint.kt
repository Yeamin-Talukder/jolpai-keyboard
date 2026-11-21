package com.yeamin.jolpai.theme

/** How a [LiveStyle.AppColour] theme uses the colour of the app being typed in. */
enum class AppTintStyle {
    /** Near-black, with the app's colour as glows behind see-through keys. */
    GLOW,
    /** A deep wash of the app's colour, with solid keys in a darker shade of it. */
    MIST,
    /** Light: a pale wash of the app's colour, white keys, the colour as the accent. */
    LIGHT,
    /** Pure black (OLED), the app's colour only in outlines, hints and Enter. */
    BLACK,
    /** Near-black keys with bright outlines in the app's colour. */
    NEON,
    /** Near-black lit from the top by one strong light in the app's colour. */
    SPOTLIGHT,
    /** The app's colour and its opposite as two glows. */
    DUO,
    /** A rich gradient of the app's colour behind frosted see-through keys. */
    GLASS,
    /** Light pastel keys and background in the app's colour. */
    PASTEL,
}

/**
 * Chameleon themes: the keyboard takes on the main colour of the app you are typing in.
 *
 * A keyboard can't see the screen (only screen recording can, which Android allows only with a
 * consent prompt and a recording icon each time), so the colour comes from the app's icon, which
 * almost always carries the app's own colour (WhatsApp green, Messenger blue, YouTube red). The
 * icon is read on the phone; nothing leaves it. Plain Kotlin, unit-tested on the PC.
 */
object AppTint {
    /** Colour used where no app colour is known (and for previews): a calm blue. */
    const val FALLBACK = 0xFF4F7DD9.toInt()

    /** Below this saturation an icon counts as grey (black-and-white icons get a neutral keyboard). */
    private const val MIN_SATURATION = 0.18f
    private const val HUE_BUCKETS = 24
    private const val MIN_LABEL_CONTRAST = 4.5
    private const val MIN_ACCENT_CONTRAST = 4.5

    /**
     * The main colour of an icon's [pixels] (ARGB), or null when it has no clear colour (grey,
     * black and white, or nearly empty). Transparent, near-white, near-black and grey pixels are
     * skipped; the rest vote by hue, weighted by how colourful they are, and the winning hue's
     * pixels are averaged.
     */
    fun dominantColour(pixels: IntArray): Int? {
        val weight = FloatArray(HUE_BUCKETS)
        val sumR = FloatArray(HUE_BUCKETS)
        val sumG = FloatArray(HUE_BUCKETS)
        val sumB = FloatArray(HUE_BUCKETS)
        var opaque = 0
        var colourful = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            opaque++
            val hsl = ThemeColors.toHsl(p)
            val s = hsl[1]
            val l = hsl[2]
            if (s < MIN_SATURATION || l < 0.12f || l > 0.92f) continue
            colourful++
            val bucket = (hsl[0] / 360f * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            val w = s * (1f - Math.abs(l - 0.5f))
            weight[bucket] += w
            sumR[bucket] += ((p shr 16) and 0xFF) * w
            sumG[bucket] += ((p shr 8) and 0xFF) * w
            sumB[bucket] += (p and 0xFF) * w
        }
        // A few coloured pixels in a black-and-white icon don't make it a coloured app.
        if (opaque == 0 || colourful < opaque * 0.06f) return null
        // Neighbouring buckets count together, so a hue on a bucket edge isn't split in two.
        var best = -1
        var bestScore = 0f
        for (i in 0 until HUE_BUCKETS) {
            val score = weight[i] + 0.5f * (weight[(i + 1) % HUE_BUCKETS] + weight[(i + HUE_BUCKETS - 1) % HUE_BUCKETS])
            if (score > bestScore) { bestScore = score; best = i }
        }
        if (best < 0 || weight[best] == 0f) return null
        val w = weight[best]
        fun ch(v: Float) = Math.round(v / w).coerceIn(0, 255)
        return (0xFF shl 24) or (ch(sumR[best]) shl 16) or (ch(sumG[best]) shl 8) or ch(sumB[best])
    }

    /**
     * [theme] (an app-colour theme) coloured for an app whose main colour is [appColour]
     * (null: no clear colour, which gives a neutral version). The result keeps the theme's id,
     * name, key shapes and press effect (now in the app's colour); it no longer depends on the
     * app ([KeyboardTheme.live] is null, or drifting glows for a drifting Chameleon). Labels
     * always keep 4.5:1 on the keys, even over the brightest glow, whatever the colour.
     */
    fun apply(theme: KeyboardTheme, appColour: Int?): KeyboardTheme {
        val style = (theme.live as? LiveStyle.AppColour)?.style ?: return theme
        val hsl = appColour?.let { ThemeColors.toHsl(it) }
        val h = hsl?.get(0) ?: 220f
        // Grey icons give grey keyboards; colourful ones keep a pleasant, not garish, strength.
        val s = if (hsl == null || hsl[1] < MIN_SATURATION) 0f else hsl[1].coerceIn(0.45f, 0.85f)
        val tinted = when (style) {
            AppTintStyle.GLOW -> theme.copy(
                dark = true,
                background = ThemeColors.fromHsl(h, s * 0.35f, 0.07f),
                backgroundEnd = ThemeColors.fromHsl(h, s * 0.45f, 0.11f),
                key = 0x1FFFFFFF, keyPressed = 0x40FFFFFF, functionKey = 0x12FFFFFF, border = 0x1AFFFFFF,
                text = ThemeColors.fromHsl(h, s * 0.2f, 0.97f),
                hint = ThemeColors.fromHsl(h, s * 0.35f, 0.80f),
                accent = ThemeColors.fromHsl(h, s, 0.72f),
                onAccent = ThemeColors.fromHsl(h, s * 0.6f, 0.12f),
                glows = listOf(
                    Glow(ThemeColors.fromHsl(h, s, 0.55f, alpha = 0x5C), 0.2f, -0.05f, 0.8f),
                    Glow(ThemeColors.fromHsl(h + 35f, s, 0.5f, alpha = 0x3D), 0.9f, 1.05f, 0.6f),
                ),
            )
            AppTintStyle.MIST -> theme.copy(
                dark = true,
                background = ThemeColors.fromHsl(h, s * 0.5f, 0.13f),
                backgroundEnd = ThemeColors.fromHsl(h, s * 0.55f, 0.18f),
                key = ThemeColors.fromHsl(h, s * 0.35f, 0.22f),
                keyPressed = ThemeColors.fromHsl(h, s * 0.35f, 0.30f),
                functionKey = ThemeColors.fromHsl(h, s * 0.4f, 0.17f),
                border = null,
                text = ThemeColors.fromHsl(h, s * 0.25f, 0.95f),
                hint = ThemeColors.fromHsl(h, s * 0.3f, 0.74f),
                accent = ThemeColors.fromHsl(h, s, 0.72f),
                onAccent = ThemeColors.fromHsl(h, s * 0.6f, 0.13f),
                glows = listOf(Glow(ThemeColors.fromHsl(h, s, 0.5f, alpha = 0x38), 0.5f, 1.1f, 0.9f)),
            )
            AppTintStyle.LIGHT -> theme.copy(
                dark = false,
                background = ThemeColors.fromHsl(h, s * 0.55f, 0.93f),
                backgroundEnd = ThemeColors.fromHsl(h, s * 0.6f, 0.88f),
                key = 0xFFFFFFFF.toInt(),
                keyPressed = ThemeColors.fromHsl(h, s * 0.5f, 0.86f),
                functionKey = ThemeColors.fromHsl(h, s * 0.45f, 0.89f),
                border = null,
                text = ThemeColors.fromHsl(h, s * 0.4f, 0.12f),
                hint = ThemeColors.fromHsl(h, s * 0.25f, 0.45f),
                accent = ThemeColors.fromHsl(h, s, 0.36f),
                onAccent = 0xFFFFFFFF.toInt(),
                glows = listOf(Glow(ThemeColors.fromHsl(h, s, 0.78f, alpha = 0x4D), 0.85f, -0.1f, 0.7f)),
            )
            AppTintStyle.BLACK -> theme.copy(
                // Pure black for OLED screens; the app's colour only in the outlines, hints and Enter.
                dark = true,
                background = 0xFF000000.toInt(),
                backgroundEnd = null,
                key = ThemeColors.fromHsl(h, s * 0.25f, 0.07f),
                keyPressed = ThemeColors.fromHsl(h, s * 0.35f, 0.16f),
                functionKey = ThemeColors.fromHsl(h, s * 0.2f, 0.04f),
                border = ThemeColors.fromHsl(h, s * 0.6f, 0.28f),
                text = ThemeColors.fromHsl(h, s * 0.15f, 0.95f),
                hint = ThemeColors.fromHsl(h, s * 0.5f, 0.70f),
                accent = ThemeColors.fromHsl(h, s, 0.62f),
                onAccent = ThemeColors.fromHsl(h, s * 0.6f, 0.08f),
                glows = emptyList(),
            )
            AppTintStyle.NEON -> theme.copy(
                // Bright outlines in the app's colour on near-black keys.
                dark = true,
                background = ThemeColors.fromHsl(h, s * 0.3f, 0.03f),
                backgroundEnd = null,
                key = ThemeColors.fromHsl(h, s * 0.3f, 0.06f),
                keyPressed = ThemeColors.fromHsl(h, s * 0.5f, 0.14f),
                functionKey = ThemeColors.fromHsl(h, s * 0.3f, 0.045f),
                border = ThemeColors.fromHsl(h, s, 0.5f),
                text = ThemeColors.fromHsl(h, s * 0.2f, 0.96f),
                hint = ThemeColors.fromHsl(h, s, 0.72f),
                accent = ThemeColors.fromHsl(h, s, 0.6f),
                onAccent = ThemeColors.fromHsl(h, s * 0.6f, 0.07f),
                glows = listOf(Glow(ThemeColors.fromHsl(h, s, 0.5f, alpha = 0x26), 0.5f, 1.1f, 0.9f)),
            )
            AppTintStyle.SPOTLIGHT -> theme.copy(
                // Near-black lit from the top by one strong light in the app's colour.
                dark = true,
                background = ThemeColors.fromHsl(h, s * 0.3f, 0.035f),
                backgroundEnd = ThemeColors.fromHsl(h, s * 0.35f, 0.05f),
                key = 0x1AFFFFFF, keyPressed = 0x3DFFFFFF, functionKey = 0x0FFFFFFF, border = 0x1AFFFFFF,
                text = ThemeColors.fromHsl(h, s * 0.15f, 0.97f),
                hint = ThemeColors.fromHsl(h, s * 0.4f, 0.80f),
                accent = ThemeColors.fromHsl(h, s, 0.70f),
                onAccent = ThemeColors.fromHsl(h, s * 0.6f, 0.10f),
                glows = listOf(Glow(ThemeColors.fromHsl(h, s, 0.55f, alpha = 0x66), 0.5f, -0.2f, 0.9f)),
            )
            AppTintStyle.DUO -> theme.copy(
                // The app's colour with its opposite: two glows that complement each other.
                dark = true,
                background = ThemeColors.fromHsl(h, s * 0.3f, 0.07f),
                backgroundEnd = ThemeColors.fromHsl(h + 150f, s * 0.35f, 0.10f),
                key = 0x1FFFFFFF, keyPressed = 0x40FFFFFF, functionKey = 0x12FFFFFF, border = 0x1AFFFFFF,
                text = ThemeColors.fromHsl(h, s * 0.15f, 0.97f),
                hint = ThemeColors.fromHsl(h + 150f, s * 0.35f, 0.80f),
                accent = ThemeColors.fromHsl(h, s, 0.72f),
                onAccent = ThemeColors.fromHsl(h, s * 0.6f, 0.12f),
                glows = listOf(
                    Glow(ThemeColors.fromHsl(h, s, 0.55f, alpha = 0x52), 0.15f, -0.05f, 0.8f),
                    Glow(ThemeColors.fromHsl(h + 150f, s, 0.55f, alpha = 0x4D), 0.9f, 1.05f, 0.75f),
                ),
            )
            AppTintStyle.GLASS -> theme.copy(
                // A rich gradient of the app's colour behind frosted see-through keys.
                dark = true,
                background = ThemeColors.fromHsl(h, s * 0.65f, 0.20f),
                backgroundEnd = ThemeColors.fromHsl(h + 20f, s * 0.7f, 0.28f),
                key = 0x26FFFFFF, keyPressed = 0x4DFFFFFF, functionKey = 0x14FFFFFF, border = 0x33FFFFFF,
                text = ThemeColors.fromHsl(h, s * 0.1f, 0.98f),
                hint = ThemeColors.fromHsl(h, s * 0.3f, 0.88f),
                accent = ThemeColors.fromHsl(h, s * 0.4f, 0.92f),
                onAccent = ThemeColors.fromHsl(h, s * 0.8f, 0.20f),
                glows = emptyList(),
            )
            AppTintStyle.PASTEL -> theme.copy(
                // Soft and light: keys and background both carry a pastel of the app's colour.
                dark = false,
                background = ThemeColors.fromHsl(h, s * 0.7f, 0.86f),
                backgroundEnd = ThemeColors.fromHsl(h + 15f, s * 0.7f, 0.82f),
                key = ThemeColors.fromHsl(h, s * 0.6f, 0.95f),
                keyPressed = ThemeColors.fromHsl(h, s * 0.6f, 0.85f),
                functionKey = ThemeColors.fromHsl(h, s * 0.55f, 0.90f),
                border = null,
                text = ThemeColors.fromHsl(h, s * 0.5f, 0.15f),
                hint = ThemeColors.fromHsl(h, s * 0.35f, 0.40f),
                accent = ThemeColors.fromHsl(h, s, 0.38f),
                onAccent = 0xFFFFFFFF.toInt(),
                glows = listOf(Glow(ThemeColors.fromHsl(h + 40f, s, 0.8f, alpha = 0x40), 0.85f, -0.1f, 0.7f)),
            )
        }
        val done = readable(tinted)
        val drift = (theme.live as LiveStyle.AppColour).driftSeconds
        return done.copy(
            // A press effect takes the app's colour too.
            effectColor = if (done.pressEffect != null) done.accent else done.effectColor,
            live = if (drift > 0) LiveStyle.Drift(drift) else null,
        )
    }

    /**
     * Makes any colouring clearly readable: fades glows, then darkens the background (dark
     * themes) or the labels (light themes), until labels keep 4.5:1 on the keys over the
     * brightest point; then moves the accent until Enter keeps 4.5:1.
     */
    private fun readable(t: KeyboardTheme): KeyboardTheme {
        var theme = t
        fun labelsFail(x: KeyboardTheme) = LiveThemes.brightestBackgrounds(x).any { bg ->
            ThemeColors.contrast(x.text, ThemeColors.over(x.key, bg)) < MIN_LABEL_CONTRAST
        }
        var step = 0
        while (step < 20 && theme.glows.isNotEmpty() && labelsFail(theme)) {
            theme = theme.copy(glows = theme.glows.map { g -> g.copy(color = (((g.color ushr 24) * 0.8f).toInt() shl 24) or (g.color and 0xFFFFFF)) })
            step++
        }
        step = 0
        val black = 0xFF000000.toInt()
        while (step < 20 && labelsFail(theme)) {
            theme = if (theme.dark) {
                theme.copy(
                    background = ThemeColors.mix(theme.background, black, 0.15f),
                    backgroundEnd = theme.backgroundEnd?.let { ThemeColors.mix(it, black, 0.15f) },
                    key = if (theme.key ushr 24 == 0xFF) ThemeColors.mix(theme.key, black, 0.15f) else theme.key,
                )
            } else {
                theme.copy(text = ThemeColors.mix(theme.text, black, 0.25f))
            }
            step++
        }
        var accent = theme.accent
        step = 0
        val onAccentLight = ThemeColors.luminance(theme.onAccent) > 0.5
        while (step < 20 && ThemeColors.contrast(theme.onAccent, accent) < MIN_ACCENT_CONTRAST) {
            accent = ThemeColors.mix(accent, if (onAccentLight) black else -1, 0.12f)
            step++
        }
        return theme.copy(accent = accent)
    }
}
