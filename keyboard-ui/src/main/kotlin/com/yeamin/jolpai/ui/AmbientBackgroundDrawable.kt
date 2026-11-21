package com.yeamin.jolpai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.os.SystemClock
import com.yeamin.jolpai.theme.Glow
import com.yeamin.jolpai.theme.LiveThemes

/**
 * The keyboard background for ambient and drifting themes: the theme's flat colour or gradient
 * with soft round glows over it.
 *
 * With [driftPeriodMs] > 0 the glows drift slowly ([LiveThemes.drift]), kept cheap on battery:
 * - about 15 frames a second (the motion is a few pixels a second, so more frames would not
 *   look smoother), and only this background is redrawn; the keys keep their own drawing;
 * - each glow's gradient is made once per size and only moved (a matrix) and faded per frame;
 * - it runs only while the keyboard is on screen (a view tells its background when it is shown
 *   or hidden), and stays still when the phone's animations are turned off ("Remove
 *   animations", animator scale 0) or battery saver is on.
 */
class AmbientBackgroundDrawable(
    private val context: Context,
    private val top: Int,
    private val bottom: Int?,
    private val glows: List<Glow>,
    private val driftPeriodMs: Long,
) : Drawable(), Animatable {

    private val basePaint = Paint()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaders = ArrayList<RadialGradient>()
    private val matrix = Matrix()
    private var running = false

    init {
        // Start hidden: a view only tells its background when visibility changes, so a drawable that
        // starts "visible" would never hear that the keyboard came on screen.
        super.setVisible(false, false)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            invalidateSelf()
            scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        basePaint.shader = bottom?.let {
            LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(), top, it, Shader.TileMode.CLAMP)
        }
        basePaint.color = top
        // Each glow: its colour at the centre fading to nothing, drawn at the origin and moved by a matrix.
        shaders.clear()
        glows.forEach { g ->
            val r = (g.radius * bounds.width()).coerceAtLeast(1f)
            shaders += RadialGradient(0f, 0f, r, intArrayOf(g.color or OPAQUE, g.color and RGB), null, Shader.TileMode.CLAMP)
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        canvas.drawRect(b, basePaint)
        // Still: the resting place. Drifting: where the loop is now, on the uptime clock, so a rebuilt
        // keyboard carries on from the same point instead of jumping back.
        val now = if (running && driftPeriodMs > 0) LiveThemes.drift(glows, (SystemClock.uptimeMillis() % driftPeriodMs).toFloat() / driftPeriodMs) else glows
        canvas.save()
        canvas.clipRect(b)
        now.forEachIndexed { i, g ->
            val shader = shaders.getOrNull(i) ?: return@forEachIndexed
            val cx = b.left + g.x * b.width()
            val cy = b.top + g.y * b.height()
            matrix.setTranslate(cx, cy)
            shader.setLocalMatrix(matrix)
            glowPaint.shader = shader
            glowPaint.alpha = g.color ushr 24
            canvas.drawCircle(cx, cy, glows[i].radius * b.width(), glowPaint)
        }
        canvas.restore()
    }

    // ------------------------------------------------------------------ animation

    /** Whether drifting is allowed now: a drifting theme, animations on, battery saver off. */
    private fun mayAnimate(): Boolean {
        if (driftPeriodMs <= 0 || glows.isEmpty()) return false
        if (!ValueAnimator.areAnimatorsEnabled()) return false
        val power = context.getSystemService(PowerManager::class.java)
        return power?.isPowerSaveMode != true
    }

    override fun start() {
        if (running || !mayAnimate()) return
        running = true
        scheduleSelf(tick, SystemClock.uptimeMillis() + FRAME_MS)
    }

    override fun stop() {
        if (!running) return
        running = false
        unscheduleSelf(tick)
        invalidateSelf() // settle on the resting place
    }

    override fun isRunning(): Boolean = running

    /** A view shows or hides its background with itself: drift only while it can be seen. */
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) start() else stop()
        return changed
    }

    override fun setAlpha(alpha: Int) {
        basePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        basePaint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = if (top ushr 24 == 0xFF && (bottom ?: top) ushr 24 == 0xFF) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT

    private companion object {
        /** About 15 frames a second. */
        const val FRAME_MS = 66L
        const val RGB = 0x00FFFFFF
        const val OPAQUE = 0xFF shl 24
    }
}
