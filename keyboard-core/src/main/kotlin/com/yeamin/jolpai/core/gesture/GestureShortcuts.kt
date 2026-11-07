package com.yeamin.jolpai.core.gesture

import com.yeamin.jolpai.core.model.EditCommand
import com.yeamin.jolpai.core.model.HostCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.Panel
import java.util.Base64
import kotlin.math.abs

/**
 * Custom gesture shortcuts: a gesture (swipe, long-press, double-tap) on a key or the toolbar
 * runs an action from the [GestureAction] registry.
 *
 * - [GestureBinding]s and thresholds live in a [GestureConfig], stored in the settings
 *   (DataStore) through [GestureConfig.encode]/[GestureConfig.decode].
 * - [GestureConflicts] says which gesture/zone pairs the keyboard already uses (reserved) and
 *   which replace a built-in behaviour (warnings).
 * - [GestureDispatcher] answers "what does this gesture on this zone do?" for the key view.
 * - [GestureAction.toKeyActions] turns an action into existing keyboard actions, so nothing is
 *   implemented twice.
 * - [SwipeClassifier] and [DoubleTapTracker] are the recognition rules, shared by the keyboard
 *   and the settings screen's test pad.
 *
 * Pure Kotlin; unit-tested on the PC.
 */
enum class GestureZone(val id: String, val title: String) {
    SPACE("space", "Space bar"),
    COMMA("comma", "Comma key"),
    PERIOD("period", "Full stop key"),
    SHIFT("shift", "Shift"),
    BACKSPACE("backspace", "Backspace"),
    TOOLBAR("toolbar", "Top bar"),
    ;

    companion object {
        fun fromId(id: String): GestureZone? = entries.firstOrNull { it.id == id }
    }
}

enum class GestureType(val id: String, val title: String) {
    SWIPE_UP("up", "Swipe up"),
    SWIPE_DOWN("down", "Swipe down"),
    SWIPE_LEFT("left", "Swipe left"),
    SWIPE_RIGHT("right", "Swipe right"),
    LONG_PRESS("hold", "Long-press"),
    DOUBLE_TAP("double", "Double-tap"),
    ;

    val isSwipe: Boolean get() = this == SWIPE_UP || this == SWIPE_DOWN || this == SWIPE_LEFT || this == SWIPE_RIGHT

    companion object {
        fun fromId(id: String): GestureType? = entries.firstOrNull { it.id == id }

        fun swipe(direction: SwipeDirection): GestureType = when (direction) {
            SwipeDirection.UP -> SWIPE_UP
            SwipeDirection.DOWN -> SWIPE_DOWN
            SwipeDirection.LEFT -> SWIPE_LEFT
            SwipeDirection.RIGHT -> SWIPE_RIGHT
        }
    }
}

/**
 * The action registry. [available] false means Jolpai doesn't have the feature yet: such actions
 * are listed (so the choice is visible) but can't be assigned, and never run.
 */
enum class GestureAction(val id: String, val title: String, val description: String, val available: Boolean = true) {
    EMOJI("emoji", "Open emoji panel", "Emoji, kaomoji and search"),
    CLIPBOARD("clipboard", "Open clipboard", "Clipboard history and snippets"),
    GIF("gif", "Open GIF panel", "GIF search (needs a GIPHY key)"),
    STICKERS("stickers", "Open stickers", "Your own stickers"),
    VOICE("voice", "Voice typing", "Dictate in the top bar"),
    TRANSLATE("translate", "Open translation", "Not available yet: Jolpai has no translator", available = false),
    AI_WRITING("ai_writing", "AI writing assistant", "Not available yet: Jolpai has no AI assistant", available = false),
    SETTINGS("settings", "Open keyboard settings", "The Jolpai app"),
    SYMBOLS("symbols", "Open symbols", "The ?123 page"),
    NEXT_LANGUAGE("next_language", "Next language", "Switch to the next layout"),
    PREVIOUS_LANGUAGE("previous_language", "Previous language", "Switch to the previous layout"),
    TOGGLE_NUMBER_ROW("number_row", "Toggle number row", "Show or hide the digits row"),
    TOGGLE_ONE_HANDED("one_handed", "Toggle one-handed mode", "Keys to one side, or back"),
    TOGGLE_FLOATING("floating", "Toggle floating keyboard", "Float the keyboard, or dock it"),
    INSERT_PERIOD("period", "Insert full stop", "\".\" (or \"।\" on Bangla layouts)"),
    INSERT_COMMA("comma", "Insert comma", "\",\""),
    INSERT_TEXT("text", "Insert custom text", "Text you choose, e.g. @gmail.com"),
    SELECT_ALL("select_all", "Select all", "Select the whole field"),
    COPY("copy", "Copy", "Copy the selection"),
    CUT("cut", "Cut", "Cut the selection"),
    PASTE("paste", "Paste", "Paste the clipboard"),
    UNDO("undo", "Undo", "Where the app supports it"),
    REDO("redo", "Redo", "Where the app supports it"),
    ;

    /**
     * The keyboard actions that perform this one. Host commands (settings, mode toggles) are
     * carried out by the keyboard service; everything else by the controller.
     */
    fun toKeyActions(text: String?, layout: LayoutId): List<KeyAction> = when (this) {
        EMOJI -> listOf(KeyAction.ShowPanel(Panel.EMOJI))
        CLIPBOARD -> listOf(KeyAction.ShowPanel(Panel.CLIPBOARD))
        GIF -> listOf(KeyAction.OpenEmojiTab(EmojiTab.GIFS))
        STICKERS -> listOf(KeyAction.OpenEmojiTab(EmojiTab.STICKERS))
        VOICE -> listOf(KeyAction.Host(HostCommand.START_VOICE)) // dictate in the top bar
        TRANSLATE, AI_WRITING -> emptyList()
        SETTINGS -> listOf(KeyAction.Host(HostCommand.OPEN_SETTINGS))
        SYMBOLS -> listOf(KeyAction.SwitchPage(KeyboardPage.SYMBOLS))
        NEXT_LANGUAGE -> listOf(KeyAction.NextLayout)
        PREVIOUS_LANGUAGE -> listOf(KeyAction.PreviousLayout)
        TOGGLE_NUMBER_ROW -> listOf(KeyAction.Host(HostCommand.TOGGLE_NUMBER_ROW))
        TOGGLE_ONE_HANDED -> listOf(KeyAction.Host(HostCommand.TOGGLE_ONE_HANDED))
        TOGGLE_FLOATING -> listOf(KeyAction.Host(HostCommand.TOGGLE_FLOATING))
        INSERT_PERIOD -> listOf(KeyAction.PasteText(if (layout == LayoutId.ENGLISH) "." else "।"))
        INSERT_COMMA -> listOf(KeyAction.PasteText(","))
        INSERT_TEXT -> if (text.isNullOrEmpty()) emptyList() else listOf(KeyAction.PasteText(text))
        SELECT_ALL -> listOf(KeyAction.Edit(EditCommand.SELECT_ALL))
        COPY -> listOf(KeyAction.Edit(EditCommand.COPY))
        CUT -> listOf(KeyAction.Edit(EditCommand.CUT))
        PASTE -> listOf(KeyAction.Edit(EditCommand.PASTE))
        UNDO -> listOf(KeyAction.Edit(EditCommand.UNDO))
        REDO -> listOf(KeyAction.Edit(EditCommand.REDO))
    }

    companion object {
        fun fromId(id: String): GestureAction? = entries.firstOrNull { it.id == id }
    }
}

/** Which emoji-panel tab a shortcut opens. */
enum class EmojiTab { GIFS, STICKERS }

data class GestureBinding(
    val zone: GestureZone,
    val type: GestureType,
    val action: GestureAction,
    val enabled: Boolean = true,
    /** For [GestureAction.INSERT_TEXT]. */
    val text: String = "",
) {
    val key: String get() = "${zone.id}:${type.id}"
}

/**
 * All gesture settings. [sensitivity] 1–5 sets how straight a swipe must be (5 accepts the
 * most diagonal swipes); [minSwipeDp] how far it must go; [longPressMs] how long a hold is.
 */
data class GestureConfig(
    val enabled: Boolean = true,
    val bindings: List<GestureBinding> = DEFAULT_BINDINGS,
    val sensitivity: Int = 3,
    val minSwipeDp: Int = 36,
    val longPressMs: Int = 450,
) {
    fun binding(zone: GestureZone, type: GestureType): GestureBinding? = bindings.firstOrNull { it.zone == zone && it.type == type }

    /** The binding that should run now: system on, binding on, action available, not reserved. */
    fun active(zone: GestureZone, type: GestureType, builtIns: BuiltIns): GestureBinding? {
        if (!enabled) return null
        val b = binding(zone, type) ?: return null
        if (!b.enabled || !b.action.available || GestureConflicts.reserved(zone, type, builtIns) != null) return null
        if (b.action == GestureAction.INSERT_TEXT && b.text.isEmpty()) return null
        return b
    }

    fun activeCount(builtIns: BuiltIns): Int = bindings.count { active(it.zone, it.type, builtIns) != null }

    /** Replaces or adds [binding] (one binding per zone and gesture). */
    fun with(binding: GestureBinding): GestureConfig =
        copy(bindings = bindings.filterNot { it.zone == binding.zone && it.type == binding.type } + binding)

    fun without(zone: GestureZone, type: GestureType): GestureConfig =
        copy(bindings = bindings.filterNot { it.zone == zone && it.type == type })

    /** Stored as text: a version line, the settings, then one line per binding. */
    fun encode(): String = buildString {
        append("v$VERSION\n")
        append("enabled=").append(enabled).append('\n')
        append("sensitivity=").append(sensitivity).append('\n')
        append("minSwipeDp=").append(minSwipeDp).append('\n')
        append("longPressMs=").append(longPressMs).append('\n')
        bindings.forEach { b ->
            append("b=").append(b.zone.id).append(',').append(b.type.id).append(',').append(b.action.id).append(',')
                .append(b.enabled).append(',').append(Base64.getEncoder().encodeToString(b.text.toByteArray())).append('\n')
        }
    }

    companion object {
        const val VERSION = 1

        val SENSITIVITY_RANGE = 1..5
        val MIN_SWIPE_RANGE = 20..80
        val LONG_PRESS_RANGE = 250..900

        /**
         * The starting set. Space-bar language swipes (from the brief) are included but off: the
         * user chose Gboard-style cursor swipes on space, and those would replace them.
         * Comma/full stop long-presses are off too, as they would replace the punctuation menus.
         */
        val DEFAULT_BINDINGS: List<GestureBinding> = listOf(
            GestureBinding(GestureZone.SPACE, GestureType.SWIPE_UP, GestureAction.EMOJI),
            GestureBinding(GestureZone.SPACE, GestureType.SWIPE_DOWN, GestureAction.CLIPBOARD),
            GestureBinding(GestureZone.SPACE, GestureType.SWIPE_LEFT, GestureAction.PREVIOUS_LANGUAGE, enabled = false),
            GestureBinding(GestureZone.SPACE, GestureType.SWIPE_RIGHT, GestureAction.NEXT_LANGUAGE, enabled = false),
            GestureBinding(GestureZone.COMMA, GestureType.LONG_PRESS, GestureAction.SETTINGS, enabled = false),
            GestureBinding(GestureZone.PERIOD, GestureType.LONG_PRESS, GestureAction.SYMBOLS, enabled = false),
            GestureBinding(GestureZone.TOOLBAR, GestureType.SWIPE_LEFT, GestureAction.TRANSLATE),
            GestureBinding(GestureZone.TOOLBAR, GestureType.SWIPE_RIGHT, GestureAction.AI_WRITING),
            GestureBinding(GestureZone.COMMA, GestureType.SWIPE_UP, GestureAction.INSERT_TEXT, enabled = false, text = "@gmail.com"),
            GestureBinding(GestureZone.COMMA, GestureType.SWIPE_DOWN, GestureAction.INSERT_TEXT, enabled = false, text = "https://"),
        )

        /**
         * Reads a stored configuration. Unknown zones, gestures or actions (from a newer or older
         * version) are skipped, numbers are clamped, and missing or broken text gives the defaults.
         */
        fun decode(text: String?): GestureConfig {
            if (text.isNullOrBlank() || !text.startsWith("v")) return GestureConfig()
            var config = GestureConfig(bindings = emptyList())
            val bindings = ArrayList<GestureBinding>()
            text.lineSequence().drop(1).forEach { line ->
                val eq = line.indexOf('=')
                if (eq < 0) return@forEach
                val value = line.substring(eq + 1)
                when (line.substring(0, eq)) {
                    "enabled" -> config = config.copy(enabled = value.toBooleanStrictOrNull() ?: true)
                    "sensitivity" -> config = config.copy(sensitivity = (value.toIntOrNull() ?: 3).coerceIn(SENSITIVITY_RANGE))
                    "minSwipeDp" -> config = config.copy(minSwipeDp = (value.toIntOrNull() ?: 36).coerceIn(MIN_SWIPE_RANGE))
                    "longPressMs" -> config = config.copy(longPressMs = (value.toIntOrNull() ?: 450).coerceIn(LONG_PRESS_RANGE))
                    "b" -> parseBinding(value)?.let { b ->
                        if (bindings.none { it.zone == b.zone && it.type == b.type }) bindings += b
                    }
                }
            }
            return config.copy(bindings = bindings)
        }

        private fun parseBinding(value: String): GestureBinding? {
            val f = value.split(',')
            if (f.size < 4) return null
            val zone = GestureZone.fromId(f[0]) ?: return null
            val type = GestureType.fromId(f[1]) ?: return null
            if (type !in GestureConflicts.allowedTypes(zone)) return null
            val action = GestureAction.fromId(f[2]) ?: return null
            val text = f.getOrNull(4)?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }.orEmpty()
            return GestureBinding(zone, type, action, f[3].toBooleanStrictOrNull() ?: true, text)
        }
    }
}

/** The keyboard's own behaviours that gestures must not take over. */
data class BuiltIns(val swipeGestures: Boolean = true, val doubleSpacePeriod: Boolean = true)

/** What the keyboard already does with a gesture, and what an assignment would replace. */
object GestureConflicts {

    /** Gestures that make sense on each zone (the toolbar sits above the app: no swipe up). */
    fun allowedTypes(zone: GestureZone): List<GestureType> = when (zone) {
        GestureZone.TOOLBAR -> listOf(GestureType.SWIPE_LEFT, GestureType.SWIPE_RIGHT, GestureType.SWIPE_DOWN)
        else -> GestureType.entries
    }

    /** Why this gesture can't be assigned (a built-in behaviour owns it), or null if it can. */
    fun reserved(zone: GestureZone, type: GestureType, builtIns: BuiltIns): String? = when {
        type !in allowedTypes(zone) -> "Not available on the ${zone.title.lowercase()}"
        zone == GestureZone.SPACE && type == GestureType.LONG_PRESS -> "Holding space is the cursor trackpad"
        zone == GestureZone.SPACE && type == GestureType.DOUBLE_TAP && builtIns.doubleSpacePeriod ->
            "Double-space types a full stop (Typing settings)"
        zone == GestureZone.SHIFT && type == GestureType.DOUBLE_TAP -> "Double-tapping Shift is caps lock"
        zone == GestureZone.BACKSPACE && type == GestureType.LONG_PRESS -> "Holding backspace keeps deleting"
        zone == GestureZone.BACKSPACE && type == GestureType.DOUBLE_TAP -> "Two quick taps on backspace delete twice"
        zone == GestureZone.BACKSPACE && type == GestureType.SWIPE_LEFT && builtIns.swipeGestures ->
            "Swiping left from backspace selects words (Swipe gestures)"
        else -> null
    }

    /** A note when an assignment replaces something built in (allowed, but worth knowing). */
    fun replaces(zone: GestureZone, type: GestureType, builtIns: BuiltIns): String? = when {
        zone == GestureZone.SPACE && (type == GestureType.SWIPE_LEFT || type == GestureType.SWIPE_RIGHT) && builtIns.swipeGestures ->
            "Replaces moving the cursor in that direction"
        zone == GestureZone.COMMA && type == GestureType.LONG_PRESS -> "Replaces the comma's punctuation menu"
        zone == GestureZone.PERIOD && type == GestureType.LONG_PRESS -> "Replaces the full stop's punctuation menu"
        zone == GestureZone.TOOLBAR -> "Works while the tools are showing (not over scrolling suggestions)"
        type == GestureType.DOUBLE_TAP -> "A single tap on this key waits a moment to see if a second tap follows"
        else -> null
    }

    /** Other enabled bindings that run the same action (allowed; shown as a heads-up). */
    fun sameAction(config: GestureConfig, binding: GestureBinding): List<GestureBinding> =
        config.bindings.filter {
            it.enabled && it.action == binding.action && it.key != binding.key &&
                (binding.action != GestureAction.INSERT_TEXT || it.text == binding.text)
        }
}

/** What the keyboard view asks while a finger is down. Rebuilt whenever settings change. */
class GestureDispatcher(private val config: GestureConfig, private val builtIns: BuiltIns) {
    val thresholds: GestureThresholds = GestureThresholds.of(config)

    fun bindingFor(zone: GestureZone, type: GestureType): GestureBinding? = config.active(zone, type, builtIns)

    fun hasSwipe(zone: GestureZone): Boolean = GestureType.entries.any { it.isSwipe && bindingFor(zone, it) != null }

    fun hasVerticalSwipe(zone: GestureZone): Boolean =
        bindingFor(zone, GestureType.SWIPE_UP) != null || bindingFor(zone, GestureType.SWIPE_DOWN) != null

    /** The action to emit for a recognised gesture, or null if nothing is assigned. */
    fun actionFor(zone: GestureZone, type: GestureType): KeyAction.Shortcut? =
        bindingFor(zone, type)?.let { KeyAction.Shortcut(it.action, it.text) }
}

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/** Recognition thresholds in dp/ms; the view converts dp to pixels. */
data class GestureThresholds(val minSwipeDp: Float, val dominance: Float, val longPressMs: Long, val doubleTapMs: Long = 280) {
    companion object {
        /** Sensitivity 1 (strict) … 5 (relaxed): how much straighter than diagonal a swipe must be. */
        fun of(config: GestureConfig) = GestureThresholds(
            minSwipeDp = config.minSwipeDp.toFloat(),
            dominance = when (config.sensitivity.coerceIn(GestureConfig.SENSITIVITY_RANGE)) {
                1 -> 2.6f
                2 -> 2.1f
                3 -> 1.7f
                4 -> 1.4f
                else -> 1.2f
            },
            longPressMs = config.longPressMs.toLong(),
        )
    }
}

/** Swipe direction from the finger's travel; null until it is long and straight enough. */
object SwipeClassifier {
    fun classify(dx: Float, dy: Float, minDistance: Float, dominance: Float): SwipeDirection? {
        val ax = abs(dx)
        val ay = abs(dy)
        return when {
            ax >= minDistance && ax >= ay * dominance -> if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            ay >= minDistance && ay >= ax * dominance -> if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP
            else -> null
        }
    }
}

/** Two taps on the same key within [windowMs] make a double-tap. */
class DoubleTapTracker(private val windowMs: Long) {
    private var lastKey: Any? = null
    private var lastTime = Long.MIN_VALUE

    /** Records a tap on [key] at [time]; true when it completes a double-tap (then resets). */
    fun onTap(key: Any, time: Long): Boolean {
        val isDouble = key == lastKey && lastTime != Long.MIN_VALUE && time - lastTime <= windowMs
        if (isDouble) {
            lastKey = null
            lastTime = Long.MIN_VALUE
        } else {
            lastKey = key
            lastTime = time
        }
        return isDouble
    }

    fun reset() {
        lastKey = null
        lastTime = Long.MIN_VALUE
    }
}
