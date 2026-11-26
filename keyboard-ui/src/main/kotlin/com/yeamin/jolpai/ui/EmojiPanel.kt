package com.yeamin.jolpai.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeamin.jolpai.clipboard.PanelColors
import com.yeamin.jolpai.emoji.Emoji
import com.yeamin.jolpai.emoji.EmojiData
import com.yeamin.jolpai.emoji.EmojiGroup
import com.yeamin.jolpai.emoji.Kaomoji
import com.yeamin.jolpai.media.Gif
import com.yeamin.jolpai.media.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the emoji panel asks the keyboard service to do. */
interface EmojiPanelActions {
    fun insert(text: String)
    fun toggleFavorite(emoji: String)
    fun setSkinTone(tone: Int)
    fun startSearch()
    fun clearSearch()
    fun backspace()
    fun close()

    /** Sends an image; returns an error message, or null when sent. */
    fun sendSticker(sticker: Sticker): String?
    suspend fun loadGifs(query: String): Result<List<Gif>>
    suspend fun gifPreview(gif: Gif): Bitmap?

    /** The moving preview (Android 9+), or null to keep the still one. */
    suspend fun gifAnimation(gif: Gif): android.graphics.drawable.Drawable? = null
    suspend fun sendGif(gif: Gif): String?

    /** GIFs sent lately, newest first (kept on this phone), for the Recent chip. */
    fun recentGifs(): List<Gif> = emptyList()
    fun openSettings(screen: String)

    /** Leaves search typing and shows every result in the full panel. */
    fun finishSearch() {}
}

/** Everything the emoji panel shows. */
class EmojiPanelData(
    val emoji: EmojiData?,
    val canDraw: (String) -> Boolean,
    val recents: List<String>,
    val favorites: List<String>,
    val stickers: List<Sticker>,
    val skinTone: Int,
    /** The GIF service in use ("KLIPY", "GIPHY"), or null when GIF search is off or unavailable. */
    val gifSource: String?,
    /** Open on this tab (a GIF or sticker shortcut), or null for the usual start. */
    val startTab: com.yeamin.jolpai.core.gesture.EmojiTab? = null,
)

/** The panel's four kinds of content, always one tap away in the bottom bar. */
private enum class Mode(val icon: String, val label: String) {
    EMOJI("😊", "Emoji"),
    GIFS("GIF", "GIFs"),
    STICKERS("🖼️", "Stickers"),
    KAOMOJI("(^_^)", "Kaomoji"),
}

/**
 * The kind of content shown last, so the panel opens where you left it (someone sending GIFs
 * gets GIFs straight away). Kept for as long as the keyboard runs.
 */
private var lastMode = Mode.EMOJI

/** A section of the emoji list: its category icon, title and emoji. */
private class EmojiSection(val key: String, val icon: String, val title: String, val emoji: List<String>)

/**
 * Emoji (with skin tones, recents, favourites and search in English and Bangla), GIFs, the
 * user's stickers and kaomoji, shown above the keys (the keys stay usable: pressing one closes
 * the panel and types).
 *
 * Layout, like the big keyboards: a search bar with backspace on top; the content; and a bottom
 * bar with the four kinds (Emoji, GIF, Stickers, Kaomoji) as big buttons, so GIFs are one tap
 * away. Emoji scroll as one list with a category row above it: tapping a category jumps to it,
 * and the row follows as you scroll. GIFs have quick topic chips (Trending, Recent, Haha,
 * Love …) so most GIFs need no typing. Emoji spring when tapped; long-press opens a floating
 * bar with skin tones and favourite.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPanel(data: EmojiPanelData, query: String, colors: PanelColors, actions: EmojiPanelActions) {
    var mode by remember(data.startTab) {
        mutableStateOf(
            when (data.startTab) {
                com.yeamin.jolpai.core.gesture.EmojiTab.GIFS -> Mode.GIFS
                com.yeamin.jolpai.core.gesture.EmojiTab.STICKERS -> Mode.STICKERS
                null -> lastMode
            },
        )
    }
    LaunchedEffect(mode) { lastMode = mode }
    var menuFor by remember { mutableStateOf<Emoji?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 6.dp)) {
        // Search bar + backspace (no search for kaomoji and stickers).
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            if (mode == Mode.EMOJI || mode == Mode.GIFS) {
                SearchPill(
                    hint = when {
                        mode == Mode.EMOJI -> "Search emoji · ইমোজি খুঁজুন"
                        data.gifSource == "KLIPY" -> "Search KLIPY"
                        else -> "Search GIFs"
                    },
                    query = query,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { actions.startSearch() },
                    onClear = { actions.clearSearch() },
                )
            } else {
                Text(
                    mode.label,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundButton(R.drawable.ms_backspace, "Delete", colors) { actions.backspace() }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            when (mode) {
                Mode.EMOJI -> EmojiPages(data, query, colors, actions) { menuFor = it }
                Mode.GIFS -> GifGrid(data.gifSource, query, colors, actions) { notice = it }
                Mode.STICKERS -> StickerGrid(data.stickers, colors, actions) { notice = it }
                Mode.KAOMOJI -> KaomojiList(colors, actions)
            }
            // Long-press: floating bar with skin tones and favourite.
            menuFor?.let { e ->
                // Pops in from the top of the grid.
                val shown = remember(e) { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(e) { shown.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
                Box(
                    Modifier.align(Alignment.TopCenter).graphicsLayer {
                        alpha = shown.value
                        translationY = (1f - shown.value) * -24f
                        scaleX = 0.9f + 0.1f * shown.value
                        scaleY = 0.9f + 0.1f * shown.value
                    },
                ) { EmojiMenu(e, data, colors, actions) { menuFor = null } }
            }
            // "Sending GIF…", "Couldn't send": a small pill over the bottom of the content.
            notice?.let {
                Text(
                    it,
                    color = colors.text,
                    fontSize = 13.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.popupSurface())
                        .border(1.dp, colors.hint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        // The four kinds, always visible.
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = colors.surface.alpha * 0.7f))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mode.entries.forEach { m ->
                ModeButton(m, selected = mode == m, colors, Modifier.weight(1f)) {
                    if (mode != m) {
                        actions.clearSearch()
                        menuFor = null
                        mode = m
                    }
                }
            }
        }
    }
}

/** One of the four kinds in the bottom bar: icon and name; the chosen one sits on the accent colour. */
@Composable
private fun ModeButton(mode: Mode, selected: Boolean, colors: PanelColors, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) colors.accent else Color.Transparent, label = "mode")
    val content = if (selected) colors.onAccent else colors.text
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(17.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textIcon = mode == Mode.GIFS || mode == Mode.KAOMOJI
        Text(
            mode.icon,
            color = content,
            fontSize = if (textIcon) 11.sp else 16.sp,
            fontWeight = if (textIcon) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        if (mode != Mode.GIFS) {
            Spacer(Modifier.width(5.dp))
            Text(mode.label, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Every emoji in one scrolling list (Recent, Favourites, then each category, each under its
 * name), with a category row above it: tap a category to jump there; the row follows the list.
 * A search shows its results instead.
 */
@Composable
private fun EmojiPages(data: EmojiPanelData, query: String, colors: PanelColors, actions: EmojiPanelActions, onLongPress: (Emoji?) -> Unit) {
    val emoji = data.emoji
    if (emoji == null) {
        Empty("Loading emoji…", colors)
        return
    }
    if (query.isNotBlank()) {
        val found = remember(query, data.skinTone) {
            emoji.search(query).filter { data.canDraw(it.value) }.map { it.withTone(data.skinTone) }
        }
        if (found.isEmpty()) {
            Empty("No emoji found for “$query”.", colors)
        } else {
            Column(Modifier.fillMaxSize()) {
                SectionTitle("Results for “$query”", colors)
                LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxSize()) {
                    items(found) { e -> EmojiCell(e, onClick = { actions.insert(e) }, onLongClick = { onLongPress(emoji.find(e)) }) }
                }
            }
        }
        return
    }

    val groups = remember(emoji, data.skinTone) {
        EmojiGroup.entries.map { g ->
            EmojiSection(g.name, g.icon, g.title, emoji.group(g).filter { data.canDraw(it.value) }.map { it.withTone(data.skinTone) })
        }
    }
    val sections = buildList {
        if (data.recents.isNotEmpty()) add(EmojiSection("recent", "🕘", "Recent", data.recents))
        if (data.favorites.isNotEmpty()) add(EmojiSection("favorites", "⭐", "Favourites", data.favorites))
        addAll(groups.filter { it.emoji.isNotEmpty() })
    }
    // Where each section's title sits in the list (a title, then its emoji).
    val starts = remember(sections.map { it.key to it.emoji.size }) {
        var index = 0
        sections.map { s -> index.also { index += 1 + s.emoji.size } }
    }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val current by remember(starts) {
        androidx.compose.runtime.derivedStateOf {
            val first = grid.firstVisibleItemIndex
            starts.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }
    val categories = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .horizontalScroll(categories),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEachIndexed { i, s ->
                CategoryTab(s.icon, i == current, colors) { scope.launch { grid.animateScrollToItem(starts[i]) } }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            sections.forEach { s ->
                item(key = "title/${s.key}", span = { GridItemSpan(maxLineSpan) }) { SectionTitle(s.title, colors) }
                items(s.emoji) { e ->
                    EmojiCell(e, onClick = { actions.insert(e) }, onLongClick = { onLongPress(emoji.find(e)) })
                }
            }
            if (data.favorites.isEmpty()) {
                item(key = "favorites-tip", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Tip: long-press an emoji and tap ☆ to keep it in Favourites.",
                        color = colors.hint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, colors: PanelColors) {
    Text(
        title,
        color = colors.hint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** One emoji: springs down a little while pressed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(e: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.78f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "emoji")
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(e, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
    }
}

/** A tab in the category bar: the chosen one sits on an accent pill that fades in. */
@Composable
private fun CategoryTab(label: String, selected: Boolean, colors: PanelColors, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) colors.accent else Color.Transparent, label = "tab")
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.5f), label = "tabScale")
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = if (label.length > 2) 10.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (label.length > 2) 12.sp else 18.sp,
            fontWeight = if (label.length > 2) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.onAccent else colors.text,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

/** A rounded search field look-alike: tapping it types the search with the keys. */
@Composable
internal fun SearchPill(hint: String, query: String, colors: PanelColors, modifier: Modifier, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(19.dp))
            .background(colors.surface)
            .border(1.dp, colors.hint.copy(alpha = 0.25f), RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ms_search), null, tint = colors.hint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            query.ifBlank { hint },
            color = if (query.isBlank()) colors.hint else colors.text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (query.isNotBlank()) {
            Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ms_close), "Clear search", tint = colors.hint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun RoundButton(icon: Int, description: String, colors: PanelColors, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(colors.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = colors.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmojiMenu(e: Emoji, data: EmojiPanelData, colors: PanelColors, actions: EmojiPanelActions, dismiss: () -> Unit) {
    val current = e.withTone(data.skinTone)
    val isFavorite = current in data.favorites || e.value in data.favorites
    Row(
        Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.popupSurface())
            .border(1.dp, colors.hint.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (e.hasTones) {
            (0..5).forEach { tone ->
                Text(
                    e.withTone(tone),
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (tone == data.skinTone) colors.accent.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable {
                            actions.setSkinTone(tone)
                            actions.insert(e.withTone(tone))
                            dismiss()
                        }
                        .padding(4.dp),
                )
            }
        } else {
            Text(current, fontSize = 26.sp, modifier = Modifier.padding(4.dp))
        }
        Text(
            if (isFavorite) "★" else "☆",
            color = colors.accent,
            fontSize = 22.sp,
            modifier = Modifier.clip(CircleShape).clickable { actions.toggleFavorite(current); dismiss() }.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        data.emoji?.banglaName(e)?.let { Text(it, color = colors.hint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        Box(Modifier.size(30.dp).clip(CircleShape).clickable { dismiss() }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ms_close), "Close", tint = colors.hint, modifier = Modifier.size(16.dp))
        }
    }
}

/** An opaque surface for floating bars (theme keys can be see-through). */
private fun PanelColors.popupSurface(): Color = Color(
    red = surface.red * 0.9f + background.red * 0.1f,
    green = surface.green * 0.9f + background.green * 0.1f,
    blue = surface.blue * 0.9f + background.blue * 0.1f,
    alpha = 1f,
)

@Composable
private fun KaomojiList(colors: PanelColors, actions: EmojiPanelActions) {
    LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.fillMaxSize()) {
        Kaomoji.groups.forEach { (title, faces) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(title, color = colors.hint, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
            }
            items(faces) { face ->
                Text(
                    face,
                    color = colors.text,
                    fontSize = 15.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(3.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                        .clickable { actions.insert(face) }.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StickerGrid(stickers: List<Sticker>, colors: PanelColors, actions: EmojiPanelActions, notify: (String) -> Unit) {
    if (stickers.isEmpty()) {
        Empty("No stickers yet. Add your own pictures as stickers in the Jolpai app (Emoji & GIFs).", colors) {
            actions.openSettings("stickers")
        }
        return
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.fillMaxSize()) {
        items(stickers, key = { it.file.name }) { s ->
            val bitmap by produceState<Bitmap?>(null, s.file) {
                value = withContext(Dispatchers.IO) { thumbnail(s.file.absolutePath, 160) }
            }
            Box(
                Modifier.padding(3.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(colors.surface)
                    .clickable { actions.sendSticker(s)?.let(notify) },
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let { Image(it.asImageBitmap(), "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)) }
            }
        }
    }
}

/** Quick GIF topics: one tap shows them, no typing. The words are what is searched. */
private val GIF_TOPICS = listOf(
    "😂" to "Haha", "❤️" to "Love", "👋" to "Hello", "🙏" to "Thank you", "👍" to "OK",
    "😢" to "Sad", "😮" to "Wow", "🎉" to "Congratulations", "🎂" to "Happy birthday",
    "🌙" to "Eid Mubarak", "☀️" to "Good morning", "😴" to "Good night", "😡" to "Angry",
    "💃" to "Dance", "👏" to "Clap", "🤦" to "Facepalm", "🙄" to "Eye roll", "🤗" to "Hug",
    "😘" to "Kiss", "👀" to "Waiting", "🥳" to "Party", "😎" to "Cool", "🤔" to "Thinking", "👋🏻" to "Bye",
)

private const val TRENDING = ""
private const val RECENT = "\u0000recent"

/**
 * GIFs: a row of quick topics (Trending, your recently sent GIFs, Haha, Love, Hello …), then
 * the GIFs in two columns at their own shape, moving. Typing in the search box searches
 * instead; the search waits for a short pause in typing, so each letter doesn't cost a request.
 */
@Composable
private fun GifGrid(source: String?, query: String, colors: PanelColors, actions: EmojiPanelActions, notify: (String) -> Unit) {
    if (source == null) {
        Empty("GIF search is off. Tap to turn it on in the Jolpai app (Emoji, GIFs & stickers).", colors) { actions.openSettings("gifs") }
        return
    }
    val recents = remember { actions.recentGifs() }
    var topic by remember { mutableStateOf(TRENDING) }
    val typed = query.trim()
    var result by remember { mutableStateOf<Result<List<Gif>>?>(null) }
    LaunchedEffect(typed, topic) {
        result = null
        result = when {
            typed.isNotEmpty() -> {
                delay(450) // wait for a pause in typing
                actions.loadGifs(typed)
            }
            topic == RECENT -> Result.success(actions.recentGifs())
            else -> actions.loadGifs(topic)
        }
    }
    val scope = rememberCoroutineScope()
    val grid = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    LaunchedEffect(typed, topic) { grid.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val chips = buildList {
                add(TRENDING to "🔥 Trending")
                if (recents.isNotEmpty()) add(RECENT to "🕘 Recent")
                GIF_TOPICS.forEach { (icon, word) -> add(word to "$icon $word") }
            }
            chips.forEach { (value, label) ->
                GifTopicChip(label, selected = typed.isEmpty() && topic == value, colors) {
                    if (typed.isNotEmpty()) actions.clearSearch()
                    topic = value
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    typed.isNotEmpty() -> "“$typed”"
                    topic == TRENDING -> "Trending"
                    topic == RECENT -> "You sent recently"
                    else -> topic
                },
                color = colors.hint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (topic != RECENT || typed.isNotEmpty()) Text("Powered by $source", color = colors.hint, fontSize = 10.sp)
        }
        val r = result
        when {
            r == null -> Empty("Loading GIFs…", colors)
            r.isFailure -> Empty(r.exceptionOrNull()?.message ?: "Couldn't load GIFs. Check your internet connection.", colors)
            r.getOrThrow().isEmpty() -> Empty(if (topic == RECENT && typed.isEmpty()) "GIFs you send appear here." else "No GIFs found.", colors)
            else -> androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Adaptive(140.dp),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                verticalItemSpacing = 4.dp,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(r.getOrThrow(), key = { it.id }) { gif ->
                    val preview by produceState<Bitmap?>(null, gif.id) { value = actions.gifPreview(gif) }
                    val moving by produceState<android.graphics.drawable.Drawable?>(null, gif.id) { value = actions.gifAnimation(gif) }
                    // The GIF's own shape (square when unknown), kept between 1:2 and 2:1.
                    val ratio = if (gif.width > 0 && gif.height > 0) (gif.width.toFloat() / gif.height).coerceIn(0.5f, 2f) else 1f
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                            .clickable {
                                notify("Sending GIF…")
                                scope.launch { notify(actions.sendGif(gif) ?: "Sent") }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val anim = moving
                        if (anim != null) {
                            MovingGif(anim, gif.title)
                        } else {
                            preview?.let { Image(it.asImageBitmap(), gif.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                ?: Text("GIF", color = colors.hint)
                        }
                    }
                }
            }
        }
    }
}

/** A quick-topic chip above the GIFs; the chosen one is filled with the accent colour. */
@Composable
private fun GifTopicChip(label: String, selected: Boolean, colors: PanelColors, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) colors.accent else colors.surface, label = "gifChip")
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) colors.onAccent else colors.text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Empty(message: String, colors: PanelColors, action: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = colors.hint, fontSize = 14.sp, textAlign = TextAlign.Center)
        action?.let {
            Text(
                "Open Jolpai settings",
                color = colors.onAccent,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(colors.accent)
                    .clickable(onClick = it).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Decodes an image file scaled down to roughly [size] pixels. */
internal fun thumbnail(path: String, size: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/**
 * A moving GIF preview (an animated WebP from the GIF service, Android 9+), cropped to fill its
 * cell. It plays while on screen and stops when scrolled away or the panel closes.
 */
@Composable
private fun MovingGif(drawable: android.graphics.drawable.Drawable, description: String) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.widget.ImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                contentDescription = description
            }
        },
        update = { view ->
            if (view.drawable !== drawable) view.setImageDrawable(drawable)
            (drawable as? android.graphics.drawable.Animatable)?.start()
        },
        onRelease = { view -> (view.drawable as? android.graphics.drawable.Animatable)?.stop() },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Shown above the keys while they type an emoji or GIF search (the panel steps aside so the
 * keys can type): back to the panel, the search box with the typed words, a search button that
 * shows every result in the panel, and a row of large live results under it (emoji as you type,
 * GIFs after a short pause). Tap a result to insert or send it.
 */
@Composable
fun EmojiSearchBar(data: EmojiPanelData, query: String, colors: PanelColors, actions: EmojiPanelActions) {
    val gifs = lastMode == Mode.GIFS && data.gifSource != null
    val typed = query.trim()
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(R.drawable.ms_arrow_back, "Back to the panel", colors) { actions.clearSearch() }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(1.5.dp, colors.accent, RoundedCornerShape(20.dp))
                    .padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ms_search), null, tint = colors.hint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    typed.ifEmpty {
                        when {
                            !gifs -> "Type to search emoji · ইমোজি খুঁজুন"
                            data.gifSource == "KLIPY" -> "Search KLIPY"
                            else -> "Search GIFs"
                        }
                    },
                    color = if (typed.isEmpty()) colors.hint else colors.text,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // A blinking caret, so it is clear the keys type here.
                val blink = androidx.compose.animation.core.rememberInfiniteTransition(label = "caret")
                val caret by blink.animateFloat(
                    1f, 0f,
                    androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(530, easing = androidx.compose.animation.core.LinearEasing),
                        androidx.compose.animation.core.RepeatMode.Reverse,
                    ),
                    label = "caretAlpha",
                )
                Box(Modifier.padding(start = 2.dp).width(2.dp).height(20.dp).graphicsLayer { alpha = caret }.background(colors.accent))
            }
            Spacer(Modifier.width(8.dp))
            RoundButton(R.drawable.ms_search, "Show all results", colors) { actions.finishSearch() }
        }
        Box(Modifier.fillMaxWidth().height(if (gifs) 104.dp else 56.dp).padding(top = 6.dp)) {
            if (gifs) {
                GifSearchResults(typed, colors, actions) { notice = it }
            } else {
                val found = remember(typed, data.emoji, data.skinTone) {
                    if (typed.isEmpty()) {
                        emptyList()
                    } else {
                        data.emoji?.search(typed).orEmpty().filter { data.canDraw(it.value) }.map { it.withTone(data.skinTone) }
                    }
                }
                when {
                    typed.isEmpty() -> SearchHint("Type a word in English or Bangla: happy, হাসি, love…", colors)
                    found.isEmpty() -> SearchHint("No emoji for “$typed” yet. Keep typing or try another word.", colors)
                    else -> androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        items(found.size) { i ->
                            Box(Modifier.size(48.dp)) { EmojiCell(found[i], onClick = { actions.insert(found[i]) }, onLongClick = {}) }
                        }
                    }
                }
            }
            notice?.let {
                Text(
                    it,
                    color = colors.text,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.popupSurface())
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchHint(text: String, colors: PanelColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Text(text, color = colors.hint, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/** GIFs for the words typed so far, in one moving row; fetched after a short pause in typing. */
@Composable
private fun GifSearchResults(typed: String, colors: PanelColors, actions: EmojiPanelActions, notify: (String) -> Unit) {
    var result by remember { mutableStateOf<Result<List<Gif>>?>(null) }
    LaunchedEffect(typed) {
        result = null
        if (typed.isEmpty()) return@LaunchedEffect
        delay(500) // wait for a pause in typing
        result = actions.loadGifs(typed)
    }
    val scope = rememberCoroutineScope()
    val r = result
    when {
        typed.isEmpty() -> SearchHint("Type what the GIF is about: haha, love, eid mubarak…", colors)
        r == null -> SearchHint("Searching GIFs…", colors)
        r.isFailure -> SearchHint(r.exceptionOrNull()?.message ?: "Couldn't load GIFs. Check your internet connection.", colors)
        r.getOrThrow().isEmpty() -> SearchHint("No GIFs for “$typed”.", colors)
        else -> androidx.compose.foundation.lazy.LazyRow(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val list = r.getOrThrow()
            items(list.size) { i ->
                val gif = list[i]
                val preview by produceState<Bitmap?>(null, gif.id) { value = actions.gifPreview(gif) }
                val moving by produceState<android.graphics.drawable.Drawable?>(null, gif.id) { value = actions.gifAnimation(gif) }
                val ratio = if (gif.width > 0 && gif.height > 0) (gif.width.toFloat() / gif.height).coerceIn(0.6f, 1.8f) else 1f
                Box(
                    Modifier.fillMaxHeight().aspectRatio(ratio).clip(RoundedCornerShape(10.dp)).background(colors.surface)
                        .clickable {
                            notify("Sending GIF…")
                            scope.launch { notify(actions.sendGif(gif) ?: "Sent") }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val anim = moving
                    if (anim != null) {
                        MovingGif(anim, gif.title)
                    } else {
                        preview?.let { Image(it.asImageBitmap(), gif.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            ?: Text("GIF", color = colors.hint)
                    }
                }
            }
        }
    }
}
