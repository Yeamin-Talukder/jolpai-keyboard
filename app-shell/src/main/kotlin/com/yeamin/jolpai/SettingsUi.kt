package com.yeamin.jolpai

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.ui.R as UiR

/*
 * Jolpai's settings design system: Material 3 with Material Symbols (Rounded) icons, the same
 * icon files the keyboard draws (keyboard-ui/res/drawable/ms_*.xml).
 *
 * Every screen is a SettingsScaffold (large collapsing title, round back button) holding
 * SettingsGroups: rounded cards of rows, each row led by a tinted icon badge.
 */

/** A Material Symbol from keyboard-ui's drawables. */
@Composable
fun MsIcon(@DrawableRes res: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    Icon(painterResource(res), contentDescription, modifier, tint)

/**
 * A settings screen: large title that collapses as you scroll, back arrow, and [content]
 * in a column with the standard side margins. [scrollable] false lets the content hold its
 * own lazy list (give that list `Modifier.weight(1f)`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { MsIcon(UiR.drawable.ms_arrow_back, "Back") }
                    }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .imePadding()
                .fillMaxSize()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
            if (scrollable) Spacer(Modifier.height(24.dp))
        }
    }
}

/** A titled group of rows on one rounded card. */
@Composable
fun SettingsGroup(title: String? = null, tone: GroupTone = GroupTone.SECONDARY, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalGroupTone provides tone) { GroupBody(title, content) }
}

@Composable
private fun GroupBody(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

/** Colour family for a group's icon badges, so sections are easy to tell apart. */
enum class GroupTone { PRIMARY, SECONDARY, TERTIARY }

val LocalGroupTone = compositionLocalOf { GroupTone.SECONDARY }

@Composable
private fun toneColors(tone: GroupTone): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (tone) {
        GroupTone.PRIMARY -> primaryContainer to onPrimaryContainer
        GroupTone.SECONDARY -> secondaryContainer to onSecondaryContainer
        GroupTone.TERTIARY -> tertiaryContainer to onTertiaryContainer
    }
}

/** A gentle spring: rows sink a little while pressed and settle back. */
@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return scale
}

/** The icon in a tinted circle that leads each row. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    container: Color = toneColors(LocalGroupTone.current).first,
    tint: Color = toneColors(LocalGroupTone.current).second,
    size: Int = 40,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        MsIcon(icon, null, Modifier.size((size * 0.55f).dp), tint)
    }
}

/** Row layout shared by every item: badge, title + summary, trailing control. */
@Composable
private fun RowBody(
    @DrawableRes icon: Int?,
    title: String,
    summary: String?,
    enabled: Boolean,
    modifier: Modifier,
    trailing: @Composable (() -> Unit)?,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
            if (!summary.isNullOrEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A row that opens something: icon badge, title, summary and a chevron. */
@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    RowBody(
        icon, title, summary, enabled,
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = onClick),
        trailing ?: if (showChevron) {
            { MsIcon(UiR.drawable.ms_chevron_right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            null
        },
    )
}

/** An on/off setting: the whole row toggles. */
@Composable
fun SettingsSwitch(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    RowBody(
        icon, title, summary, enabled,
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .toggleable(value = checked, interactionSource = source, indication = ripple(), enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
}

/** One choice of several: the whole row selects. */
@Composable
fun SettingsRadio(title: String, summary: String? = null, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Explanatory text under a group or at the top of a screen. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) =
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

/** Padding for text and controls placed directly inside a [SettingsGroup]. */
val GroupContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
