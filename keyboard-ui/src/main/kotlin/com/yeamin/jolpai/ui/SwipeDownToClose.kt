package com.yeamin.jolpai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Holds one of the sections that open above the toolbar (translate, check, emoji, clipboard)
 * and closes it with a swipe down. The section follows the finger, sliding down behind the
 * toolbar and fading; let go past a third of its height (or flick down) and it closes with
 * [onClose], otherwise it springs back.
 *
 * Taps, sideways scrolling (chips, categories) and lists that can still scroll up are left
 * alone: the swipe is only claimed when it is clearly downward and nothing under the finger
 * would scroll instead.
 */
@SuppressLint("ViewConstructor")
class SwipeDownToClose(context: Context, private val onClose: () -> Unit) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var dragStartRawY = 0f
    private var downTime = 0L
    private var dragging = false
    private var blocked = false
    private var pastThreshold = false
    private var velocity: VelocityTracker? = null

    /** Closed by a swipe: put the section back once its own hide has finished. */
    private var awaitingReset = false

    private val threshold: Float get() = maxOf(56 * density, height / 3f)

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                downTime = e.eventTime
                dragging = false
                blocked = false
                track(e, reset = true)
            }
            MotionEvent.ACTION_MOVE -> {
                track(e)
                if (dragging || blocked || e.pointerCount > 1) return dragging
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy)) { blocked = true; return false } // sideways: scrolling chips
                if (dy < -slop) { blocked = true; return false }
                if (dy > slop * 1.5f && dy > 2 * abs(dx)) {
                    // Too late (a long-press menu is up) or a list under the finger can scroll up: not ours.
                    if (e.eventTime - downTime > longPress || canScrollUpAt(this, downX, downY)) { blocked = true; return false }
                    dragging = true
                    pastThreshold = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragStartRawY = e.rawY - 1 // start following from here, without a jump
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> recycle()
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility") // only handles a claimed swipe
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) return false
        track(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> follow((e.rawY - dragStartRawY).coerceAtLeast(0f)) // raw: we move under the finger
            MotionEvent.ACTION_UP -> {
                val v = velocity?.let { it.computeCurrentVelocity(1000); it.yVelocity } ?: 0f
                val dy = translationY
                dragging = false
                recycle()
                if (dy > threshold || (v > 900 * density && dy > 16 * density)) close() else settle()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                recycle()
                settle()
            }
        }
        return true
    }

    private fun follow(dy: Float) {
        translationY = dy
        alpha = 1f - 0.45f * (dy / (height.coerceAtLeast(1))).coerceIn(0f, 1f)
        val past = dy > threshold
        if (past != pastThreshold) {
            pastThreshold = past
            if (past) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Not far enough: spring back into place. */
    private fun settle() {
        animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(OvershootInterpolator(1.2f)).withEndAction(null).start()
    }

    /** Slides the rest of the way down, then closes; the section's own hide runs out of sight. */
    private fun close() {
        val remaining = (height - translationY).coerceAtLeast(0f)
        animate()
            .translationY(height.toFloat()).alpha(0f)
            .setDuration((90 + 120 * remaining / height.coerceAtLeast(1)).toLong())
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                awaitingReset = true
                onClose()
                // Back in place once the section is gone (or anyway soon, if it was reopened).
                postDelayed({ reset() }, 260)
            }
            .start()
    }

    private fun reset() {
        if (!awaitingReset) return
        awaitingReset = false
        animate().cancel()
        translationY = 0f
        alpha = 1f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (awaitingReset && (0 until childCount).all { getChildAt(it).visibility == View.GONE }) post { reset() }
    }

    private fun track(e: MotionEvent, reset: Boolean = false) {
        if (reset) recycle()
        val t = velocity ?: VelocityTracker.obtain().also { velocity = it }
        // Raw coordinates: our own translation moves under the finger while dragging.
        val copy = MotionEvent.obtain(e).apply { setLocation(e.rawX, e.rawY) }
        t.addMovement(copy)
        copy.recycle()
    }

    private fun recycle() {
        velocity?.recycle()
        velocity = null
    }

    /** True when some view under ([x], [y]) inside [group] can still scroll its content up. */
    private fun canScrollUpAt(group: ViewGroup, x: Float, y: Float): Boolean {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val cx = x + group.scrollX - child.left - child.translationX
            val cy = y + group.scrollY - child.top - child.translationY
            if (cx < 0 || cy < 0 || cx >= child.width || cy >= child.height) continue
            if (child.canScrollVertically(-1)) return true
            if (child is ViewGroup && canScrollUpAt(child, cx, cy)) return true
        }
        return false
    }
}
