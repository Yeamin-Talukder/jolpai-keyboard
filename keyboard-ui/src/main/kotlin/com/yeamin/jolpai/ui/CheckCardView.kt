package com.yeamin.jolpai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.yeamin.jolpai.core.CheckUi
import com.yeamin.jolpai.suggest.IssueKind
import com.yeamin.jolpai.suggest.TextIssue

/**
 * The Check card above the toolbar (the keys don't move): spelling, punctuation and grammar
 * suggestions for the text in the field, found offline.
 *
 * Header: ✓ Check, a summary, Fix all, check again, close. Below: one small card per suggestion
 * in a sideways list (kind, "mistake → fix", reason, Ignore / Fix), or a calm "looks good" state.
 *
 * Motion: the card rises in with its rows following, suggestion cards slide in one after
 * another, a fixed card shrinks away, the check-again arrow spins, and "looks good" pops in.
 */
@SuppressLint("ViewConstructor", "SetTextI18n") // built in code only; the keyboard is English/Bangla text in code
class CheckCardView(
    context: Context,
    private val palette: KeyboardPalette,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onClose()
        fun onRecheck()
        fun onFix(issue: TextIssue)
        fun onFixAll()
        fun onIgnore(issue: TextIssue)

        /** Run (or download, then run) the on-device AI proofreading. */
        fun onAiCheck()

        /** Use the AI's rewrite of [original]. */
        fun onAiApply(original: String, replacement: String)
    }

    /** The optional on-device AI proofreading, as shown on the card. */
    sealed interface Ai {
        /** Not on this phone (or not English): no button. */
        data object Hidden : Ai
        data class Ready(val needsDownload: Boolean) : Ai
        data object Working : Ai
        data class Suggestion(val original: String, val replacement: String) : Ai
        data object NothingToChange : Ai
        data object Failed : Ai
    }

    private var ai: Ai = Ai.Hidden

    /** Updates the AI button and, for a suggestion, the AI card at the front of the list. */
    fun setAi(state: Ai) {
        if (state == ai) return
        ai = state
        renderAiButton()
        model?.let { render(it, force = true) }
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private var model: CheckUi? = null
    private var hiding = false

    private val title = TextView(context).apply {
        text = "Check"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(palette.text)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val summary = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(palette.hint)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    private val fixAll = TextView(context).apply {
        text = "Fix all"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(palette.onAccent)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(12f), 0, dp(12f), 0)
        background = pill(palette.accent, ColorUtils.setAlphaComponent(palette.onAccent, 0x40))
        setOnClickListener { listener.onFixAll() }
    }
    /** ✨ AI: deeper English proofreading on phones with on-device AI. */
    private val aiButton = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(AI_VIOLET)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(10f), 0, dp(10f), 0)
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(AI_VIOLET, 0x33)),
            GradientDrawable().apply {
                cornerRadius = 100 * density
                setColor(ColorUtils.setAlphaComponent(AI_VIOLET, 0x1A))
                setStroke(dp(1f).coerceAtLeast(1), ColorUtils.setAlphaComponent(AI_VIOLET, 0x88))
            },
            null,
        )
        contentDescription = "Check with on-device AI"
        visibility = GONE
        setOnClickListener { listener.onAiCheck() }
    }

    private fun renderAiButton() {
        aiButton.animate().cancel()
        aiButton.alpha = 1f
        when (val a = ai) {
            Ai.Hidden -> aiButton.isVisible = false
            is Ai.Ready -> {
                aiButton.isVisible = true
                aiButton.isEnabled = true
                aiButton.text = if (a.needsDownload) "✨ Get AI" else "✨ AI"
            }
            Ai.Working -> {
                aiButton.isVisible = true
                aiButton.isEnabled = false
                aiButton.text = "✨ …"
                // Breathe while the AI works.
                aiButton.animate().alpha(0.45f).setDuration(520).withEndAction {
                    if (ai == Ai.Working) aiButton.animate().alpha(1f).setDuration(520).withEndAction { if (ai == Ai.Working) renderAiButton() }.start()
                }.start()
            }
            else -> {
                aiButton.isVisible = true
                aiButton.isEnabled = true
                aiButton.text = "✨ AI"
            }
        }
    }

    private val recheck = circle(KeyboardIcon.RESTART, "Check again") {
        spin()
        listener.onRecheck()
    }
    private val close = circle(KeyboardIcon.CLOSE, "Close check") { listener.onClose() }

    private val header: LinearLayout
    private val list = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(list, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val message = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(palette.text)
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = dp(10f)
        setPadding(dp(14f), 0, dp(14f), 0)
    }
    private val body = FrameLayout(context)

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(8f), dp(10f), dp(4f))
        isClickable = true

        header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageDrawable(KeyboardIcon.SPELLCHECK.drawable(context, palette.accent, 18 * density))
                scaleType = ImageView.ScaleType.CENTER
            }, LayoutParams(dp(26f), dp(30f)))
            addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(2f) })
            addView(summary, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f); rightMargin = dp(6f) })
            addView(aiButton, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply { rightMargin = dp(6f) })
            addView(fixAll, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply { rightMargin = dp(6f) })
            addView(recheck, LayoutParams(dp(30f), dp(30f)).apply { rightMargin = dp(6f) })
            addView(close, LayoutParams(dp(30f), dp(30f)))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(32f)))

        body.addView(scroller, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        body.addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        message.background = GradientDrawable().apply {
            cornerRadius = 14 * density
            setColor(ColorUtils.setAlphaComponent(palette.accent, 0x22))
        }
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, dp(96f)).apply { topMargin = dp(6f) })
        visibility = GONE
    }

    // ------------------------------------------------------------------ show / hide

    val isShowing: Boolean get() = isVisible && !hiding

    fun show() {
        hiding = false
        animate().cancel()
        visibility = VISIBLE
        alpha = 1f
        translationY = 0f
        listOf(header, body).forEachIndexed { i, row ->
            row.animate().cancel()
            row.alpha = 0f
            row.translationY = 14 * density
            row.animate().alpha(1f).translationY(0f).setStartDelay(50L * i).setDuration(260).setInterpolator(SETTLE).start()
        }
    }

    fun hide() {
        if (!isVisible || hiding) return
        hiding = true
        animate().cancel()
        animate().alpha(0f).translationY(12 * density).setStartDelay(0).setDuration(170)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                visibility = GONE
                alpha = 1f
                translationY = 0f
                hiding = false
                model = null
                list.removeAllViews()
            }.start()
    }

    // ------------------------------------------------------------------ content

    fun render(m: CheckUi) = render(m, force = false)

    private fun render(m: CheckUi, force: Boolean) {
        val old = model
        model = m
        val n = m.issues.size
        val aiCard = ai as? Ai.Suggestion
        summary.text = when {
            m.stale -> "Text changed · tap ↻ to check again"
            m.empty -> "Nothing to check yet"
            ai == Ai.NothingToChange && n == 0 -> "Looks good · AI agrees"
            ai == Ai.Failed -> "AI check didn't work this time"
            n == 0 && aiCard != null -> "AI has a suggestion"
            n == 0 && m.fixed > 0 -> "Fixed ${m.fixed} · looks good"
            n == 0 -> "Looks good"
            else -> summaryOf(m.issues)
        }
        fixAll.isVisible = n > 1 && !m.stale
        recheck.alpha = if (m.stale) 1f else 0.7f

        if (n == 0 && aiCard == null) {
            scroller.isVisible = false
            list.removeAllViews()
            message.isVisible = true
            val icon = if (m.empty) KeyboardIcon.EDIT else KeyboardIcon.CHECK_CIRCLE
            message.setCompoundDrawablesRelative(icon.drawable(context, palette.accent, 26 * density).apply { setBounds(0, 0, dp(26f), dp(26f)) }, null, null, null)
            message.text = when {
                m.empty -> "Type something, then tap ↻ to check spelling, punctuation and grammar."
                else -> "No spelling, punctuation or grammar problems found. Checked on your phone, offline."
            }
            if (old == null || old.issues.isNotEmpty()) pop(message)
            return
        }
        message.isVisible = false
        scroller.isVisible = true
        val changed = force || old == null || old.issues != m.issues
        if (!changed) return
        val firstShow = old == null || old.issues.isEmpty()
        list.removeAllViews()
        aiCard?.let { a ->
            val card = aiSuggestionCard(a)
            list.addView(card, LayoutParams(dp(264f), LayoutParams.MATCH_PARENT).apply { rightMargin = dp(8f) })
            card.alpha = 0f
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(OvershootInterpolator(1.3f)).start()
        }
        m.issues.forEachIndexed { i, issue ->
            val card = issueCard(issue)
            list.addView(card, LayoutParams(dp(if (issue.alternatives.isEmpty()) 232f else 272f), LayoutParams.MATCH_PARENT).apply { rightMargin = dp(8f) })
            if (firstShow || i == 0) {
                card.alpha = 0f
                card.translationX = 24 * density
                card.animate().alpha(1f).translationX(0f).setStartDelay(40L * i.coerceAtMost(5)).setDuration(240).setInterpolator(SETTLE).start()
            }
        }
        if (firstShow) scroller.scrollTo(0, 0)
    }

    private fun summaryOf(issues: List<TextIssue>): String {
        val parts = IssueKind.entries.mapNotNull { k ->
            val c = issues.count { it.kind == k }
            if (c == 0) null else "$c ${k.label.lowercase()}"
        }
        return parts.joinToString(" · ")
    }

    private fun kindColor(kind: IssueKind): Int = when (kind) {
        IssueKind.SPELLING -> SPELLING_RED
        IssueKind.PUNCTUATION -> PUNCTUATION_BLUE
        IssueKind.GRAMMAR -> GRAMMAR_AMBER
    }

    private fun issueCard(issue: TextIssue): View {
        val color = kindColor(issue.kind)
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12f), dp(8f), dp(8f), dp(6f))
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(ColorUtils.setAlphaComponent(palette.key or 0xFF000000.toInt(), (255 * palette.keyOpacity.coerceIn(0.6f, 1f)).toInt()))
                setStroke(dp(1f).coerceAtLeast(1), ColorUtils.setAlphaComponent(color, 0x66))
            }
        }
        // Kind: a coloured dot and label.
        card.addView(TextView(context).apply {
            text = issue.kind.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setCompoundDrawablesRelative(dot(color), null, null, null)
            compoundDrawablePadding = dp(5f)
            isSingleLine = true
        })
        // "mistake → fix"
        card.addView(TextView(context).apply {
            text = change(issue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.text)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
        // Reason + buttons. With other likely words (like the prediction strip), they take the
        // reason's place: "or  tall  talk" — tap one to use it instead of the suggested fix.
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (issue.alternatives.isEmpty()) {
                addView(TextView(context).apply {
                    text = issue.reason
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(palette.hint)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            } else {
                addView(LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = "or"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(palette.hint)
                    })
                    issue.alternatives.forEach { alt ->
                        addView(alternativeChip(alt) { remove(card) { listener.onFix(issue.copy(replacement = alt)) } },
                            LayoutParams(LayoutParams.WRAP_CONTENT, dp(26f)).apply { leftMargin = dp(5f) })
                    }
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }
            // A spelling flag on a name or rare word: "Add word" keeps it for good (personal dictionary).
            val keep = if (issue.kind == IssueKind.SPELLING) "Add word" else "Ignore"
            addView(textButton(keep, filled = false) { remove(card) { listener.onIgnore(issue) } }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply { leftMargin = dp(4f) })
            addView(textButton("Fix", filled = true) { remove(card) { listener.onFix(issue) } }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply { leftMargin = dp(6f) })
        }
        card.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(2f) })
        return card
    }

    /** The AI's rewrite: only the words that change, struck through → new, in the AI colour. */
    private fun aiSuggestionCard(a: Ai.Suggestion): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12f), dp(8f), dp(8f), dp(6f))
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(ColorUtils.blendARGB(palette.key or 0xFF000000.toInt(), AI_VIOLET, 0.10f))
                setStroke(dp(1f).coerceAtLeast(1), ColorUtils.setAlphaComponent(AI_VIOLET, 0xAA))
            }
        }
        card.addView(TextView(context).apply {
            text = "✨ AI suggestion"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(AI_VIOLET)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        val (from, to) = changedPart(a.original, a.replacement)
        card.addView(TextView(context).apply {
            text = change(TextIssue(0, from.length, from, to, IssueKind.GRAMMAR, ""))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.text)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "Proofread on this phone"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(palette.hint)
                maxLines = 1
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(textButton("Dismiss", filled = false) { remove(card) { setAi(Ai.Ready(needsDownload = false)) } }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply { leftMargin = dp(4f) })
            addView(textButton("Apply", filled = true) { remove(card) { listener.onAiApply(a.original, a.replacement) } }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply { leftMargin = dp(6f) })
        }
        card.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(2f) })
        return card
    }

    /** The words that differ between [a] and [b] (common words at both ends trimmed), with a little context. */
    private fun changedPart(a: String, b: String): Pair<String, String> {
        val x = a.trim().split(" ")
        val y = b.trim().split(" ")
        var start = 0
        while (start < x.size && start < y.size && x[start] == y[start]) start++
        var endX = x.size
        var endY = y.size
        while (endX > start && endY > start && x[endX - 1] == y[endY - 1]) { endX--; endY-- }
        if (start >= endX && start >= endY) return a.trim() to b.trim()
        val from = (if (start > 0) "…" else "") + x.subList(start, endX).joinToString(" ") + (if (endX < x.size) "…" else "")
        val to = y.subList(start, endY).joinToString(" ")
        return from.ifBlank { "…" } to to.ifBlank { "(remove)" }
    }

    /** "recieved → received", with the mistake struck through and the fix in bold. */
    private fun change(issue: TextIssue): CharSequence {
        val sb = SpannableStringBuilder()
        val shown = { s: String -> if (s.isBlank()) "␣".repeat(s.length.coerceIn(1, 3)) else s.trim().ifEmpty { s } }
        val from = shown(issue.original)
        sb.append(from)
        sb.setSpan(StrikethroughSpan(), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(palette.hint), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("  →  ")
        val start = sb.length
        sb.append(shown(issue.replacement))
        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(palette.text), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    /** The card shrinks and fades away, then [then] runs (the list is re-rendered from the new state). */
    private fun remove(card: View, then: () -> Unit) {
        card.isEnabled = false
        card.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(140).setInterpolator(DecelerateInterpolator())
            .withEndAction(then).start()
    }

    private fun spin() {
        recheck.animate().cancel()
        recheck.rotation = 0f
        recheck.animate().rotation(360f).setDuration(520).setInterpolator(SETTLE).start()
    }

    private fun pop(v: View) {
        v.scaleX = 0.92f
        v.scaleY = 0.92f
        v.alpha = 0f
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    // ------------------------------------------------------------------ pieces

    private fun pill(color: Int, ripple: Int) = RippleDrawable(
        ColorStateList.valueOf(ripple),
        GradientDrawable().apply { cornerRadius = 100 * density; setColor(color) },
        null,
    )

    /** A word the user can pick instead of the suggested fix: outlined, in the text colour. */
    private fun alternativeChip(word: String, onClick: () -> Unit) = TextView(context).apply {
        text = word
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(palette.text)
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = dp(76f)
        setPadding(dp(10f), 0, dp(10f), 0)
        background = RippleDrawable(
            ColorStateList.valueOf(palette.keyPressed),
            GradientDrawable().apply {
                cornerRadius = 100 * density
                setStroke(dp(1f).coerceAtLeast(1), ColorUtils.setAlphaComponent(palette.hint, 0x80))
            },
            null,
        )
        contentDescription = "Use $word"
        setOnClickListener { onClick() }
    }

    private fun textButton(label: String, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(12f), 0, dp(12f), 0)
        if (filled) {
            setTextColor(palette.onAccent)
            background = pill(palette.accent, ColorUtils.setAlphaComponent(palette.onAccent, 0x40))
        } else {
            setTextColor(palette.hint)
            background = RippleDrawable(ColorStateList.valueOf(palette.keyPressed), null, GradientDrawable().apply { cornerRadius = 100 * density; setColor(Color.WHITE) })
        }
        setOnClickListener { onClick() }
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

    private fun dot(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setBounds(0, 0, dp(7f), dp(7f))
    }

    private companion object {
        val SETTLE = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)

        // Fixed colours for the three kinds, readable on light and dark themes.
        val SPELLING_RED = Color.rgb(0xE5, 0x48, 0x4D)
        val PUNCTUATION_BLUE = Color.rgb(0x3E, 0x8E, 0xF0)
        val GRAMMAR_AMBER = Color.rgb(0xE0, 0x9A, 0x1F)
        val AI_VIOLET = Color.rgb(0x9B, 0x7B, 0xF7)
    }
}
