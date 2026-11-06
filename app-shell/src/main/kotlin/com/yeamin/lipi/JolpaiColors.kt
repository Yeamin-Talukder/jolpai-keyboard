package com.yeamin.lipi

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The settings app's own look: always dark, with Jolpai's olive (জলপাই) greens from the app
 * icon (#6E8A2C, #94AE3A, #4E6120) and its cream background (#F3EFD9) as a warm second tone.
 * It replaced Material You wallpaper colours, which made the app grey on many phones and didn't
 * look like Jolpai. Surfaces are near-black with a faint olive tint so cards and groups stay
 * calm; olive marks what is active (switches, sliders, selected items), cream-gold accents
 * the tertiary groups.
 */
private val JolpaiDark = darkColorScheme(
    primary = Color(0xFFB4CF66),
    onPrimary = Color(0xFF253500),
    primaryContainer = Color(0xFF3B4F10),
    onPrimaryContainer = Color(0xFFD3EE86),
    inversePrimary = Color(0xFF55701F),

    secondary = Color(0xFFC1CAA4),
    onSecondary = Color(0xFF2B3318),
    secondaryContainer = Color(0xFF41492C),
    onSecondaryContainer = Color(0xFFDDE6BF),

    tertiary = Color(0xFFE2C98A),
    onTertiary = Color(0xFF3D2F04),
    tertiaryContainer = Color(0xFF554519),
    onTertiaryContainer = Color(0xFFFFE5A6),

    background = Color(0xFF11130D),
    onBackground = Color(0xFFE3E4D5),
    surface = Color(0xFF11130D),
    onSurface = Color(0xFFE3E4D5),
    surfaceVariant = Color(0xFF45493A),
    onSurfaceVariant = Color(0xFFC6C9B4),
    surfaceTint = Color(0xFFB4CF66),
    inverseSurface = Color(0xFFE3E4D5),
    inverseOnSurface = Color(0xFF2F3128),

    surfaceDim = Color(0xFF11130D),
    surfaceBright = Color(0xFF373A30),
    surfaceContainerLowest = Color(0xFF0C0E08),
    surfaceContainerLow = Color(0xFF1A1C15),
    surfaceContainer = Color(0xFF1E2119),
    surfaceContainerHigh = Color(0xFF282B22),
    surfaceContainerHighest = Color(0xFF33362C),

    outline = Color(0xFF8F9381),
    outlineVariant = Color(0xFF45493A),
    scrim = Color(0xFF000000),
)

/** Background colour of the app window (also used for the launch screen). */
val JolpaiBackground: Color get() = JolpaiDark.background

@Composable
fun JolpaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JolpaiDark, content = content)
}
