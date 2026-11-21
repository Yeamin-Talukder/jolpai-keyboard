package com.yeamin.jolpai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yeamin.jolpai.core.KeyboardController
import com.yeamin.jolpai.core.editor.InMemoryEditor
import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.settings.HapticLevel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.OneHanded
import com.yeamin.jolpai.core.theme.ThemeRepository
import com.yeamin.jolpai.theme.BuiltInThemes
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.theme.AppTint
import com.yeamin.jolpai.theme.AppTintStyle
import com.yeamin.jolpai.theme.LiveStyle
import com.yeamin.jolpai.theme.LiveThemes
import com.yeamin.jolpai.theme.PressEffect
import com.yeamin.jolpai.theme.ThemeFamily
import com.yeamin.jolpai.theme.ThemeTypes
import androidx.compose.material3.FilterChip
import com.yeamin.jolpai.theme.ThemeCodec
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.yeamin.jolpai.ui.KeyboardContainer
import com.yeamin.jolpai.ui.KeyboardPalette
import com.yeamin.jolpai.ui.R as UiR

/**
 * The real keyboard view, drawn with [theme] and [settings]: what you see here is exactly what
 * the keyboard looks like. Keys respond (shift, layouts) but type nowhere.
 */
@Composable
fun KeyboardPreview(theme: KeyboardTheme, settings: KeyboardSettings) {
    key(theme, settings.heightPortrait, settings.numberRow, settings.oneHanded, settings.enabledLayouts, settings.toolbar, settings.panelPlacement, settings.keyLook, settings.keyFont, settings.banglaKeyFont, settings.customKeyFontVersion) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp)),
            factory = { context ->
                val controller = KeyboardController(InMemoryEditor(), clock = { System.nanoTime() / 1_000_000 })
                controller.updateSettings(settings.copy(autoCapitalize = false, floating = false))
                controller.onStartInput(FieldInfo.DEFAULT, restarting = false)
                lateinit var view: KeyboardContainer
                view = KeyboardContainer(
                    context = context,
                    onAction = { action ->
                        controller.onKey(action)
                        view.render(controller.state.value)
                    },
                    servicePanel = { _, _, _ -> },
                    palette = KeyboardPalette.from(context, theme),
                    options = KeyboardContainer.LayoutOptions(
                        heightScale = settings.heightPortrait / 100f,
                        oneHanded = settings.oneHanded,
                        keyFont = settings.keyFont,
                        banglaKeyFont = settings.banglaKeyFont,
                        customFontVersion = settings.customKeyFontVersion,
                    ),
                )
                view.render(controller.state.value)
                view
            },
        )
    }
}

/** Theme gallery: pick, customise, import and export themes. */
@Composable
fun ThemesScreen(
    vm: MainViewModel,
    settings: KeyboardSettings,
    customThemes: List<KeyboardTheme>,
    systemDark: Boolean,
    onEdit: (KeyboardTheme) -> Unit,
    onBack: () -> Unit,
) {
    var importText by rememberSaveable { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var exported by remember { mutableStateOf<String?>(null) }
    val active = ThemeRepository.resolve(settings.themeId, customThemes, systemDark)
    // Which kind of theme to show: a ThemeFamily name, FESTIVALS, YOURS, or null for all.
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    fun shows(key: String) = filter == null || filter == key
    // Chameleon preview colour: null shows Jolpai's own (the app you're in); a swatch shows how
    // the Chameleon themes look in an app of that colour.
    var sample by rememberSaveable { mutableStateOf<Int?>(null) }
    fun shown(t: KeyboardTheme): KeyboardTheme {
        val s = sample
        return if (s != null && t.live is LiveStyle.AppColour) AppTint.apply(t, s) else t
    }

    SettingsScaffold("Themes", onBack) {
        KeyboardPreview(shown(active), settings)
        Text(
            "In use: ${active.name} · ${themeTypeLabel(active.id)}",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Filter by kind: one tap shows only that kind of theme.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val chips = listOf<Pair<String?, String>>(null to "All") +
                ThemeFamily.entries.map { it.name to it.label } +
                listOf(FESTIVALS to "Festivals", YOURS to "Yours")
            chips.forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
            }
        }

        ThemeFamily.entries.filter { shows(it.name) }.forEach { family ->
            if (family == ThemeFamily.CHAMELEON) {
                SettingsNote(
                    "Chameleon themes take the colour of the app you're typing in, read from its icon on this phone " +
                        "(a keyboard can't see the screen itself; nothing leaves the phone). Here they show Jolpai's own " +
                        "colour; tap a colour to see them as they'd look in an app of that colour.",
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CHAMELEON_SAMPLES.forEach { (colour, label) ->
                        FilterChip(
                            selected = sample == colour,
                            onClick = { sample = colour },
                            label = { Text(label) },
                            leadingIcon = {
                                Box(
                                    Modifier.size(14.dp).clip(CircleShape)
                                        .background(colour?.let { Color(it) } ?: MaterialTheme.colorScheme.primary),
                                )
                            },
                        )
                    }
                }
            }
            ThemeTypes.inFamily(family).forEach { type ->
                SettingsGroup("${type.name} · ${type.themes.size + if (family == ThemeFamily.BASICS) 1 else 0}") {
                    if (family == ThemeFamily.BASICS) {
                        ThemeRow(
                            "System",
                            "Material You: your wallpaper colours, light or dark with the phone",
                            BuiltInThemes.forSystem(systemDark),
                            settings.themeId == KeyboardTheme.SYSTEM_ID,
                        ) { vm.selectTheme(KeyboardTheme.SYSTEM_ID) }
                    }
                    type.themes.forEach { t ->
                        val summary = if (t.dynamic) "Your wallpaper colours (Android 12+)" else liveSummary(t)
                        BuiltInThemeRow(vm, t, summary, settings.themeId, onEdit, shown = shown(t))
                    }
                }
                type.description?.let { SettingsNote("$it.") }
            }
        }
        if (shows(ThemeFamily.DYNAMIC.name)) {
            SettingsNote("With the phone's animations turned off, nothing moves.")
        }

        if (shows(FESTIVALS)) SettingsGroup("Festivals · ${BuiltInThemes.festivals.size}") {
            val today = remember { com.yeamin.jolpai.theme.Festivals.on(java.time.LocalDate.now()) }
            SettingsSwitch(
                "Festival themes on festival days",
                (today?.let { "Today is ${it.name}. " } ?: "") +
                    "পহেলা বৈশাখ, একুশে ফেব্রুয়ারি, স্বাধীনতা ও বিজয় দিবস, রমজান and both Eids get their own theme for the day; then yours comes back.",
                checked = settings.festivalThemes,
                onChange = vm::setFestivalThemes,
                icon = UiR.drawable.ms_auto_awesome,
            )
            BuiltInThemes.festivals.forEach { t ->
                ThemeRow(
                    t.name, null, t, settings.themeId == t.id,
                    actions = {
                        IconButton(onClick = { vm.duplicateTheme(t, onEdit) }) { MsIcon(UiR.drawable.ms_edit, "Customise ${t.name}") }
                    },
                ) { vm.selectTheme(t.id) }
            }
        }

        if (shows(YOURS)) SettingsGroup("Yours · ${customThemes.size}") {
            if (customThemes.isEmpty()) {
                SettingsNote("Tap the pencil on any theme to make your own copy.", Modifier.padding(vertical = 8.dp))
            }
            customThemes.forEach { t ->
                ThemeRow(
                    t.name, if (t.dynamic) "Your wallpaper colours (Android 12+)" else liveSummary(t), t, settings.themeId == t.id,
                    actions = {
                        IconButton(onClick = { onEdit(t) }) { MsIcon(UiR.drawable.ms_edit, "Edit ${t.name}") }
                        IconButton(onClick = { vm.duplicateTheme(t) { } }) { MsIcon(UiR.drawable.ms_content_copy, "Duplicate ${t.name}") }
                        IconButton(onClick = { vm.exportTheme(t); exported = t.name }) { MsIcon(UiR.drawable.ms_upload, "Copy ${t.name} as text") }
                        IconButton(onClick = { vm.deleteTheme(t, settings.themeId) }) { MsIcon(UiR.drawable.ms_delete, "Delete ${t.name}") }
                    },
                ) { vm.selectTheme(t.id) }
            }
        }
        exported?.let { SettingsNote("“$it” copied as text. Paste it anywhere to share or back it up (the background picture isn't included).") }

        if (shows(YOURS)) SettingsGroup("Import a theme") {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("Paste theme text") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
                FilledTonalButton(onClick = {
                    vm.importTheme(importText) { error ->
                        importMessage = error ?: "Imported and applied."
                        if (error == null) importText = ""
                    }
                }, enabled = importText.isNotBlank()) {
                    MsIcon(UiR.drawable.ms_download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
                importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/**
 * A built-in theme with a pencil that makes a custom copy. A dynamic theme is copied as it looks
 * right now (custom themes don't change by themselves; ambient glows are kept); a Chameleon in
 * the colour it is [shown] in. [shown] is what the sketch draws (a Chameleon in a preview colour).
 */
@Composable
private fun BuiltInThemeRow(
    vm: MainViewModel,
    t: KeyboardTheme,
    summary: String?,
    activeId: String,
    onEdit: (KeyboardTheme) -> Unit,
    shown: KeyboardTheme = t,
) {
    val context = LocalContext.current
    ThemeRow(
        t.name, summary, shown, activeId == t.id,
        actions = {
            // The copy keeps everything, including how the theme changes by itself (app colour,
            // time of day, drifting glows, wallpaper colours); the editor can turn those off.
            IconButton(onClick = { vm.duplicateTheme(t, onEdit) }) {
                MsIcon(UiR.drawable.ms_edit, "Customise ${t.name}")
            }
        },
    ) { vm.selectTheme(t.id) }
}

private const val FESTIVALS = "FESTIVALS"
private const val YOURS = "YOURS"

/** Colours to preview Chameleon themes in (null: Jolpai's own, the app you're in). */
private val CHAMELEON_SAMPLES: List<Pair<Int?, String>> = listOf(
    null to "Jolpai",
    0xFF25D366.toInt() to "Green",
    0xFF1877F2.toInt() to "Blue",
    0xFF2AABEE.toInt() to "Sky",
    0xFF7C4DFF.toInt() to "Purple",
    0xFFE1306C.toInt() to "Pink",
    0xFFFF0033.toInt() to "Red",
    0xFFFF6D00.toInt() to "Orange",
    0xFFFFC107.toInt() to "Yellow",
    0xFF9E9E9E.toInt() to "Grey",
)

/** The kind of the theme being drawn ([id]), for the "In use" line: its family and type ("Dark · Soft dark"). */
private fun themeTypeLabel(id: String): String = when {
    id.startsWith(KeyboardTheme.CUSTOM_PREFIX) -> "Yours"
    BuiltInThemes.festivals.any { it.id == id } -> "Festivals"
    else -> ThemeTypes.typeOf(id)?.let { "${it.family.label} · ${it.name}" } ?: "Basics · Essentials"
}

/** What a changing theme does, for its row in the gallery. */
private fun liveSummary(t: KeyboardTheme): String? = when (val live = t.live) {
    is LiveStyle.TimeOfDay -> when (t.id) {
        BuiltInThemes.NIGHT_SHIFT.id -> "Always dark: cool by day, warmer and lower in blue light late at night"
        BuiltInThemes.TWILIGHT_CYCLE.id -> "Always dark: rose dawn, indigo day, magenta evening, navy night"
        else -> "Follows the time of day: light by day, dark at night"
    }
    is LiveStyle.Drift -> t.pressEffect?.let { "Drifting glow, and ${pressEffectSummary(it).replaceFirstChar(Char::lowercase)}" }
        ?: "Slowly drifting glow (still when animations are off)"
    is LiveStyle.AppColour -> when {
        live.driftSeconds > 0 -> "Glows in the app's colour that slowly drift"
        t.pressEffect != null -> "In the app's colour; ${pressEffectSummary(t.pressEffect!!).replaceFirstChar(Char::lowercase)}"
        else -> when (live.style) {
            AppTintStyle.GLOW -> "Glows in the colour of the app you're typing in"
            AppTintStyle.MIST -> "A deep wash of the app's colour"
            AppTintStyle.LIGHT -> "Light, tinted with the app's colour"
            AppTintStyle.BLACK -> "Pure black; the app's colour in the outlines and Enter"
            AppTintStyle.NEON -> "Bright outlines in the app's colour"
            AppTintStyle.SPOTLIGHT -> "Lit from the top by the app's colour"
            AppTintStyle.DUO -> "The app's colour with its opposite, as two glows"
            AppTintStyle.GLASS -> "Frosted keys over a gradient of the app's colour"
            AppTintStyle.PASTEL -> "Soft pastel keys in the app's colour"
        }
    }
    null -> t.pressEffect?.let { pressEffectSummary(it) }
}

private fun pressEffectSummary(e: PressEffect): String = when (e) {
    PressEffect.INK -> "A soft wash of colour spreads from your fingertip"
    PressEffect.BACKLIGHT -> "Keys light up from within, like a backlit keyboard"
    PressEffect.FLOAT -> "The letter you type floats up and fades"
    PressEffect.HALO -> "A thin glowing outline lifts off each key"
    PressEffect.TRAIL -> "A soft light trail joins the keys as you type"
    PressEffect.MOTES -> "A glowing mote or two drifts up from the key"
    PressEffect.POP -> "Keys give a soft, springy bounce of light"
    PressEffect.SHEEN -> "A glint of light sweeps across each key"
    PressEffect.SPARKLE -> "Little stars twinkle at the key's edges"
    PressEffect.DROPLET -> "Rings spread from your fingertip like a raindrop"
    PressEffect.HEARTS -> "A small heart floats up from each key"
    PressEffect.UNDERLINE -> "A glowing line grows under each key"
    PressEffect.RUBBER -> "Soft rubber keys that squash and bounce back"
    PressEffect.SQUISH -> "Soft keys that squish and settle with one gentle bounce"
    PressEffect.JELLY -> "Jelly keys: a deep squash and a slow wobble"
}

/** A theme: a small keyboard sketch in its colours, its name, a check when in use, and actions. */
@Composable
private fun ThemeRow(
    name: String,
    summary: String?,
    theme: KeyboardTheme,
    selected: Boolean,
    actions: @Composable () -> Unit = {},
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSketch(theme, selected)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) MsIcon(UiR.drawable.ms_check_circle_fill, "In use", tint = MaterialTheme.colorScheme.primary)
        actions()
    }
}

/**
 * Two rows of tiny keys in the theme's colours, with the accent Enter key. The theme in use gets
 * a ring around the sketch, separated from it by a gap in the page colour, and a check badge, so
 * the mark stands out whatever the theme's own colours are (a primary-coloured border vanished
 * on light themes when the app's colours were light too).
 */
@Composable
internal fun ThemeSketch(theme: KeyboardTheme, selected: Boolean) {
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "ring",
    )
    val badge by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "badge")
    Box {
        Box(
            Modifier
                .border(2.5.dp, ring, RoundedCornerShape(16.dp))
                .padding(4.dp),
        ) { SketchBody(theme) }
        if (badge > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .graphicsLayer { scaleX = badge; scaleY = badge }
                    .size(22.dp)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                MsIcon(UiR.drawable.ms_check, "In use", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun SketchBody(sketched: KeyboardTheme) {
    val context = LocalContext.current
    val theme = remember(sketched, context) { KeyboardPalette.resolveDynamic(context, sketched) }
    val bg = theme.backgroundEnd?.let { Brush.verticalGradient(listOf(Color(theme.background), Color(it))) }
        ?: Brush.verticalGradient(listOf(Color(theme.background), Color(theme.background)))
    Column(
        Modifier
            .size(width = 76.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .drawBehind {
                // Ambient glows, at rest (the sketches don't animate).
                theme.glows.forEach { g ->
                    val center = Offset(g.x * size.width, g.y * size.height)
                    val radius = (g.radius * size.width).coerceAtLeast(1f)
                    drawCircle(
                        Brush.radialGradient(listOf(Color(g.color), Color(g.color).copy(alpha = 0f)), center, radius),
                        radius,
                        center,
                    )
                }
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val r = RoundedCornerShape((theme.keyRadiusDp / 3).coerceAtMost(4f).dp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { Box(Modifier.size(width = 10.dp, height = 14.dp).clip(r).background(Color(theme.key))) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(width = 10.dp, height = 14.dp).clip(r).background(Color(theme.functionKey)))
            Box(Modifier.size(width = 23.dp, height = 14.dp).clip(r).background(Color(theme.key)))
            Box(Modifier.size(width = 10.dp, height = 14.dp).clip(r).background(Color(theme.functionKey)))
            Box(Modifier.size(width = 10.dp, height = 14.dp).clip(r).background(Color(theme.accent)))
        }
    }
}

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFF7F7F7, 0xFFE6E8EB, 0xFFC9CED6, 0xFF9AA0A6, 0xFF5F6368, 0xFF3C4043, 0xFF1E1F22, 0xFF000000,
    0xFF1A73E8, 0xFF8AB4F8, 0xFF0B3C5D, 0xFF1D5F80, 0xFF2D5A47, 0xFF1B3A2F, 0xFF006A4E, 0xFFF42A41,
    0xFF7B2D8E, 0xFFB83B6B, 0xFFFFD166, 0xFFF4D35E, 0xFFFFC857, 0xFFE8EAED, 0xFF1F2328,
).map { it.toInt() }

/** Edits a custom theme with a live preview. */
@Composable
fun ThemeEditorScreen(vm: MainViewModel, settings: KeyboardSettings, initial: KeyboardTheme, onDone: () -> Unit) {
    // Everything is kept, including how the theme changes by itself (see "Changes by itself").
    var t by remember(initial.id) { mutableStateOf(initial) }
    var imageNote by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    /** The colours as they look right now (app colour, time of day, wallpaper applied), as plain colours. */
    fun stillColours(x: KeyboardTheme): KeyboardTheme = LiveThemes.at(KeyboardPalette.resolveDynamic(context, x), java.time.LocalTime.now())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickThemeBackground(uri) { path ->
            if (path != null) t = t.copy(backgroundImage = path) else imageNote = "That picture couldn't be used."
        }
    }

    SettingsScaffold(
        "Edit theme",
        onDone,
        actions = { TextButton(onClick = { vm.saveTheme(t); onDone() }) { Text("Save") } },
    ) {
        KeyboardPreview(t, settings)
        OutlinedTextField(
            value = t.name,
            onValueChange = { t = t.copy(name = it.take(40)) },
            label = { Text("Name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        LiveSettings(initial, t, onChange = { t = it }, stillColours = ::stillColours)

        val colourSource = when {
            t.live is LiveStyle.AppColour -> "the app you're typing in"
            t.live is LiveStyle.TimeOfDay -> "the time of day"
            t.dynamic -> "your wallpaper (Android 12+)"
            else -> null
        }
        if (colourSource != null) {
            SettingsGroup("Colours") {
                SettingsNote(
                    "The colours come from $colourSource. Turn that off above to pick your own; they start from how the theme looks now.",
                    Modifier.padding(vertical = 8.dp),
                )
            }
        } else SettingsGroup("Colours") {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorRow("Background", t.background) { t = t.copy(background = it) }
                OptionalColorRow("Gradient bottom", t.backgroundEnd) { t = t.copy(backgroundEnd = it) }
                ColorRow("Letter keys", t.key) { t = t.copy(key = it) }
                ColorRow("Pressed keys", t.keyPressed) { t = t.copy(keyPressed = it) }
                ColorRow("Other keys", t.functionKey) { t = t.copy(functionKey = it) }
                ColorRow("Key text", t.text) { t = t.copy(text = it) }
                ColorRow("Small hints", t.hint) { t = t.copy(hint = it) }
                ColorRow("Accent (Enter, highlights)", t.accent) { t = t.copy(accent = it) }
                ColorRow("Text on accent", t.onAccent) { t = t.copy(onAccent = it) }
                OptionalColorRow("Key outline", t.border) { t = t.copy(border = it) }
            }
        }

        SettingsGroup("Keys") {
            Column(Modifier.padding(GroupContentPadding)) {
                LabeledSlider("Key corners", t.keyRadiusDp, 0f..24f, "${t.keyRadiusDp.toInt()} dp") { t = t.copy(keyRadiusDp = it) }
                LabeledSlider("Space between keys", t.keyGapDp, 0f..16f, "${t.keyGapDp.toInt()} dp") { t = t.copy(keyGapDp = it) }
                LabeledSlider("Key opacity", t.keyOpacity, 0.2f..1f, "${(t.keyOpacity * 100).toInt()} %") { t = t.copy(keyOpacity = it) }
                LabeledSlider("Label size", t.labelScale, 0.7f..1.4f, "${(t.labelScale * 100).toInt()} %") { t = t.copy(labelScale = it) }
            }
            SettingsSwitch("Bold labels", checked = t.boldLabels, onChange = { t = t.copy(boldLabels = it) }, icon = UiR.drawable.ms_format_size)
            SettingsSwitch("Key shadows", checked = t.shadow, onChange = { t = t.copy(shadow = it) }, icon = UiR.drawable.ms_tune)
            SettingsSwitch("Dark theme", "For the panels and previews", t.dark, { t = t.copy(dark = it) }, UiR.drawable.ms_dark_mode)
        }

        SettingsGroup("Key press effect") {
            SettingsRadio("None", selected = t.pressEffect == null) { t = t.copy(pressEffect = null) }
            PressEffect.entries.forEach { e ->
                SettingsRadio(e.label, selected = t.pressEffect == e) { t = t.copy(pressEffect = e) }
            }
        }
        SettingsNote("Drawn in the accent colour (or the theme's own effect colour). Try it on the preview above.")

        SettingsGroup("Background picture") {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        MsIcon(UiR.drawable.ms_image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (t.backgroundImage == null) "Choose picture" else "Change picture")
                    }
                    if (t.backgroundImage != null) OutlinedButton(onClick = { t = t.copy(backgroundImage = null) }) { Text("Remove") }
                }
                if (t.backgroundImage != null) {
                    LabeledSlider("Darken picture", t.imageDim, 0f..0.9f, "${(t.imageDim * 100).toInt()} %") { t = t.copy(imageDim = it) }
                }
                Text(
                    "Scaled to the keyboard and cropped from the centre. It stays on this phone." + (imageNote?.let { "\n$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveTheme(t); onDone() }) { Text("Save and use") }
            OutlinedButton(onClick = { t = initial }) { Text("Reset") }
            TextButton(onClick = onDone) { Text("Cancel") }
        }
    }
}

/**
 * "Changes by itself": switches for what makes [initial] change on its own, so a customised
 * copy keeps it (or drops it on purpose). Only what the theme has, plus drifting for any theme
 * with glows. Turning a colour source off keeps the colours as they look now ([stillColours]).
 */
@Composable
private fun LiveSettings(initial: KeyboardTheme, t: KeyboardTheme, onChange: (KeyboardTheme) -> Unit, stillColours: (KeyboardTheme) -> KeyboardTheme) {
    val originalApp = initial.live as? LiveStyle.AppColour
    val originalTime = initial.live as? LiveStyle.TimeOfDay
    val appColour = t.live as? LiveStyle.AppColour
    val canDrift = t.glows.isNotEmpty() || appColour != null || initial.live is LiveStyle.Drift || (originalApp?.driftSeconds ?: 0) > 0
    if (originalApp == null && originalTime == null && !initial.dynamic && !canDrift) return

    SettingsGroup("Changes by itself") {
        if (initial.dynamic) {
            SettingsSwitch(
                "Wallpaper colours",
                "Material You: the colours of your wallpaper (Android 12+)",
                checked = t.dynamic,
                onChange = { on -> onChange(if (on) t.copy(dynamic = true) else stillColours(t).copy(dynamic = false)) },
                icon = UiR.drawable.ms_auto_awesome,
            )
        }
        if (originalApp != null) {
            SettingsSwitch(
                "Colours from the app",
                "Takes the colour of the app you're typing in (Chameleon)",
                checked = appColour != null,
                onChange = { on ->
                    onChange(
                        if (on) t.copy(live = LiveStyle.AppColour(originalApp.style, originalApp.driftSeconds))
                        // Keep the colours as they look now; drifting glows stay if they were drifting.
                        else stillColours(t),
                    )
                },
                icon = UiR.drawable.ms_auto_awesome,
            )
        }
        if (originalTime != null) {
            SettingsSwitch(
                "Colours follow the time of day",
                "They change through the day, each time the keyboard opens",
                checked = t.live is LiveStyle.TimeOfDay,
                onChange = { on -> onChange(if (on) t.copy(live = originalTime) else stillColours(t).copy(live = null)) },
                icon = UiR.drawable.ms_dark_mode,
            )
        }
        if (canDrift && t.live !is LiveStyle.TimeOfDay) {
            val drifting = t.live is LiveStyle.Drift || (appColour?.driftSeconds ?: 0) > 0
            SettingsSwitch(
                "Glows drift slowly",
                "Still when the phone's animations are off or battery saver is on",
                checked = drifting,
                onChange = { on ->
                    val period = (initial.live as? LiveStyle.Drift)?.periodSeconds ?: originalApp?.driftSeconds?.takeIf { it > 0 } ?: 50
                    onChange(
                        when {
                            appColour != null -> t.copy(live = appColour.copy(driftSeconds = if (on) period else 0))
                            on -> t.copy(live = LiveStyle.Drift(period))
                            else -> t.copy(live = null)
                        },
                    )
                },
                icon = UiR.drawable.ms_tune,
            )
        }
    }
    if (appColour != null) {
        SettingsGroup("Chameleon look") {
            AppTintStyle.entries.forEach { style ->
                SettingsRadio(appTintLabel(style), selected = appColour.style == style) {
                    onChange(t.copy(live = appColour.copy(style = style), dark = style != AppTintStyle.LIGHT && style != AppTintStyle.PASTEL))
                }
            }
        }
    }
}

private fun appTintLabel(style: AppTintStyle): String = when (style) {
    AppTintStyle.GLOW -> "Glow: near-black with glows in the app's colour"
    AppTintStyle.MIST -> "Mist: a deep wash of the app's colour"
    AppTintStyle.LIGHT -> "Light: a pale tint, white keys"
    AppTintStyle.BLACK -> "Black: pure black, coloured outlines and Enter"
    AppTintStyle.NEON -> "Neon: bright outlines in the app's colour"
    AppTintStyle.SPOTLIGHT -> "Spotlight: lit from the top by the app's colour"
    AppTintStyle.DUO -> "Duo: the app's colour and its opposite"
    AppTintStyle.GLASS -> "Glass: frosted keys over the app's colour"
    AppTintStyle.PASTEL -> "Pastel: soft pastel keys"
}

@Composable
private fun ColorRow(label: String, color: Int, onPick: (Int) -> Unit) {
    // Opaque colours show as #RRGGBB; translucent ones keep their alpha (#AARRGGBB).
    var hex by remember(color) { mutableStateOf(ThemeCodec.hex(color).let { if (it.startsWith("#FF")) "#" + it.substring(3) else it }) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            Text(label, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = hex,
                onValueChange = { v ->
                    hex = v
                    runCatching { ThemeCodec.parseHex(v) }.getOrNull()?.let(onPick)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(120.dp),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SWATCHES.forEach { s ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(s))
                        .border(if (s == color) 3.dp else 1.dp, if (s == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .selectable(s == color, role = Role.RadioButton) { onPick(s) },
                )
            }
        }
    }
}

@Composable
private fun OptionalColorRow(label: String, color: Int?, onPick: (Int?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPick(if (color == null) 0xFF9AA0A6.toInt() else null) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Switch(checked = color != null, onCheckedChange = null)
    }
    if (color != null) ColorRow("$label colour", color) { onPick(it) }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(valueText, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Keyboard height, number row, one-handed and floating modes. */
@Composable
fun SizeScreen(vm: MainViewModel, settings: KeyboardSettings, theme: KeyboardTheme, onBack: () -> Unit) {
    SettingsScaffold("Size & layout", onBack) {
        KeyboardPreview(theme, settings)
        SettingsGroup("Height") {
            Column(Modifier.padding(GroupContentPadding)) {
                LabeledSlider("Portrait", settings.heightPortrait.toFloat(), 70f..140f, "${settings.heightPortrait} %") {
                    vm.setHeight(portrait = true, percent = it.toInt())
                }
                LabeledSlider("Landscape", settings.heightLandscape.toFloat(), 70f..140f, "${settings.heightLandscape} %") {
                    vm.setHeight(portrait = false, percent = it.toInt())
                }
            }
        }
        SettingsGroup("Layout") {
            SettingsSwitch("Number row", "Digits above the letters", settings.numberRow, vm::setNumberRow, UiR.drawable.ms_123)
            SettingsSwitch(
                "Floating keyboard",
                "A smaller keyboard you can drag; the app behind stays full size",
                settings.floating,
                vm::setFloating,
                UiR.drawable.ms_picture_in_picture,
            )
            if (settings.floating) {
                SettingsItem("Reset position", null, UiR.drawable.ms_restart_alt, showChevron = false, onClick = vm::resetFloatPosition)
            }
        }
        SettingsGroup("One-handed") {
            listOf(OneHanded.OFF to "Off", OneHanded.LEFT to "Left hand", OneHanded.RIGHT to "Right hand").forEach { (mode, label) ->
                SettingsRadio(label, selected = settings.oneHanded == mode) { vm.setOneHanded(mode) }
            }
        }
        SettingsNote("In one-handed mode the keys move to one side; the arrows beside them switch sides or return to full width.")

        SettingsGroup("Split keyboard") {
            com.yeamin.jolpai.core.settings.SplitKeys.entries.forEach { mode ->
                SettingsRadio(mode.label, selected = settings.splitKeys == mode) { vm.setSplitKeys(mode) }
            }
        }
        SettingsNote("Split keys move the two halves of the keyboard to the sides, for typing with both thumbs on a wide screen or tablet. Not in one-handed or floating mode.")
    }
}

/** Vibration strength and key sounds. */
@Composable
fun SoundScreen(vm: MainViewModel, settings: KeyboardSettings, onBack: () -> Unit) {
    SettingsScaffold("Sound & vibration", onBack) {
        SettingsGroup("Vibration on key press") {
            HapticLevel.entries.forEach { level ->
                SettingsRadio(level.label, selected = settings.haptics == level) { vm.setHaptics(level) }
            }
        }
        SettingsNote("“Phone default” follows the phone's own setting. The other strengths are used only while the phone's touch vibration is on.")
        SettingsGroup("Sound") {
            SettingsSwitch("Key sounds", "Space, Enter and Backspace have their own sounds", settings.keySound, vm::setKeySound, UiR.drawable.ms_volume_up)
            if (settings.keySound) {
                Column(Modifier.padding(GroupContentPadding)) {
                    LabeledSlider("Volume", settings.soundVolume, 0f..1f, "${(settings.soundVolume * 100).toInt()} %") { vm.setSoundVolume(it) }
                }
            }
        }
        if (settings.keySound) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val player = remember { com.yeamin.jolpai.ui.SoundPackPlayer(context) }
            androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
            SettingsGroup("Sound") {
                com.yeamin.jolpai.core.settings.SoundPack.entries.forEach { pack ->
                    SettingsRadio(pack.label, selected = settings.soundPack == pack) {
                        vm.setSoundPack(pack)
                        // A short preview: letter, letter, space.
                        if (pack == com.yeamin.jolpai.core.settings.SoundPack.SYSTEM) {
                            context.getSystemService(android.media.AudioManager::class.java)
                                ?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, settings.soundVolume)
                        } else {
                            player.prepare(pack)
                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            listOf(120L, 260L, 400L).forEachIndexed { i, delay ->
                                handler.postDelayed({
                                    player.play(pack, if (i == 2) com.yeamin.jolpai.ui.SoundPackPlayer.Kind.SPACE else com.yeamin.jolpai.ui.SoundPackPlayer.Kind.STANDARD, settings.soundVolume.coerceAtLeast(0.3f))
                                }, delay)
                            }
                        }
                    }
                }
            }
            SettingsNote("Jolpai's own sounds are made on the phone, not downloaded. Tap one to hear it.")
        }
        SettingsNote("Sounds stay silent when the phone is on silent, vibrate or Do Not Disturb.")
        Spacer(Modifier.height(8.dp))
    }
}
