package com.yeamin.lipi.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Draws a bitmap scaled to cover its bounds, cropping the overflow (like ImageView centerCrop). */
class CenterCropDrawable(private val bitmap: Bitmap) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()

    override fun onBoundsChange(bounds: Rect) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = maxOf(bounds.width() / bw, bounds.height() / bh)
        val w = (bounds.width() / scale).toInt()
        val h = (bounds.height() / scale).toInt()
        val left = ((bw - w) / 2).toInt()
        val top = ((bh - h) / 2).toInt()
        src.set(left, top, left + w, top + h)
    }

    override fun draw(canvas: Canvas) {
        if (!bounds.isEmpty) canvas.drawBitmap(bitmap, src, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
