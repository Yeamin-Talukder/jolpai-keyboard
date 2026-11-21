package com.yeamin.jolpai.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeamin.jolpai.clipboard.PanelColors
import com.yeamin.jolpai.core.model.DateTimeKind
import com.yeamin.jolpai.core.model.EditCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.tools.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Text editing tools in place of the keys (redesigned 2026-11-21, user request):
 *
 * - Left, a cursor pad like a game pad: ▲ ◀ ▶ ▼ around a big Select toggle (while it is on, the
 *   arrows extend the selection and the pad is outlined in the accent), line start / end in the
 *   top corners, word left / right in the lower corners, and Undo · Delete word · Redo below.
 * - Right, the clipboard column in a tinted colour: Select all, Copy, Cut, Paste.
 * - Below: Insert (date, time, Bangla date) and Style (type or restyle in bold, italic, script…).
 *
 * Every tool has a Material Symbols icon (no text arrows or emoji), squashes a little and ticks
 * when pressed; arrows, word moves and Delete word repeat while held, speeding up after a moment.
 */
@Composable
fun EditingPanel(selecting: Boolean, textStyle: TextStyle?, colors: PanelColors, onAction: (KeyAction) -> Unit) {
    fun edit(c: EditCommand) = onAction(KeyAction.Edit(c))
    val padOutline by animateColorAsState(if (selecting) colors.accent else Color.Transparent, tween(180), label = "selecting")
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // ---- cursor pad
            Column(
                Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, padOutline, RoundedCornerShape(16.dp))
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // A D-pad: the four arrows are solid keys, so the cross stands out; the corner keys
                // (line start / end, word left / right) are quiet outlined ones.
                PadRow {
                    EditKey(R.drawable.ms_keyboard_tab_rtl, "Home", "Line start", colors, kind = KeyKind.QUIET, repeat = true) { edit(EditCommand.LINE_START) }
                    EditKey(R.drawable.ms_keyboard_arrow_up, null, "Up", colors, repeat = true, iconSize = 26) { edit(EditCommand.UP) }
                    EditKey(R.drawable.ms_keyboard_tab, "End", "Line end", colors, kind = KeyKind.QUIET, repeat = true, labelFirst = true) { edit(EditCommand.LINE_END) }
                }
                PadRow {
                    EditKey(R.drawable.ms_keyboard_arrow_left, null, "Left", colors, repeat = true, iconSize = 26) { edit(EditCommand.LEFT) }
                    // Select: a pill in the middle of the pad; while on, the arrows select.
                    EditKey(
                        if (selecting) R.drawable.ms_check else null,
                        if (selecting) "Selecting" else "Select",
                        if (selecting) "Stop selecting" else "Select with the arrows",
                        colors,
                        kind = if (selecting) KeyKind.ACTIVE else KeyKind.PLAIN,
                        pill = true,
                        alwaysLabel = true,
                    ) { edit(EditCommand.TOGGLE_SELECT) }
                    EditKey(R.drawable.ms_keyboard_arrow_right, null, "Right", colors, repeat = true, iconSize = 26) { edit(EditCommand.RIGHT) }
                }
                PadRow {
                    EditKey(R.drawable.ms_keyboard_double_arrow_left, "Word", "Word left", colors, kind = KeyKind.QUIET, repeat = true) { edit(EditCommand.SELECT_WORD_LEFT) }
                    EditKey(R.drawable.ms_keyboard_arrow_down, null, "Down", colors, repeat = true, iconSize = 26) { edit(EditCommand.DOWN) }
                    EditKey(R.drawable.ms_keyboard_double_arrow_right, "Word", "Word right", colors, kind = KeyKind.QUIET, repeat = true, labelFirst = true) { edit(EditCommand.SELECT_WORD_RIGHT) }
                }
                PadRow {
                    EditKey(R.drawable.ms_undo, "Undo", "Undo", colors) { edit(EditCommand.UNDO) }
                    EditKey(R.drawable.ms_backspace, "Word", "Delete word", colors, repeat = true) { edit(EditCommand.DELETE_WORD) }
                    EditKey(R.drawable.ms_redo, "Redo", "Redo", colors, labelFirst = true) { edit(EditCommand.REDO) }
                }
            }
            // ---- clipboard column: Select all; Copy and Cut side by side (a ✓ flashes when
            // done); Paste as the main, accent-filled key with a peek at what it will paste.
            val clip = rememberClipPreview()
            var done by remember { mutableStateOf<EditCommand?>(null) }
            LaunchedEffect(done) {
                if (done != null) {
                    delay(900)
                    done = null
                }
            }
            Column(Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    EditKey(R.drawable.ms_select_all, "Select all", "Select all", colors, kind = KeyKind.TONAL, alwaysLabel = true) { edit(EditCommand.SELECT_ALL) }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val copied = done == EditCommand.COPY
                    val cut = done == EditCommand.CUT
                    EditKey(
                        if (copied) R.drawable.ms_check else R.drawable.ms_content_copy, if (copied) "Done" else "Copy", "Copy",
                        colors, kind = if (copied) KeyKind.ACTIVE else KeyKind.TONAL,
                    ) { edit(EditCommand.COPY); done = EditCommand.COPY }
                    EditKey(
                        if (cut) R.drawable.ms_check else R.drawable.ms_content_cut, if (cut) "Done" else "Cut", "Cut",
                        colors, kind = if (cut) KeyKind.ACTIVE else KeyKind.TONAL,
                    ) { edit(EditCommand.CUT); done = EditCommand.CUT }
                }
                Row(Modifier.fillMaxWidth().weight(2f)) {
                    EditKey(
                        R.drawable.ms_content_paste, "Paste",
                        "Paste" + (clip?.let { ": $it" } ?: ""),
                        colors, kind = KeyKind.ACTIVE, alwaysLabel = true, iconSize = 22,
                        subtitle = clip ?: "Clipboard is empty",
                    ) { edit(EditCommand.PASTE) }
                }
            }
        }
        // ---- insert
        ChipRow("Insert", colors) {
            Chip(R.drawable.ms_calendar_today, "Date", false, colors) { onAction(KeyAction.InsertDateTime(DateTimeKind.DATE)) }
            Chip(R.drawable.ms_schedule, "Time", false, colors) { onAction(KeyAction.InsertDateTime(DateTimeKind.TIME)) }
            Chip(R.drawable.ms_calendar_today, "বাংলা তারিখ", false, colors) { onAction(KeyAction.InsertDateTime(DateTimeKind.BANGLA_DATE)) }
        }
        // ---- style: restyle the selection, or with nothing selected, type in that style
        ChipRow("Style", colors) {
            Chip(null, "Aa Normal", textStyle == null, colors) { onAction(KeyAction.SetTextStyle(null)) }
            TextStyle.entries.forEach { s ->
                Chip(null, s.sample, textStyle == s, colors) { onAction(KeyAction.SetTextStyle(s)) }
            }
        }
    }
}

/** PLAIN: a key; TONAL: accent-tinted (clipboard); ACTIVE: accent-filled (Select on); QUIET: outlined only. */
private enum class KeyKind { PLAIN, TONAL, ACTIVE, QUIET }

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PadRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    // Each pad row shares the height equally; keys share the width.
    Row(
        Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A one-line peek at the clipboard for the Paste key, kept up to date while the panel is open:
 * the text (spaces squeezed, first 60 characters), "Image" for a picture, dots for anything the
 * copying app marked sensitive (passwords; Android 13+), or null when it's empty. Read only on
 * the phone, like the clipboard history; the keyboard may read the clipboard while it's in use.
 */
@Composable
private fun rememberClipPreview(): String? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember { context.getSystemService(android.content.ClipboardManager::class.java) }
    fun read(): String? = runCatching {
        val clip = manager?.primaryClip ?: return@runCatching null
        if (clip.itemCount == 0) return@runCatching null
        val description = clip.description
        val sensitive = android.os.Build.VERSION.SDK_INT >= 33 &&
            description.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE) == true
        when {
            sensitive -> "••••••••"
            description.hasMimeType("image/*") -> "Image"
            else -> clip.getItemAt(0).coerceToText(context)?.toString()
                ?.replace(Regex("\\s+"), " ")?.trim()?.take(60)?.ifEmpty { null }
        }
    }.getOrNull()
    var preview by remember { mutableStateOf(read()) }
    DisposableEffect(manager) {
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener { preview = read() }
        manager?.addPrimaryClipChangedListener(listener)
        onDispose { manager?.removePrimaryClipChangedListener(listener) }
    }
    return preview
}

/**
 * One editing key: an icon with its label beside it ([labelFirst] puts the label before the
 * icon, for keys on the right). The label shows only when the key is wide enough, so it is never
 * cut off on a short or narrow keyboard ([alwaysLabel] keys always show it). [pill] rounds it
 * fully. [repeat] keys repeat while held (after 400 ms, every 60 ms, then every 30 ms after a
 * second of holding).
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.EditKey(
    @DrawableRes icon: Int?,
    label: String?,
    description: String,
    colors: PanelColors,
    kind: KeyKind = KeyKind.PLAIN,
    repeat: Boolean = false,
    iconSize: Int = 20,
    labelFirst: Boolean = false,
    pill: Boolean = false,
    alwaysLabel: Boolean = false,
    /** A second, smaller line under the label (Paste's clipboard peek), when the key is tall enough. */
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val base = when (kind) {
        KeyKind.PLAIN -> colors.surface
        KeyKind.TONAL -> colors.accent.copy(alpha = 0.18f).compositeOver(colors.surface.compositeOver(colors.background))
        KeyKind.ACTIVE -> colors.accent
        KeyKind.QUIET -> Color.Transparent
    }
    val content = if (kind == KeyKind.ACTIVE) colors.onAccent else colors.text
    // Quiet keys fill in with the key colour while pressed; the others darken a little.
    val pressedBg = if (kind == KeyKind.QUIET) colors.surface else lerp(base, content, 0.16f)
    val bg by animateColorAsState(if (pressed) pressedBg else base, tween(if (pressed) 40 else 160), label = "bg")
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "scale")
    val shape = RoundedCornerShape(if (pill) 50 else 30) // percent: soft, rounded keys at any size
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .then(if (kind == KeyKind.QUIET) Modifier.border(1.dp, content.copy(alpha = 0.22f), shape) else Modifier)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (kind == KeyKind.ACTIVE) selected = true
            }
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    click()
                    val repeater = if (repeat) {
                        scope.launch {
                            delay(400)
                            var n = 0
                            while (true) {
                                click()
                                n++
                                delay(if (n < 16) 60 else 30) // speeds up after about a second
                            }
                        }
                    } else {
                        null
                    }
                    // However the touch ends (lifted, cancelled, the panel closing), the repeat stops.
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        repeater?.cancel()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Icon and label side by side; the label only where it fits whole.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Room needed: icon, gap, the label (~7.5 dp a letter at 13 sp, more with a larger
            // phone font) and a little side padding.
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val needed = (if (icon != null) iconSize + 6 else 0) + (label?.length ?: 0) * 7.5f * fontScale + 12
            val showLabel = label != null && (alwaysLabel || icon == null || maxWidth.value >= needed)
            val showSubtitle = subtitle != null && maxHeight >= 52.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconView: @Composable () -> Unit = {
                    if (icon != null) Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(iconSize.dp))
                }
                val labelView: @Composable () -> Unit = {
                    if (showLabel) Text(label!!, color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                val gap = if (icon != null && showLabel) 6.dp else 0.dp
                if (labelFirst) {
                    labelView(); Spacer(Modifier.width(gap)); iconView()
                } else {
                    iconView(); Spacer(Modifier.width(gap)); labelView()
                }
            }
            if (showSubtitle) {
                Text(
                    subtitle!!,
                    color = content.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp).padding(top = 2.dp),
                )
            }
            }
        }
    }
}

/** A labelled, sideways-scrolling row of chips ("Insert", "Style"). */
@Composable
private fun ChipRow(title: String, colors: PanelColors, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = colors.hint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(44.dp).padding(start = 4.dp),
            maxLines = 1,
        )
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** A pill chip: an optional icon and a label; [active] fills it with the accent. */
@Composable
private fun Chip(@DrawableRes icon: Int?, label: String, active: Boolean, colors: PanelColors, onClick: () -> Unit) {
    val view = LocalView.current
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val base = if (active) colors.accent else colors.surface
    val content = if (active) colors.onAccent else colors.text
    val bg by animateColorAsState(if (pressed) lerp(base, content, 0.16f) else base, tween(if (pressed) 40 else 160), label = "chip")
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "chipScale")
    Row(
        Modifier
            .height(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(17.dp))
            .background(bg)
            .semantics {
                role = Role.Button
                if (active) selected = true
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    val up = try { waitForUpOrCancellation() } finally { pressed = false }
                    if (up != null) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        click()
                    }
                }
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = content, fontSize = 14.sp, maxLines = 1)
    }
}
