package com.yeamin.jolpai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout

/**
 * A frame for content laid over the keys (Compose panels). It takes no height of its own:
 * the parent sizes itself to the keys, then gives this frame exactly that height. Panels
 * that fill their space would otherwise stretch the input window to the full screen.
 */
@SuppressLint("ViewConstructor")
class OverlayFrame(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0)
        }
    }
}
