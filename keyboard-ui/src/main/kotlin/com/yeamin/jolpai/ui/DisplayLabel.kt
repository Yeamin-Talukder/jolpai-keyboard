package com.yeamin.jolpai.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.annotation.RequiresApi
import com.yeamin.jolpai.core.fonts.FontFile

/**
 * Key labels as drawn. A Bangla vowel sign or hasanta on its own (ি ে ো ্ ং …) has no letter
 * to sit on, so the text shaper draws it on a dotted circle (◌ি). Putting a no-break space in
 * front gives it an invisible base instead, so the key shows just the sign. Only the label
 * changes; the key still types the sign alone.
 *
 * The no-break space only helps when the font drawing the sign has one too. Hind Siliguri,
 * Galada and many fonts people pick don't, so the space comes from another font, the sign is
 * left alone, and the circle comes back. On Android 12+ a sign label is therefore shaped once
 * and any dotted-circle glyph in the result is left out before drawing ([Shaped]); on older
 * versions such fonts draw signs in the phone's font ([KeyFonts.lacksNoBreakSpace]).
 */
internal object DisplayLabel {
    private const val INVISIBLE_BASE = ' '
    private const val DOTTED_CIRCLE = 0x25CC

    fun of(text: String): String = if (text.isNotEmpty() && isMark(text[0])) INVISIBLE_BASE + text else text

    /** A label [of] gave an invisible base: a sign on its own (া ি ে ো ু ৃ ্ ঁ ং ঃ …). */
    fun isSign(label: String): Boolean = label.length > 1 && label[0] == INVISIBLE_BASE

    /** Has Bangla script (letters, signs, digits): drawn in the Bangla letters font. */
    fun isBangla(label: String): Boolean = label.any { it in 'ঀ'..'৿' }

    /** Reused for measuring (labels are drawn on the main thread only; no allocation in onDraw). */
    private val bounds = Rect()

    /**
     * Draws a sign label with its drawn shape centred on ([cx], [cy]). On its own a sign sits
     * where it would on a letter (ু ৃ ্ low, ঁ high, ি to the left), which looks untidy on a key;
     * centring the ink makes it sit in the middle like any other label.
     */
    fun drawSignCentered(canvas: Canvas, label: String, cx: Float, cy: Float, paint: Paint) {
        if (Build.VERSION.SDK_INT >= 31) {
            val shaped = shaped(label, paint)
            if (shaped != null) return shaped.draw(canvas, cx - shaped.ink.centerX(), cy - shaped.ink.centerY(), paint)
        }
        withSignTypeface(paint) {
            paint.getTextBounds(label, 0, label.length, bounds)
            val align = paint.textAlign
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, cx - bounds.exactCenterX(), cy - bounds.exactCenterY(), paint)
            paint.textAlign = align
        }
    }

    /** Draws a sign hint with its drawn shape tucked into the corner: top-right at ([right], [top]). */
    fun drawSignTopRight(canvas: Canvas, label: String, right: Float, top: Float, paint: Paint) {
        if (Build.VERSION.SDK_INT >= 31) {
            val shaped = shaped(label, paint)
            if (shaped != null) return shaped.draw(canvas, right - shaped.ink.right, top - shaped.ink.top, paint)
        }
        withSignTypeface(paint) {
            paint.getTextBounds(label, 0, label.length, bounds)
            val align = paint.textAlign
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, right - bounds.right, top - bounds.top, paint)
            paint.textAlign = align
        }
    }

    /** Android 11 and older: a font without a no-break space draws signs in the phone's font. */
    private inline fun withSignTypeface(paint: Paint, draw: () -> Unit) {
        val typeface = paint.typeface
        if (Build.VERSION.SDK_INT >= 31 || !KeyFonts.lacksNoBreakSpace(typeface)) return draw()
        paint.typeface = if (typeface.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        draw()
        paint.typeface = typeface
    }

    // ---- Android 12+: shape once, leave out dotted circles, draw the glyphs ----

    /** Shaped sign labels by label; each keeps the typeface and size it was shaped for. */
    private val shapes = HashMap<String, ArrayList<Entry>>()
    private var shapeCount = 0

    /** The label's glyphs without dotted circles, or null when there was no circle (draw it as text). */
    @RequiresApi(31)
    private fun shaped(label: String, paint: Paint): Shaped? {
        val list = shapes.getOrPut(label) { ArrayList(2) }
        for (i in list.indices) {
            val entry = list[i]
            if (entry.typeface === paint.typeface && entry.textSize == paint.textSize) return entry.shaped
        }
        if (shapeCount > 512) {
            shapes.clear()
            shapeCount = 0
        }
        val shaped = runCatching { Shaped.of(label, paint) }.getOrNull()
        shapes.getOrPut(label) { ArrayList(2) }.add(Entry(paint.typeface, paint.textSize, shaped))
        shapeCount++
        return shaped
    }

    private class Entry(val typeface: Typeface?, val textSize: Float, val shaped: Shaped?)

    /** Glyph runs (one font each) of a sign label with the dotted circles left out, and their ink bounds. */
    @RequiresApi(31)
    private class Shaped(
        val fonts: Array<Font>,
        val glyphs: Array<IntArray>,
        val positions: Array<FloatArray>,
        val fakeBold: BooleanArray,
        val ink: RectF,
    ) {
        fun draw(canvas: Canvas, dx: Float, dy: Float, paint: Paint) {
            val bold = paint.isFakeBoldText
            canvas.save()
            canvas.translate(dx, dy)
            for (i in fonts.indices) {
                paint.isFakeBoldText = fakeBold[i]
                canvas.drawGlyphs(glyphs[i], 0, positions[i], 0, glyphs[i].size, fonts[i], paint)
            }
            canvas.restore()
            paint.isFakeBoldText = bold
        }

        companion object {
            fun of(label: String, paint: Paint): Shaped? {
                val run = TextRunShaper.shapeTextRun(label, 0, label.length, 0, label.length, 0f, 0f, false, paint)
                val keep = ArrayList<Int>(run.glyphCount())
                var circles = 0
                var lastFont: Font? = null
                var lastCircle = 0
                for (i in 0 until run.glyphCount()) {
                    val font = run.getFont(i)
                    if (font !== lastFont) {
                        lastFont = font
                        lastCircle = circleGlyph(font)
                    }
                    if (lastCircle != 0 && run.getGlyphId(i) == lastCircle) circles++ else keep += i
                }
                if (circles == 0 || keep.isEmpty()) return null

                // Group the kept glyphs into runs of one font, as drawGlyphs needs.
                val fonts = ArrayList<Font>()
                val glyphs = ArrayList<IntArray>()
                val positions = ArrayList<FloatArray>()
                val ink = RectF()
                val box = RectF()
                var start = 0
                while (start < keep.size) {
                    val font = run.getFont(keep[start])
                    var end = start
                    while (end + 1 < keep.size && run.getFont(keep[end + 1]) === font) end++
                    val n = end - start + 1
                    val ids = IntArray(n)
                    val pos = FloatArray(2 * n)
                    for (k in 0 until n) {
                        val g = keep[start + k]
                        ids[k] = run.getGlyphId(g)
                        pos[2 * k] = run.getGlyphX(g)
                        pos[2 * k + 1] = run.getGlyphY(g)
                        font.getGlyphBounds(ids[k], paint, box)
                        box.offset(pos[2 * k], pos[2 * k + 1])
                        if (ink.isEmpty) ink.set(box) else ink.union(box)
                    }
                    fonts += font
                    glyphs += ids
                    positions += pos
                    start = end + 1
                }
                // A bold label in a font without a bold face is drawn fake-bold, like the text would be.
                val wantsBold = paint.typeface?.isBold == true || paint.isFakeBoldText
                val fake = BooleanArray(fonts.size) { wantsBold && fonts[it].style.weight < 600 }
                return Shaped(fonts.toTypedArray(), glyphs.toTypedArray(), positions.toTypedArray(), fake, ink)
            }

            /** The font's own dotted-circle glyph (what the shaper inserts), or 0 if it has none. */
            private fun circleGlyph(font: Font): Int {
                val buffer = font.buffer ?: return 0
                return FontFile.parse(buffer, font.ttcIndex)?.glyphId(DOTTED_CIRCLE) ?: 0
            }
        }
    }

    private fun isMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }
}
