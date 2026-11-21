package com.yeamin.jolpai.core.settings

import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.ToolbarItem

/** The keyboard someone used before Jolpai; the welcome flow starts from what they already know. */
enum class PreviousKeyboard {
    /** Ridmik Keyboard: starts on the Classic style. */
    RIDMIK,

    /** Gboard: the Gboard-like style, autocorrect on. */
    GBOARD,

    /** First Bangla keyboard. */
    NONE,
}

/**
 * Two ways the keyboard can behave, chosen in the welcome flow and in Toolbar & panels.
 *
 * [CLASSIC] is the Ridmik way: English ⇄ one Bangla layout, a short toolbar (emoji, clipboard,
 * voice), and emoji/clipboard open in place of the keys. [MODERN] is the developer's own
 * setup (read from their phone, 2026-11-21): every tool in the toolbar (Translate, Check,
 * editing), panels opening above the keys so the keys stay, English ⇄ জাতীয়.
 */
enum class KeyboardStyle(
    val storageKey: String,
    val label: String,
    val banglaLabel: String,
    /** Which keyboard it copies, shown as a small tag, or null for Jolpai's own. */
    val like: String?,
    val summary: String,
    val toolbar: List<ToolbarItem>,
    val panelPlacement: PanelPlacement,
    val look: KeyLook,
) {
    CLASSIC(
        "classic", "Classic", "ক্লাসিক", "Ridmik",
        "Like Ridmik: slide the space bar to change language, symbol hints on the letters, emoji and clipboard in place of the keys.",
        listOf(ToolbarItem.EMOJI, ToolbarItem.CLIPBOARD, ToolbarItem.EDIT, ToolbarItem.CHECK, ToolbarItem.VOICE),
        PanelPlacement.KEYS, KeyLook.RIDMIK,
    ),
    GBOARD(
        "gboard", "Simple", "সহজ", "Gboard",
        "Like Gboard: comma before the emoji key, translate and clipboard in the top bar, emoji and clipboard in place of the keys.",
        listOf(ToolbarItem.TRANSLATE, ToolbarItem.CLIPBOARD, ToolbarItem.VOICE),
        PanelPlacement.KEYS, KeyLook.GBOARD,
    ),
    MODERN(
        "modern", "Modern", "মডার্ন", null,
        "Jolpai's own: every tool in the top bar, emoji and clipboard open above so the keys stay.",
        ToolbarItem.DEFAULT, PanelPlacement.ABOVE, KeyLook.JOLPAI,
    ),
    ;

    companion object {
        /** The style these settings match, or null when they were customised. */
        fun matching(toolbar: List<ToolbarItem>, placement: PanelPlacement, look: KeyLook): KeyboardStyle? =
            entries.firstOrNull { it.toolbar == toolbar && it.panelPlacement == placement && it.look == look }

        fun of(settings: KeyboardSettings): KeyboardStyle? = matching(settings.toolbar, settings.panelPlacement, settings.keyLook)
    }
}

/**
 * The welcome flow's answers. [suggestedFor] picks the starting answers from the keyboard used
 * before, so a Ridmik user who only taps Next gets a keyboard that behaves like Ridmik.
 */
data class WelcomeChoices(
    val style: KeyboardStyle,
    /** Bangla layouts to use (English is always on). Empty: English only. */
    val bangla: List<LayoutId>,
    val autoCorrect: Boolean,
    val numberRow: Boolean,
    val keySound: Boolean,
    val vibrate: Boolean,
) {
    /** English first, then the Bangla layouts in the language key's order. */
    val layouts: List<LayoutId>
        get() = listOf(LayoutId.ENGLISH) + LayoutId.entries.filter { it != LayoutId.ENGLISH && it in bangla }

    /** Opens in the first Bangla layout picked: Jolpai is chosen to type Bangla. */
    val startLayout: LayoutId get() = layouts.getOrElse(1) { LayoutId.ENGLISH }

    /** Picks a style and its usual Bangla layouts (Classic: one, like Ridmik; Modern: Jolpai's defaults). */
    fun withStyle(style: KeyboardStyle): WelcomeChoices = copy(style = style, bangla = banglaFor(style))

    companion object {
        /** Classic: Avro, the most used Bangla layout on Ridmik. Modern: জাতীয়, as in the developer's own setup (2026-11-21). */
        private fun banglaFor(style: KeyboardStyle): List<LayoutId> =
            if (style == KeyboardStyle.MODERN) listOf(LayoutId.NATIONAL) else listOf(LayoutId.AVRO)

        /** Starting answers for someone coming from [previous], keeping their sound/vibration/number-row choices. */
        fun suggestedFor(previous: PreviousKeyboard, current: KeyboardSettings): WelcomeChoices {
            val style = when (previous) {
                PreviousKeyboard.RIDMIK -> KeyboardStyle.CLASSIC
                PreviousKeyboard.GBOARD -> KeyboardStyle.GBOARD
                PreviousKeyboard.NONE -> KeyboardStyle.MODERN
            }
            return WelcomeChoices(
                style = style,
                bangla = banglaFor(style),
                autoCorrect = previous == PreviousKeyboard.GBOARD,
                numberRow = current.numberRow,
                keySound = current.keySound,
                vibrate = current.haptics != HapticLevel.OFF,
            )
        }
    }
}
