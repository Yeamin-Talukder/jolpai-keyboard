package com.yeamin.lipi.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.yeamin.lipi.core.settings.KeyFont

/**
 * The bundled key fonts (Bangla fonts from Google Fonts, SIL Open Font License; notices in
 * resources/fonts). Each has a regular face for letters and a medium one for labels like "?123".
 */
object KeyFonts {
    private val cache = HashMap<KeyFont, Pair<Typeface, Typeface>?>()

    /** Regular and medium typefaces for [font], or null for the phone's default font. */
    fun load(context: Context, font: KeyFont): Pair<Typeface, Typeface>? = cache.getOrPut(font) {
        val (regular, medium) = when (font) {
            KeyFont.SYSTEM -> return@getOrPut null
            KeyFont.HIND_SILIGURI -> R.font.hind_siliguri_regular to R.font.hind_siliguri_medium
            KeyFont.TIRO_BANGLA -> R.font.tiro_bangla_regular to R.font.tiro_bangla_regular
            KeyFont.GALADA -> R.font.galada_regular to R.font.galada_regular
            KeyFont.ATMA -> R.font.atma_regular to R.font.atma_medium
        }
        runCatching {
            val r = ResourcesCompat.getFont(context, regular) ?: return@runCatching null
            val m = ResourcesCompat.getFont(context, medium) ?: r
            r to m
        }.getOrNull()
    }
}
