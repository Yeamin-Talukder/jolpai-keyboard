package com.yeamin.jolpai.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.yeamin.jolpai.core.fonts.FontFile
import com.yeamin.jolpai.core.settings.KeyFont
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.WeakHashMap

/**
 * The key fonts: the bundled ones (Google Fonts, SIL Open Font License; notices in
 * resources/fonts) and the one the user picked ([CustomKeyFont]). Each gives a regular face for
 * letters and a medium one for labels like "?123". A font that can't be loaded gives null, and
 * the keys use the phone's font.
 */
object KeyFonts {
    private val cache = HashMap<KeyFont, Pair<Typeface, Typeface>?>()
    private var custom: Pair<String, Pair<Typeface, Typeface>?>? = null

    /**
     * Typefaces whose font has no no-break space (Hind Siliguri, Galada, many picked fonts).
     * [DisplayLabel] puts one before a lone vowel sign as its invisible base; without it the
     * sign is drawn on a dotted circle, so on Android 11 and older such signs use the phone's
     * font (newer versions drop the circle itself).
     */
    private val noBreakSpaceMissing: MutableSet<Typeface> = Collections.newSetFromMap(WeakHashMap())

    // medium = 0: labels like "?123" stay in the phone's font (a very wide font would not fit them).
    private class Spec(val regular: Int, val medium: Int, val regularWeight: Int = 0, val mediumWeight: Int = 0)

    private fun spec(font: KeyFont): Spec? = when (font) {
        KeyFont.SYSTEM, KeyFont.CUSTOM -> null
        KeyFont.HIND_SILIGURI -> Spec(R.font.hind_siliguri_regular, R.font.hind_siliguri_medium)
        KeyFont.TIRO_BANGLA -> Spec(R.font.tiro_bangla_regular, R.font.tiro_bangla_regular)
        KeyFont.GALADA -> Spec(R.font.galada_regular, R.font.galada_regular)
        KeyFont.ATMA -> Spec(R.font.atma_regular, R.font.atma_medium)
        // Variable fonts: weights set on Android 10+; older phones get the file's default weight.
        KeyFont.BALOO_DA -> Spec(R.font.baloo_da_2, R.font.baloo_da_2, 400, 500)
        KeyFont.MINA -> Spec(R.font.mina_regular, R.font.mina_regular)
        KeyFont.POPPINS -> Spec(R.font.poppins_regular, R.font.poppins_medium)
        KeyFont.QUICKSAND -> Spec(R.font.quicksand, R.font.quicksand, 500, 600)
        KeyFont.PACIFICO -> Spec(R.font.pacifico_regular, R.font.pacifico_regular)
        KeyFont.SPACE_MONO -> Spec(R.font.space_mono_regular, R.font.space_mono_regular)
        KeyFont.DANCING_SCRIPT -> Spec(R.font.dancing_script, R.font.dancing_script, 500, 600)
        KeyFont.LOBSTER -> Spec(R.font.lobster_regular, R.font.lobster_regular)
        KeyFont.KAUSHAN_SCRIPT -> Spec(R.font.kaushan_script_regular, R.font.kaushan_script_regular)
        KeyFont.INDIE_FLOWER -> Spec(R.font.indie_flower_regular, R.font.indie_flower_regular)
        KeyFont.RIGHTEOUS -> Spec(R.font.righteous_regular, R.font.righteous_regular)
        KeyFont.ORBITRON -> Spec(R.font.orbitron, R.font.orbitron, 500, 600)
        KeyFont.PRESS_START -> Spec(R.font.press_start_2p_regular, 0)
        KeyFont.KALPURUSH -> Spec(R.font.kalpurush, R.font.kalpurush)
        KeyFont.KALAA -> Spec(R.font.kalaa, R.font.kalaa)
        KeyFont.NOTO_SERIF_BENGALI -> Spec(R.font.noto_serif_bengali_regular, R.font.noto_serif_bengali_regular)
        KeyFont.ALKATRA -> Spec(R.font.alkatra, R.font.alkatra, 400, 600)
        KeyFont.TIRO_BANGLA_ITALIC -> Spec(R.font.tiro_bangla_italic, R.font.tiro_bangla_italic)
        KeyFont.BALOO_DA_HEAVY -> Spec(R.font.baloo_da_2, R.font.baloo_da_2, 800, 800)
        KeyFont.SATISFY -> Spec(R.font.satisfy_regular, R.font.satisfy_regular)
        KeyFont.COURGETTE -> Spec(R.font.courgette_regular, R.font.courgette_regular)
        KeyFont.PERMANENT_MARKER -> Spec(R.font.permanent_marker_regular, R.font.permanent_marker_regular)
        KeyFont.SHADOWS_INTO_LIGHT -> Spec(R.font.shadows_into_light, R.font.shadows_into_light)
        KeyFont.FREDOKA -> Spec(R.font.fredoka, R.font.fredoka, 500, 600)
        KeyFont.BANGERS -> Spec(R.font.bangers_regular, R.font.bangers_regular)
        KeyFont.ABRIL_FATFACE -> Spec(R.font.abril_fatface_regular, R.font.abril_fatface_regular)
        KeyFont.CREEPSTER -> Spec(R.font.creepster_regular, R.font.creepster_regular)
    }

    /** Regular and medium typefaces for [font], or null for the phone's default font. */
    @Synchronized
    fun load(context: Context, font: KeyFont): Pair<Typeface, Typeface>? {
        if (font == KeyFont.CUSTOM) return loadCustom(context)
        return cache.getOrPut(font) {
            val s = spec(font) ?: return@getOrPut null
            runCatching {
                val r = bundled(context, s.regular, s.regularWeight) ?: return@runCatching null
                val m = when {
                    s.medium == 0 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    s.medium == s.regular && s.mediumWeight == s.regularWeight -> r
                    else -> bundled(context, s.medium, s.mediumWeight) ?: r
                }
                if (Build.VERSION.SDK_INT < 31) {
                    val file = context.resources.openRawResource(s.regular).use { FontFile.parse(it.readBytes()) }
                    if (file != null && !file.has(NO_BREAK_SPACE)) noBreakSpaceMissing += if (s.medium == 0) listOf(r) else listOf(r, m)
                }
                r to m
            }.getOrNull()
        }
    }

    private fun bundled(context: Context, res: Int, weight: Int): Typeface? {
        if (weight == 0 || Build.VERSION.SDK_INT < 29) return ResourcesCompat.getFont(context, res)
        val font = Font.Builder(context.resources, res)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .build()
        return Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
            .setStyle(FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
            .build()
    }

    /** The picked font, reloaded whenever its file changes; null if there is none or it doesn't load. */
    private fun loadCustom(context: Context): Pair<Typeface, Typeface>? {
        val file = CustomKeyFont.file(context)
        if (!file.isFile) return null
        val stamp = "${file.length()}:${file.lastModified()}"
        custom?.let { (s, fonts) -> if (s == stamp) return fonts }
        val fonts = runCatching { typefaceFrom(file) }.getOrNull()?.let { it to it }
        custom = stamp to fonts
        return fonts
    }

    /**
     * A typeface from a font file, or null if the file isn't a usable font. Checked twice: by
     * Jolpai's own reader (the header and character map), then by Android loading it.
     */
    internal fun typefaceFrom(file: File): Typeface? {
        if (file.length() !in 12..FontFile.MAX_BYTES.toLong()) return null
        val info = FontFile.parse(map(file)) ?: return null
        val typeface = if (Build.VERSION.SDK_INT >= 29) {
            val font = Font.Builder(file).build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).build()
        } else {
            Typeface.createFromFile(file).takeIf { it != Typeface.DEFAULT }
        } ?: return null
        if (!info.has(NO_BREAK_SPACE)) synchronized(this) { noBreakSpaceMissing += typeface }
        return typeface
    }

    /** Forgets the loaded picked font, so the next [load] reads the new file. */
    @Synchronized
    fun forgetCustom() {
        custom = null
    }

    private fun map(file: File): ByteBuffer = RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }

    /** [typeface] in bold (bold-label themes), remembering whether its font has a no-break space. */
    @Synchronized
    fun bold(typeface: Typeface): Typeface = Typeface.create(typeface, Typeface.BOLD).also {
        if (typeface in noBreakSpaceMissing) noBreakSpaceMissing += it
    }

    @Synchronized
    fun lacksNoBreakSpace(typeface: Typeface?): Boolean = typeface != null && typeface in noBreakSpaceMissing

    private const val NO_BREAK_SPACE = 0x00A0
}
