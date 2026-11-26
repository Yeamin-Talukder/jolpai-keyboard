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
import com.yeamin.jolpai.theme.ThemeType
import com.yeamin.jolpai.theme.ThemeTypes
import androidx.compose.material3.FilterChip
import com.yeamin.jolpai.theme.ThemeCodec
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.yeamin.jolpai.ui.KeyboardContainer
import com.yeamin.jolpai.ui.KeyboardPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
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

/**
 * Theme gallery: the keyboard as it looks now, then every built-in theme as a card in a grid,
 * grouped by kind. The kind filters stay pinned under the title while the grid scrolls. Tap a
 * card to use the theme; its pencil makes your own copy to customise. Your themes are rows with
 * edit, copy, share and delete. The grid is lazy: only the cards on screen are drawn.
 */
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
    // The last import's result: worked, or the reader's (English) error. Worded when shown.
    var imported by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
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
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    SettingsScaffold(tr("Themes", "থিম"), onBack, scrollable = false) {
        // Pinned: outside the grid, so it stays put while the themes scroll under it.
        ThemeFilters(filter, customThemes.size) { picked ->
            filter = picked
            scope.launch { grid.scrollToItem(0) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            state = grid,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = FabClearance),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "preview", span = full) {
                Column {
                    KeyboardPreview(shown(active), settings)
                    Text(
                        tr("In use: ${active.name}", "এখন চালু: ${active.name}"),
                        Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        listOfNotNull(themeTypeLabel(active.id), if (active.dynamic) wallpaperSummary() else liveSummary(active)).joinToString(" · "),
                        Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ThemeFamily.entries.filter { shows(it.name) }.forEach { family ->
                if (family == ThemeFamily.CHAMELEON) {
                    item(key = "chameleon", span = full) {
                        Column(Modifier.padding(top = 10.dp)) {
                            SettingsNote(
                                tr(
                                    "Chameleon themes match the colour of the app you're typing in. Nothing leaves your phone. Tap a colour to preview.",
                                    "ক্যামেলিয়ন থিম আপনি যে অ্যাপে লিখছেন তার রং নেয়। কিছুই ফোনের বাইরে যায় না। প্রিভিউ দেখতে একটি রং চাপুন।",
                                ),
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
                    }
                }
                ThemeTypes.inFamily(family).forEach { type ->
                    val count = type.themes.size + if (family == ThemeFamily.BASICS) 1 else 0
                    item(key = "type/${type.name}", span = full) { SectionHeader(type.galleryName, count, type.galleryNote) }
                    if (family == ThemeFamily.BASICS) {
                        item(key = "system") {
                            ThemeCard(
                                tr("System", "সিস্টেম"),
                                BuiltInThemes.forSystem(systemDark),
                                settings.themeId == KeyboardTheme.SYSTEM_ID,
                                onCustomise = null,
                            ) { vm.selectTheme(KeyboardTheme.SYSTEM_ID) }
                        }
                    }
                    items(type.themes, key = { "${type.name}/${it.id}" }) { t ->
                        // The copy keeps everything, including how the theme changes by itself
                        // (app colour, time of day, drifting glows, wallpaper colours); a
                        // Chameleon is copied in the colour it is shown in.
                        ThemeCard(t.name, shown(t), settings.themeId == t.id, onCustomise = { vm.duplicateTheme(t, onEdit) }) {
                            vm.selectTheme(t.id)
                        }
                    }
                }
            }
            if (shows(ThemeFamily.DYNAMIC.name)) {
                item(key = "dynamic-note", span = full) {
                    SettingsNote(tr("Nothing moves while the phone's animations are off.", "ফোনের অ্যানিমেশন বন্ধ থাকলে কিছুই নড়ে না।"))
                }
            }

            if (shows(FESTIVALS)) {
                item(key = "festivals", span = full) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(tr("Festivals", "উৎসব"), BuiltInThemes.festivals.size, null)
                        SettingsGroup {
                            val today = remember { com.yeamin.jolpai.theme.Festivals.on(java.time.LocalDate.now()) }
                            SettingsSwitch(
                                tr("Festival themes", "উৎসবের থিম"),
                                (today?.let { tr("Today is ${it.name}. ", "আজ ${it.name}। ") } ?: "") +
                                    tr(
                                        "A special theme on festival days like পহেলা বৈশাখ and Eid; yours comes back after.",
                                        "পহেলা বৈশাখ, একুশে, ঈদের মতো উৎসবের দিনে বিশেষ থিম; দিন শেষে আপনার থিম ফিরে আসে।",
                                    ),
                                checked = settings.festivalThemes,
                                onChange = vm::setFestivalThemes,
                                icon = UiR.drawable.ms_auto_awesome,
                            )
                        }
                    }
                }
                items(BuiltInThemes.festivals, key = { "festival/${it.id}" }) { t ->
                    ThemeCard(t.name, t, settings.themeId == t.id, onCustomise = { vm.duplicateTheme(t, onEdit) }) { vm.selectTheme(t.id) }
                }
            }

            if (shows(YOURS)) {
                item(key = "yours", span = full) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(tr("Your themes", "আপনার থিম"), customThemes.size, null)
                        SettingsGroup {
                            if (customThemes.isEmpty()) {
                                SettingsNote(
                                    tr("Tap the pencil on any theme to make your own copy.", "নিজের কপি বানাতে যেকোনো থিমের পেনসিলে চাপুন।"),
                                    Modifier.padding(vertical = 8.dp),
                                )
                            }
                            customThemes.forEach { t ->
                                ThemeRow(
                                    t.name, if (t.dynamic) wallpaperSummary() else liveSummary(t), t, settings.themeId == t.id,
                                    actions = {
                                        IconButton(onClick = { onEdit(t) }) {
                                            MsIcon(UiR.drawable.ms_edit, tr("Edit ${t.name}", "${t.name} এডিট করুন"))
                                        }
                                        IconButton(onClick = { vm.duplicateTheme(t) { } }) {
                                            MsIcon(UiR.drawable.ms_content_copy, tr("Duplicate ${t.name}", "${t.name}-এর কপি বানান"))
                                        }
                                        IconButton(onClick = { vm.exportTheme(t); exported = t.name }) {
                                            MsIcon(UiR.drawable.ms_upload, tr("Copy ${t.name} as text", "${t.name} লেখা হিসেবে কপি করুন"))
                                        }
                                        IconButton(onClick = { vm.deleteTheme(t, settings.themeId) }) {
                                            MsIcon(UiR.drawable.ms_delete, tr("Delete ${t.name}", "${t.name} মুছুন"))
                                        }
                                    },
                                ) { vm.selectTheme(t.id) }
                            }
                        }
                        exported?.let {
                            SettingsNote(
                                tr(
                                    "“$it” copied as text. Paste it anywhere to share or keep it. The background photo isn't included.",
                                    "“$it” লেখা হিসেবে কপি হয়েছে। শেয়ার করতে বা রেখে দিতে যেকোনো জায়গায় পেস্ট করুন। ব্যাকগ্রাউন্ডের ছবি এতে থাকে না।",
                                ),
                            )
                        }
                        SettingsGroup(tr("Import a theme", "থিম ইমপোর্ট করুন")) {
                            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = importText,
                                    onValueChange = { importText = it },
                                    placeholder = { Text(tr("Paste theme text", "থিমের লেখা পেস্ট করুন")) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4,
                                )
                                FilledTonalButton(onClick = {
                                    vm.importTheme(importText) { error ->
                                        importError = error
                                        imported = error == null
                                        if (error == null) importText = ""
                                    }
                                }, enabled = importText.isNotBlank()) {
                                    MsIcon(UiR.drawable.ms_download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tr("Import", "ইমপোর্ট"))
                                }
                                if (imported) {
                                    Text(
                                        tr("Imported and applied.", "ইমপোর্ট হয়ে চালু হয়েছে।"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                importError?.let {
                                    Text(
                                        tr(it, "থিমটি ইমপোর্ট করা যায়নি। পুরো লেখাটি ঠিকমতো পেস্ট হয়েছে কি না দেখুন।"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The kind filters, pinned above the gallery: All, each family, Festivals, Yours (with a count). */
@Composable
private fun ThemeFilters(filter: String?, yours: Int, onPick: (String?) -> Unit) {
    val chips = listOf<Pair<String?, String>>(null to tr("All", "সব")) +
        ThemeFamily.entries.map { it.name to it.galleryLabel } +
        listOf(FESTIVALS to tr("Festivals", "উৎসব"), YOURS to (tr("Yours", "আপনার") + if (yours > 0) " · ${num(yours)}" else ""))
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (key, label) ->
            FilterChip(
                selected = filter == key,
                onClick = { onPick(key) },
                label = { Text(label) },
                leadingIcon = if (filter == key) {
                    { MsIcon(UiR.drawable.ms_check, null, Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/** A group's heading in the gallery: its name, how many themes, and one short line about them. */
@Composable
private fun SectionHeader(title: String, count: Int, note: String?) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    num(count),
                    Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One theme in the gallery grid: a keyboard sketch in its colours with its name under it. The
 * theme in use gets an outline and a check. [onCustomise] adds a pencil that makes your own copy.
 */
@Composable
private fun ThemeCard(
    name: String,
    theme: KeyboardTheme,
    selected: Boolean,
    onCustomise: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    val outline by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "cardOutline",
    )
    Surface(
        selected = selected,
        onClick = onSelect,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(2.dp, outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 7.dp, end = 7.dp, top = 7.dp)) {
            Box {
                CardSketch(theme, Modifier.fillMaxWidth().aspectRatio(1.5f))
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(1.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        MsIcon(UiR.drawable.ms_check, tr("In use", "চালু আছে"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    Modifier.weight(1f).padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onCustomise != null) {
                    IconButton(onClick = onCustomise, modifier = Modifier.size(36.dp)) {
                        MsIcon(
                            UiR.drawable.ms_edit,
                            tr("Customise $name", "$name নিজের মতো সাজান"),
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A bigger keyboard sketch for the gallery cards: background, glows and three rows of keys. */
@Composable
private fun CardSketch(sketched: KeyboardTheme, modifier: Modifier) {
    val context = LocalContext.current
    val theme = remember(sketched, context) { KeyboardPalette.resolveDynamic(context, sketched) }
    val shape = RoundedCornerShape(12.dp)
    Canvas(modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        val bg = theme.backgroundEnd
        if (bg != null) {
            drawRect(Brush.verticalGradient(listOf(Color(theme.background), Color(bg))))
        } else {
            drawRect(Color(theme.background))
        }
        // Ambient glows, at rest (the sketches don't animate).
        theme.glows.forEach { g ->
            val center = Offset(g.x * size.width, g.y * size.height)
            val radius = (g.radius * size.width).coerceAtLeast(1f)
            drawCircle(Brush.radialGradient(listOf(Color(g.color), Color(g.color).copy(alpha = 0f)), center, radius), radius, center)
        }
        val pad = size.width * 0.07f
        val gap = size.width * 0.03f
        val rowHeight = (size.height - 2 * pad - 2 * gap) / 3
        val corner = CornerRadius((theme.keyRadiusDp.dp.toPx() / 2.5f).coerceAtMost(rowHeight / 2.5f))
        fun row(index: Int, units: List<Pair<Float, Int>>) {
            val total = units.sumOf { it.first.toDouble() }.toFloat()
            val unit = (size.width - 2 * pad - gap * (units.size - 1)) / total
            var x = pad
            val y = pad + index * (rowHeight + gap)
            units.forEach { (w, colour) ->
                drawRoundRect(Color(colour), Offset(x, y), Size(w * unit, rowHeight), corner)
                x += w * unit + gap
            }
        }
        val letter = 1f to theme.key
        row(0, List(6) { letter })
        row(1, List(6) { letter })
        row(2, listOf(1f to theme.functionKey, 3f to theme.key, 1f to theme.functionKey, 1f to theme.accent))
    }
}

private const val FESTIVALS = "FESTIVALS"
private const val YOURS = "YOURS"

/**
 * Colours to preview Chameleon themes in (null: Jolpai's own, the app you're in). A getter, so
 * the names follow the app's language.
 */
private val CHAMELEON_SAMPLES: List<Pair<Int?, String>>
    get() = listOf(
        null to tr("Jolpai", "জলপাই"),
        0xFF25D366.toInt() to tr("Green", "সবুজ"),
        0xFF1877F2.toInt() to tr("Blue", "নীল"),
        0xFF2AABEE.toInt() to tr("Sky", "আকাশি"),
        0xFF7C4DFF.toInt() to tr("Purple", "বেগুনি"),
        0xFFE1306C.toInt() to tr("Pink", "গোলাপি"),
        0xFFFF0033.toInt() to tr("Red", "লাল"),
        0xFFFF6D00.toInt() to tr("Orange", "কমলা"),
        0xFFFFC107.toInt() to tr("Yellow", "হলুদ"),
        0xFF9E9E9E.toInt() to tr("Grey", "ধূসর"),
    )

/** The kind of the theme being drawn ([id]), for the "In use" line: its family and type ("Dark · Soft dark"). */
private fun themeTypeLabel(id: String): String = when {
    id.startsWith(KeyboardTheme.CUSTOM_PREFIX) -> tr("Yours", "আপনার")
    BuiltInThemes.festivals.any { it.id == id } -> tr("Festivals", "উৎসব")
    else -> ThemeTypes.typeOf(id)?.let { "${it.family.galleryLabel} · ${it.galleryName}" } ?: tr("Basics · Essentials", "সাধারণ · প্রয়োজনীয়")
}

/** The gallery's filter names for the theme families (the module keeps English ones). */
private val ThemeFamily.galleryLabel: String
    get() = when (this) {
        ThemeFamily.BASICS -> tr("Basics", "সাধারণ")
        ThemeFamily.CHAMELEON -> tr("Chameleon", "ক্যামেলিয়ন")
        ThemeFamily.DARK -> tr("Dark", "গাঢ়")
        ThemeFamily.LIGHT -> tr("Light", "হালকা")
        ThemeFamily.AMBIENT -> tr("Ambient", "আলো-ছায়া")
        ThemeFamily.DYNAMIC -> tr("Dynamic", "চলমান")
    }

/** A theme type's name in the app's language; a type this list doesn't know keeps its own name. */
private val ThemeType.galleryName: String
    get() = when (name) {
        "Essentials" -> tr("Essentials", "প্রয়োজনীয়")
        "Tinted dark" -> tr("Tinted dark", "রঙিন গাঢ়")
        "Black & neon" -> tr("Black & neon", "কালো ও নিয়ন")
        "Tinted light" -> tr("Tinted light", "রঙিন হালকা")
        "Moving chameleon" -> tr("Moving chameleon", "চলমান ক্যামেলিয়ন")
        "Soft dark" -> tr("Soft dark", "নরম গাঢ়")
        "Cool dark" -> tr("Cool dark", "ঠান্ডা গাঢ়")
        "Classic dark" -> tr("Classic dark", "ক্লাসিক গাঢ়")
        "Jewel tones" -> tr("Jewel tones", "রত্নের রং")
        "Light & pastel" -> tr("Light & pastel", "হালকা ও প্যাস্টেল")
        "Bright & colourful" -> tr("Bright & colourful", "উজ্জ্বল ও রঙিন")
        "Gradient & glass" -> tr("Gradient & glass", "গ্রেডিয়েন্ট ও কাচ")
        "Glow" -> tr("Glow", "আভা")
        "Spotlight" -> tr("Spotlight", "স্পটলাইট")
        "Light glow" -> tr("Light glow", "হালকা আভা")
        "Key glow effects" -> tr("Key glow effects", "কী-এর আলোর ইফেক্ট")
        "Bouncy dark" -> tr("Bouncy dark", "তুলতুলে গাঢ়")
        "Bouncy light" -> tr("Bouncy light", "তুলতুলে হালকা")
        "Playful effects" -> tr("Playful effects", "মজার ইফেক্ট")
        "Glow + effects" -> tr("Glow + effects", "আভা + ইফেক্ট")
        "Time of day" -> tr("Time of day", "দিনের সময়")
        "Drifting glow" -> tr("Drifting glow", "ভাসমান আভা")
        else -> name
    }

/** The one line under a theme type, short and in the app's language (no full stop); unknown types keep their own. */
private val ThemeType.galleryNote: String?
    get() = when (name) {
        "Essentials" -> tr("Plain, wallpaper-coloured and high-contrast", "সাধারণ, ওয়ালপেপারের রঙে, আর স্পষ্ট কনট্রাস্টের")
        "Tinted dark" -> tr("Dark glows and glass in the app's colour", "অ্যাপের রঙে গাঢ় আভা ও কাচের মতো কী")
        "Black & neon" -> tr("Black keys; the app's colour in the outlines and Enter", "কালো কী; বর্ডার আর এন্টার অ্যাপের রঙে")
        "Tinted light" -> tr("Light, with a soft tint of the app's colour", "হালকা, অ্যাপের রঙের নরম ছোঁয়ায়")
        "Moving chameleon" -> tr("Moving glows or key effects in the app's colour", "অ্যাপের রঙে চলমান আভা বা কী চাপার ইফেক্ট")
        "Soft dark" -> tr("Gentle darks for low light", "কম আলোর জন্য চোখে আরামের গাঢ় রং")
        "Cool dark" -> tr("Blue and grey darks with pastel accents", "নীল ও ধূসর গাঢ় রং, সঙ্গে প্যাস্টেল অ্যাকসেন্ট")
        "Classic dark" -> tr("Warm and deep darks", "উষ্ণ ও গভীর গাঢ় রং")
        "Jewel tones" -> tr("Rich, bright darks", "ঝলমলে গাঢ় রং")
        "Light & pastel" -> tr("Soft light colours, easy on the eyes", "চোখে আরামের নরম, হালকা রং")
        "Bright & colourful" -> tr("Bright light colours", "ঝলমলে হালকা রং")
        "Gradient & glass" -> tr("Colour blends, see-through and glowing keys", "রঙের মিশেল, স্বচ্ছ ও জ্বলজ্বলে কী")
        "Glow" -> tr("Soft lights behind see-through keys", "স্বচ্ছ কী-এর পেছনে নরম আলো")
        "Spotlight" -> tr("Near-black, lit from one edge like a stage", "প্রায় কালো, মঞ্চের মতো এক পাশ থেকে আলো")
        "Light glow" -> tr("Pastel glows behind frosted keys, for daylight", "ঘষা কাচের মতো কী-এর পেছনে প্যাস্টেল আভা, দিনের আলোর জন্য")
        "Key glow effects" -> tr("The key you press lights up, ripples or glints. Try them above", "চাপা কী-টি জ্বলে ওঠে, ঢেউ তোলে বা ঝিলিক দেয়। ওপরের কীবোর্ডে চেষ্টা করে দেখুন")
        "Bouncy dark" -> tr("Soft rubber keys that squash and spring back", "নরম রাবারের কী, চাপলে দেবে যায়, আবার ফিরে আসে")
        "Bouncy light" -> tr("The same springy keys in light pastel colours", "একই তুলতুলে কী, হালকা প্যাস্টেল রঙে")
        "Playful effects" -> tr("Your letter, a heart or sparkles rise from the key", "চাপা কী থেকে অক্ষর, হার্ট বা ঝিকিমিকি ভেসে ওঠে")
        "Glow + effects" -> tr("Soft glows plus a key press effect", "নরম আভার সঙ্গে কী চাপার ইফেক্ট")
        "Time of day" -> tr("Colours follow the clock, changing when the keyboard opens", "রং ঘড়ি মেনে চলে; কীবোর্ড খুললে বদলায়")
        "Drifting glow" -> tr("Glows drift slowly; still with battery saver", "আভা ধীরে ধীরে সরে; ব্যাটারি সেভারে থেমে থাকে")
        else -> description
    }

/** The summary for a theme coloured by the wallpaper. */
private fun wallpaperSummary(): String = tr("Your wallpaper colours (Android 12+)", "ওয়ালপেপারের রং (Android ১২+)")

/** What a changing theme does, for its row in the gallery. */
private fun liveSummary(t: KeyboardTheme): String? = when (val live = t.live) {
    is LiveStyle.TimeOfDay -> when (t.id) {
        BuiltInThemes.NIGHT_SHIFT.id -> tr("Always dark; warmer, with less blue light, late at night", "সবসময় গাঢ়; গভীর রাতে উষ্ণ, নীল আলো কম")
        BuiltInThemes.TWILIGHT_CYCLE.id -> tr(
            "Always dark: rose dawn, indigo day, magenta evening, navy night",
            "সবসময় গাঢ়: গোলাপি ভোর, নীল দিন, বেগুনি সন্ধ্যা, গাঢ় নীল রাত",
        )
        else -> tr("Light by day, dark at night", "দিনে হালকা, রাতে গাঢ়")
    }
    is LiveStyle.Drift -> t.pressEffect?.let {
        tr("Drifting glow, and ${pressEffectSummary(it).replaceFirstChar(Char::lowercase)}", "ভাসমান আভা, আর ${pressEffectSummary(it)}")
    } ?: tr("Slowly drifting glow (still when animations are off)", "ধীরে ভেসে বেড়ানো আভা (অ্যানিমেশন বন্ধ থাকলে থেমে থাকে)")
    is LiveStyle.AppColour -> when {
        live.driftSeconds > 0 -> tr("Glows in the app's colour that slowly drift", "অ্যাপের রঙে ধীরে ভেসে বেড়ানো আভা")
        t.pressEffect != null -> tr(
            "In the app's colour; ${pressEffectSummary(t.pressEffect!!).replaceFirstChar(Char::lowercase)}",
            "অ্যাপের রঙে; ${pressEffectSummary(t.pressEffect!!)}",
        )
        else -> when (live.style) {
            AppTintStyle.GLOW -> tr("Glows in the colour of the app you're typing in", "আপনি যে অ্যাপে লিখছেন তার রঙে আভা")
            AppTintStyle.MIST -> tr("A deep wash of the app's colour", "অ্যাপের রঙে গাঢ় ছোপ")
            AppTintStyle.LIGHT -> tr("Light, tinted with the app's colour", "হালকা, অ্যাপের রঙের ছোঁয়ায়")
            AppTintStyle.BLACK -> tr("Pure black; the app's colour in the outlines and Enter", "পুরো কালো; বর্ডার ও এন্টার অ্যাপের রঙে")
            AppTintStyle.NEON -> tr("Bright outlines in the app's colour", "অ্যাপের রঙে উজ্জ্বল বর্ডার")
            AppTintStyle.SPOTLIGHT -> tr("Lit from the top by the app's colour", "ওপর থেকে অ্যাপের রঙের আলো")
            AppTintStyle.DUO -> tr("The app's colour and its opposite, as two glows", "অ্যাপের রং আর তার বিপরীত রং, দুটি আভায়")
            AppTintStyle.GLASS -> tr("Frosted keys over the app's colour", "অ্যাপের রঙের ওপর ঘষা কাচের মতো কী")
            AppTintStyle.PASTEL -> tr("Soft pastel keys in the app's colour", "অ্যাপের রঙে নরম প্যাস্টেল কী")
        }
    }
    null -> t.pressEffect?.let { pressEffectSummary(it) }
}

private fun pressEffectSummary(e: PressEffect): String = when (e) {
    PressEffect.INK -> tr("A soft wash of colour spreads from your fingertip", "আঙুলের ছোঁয়া থেকে নরম রং ছড়িয়ে পড়ে")
    PressEffect.BACKLIGHT -> tr("Keys light up from within, like a backlit keyboard", "ব্যাকলাইট কীবোর্ডের মতো কী ভেতর থেকে জ্বলে ওঠে")
    PressEffect.FLOAT -> tr("The letter you type floats up and fades", "টাইপ করা অক্ষর ভেসে উঠে মিলিয়ে যায়")
    PressEffect.HALO -> tr("A thin glowing outline lifts off each key", "প্রতিটি কী থেকে আলোর সরু রেখা উঠে যায়")
    PressEffect.TRAIL -> tr("A soft light trail joins the keys as you type", "টাইপ করার সময় কী-গুলোর মাঝে আলোর রেখা পড়ে")
    PressEffect.MOTES -> tr("A glowing mote or two drifts up from the key", "কী থেকে দু-একটি আলোর কণা ভেসে ওঠে")
    PressEffect.POP -> tr("Keys give a soft, springy bounce of light", "কী থেকে আলো নরমভাবে লাফিয়ে ওঠে")
    PressEffect.SHEEN -> tr("A glint of light sweeps across each key", "প্রতিটি কী-এর ওপর দিয়ে আলোর ঝিলিক বয়ে যায়")
    PressEffect.SPARKLE -> tr("Little stars twinkle at the key's edges", "কী-এর কিনারে ছোট তারা ঝিকমিক করে")
    PressEffect.DROPLET -> tr("Rings spread from your fingertip like a raindrop", "বৃষ্টির ফোঁটার মতো আঙুল থেকে বৃত্ত ছড়ায়")
    PressEffect.HEARTS -> tr("A small heart floats up from each key", "প্রতিটি কী থেকে ছোট হার্ট ভেসে ওঠে")
    PressEffect.UNDERLINE -> tr("A glowing line grows under each key", "প্রতিটি কী-এর নিচে আলোর দাগ ফুটে ওঠে")
    PressEffect.RUBBER -> tr("Soft rubber keys that squash and bounce back", "নরম রাবারের কী, চাপলে দেবে যায়, আবার ফিরে আসে")
    PressEffect.SQUISH -> tr("Soft keys that squish and settle with one gentle bounce", "নরম কী, চাপলে দেবে যায়, একবার আলতো লাফিয়ে থামে")
    PressEffect.JELLY -> tr("Jelly keys: a deep squash and a slow wobble", "জেলির মতো কী: গভীরভাবে দেবে যায়, ধীরে দোলে")
}

/** An effect's name in the app's language (the module keeps English ones). */
private val PressEffect.effectLabel: String
    get() = when (this) {
        PressEffect.INK -> tr("Ink", "কালি")
        PressEffect.BACKLIGHT -> tr("Backlight", "ব্যাকলাইট")
        PressEffect.FLOAT -> tr("Letter float", "ভেসে ওঠা অক্ষর")
        PressEffect.HALO -> tr("Halo", "আলোর বলয়")
        PressEffect.TRAIL -> tr("Light trail", "আলোর রেখা")
        PressEffect.MOTES -> tr("Motes", "আলোর কণা")
        PressEffect.POP -> tr("Pop", "পপ")
        PressEffect.SHEEN -> tr("Sheen", "ঝিলিক")
        PressEffect.SPARKLE -> tr("Sparkle", "ঝিকিমিকি")
        PressEffect.DROPLET -> tr("Droplet", "পানির ফোঁটা")
        PressEffect.HEARTS -> tr("Hearts", "হার্ট")
        PressEffect.UNDERLINE -> tr("Glow line", "আলোর দাগ")
        PressEffect.RUBBER -> tr("Rubber", "রাবার")
        PressEffect.SQUISH -> tr("Squish", "তুলতুলে")
        PressEffect.JELLY -> tr("Jelly", "জেলি")
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
        if (selected) MsIcon(UiR.drawable.ms_check_circle_fill, tr("In use", "চালু আছে"), tint = MaterialTheme.colorScheme.primary)
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
                MsIcon(UiR.drawable.ms_check, tr("In use", "চালু আছে"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
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
    // Set when a picked picture couldn't be used; worded when shown.
    var imageFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    /** The colours as they look right now (app colour, time of day, wallpaper applied), as plain colours. */
    fun stillColours(x: KeyboardTheme): KeyboardTheme = LiveThemes.at(KeyboardPalette.resolveDynamic(context, x), java.time.LocalTime.now())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickThemeBackground(uri) { path ->
            if (path != null) t = t.copy(backgroundImage = path) else imageFailed = true
        }
    }

    SettingsScaffold(
        tr("Edit theme", "থিম এডিট"),
        onDone,
        actions = { TextButton(onClick = { vm.saveTheme(t); onDone() }) { Text(tr("Save", "সেভ করুন")) } },
    ) {
        KeyboardPreview(t, settings)
        OutlinedTextField(
            value = t.name,
            onValueChange = { t = t.copy(name = it.take(40)) },
            label = { Text(tr("Name", "নাম")) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        LiveSettings(initial, t, onChange = { t = it }, stillColours = ::stillColours)

        // Where the colours come from when the theme sets them itself, or null when they're picked here.
        val colourSource = when {
            t.live is LiveStyle.AppColour -> tr("The colours come from the app you're typing in.", "রং আসে আপনি যে অ্যাপে লিখছেন সেখান থেকে।")
            t.live is LiveStyle.TimeOfDay -> tr("The colours follow the time of day.", "রং দিনের সময় মেনে বদলায়।")
            t.dynamic -> tr("The colours come from your wallpaper (Android 12+).", "রং আসে আপনার ওয়ালপেপার থেকে (Android ১২+)।")
            else -> null
        }
        if (colourSource != null) {
            SettingsGroup(tr("Colours", "রং")) {
                SettingsNote(
                    colourSource + " " + tr("Turn it off above to pick your own.", "নিজে রং বাছতে ওপরে এটি বন্ধ করুন।"),
                    Modifier.padding(vertical = 8.dp),
                )
            }
        } else SettingsGroup(tr("Colours", "রং")) {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorRow(tr("Background", "ব্যাকগ্রাউন্ড"), t.background) { t = t.copy(background = it) }
                OptionalColorRow(tr("Gradient bottom", "গ্রেডিয়েন্টের নিচের রং"), t.backgroundEnd) { t = t.copy(backgroundEnd = it) }
                ColorRow(tr("Letter keys", "অক্ষরের কী"), t.key) { t = t.copy(key = it) }
                ColorRow(tr("Pressed keys", "চাপ দেওয়া কী"), t.keyPressed) { t = t.copy(keyPressed = it) }
                ColorRow(tr("Other keys", "অন্য কী"), t.functionKey) { t = t.copy(functionKey = it) }
                ColorRow(tr("Key text", "কী-এর লেখা"), t.text) { t = t.copy(text = it) }
                ColorRow(tr("Small hints", "কী-এর ছোট চিহ্ন"), t.hint) { t = t.copy(hint = it) }
                ColorRow(tr("Accent (Enter, highlights)", "অ্যাকসেন্ট রং (এন্টার, হাইলাইট)"), t.accent) { t = t.copy(accent = it) }
                ColorRow(tr("Text on accent", "অ্যাকসেন্টের ওপরের লেখা"), t.onAccent) { t = t.copy(onAccent = it) }
                OptionalColorRow(tr("Key outline", "কী-এর বর্ডার"), t.border) { t = t.copy(border = it) }
            }
        }

        SettingsGroup(tr("Keys", "কী")) {
            Column(Modifier.padding(GroupContentPadding)) {
                LabeledSlider(tr("Key corners", "কী-এর কোণ"), t.keyRadiusDp, 0f..24f, "${num(t.keyRadiusDp.toInt())} dp") { t = t.copy(keyRadiusDp = it) }
                LabeledSlider(tr("Space between keys", "কী-গুলোর মাঝে ফাঁক"), t.keyGapDp, 0f..16f, "${num(t.keyGapDp.toInt())} dp") { t = t.copy(keyGapDp = it) }
                LabeledSlider(tr("Key opacity", "কী-এর ঘনত্ব"), t.keyOpacity, 0.2f..1f, "${num((t.keyOpacity * 100).toInt())} %") { t = t.copy(keyOpacity = it) }
                LabeledSlider(tr("Label size", "লেখার আকার"), t.labelScale, 0.7f..1.4f, "${num((t.labelScale * 100).toInt())} %") { t = t.copy(labelScale = it) }
            }
            SettingsSwitch(tr("Bold labels", "মোটা লেখা"), checked = t.boldLabels, onChange = { t = t.copy(boldLabels = it) }, icon = UiR.drawable.ms_format_size)
            SettingsSwitch(tr("Key shadows", "কী-এর ছায়া"), checked = t.shadow, onChange = { t = t.copy(shadow = it) }, icon = UiR.drawable.ms_tune)
            SettingsSwitch(
                tr("Dark theme", "গাঢ় থিম"),
                tr("For the panels and previews", "প্যানেল ও প্রিভিউয়ের জন্য"),
                t.dark,
                { t = t.copy(dark = it) },
                UiR.drawable.ms_dark_mode,
            )
        }

        SettingsGroup(tr("Key press effect", "কী চাপার ইফেক্ট")) {
            SettingsRadio(tr("None", "কিছু না"), selected = t.pressEffect == null) { t = t.copy(pressEffect = null) }
            PressEffect.entries.forEach { e ->
                SettingsRadio(e.effectLabel, selected = t.pressEffect == e) { t = t.copy(pressEffect = e) }
            }
        }
        SettingsNote(tr("Shown in the accent colour. Try it on the preview above.", "অ্যাকসেন্ট রঙে দেখায়। ওপরের প্রিভিউতে চেষ্টা করে দেখুন।"))

        SettingsGroup(tr("Background picture", "ব্যাকগ্রাউন্ডের ছবি")) {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        MsIcon(UiR.drawable.ms_image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (t.backgroundImage == null) tr("Choose picture", "ছবি বাছুন") else tr("Change picture", "ছবি বদলান"))
                    }
                    if (t.backgroundImage != null) OutlinedButton(onClick = { t = t.copy(backgroundImage = null) }) { Text(tr("Remove", "সরান")) }
                }
                if (t.backgroundImage != null) {
                    LabeledSlider(tr("Darken picture", "ছবি গাঢ় করা"), t.imageDim, 0f..0.9f, "${num((t.imageDim * 100).toInt())} %") { t = t.copy(imageDim = it) }
                }
                Text(
                    tr(
                        "Fitted to the keyboard from the centre. It stays on this phone.",
                        "কীবোর্ডের মাপে মাঝখান থেকে কাটা হয়। ছবিটি এই ফোনেই থাকে।",
                    ) + (if (imageFailed) "\n" + tr("That picture couldn't be used.", "এই ছবিটি ব্যবহার করা যায়নি।") else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveTheme(t); onDone() }) { Text(tr("Save and use", "সেভ করে চালু করুন")) }
            OutlinedButton(onClick = { t = initial }) { Text(tr("Reset", "রিসেট")) }
            TextButton(onClick = onDone) { Text(tr("Cancel", "বাতিল")) }
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

    SettingsGroup(tr("Changes by itself", "নিজে নিজে বদলায়")) {
        if (initial.dynamic) {
            SettingsSwitch(
                tr("Wallpaper colours", "ওয়ালপেপারের রং"),
                tr("Uses your wallpaper's colours (Android 12+)", "আপনার ওয়ালপেপারের রং নেয় (Android ১২+)"),
                checked = t.dynamic,
                onChange = { on -> onChange(if (on) t.copy(dynamic = true) else stillColours(t).copy(dynamic = false)) },
                icon = UiR.drawable.ms_auto_awesome,
            )
        }
        if (originalApp != null) {
            SettingsSwitch(
                tr("Colours from the app", "অ্যাপ থেকে রং"),
                tr("Takes the colour of the app you're typing in", "আপনি যে অ্যাপে লিখছেন তার রং নেয়"),
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
                tr("Time of day colours", "দিনের সময় অনুযায়ী রং"),
                tr("Change through the day, when the keyboard opens", "দিনভর বদলায়, কীবোর্ড খোলার সময়"),
                checked = t.live is LiveStyle.TimeOfDay,
                onChange = { on -> onChange(if (on) t.copy(live = originalTime) else stillColours(t).copy(live = null)) },
                icon = UiR.drawable.ms_dark_mode,
            )
        }
        if (canDrift && t.live !is LiveStyle.TimeOfDay) {
            val drifting = t.live is LiveStyle.Drift || (appColour?.driftSeconds ?: 0) > 0
            SettingsSwitch(
                tr("Glows drift slowly", "আভা ধীরে সরে"),
                tr("Still with animations off or battery saver on", "অ্যানিমেশন বন্ধ বা ব্যাটারি সেভার চালু থাকলে থেমে থাকে"),
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
        SettingsGroup(tr("Chameleon look", "ক্যামেলিয়নের ধরন")) {
            AppTintStyle.entries.forEach { style ->
                SettingsRadio(appTintLabel(style), selected = appColour.style == style) {
                    onChange(t.copy(live = appColour.copy(style = style), dark = style != AppTintStyle.LIGHT && style != AppTintStyle.PASTEL))
                }
            }
        }
    }
}

private fun appTintLabel(style: AppTintStyle): String = when (style) {
    AppTintStyle.GLOW -> tr("Glow: near-black with glows in the app's colour", "আভা: প্রায় কালো, অ্যাপের রঙে আভা")
    AppTintStyle.MIST -> tr("Mist: a deep wash of the app's colour", "কুয়াশা: অ্যাপের রঙে গাঢ় ছোপ")
    AppTintStyle.LIGHT -> tr("Light: a pale tint, white keys", "হালকা: হালকা রঙের ছোঁয়া, সাদা কী")
    AppTintStyle.BLACK -> tr("Black: pure black, coloured outlines and Enter", "কালো: পুরো কালো, রঙিন বর্ডার ও এন্টার")
    AppTintStyle.NEON -> tr("Neon: bright outlines in the app's colour", "নিয়ন: অ্যাপের রঙে উজ্জ্বল বর্ডার")
    AppTintStyle.SPOTLIGHT -> tr("Spotlight: lit from the top by the app's colour", "স্পটলাইট: ওপর থেকে অ্যাপের রঙের আলো")
    AppTintStyle.DUO -> tr("Duo: the app's colour and its opposite", "জোড়া: অ্যাপের রং ও তার বিপরীত রং")
    AppTintStyle.GLASS -> tr("Glass: frosted keys over the app's colour", "কাচ: অ্যাপের রঙের ওপর ঘষা কাচের মতো কী")
    AppTintStyle.PASTEL -> tr("Pastel: soft pastel keys", "প্যাস্টেল: নরম প্যাস্টেল কী")
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
    if (color != null) ColorRow(tr("$label colour", "রং বাছুন"), color) { onPick(it) }
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
    SettingsScaffold(tr("Size & layout", "আকার ও লেআউট"), onBack) {
        KeyboardPreview(theme, settings)
        SettingsGroup(tr("Height", "উচ্চতা")) {
            Column(Modifier.padding(GroupContentPadding)) {
                LabeledSlider(tr("Portrait", "খাড়া অবস্থায়"), settings.heightPortrait.toFloat(), 70f..140f, "${num(settings.heightPortrait)} %") {
                    vm.setHeight(portrait = true, percent = it.toInt())
                }
                LabeledSlider(tr("Landscape", "আড়াআড়ি অবস্থায়"), settings.heightLandscape.toFloat(), 70f..140f, "${num(settings.heightLandscape)} %") {
                    vm.setHeight(portrait = false, percent = it.toInt())
                }
            }
        }
        SettingsGroup(tr("Layout", "লেআউট")) {
            SettingsSwitch(
                tr("Number row", "সংখ্যার সারি"),
                tr("Digits above the letters", "অক্ষরের ওপরে সংখ্যা"),
                settings.numberRow,
                vm::setNumberRow,
                UiR.drawable.ms_123,
            )
            SettingsSwitch(
                tr("Floating keyboard", "ভাসমান কীবোর্ড"),
                tr("A small keyboard you can move; the app stays full size", "ছোট কীবোর্ড, যেখানে খুশি সরান; পেছনের অ্যাপ পুরো দেখা যায়"),
                settings.floating,
                vm::setFloating,
                UiR.drawable.ms_picture_in_picture,
            )
            if (settings.floating) {
                SettingsItem(tr("Reset position", "জায়গা রিসেট করুন"), null, UiR.drawable.ms_restart_alt, showChevron = false, onClick = vm::resetFloatPosition)
            }
        }
        SettingsGroup(tr("Hands", "হাত")) {
            SettingsChoice(
                tr("One-handed", "এক হাতে"),
                tr("Keys move to one side; the arrows beside them switch sides", "কী-গুলো এক পাশে সরে; পাশের তীর চেপে দিক বদলান"),
                listOf(
                    OneHanded.OFF to tr("Off", "বন্ধ"),
                    OneHanded.LEFT to tr("Left hand", "বাঁ হাত"),
                    OneHanded.RIGHT to tr("Right hand", "ডান হাত"),
                ),
                settings.oneHanded,
                vm::setOneHanded,
                UiR.drawable.ms_front_hand,
            )
            SettingsChoice(
                tr("Split keyboard", "ভাগ করা কীবোর্ড"),
                tr(
                    "Keys in two halves for both thumbs on wide screens. Not with one-handed or floating.",
                    "চওড়া স্ক্রিনে দুই বুড়ো আঙুলের জন্য কী দুই ভাগে। এক হাতে বা ভাসমান মোডে নয়।",
                ),
                com.yeamin.jolpai.core.settings.SplitKeys.entries.map { it to it.uiLabel },
                settings.splitKeys,
                vm::setSplitKeys,
                UiR.drawable.ms_swap_horiz,
            )
        }
    }
}


/** Vibration strength and key sounds. */
@Composable
fun SoundScreen(vm: MainViewModel, settings: KeyboardSettings, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember { com.yeamin.jolpai.ui.SoundPackPlayer(context) }
    androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
    /** A short preview of a sound style: letter, letter, space. */
    fun preview(pack: com.yeamin.jolpai.core.settings.SoundPack) {
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
    SettingsScaffold(tr("Sound & vibration", "শব্দ ও ভাইব্রেশন"), onBack) {
        SettingsGroup(tr("Vibration", "ভাইব্রেশন")) {
            SettingsChoice(
                tr("Vibrate on key press", "কী চাপলে ভাইব্রেশন"),
                tr(
                    "“${HapticLevel.SYSTEM.uiLabel}” follows your phone. The others need the phone's touch vibration on.",
                    "“${HapticLevel.SYSTEM.uiLabel}” ফোনের সেটিং মেনে চলে। বাকিগুলোর জন্য ফোনের টাচ ভাইব্রেশন চালু থাকতে হবে।",
                ),
                HapticLevel.entries.map { it to it.uiLabel },
                settings.haptics,
                vm::setHaptics,
                UiR.drawable.ms_vibration,
            )
        }
        SettingsGroup(tr("Sound", "শব্দ")) {
            SettingsSwitch(
                tr("Key sounds", "কী-এর শব্দ"),
                tr("Space, Enter and Backspace have their own sounds", "স্পেস, এন্টার ও ব্যাকস্পেসের আলাদা শব্দ"),
                settings.keySound,
                vm::setKeySound,
                UiR.drawable.ms_volume_up,
            )
            if (settings.keySound) {
                Column(Modifier.padding(GroupContentPadding)) {
                    LabeledSlider(tr("Volume", "ভলিউম"), settings.soundVolume, 0f..1f, "${num((settings.soundVolume * 100).toInt())} %") { vm.setSoundVolume(it) }
                }
                SettingsChoice(
                    tr("Sound style", "শব্দের ধরন"),
                    tr("Tap one to hear it. Nothing is downloaded.", "শুনতে একটিতে চাপুন। কিছুই ডাউনলোড হয় না।"),
                    com.yeamin.jolpai.core.settings.SoundPack.entries.map { it to it.uiLabel },
                    settings.soundPack,
                    { pack ->
                        vm.setSoundPack(pack)
                        preview(pack)
                    },
                    UiR.drawable.ms_tune,
                )
            }
        }
        SettingsNote(
            tr(
                "No sound while the phone is on silent, vibrate or Do Not Disturb.",
                "ফোন সাইলেন্ট, ভাইব্রেট বা ডু নট ডিস্টার্বে থাকলে কোনো শব্দ হয় না।",
            ),
        )
    }
}

