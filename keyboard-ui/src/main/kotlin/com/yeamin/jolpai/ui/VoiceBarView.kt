package com.yeamin.jolpai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.yeamin.jolpai.core.gesture.SwipeClassifier
import com.yeamin.jolpai.core.gesture.SwipeDirection
import kotlin.math.abs
import kotlin.math.sin

/** What the voice bar shows. */
data class VoiceBarModel(
    val listening: Boolean,
    /** Words heard so far, or a status ("Speak now", an error). */
    val text: String,
    /** True when [text] is the user's words (shown brighter) rather than a status. */
    val isSpeech: Boolean,
    /** 0–1 loudness, for the animations. */
    val level: Float,
    val bangla: Boolean,
)

/**
 * How the keyboard looks while dictating: the full keyboard with the voice bar on top, the
 * bar alone (keys collapsed), or a small floating pill, wide or tall, that can be dragged.
 */
enum class VoiceSize { FULL, COLLAPSED, PILL_H, PILL_V }

/**
 * Voice typing controls, like Google voice typing. Used twice by the keyboard:
 *
 * - [pill] = false, the **bar** over the toolbar: `[● mic] ▁▃▅▃▁ words… [বাংলা] [⌄] [✕]`
 *   (collapsed: `… [⌫] [⤡] [⌃] [✕]`). Swipe down to collapse the keys, up to bring them back,
 *   left or right to make it a pill.
 * - [pill] = true, the **pill** floating over the app, a row of round buttons
 *   `⠿ ● ▁▃▅ EN ⌫ ⟲ ⤢ ✕` (wide) or the same as a column (tall). Drag it anywhere by any part
 *   that isn't a button; the keyboard turns it tall when it is dropped against the left or
 *   right edge, and wide again when dragged back towards the middle. ⟲ turns it too.
 *
 * The words you say appear in the text field as you speak, so the pill doesn't repeat them.
 */
@SuppressLint("ViewConstructor")
class VoiceBarView(
    context: Context,
    private val palette: KeyboardPalette,
    private val listener: Listener,
    private val pill: Boolean,
) : LinearLayout(context) {

    interface Listener {
        fun onMic()
        fun onLanguage()
        fun onClose()
        fun onBackspace()
        fun onSize(size: VoiceSize)

        /** The pill was dragged by ([dx], [dy]) pixels; [done] when the finger lifts. */
        fun onDrag(dx: Float, dy: Float, done: Boolean) {}
    }

    private val density = resources.displayMetrics.density
    private var size = if (pill) VoiceSize.PILL_H else VoiceSize.FULL
    private var model: VoiceBarModel? = null
    private val vertical: Boolean get() = size == VoiceSize.PILL_V

    /** The pill's handle (drawn only; the whole pill drags). */
    private val grip = ImageView(context).apply {
        setImageDrawable(KeyboardIcon.GRIP_DOTS.drawable(context, palette.hint, 20 * density))
        scaleType = ImageView.ScaleType.CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val mic = MicPulseView()
    private val wave = WaveView()
    private val text = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.START // newest words stay visible
        gravity = Gravity.CENTER_VERTICAL
        setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0)
    }
    private val language = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(palette.text)
        gravity = Gravity.CENTER
        isSingleLine = true
        background = rounded(palette.functionKey)
        contentDescription = "Switch voice language"
        setOnClickListener { listener.onLanguage() }
    }

    // Tap deletes one character; holding keeps deleting (like the backspace key).
    @SuppressLint("ClickableViewAccessibility") // RepeatDelete calls performClick() for a tap
    private val backspace = circle(KeyboardIcon.BACKSPACE, "Delete") {}.apply { setOnTouchListener(RepeatDelete()) }
    private val rotate = circle(KeyboardIcon.ROTATE, "Turn") { listener.onSize(if (vertical) VoiceSize.PILL_H else VoiceSize.PILL_V) }
    private val shrink = icon(KeyboardIcon.SHRINK, "Make it small") { listener.onSize(VoiceSize.PILL_H) }
    private val collapse = icon(KeyboardIcon.COLLAPSE, "Collapse the keyboard") {
        listener.onSize(if (size == VoiceSize.FULL) VoiceSize.COLLAPSED else VoiceSize.FULL)
    }
    private val expand = circle(KeyboardIcon.EXPAND, "Back to the keyboard") { listener.onSize(VoiceSize.FULL) }
    private val close = icon(KeyboardIcon.CLOSE, "Stop voice typing") { listener.onClose() }
    private val closeRound = circle(KeyboardIcon.CLOSE, "Stop voice typing") { listener.onClose() }
    private val barBackspace = icon(KeyboardIcon.BACKSPACE, "Delete") {}.apply {
        @SuppressLint("ClickableViewAccessibility")
        setOnTouchListener(RepeatDelete())
    }

    init {
        gravity = Gravity.CENTER
        mic.setOnClickListener { listener.onMic() }
        arrange()
    }

    fun show(m: VoiceBarModel) {
        model = m
        text.text = m.text
        text.setTextColor(if (m.isSpeech) palette.text else palette.hint)
        language.text = if (m.bangla) (if (pill) "বা" else "বাংলা") else "EN"
        mic.contentDescription = if (m.listening) "Pause voice typing" else "Resume voice typing"
        mic.invalidate()
        wave.invalidate()
    }

    fun setSize(s: VoiceSize) {
        if (s == size) return
        size = s
        arrange()
        model?.let(::show)
    }

    /** Lays out the pieces for the bar, the wide pill or the tall pill. */
    private fun arrange() {
        removeAllViews()
        orientation = if (vertical) VERTICAL else HORIZONTAL
        fun d(v: Float) = (v * density).toInt()
        fun add(view: View, w: Int, h: Int, weight: Float = 0f, margin: Int = 0) {
            addView(
                view,
                LayoutParams(w, h, weight).apply {
                    gravity = Gravity.CENTER
                    if (vertical) setMargins(0, margin, 0, margin) else setMargins(margin, 0, margin, 0)
                },
            )
        }
        val mp = LayoutParams.MATCH_PARENT
        when {
            !pill -> {
                setPadding(d(4f), 0, d(2f), 0)
                add(mic, d(44f), mp)
                add(wave, d(52f), mp)
                add(text, 0, mp, 1f)
                language.setPadding(d(10f), 0, d(10f), 0)
                add(language, LayoutParams.WRAP_CONTENT, d(30f), margin = d(2f))
                if (size == VoiceSize.COLLAPSED) {
                    add(barBackspace, d(40f), mp)
                    add(shrink, d(40f), mp)
                }
                add(collapse, d(40f), mp)
                add(close, d(40f), mp)
                setIcon(collapse, if (size == VoiceSize.FULL) KeyboardIcon.COLLAPSE else KeyboardIcon.EXPAND_UP)
                collapse.contentDescription = if (size == VoiceSize.FULL) "Collapse the keyboard" else "Show the keyboard"
                background = null
                elevation = 0f
            }
            else -> {
                // Pill: a row (or column) of evenly sized round controls.
                val btn = d(40f)
                val gap = d(2f)
                language.setPadding(0, 0, 0, 0)
                if (vertical) setPadding(d(6f), d(8f), d(6f), d(8f)) else setPadding(d(6f), d(6f), d(8f), d(6f))
                if (vertical) add(grip, btn, d(20f)) else add(grip, d(20f), btn)
                add(mic, d(48f), d(48f), margin = gap)
                if (vertical) add(wave, btn, d(36f), margin = gap) else add(wave, d(36f), btn, margin = gap)
                add(language, btn, d(32f).coerceAtMost(btn), margin = gap)
                add(backspace, btn, btn, margin = gap)
                add(rotate, btn, btn, margin = gap)
                add(expand, btn, btn, margin = gap)
                add(closeRound, btn, btn, margin = gap)
                background = rounded(palette.background, outline = true)
                elevation = 10 * density
            }
        }
        wave.vertical = vertical
    }

    // ------------------------------------------------------------------ touches

    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var claimed = false
    private var dragging = false

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                lastRawX = e.rawX
                lastRawY = e.rawY
                claimed = false
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> if (!claimed && e.pointerCount == 1) {
                if (pill) {
                    // Pill: moving the finger anywhere (even from a button) drags the pill.
                    if (abs(e.x - downX) + abs(e.y - downY) > DRAG_SLOP_DP * density) {
                        claimed = true
                        dragging = true
                        lastRawX = e.rawX
                        lastRawY = e.rawY
                        return true
                    }
                    return false
                }
                val dir = SwipeClassifier.classify(e.x - downX, e.y - downY, SWIPE_DP * density, 1.5f) ?: return false
                val next = nextSize(dir) ?: return false
                claimed = true
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                listener.onSize(next)
                return true
            }
        }
        return false
    }

    /** The bar: down collapses the keys, up brings them back, sideways makes a pill. */
    private fun nextSize(dir: SwipeDirection): VoiceSize? = when (size) {
        VoiceSize.FULL -> when (dir) {
            SwipeDirection.DOWN -> VoiceSize.COLLAPSED
            SwipeDirection.LEFT, SwipeDirection.RIGHT -> VoiceSize.PILL_H
            else -> null
        }
        VoiceSize.COLLAPSED -> when (dir) {
            SwipeDirection.UP -> VoiceSize.FULL
            SwipeDirection.LEFT, SwipeDirection.RIGHT -> VoiceSize.PILL_H
            else -> null
        }
        else -> null
    }

    @SuppressLint("ClickableViewAccessibility") // the pill's body only drags; its buttons click
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pill) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    dragging = true
                    listener.onDrag(e.rawX - lastRawX, e.rawY - lastRawY, false)
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) listener.onDrag(0f, 0f, true)
                    dragging = false
                    claimed = false
                    return true
                }
            }
            return true
        }
        return claimed
    }

    /** Backspace: delete now, then repeat while held. */
    private inner class RepeatDelete : OnTouchListener {
        private val handler = Handler(Looper.getMainLooper())
        private var view: View? = null
        private val repeat = object : Runnable {
            override fun run() {
                listener.onBackspace()
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handler.postDelayed(this, REPEAT_MS)
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view = v
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener.onBackspace()
                    handler.postDelayed(repeat, REPEAT_START_MS)
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            return true
        }
    }

    // ------------------------------------------------------------------ pieces

    private fun rounded(color: Int, outline: Boolean = false) = GradientDrawable().apply {
        cornerRadius = 100 * density
        setColor(color)
        if (outline) setStroke(density.toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(palette.hint, 0x50))
    }

    private fun icon(i: KeyboardIcon, description: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageDrawable(i.drawable(context, palette.text, 22 * density))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        background = RippleDrawable(ColorStateList.valueOf(palette.keyPressed), null, null).apply { radius = (18 * density).toInt() }
        setOnClickListener { onClick() }
    }

    /** A round pill button with a soft fill, so every control in the pill looks the same. */
    private fun circle(i: KeyboardIcon, description: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageDrawable(i.drawable(context, palette.text, 20 * density))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        background = RippleDrawable(
            ColorStateList.valueOf(palette.keyPressed),
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.functionKey) },
            null,
        )
        setOnClickListener { onClick() }
    }

    private fun setIcon(view: ImageView, i: KeyboardIcon) = view.setImageDrawable(i.drawable(context, palette.text, 22 * density))

    /** The mic: an accent circle with rings that breathe and swell with your voice. */
    private inner class MicPulseView : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val icons = KeyIconCache(context, intArrayOf(palette.onAccent, palette.text))
        private var smooth = 0f

        init {
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        override fun onDraw(canvas: Canvas) {
            val m = model
            val listening = m?.listening == true
            val cx = width / 2f
            val cy = height / 2f
            val r = (minOf(width, height) / 2f - 6 * density).coerceAtMost(17 * density)
            smooth += ((m?.level ?: 0f) - smooth) * 0.25f
            if (listening) {
                val t = (SystemClock.uptimeMillis() % 1400) / 1400f
                fill.color = ColorUtils.setAlphaComponent(palette.accent, (90 * (1 - t)).toInt())
                canvas.drawCircle(cx, cy, r + 6 * density * t, fill)
                fill.color = ColorUtils.setAlphaComponent(palette.accent, 70)
                canvas.drawCircle(cx, cy, r + 5 * density * smooth.coerceIn(0f, 1f), fill)
            }
            fill.color = if (listening) palette.accent else palette.functionKey
            canvas.drawCircle(cx, cy, r, fill)
            icons.draw(canvas, if (listening) KeyboardIcon.MIC_ON else KeyboardIcon.MIC, if (listening) 0 else 1, cx, cy, r * 1.15f)
            if (listening) postInvalidateOnAnimation()
        }
    }

    /** Rounded bars that move with the voice; stacked sideways in the tall pill. */
    private inner class WaveView : View(context) {
        var vertical = false
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var smooth = 0f

        override fun onDraw(canvas: Canvas) {
            val m = model
            val listening = m?.listening == true
            smooth += ((if (listening) m!!.level else 0f) - smooth) * 0.3f
            bar.color = if (listening) palette.accent else palette.hint
            val along = if (vertical) height else width
            val n = if (along < 44 * density) 4 else 5
            val w = 3.5f * density
            val gap = 3.5f * density
            val total = n * w + (n - 1) * gap
            val t = SystemClock.uptimeMillis() / 140f
            val across = if (vertical) width else height
            val maxH = across * 0.55f
            var pos = (along - total) / 2f
            for (i in 0 until n) {
                val wobble = if (listening) 0.35f + 0.65f * (0.5f + 0.5f * sin(t + i * 1.3f)) else 0f
                val h = (w + maxH * (0.15f + 0.85f * smooth.coerceIn(0f, 1f)) * wobble).coerceAtLeast(w)
                if (vertical) rect.set((width - h) / 2f, pos, (width + h) / 2f, pos + w) else rect.set(pos, (height - h) / 2f, pos + w, (height + h) / 2f)
                canvas.drawRoundRect(rect, w / 2, w / 2, bar)
                pos += w + gap
            }
            if (listening) postInvalidateOnAnimation()
        }
    }

    private companion object {
        const val SWIPE_DP = 32f
        const val DRAG_SLOP_DP = 8f
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 70L
    }
}
