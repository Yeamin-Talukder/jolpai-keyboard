package com.yeamin.lipi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withScale
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.yeamin.lipi.core.model.EditCommand
import com.yeamin.lipi.core.model.Key
import com.yeamin.lipi.core.model.KeyAction
import com.yeamin.lipi.core.model.KeyIcon
import com.yeamin.lipi.core.model.KeyStyle
import com.yeamin.lipi.core.model.KeyboardLayout
import com.yeamin.lipi.core.model.ShiftState
import com.yeamin.lipi.core.touch.TouchCalibration
import com.yeamin.lipi.core.gesture.GestureDispatcher
import com.yeamin.lipi.core.gesture.GestureType
import com.yeamin.lipi.core.gesture.GestureZone
import com.yeamin.lipi.core.gesture.SwipeClassifier
import com.yeamin.lipi.core.touch.TouchResolver
import com.yeamin.lipi.core.touch.TouchTarget
import com.yeamin.lipi.suggest.NextChars
import android.os.SystemClock

/**
 * The key grid, drawn directly on a Canvas for the lowest possible latency.
 *
 * Touch model:
 * - Character keys commit on release. If another finger lands while one is still down
 *   (fast two-thumb typing), the earlier key is committed at that moment so the order of
 *   letters always matches the order of presses.
 * - Delete acts on press, then repeats while held.
 * - Holding a key that has choices opens them above the key; slide to pick, release to commit.
 * - Sliding a finger from one character key to another moves the press to the new key.
 *
 * Nothing in [onDraw] or the touch path allocates, apart from objects created once per press.
 *
 * Accessibility: keys are not separate views, so each key is exposed to TalkBack as a
 * virtual view ([ExploreByTouchHelper]) with a spoken name ([KeyAccessibility]). With
 * TalkBack's touch exploration on, lifting the finger over a key types it ("lift to type"),
 * and double-tap also works. Taps are handled in [onTouchEvent] per pointer, which is why the
 * performClick lint check doesn't apply here.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility") // created in code only; see note above
class KeyboardView(context: Context, private val palette: KeyboardPalette) : View(context) {

    fun interface Listener {
        fun onKeyAction(action: KeyAction)
    }

    var listener: Listener? = null

    /** Vibration and sound for presses; null uses the plain system key haptic. */
    var feedback: KeyFeedback? = null
    var popup: KeyPopupView? = null

    private class Cell(val key: Key, val hit: RectF, val draw: RectF) {
        // Prepared once, so drawing doesn't build strings.
        val label: String = DisplayLabel.of(key.label)
        val hint: String? = key.hint?.let(DisplayLabel::of)
    }

    private var layout = KeyboardLayout(emptyList())
    private var shift = ShiftState.OFF
    private var cells: List<Cell> = emptyList()
    private var targets: List<TouchTarget> = emptyList()

    /** Next-letter probabilities: likely letters win touches near their edges (predictive touch). */
    var nextChars: NextChars? = null

    /** This user's learned touch offsets, used with [nextChars]; learned from while [learnTouches]. */
    var calibration: TouchCalibration? = null
    var learnTouches = false

    private class Tap(val char: Char, val x: Float, val y: Float, val time: Long)
    private var lastTap: Tap? = null
    private var correction: Tap? = null

    private val density = resources.displayMetrics.density
    private val padH = 3 * density
    private val padV = 4 * density

    // Key shape and spacing come from the theme: the gap is split between neighbours.
    private val gapH = palette.keyGapDp / 2 * density
    private val gapV = (palette.keyGapDp / 2 + 2) * density
    private val radius = palette.keyRadiusDp * density

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.shadow }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = palette.border ?: 0
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        if (palette.boldLabels) typeface = Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = palette.hint
        alpha = 210
        textSize = sp(9.5f) * palette.labelScale
    }
    // Colour roles for icons: 0 = label, 1 = on the accent colour, 2 = accent.
    private val icons = KeyIconCache(context, intArrayOf(palette.text, palette.onAccent, palette.accent))
    private val regular: Typeface = if (palette.boldLabels) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    private val medium: Typeface = if (palette.boldLabels) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Keyboard height relative to normal (from the size setting), e.g. 1.1 = 10 % taller. */
    var heightScale: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    private val handler = Handler(Looper.getMainLooper())
    private val pointers = HashMap<Int, Pointer>(4)
    private val location = IntArray(2)
    private val popupLocation = IntArray(2)

    private inner class Pointer(val id: Int, var cell: Cell?, val downX: Float, val downY: Float) {
        /** The key first pressed (the finger may slide to another). */
        val firstCell = cell
        var showingChoices = false
        var repeating = false
        var consumed = false

        /** A space-bar or backspace swipe is in progress; [swipeSteps] steps were emitted so far. */
        var swiping = false
        var swipeSteps = 0
        val longPress = Runnable { onLongPress(this) }
        val repeat = Runnable { onRepeat(this) }

        /** Space held still: trackpad mode. [trackX]/[trackY] are where the last cursor step happened. */
        var trackpad = false
        var lastX = downX
        var lastY = downY
        var trackX = 0f
        var trackY = 0f
        val trackpadStart = Runnable { onTrackpad(this) }

        /** A long-press gesture shortcut on this key (instead of its menu). */
        val gestureHold = Runnable { onGestureHold(this) }
    }

    /** Custom gesture shortcuts; null when off. */
    var gestures: GestureDispatcher? = null
        set(value) {
            if (value == null) flushPendingTap()
            field = value
        }

    /** A tap held back to see if it becomes a double-tap shortcut. */
    private class PendingTap(val cell: Cell, val action: KeyAction, val zone: GestureZone)
    private var pendingTap: PendingTap? = null
    private val flushPending = Runnable { flushPendingTap() }

    private fun flushPendingTap() {
        val pending = pendingTap ?: return
        pendingTap = null
        handler.removeCallbacks(flushPending)
        emit(pending.action)
    }

    /** Runs a recognised gesture: ends this touch (nothing else will come of it) and emits once. */
    private fun fireGesture(p: Pointer, action: KeyAction) {
        p.consumed = true
        handler.removeCallbacks(p.longPress)
        handler.removeCallbacks(p.repeat)
        handler.removeCallbacks(p.trackpadStart)
        handler.removeCallbacks(p.gestureHold)
        popup?.hideAll()
        feedback?.longPress(this) ?: performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        emit(action)
        invalidate()
    }

    private fun onGestureHold(p: Pointer) {
        if (!pointers.containsValue(p) || p.consumed || p.swiping || p.trackpad || p.showingChoices) return
        val zone = p.firstCell?.key?.zone ?: return
        gestures?.actionFor(zone, GestureType.LONG_PRESS)?.let { fireGesture(p, it) }
    }

    /** A swipe shortcut from this touch, once it is long and straight enough. */
    private fun checkSwipeGesture(p: Pointer, x: Float, y: Float): Boolean {
        val g = gestures ?: return false
        if (p.consumed || p.swiping || p.trackpad || p.showingChoices || p.repeating) return false
        val zone = p.firstCell?.key?.zone ?: return false
        if (!g.hasSwipe(zone)) return false
        val t = g.thresholds
        val dir = SwipeClassifier.classify(x - p.downX, y - p.downY, t.minSwipeDp * density, t.dominance) ?: return false
        val action = g.actionFor(zone, GestureType.swipe(dir)) ?: return false
        fireGesture(p, action)
        return true
    }

    /** True when a shortcut owns swipes this way, so the built-in swipe must not start. */
    private fun gestureOwns(zone: GestureZone?, dx: Float, dy: Float): Boolean {
        val g = gestures ?: return false
        if (zone == null) return false
        if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) return g.hasVerticalSwipe(zone)
        return g.bindingFor(zone, if (dx < 0) GestureType.SWIPE_LEFT else GestureType.SWIPE_RIGHT) != null
    }

    /**
     * Swipe along the space bar to move the cursor (Gboard), and swipe left from backspace to
     * select words: move back right to shrink the range, release to delete it. When on,
     * backspace deletes on release instead of on press, so a swipe doesn't first delete a character.
     */
    var cursorSwipe = true

    /** In trackpad mode (hold the space bar), moving up and down moves between lines. */
    var trackpadVertical = true

    /** Hold time before a key shows its hidden characters (user setting). */
    var longPressMs = LONG_PRESS_MS

    /** True while a finger is in trackpad mode: keys are drawn blank, like iOS. */
    private var trackpadActive = false

    private val swipeStart = 14 * density
    private val cursorStep = 11 * density
    private val wordStep = 26 * density
    private val trackStepX = 8 * density
    private val trackStepY = 28 * density

    /** Exposes every key to accessibility services as its own virtual view. */
    private val accessibility = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val i = cells.indexOfFirst { it.hit.contains(x, y) }
            return if (i >= 0) i else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            cells.indices.forEach { virtualViewIds.add(it) }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val cell = cells.getOrNull(virtualViewId)
            if (cell == null) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            node.contentDescription = KeyAccessibility.description(cell.key, shift)
            node.setBoundsInParent(Rect().also { cell.draw.roundOut(it) })
            node.className = "android.widget.Button"
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            val cell = cells.getOrNull(virtualViewId) ?: return false
            emit(cell.key.action)
            return true
        }
    }

    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)

    init {
        contentDescription = "Keyboard"
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        // TalkBack "lift to type": exploring ends with the finger over a key → type that key.
        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT && accessibilityManager?.isTouchExplorationEnabled == true) {
            val inside = event.x >= 0 && event.y >= 0 && event.x < width && event.y < height
            if (inside) findCell(event.x, event.y)?.let { emit(it.key.action) }
        }
        return accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    fun setKeyboard(newLayout: KeyboardLayout, newShift: ShiftState) {
        val changed = newLayout !== layout || newShift != shift
        if (newLayout !== layout) {
            val rowsChanged = newLayout.rows.size != layout.rows.size
            layout = newLayout
            computeCells()
            if (rowsChanged) requestLayout()
        }
        shift = newShift
        invalidate()
        if (changed) accessibility.invalidateRoot()
    }

    /** Ends every touch in progress, e.g. when the keyboard is hidden. */
    fun cancelTouches() {
        pointers.values.forEach {
            handler.removeCallbacks(it.longPress)
            handler.removeCallbacks(it.repeat)
            handler.removeCallbacks(it.trackpadStart)
            handler.removeCallbacks(it.gestureHold)
        }
        pointers.clear()
        trackpadActive = false
        flushPendingTap()
        popup?.hideAll()
        invalidate()
    }

    private val rowHeight: Float
        get() {
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            // A number row adds a fifth row; keep the keyboard's total height about the same.
            val rowsFactor = if (layout.rows.size > 4) 4f / layout.rows.size * 1.12f else 1f
            return (if (landscape) 44 else 56) * density * heightScale * rowsFactor.coerceAtMost(1f)
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout.rows.size.coerceAtLeast(4)
        setMeasuredDimension(width, (rows * rowHeight + 2 * padV).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeCells()

    private fun computeCells() {
        if (width == 0 || layout.rows.isEmpty()) {
            cells = emptyList()
            return
        }
        val unit = (width - 2 * padH) / layout.units
        val rh = rowHeight
        val out = ArrayList<Cell>()
        layout.rows.forEachIndexed { r, row ->
            val top = padV + r * rh
            var x = padH + (layout.units - row.units) / 2f * unit
            row.keys.forEachIndexed { i, key ->
                val left = x
                val right = x + key.width * unit
                // Edge keys own the margin beside them, so a tap near the edge still hits a key.
                val hitLeft = if (i == 0) 0f else left
                val hitRight = if (i == row.keys.lastIndex) width.toFloat() else right
                out += Cell(
                    key = key,
                    hit = RectF(hitLeft, if (r == 0) 0f else top, hitRight, if (r == layout.rows.lastIndex) height.toFloat().coerceAtLeast(top + rh) else top + rh),
                    draw = RectF(left + gapH, top + gapV, right - gapH, top + rh - gapV),
                )
                x = right
            }
        }
        cells = out
        press = FloatArray(out.size)
        targets = out.map { TouchTarget(it.draw.left, it.draw.top, it.draw.right, it.draw.bottom, letterOf(it.key)) }
    }

    /** The lower-case character a letter key types (its first character), or null for other keys. */
    private fun letterOf(key: Key): Char? {
        val text = (key.action as? KeyAction.Text)?.text ?: return null
        return if (key.style == KeyStyle.CHARACTER && text.isNotEmpty()) text[0].lowercaseChar() else null
    }

    // ------------------------------------------------------------------ drawing

    // Soft key presses: each key has a press amount (0 up … 1 fully down) that rises quickly when
    // touched and eases back after release, so keys sink and spring back instead of blinking.
    private var press = FloatArray(0)
    private var lastFrame = 0L

    override fun onDraw(canvas: Canvas) {
        if (press.size != cells.size) resetPress()
        val now = android.os.SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) FRAME_MS else (now - lastFrame).coerceIn(0L, 64L).toFloat()
        lastFrame = now
        var animating = false
        for (i in cells.indices) {
            val cell = cells[i]
            val down = isPressed(cell)
            var a = press[i]
            if (down && a < 1f) a = (a + dt / PRESS_IN_MS).coerceAtMost(1f)
            if (!down && a > 0f) a = (a - dt / PRESS_OUT_MS).coerceAtLeast(0f)
            press[i] = a
            if ((down && a < 1f) || (!down && a > 0f)) animating = true
            val e = a * a * (3 - 2 * a) // smoothstep: gentle start and end
            val scale = 1f - PRESS_SCALE * e
            canvas.withScale(scale, scale, cell.draw.centerX(), cell.draw.centerY()) { drawKey(this, cell, e) }
        }
        if (animating) postInvalidateOnAnimation() else lastFrame = 0L
    }

    /** Keeps one press amount per key (normally sized when the keys are laid out). */
    private fun resetPress() {
        press = FloatArray(cells.size)
    }

    private fun drawKey(canvas: Canvas, cell: Cell, pressAmount: Float) {
        val key = cell.key
        val pressed = pressAmount > 0f
        // Enter is always the accent key (the one action you look for); caps lock lights up too.
        val enter = key.style == KeyStyle.ENTER || key.style == KeyStyle.ENTER_ACTION
        val accentBg = enter || (key.style == KeyStyle.SHIFT && shift == ShiftState.LOCKED)
        val base = when {
            accentBg -> palette.accent
            key.style == KeyStyle.CHARACTER || key.style == KeyStyle.SPACE -> palette.key
            else -> palette.functionKey
        }
        keyPaint.color = if (accentBg || !pressed) base else ColorUtils.blendARGB(base, palette.keyPressed, pressAmount)
        if (palette.keyOpacity < 1f && !accentBg) {
            keyPaint.alpha = (keyPaint.alpha * palette.keyOpacity).toInt()
        }
        val r = cell.draw
        // The shadow shrinks as the key goes down, so it looks pressed into the board.
        if (palette.drawShadow && !accentBg) {
            val drop = density * 1.5f * (1f - pressAmount)
            if (drop > 0.2f) canvas.drawRoundRect(r.left, r.top + drop, r.right, r.bottom + drop, radius, radius, shadowPaint)
        }
        canvas.drawRoundRect(r, radius, radius, keyPaint)
        if (accentBg && pressed) {
            // A pressed accent key darkens instead of losing its colour.
            keyPaint.color = ColorUtils.setAlphaComponent(PRESSED_TINT, (0x33 * pressAmount).toInt())
            canvas.drawRoundRect(r, radius, radius, keyPaint)
        }
        if (palette.border != null && !accentBg) canvas.drawRoundRect(r, radius, radius, borderPaint)

        // Trackpad mode: blank keys, so it is clear the keyboard is now a touchpad.
        if (trackpadActive) return

        val shiftOn = key.style == KeyStyle.SHIFT && shift == ShiftState.ON
        val icon = key.icon?.let(::keyboardIcon)
        if (icon != null) {
            val role = when {
                accentBg -> 1
                shiftOn -> 2
                else -> 0
            }
            val size = (palette.labelScale * ICON_DP * density).coerceAtMost(r.height() * 0.62f)
            icons.draw(canvas, icon, role, r.centerX(), r.centerY(), size)
        } else {
            labelPaint.color = when {
                accentBg -> palette.onAccent
                key.style == KeyStyle.SPACE -> palette.hint
                else -> palette.text
            }
            labelPaint.textSize = labelSize(key)
            labelPaint.typeface = if (key.style == KeyStyle.CHARACTER) regular else medium
            val fm = labelPaint.fontMetrics
            val baseline = r.centerY() - (fm.ascent + fm.descent) / 2
            canvas.drawText(cell.label, r.centerX(), baseline, labelPaint)
        }

        cell.hint?.let { hint ->
            canvas.drawText(hint, r.right - 4 * density, r.top + 3 * density + hintPaint.textSize, hintPaint)
        }
    }

    private fun keyboardIcon(icon: KeyIcon): KeyboardIcon = when (icon) {
        KeyIcon.SHIFT -> when (shift) {
            ShiftState.OFF -> KeyboardIcon.SHIFT
            ShiftState.ON -> KeyboardIcon.SHIFT_ON
            ShiftState.LOCKED -> KeyboardIcon.CAPS_LOCK
        }
        KeyIcon.DELETE -> KeyboardIcon.BACKSPACE
        KeyIcon.ENTER -> KeyboardIcon.ENTER
        KeyIcon.GO -> KeyboardIcon.GO
        KeyIcon.SEARCH -> KeyboardIcon.SEARCH
        KeyIcon.SEND -> KeyboardIcon.SEND
        KeyIcon.NEXT -> KeyboardIcon.NEXT
        KeyIcon.PREVIOUS -> KeyboardIcon.PREVIOUS
        KeyIcon.DONE -> KeyboardIcon.DONE
        KeyIcon.LANGUAGE -> KeyboardIcon.LANGUAGE
        KeyIcon.EMOJI -> KeyboardIcon.EMOJI
    }

    private fun labelSize(key: Key): Float = palette.labelScale * when {
        key.style == KeyStyle.SPACE -> sp(13f)
        key.label.length > 2 -> sp(15f)
        key.style == KeyStyle.CHARACTER -> sp(23f)
        else -> sp(20f)
    }

    private fun isPressed(cell: Cell): Boolean {
        for (p in pointers.values) {
            val c = p.cell ?: continue
            if (!p.consumed && !p.showingChoices && c.hit == cell.hit) return true
        }
        return false
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                pointerDown(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                pointerMove(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> pointerUp(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> cancelTouches()
        }
        return true
    }

    /** The key a new touch meant: the key under the finger, adjusted by predictive touch. */
    private fun resolveTouch(x: Float, y: Float): Cell? {
        val cx = x.coerceIn(0f, (width - 1).toFloat())
        val cy = y.coerceIn(0f, (height - 1).toFloat())
        var direct = -1
        for (i in cells.indices) if (cells[i].hit.contains(cx, cy)) { direct = i; break }
        if (direct < 0) return null
        val i = TouchResolver.resolve(targets, x, y, direct, nextChars, calibration)
        return cells[i]
    }

    /**
     * Personal touch calibration: every kept letter tap nudges that key's learned offset a
     * little; a correction (letter, Delete, then a neighbouring letter) teaches that the first
     * touch was meant for the neighbour.
     */
    private fun learnTap(p: Pointer, cell: Cell) {
        val cal = calibration ?: return
        if (!learnTouches || p.firstCell !== cell) return
        val c = letterOf(cell.key) ?: return
        val d = cell.draw
        val now = SystemClock.uptimeMillis()
        correction?.let { wrong ->
            val near = kotlin.math.abs(wrong.x - d.centerX()) < d.width() * 1.5f && kotlin.math.abs(wrong.y - d.centerY()) < d.height() * 1.5f
            if (wrong.char != c && near && now - wrong.time < CORRECTION_MS) {
                cal.learn(c, (wrong.x - d.centerX()) / d.width(), (wrong.y - d.centerY()) / d.height(), TouchCalibration.CORRECTION_RATE)
            }
        }
        correction = null
        cal.learn(c, (p.downX - d.centerX()) / d.width(), (p.downY - d.centerY()) / d.height(), TouchCalibration.TAP_RATE)
        lastTap = Tap(c, p.downX, p.downY, now)
    }

    /** A Delete right after a letter may be a correction; remember that letter's touch. */
    private fun noteDelete() {
        val tap = lastTap ?: return
        if (SystemClock.uptimeMillis() - tap.time < CORRECTION_MS) correction = tap
        lastTap = null
    }

    private fun findCell(x: Float, y: Float): Cell? {
        val cx = x.coerceIn(0f, (width - 1).toFloat())
        val cy = y.coerceIn(0f, (height - 1).toFloat())
        return cells.firstOrNull { it.hit.contains(cx, cy) }
    }

    private fun pointerDown(id: Int, x: Float, y: Float) {
        // A tap waiting for a possible double-tap: a second tap on the same key completes the
        // shortcut; any other key types the waiting tap first, so the order of text is kept.
        val target = resolveTouch(x, y)
        pendingTap?.let { pending ->
            if (target === pending.cell) {
                handler.removeCallbacks(flushPending)
                pendingTap = null
                val shortcut = gestures?.actionFor(pending.zone, GestureType.DOUBLE_TAP)
                if (shortcut != null) {
                    val p = Pointer(id, target, x, y)
                    pointers[id] = p
                    fireGesture(p, shortcut)
                    return
                }
                emit(pending.action)
            } else {
                flushPendingTap()
            }
        }
        // Fast typing: commit keys still held by other fingers, in the order they were pressed.
        for (other in pointers.values) {
            val c = other.cell ?: continue
            if (!other.consumed && !other.showingChoices && !other.repeating && c.key.style == KeyStyle.CHARACTER) {
                handler.removeCallbacks(other.longPress)
                other.consumed = true
                popup?.hidePreview()
                emit(c.key.action)
            }
        }
        val cell = target
        val p = Pointer(id, cell, x, y)
        pointers[id] = p
        if (cell == null) return
        feedback?.keyDown(this, cell.key.style) ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        startPress(p, cell)
        if (cell.key.style == KeyStyle.SPACE) handler.postDelayed(p.trackpadStart, TRACKPAD_MS)
        if (cell.key.action == KeyAction.Delete) {
            noteDelete()
            if (!cursorSwipe) emit(KeyAction.Delete)
            handler.postDelayed(p.repeat, REPEAT_START_MS)
        }
        invalidate()
    }

    /** Handles space-bar and backspace swipes. Returns true if the move was used for a swipe. */
    private fun handleSwipe(p: Pointer, x: Float): Boolean {
        if (!cursorSwipe || p.consumed || p.repeating || p.showingChoices) return false
        val cell = p.cell ?: return false
        when (cell.key.style) {
            KeyStyle.SPACE -> {
                // Gboard: slide along the space bar to move the cursor a character at a time.
                val dx = x - p.downX
                if (!p.swiping && kotlin.math.abs(dx) < swipeStart) return false
                if (!p.swiping && gestureOwns(cell.key.zone, dx, p.lastY - p.downY)) return false
                if (!p.swiping) handler.removeCallbacks(p.trackpadStart)
                p.swiping = true
                val steps = (dx / cursorStep).toInt()
                if (steps != p.swipeSteps) {
                    emit(KeyAction.MoveCursor(steps - p.swipeSteps))
                    feedback?.tick(this) ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    p.swipeSteps = steps
                }
                return true
            }
            KeyStyle.DELETE -> {
                // Slide left to select words before the cursor; slide back right to select fewer.
                val dx = p.downX - x
                if (!p.swiping && dx < swipeStart) return false
                if (!p.swiping && gestureOwns(cell.key.zone, x - p.downX, p.lastY - p.downY)) return false
                if (!p.swiping) {
                    p.swiping = true
                    handler.removeCallbacks(p.repeat)
                }
                val words = if (dx < swipeStart / 2) 0 else (dx / wordStep).toInt() + 1
                if (words != p.swipeSteps) {
                    emit(KeyAction.SelectBack(words))
                    feedback?.tick(this) ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    p.swipeSteps = words
                }
                return true
            }
            else -> return false
        }
    }

    /** Space held without sliding: the keyboard becomes a trackpad for the cursor (like iOS). */
    private fun onTrackpad(p: Pointer) {
        if (!pointers.containsValue(p) || p.swiping || p.consumed) return
        p.trackpad = true
        p.trackX = p.lastX
        p.trackY = p.lastY
        trackpadActive = true
        popup?.hideAll()
        feedback?.longPress(this) ?: performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    /** One character per [trackStepX] sideways; one line per [trackStepY] up or down. */
    private fun moveTrackpad(p: Pointer, x: Float, y: Float) {
        val stepsX = ((x - p.trackX) / trackStepX).toInt()
        if (stepsX != 0) {
            emit(KeyAction.MoveCursor(stepsX))
            p.trackX += stepsX * trackStepX
        }
        if (!trackpadVertical) return
        val stepsY = ((y - p.trackY) / trackStepY).toInt()
        if (stepsY != 0) {
            val command = if (stepsY > 0) EditCommand.DOWN else EditCommand.UP
            repeat(kotlin.math.abs(stepsY)) { emit(KeyAction.Edit(command)) }
            p.trackY += stepsY * trackStepY
            feedback?.tick(this) ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun startPress(p: Pointer, cell: Cell) {
        if (cell.key.style == KeyStyle.CHARACTER) popup?.showPreview(toPopup(cell.draw), cell.label)
        // A long-press shortcut on this key takes the place of its menu or built-in hold action.
        val zone = cell.key.zone
        val g = gestures
        if (zone != null && g != null && p.firstCell === cell && g.bindingFor(zone, GestureType.LONG_PRESS) != null) {
            handler.postDelayed(p.gestureHold, g.thresholds.longPressMs)
        } else if (cell.key.popup.isNotEmpty() || cell.key.longPress != null) {
            handler.postDelayed(p.longPress, longPressMs)
        }
    }

    private fun pointerMove(id: Int, x: Float, y: Float) {
        val p = pointers[id] ?: return
        p.lastX = x
        p.lastY = y
        if (p.trackpad) {
            moveTrackpad(p, x, y)
            return
        }
        if (checkSwipeGesture(p, x, y)) return
        if (handleSwipe(p, x)) return
        if (p.consumed || p.repeating) return
        if (p.showingChoices) {
            popup?.selectAt(x + popupOffsetX())
            return
        }
        val current = p.cell ?: return
        if (current.key.style != KeyStyle.CHARACTER) return
        val next = findCell(x, y) ?: return
        if (next === current || next.key.style != KeyStyle.CHARACTER) return
        handler.removeCallbacks(p.longPress)
        p.cell = next
        startPress(p, next)
        invalidate()
    }

    private fun pointerUp(id: Int) {
        val p = pointers.remove(id) ?: return
        handler.removeCallbacks(p.longPress)
        handler.removeCallbacks(p.repeat)
        handler.removeCallbacks(p.trackpadStart)
        handler.removeCallbacks(p.gestureHold)
        if (p.trackpad) {
            trackpadActive = pointers.values.any { it.trackpad }
            invalidate()
            return
        }
        val cell = p.cell
        if (p.showingChoices) {
            val choice = popup?.selectedChoice()
            popup?.hideChoices()
            invalidate()
            choice?.let { emit(it.action) }
            return
        }
        popup?.hidePreview()
        invalidate()
        if (p.consumed || cell == null) return
        if (p.swiping) {
            // Space swipe already moved the cursor; a backspace swipe deletes what it selected
            // (or, slid back to where it started, deletes nothing).
            if (cell.key.action == KeyAction.Delete) emit(KeyAction.EndSelectBack(delete = p.swipeSteps > 0))
            return
        }
        if (cell.key.action == KeyAction.Delete) {
            // Deleted on press (swipe off) or by the repeat while held; otherwise delete now.
            if (cursorSwipe && !p.repeating) emit(KeyAction.Delete)
            return
        }
        learnTap(p, cell)
        // A double-tap shortcut on this key: hold the tap back briefly to see if a second follows.
        val zone = cell.key.zone
        val g = gestures
        if (zone != null && g != null && p.firstCell === cell && g.bindingFor(zone, GestureType.DOUBLE_TAP) != null) {
            pendingTap = PendingTap(cell, cell.key.action, zone)
            handler.postDelayed(flushPending, g.thresholds.doubleTapMs)
            return
        }
        emit(cell.key.action)
    }

    private fun onLongPress(p: Pointer) {
        val cell = p.cell ?: return
        cell.key.longPress?.let { action ->
            if (p.consumed) return
            p.consumed = true // released later: nothing more to type
            popup?.hidePreview()
            feedback?.longPress(this) ?: performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            emit(action)
            invalidate()
            return
        }
        if (p.consumed || cell.key.popup.isEmpty()) return
        p.showingChoices = true
        feedback?.longPress(this) ?: performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        popup?.showChoices(toPopup(cell.draw), cell.key.popup, 0)
        invalidate()
    }

    private fun onRepeat(p: Pointer) {
        if (!pointers.containsValue(p)) return
        p.repeating = true
        emit(KeyAction.Delete)
        handler.postDelayed(p.repeat, REPEAT_INTERVAL_MS)
    }

    private fun emit(action: KeyAction) {
        listener?.onKeyAction(action)
    }

    // ------------------------------------------------------------------ popup coordinates

    private fun toPopup(r: RectF): RectF {
        val target = popup ?: return RectF(r)
        getLocationInWindow(location)
        target.getLocationInWindow(popupLocation)
        return RectF(r).apply {
            offset((location[0] - popupLocation[0]).toFloat(), (location[1] - popupLocation[1]).toFloat())
        }
    }

    private fun popupOffsetX(): Float {
        val target = popup ?: return 0f
        getLocationInWindow(location)
        target.getLocationInWindow(popupLocation)
        return (location[0] - popupLocation[0]).toFloat()
    }

    override fun onDetachedFromWindow() {
        cancelTouches()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val LONG_PRESS_MS = 350L
        const val REPEAT_START_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
        const val TRACKPAD_MS = 400L
        const val CORRECTION_MS = 3000L
        const val PRESSED_TINT = 0x33000000
        const val FRAME_MS = 16f
        const val PRESS_IN_MS = 45f
        const val PRESS_OUT_MS = 170f
        const val PRESS_SCALE = 0.07f
        const val ICON_DP = 24f
    }
}
