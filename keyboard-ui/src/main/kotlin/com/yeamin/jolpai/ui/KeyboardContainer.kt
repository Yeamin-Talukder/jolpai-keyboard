package com.yeamin.jolpai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.animation.togetherWith
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import com.yeamin.jolpai.clipboard.PanelColors
import com.yeamin.jolpai.core.KeyboardUiState
import com.yeamin.jolpai.core.StripItem
import com.yeamin.jolpai.core.model.HostCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.gesture.GestureDispatcher
import com.yeamin.jolpai.core.gesture.GestureType
import com.yeamin.jolpai.core.gesture.GestureZone
import com.yeamin.jolpai.core.gesture.SwipeDirection
import com.yeamin.jolpai.core.gesture.ToolbarSwipe
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.OneHanded

/**
 * The keyboard's whole input view:
 *
 * ```
 * ┌ top bar: [⊞]  teh │ the │ then   (no word: 😊 📋 🎤 ✎ ⚙ spread evenly)  ┐
 * │ KeyboardView (key grid)                                              │
 * └──────────────────────────────────────────────────────────────────────┘
 * + KeyPopupView drawn on top of both (previews, long-press choices)
 * ```
 *
 * The strip scrolls sideways when there are more suggestions than fit (Avro candidates).
 */
@SuppressLint("ViewConstructor")
class KeyboardContainer(
    context: Context,
    private val onAction: (KeyAction) -> Unit,
    /**
     * Content for the clipboard, emoji and voice panels, supplied by the service (it owns
     * their data). The editing panel is built here.
     */
    private val servicePanel: @Composable (Panel, PanelColors, KeyboardUiState) -> Unit,
    private val palette: KeyboardPalette = KeyboardPalette.forContext(context),
    private val options: LayoutOptions = LayoutOptions(),
    feedback: KeyFeedback? = null,
    /** Changes to one-handed / floating mode made from the keyboard itself. */
    private val onModeChange: (ModeChange) -> Unit = {},
    /** Learned touch offsets for predictive touch (owned and saved by the service). */
    private val touchCalibration: com.yeamin.jolpai.core.touch.TouchCalibration? = null,
    /** Voice typing in the top bar: start, pause, language, close, or the full voice panel. */
    private val onVoice: (VoiceCommand) -> Unit = {},
    /** The translate card asks for the offline Bangla pack to be downloaded. */
    private val onTranslateDownload: () -> Unit = {},
    /** The Check card asks for on-device AI proofreading of this text. */
    private val onAiCheck: (String) -> Unit = {},
) : FrameLayout(context) {

    /** Size and position options that change the keyboard's structure. */
    data class LayoutOptions(
        val heightScale: Float = 1f,
        val oneHanded: OneHanded = OneHanded.OFF,
        val floating: Boolean = false,
        val floatX: Float = 0.5f,
        val floatY: Float = 0f,
        /** The typeface on the keys. */
        val keyFont: com.yeamin.jolpai.core.settings.KeyFont = com.yeamin.jolpai.core.settings.KeyFont.SYSTEM,
        /** A separate font for Bangla letters (null: [keyFont] for both, if it has them). */
        val banglaKeyFont: com.yeamin.jolpai.core.settings.KeyFont? = null,
        /** Changes when a new custom key font is picked or removed, so the keyboard reloads it. */
        val customFontVersion: Long = 0,
        /** Keys split into two halves (worked out from the setting and the orientation). */
        val split: Boolean = false,
    )

    sealed interface ModeChange {
        data class SetOneHanded(val mode: OneHanded) : ModeChange
        data class SetFloating(val floating: Boolean) : ModeChange
        data class MoveFloating(val x: Float, val y: Float) : ModeChange
    }

    private val density = resources.displayMetrics.density

    val panelColors = PanelColors(
        background = Color(palette.background),
        surface = Color(palette.key),
        text = Color(palette.text),
        hint = Color(palette.hint),
        accent = Color(palette.accent),
        onAccent = Color(palette.onAccent),
    )

    /** Toolbar buttons (emoji, clipboard, voice, editing, then settings), spread evenly. */
    private val toolbarButtons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private var shownToolbar: List<ToolbarItem>? = null
    private var shownPanel: Panel? = null
    private var shownTranslating = false
    private var shownNumberPad = false

    // ------------------------------------------------------------------ translate card

    /** The translate box above the toolbar (the keys stay where they are). */
    private val translateCard = TranslateCardView(context, palette, object : TranslateCardView.Listener {
        override fun onClose() = onAction(KeyAction.EndTranslate)
        override fun onSwap() = onAction(KeyAction.SwapTranslate)
        override fun onClear() = onAction(KeyAction.ClearTranslate)
        override fun onInsert(text: String) = onAction(KeyAction.CommitTranslation(text))
        override fun onDownload() = onTranslateDownload()
        override fun onFixIssue(issue: com.yeamin.jolpai.suggest.TextIssue) = onAction(KeyAction.FixTranslateIssue(issue))
        override fun onIgnoreIssue(issue: com.yeamin.jolpai.suggest.TextIssue) = onAction(KeyAction.IgnoreTranslateIssue(issue))
        override fun onFixAll() = onAction(KeyAction.ApplyTranslateSpelling)
    })
    private var translateUi: com.yeamin.jolpai.core.TranslateUi? = null

    /** The Check card (spelling, punctuation, grammar) above the toolbar. */
    private val checkCard = CheckCardView(context, palette, object : CheckCardView.Listener {
        override fun onClose() = onAction(KeyAction.EndCheck)
        override fun onRecheck() = onAction(KeyAction.Recheck)
        override fun onFix(issue: com.yeamin.jolpai.suggest.TextIssue) = onAction(KeyAction.FixIssue(issue))
        override fun onFixAll() = onAction(KeyAction.FixAllIssues)
        override fun onIgnore(issue: com.yeamin.jolpai.suggest.TextIssue) = onAction(KeyAction.IgnoreIssue(issue))
        override fun onAiCheck() = onAiCheck(checkUi?.text.orEmpty())
        override fun onAiApply(original: String, replacement: String) = onAction(KeyAction.ApplyRewrite(original, replacement))
    })
    private var checkUi: com.yeamin.jolpai.core.CheckUi? = null
    private var shownChecking = false
    private var translateStatus = TranslateStatus.CHECKING
    private var translateBusy = false

    /** The service reports the on-device AI proofreading state for the Check card. */
    fun setAi(state: CheckCardView.Ai) = checkCard.setAi(state)

    /** The service reports whether offline translation is ready (or downloading, …). */
    fun setTranslateStatus(status: TranslateStatus) {
        translateStatus = status
        renderTranslate()
    }

    /** The service is (or stopped) working out a translation of the current text. */
    fun setTranslateBusy(busy: Boolean) {
        if (busy == translateBusy) return
        translateBusy = busy
        renderTranslate()
    }

    private fun renderTranslate() {
        val t = translateUi
        if (t == null) {
            translateCard.hide()
            return
        }
        if (!translateCard.isShowing) translateCard.show()
        translateCard.render(TranslateCardModel(t.source, t.toBangla, t.translation, translateStatus, translateBusy && t.translation == null, t.issues))
    }

    private val uiState = mutableStateOf<KeyboardUiState?>(null)

    /** Panels drawn with Compose over the keys, at the keys' size. */
    private val panelView = ComposeView(context).apply {
        visibility = View.GONE
        setContent {
            val s = uiState.value ?: return@setContent
            when (s.panel) {
                Panel.NONE -> Unit
                // Emoji and clipboard open above the toolbar, unless the user put them in place of the keys.
                Panel.EMOJI, Panel.CLIPBOARD -> if (!s.panelsAbove) servicePanel(s.panel, panelColors, s)
                Panel.EDITING -> EditingPanel(s.selecting, s.textStyle, panelColors, onAction)
                else -> servicePanel(s.panel, panelColors, s)
            }
        }
    }

    // ------------------------------------------------------------------ upper section (emoji, clipboard)

    /** Which panel the section above the toolbar shows (kept while it animates away). */
    private val upperPanel = mutableStateOf<Panel?>(null)

    /** Emoji and clipboard, drawn with Compose above the toolbar; switching between them crossfades. */
    private val upperView = ComposeView(context).apply {
        setContent {
            val p = upperPanel.value
            val s = uiState.value
            if (p != null && s != null) {
                androidx.compose.animation.AnimatedContent(
                    targetState = p,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 40)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 10 }) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
                    },
                    label = "upper",
                ) { panel -> servicePanel(panel, panelColors, s) }
            }
        }
    }

    /**
     * The section above the toolbar for emoji and clipboard. The keys stay where they are and
     * stay usable: pressing one closes the section and types (see [keyFromKeyboard]).
     */
    private val upperSection = FrameLayout(context).apply {
        visibility = View.GONE
        addView(upperView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    private var upperHiding = false

    /** Rises into place from just above the toolbar. */
    private fun showUpper() {
        upperHiding = false
        upperSection.animate().cancel()
        if (upperSection.isVisible && upperSection.alpha >= 1f) return
        val fresh = upperSection.visibility != View.VISIBLE
        upperSection.visibility = View.VISIBLE
        upperSection.pivotY = UPPER_HEIGHT_DP * density
        if (fresh) {
            upperSection.alpha = 0f
            upperSection.translationY = 28 * density
            upperSection.scaleX = 0.97f
            upperSection.scaleY = 0.97f
        }
        upperSection.animate()
            .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(300).setInterpolator(UPPER_SETTLE)
            .withEndAction(null)
            .start()
    }

    /** Sinks back towards the toolbar and fades, then gives the space back to the app. */
    private fun hideUpper() {
        if (upperSection.visibility != View.VISIBLE || upperHiding) return
        upperHiding = true
        upperSection.animate().cancel()
        upperSection.animate()
            .alpha(0f).translationY(20 * density).scaleX(0.98f).scaleY(0.98f)
            .setDuration(170).setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                upperSection.visibility = View.GONE
                upperSection.alpha = 1f
                upperSection.translationY = 0f
                upperSection.scaleX = 1f
                upperSection.scaleY = 1f
                upperHiding = false
                upperPanel.value = null
            }
            .start()
    }

    private val upperOpen: Boolean
        get() = uiState.value?.let { it.panelsAbove && (it.panel == Panel.EMOJI || it.panel == Panel.CLIPBOARD) } == true

    /**
     * A key pressed on the keyboard while emoji or clipboard is open above it: the section
     * closes first, then the key types as usual. (Not while typing a search for that panel,
     * and not for keys that open a panel themselves.)
     */
    private fun keyFromKeyboard(action: KeyAction) {
        val opensSomething = action is KeyAction.ShowPanel || action is KeyAction.OpenEmojiTab ||
            action is KeyAction.StartSearch || action is KeyAction.Host || action is KeyAction.StartTranslate ||
            action is KeyAction.StartCheck
        if (upperOpen && uiState.value?.search == null && !opensSomething) onAction(KeyAction.ShowPanel(Panel.NONE))
        onAction(action)
    }

    @SuppressLint("SetTextI18n")
    private val privateLabel = toolbarText("Private · nothing saved", 12f).apply {
        setTextColor(palette.hint)
        setCompoundDrawablesRelative(KeyboardIcon.LOCK.drawable(context, palette.hint, 14 * density).apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }, null, null, null)
        compoundDrawablePadding = (6 * density).toInt()
        setPadding((10 * density).toInt(), 0, (6 * density).toInt(), 0)
        visibility = View.GONE
    }

    private val idleRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(privateLabel, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(toolbarButtons, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    /**
     * While suggestions fill the strip, this button at its start brings the tools back
     * (and then returns to the suggestions), so emoji and clipboard are always one tap away.
     */
    private var toolsWhileTyping = false
    private val appsIcon = KeyboardIcon.APPS.drawable(context, palette.text, 20 * density)
    private val backIcon = KeyboardIcon.CHEVRON_LEFT.drawable(context, palette.text, 22 * density)
    /** The current layout (EN / অভ্র / জাতীয়) at the end of the top bar: tap to switch, hold for the system chooser. */
    private val languageButton = toolbarText("", 13f).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setCompoundDrawablesRelative(KeyboardIcon.LANGUAGE.drawable(context, palette.text, 16 * density).apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }, null, null, null)
        compoundDrawablePadding = (6 * density).toInt()
        setPadding((10 * density).toInt(), 0, (12 * density).toInt(), 0)
        background = pressRipple(palette.functionKey)
        setOnClickListener { onAction(KeyAction.NextLayout) }
        setOnLongClickListener {
            onAction(KeyAction.Host(HostCommand.SYSTEM_KEYBOARD_PICKER))
            true
        }
    }

    private val leadButton = iconView(KeyboardIcon.APPS, "Show tools").apply {
        setOnClickListener {
            toolsWhileTyping = !toolsWhileTyping
            applyStripMode(shownStrip.isNotEmpty())
        }
    }

    private val stripRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val stripScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(stripRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        visibility = View.GONE
    }

    private val popupView = KeyPopupView(context, palette)

    val keyboardView = KeyboardView(context, palette).apply {
        popup = popupView
        listener = KeyboardView.Listener { keyFromKeyboard(it) }
        this.feedback = feedback
        heightScale = options.heightScale
        split = options.split && !options.floating && options.oneHanded == OneHanded.OFF
        val english = KeyFonts.load(context, options.keyFont)
        val bangla = KeyFonts.load(context, com.yeamin.jolpai.core.settings.banglaLettersFont(options.keyFont, options.banglaKeyFont))
        setKeyFont(english, bangla)
        popupView.setKeyFont(english, bangla)
    }

    /** Toolbar + keys: the whole keyboard (the movable card in floating mode). */
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private var shownStrip: List<StripItem> = emptyList()

    /** True in floating mode: the service then lets touches outside [column] reach the app. */
    val isFloating: Boolean get() = options.floating

    // ------------------------------------------------------------------ voice typing in the top bar

    /** Voice typing requests from the top bar (the service runs the recogniser). */
    enum class VoiceCommand { START, OPEN_PANEL, MIC, LANGUAGE, CLOSE, BACKSPACE }

    private val topBar = FrameLayout(context)

    /** Draws the top-bar swipe's pill (see [SwipeHintView]). */
    private val swipeHint = SwipeHintView(context, palette)
    private lateinit var toolbarRow: View
    private lateinit var keysHolder: View
    private val columnBackground by lazy { keyboardBackground() }
    private var voiceSize = VoiceSize.FULL

    private val voiceListener = object : VoiceBarView.Listener {
        override fun onMic() = onVoice(VoiceCommand.MIC)
        override fun onLanguage() = onVoice(VoiceCommand.LANGUAGE)
        override fun onClose() = onVoice(VoiceCommand.CLOSE)
        override fun onBackspace() = onVoice(VoiceCommand.BACKSPACE)
        override fun onSize(size: VoiceSize) = setVoiceSize(size)
        override fun onDrag(dx: Float, dy: Float, done: Boolean) = if (done) pillDropped() else movePill(dx, dy)
    }

    /** The voice bar over the toolbar. */
    private val voiceBar = VoiceBarView(context, palette, voiceListener, pill = false)

    /** The minimised voice pill, floating over the app (wide or tall, draggable). */
    private val voicePill = VoiceBarView(context, palette, voiceListener, pill = true)

    /** Where the pill sits (its top-left inside this view), or null for the default spot. */
    private var pillX: Int? = null
    private var pillY: Int? = null
    private var navBottom = 0
    private var navLeft = 0
    private var navRight = 0

    private companion object {
        /** Bottom padding last worked out from the system's insets (-1 until known). */
        var lastBottomPadding = -1

        /** A top-bar touch moving less than this is still a tap on a button. */
        const val SWIPE_SLOP_DP = 12f

        /** The top-bar swipe's pill is full (let go to run it) after at least this far. */
        const val MIN_ARM_DP = 56f

        /** Height of the emoji / clipboard section above the toolbar. */
        const val UPPER_HEIGHT_DP = 236f

        /** Quick start, long gentle settle (as the translate and check cards). */
        val UPPER_SETTLE = android.view.animation.PathInterpolator(0.2f, 0.9f, 0.25f, 1f)

        /** Radius of the keyboard's rounded top corners. */
        const val TOP_CORNER_DP = 18f
    }

    private val pillMode: Boolean get() = voiceSize == VoiceSize.PILL_H || voiceSize == VoiceSize.PILL_V

    /** Shows the voice bar over the toolbar ([model] non-null) or puts the toolbar back. */
    fun renderVoice(model: VoiceBarModel?) {
        if (model == null) {
            if (voiceBar.isVisible || voicePill.isVisible) {
                setVoiceSize(VoiceSize.FULL)
                voiceBar.visibility = View.GONE
                toolbarRow.visibility = View.VISIBLE
                pillX = null
                pillY = null
                pillDock = null
            }
            return
        }
        voiceBar.show(model)
        voicePill.show(model)
        if (!voiceBar.isVisible) {
            keyboardView.cancelTouches()
            voiceBar.visibility = View.VISIBLE
            toolbarRow.visibility = View.INVISIBLE
        }
    }

    /**
     * Shrinks the keyboard while dictating, like Google's keyboard:
     * - COLLAPSED hides the keys; only the voice bar stays, and the app gets the room.
     * - PILL_H / PILL_V hide the keyboard and float a small wide or tall pill over the app. This
     *   view then grows into a see-through area the pill can be dragged around in, and only the
     *   pill takes touches ([voicePillRectInWindow]); everything else reaches the app.
     * In floating mode (already a small card) only collapsing applies.
     */
    private fun setVoiceSize(requested: VoiceSize) {
        val size = if (options.floating && (requested == VoiceSize.PILL_H || requested == VoiceSize.PILL_V)) VoiceSize.COLLAPSED else requested
        if (size == voiceSize) return
        val wasPill = pillMode
        voiceSize = size
        if (pillMode) {
            voicePill.setSize(size)
            column.visibility = View.GONE
            voicePill.visibility = View.VISIBLE
            // After turning (wide ↔ tall), keep it inside the area once its new size is known.
            if (wasPill) voicePill.post { movePill(0f, 0f) }
        } else {
            voiceBar.setSize(size)
            voicePill.visibility = View.GONE
            column.visibility = View.VISIBLE
            keysHolder.visibility = if (size == VoiceSize.FULL) View.VISIBLE else View.GONE
        }
        requestLayout()
    }

    /** Docked to the left (-1) or right (1) edge as a tall pill, or null when placed freely. */
    private var pillDock: Int? = null

    /**
     * Dropping the pill, like Google's keyboard: against the left or right edge a wide pill turns
     * tall and docks there; a tall pill dragged back towards the middle turns wide again.
     */
    private fun pillDropped() {
        if (!pillMode) return
        val (x, _) = pillPosition()
        val w = voicePill.width
        val edge = (28 * density).toInt()
        val leftGap = x - navLeft
        val rightGap = width - navRight - (x + w)
        when {
            voiceSize == VoiceSize.PILL_H && (leftGap <= edge || rightGap <= edge) -> {
                pillDock = if (leftGap <= edge) -1 else 1
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                setVoiceSize(VoiceSize.PILL_V)
            }
            voiceSize == VoiceSize.PILL_V && minOf(leftGap, rightGap) > (72 * density).toInt() -> {
                // Centre the wide pill where the tall one was let go.
                val centre = x + w / 2
                pillX = centre - (WIDE_PILL_DP * density / 2).toInt()
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                setVoiceSize(VoiceSize.PILL_H)
                voicePill.post { movePill(0f, 0f) }
            }
        }
    }

    /** The area the pill can move in: the lower part of the screen. */
    private fun pillArea(): Int = (resources.displayMetrics.heightPixels * 0.45f).toInt()

    /** Moves the pill by ([dx], [dy]), keeping it inside this view and above the navigation bar. */
    private fun movePill(dx: Float, dy: Float) {
        if (!pillMode || width == 0) return
        if (dx != 0f || dy != 0f) pillDock = null // dragging undocks; a plain re-check keeps it
        val m = (8 * density).toInt()
        val minX = navLeft + m
        val maxX = (width - voicePill.width - navRight - m).coerceAtLeast(minX)
        val maxY = (height - voicePill.height - navBottom - m).coerceAtLeast(m)
        val (x, y) = pillPosition()
        pillX = (x + dx).toInt().coerceIn(minX, maxX)
        pillY = (y + dy).toInt().coerceIn(m, maxY)
        requestLayout()
    }

    /** The pill's spot: where it was dragged, or bottom right above the navigation bar. */
    private fun pillPosition(): Pair<Int, Int> {
        val m = (12 * density).toInt()
        // Clear of a side navigation bar or camera cutout in landscape.
        val minX = navLeft + m
        val maxX = (width - voicePill.measuredWidth - navRight - m).coerceAtLeast(minX)
        val x = when (pillDock) {
            -1 -> minX
            1 -> maxX
            else -> pillX ?: maxX
        }
        val y = pillY ?: (height - voicePill.measuredHeight - navBottom - m).coerceAtLeast(m)
        return x to y
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (options.floating) fitFloatingCard() else recheckGapBelow()
        if (pillMode && voicePill.isVisible) {
            val (x, y) = pillPosition()
            voicePill.layout(x, y, x + voicePill.measuredWidth, y + voicePill.measuredHeight)
        }
    }

    /** How far the floating card can move sideways: inside the side bars / cutout padding. */
    private fun floatRoom(): Int = (width - paddingLeft - paddingRight - column.width).coerceAtLeast(0)

    /**
     * The floating card is sized from the screen width before the side insets are known; once
     * laid out, shrink and move it so it stays clear of a side navigation bar or camera cutout.
     */
    private fun fitFloatingCard() {
        if (width == 0) return
        val lp = column.layoutParams as? LayoutParams ?: return
        val room = width - paddingLeft - paddingRight
        val cardWidth = minOf(lp.width, room)
        val left = lp.leftMargin.coerceIn(0, (room - cardWidth).coerceAtLeast(0))
        if (cardWidth != lp.width || left != lp.leftMargin) post {
            lp.width = cardWidth
            lp.leftMargin = left
            column.layoutParams = lp
        }
    }

    /** The voice pill's bounds while minimised (touches elsewhere reach the app). */
    fun voicePillRectInWindow(): android.graphics.Rect? {
        if (!pillMode || !voicePill.isVisible || voicePill.width == 0) return null
        val loc = IntArray(2)
        voicePill.getLocationInWindow(loc)
        return android.graphics.Rect(loc[0], loc[1], loc[0] + voicePill.width, loc[1] + voicePill.height)
    }

    init {
        column.background = columnBackground

        val middle = FrameLayout(context).apply {
            addView(idleRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(stripScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        val toolbar = SwipeToolbar().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            addView(leadButton, LinearLayout.LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT))
            addView(middle, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(languageButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, (32 * density).toInt()).apply { leftMargin = (4 * density).toInt(); rightMargin = (2 * density).toInt() })
        }
        if (options.floating) column.addView(floatingHandle(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, (28 * density).toInt()))
        // Translate opens above the toolbar, so the window grows upward and the keys don't move.
        // Each section above the toolbar closes with a swipe down.
        fun swipeable(section: View, onClose: () -> Unit) = SwipeDownToClose(context, onClose).apply {
            addView(section, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        column.addView(swipeable(translateCard) { onAction(KeyAction.EndTranslate) }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(swipeable(checkCard) { onAction(KeyAction.EndCheck) }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(
            SwipeDownToClose(context) { onAction(KeyAction.ShowPanel(Panel.NONE)) }.apply {
                addView(upperSection, LayoutParams(LayoutParams.MATCH_PARENT, (UPPER_HEIGHT_DP * density).toInt()))
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        // The top bar: the toolbar, with the voice bar laid over it while dictating.
        toolbarRow = toolbar
        topBar.addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        voiceBar.visibility = View.GONE
        topBar.addView(voiceBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Shows a top-bar swipe as it happens (the top bar has a fixed height, so this can't stretch it).
        topBar.addView(swipeHint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        column.addView(topBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, (44 * density).toInt()))
        // The keys stay laid out (invisible) under a panel, so the keyboard keeps its height.
        val keysArea = FrameLayout(context).apply {
            addView(keyboardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            // Theme press effects: a layer over the keys that redraws only while an effect plays.
            palette.pressEffect?.let { effect ->
                val effects = KeyEffectsView(context, effect, palette.effectColor ?: palette.accent, keyboardView.keyCorner, keyboardView.letterTypeface)
                keyboardView.onKeyPressed = effects::press
                addView(effects, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
            addView(OverlayFrame(context).apply { addView(panelView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        if (options.oneHanded != OneHanded.OFF && !options.floating) {
            // One-handed: keys squeezed to one side, with buttons to switch side or go back.
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val side = oneHandedSide()
            if (options.oneHanded == OneHanded.RIGHT) row.addView(side, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.18f))
            row.addView(keysArea, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.82f))
            if (options.oneHanded == OneHanded.LEFT) row.addView(side, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.18f))
            column.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            keysHolder = row
        } else {
            column.addView(keysArea, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            keysHolder = keysArea
        }

        if (options.floating) {
            // A smaller card over the app; the rest of this view is see-through and lets touches through.
            column.clipToOutline = true
            column.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, 16 * density)
            }
            column.elevation = 8 * density
            val screenWidth = resources.displayMetrics.widthPixels
            val cardWidth = (screenWidth * 0.86f).toInt()
            addView(column, LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ((screenWidth - cardWidth) * options.floatX).toInt()
                topMargin = (floatTravel() * (1 - options.floatY)).toInt()
            })
        } else {
            // Softly rounded top corners (the app shows through them; the window is see-through).
            // The outline reaches past the bottom edge, so only the top corners are rounded.
            column.clipToOutline = true
            column.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    val r = TOP_CORNER_DP * density
                    outline.setRoundRect(0, 0, view.width, view.height + r.toInt(), r)
                }
            }
            addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(popupView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        voicePill.visibility = View.GONE
        addView(voicePill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        // Keep keys clear of the gesture/navigation bar when the window is drawn edge to edge, plus a
        // little room so the bottom row isn't crowded against the system's hide and switch buttons.
        val bottomGap = (10 * density).toInt()
        // A rebuilt keyboard (e.g. after a theme change) starts with the last known bottom space,
        // so it doesn't show squeezed until the system next sends insets.
        if (!options.floating) column.setPadding(0, 0, 0, if (lastBottomPadding >= 0) lastBottomPadding else bottomGap)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val reported = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            // The system draws its own hide (⌄) and keyboard-switch (🌐) buttons in a taller bar
            // under a keyboard than under apps. On Android 17 the inset reported to the keyboard
            // is the short app bar (63 px vs 108 px on a Pixel 7 Pro), so the bottom row sat on
            // those buttons: keep clear of most of the keyboard bar whenever the bar overlaps the
            // window. When it doesn't (Android 14 lays the keyboard out above an opaque bar and
            // reports 0, e.g. Xiaomi HyperOS), add nothing, or an empty band shows above the bar.
            val sideBar = reported.left > 0 || reported.right > 0
            lastGapBelow = gapBelowWindow()
            val navHeight = com.yeamin.jolpai.core.layout.NavBarInset.bottomPadding(
                reportedBottom = reported.bottom,
                sideBar = sideBar,
                imeBarHeight = imeNavBarHeight(),
                gapBelowWindow = lastGapBelow,
            )
            // At the sides, keep clear of a side navigation bar and of a camera cutout (landscape,
            // drawn edge to edge from Android 15); both are 0 where the system already keeps the
            // window away from them.
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val nav = androidx.core.graphics.Insets.of(
                com.yeamin.jolpai.core.layout.NavBarInset.sidePadding(reported.left, cutout.left),
                reported.top,
                com.yeamin.jolpai.core.layout.NavBarInset.sidePadding(reported.right, cutout.right),
                navHeight,
            )
            navBottom = nav.bottom
            navLeft = nav.left
            navRight = nav.right
            val gap = if (nav.bottom > 0) 0 else bottomGap
            if (options.floating) {
                if (paddingLeft != nav.left || paddingRight != nav.right || paddingBottom != nav.bottom) {
                    setPadding(nav.left, 0, nav.right, nav.bottom)
                }
            } else {
                val bottom = nav.bottom + gap
                lastBottomPadding = bottom
                if (column.paddingBottom != bottom || column.paddingLeft != nav.left || column.paddingRight != nav.right) {
                    column.setPadding(nav.left, 0, nav.right, bottom)
                }
            }
            insets
        }
    }

    // Ask for fresh insets whenever a (re)built keyboard is attached, instead of waiting for the
    // next system layout pass (which made a new theme look cut off until the first key press).
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    /** Gap below the window used for the last bottom padding (-1 until insets arrive). */
    private var lastGapBelow = -1

    /**
     * How far the keyboard window's bottom edge sits above the bottom of the screen: room the
     * system keeps for its own bar under the window. 0 when it reaches the bottom or isn't laid
     * out yet.
     */
    private fun gapBelowWindow(): Int {
        val root = rootView ?: return 0
        if (!root.isLaidOut || root.height == 0) return 0
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        return (screenHeight() - (loc[1] + root.height)).coerceAtLeast(0)
    }

    /** Full height of the display, system bars included. */
    private fun screenHeight(): Int {
        val wm = context.getSystemService(android.view.WindowManager::class.java)
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            wm.maximumWindowMetrics.bounds.height()
        } else {
            val size = android.graphics.Point()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(size)
            size.y
        }
    }

    /** Once the window has its final place, check the bar overlap again if it moved. */
    private fun recheckGapBelow() {
        if (lastGapBelow >= 0 && gapBelowWindow() != lastGapBelow) ViewCompat.requestApplyInsets(this)
    }

    /**
     * Height of the navigation bar the system shows under a keyboard (with its hide and switch
     * buttons): the platform's `navigation_bar_frame_height`, capped so an odd value can't eat
     * the keys; 0 when the platform doesn't say.
     */
    @SuppressLint("DiscouragedApi", "InternalInsetResource") // platform dimen with no public API
    private fun imeNavBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
        val h = if (id != 0) resources.getDimensionPixelSize(id) else 0
        // 40 dp when the platform doesn't say: room for the system's ⌄ and 🌐 buttons.
        return (if (h > 0) h else (40 * density).toInt()).coerceAtMost((56 * density).toInt())
    }

    /**
     * The theme background: flat colour, vertical gradient, soft glows (ambient themes, drifting
     * for dynamic ones), or a picture with a dark overlay.
     */
    private fun keyboardBackground(): android.graphics.drawable.Drawable {
        val base = if (palette.glows.isNotEmpty()) {
            AmbientBackgroundDrawable(context, palette.background, palette.backgroundEnd, palette.glows, palette.driftPeriodMs)
        } else palette.backgroundEnd?.let {
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(palette.background, it))
        } ?: palette.background.toDrawable()
        val image = palette.backgroundImage?.let { thumbnail(it, 1080) } ?: return base
        val dim = ((palette.imageDim * 255).toInt() shl 24).toDrawable()
        return android.graphics.drawable.LayerDrawable(arrayOf(base, CenterCropDrawable(image), dim))
    }

    /** How far the floating card can move up from the bottom. */
    private fun floatTravel(): Int = (resources.displayMetrics.heightPixels * 0.35f).toInt()

    /** Height this view needs: the card plus the room it can move in. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (pillMode) {
            // Minimised voice pill: a see-through area over the app for the pill to move in.
            setMeasuredDimension(measuredWidth, pillArea())
            return
        }
        if (options.floating) {
            val needed = column.measuredHeight + floatTravel() + paddingBottom
            setMeasuredDimension(measuredWidth, needed)
        }
    }

    /** The floating card's bounds in window coordinates, for the touchable region. */
    fun cardRectInWindow(): android.graphics.Rect? {
        if (!options.floating || column.width == 0) return null
        val loc = IntArray(2)
        column.getLocationInWindow(loc)
        return android.graphics.Rect(loc[0], loc[1], loc[0] + column.width, loc[1] + column.height)
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun floatingHandle(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val grip = android.widget.ImageView(context).apply {
            setImageDrawable(KeyboardIcon.GRIP.drawable(context, palette.hint, 20 * density))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = "Drag to move the keyboard"
        }
        val dock = iconView(KeyboardIcon.DOCK, "Dock the keyboard at the bottom", sizeDp = 18f).apply {
            setOnClickListener { onModeChange(ModeChange.SetFloating(false)) }
        }
        addView(grip, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(dock, LinearLayout.LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT).apply { rightMargin = (4 * density).toInt() })
        var lastX = 0f
        var lastY = 0f
        grip.setOnTouchListener { _, e ->
            val lp = column.layoutParams as LayoutParams
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val maxLeft = floatRoom()
                    lp.leftMargin = (lp.leftMargin + (e.rawX - lastX)).toInt().coerceIn(0, maxLeft)
                    lp.topMargin = (lp.topMargin + (e.rawY - lastY)).toInt().coerceIn(0, floatTravel())
                    lastX = e.rawX
                    lastY = e.rawY
                    column.layoutParams = lp
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val maxLeft = floatRoom().coerceAtLeast(1)
                    onModeChange(ModeChange.MoveFloating(lp.leftMargin / maxLeft.toFloat(), 1 - lp.topMargin / floatTravel().toFloat()))
                }
            }
            true
        }
    }

    private fun oneHandedSide(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val towards = if (options.oneHanded == OneHanded.LEFT) KeyboardIcon.CHEVRON_RIGHT else KeyboardIcon.CHEVRON_LEFT
        val swap = iconView(towards, "Move keyboard to the other side", sizeDp = 26f).apply {
            setOnClickListener {
                onModeChange(ModeChange.SetOneHanded(if (options.oneHanded == OneHanded.LEFT) OneHanded.RIGHT else OneHanded.LEFT))
            }
        }
        val full = iconView(KeyboardIcon.EXPAND, "Full-width keyboard").apply {
            setOnClickListener { onModeChange(ModeChange.SetOneHanded(OneHanded.OFF)) }
        }
        addView(swap, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(full, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun render(state: KeyboardUiState) {
        keyboardView.setKeyboard(state.layout, state.shift)
        keyboardView.cursorSwipe = state.cursorSwipe
        keyboardView.spaceLanguageSwipe = state.spaceLanguageSwipe
        keyboardView.trackpadVertical = state.trackpadVertical
        keyboardView.longPressMs = state.longPressMs
        keyboardView.gestures = state.gestures
        keyboardView.enterHold = state.enterHold
        toolbarGestures = state.gestures
        keyboardView.nextChars = state.nextChars
        keyboardView.learnTouches = state.touchCalibration
        keyboardView.calibration = if (state.touchCalibration) touchCalibration else null
        val layoutName = if (state.layoutId == LayoutId.ENGLISH) "EN" else state.layoutId.shortLabel
        if (languageButton.text.toString() != layoutName) {
            languageButton.text = layoutName
            languageButton.contentDescription = "Language: ${state.layoutId.displayName}. Tap to switch, hold for other keyboards"
        }

        if (uiState.value?.panel != state.panel && state.panel != Panel.NONE) keyboardView.cancelTouches()
        uiState.value = state
        // Emoji and clipboard open above the toolbar (the keys stay), or in place of the keys if the
        // user chose that (Toolbar settings); editing and voice always cover the keys.
        val upper = state.panelsAbove && (state.panel == Panel.EMOJI || state.panel == Panel.CLIPBOARD)
        val showPanel = state.panel != Panel.NONE && !upper
        panelView.visibility = if (showPanel) View.VISIBLE else View.GONE
        keyboardView.visibility = if (showPanel) View.INVISIBLE else View.VISIBLE
        if (upper) {
            upperPanel.value = state.panel
            showUpper()
        } else {
            hideUpper()
        }
        // Password fields hide the clipboard, voice and emoji shortcuts.
        // (The number pad stays: PIN fields are often plain password fields.)
        val items = if (state.field.isPassword) state.toolbar.filter { it == ToolbarItem.EDIT || it == ToolbarItem.NUMBER_PAD } else state.toolbar
        val translating = state.translate != null
        val checking = state.check != null
        val numberPad = state.page == com.yeamin.jolpai.core.model.KeyboardPage.NUMBER
        if (items != shownToolbar || state.panel != shownPanel || translating != shownTranslating || checking != shownChecking || numberPad != shownNumberPad) {
            shownToolbar = items
            shownPanel = state.panel
            shownTranslating = translating
            shownChecking = checking
            shownNumberPad = numberPad
            renderToolbar(items, state.panel, translating, checking, numberPad)
        }
        if (state.check != checkUi) {
            checkUi = state.check
            val c = state.check
            if (c == null) {
                checkCard.hide()
            } else {
                if (!checkCard.isShowing) checkCard.show()
                checkCard.render(c)
            }
        }
        if (state.translate != translateUi) {
            translateUi = state.translate
            renderTranslate()
        }
        privateLabel.visibility = if (state.field.isSensitive) View.VISIBLE else View.GONE

        if (state.strip != shownStrip) {
            shownStrip = state.strip
            toolsWhileTyping = false
            renderStrip(state.strip)
        }
        applyStripMode(state.strip.isNotEmpty())
    }

    /** Suggestions or tools in the top bar; the lead button switches while suggestions exist. */
    private fun applyStripMode(hasStrip: Boolean) {
        val showStrip = hasStrip && !toolsWhileTyping
        stripScroll.visibility = if (showStrip) View.VISIBLE else View.GONE
        idleRow.visibility = if (showStrip) View.GONE else View.VISIBLE
        leadButton.visibility = if (hasStrip) View.VISIBLE else View.GONE
        leadButton.setImageDrawable(if (showStrip) appsIcon else backIcon)
        leadButton.contentDescription = if (showStrip) "Show tools" else "Back to suggestions"
    }

    /**
     * Settings first (top left, at the user's request), then the tools in the user's order, then
     * voice typing (always second from the right, next to the language button). Each tool
     * toggles: tap to open its panel, tap again to go back to the keys; the open one is highlighted.
     */
    private fun renderToolbar(items: List<ToolbarItem>, open: Panel, translating: Boolean, checking: Boolean, numberPad: Boolean = false) {
        toolbarButtons.removeAllViews()
        toolbarButtons.addView(
            toolbarButton(KeyboardIcon.SETTINGS, "Keyboard settings", false) { onAction(KeyAction.Host(HostCommand.OPEN_SETTINGS)) },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
        )
        fun add(item: ToolbarItem) {
            val active = when (item) {
                ToolbarItem.TRANSLATE -> translating
                ToolbarItem.CHECK -> checking
                ToolbarItem.NUMBER_PAD -> numberPad && open == Panel.NONE
                else -> item.panel == open
            }
            val description = if (active) "${item.title}. Tap to go back to the keys" else item.title
            toolbarButtons.addView(
                if (item == ToolbarItem.VOICE) {
                    // Tap: dictate in the top bar with the keys still usable. Hold: the full voice panel.
                    toolbarButton(iconFor(item), if (active) description else "Voice typing. Hold for the voice panel", active, onLongClick = { onVoice(VoiceCommand.OPEN_PANEL) }) {
                        if (active) onAction(KeyAction.ShowPanel(Panel.NONE)) else onVoice(VoiceCommand.START)
                    }
                } else if (item == ToolbarItem.CHECK) {
                    // Opens the Check card above the toolbar with what is wrong in the text.
                    toolbarButton(iconFor(item), description, active) {
                        onAction(if (active) KeyAction.EndCheck else KeyAction.StartCheck)
                    }
                } else if (item == ToolbarItem.NUMBER_PAD) {
                    // The number pad in any field; tap again (or its ABC key) for the letters.
                    toolbarButton(iconFor(item), description, active) { onAction(KeyAction.ToggleNumberPad) }
                } else if (item == ToolbarItem.TRANSLATE) {
                    // Opens the translate box above the toolbar; the keys stay for typing into it.
                    toolbarButton(iconFor(item), description, active) {
                        onAction(if (active) KeyAction.EndTranslate else KeyAction.StartTranslate(toBangla = true))
                    }
                } else {
                    toolbarButton(iconFor(item), description, active) {
                        onAction(KeyAction.ShowPanel(if (active) Panel.NONE else item.panel))
                    }
                },
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
            )
        }
        items.filter { it != ToolbarItem.VOICE }.forEach(::add)
        if (ToolbarItem.VOICE in items) add(ToolbarItem.VOICE)
    }

    private fun iconFor(item: ToolbarItem): KeyboardIcon = when (item) {
        ToolbarItem.EMOJI -> KeyboardIcon.EMOJI
        ToolbarItem.CLIPBOARD -> KeyboardIcon.CLIPBOARD
        ToolbarItem.VOICE -> KeyboardIcon.MIC
        ToolbarItem.EDIT -> KeyboardIcon.EDIT
        ToolbarItem.TRANSLATE -> KeyboardIcon.TRANSLATE
        ToolbarItem.CHECK -> KeyboardIcon.SPELLCHECK
        ToolbarItem.NUMBER_PAD -> KeyboardIcon.NUMBERS
    }

    /**
     * Up to three suggestions share the width equally, centred, with thin dividers between
     * them (like Gboard); the one that space will commit is bold. More than three (Avro
     * candidates, emoji search) scroll sideways.
     */
    private fun renderStrip(items: List<StripItem>) {
        stripRow.removeAllViews()
        val fill = items.size <= 3
        stripScroll.scrollTo(0, 0)
        items.forEachIndexed { i, item ->
            if (i > 0) stripRow.addView(divider(), LinearLayout.LayoutParams(dividerWidth, (18 * density).toInt()))
            val cell = toolbarText(item.label, 16f).apply {
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                minWidth = (56 * density).toInt()
                if (item.emphasized) typeface = Typeface.DEFAULT_BOLD
                contentDescription = item.label
                setOnClickListener { onAction(item.action) }
            }
            val lp = if (fill) {
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            } else {
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            }
            stripRow.addView(cell, lp)
        }
        // A filled row needs the scroll view's full width to share.
        stripRow.layoutParams = stripRow.layoutParams.apply {
            width = if (fill) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
        }
    }

    fun dismissPopups() = keyboardView.cancelTouches()

    private var toolbarGestures: GestureDispatcher? = null

    /** Toolbar swipes count while the tools show, or suggestions that don't scroll sideways. */
    private fun toolbarSwipeAllowed(): Boolean =
        stripScroll.visibility != View.VISIBLE || stripRow.width <= stripScroll.width

    /**
     * The top bar, with swipe shortcuts (by default left = Undo, right = Redo) that show what they
     * will do and can be called off (see [ToolbarSwipe]):
     * - a touch that barely moves stays a tap for the button under it;
     * - once the finger clearly heads sideways, a pill with the action slides in and fills;
     * - full pill: a tick, and letting go runs the action (pill pops and glides off);
     * - letting go early, or sliding back, does nothing.
     */
    private inner class SwipeToolbar : LinearLayout(context) {
        private var swipe: ToolbarSwipe? = null
        private var gestures: GestureDispatcher? = null
        private var wasArmed = false

        private fun binding(dir: SwipeDirection) = gestures?.bindingFor(GestureZone.TOOLBAR, GestureType.swipe(dir))

        private fun begin(e: android.view.MotionEvent) {
            val g = toolbarGestures
            swipe = if (g == null || !toolbarSwipeAllowed()) null else ToolbarSwipe(
                slop = SWIPE_SLOP_DP * density,
                // Far enough to be deliberate, never so short that a sloppy tap fires it.
                armDistance = maxOf(g.thresholds.minSwipeDp, MIN_ARM_DP) * density,
                dominance = g.thresholds.dominance,
            ) { dir -> g.bindingFor(GestureZone.TOOLBAR, GestureType.swipe(dir)) != null }
            gestures = g
            wasArmed = false
            swipe?.down(e.x, e.y)
        }

        private fun track(phase: ToolbarSwipe.Phase) {
            when (phase) {
                ToolbarSwipe.Phase.Idle -> Unit
                is ToolbarSwipe.Phase.Tracking -> {
                    wasArmed = false
                    val b = binding(phase.direction)
                    if (b == null) swipeHint.cancel()
                    else swipeHint.show(phase.direction, shortTitle(b.action.title), iconFor(b.action, phase.direction), phase.progress, armed = false)
                }
                is ToolbarSwipe.Phase.Armed -> {
                    if (!wasArmed) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    wasArmed = true
                    val b = binding(phase.direction) ?: return
                    swipeHint.show(phase.direction, shortTitle(b.action.title), iconFor(b.action, phase.direction), 1f, armed = true)
                }
            }
        }

        private fun finish() {
            val s = swipe ?: return
            val dir = s.up()
            val action = dir?.let { gestures?.actionFor(GestureZone.TOOLBAR, GestureType.swipe(it)) }
            if (action != null) {
                performHapticFeedback(
                    if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                    else android.view.HapticFeedbackConstants.LONG_PRESS,
                )
                swipeHint.fire()
                onAction(action)
            } else {
                swipeHint.cancel()
            }
            swipe = null
        }

        private fun abandon() {
            swipe?.cancel()
            swipe = null
            swipeHint.cancel()
        }

        override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> begin(e)
                android.view.MotionEvent.ACTION_POINTER_DOWN -> abandon() // a second finger: not a swipe
                android.view.MotionEvent.ACTION_MOVE -> {
                    val s = swipe ?: return false
                    track(s.move(e.x, e.y))
                    if (s.claimed) {
                        // Ours now: the button under the finger gets a cancel, not a click.
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // Never claimed: it was a tap or a small wobble on a button.
                    swipe = null
                }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility") // handles only the swipe; taps go to the buttons
        override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
            val s = swipe ?: return false
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_MOVE -> track(s.move(e.x, e.y))
                android.view.MotionEvent.ACTION_UP -> finish()
                android.view.MotionEvent.ACTION_CANCEL -> abandon()
            }
            return true
        }
    }

    /** A short name for the pill ("Open emoji panel" → "Emoji panel"). */
    private fun shortTitle(title: String): String = title.removePrefix("Open ").replaceFirstChar { it.uppercase() }

    private fun iconFor(action: com.yeamin.jolpai.core.gesture.GestureAction, dir: SwipeDirection): KeyboardIcon = when (action) {
        com.yeamin.jolpai.core.gesture.GestureAction.UNDO -> KeyboardIcon.UNDO
        com.yeamin.jolpai.core.gesture.GestureAction.REDO -> KeyboardIcon.REDO
        else -> when (dir) {
            SwipeDirection.LEFT -> KeyboardIcon.ARROW_BACK
            SwipeDirection.RIGHT -> KeyboardIcon.ARROW_FORWARD
            else -> KeyboardIcon.ARROW_DOWN
        }
    }

    private val dividerWidth = density.toInt().coerceAtLeast(1)

    private fun divider() = View(context).apply { setBackgroundColor(ColorUtils.setAlphaComponent(palette.hint, 0x55)) }

    /** Ripple that stays inside a rounded rectangle; built in code (the IME theme has none). */
    private fun pressRipple(fill: Int? = null) = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.keyPressed, 0xCC)),
        fill?.let { GradientDrawable().apply { cornerRadius = 18 * density; setColor(it) } },
        GradientDrawable().apply { cornerRadius = 18 * density; setColor(0xFFFFFFFF.toInt()) },
    )

    /** A tool button: the icon in a pill that fills in while its panel is open. */
    private fun toolbarButton(icon: KeyboardIcon, description: String, active: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): View {
        val color = if (active) palette.accent else palette.text
        val pill = android.widget.ImageView(context).apply {
            setImageDrawable(icon.drawable(context, color, 22 * density))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = description
            background = pressRipple(if (active) ColorUtils.setAlphaComponent(palette.accent, 0x2E) else null)
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
        return FrameLayout(context).apply {
            addView(pill, LayoutParams((52 * density).toInt(), (36 * density).toInt(), Gravity.CENTER))
        }
    }

    /** A plain icon button (round ripple), for the lead button and mode controls. */
    private fun iconView(icon: KeyboardIcon, description: String, sizeDp: Float = 22f) = android.widget.ImageView(context).apply {
        setImageDrawable(icon.drawable(context, palette.text, sizeDp * density))
        scaleType = android.widget.ImageView.ScaleType.CENTER
        contentDescription = description
        background = RippleDrawable(ColorStateList.valueOf(palette.keyPressed), null, null).apply { radius = (20 * density).toInt() }
    }

    private fun toolbarText(text: String, sizeSp: Float) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        background = pressRipple()
    }
}

/** About how wide the wide voice pill is, to centre it when it turns wide. */
private const val WIDE_PILL_DP = 360f
