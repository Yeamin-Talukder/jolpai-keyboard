package com.yeamin.jolpai.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.yeamin.jolpai.theme.BuiltInThemes
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.theme.ThemeColors

/**
 * The resolved look of the keyboard for drawing: a [KeyboardTheme] with Material You colours
 * filled in from the phone where the theme asks for them.
 */
data class KeyboardPalette(
    val background: Int,
    val backgroundEnd: Int?,
    val key: Int,
    val keyPressed: Int,
    val functionKey: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val onAccent: Int,
    val shadow: Int,
    val popup: Int,
    val border: Int?,
    val drawShadow: Boolean,
    val keyRadiusDp: Float,
    val keyGapDp: Float,
    val keyOpacity: Float,
    val labelScale: Float,
    val boldLabels: Boolean,
    val backgroundImage: String?,
    val imageDim: Float,
    /** Soft lights over the background (ambient themes). */
    val glows: List<com.yeamin.jolpai.theme.Glow> = emptyList(),
    /** One loop of drifting glows in ms, or 0 when they stay still. */
    val driftPeriodMs: Long = 0L,
    /** Animation where a key is pressed, or null. */
    val pressEffect: com.yeamin.jolpai.theme.PressEffect? = null,
    /** Colour of [pressEffect], or null for the accent. */
    val effectColor: Int? = null,
    /** Readable colours for the Translate and Check cards (see [com.yeamin.jolpai.theme.CardColors]). */
    val card: com.yeamin.jolpai.theme.CardColors = com.yeamin.jolpai.theme.CardColors.of(com.yeamin.jolpai.theme.BuiltInThemes.DEFAULT),
) {
    companion object {
        /** The default theme (Olive night), drawn before the user's settings have loaded. */
        fun forContext(context: Context): KeyboardPalette = from(context, BuiltInThemes.DEFAULT)

        fun isNight(context: Context): Boolean =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        fun from(context: Context, theme: KeyboardTheme): KeyboardPalette {
            val t = resolveDynamic(context, theme)
            val popup = ThemeColors.popup(t)
            return KeyboardPalette(
                background = t.background,
                backgroundEnd = t.backgroundEnd,
                key = t.key,
                keyPressed = t.keyPressed,
                functionKey = t.functionKey,
                text = t.text,
                hint = t.hint,
                accent = t.accent,
                onAccent = t.onAccent,
                shadow = if (t.dark) 0x66000000 else 0x33000000,
                popup = popup,
                border = t.border,
                drawShadow = t.shadow,
                keyRadiusDp = t.keyRadiusDp,
                keyGapDp = t.keyGapDp,
                keyOpacity = t.keyOpacity,
                labelScale = t.labelScale,
                boldLabels = t.boldLabels,
                backgroundImage = t.backgroundImage,
                imageDim = t.imageDim,
                glows = t.glows,
                driftPeriodMs = (t.live as? com.yeamin.jolpai.theme.LiveStyle.Drift)?.let { it.periodSeconds * 1000L } ?: 0L,
                pressEffect = t.pressEffect,
                effectColor = t.effectColor,
                card = com.yeamin.jolpai.theme.CardColors.of(t),
            )
        }

        /**
         * [theme] with its real colours: wallpaper colours for a Material You theme, the colours of
         * this moment for a time-of-day theme, as is otherwise. A Chameleon theme that the keyboard
         * service hasn't already coloured for the app being typed in (previews in Jolpai's own
         * screens) takes Jolpai's own colour, since that is the app you are in.
         */
        fun resolveDynamic(context: Context, theme: KeyboardTheme): KeyboardTheme {
            var t = com.yeamin.jolpai.theme.LiveThemes.at(theme, java.time.LocalTime.now())
            if (t.live is com.yeamin.jolpai.theme.LiveStyle.AppColour) {
                t = com.yeamin.jolpai.theme.AppTint.apply(t, AppColours.colourNow(context, context.packageName))
            }
            return if (t.dynamic) materialYou(context, t) else t
        }

        /** Android 12+ wallpaper colours (Material You); the theme's own colours before that. */
        private fun materialYou(context: Context, t: KeyboardTheme): KeyboardTheme {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return t
            fun c(id: Int) = context.getColor(id)
            return if (t.dark) {
                t.copy(
                    background = c(android.R.color.system_neutral1_900),
                    key = c(android.R.color.system_neutral1_800),
                    keyPressed = c(android.R.color.system_neutral1_700),
                    functionKey = c(android.R.color.system_accent2_800),
                    text = c(android.R.color.system_neutral1_50),
                    hint = c(android.R.color.system_neutral2_300),
                    accent = c(android.R.color.system_accent1_200),
                    onAccent = c(android.R.color.system_accent1_800),
                )
            } else {
                t.copy(
                    background = c(android.R.color.system_neutral1_100),
                    key = c(android.R.color.system_neutral1_10),
                    keyPressed = c(android.R.color.system_neutral1_200),
                    functionKey = c(android.R.color.system_accent2_100),
                    text = c(android.R.color.system_neutral1_900),
                    hint = c(android.R.color.system_neutral2_600),
                    accent = c(android.R.color.system_accent1_600),
                    onAccent = c(android.R.color.system_accent1_0),
                )
            }
        }
    }
}
