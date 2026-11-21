package com.yeamin.jolpai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.yeamin.jolpai.core.gesture.SwipeDirection

/**
 * Shows a top-bar swipe as it happens: a pill with the action's icon and name ("Undo") slides
 * in from the side the finger is heading to and fills with the accent colour as the swipe
 * goes on. Full means "let go to do it" (the pill turns solid accent and grows a little). When
 * the action runs, the pill pops and glides off in the swipe's direction; when the swipe is
 * given up, it slides back out. Drawn over the top bar; takes no touches.
 */
class SwipeHintView(context: Context, private val palette: KeyboardPalette) : View(context) {
    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14 * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()

    private var direction = SwipeDirection.LEFT
    private var label = ""
    private var icon: Drawable? = null
    private var iconRole: KeyboardIcon? = null
    private var progress = 0f
    private var armed = false

    /** 0 hidden … 1 fully shown; animated on fire and cancel. */
    private var shown = 0f
    /** Fire animation: 0 … 1 (pop, then glide off), or -1 when not firing. */
    private var firing = -1f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The swipe is heading to [dir] for [title]: [progress] 0…1, [armed] when letting go would run it. */
    fun show(dir: SwipeDirection, title: String, iconFor: KeyboardIcon, progress: Float, armed: Boolean) {
        animator?.cancel()
        firing = -1f
        if (dir != direction || title != label || iconRole != iconFor || icon == null) {
            direction = dir
            label = title
            iconRole = iconFor
            // The plain vector (not the sized wrapper), so it can be re-tinted when the pill arms.
            icon = androidx.core.content.ContextCompat.getDrawable(context, iconFor.res)?.mutate()
        }
        this.progress = progress
        this.armed = armed
        shown = 1f
        invalidate()
    }

    /** The action ran: pop and glide off. */
    fun fire() {
        if (shown == 0f) return
        armed = true
        progress = 1f
        animate(FIRE_MS) { t -> firing = t; if (t >= 1f) { shown = 0f; firing = -1f } }
    }

    /** The swipe was given up: slide back out. */
    fun cancel() {
        if (shown == 0f) return
        val from = progress
        animate(CANCEL_MS) { t ->
            progress = from * (1 - t)
            shown = 1 - t
        }
    }

    private fun animate(ms: Long, step: (Float) -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = DecelerateInterpolator()
            addUpdateListener { step(it.animatedValue as Float); invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        shown = 0f
    }

    override fun onDraw(canvas: Canvas) {
        if (shown <= 0f || label.isEmpty()) return
        val iconSize = 18 * density
        val padH = 14 * density
        val gap = 6 * density
        val h = 32 * density
        val w = padH * 2 + iconSize + gap + textPaint.measureText(label)
        val margin = 10 * density
        val ease = 1 - (1 - progress) * (1 - progress) // slides in quickly, settles gently

        // Where the pill sits: it slides in from the side the finger heads to (or drops from the top).
        val cy = height / 2f
        var cx = when (direction) {
            SwipeDirection.LEFT -> -w / 2 + (w + margin) * ease
            SwipeDirection.RIGHT -> width + w / 2 - (w + margin) * ease
            else -> width / 2f
        }
        var top = cy - h / 2
        if (direction == SwipeDirection.DOWN || direction == SwipeDirection.UP) top = -h + (cy + h / 2) * ease

        var scale = if (armed) 1.06f else 1f
        var alpha = (0.55f + 0.45f * progress) * shown
        if (firing >= 0f) {
            // Pop (first third), then glide off in the swipe's direction and fade.
            val pop = (firing / 0.33f).coerceAtMost(1f)
            scale = 1.06f + 0.12f * kotlin.math.sin(Math.PI * pop).toFloat()
            val glide = ((firing - 0.33f) / 0.67f).coerceIn(0f, 1f)
            val off = (w + margin) * glide * glide
            when (direction) {
                SwipeDirection.LEFT -> cx -= off
                SwipeDirection.RIGHT -> cx += off
                else -> top += off * 0.5f
            }
            alpha = 1f - glide
        }

        canvas.save()
        canvas.scale(scale, scale, cx, top + h / 2)
        rect.set(cx - w / 2, top, cx + w / 2, top + h)
        val a = (alpha * 255).toInt().coerceIn(0, 255)

        // The track: a raised, key-coloured pill.
        trackPaint.color = ColorUtils.compositeColors(palette.popup, palette.background or OPAQUE)
        trackPaint.alpha = a
        canvas.drawRoundRect(rect, h / 2, h / 2, trackPaint)

        // The accent fills it from the side the swipe started on, solid once armed.
        fillPaint.color = palette.accent
        fillPaint.alpha = (a * if (armed) 1f else 0.35f).toInt()
        canvas.save()
        val fillW = if (armed) w else w * progress
        when (direction) {
            SwipeDirection.LEFT -> canvas.clipRect(rect.right - fillW, rect.top, rect.right, rect.bottom)
            SwipeDirection.RIGHT -> canvas.clipRect(rect.left, rect.top, rect.left + fillW, rect.bottom)
            else -> canvas.clipRect(rect.left, rect.top, rect.right, rect.top + h * (if (armed) 1f else progress))
        }
        canvas.drawRoundRect(rect, h / 2, h / 2, fillPaint)
        canvas.restore()

        // Icon and name: on the accent once armed, in the label colour before.
        val ink = if (armed) palette.onAccent else palette.text
        icon?.let { d ->
            d.setTint(ink)
            d.alpha = a
            val left = (rect.left + padH).toInt()
            val t = (top + (h - iconSize) / 2).toInt()
            d.setBounds(left, t, left + iconSize.toInt(), t + iconSize.toInt())
            d.draw(canvas)
        }
        textPaint.color = ink
        textPaint.alpha = a
        val baseline = top + h / 2 - (textPaint.ascent() + textPaint.descent()) / 2
        canvas.drawText(label, rect.left + padH + iconSize + gap, baseline, textPaint)
        canvas.restore()
    }

    private companion object {
        const val FIRE_MS = 380L
        const val CANCEL_MS = 180L
        const val OPAQUE = 0xFF shl 24
    }
}
