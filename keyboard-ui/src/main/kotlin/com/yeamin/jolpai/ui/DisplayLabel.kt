package com.yeamin.jolpai.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Key labels as drawn. A Bangla vowel sign or hasanta on its own (ি ে ো ্ ং …) has no letter
 * to sit on, so the text shaper draws it on a dotted circle (◌ি). Putting a no-break space in
 * front gives it an invisible base instead, so the key shows just the sign. Only the label
 * changes; the key still types the sign alone.
 */
internal object DisplayLabel {
    private const val INVISIBLE_BASE = ' '

    fun of(text: String): String = if (text.isNotEmpty() && isMark(text[0])) INVISIBLE_BASE + text else text

    /** A label [of] gave an invisible base: a sign on its own (া ি ে ো ু ৃ ্ ঁ ং ঃ …). */
    fun isSign(label: String): Boolean = label.length > 1 && label[0] == INVISIBLE_BASE

    /** Reused for measuring (labels are drawn on the main thread only; no allocation in onDraw). */
    private val bounds = Rect()

    /**
     * Draws a sign label with its drawn shape centred on ([cx], [cy]). On its own a sign sits
     * where it would on a letter (ু ৃ ্ low, ঁ high, ি to the left), which looks untidy on a key;
     * centring the ink makes it sit in the middle like any other label.
     */
    fun drawSignCentered(canvas: Canvas, label: String, cx: Float, cy: Float, paint: Paint) {
        paint.getTextBounds(label, 0, label.length, bounds)
        val align = paint.textAlign
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, cx - bounds.exactCenterX(), cy - bounds.exactCenterY(), paint)
        paint.textAlign = align
    }

    /** Draws a sign hint with its drawn shape tucked into the corner: top-right at ([right], [top]). */
    fun drawSignTopRight(canvas: Canvas, label: String, right: Float, top: Float, paint: Paint) {
        paint.getTextBounds(label, 0, label.length, bounds)
        val align = paint.textAlign
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, right - bounds.right, top - bounds.top, paint)
        paint.textAlign = align
    }

    private fun isMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }
}
