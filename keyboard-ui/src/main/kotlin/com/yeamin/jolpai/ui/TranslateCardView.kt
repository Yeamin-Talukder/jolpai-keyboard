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
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible

/** What the translate card shows. */
data class TranslateCardModel(
    val source: String,
    val toBangla: Boolean,
    val translation: String?,
    val status: TranslateStatus,
    /** A translation of the current text is being worked out. */
    val busy: Boolean,
    /** Spelling and grammar suggestions for the typed text, shown as chips (nothing changes until tapped). */
    val issues: List<com.yeamin.jolpai.suggest.TextIssue> = emptyList(),
)

/**
 * The translate box that opens above the toolbar (the keys don't move): a header with the two
 * languages and a swap button, the text being typed (with a blinking caret), and the translation
 * with an insert button. Before the first use it offers to download the Bangla pack.
 *
 * Motion: the card rises into place with its rows following one after another, the swap arrow
 * spins and the language names slide over, new translations fade in, three dots pulse while
 * translating, and a thin bar runs while the pack downloads.
 */
@SuppressLint("ViewConstructor") // built in code only
class TranslateCardView(
    context: Context,
    private val palette: KeyboardPalette,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onClose()
        fun onSwap()
        fun onClear()
        fun onInsert(text: String)
        fun onDownload()

        /** Apply one suggestion chip. */
        fun onFixIssue(issue: com.yeamin.jolpai.suggest.TextIssue)

        /** Dismiss one suggestion chip. */
        fun onIgnoreIssue(issue: com.yeamin.jolpai.suggest.TextIssue)

        /** Apply every suggestion ("Fix all"). */
        fun onFixAll()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private var model: TranslateCardModel? = null
    private var shownTranslation: String? = null
    private var hiding = false

    private val fromLabel = label(bold = true, color = palette.text)
    private val toLabel = label(bold = true, color = palette.accent)
    private val swapButton = circle(KeyboardIcon.SWAP, "Swap languages") { listener.onSwap() }
    private val closeButton = circle(KeyboardIcon.CLOSE, "Close translate") { listener.onClose() }

    private val sourceText = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(palette.text)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.START // keep the end, where you are typing, in view
        gravity = Gravity.CENTER_VERTICAL
    }
    private val clearButton = smallIcon(KeyboardIcon.CLOSE, "Clear") { listener.onClear() }

    private val resultText = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(palette.text)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
    }
    private val dots = DotsView()
    private val insertButton = ImageView(context).apply {
        setImageDrawable(KeyboardIcon.SEND.drawable(context, palette.onAccent, 20 * density))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = "Insert translation"
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.onAccent, 0x40)),
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.accent) },
            null,
        )
        setOnClickListener {
            val t = model?.translation ?: return@setOnClickListener
            bounce(this)
            listener.onInsert(t)
        }
    }
    private val actionPill = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(palette.onAccent)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(14f), 0, dp(14f), 0)
        compoundDrawablePadding = dp(6f)
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.onAccent, 0x40)),
            GradientDrawable().apply { cornerRadius = 100 * density; setColor(palette.accent) },
            null,
        )
        setOnClickListener { listener.onDownload() }
    }
    private val progress = ProgressLine()

    /** Suggestion chips (spelling, grammar, punctuation), as in the Check card: tap to fix. */
    private val chipList = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val chipRow = android.widget.HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        visibility = GONE
        addView(chipList, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private var shownIssues: List<com.yeamin.jolpai.suggest.TextIssue> = emptyList()

    private val header: LinearLayout
    private val inputBox: FrameLayout
    private val resultBox: FrameLayout

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(8f), dp(10f), dp(2f))
        isClickable = true // touches on the card never fall through to the app

        header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageDrawable(KeyboardIcon.TRANSLATE.drawable(context, palette.accent, 18 * density))
                scaleType = ImageView.ScaleType.CENTER
            }, LayoutParams(dp(26f), dp(30f)))
            addView(fromLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(2f) })
            addView(swapButton, LayoutParams(dp(30f), dp(30f)).apply { leftMargin = dp(8f); rightMargin = dp(8f) })
            addView(toLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(View(context), LayoutParams(0, 1, 1f))
            addView(closeButton, LayoutParams(dp(30f), dp(30f)))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(32f)))

        inputBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(ColorUtils.setAlphaComponent(palette.key or 0xFF000000.toInt(), (255 * palette.keyOpacity.coerceIn(0.5f, 1f)).toInt()))
                setStroke(dp(1f).coerceAtLeast(1), ColorUtils.setAlphaComponent(palette.hint, 0x40))
            }
            addView(sourceText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                leftMargin = dp(14f)
                rightMargin = dp(40f)
            })
            addView(clearButton, FrameLayout.LayoutParams(dp(36f), dp(36f), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(2f) })
        }
        addView(inputBox, LayoutParams(LayoutParams.MATCH_PARENT, dp(40f)).apply { topMargin = dp(6f) })
        addView(chipRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(30f)).apply { topMargin = dp(6f) })

        resultBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(ColorUtils.setAlphaComponent(palette.accent, 0x2A))
            }
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), 0, dp(4f), 0)
                addView(resultText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(dots, LayoutParams(dp(30f), dp(16f)).apply { leftMargin = dp(6f) })
                addView(actionPill, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36f)).apply { leftMargin = dp(8f); rightMargin = dp(2f) })
                addView(insertButton, LayoutParams(dp(38f), dp(38f)).apply { leftMargin = dp(6f) })
            }
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(progress, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3f), Gravity.BOTTOM).apply {
                leftMargin = dp(14f)
                rightMargin = dp(14f)
                bottomMargin = dp(4f)
            })
        }
        addView(resultBox, LayoutParams(LayoutParams.MATCH_PARENT, dp(46f)).apply { topMargin = dp(6f); bottomMargin = dp(4f) })
        visibility = GONE
    }

    // ------------------------------------------------------------------ show / hide

    val isShowing: Boolean get() = isVisible && !hiding

    /** Rises into place; the header, the text box and the result follow one after another. */
    fun show() {
        hiding = false
        animate().cancel()
        visibility = VISIBLE
        alpha = 1f
        translationY = 0f
        listOf(header, inputBox, resultBox).forEachIndexed { i, row ->
            row.animate().cancel()
            row.alpha = 0f
            row.translationY = 14 * density
            row.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(40L * i).setDuration(260)
                .setInterpolator(SETTLE)
                .start()
        }
        startCaret()
    }

    /** Fades down and away, then removes itself from the layout (the keyboard gets its height back). */
    fun hide() {
        if (!isVisible || hiding) return
        hiding = true
        stopCaret()
        animate().cancel()
        animate()
            .alpha(0f).translationY(12 * density)
            .setStartDelay(0).setDuration(170)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                visibility = GONE
                alpha = 1f
                translationY = 0f
                hiding = false
                shownTranslation = null
            }
            .start()
    }

    // ------------------------------------------------------------------ content

    fun render(m: TranslateCardModel) {
        val old = model
        model = m
        if (old != null && old.toBangla != m.toBangla) swapLabels(m) else setLabels(m)
        renderSource()
        renderResult(m)
        renderSpelling(m)
    }

    /** Shows the suggestion chips (sliding in when they first appear) or hides the row. */
    private fun renderSpelling(m: TranslateCardModel) {
        val issues = m.issues
        if (issues == shownIssues) return
        val firstShow = shownIssues.isEmpty()
        shownIssues = issues
        chipList.removeAllViews()
        if (issues.isEmpty()) {
            chipRow.isVisible = false
            return
        }
        issues.forEach { issue -> chipList.addView(issueChip(issue), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { rightMargin = dp(6f) }) }
        if (issues.size > 1) {
            chipList.addView(fixAllChip(), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        if (!chipRow.isVisible || firstShow) {
            chipRow.isVisible = true
            chipRow.alpha = 0f
            chipRow.translationX = -12 * density
            chipRow.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(SETTLE).start()
            chipRow.scrollTo(0, 0)
        }
    }

    /** "recieved → received" in the kind's colour; tap fixes it, ✕ dismisses it. */
    private fun issueChip(issue: com.yeamin.jolpai.suggest.TextIssue): View {
        val color = when (issue.kind) {
            com.yeamin.jolpai.suggest.IssueKind.SPELLING -> android.graphics.Color.rgb(0xE5, 0x48, 0x4D)
            com.yeamin.jolpai.suggest.IssueKind.PUNCTUATION -> android.graphics.Color.rgb(0x3E, 0x8E, 0xF0)
            com.yeamin.jolpai.suggest.IssueKind.GRAMMAR -> android.graphics.Color.rgb(0xE0, 0x9A, 0x1F)
        }
        val chip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), 0, dp(2f), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)),
                GradientDrawable().apply {
                    cornerRadius = 100 * density
                    setColor(ColorUtils.setAlphaComponent(color, 0x1C))
                    setStroke(dp(1f).coerceAtLeast(1), ColorUtils.setAlphaComponent(color, 0x88))
                },
                null,
            )
            contentDescription = "${issue.kind.label}: ${issue.original.trim()} to ${issue.replacement.trim()}. ${issue.reason}"
        }
        val label = SpannableStringBuilder()
        val from = issue.original.trim().ifEmpty { "␣" }
        label.append(from)
        label.setSpan(android.text.style.StrikethroughSpan(), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.setSpan(ForegroundColorSpan(palette.hint), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.append(" → ")
        val start = label.length
        label.append(issue.replacement.trim().ifEmpty { "␣" })
        label.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        chip.addView(TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.text)
            isSingleLine = true
        })
        chip.addView(ImageView(context).apply {
            setImageDrawable(KeyboardIcon.CLOSE.drawable(context, palette.hint, 13 * density))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "Dismiss"
            setOnClickListener { shrinkAway(chip) { listener.onIgnoreIssue(issue) } }
        }, LayoutParams(dp(26f), LayoutParams.MATCH_PARENT))
        chip.setOnClickListener { shrinkAway(chip) { listener.onFixIssue(issue) } }
        return chip
    }

    private fun fixAllChip(): View = TextView(context).apply {
        text = "Fix all"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(palette.onAccent)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(12f), 0, dp(12f), 0)
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.onAccent, 0x40)),
            GradientDrawable().apply { cornerRadius = 100 * density; setColor(palette.accent) },
            null,
        )
        setOnClickListener {
            bounce(this)
            listener.onFixAll()
        }
    }

    /** The chip shrinks and fades, then [then] runs (the row is redrawn from the new state). */
    private fun shrinkAway(v: View, then: () -> Unit) {
        v.isEnabled = false
        v.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(130).withEndAction(then).start()
    }

    private fun setLabels(m: TranslateCardModel) {
        fromLabel.text = if (m.toBangla) ENGLISH else BANGLA
        toLabel.text = if (m.toBangla) BANGLA else ENGLISH
    }

    /** The arrow spins half a turn and the two names slide out and back in swapped. */
    private fun swapLabels(m: TranslateCardModel) {
        swapButton.animate().rotationBy(180f).setDuration(340).setInterpolator(OvershootInterpolator(1.4f)).start()
        listOf(fromLabel to -1f, toLabel to 1f).forEach { (v, dir) ->
            v.animate().cancel()
            v.animate().alpha(0f).translationX(dir * 10 * density).setDuration(90).withEndAction {
                setLabels(m)
                v.translationX = -dir * 10 * density
                v.animate().alpha(1f).translationX(0f).setDuration(170).setInterpolator(SETTLE).start()
            }.start()
        }
    }

    private var caretOn = true
    private val blink = object : Runnable {
        override fun run() {
            caretOn = !caretOn
            renderSource()
            postDelayed(this, CARET_MS)
        }
    }

    private fun startCaret() {
        removeCallbacks(blink)
        caretOn = true
        postDelayed(blink, CARET_MS)
    }

    private fun stopCaret() = removeCallbacks(blink)

    override fun onDetachedFromWindow() {
        stopCaret()
        super.onDetachedFromWindow()
    }

    private fun renderSource() {
        val m = model ?: return
        val sb = SpannableStringBuilder()
        if (m.source.isEmpty()) {
            sb.append(CARET)
            sb.setSpan(ForegroundColorSpan(if (caretOn) palette.accent else 0), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val hint = if (m.toBangla) "Type in English…" else "বাংলায় লিখুন…"
            sb.append(hint)
            sb.setSpan(ForegroundColorSpan(palette.hint), 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            sb.append(m.source)
            val start = sb.length
            sb.append(CARET)
            sb.setSpan(ForegroundColorSpan(if (caretOn) palette.accent else 0), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sourceText.text = sb
        clearButton.isVisible = m.source.isNotEmpty()
    }

    private fun renderResult(m: TranslateCardModel) {
        progress.running = m.status == TranslateStatus.DOWNLOADING
        progress.isVisible = progress.running
        when (m.status) {
            TranslateStatus.CHECKING -> message("Getting translation ready…")
            TranslateStatus.NOT_DOWNLOADED -> {
                message("Offline Bangla pack · about ${OfflineTranslator.PACK_SIZE_MB} MB, once")
                pill("Download", KeyboardIcon.DOWNLOAD)
            }
            TranslateStatus.DOWNLOADING -> message("Downloading the Bangla pack…")
            TranslateStatus.FAILED -> {
                message("Download failed. Check the internet and try again.")
                pill("Retry", KeyboardIcon.DOWNLOAD)
            }
            TranslateStatus.READY -> {
                actionPill.isVisible = false
                dots.isVisible = m.busy && m.source.isNotBlank()
                val t = m.translation
                when {
                    m.source.isBlank() -> {
                        setResult(null, if (m.toBangla) "বাংলা অনুবাদ এখানে দেখাবে" else "The English translation shows here")
                        insert(false)
                    }
                    t != null -> {
                        setResult(t, null)
                        insert(true)
                    }
                    else -> {
                        // Still working: keep the previous translation, faded, so nothing jumps.
                        resultText.alpha = if (shownTranslation != null) 0.45f else 1f
                        if (shownTranslation == null) setResult(null, "")
                        insert(false)
                    }
                }
            }
        }
    }

    private fun message(text: String) {
        dots.isVisible = false
        insert(false, hide = true)
        actionPill.isVisible = false
        setResult(null, text)
        resultText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }

    private fun pill(text: String, icon: KeyboardIcon) {
        actionPill.text = text
        actionPill.setCompoundDrawablesRelative(icon.drawable(context, palette.onAccent, 18 * density).apply { setBounds(0, 0, dp(18f), dp(18f)) }, null, null, null)
        actionPill.isVisible = true
    }

    /** Shows a translation (fading it in when it changes) or a grey [placeholder]. */
    private fun setResult(translation: String?, placeholder: String?) {
        if (translation != null) {
            resultText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            resultText.setTextColor(palette.text)
            if (translation != shownTranslation) {
                resultText.text = translation
                resultText.alpha = 0.35f
                resultText.translationY = 4 * density
                resultText.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(SETTLE).start()
                shownTranslation = translation
            } else {
                resultText.alpha = 1f
            }
        } else {
            resultText.animate().cancel()
            resultText.alpha = 1f
            resultText.translationY = 0f
            resultText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            resultText.setTextColor(palette.hint)
            resultText.text = placeholder
            shownTranslation = null
        }
    }

    private fun insert(enabled: Boolean, hide: Boolean = false) {
        insertButton.isVisible = !hide
        insertButton.isEnabled = enabled
        insertButton.animate().alpha(if (enabled) 1f else 0.35f).scaleX(if (enabled) 1f else 0.9f).scaleY(if (enabled) 1f else 0.9f).setDuration(150).start()
    }

    private fun bounce(v: View) {
        v.animate().scaleX(0.82f).scaleY(0.82f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    // ------------------------------------------------------------------ pieces

    private fun label(bold: Boolean, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isSingleLine = true
    }

    private fun circle(icon: KeyboardIcon, description: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageDrawable(icon.drawable(context, palette.text, 18 * density))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        background = RippleDrawable(
            ColorStateList.valueOf(palette.keyPressed),
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.functionKey or 0xFF000000.toInt()) },
            null,
        )
        setOnClickListener { onClick() }
    }

    private fun smallIcon(icon: KeyboardIcon, description: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageDrawable(icon.drawable(context, palette.hint, 16 * density))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        background = RippleDrawable(ColorStateList.valueOf(palette.keyPressed), null, null).apply { radius = dp(16f) }
        setOnClickListener { onClick() }
    }

    /** Three dots that rise and glow in turn while a translation is being worked out. */
    private inner class DotsView : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val t = (SystemClock.uptimeMillis() % 900) / 900f
            val r = 3 * density
            val gap = (width - 6 * r) / 2f
            for (i in 0 until 3) {
                val phase = ((t - i * 0.18f) + 1f) % 1f
                val lift = kotlin.math.sin(phase * Math.PI).toFloat().coerceAtLeast(0f)
                paint.color = ColorUtils.setAlphaComponent(palette.accent, (90 + 165 * lift).toInt())
                canvas.drawCircle(r + i * (2 * r + gap), height / 2f - lift * 3 * density, r, paint)
            }
            if (isVisible) postInvalidateOnAnimation()
        }
    }

    /** A thin bar with a segment sliding along it: the pack is downloading. */
    private inner class ProgressLine : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        var running = false
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            paint.color = ColorUtils.setAlphaComponent(palette.accent, 0x33)
            rect.set(0f, 0f, width.toFloat(), h)
            canvas.drawRoundRect(rect, h / 2, h / 2, paint)
            if (!running) return
            val t = (SystemClock.uptimeMillis() % 1300) / 1300f
            val segment = width * 0.35f
            val x = -segment + (width + segment) * SETTLE.getInterpolation(t)
            paint.color = palette.accent
            rect.set(x.coerceAtLeast(0f), 0f, (x + segment).coerceAtMost(width.toFloat()), h)
            if (rect.width() > 0) canvas.drawRoundRect(rect, h / 2, h / 2, paint)
            postInvalidateOnAnimation()
        }
    }

    private companion object {
        const val ENGLISH = "English"
        const val BANGLA = "বাংলা"
        const val CARET = "▏"
        const val CARET_MS = 530L

        /** Quick start, long gentle settle (the same feel as the settings pages). */
        val SETTLE = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)
    }
}
