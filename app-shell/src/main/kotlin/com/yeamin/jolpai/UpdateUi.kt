package com.yeamin.jolpai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.yeamin.jolpai.ui.R as UiR

/**
 * "Check for updates" in About: a plain row while checking or offering an update, and an
 * animated card while an update downloads, is checked and installs.
 */
@Composable
fun UpdateSection(state: UpdateState, version: String) {
    when (state) {
        UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate, is UpdateState.Available ->
            UpdateRow(state, version)
        is UpdateState.Failed -> if (state.release == null) UpdateRow(state, version) else UpdateCard(state)
        else -> UpdateCard(state)
    }
}

@Composable
private fun UpdateRow(state: UpdateState, version: String) {
    val context = LocalContext.current
    SettingsItem(
        if (state is UpdateState.Available) "Update available: ${state.release.version}" else "Check for updates",
        when (state) {
            UpdateState.Checking -> "Checking GitHub…"
            is UpdateState.UpToDate -> "You have the latest version ($version)"
            is UpdateState.Available -> listOfNotNull(
                state.release.apkSize.takeIf { it > 0 }?.let(::megabytes),
                "downloads and installs right here",
            ).joinToString(" · ")
            is UpdateState.Failed -> state.message
            else -> "You have $version · checks GitHub only when you tap"
        },
        UiR.drawable.ms_system_update,
        showChevron = false,
        trailing = {
            when (state) {
                UpdateState.Checking -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                is UpdateState.Available -> FilledTonalButton(onClick = { AppUpdater.download(context, state.release) }) { Text("Update") }
                else -> Unit
            }
        },
    ) {
        if (state is UpdateState.Available) AppUpdater.download(context, state.release) else AppUpdater.check(context, version)
    }
}

/** The download / check / install card: a progress ring, what is happening, and what to do. */
@Composable
private fun UpdateCard(state: UpdateState) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val problem = state is UpdateState.Failed || state is UpdateState.SignedDifferently
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (problem) scheme.errorContainer else scheme.primaryContainer,
        contentColor = if (problem) scheme.onErrorContainer else scheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(state)
                Spacer(Modifier.width(16.dp))
                AnimatedContent(
                    targetState = titleOf(state),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "title",
                    modifier = Modifier.weight(1f),
                ) { title ->
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            detailOf(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColorAlpha(0.8f),
                        )
                    }
                }
            }
            val buttons = buttonsOf(state, context)
            if (buttons.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    buttons.forEachIndexed { i, (label, action) ->
                        if (i == buttons.lastIndex) {
                            FilledTonalButton(onClick = action, modifier = Modifier.padding(start = 8.dp)) { Text(label) }
                        } else {
                            TextButton(onClick = action) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun contentColorAlpha(alpha: Float): Color =
    androidx.compose.material3.LocalContentColor.current.copy(alpha = alpha)

/**
 * A 64dp ring. Downloading with a known size: the arc fills smoothly to the progress, with the
 * percentage inside. Otherwise a short arc spins around an icon for the step.
 */
@Composable
private fun ProgressRing(state: UpdateState) {
    val scheme = MaterialTheme.colorScheme
    val problem = state is UpdateState.Failed || state is UpdateState.SignedDifferently
    val arcColor = if (problem) scheme.error else scheme.primary
    val trackColor = arcColor.copy(alpha = 0.18f)
    val known = state is UpdateState.Downloading && state.total > 0
    val spinning = !known && (state is UpdateState.Downloading || state is UpdateState.Verifying || state is UpdateState.Installing)
    val target = when {
        known -> (state as UpdateState.Downloading).fraction
        state is UpdateState.NeedsPermission || problem -> 1f
        else -> 0f
    }
    val progress by animateFloatAsState(target, tween(320), label = "progress")
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    val pulse by spin.animateFloat(0.9f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")

    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(trackColor, 0f, 360f, useCenter = false, style = stroke)
            if (spinning) {
                drawArc(arcColor, angle - 90f, 100f, useCenter = false, style = stroke)
            } else if (progress > 0f) {
                drawArc(arcColor, -90f, 360f * progress, useCenter = false, style = stroke)
            }
        }
        AnimatedContent(
            targetState = ringCenterOf(state),
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.55f), initialScale = 0.6f)) togetherWith fadeOut(tween(120)) },
            label = "center",
        ) { center ->
            when (center) {
                RingCenter.PERCENT -> Text(
                    "${(progress * 100).toInt()}%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = arcColor,
                )
                else -> MsIcon(
                    center.icon,
                    null,
                    modifier = Modifier.size(26.dp).let { if (spinning) it.scale(pulse) else it },
                    tint = arcColor,
                )
            }
        }
    }
}

private enum class RingCenter(val icon: Int) {
    PERCENT(0),
    DOWNLOAD(UiR.drawable.ms_download),
    SHIELD(UiR.drawable.ms_shield),
    INSTALL(UiR.drawable.ms_system_update),
    LOCK(UiR.drawable.ms_lock),
    PROBLEM(UiR.drawable.ms_close),
}

private fun ringCenterOf(state: UpdateState): RingCenter = when (state) {
    is UpdateState.Downloading -> if (state.total > 0) RingCenter.PERCENT else RingCenter.DOWNLOAD
    is UpdateState.Verifying -> RingCenter.SHIELD
    is UpdateState.Installing -> RingCenter.INSTALL
    is UpdateState.NeedsPermission -> RingCenter.LOCK
    else -> RingCenter.PROBLEM
}

private fun titleOf(state: UpdateState): String = when (state) {
    is UpdateState.Downloading -> "Downloading Jolpai ${state.release.version}"
    is UpdateState.Verifying -> "Checking the download"
    is UpdateState.Installing -> "Installing Jolpai ${state.release.version}"
    is UpdateState.NeedsPermission -> "Allow Jolpai to install updates"
    is UpdateState.SignedDifferently -> "This copy can't be updated in place"
    is UpdateState.Failed -> "The update didn't finish"
    else -> ""
}

private fun detailOf(state: UpdateState): String = when (state) {
    is UpdateState.Downloading -> buildString {
        if (state.total > 0) append("${megabytes(state.downloaded, short = true)} of ${megabytes(state.total)}")
        else append(megabytes(state.downloaded))
        if (state.bytesPerSecond > 0) {
            append(" · ").append(speed(state.bytesPerSecond))
            if (state.total > state.downloaded) {
                append(" · ").append(timeLeft((state.total - state.downloaded) / state.bytesPerSecond))
            }
        }
    }
    is UpdateState.Verifying -> "Making sure it's the real Jolpai and wasn't changed on the way."
    is UpdateState.Installing -> "Tap Update in Android's prompt. Jolpai restarts as the new version."
    is UpdateState.NeedsPermission -> "Android asks once: turn on “Allow from this source” for Jolpai, then come back here."
    is UpdateState.SignedDifferently ->
        "Your Jolpai was signed with a different key (a developer build), so Android won't update it with the " +
            "official release. Back up in Backup & restore, uninstall Jolpai, then install the release and restore."
    is UpdateState.Failed -> state.message
    else -> ""
}

private fun buttonsOf(state: UpdateState, context: android.content.Context): List<Pair<String, () -> Unit>> = when (state) {
    is UpdateState.Downloading -> listOf("Cancel" to { AppUpdater.cancel() })
    is UpdateState.NeedsPermission -> listOf(
        "Not now" to { AppUpdater.reset() },
        "Open settings" to { AppUpdater.openInstallPermission(context) },
    )
    is UpdateState.SignedDifferently -> listOf(
        "Close" to { AppUpdater.reset() },
        "Release page" to { openLink(context, state.release.pageUrl) },
    )
    is UpdateState.Failed -> listOfNotNull(
        "Close" to { AppUpdater.reset() },
        state.release?.let { r -> "Try again" to { AppUpdater.download(context, r) } },
    )
    else -> emptyList()
}

private fun megabytes(bytes: Long, short: Boolean = false): String {
    val mb = bytes / 1_048_576.0
    return if (short) String.format(Locale.US, "%.1f", mb) else String.format(Locale.US, "%.1f MB", mb)
}

private fun speed(bytesPerSecond: Long): String = if (bytesPerSecond >= 1_048_576) {
    String.format(Locale.US, "%.1f MB/s", bytesPerSecond / 1_048_576.0)
} else {
    "${bytesPerSecond / 1024} KB/s"
}

private fun timeLeft(seconds: Long): String = when {
    seconds < 1 -> "almost done"
    seconds < 60 -> "$seconds s left"
    else -> "${(seconds + 59) / 60} min left"
}
