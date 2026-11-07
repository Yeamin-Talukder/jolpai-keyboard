package com.yeamin.jolpai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.stats.TypingStats
import com.yeamin.jolpai.core.stats.TypingStatsSnapshot
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * How much you type with Jolpai: words (English and Bangla), the last seven days, and what the
 * keyboard saved you. Only counts are kept — never words — and never from password or private
 * fields. Updates live while the keyboard is used.
 */
@Composable
fun TypingStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stats = remember { TypingStats(context) }
    var snap by remember { mutableStateOf(stats.snapshot()) }
    DisposableEffect(stats) {
        val l = stats.listen { snap = stats.snapshot() }
        onDispose { stats.stopListening(l) }
    }
    var confirmReset by remember { mutableStateOf(false) }
    val number = remember { NumberFormat.getIntegerInstance() }

    SettingsScaffold("Typing stats", onBack) {
        Hero(snap, number)
        SettingsGroup("Typing speed") { SpeedCard(snap, number) }
        SettingsGroup("Last 7 days") { WeekChart(snap) }
        SettingsGroup("Languages") { LanguageSplit(snap, number) }
        SettingsGroup("What Jolpai did for you", tone = GroupTone.TERTIARY) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Autocorrections", number.format(snap.autocorrections), Modifier.weight(1f))
                Tile("Suggestions used", number.format(snap.suggestionsPicked), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Letters saved", number.format(snap.lettersSaved), Modifier.weight(1f))
                Tile("Emoji sent", number.format(snap.emoji), Modifier.weight(1f))
            }
        }
        SettingsNote("Only numbers are kept on this phone — never what you type — and nothing is counted in password or private fields.")
        SettingsGroup {
            SettingsItem(
                if (confirmReset) "Tap again to reset" else "Reset stats",
                "Start counting from zero",
                com.yeamin.jolpai.ui.R.drawable.ms_restart_alt,
                showChevron = false,
            ) {
                if (confirmReset) {
                    stats.reset()
                    snap = stats.snapshot()
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            }
        }
    }
}

@Composable
private fun Hero(snap: TypingStatsSnapshot, number: NumberFormat) {
    val since = remember(snap.sinceEpochDay) {
        LocalDate.ofEpochDay(snap.sinceEpochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Text("Words typed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                number.format(snap.words),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "${number.format(snap.today)} today · counting since $since",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Average speed on a half-circle gauge (filling in with a spring), then today, best and
 * accuracy, and the week's speeds as a small line when there are at least two days to join.
 */
@Composable
private fun SpeedCard(snap: TypingStatsSnapshot, number: NumberFormat) {
    val wpm = snap.wpm
    if (wpm == null) {
        Text(
            "Type a few sentences and your speed shows up here. Only time spent typing counts — pauses don't.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    val top = maxOf(60, ((snap.bestWpm ?: wpm) + 19) / 20 * 20) // the gauge's full scale, a round number
    val fill = remember { Animatable(0f) }
    LaunchedEffect(wpm, top) { fill.animateTo(wpm.toFloat() / top, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow)) }
    val track = MaterialTheme.colorScheme.secondaryContainer
    val arc = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(220.dp).height(120.dp), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val stroke = 18.dp.toPx()
                val d = size.width - stroke
                val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                val arcSize = androidx.compose.ui.geometry.Size(d, d)
                val cap = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(track, 180f, 180f, false, topLeft, arcSize, style = cap)
                drawArc(arc, 180f, 180f * fill.value.coerceIn(0.01f, 1f), false, topLeft, arcSize, style = cap)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$wpm", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text("words per minute", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Today", snap.todayWpm?.let { "$it wpm" } ?: "—", Modifier.weight(1f))
            Tile("Best burst", snap.bestWpm?.let { "$it wpm" } ?: "—", Modifier.weight(1f))
            Tile("Accuracy", snap.accuracy?.let { "$it%" } ?: "—", Modifier.weight(1f))
        }
        if (snap.speedDays.count { it != null } >= 2) {
            Spacer(Modifier.height(14.dp))
            SpeedLine(snap.speedDays)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "From ${number.format(snap.typingMinutes)} min of typing. Accuracy is how many key presses weren't deleted again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The last seven days' speeds as a line with dots; days without typing are skipped. */
@Composable
private fun SpeedLine(days: List<Int?>) {
    val values = days.filterNotNull()
    val lo = (values.min() - 5).coerceAtLeast(0)
    val hi = values.max() + 5
    val line = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.surface
    val grow = remember { Animatable(0f) }
    LaunchedEffect(days) { grow.snapTo(0f); grow.animateTo(1f, spring(stiffness = Spring.StiffnessLow)) }
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val step = size.width / (days.size - 1)
        fun y(v: Int) = size.height - (v - lo).toFloat() / (hi - lo) * size.height
        val points = days.mapIndexedNotNull { i, v -> v?.let { androidx.compose.ui.geometry.Offset(i * step, y(it)) } }
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        clipRect(right = size.width * grow.value) {
            drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            points.forEach { p ->
                drawCircle(line, 5.dp.toPx(), p)
                drawCircle(dot, 2.5.dp.toPx(), p)
            }
        }
    }
}

/** Seven bars, today on the right; they grow in with a spring. */
@Composable
private fun WeekChart(snap: TypingStatsSnapshot) {
    val max = (snap.days.maxOrNull() ?: 0).coerceAtLeast(1)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        snap.days.forEachIndexed { i, count ->
            val grow = remember { Animatable(0f) }
            LaunchedEffect(count) { grow.animateTo(count.toFloat() / max, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
            val isToday = i == snap.days.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (count > 0) "$count" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .fillMaxHeight(grow.value.coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    today.minusDays((snap.days.lastIndex - i).toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** English and বাংলা as two parts of one rounded bar. */
@Composable
private fun LanguageSplit(snap: TypingStatsSnapshot, number: NumberFormat) {
    val total = (snap.englishWords + snap.banglaWords).coerceAtLeast(1)
    val bn = snap.banglaWords.toFloat() / total
    val shown = remember { Animatable(0f) }
    LaunchedEffect(bn) { shown.animateTo(bn, spring(stiffness = Spring.StiffnessLow)) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.secondaryContainer)) {
            if (shown.value > 0.001f) Box(Modifier.fillMaxHeight().weight(shown.value.coerceAtLeast(0.001f)).background(MaterialTheme.colorScheme.primary))
            if (shown.value < 0.999f) Spacer(Modifier.weight((1f - shown.value).coerceAtLeast(0.001f)))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Legend(MaterialTheme.colorScheme.primary, "বাংলা  ${number.format(snap.banglaWords)}")
            Spacer(Modifier.weight(1f))
            Legend(MaterialTheme.colorScheme.secondaryContainer, "English  ${number.format(snap.englishWords)}")
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
