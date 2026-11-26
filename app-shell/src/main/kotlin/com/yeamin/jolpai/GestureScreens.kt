package com.yeamin.jolpai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.gesture.BuiltIns
import com.yeamin.jolpai.core.gesture.DoubleTapTracker
import com.yeamin.jolpai.core.gesture.GestureAction
import com.yeamin.jolpai.core.gesture.GestureBinding
import com.yeamin.jolpai.core.gesture.GestureConfig
import com.yeamin.jolpai.core.gesture.GestureConflicts
import com.yeamin.jolpai.core.gesture.GestureThresholds
import com.yeamin.jolpai.core.gesture.GestureType
import com.yeamin.jolpai.core.gesture.GestureZone
import com.yeamin.jolpai.core.gesture.SwipeClassifier
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.ui.R as UiR

private fun gestureIcon(type: GestureType): Int = when (type) {
    GestureType.SWIPE_UP -> UiR.drawable.ms_swipe_up
    GestureType.SWIPE_DOWN -> UiR.drawable.ms_swipe_down
    GestureType.SWIPE_LEFT -> UiR.drawable.ms_swipe_left
    GestureType.SWIPE_RIGHT -> UiR.drawable.ms_swipe_right
    GestureType.LONG_PRESS -> UiR.drawable.ms_touch_long
    GestureType.DOUBLE_TAP -> UiR.drawable.ms_touch_double
}

private fun actionLabel(b: GestureBinding): String =
    if (b.action == GestureAction.INSERT_TEXT) tr("Insert “${b.text}”", "“${b.text}” বসান") else b.action.uiTitle

/**
 * Why the keyboard keeps this gesture for itself, in the app's language, or null when it is free.
 * Whether it is reserved still comes from [GestureConflicts.reserved]; this only words it.
 */
private fun reservedReason(zone: GestureZone, type: GestureType, builtIns: BuiltIns): String? {
    val english = GestureConflicts.reserved(zone, type, builtIns) ?: return null
    return when {
        type !in GestureConflicts.allowedTypes(zone) ->
            tr("Not available on the ${zone.uiTitle.lowercase()}", "${zone.uiTitle}-এ এটি হয় না")
        zone == GestureZone.SPACE && type == GestureType.LONG_PRESS ->
            tr("Holding space moves the cursor", "স্পেস চেপে ধরলে কার্সর সরে")
        zone == GestureZone.SPACE && type == GestureType.DOUBLE_TAP ->
            tr("Double space types a full stop (Typing settings)", "দুবার স্পেস দিলে দাঁড়ি বসে (টাইপিং সেটিংস)")
        zone == GestureZone.SHIFT && type == GestureType.DOUBLE_TAP ->
            tr("Double-tapping Shift is caps lock", "শিফটে দুবার ট্যাপ করলে ক্যাপস লক হয়")
        zone == GestureZone.BACKSPACE && type == GestureType.LONG_PRESS ->
            tr("Holding backspace keeps deleting", "ব্যাকস্পেস চেপে ধরলে মুছতেই থাকে")
        zone == GestureZone.BACKSPACE && type == GestureType.DOUBLE_TAP ->
            tr("Two quick taps on backspace delete twice", "ব্যাকস্পেসে দুবার ট্যাপ করলে দুবার মোছে")
        zone == GestureZone.BACKSPACE && type == GestureType.SWIPE_LEFT ->
            tr("Swiping left from backspace selects words (Swipe gestures)", "ব্যাকস্পেস থেকে বাঁয়ে সোয়াইপ করলে শব্দ সিলেক্ট হয় (সোয়াইপ জেসচার)")
        else -> english
    }
}

/**
 * A heads-up when the gesture replaces something built in, in the app's language, or null.
 * Whether there is one still comes from [GestureConflicts.replaces]; this only words it.
 */
private fun replacesNote(zone: GestureZone, type: GestureType, builtIns: BuiltIns): String? {
    val english = GestureConflicts.replaces(zone, type, builtIns) ?: return null
    return when {
        zone == GestureZone.SPACE && (type == GestureType.SWIPE_LEFT || type == GestureType.SWIPE_RIGHT) ->
            tr("Replaces moving the cursor that way", "ওই দিকে কার্সর সরানোর বদলে এটি হবে")
        zone == GestureZone.COMMA && type == GestureType.LONG_PRESS ->
            tr("Replaces the comma's symbol menu", "কমার চিহ্ন-মেনুর বদলে এটি হবে")
        zone == GestureZone.PERIOD && type == GestureType.LONG_PRESS ->
            tr("Replaces the full stop's symbol menu", "দাঁড়ির চিহ্ন-মেনুর বদলে এটি হবে")
        zone == GestureZone.TOOLBAR ->
            tr(
                "Slide along the top bar until the pill fills, then let go. Slide back to cancel. Not over scrolling suggestions.",
                "ওপরের বারে আঙুল টানুন, পিলটি ভরে গেলে ছেড়ে দিন। বাতিল করতে উল্টো দিকে টানুন। স্ক্রল হওয়া সাজেশনের ওপর কাজ করে না।",
            )
        type == GestureType.DOUBLE_TAP ->
            tr("One tap on this key now waits a moment for a second tap", "এই কী-তে এক ট্যাপের পর দ্বিতীয় ট্যাপের জন্য একটু অপেক্ষা করবে")
        else -> english
    }
}

/** Custom gestures: master switch, sensitivity, the gesture list and its editor. */
@Composable
fun GestureScreen(vm: MainViewModel, settings: KeyboardSettings, onBack: () -> Unit) {
    val config = settings.gestures
    val builtIns = BuiltIns(settings.cursorSwipe, settings.doubleSpacePeriod)
    var editing by remember { mutableStateOf<GestureBinding?>(null) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    fun save(c: GestureConfig) = vm.setGestures(c)

    SettingsScaffold(
        tr("Custom gestures", "নিজের মতো জেসচার"),
        onBack,
        actions = { IconButton(onClick = { confirmRestore = true }) { MsIcon(UiR.drawable.ms_restart_alt, tr("Restore defaults", "ডিফল্টে ফেরান")) } },
    ) {
        SettingsGroup {
            val active = config.activeCount(builtIns)
            SettingsSwitch(
                tr("Gesture shortcuts", "জেসচার শর্টকাট"),
                tr(
                    "$active active · swipe, hold or double-tap a key to run an action",
                    "${num(active)}টি চালু · কী-তে সোয়াইপ, চেপে ধরা বা দুবার ট্যাপে কাজ চলে",
                ),
                config.enabled,
                { save(config.copy(enabled = it)) },
                UiR.drawable.ms_gesture,
            )
        }

        SettingsGroup(tr("Sensitivity", "সেনসিটিভিটি")) {
            Column(Modifier.padding(GroupContentPadding).alpha(if (config.enabled) 1f else 0.45f)) {
                GestureSlider(
                    tr("How straight to swipe", "সোয়াইপ কতটা সোজা হবে"),
                    config.sensitivity.toFloat(), 1f..5f, 3,
                    listOf(
                        tr("Strict", "কড়া"),
                        tr("Firm", "একটু কড়া"),
                        tr("Normal", "স্বাভাবিক"),
                        tr("Relaxed", "একটু ঢিলা"),
                        tr("Loose", "ঢিলা"),
                    )[config.sensitivity - 1],
                    config.enabled,
                ) { save(config.copy(sensitivity = it.toInt())) }
                GestureSlider(
                    tr("Shortest swipe", "সবচেয়ে ছোট সোয়াইপ"),
                    config.minSwipeDp.toFloat(), 20f..80f, 11, "${num(config.minSwipeDp)} dp", config.enabled,
                ) { save(config.copy(minSwipeDp = it.toInt())) }
                GestureSlider(
                    tr("Hold time", "কতক্ষণ চেপে ধরতে হবে"),
                    config.longPressMs.toFloat(), 250f..900f, 12, "${num(config.longPressMs)} ms", config.enabled,
                ) { save(config.copy(longPressMs = (it / 50).toInt() * 50)) }
            }
        }

        SettingsGroup(tr("Gestures", "জেসচার")) {
            val sorted = config.bindings.sortedWith(compareBy({ it.zone.ordinal }, { it.type.ordinal }))
            sorted.forEach { b ->
                GestureRow(b, config, builtIns, onToggle = { on -> save(config.with(b.copy(enabled = on))) }) { editing = b }
            }
            SettingsItem(tr("Add a gesture", "জেসচার যোগ করুন"), tr("Pick a key, a gesture and an action", "কী, জেসচার আর কাজ বেছে নিন"), UiR.drawable.ms_add, showChevron = false) {
                val free = GestureZone.entries.flatMap { z -> GestureConflicts.allowedTypes(z).map { z to it } }
                    .firstOrNull { (z, t) -> config.binding(z, t) == null && GestureConflicts.reserved(z, t, builtIns) == null }
                editing = GestureBinding(free?.first ?: GestureZone.COMMA, free?.second ?: GestureType.SWIPE_UP, GestureAction.INSERT_TEXT, text = "")
            }
        }
        SettingsNote(
            tr(
                "A few gestures belong to the keyboard, like holding space to move the cursor.",
                "কিছু জেসচার কীবোর্ডের নিজের, যেমন স্পেস চেপে ধরে কার্সর সরানো।",
            ),
        )
    }

    editing?.let { original ->
        GestureEditor(
            initial = original,
            isNew = config.binding(original.zone, original.type) != original,
            config = config,
            builtIns = builtIns,
            onDismiss = { editing = null },
            onDelete = { save(config.without(original.zone, original.type)); editing = null },
        ) { updated ->
            val base = if (updated.zone != original.zone || updated.type != original.type) config.without(original.zone, original.type) else config
            save(base.with(updated))
            editing = null
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { MsIcon(UiR.drawable.ms_restart_alt, null) },
            title = { Text(tr("Restore default gestures?", "ডিফল্ট জেসচারে ফিরবেন?")) },
            text = { Text(tr("Your gestures, texts and sensitivity go back to the defaults.", "আপনার জেসচার, লেখা ও সেনসিটিভিটি ডিফল্টে ফিরে যাবে।")) },
            confirmButton = { TextButton(onClick = { save(GestureConfig()); confirmRestore = false }) { Text(tr("Restore", "ফেরান")) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(tr("Cancel", "বাতিল")) } },
        )
    }
}

@Composable
private fun GestureSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, valueText: String, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(valueText, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = (steps - 2).coerceAtLeast(0), enabled = enabled)
    }
}

/** One gesture: icon, gesture and key, the action (or why it can't run), a switch, and edit. */
@Composable
private fun GestureRow(b: GestureBinding, config: GestureConfig, builtIns: BuiltIns, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val reserved = reservedReason(b.zone, b.type, builtIns)
    val problem = reserved ?: if (!b.action.available) tr("Not available yet", "এখনো নেই") else if (b.action == GestureAction.INSERT_TEXT && b.text.isEmpty()) tr("No text set", "কোনো লেখা দেওয়া হয়নি") else null
    val usable = problem == null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            .alpha(if (config.enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(gestureIcon(b.type))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text("${b.type.uiTitle} · ${b.zone.uiTitle}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
            Text(actionLabel(b), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (problem != null) Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Switch(checked = b.enabled && usable, onCheckedChange = onToggle, enabled = usable && config.enabled)
        IconButton(onClick = onEdit) { MsIcon(UiR.drawable.ms_edit, tr("Edit ${b.type.uiTitle} on ${b.zone.uiTitle}", "${b.zone.uiTitle}-এ ${b.type.uiTitle} এডিট করুন")) }
    }
}

/** Edits one gesture: demonstration, key, gesture, action (and text), a test pad, warnings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestureEditor(
    initial: GestureBinding,
    isNew: Boolean,
    config: GestureConfig,
    builtIns: BuiltIns,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (GestureBinding) -> Unit,
) {
    var zone by remember { mutableStateOf(initial.zone) }
    var type by remember { mutableStateOf(initial.type) }
    var action by remember { mutableStateOf(initial.action) }
    var text by remember { mutableStateOf(initial.text) }
    val draft = GestureBinding(zone, type, action, initial.enabled || isNew, text)
    val reserved = reservedReason(zone, type, builtIns)
    val taken = config.binding(zone, type)?.takeIf { zone != initial.zone || type != initial.type }
    val replaces = replacesNote(zone, type, builtIns)
    val same = GestureConflicts.sameAction(config, draft)
    val canSave = reserved == null && action.available && (action != GestureAction.INSERT_TEXT || text.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) tr("New gesture", "নতুন জেসচার") else tr("Edit gesture", "জেসচার এডিট")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GestureDemo(type, zone)

                Text(tr("Key", "কী"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GestureZone.entries.forEach { z ->
                        FilterChip(selected = zone == z, onClick = {
                            zone = z
                            if (type !in GestureConflicts.allowedTypes(z)) type = GestureConflicts.allowedTypes(z).first()
                        }, label = { Text(z.uiTitle) })
                    }
                }
                Text(tr("Gesture", "জেসচার"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GestureConflicts.allowedTypes(zone).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.uiTitle) },
                            leadingIcon = { MsIcon(gestureIcon(t), null, Modifier.size(18.dp)) },
                        )
                    }
                }
                reserved?.let { Text(tr("Can't use: $it", "ব্যবহার করা যাবে না: $it"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                taken?.let { Text(tr("Replaces “${actionLabel(it)}” on this gesture", "এই জেসচারের “${actionLabel(it)}” বদলে যাবে"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                replaces?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }

                Text(tr("Action", "কাজ"), style = MaterialTheme.typography.labelLarge)
                Column {
                    GestureAction.entries.forEach { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = action == a, enabled = a.available, role = Role.RadioButton) { action = a }
                                .padding(vertical = 2.dp)
                                .alpha(if (a.available) 1f else 0.45f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = action == a, onClick = null, enabled = a.available)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(a.uiTitle, style = MaterialTheme.typography.bodyMedium)
                                Text(a.uiDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (action == GestureAction.INSERT_TEXT) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(500) },
                        label = { Text(tr("Text to insert", "যে লেখা বসবে")) },
                        placeholder = { Text("@gmail.com") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (same.isNotEmpty()) {
                    Text(
                        tr("Also on: ", "এটি আরও আছে: ") + same.joinToString { "${it.type.uiTitle} · ${it.zone.uiTitle}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(tr("Try it", "চালিয়ে দেখুন"), style = MaterialTheme.typography.labelLarge)
                GestureTestPad(config, draft)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }, enabled = canSave) { Text(tr("Save", "সেভ করুন")) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(tr("Delete", "মুছুন")) }
                TextButton(onClick = onDismiss) { Text(tr("Cancel", "বাতিল")) }
            }
        },
    )
}

/** An animated sketch of the gesture on the chosen key. */
@Composable
private fun GestureDemo(type: GestureType, zone: GestureZone) {
    val transition = rememberInfiniteTransition(label = "demo")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val keyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val keyW = if (zone == GestureZone.SPACE || zone == GestureZone.TOOLBAR) size.width * 0.6f else size.width * 0.22f
                val keyH = 48.dp.toPx()
                val left = (size.width - keyW) / 2
                val top = (size.height - keyH) / 2
                drawRoundRect(keyColor, Offset(left, top), Size(keyW, keyH), CornerRadius(12.dp.toPx()))
                val c = Offset(size.width / 2, size.height / 2)
                val reach = 44.dp.toPx()
                val dot = 9.dp.toPx()
                when (type) {
                    GestureType.LONG_PRESS -> {
                        drawCircle(accent, dot, c)
                        drawArc(accent, -90f, 360f * t, false, Offset(c.x - 20.dp.toPx(), c.y - 20.dp.toPx()), Size(40.dp.toPx(), 40.dp.toPx()), style = Stroke(3.dp.toPx()))
                    }
                    GestureType.DOUBLE_TAP -> {
                        val pulse = if (t < 0.5f) t * 2 else (t - 0.5f) * 2
                        drawCircle(accent.copy(alpha = 1f - pulse), dot + 18.dp.toPx() * pulse, c)
                        drawCircle(accent, dot, c)
                    }
                    else -> {
                        val dir = when (type) {
                            GestureType.SWIPE_UP -> Offset(0f, -1f)
                            GestureType.SWIPE_DOWN -> Offset(0f, 1f)
                            GestureType.SWIPE_LEFT -> Offset(-1f, 0f)
                            else -> Offset(1f, 0f)
                        }
                        val end = Offset(c.x + dir.x * reach * t, c.y + dir.y * reach * t)
                        drawLine(accent.copy(alpha = 0.35f), c, end, strokeWidth = dot * 1.4f)
                        drawCircle(accent, dot, end)
                    }
                }
            }
            Text(zone.uiTitle, style = MaterialTheme.typography.labelSmall, color = label, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        }
    }
}

/**
 * A pad that recognises gestures with the keyboard's own rules (same classifier and
 * thresholds), and says whether this one matches the gesture being edited.
 */
@Composable
private fun GestureTestPad(config: GestureConfig, draft: GestureBinding) {
    val thresholds = GestureThresholds.of(config)
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    var result by remember { mutableStateOf<GestureType?>(null) }
    var tapped by remember { mutableStateOf(false) }
    val doubleTaps = remember(thresholds) { DoubleTapTracker(thresholds.doubleTapMs) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .pointerInput(thresholds) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var recognised: GestureType? = null
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (kotlin.math.abs(dx) > 12 * density || kotlin.math.abs(dy) > 12 * density) moved = true
                            val dir = SwipeClassifier.classify(dx, dy, thresholds.minSwipeDp * density, thresholds.dominance)
                            if (dir != null) {
                                recognised = GestureType.swipe(dir)
                                break
                            }
                            if (!change.pressed) {
                                val held = change.uptimeMillis - down.uptimeMillis
                                recognised = when {
                                    moved -> null
                                    held >= thresholds.longPressMs -> GestureType.LONG_PRESS
                                    doubleTaps.onTap("pad", change.uptimeMillis) -> GestureType.DOUBLE_TAP
                                    else -> null
                                }
                                tapped = recognised == null && !moved
                                break
                            }
                        }
                        if (recognised != null) {
                            result = recognised
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val r = result
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (r == null) {
                    MsIcon(UiR.drawable.ms_gesture, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(if (tapped) tr("That was a tap", "এটা ছিল একটা ট্যাপ") else tr("Do the gesture here", "এখানে জেসচারটি করে দেখুন"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                } else {
                    MsIcon(gestureIcon(r), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        if (r == draft.type) {
                            "${r.uiTitle} ✓ → ${actionLabel(draft)}"
                        } else {
                            tr("${r.uiTitle} (this one is ${draft.type.uiTitle.lowercase()})", "${r.uiTitle} (এখানে লাগবে ${draft.type.uiTitle})")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
    Spacer(Modifier.width(1.dp))
}
