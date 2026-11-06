package com.yeamin.lipi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.withScale
import com.yeamin.lipi.core.model.PopupItem

/**
 * A transparent overlay above the whole keyboard that draws the key preview bubble and the
 * long-press choices. It never handles touches: the [KeyboardView] that started the gesture
 * keeps receiving the finger's movement and tells this view what to highlight.
 *
 * The bubble pops up with a small spring overshoot, rolls gently between keys, and fades out
 * after release; the long-press row grows out of its key.
 */
@SuppressLint("ViewConstructor") // created in code only, never inflated from XML
class KeyPopupView(context: Context, private val palette: KeyboardPalette) : View(context) {

    private val density = resources.displayMetrics.density
    private val radius = 14 * density
    private val inset = 3 * density

    // A soft drop shadow (drawn by hardware rendering on Android 9+; flat before that).
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.popup
        setShadowLayer(8 * density, 0f, 3 * density, palette.shadow)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.text
        textAlign = Paint.Align.CENTER
    }

    private var previewRect: RectF? = null
    private var previewLabel = ""
    private var previewShownAt = 0L
    private var previewFrom = POP_FROM

    // The last bubble keeps fading out briefly after the finger lifts, instead of vanishing.
    private var fadingRect: RectF? = null
    private var fadingLabel = ""
    private var fadeStartedAt = 0L

    private var choices: List<PopupItem> = emptyList()
    private var choiceLabels: List<String> = emptyList()
    private val choiceRects = ArrayList<RectF>()
    private var selected = -1
    private var choicesShownAt = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * An overlay must never decide the keyboard's height: report 0 unless the parent fixes
     * the height, so the parent sizes to the keyboard and then stretches this view over it.
     * (Without this the input window grew to nearly the full screen.)
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    /** [key] is in this view's coordinates. */
    fun showPreview(key: RectF, label: String) {
        val w = (key.width() * 1.15f).coerceAtLeast(44 * density)
        val h = key.height() * 1.3f
        val left = (key.centerX() - w / 2).coerceIn(0f, (width - w).coerceAtLeast(0f))
        val top = (key.top - h - 4 * density).coerceAtLeast(0f)
        // Rolling from one key to the next only nudges the bubble; a fresh press pops it up.
        previewFrom = if (previewRect != null) ROLL_FROM else POP_FROM
        previewRect = RectF(left, top, left + w, top + h)
        previewLabel = label
        previewShownAt = SystemClock.uptimeMillis()
        fadingRect = null
        invalidate()
    }

    fun hidePreview() {
        val r = previewRect ?: return
        fadingRect = r
        fadingLabel = previewLabel
        fadeStartedAt = SystemClock.uptimeMillis()
        previewRect = null
        invalidate()
    }

    /** Shows [items] in a row above [key] and highlights [initial]. */
    fun showChoices(key: RectF, items: List<PopupItem>, initial: Int) {
        previewRect = null
        fadingRect = null
        choicesShownAt = SystemClock.uptimeMillis()
        choices = items
        choiceLabels = items.map { DisplayLabel.of(it.label) }
        choiceRects.clear()
        val cellW = key.width().coerceIn(40 * density, 56 * density)
        val cellH = key.height().coerceAtLeast(44 * density)
        val total = cellW * items.size
        val left = (key.centerX() - cellW / 2 - cellW * initial).coerceIn(0f, (width - total).coerceAtLeast(0f))
        val top = (key.top - cellH - 4 * density).coerceAtLeast(0f)
        items.indices.forEach { i ->
            choiceRects += RectF(left + i * cellW, top, left + (i + 1) * cellW, top + cellH)
        }
        selected = initial
        invalidate()
    }

    /** Highlights the choice under horizontal position [x] (this view's coordinates); returns its index. */
    fun selectAt(x: Float): Int {
        if (choiceRects.isEmpty()) return -1
        val index = when {
            x < choiceRects.first().left -> 0
            x >= choiceRects.last().right -> choiceRects.size - 1
            else -> choiceRects.indexOfFirst { x >= it.left && x < it.right }
        }
        if (index != selected) {
            selected = index
            invalidate()
        }
        return selected
    }

    fun selectedChoice(): PopupItem? = choices.getOrNull(selected)

    fun hideChoices() {
        if (choices.isNotEmpty()) {
            choices = emptyList()
            choiceRects.clear()
            selected = -1
            invalidate()
        }
    }

    fun hideAll() {
        hidePreview()
        hideChoices()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var animating = false
        fadingRect?.let { r ->
            val t = (now - fadeStartedAt) / FADE_MS
            if (t >= 1f) {
                fadingRect = null
            } else {
                animating = true
                val k = 1f - t
                drawBubble(canvas, r, fadingLabel, 0.9f + 0.1f * k, (255 * k * k).toInt())
            }
        }
        previewRect?.let { r ->
            val t = ((now - previewShownAt) / POP_MS).coerceAtMost(1f)
            if (t < 1f) animating = true
            val scale = previewFrom + (1f - previewFrom) * easeOutBack(t)
            drawBubble(canvas, r, previewLabel, scale, (255 * (t * 2.5f).coerceAtMost(1f)).toInt())
        }
        if (choiceRects.isNotEmpty()) {
            val first = choiceRects.first()
            val last = choiceRects.last()
            // The choices row grows out of the key it belongs to.
            val t = ((now - choicesShownAt) / POP_MS).coerceAtMost(1f)
            if (t < 1f) animating = true
            val scale = CHOICES_FROM + (1f - CHOICES_FROM) * easeOutBack(t)
            val alpha = (255 * (t * 2.5f).coerceAtMost(1f)).toInt()
            val pivotX = choiceRects.getOrNull(selected.coerceAtLeast(0))?.centerX() ?: first.centerX()
            bgPaint.alpha = alpha
            selectedPaint.alpha = alpha
            textPaint.textSize = 22 * density
            canvas.withScale(scale, scale, pivotX, last.bottom) {
                drawRoundRect(first.left, first.top, last.right, last.bottom, radius, radius, bgPaint)
                choiceRects.forEachIndexed { i, r ->
                    if (i == selected) {
                        drawRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset, radius - inset, radius - inset, selectedPaint)
                        textPaint.color = palette.onAccent
                    } else {
                        textPaint.color = palette.text
                    }
                    textPaint.alpha = alpha
                    drawCentered(this, choiceLabels[i], r)
                }
            }
            bgPaint.alpha = 255
            selectedPaint.alpha = 255
        }
        if (animating) postInvalidateOnAnimation()
    }

    /** The preview bubble, scaled around its bottom centre (where it meets the key). */
    private fun drawBubble(canvas: Canvas, r: RectF, label: String, scale: Float, alpha: Int) {
        // Paints are set up outside the block: inside it, `density` would mean the Canvas's own.
        bgPaint.alpha = alpha
        textPaint.color = palette.text
        textPaint.alpha = alpha
        textPaint.textSize = 32 * density
        canvas.withScale(scale, scale, r.centerX(), r.bottom) {
            drawRoundRect(r, radius, radius, bgPaint)
            drawCentered(this, label, r)
        }
        bgPaint.alpha = 255
    }

    private fun drawCentered(canvas: Canvas, label: String, r: RectF) {
        val fm = textPaint.fontMetrics
        canvas.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
    }

    private companion object {
        const val POP_MS = 150f
        const val FADE_MS = 90f
        const val POP_FROM = 0.72f
        const val ROLL_FROM = 0.94f
        const val CHOICES_FROM = 0.86f

        /** Overshoots a touch past 1 and settles back: the springy "pop". */
        fun easeOutBack(t: Float): Float {
            val c1 = 1.6f
            val c3 = c1 + 1f
            val u = t - 1f
            return 1f + c3 * u * u * u + c1 * u * u
        }
    }
}
