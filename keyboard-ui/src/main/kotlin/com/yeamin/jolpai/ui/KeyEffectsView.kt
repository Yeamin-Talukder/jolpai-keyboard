package com.yeamin.jolpai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.yeamin.jolpai.theme.EffectShape
import com.yeamin.jolpai.theme.EffectSink
import com.yeamin.jolpai.theme.KeyPress
import com.yeamin.jolpai.theme.PressEffect
import com.yeamin.jolpai.theme.PressEffects

/**
 * Draws the theme's [PressEffect] over the keys where they are pressed. A separate layer on top
 * of [KeyboardView], so only the effects redraw while they play, not every key. It takes no
 * touches (they reach the keys below). Frames run only while an effect is playing (each lasts
 * under two thirds of a second); with the phone's animations turned off, presses draw nothing.
 */
class KeyEffectsView(
    context: Context,
    private val effect: PressEffect,
    color: Int,
    /** The keys' corner radius in px, so key-shaped effects match the keys. */
    private val corner: Float,
    /** The typeface of the key labels, for the floating letter. */
    typeface: Typeface?,
) : View(context) {

    private val rgb = color and 0x00FFFFFF
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color or OPAQUE }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.color = color or OPAQUE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color or OPAQUE
        textAlign = Paint.Align.CENTER
        this.typeface = typeface ?: Typeface.DEFAULT_BOLD
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** One soft disc of radius 1, scaled and moved by [matrix] for each glow. */
    private val glowShader = RadialGradient(0f, 0f, 1f, intArrayOf(color or OPAQUE, rgb), null, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val rect = RectF()
    private val clip = Path()
    private val shape = Path()

    // Playing effects in a fixed ring buffer; the oldest gives way when typing very fast.
    private val presses = arrayOfNulls<KeyPress>(SLOTS)
    private val letters = arrayOfNulls<String>(SLOTS)
    private val start = LongArray(SLOTS)
    private var next = 0
    private var count = 0
    private var last: KeyPress? = null
    private var lastAt = 0L

    // The press being drawn, for shapes that need it (INK's clip, LETTER's text).
    private var drawing: KeyPress? = null
    private var drawingLetter: String? = null
    private var canvasRef: Canvas? = null

    private val sink = EffectSink { kind, x, y, a, b, alpha, extra ->
        val canvas = canvasRef ?: return@EffectSink
        val al = (alpha * 255).toInt().coerceIn(0, 255)
        if (al == 0) return@EffectSink
        when (kind) {
            EffectShape.GLOW -> {
                if (a <= 0f) return@EffectSink
                matrix.setScale(a, a)
                matrix.postTranslate(x, y)
                glowShader.setLocalMatrix(matrix)
                glowPaint.shader = glowShader
                glowPaint.alpha = al
                canvas.drawCircle(x, y, a, glowPaint)
            }
            EffectShape.INK -> {
                val p = drawing ?: return@EffectSink
                clipToKey(canvas, p)
                fillPaint.alpha = al
                canvas.drawCircle(x, y, a, fillPaint)
                canvas.restore()
            }
            EffectShape.KEY_FILL -> {
                rect.set(x - a / 2, y - b / 2, x + a / 2, y + b / 2)
                fillPaint.alpha = al
                canvas.drawRoundRect(rect, extra, extra, fillPaint)
            }
            EffectShape.KEY_OUTLINE -> {
                val p = drawing
                val grow = if (p != null) (a - p.keyW) / 2 else 0f
                rect.set(x - a / 2, y - b / 2, x + a / 2, y + b / 2)
                strokePaint.alpha = al
                strokePaint.strokeWidth = extra.coerceAtLeast(1f)
                val r = (p?.corner ?: corner) + grow
                canvas.drawRoundRect(rect, r, r, strokePaint)
            }
            EffectShape.LETTER -> {
                val letter = drawingLetter ?: return@EffectSink
                textPaint.alpha = al
                textPaint.textSize = a
                // Centre the letter on (x, y).
                canvas.drawText(letter, x, y - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            EffectShape.LINE -> {
                strokePaint.alpha = al
                strokePaint.strokeWidth = extra.coerceAtLeast(1f)
                canvas.drawLine(x, y, a, b, strokePaint)
            }
            EffectShape.DOT -> {
                fillPaint.alpha = al
                canvas.drawCircle(x, y, a, fillPaint)
            }
            EffectShape.KEY_RING -> {
                val p = drawing ?: return@EffectSink
                clipToKey(canvas, p)
                strokePaint.alpha = al
                strokePaint.strokeWidth = extra.coerceAtLeast(1f)
                canvas.drawCircle(x, y, a, strokePaint)
                canvas.restore()
            }
            EffectShape.SHEEN -> {
                val p = drawing ?: return@EffectSink
                clipToKey(canvas, p)
                // A slanted band, like light catching glass.
                strokePaint.alpha = al
                strokePaint.strokeWidth = a
                val slant = p.keyH * 0.35f
                canvas.drawLine(x - slant, p.cy + p.keyH * 0.6f, x + slant, p.cy - p.keyH * 0.6f, strokePaint)
                canvas.restore()
            }
            EffectShape.STAR -> {
                // Four points with curved sides: a twinkle.
                shape.rewind()
                shape.moveTo(x, y - a)
                shape.quadTo(x, y, x + a, y)
                shape.quadTo(x, y, x, y + a)
                shape.quadTo(x, y, x - a, y)
                shape.quadTo(x, y, x, y - a)
                shape.close()
                fillPaint.alpha = al
                canvas.drawPath(shape, fillPaint)
            }
            EffectShape.HEART -> {
                // A heart a wide and a tall, centred on (x, y).
                val h = a / 2
                shape.rewind()
                shape.moveTo(x, y + h)
                shape.cubicTo(x - a * 0.95f, y + h * 0.1f, x - a * 0.55f, y - h * 1.25f, x, y - h * 0.45f)
                shape.cubicTo(x + a * 0.55f, y - h * 1.25f, x + a * 0.95f, y + h * 0.1f, x, y + h)
                shape.close()
                fillPaint.alpha = al
                canvas.drawPath(shape, fillPaint)
            }
        }
    }

    /** Saves the canvas and clips it to [p]'s key shape; the caller restores. */
    private fun clipToKey(canvas: Canvas, p: KeyPress) {
        rect.set(p.cx - p.keyW / 2, p.cy - p.keyH / 2, p.cx + p.keyW / 2, p.cy + p.keyH / 2)
        clip.rewind()
        clip.addRoundRect(rect, p.corner, p.corner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    /**
     * Takes no height of its own: it only covers the keys once the keyboard's height is known
     * (like [OverlayFrame]). Asking for "as tall as allowed" made the keyboard window fill the
     * whole screen.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    /**
     * A key was pressed: [key] is its drawn bounds (in this view's coordinates), ([x], [y]) the
     * touch, and [letter] the character it types, or null for keys like Space and Shift.
     */
    fun press(key: RectF, x: Float, y: Float, letter: String?) {
        if (!ValueAnimator.areAnimatorsEnabled()) return // the phone's "Remove animations"
        val now = SystemClock.uptimeMillis()
        val prev = last?.takeIf { now - lastAt < TRAIL_GAP_MS }
        val press = KeyPress(
            cx = key.centerX(), cy = key.centerY(), keyW = key.width(), keyH = key.height(), corner = corner,
            touchX = x, touchY = y, seed = ++count,
            fromX = prev?.cx ?: Float.NaN, fromY = prev?.cy ?: Float.NaN,
        )
        last = press
        lastAt = now
        val i = next
        next = (next + 1) % SLOTS
        presses[i] = press
        letters[i] = letter
        start[i] = now
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var playing = false
        canvasRef = canvas
        for (i in 0 until SLOTS) {
            val p = presses[i] ?: continue
            val t = (now - start[i]).toFloat() / effect.durationMs
            if (t >= 1f) { presses[i] = null; continue }
            drawing = p
            drawingLetter = letters[i]
            PressEffects.frame(effect, t, p, letters[i] != null, sink)
            playing = true
        }
        drawing = null
        drawingLetter = null
        canvasRef = null
        if (playing) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        presses.fill(null)
        last = null
    }

    private companion object {
        const val SLOTS = 8
        const val OPAQUE = 0xFF shl 24
        /** A light trail joins two presses only this close together in time. */
        const val TRAIL_GAP_MS = 700L
    }
}
