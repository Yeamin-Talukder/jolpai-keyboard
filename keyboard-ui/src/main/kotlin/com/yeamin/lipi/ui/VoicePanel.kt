package com.yeamin.lipi.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeamin.lipi.clipboard.PanelColors
import com.yeamin.lipi.voice.VoiceAvailability
import com.yeamin.lipi.voice.VoiceState

interface VoicePanelActions {
    fun toggleListening()
    fun switchLanguage()
    fun deleteLastWord()
    fun close()

    /** Shows Android's microphone dialog over the current app (no settings page). */
    fun requestPermission()

    /** Android's app settings: only when the user blocked the dialog for good. */
    fun openPermissionSettings()
}

/**
 * Voice typing in place of the keys, like Google voice typing: everything happens here.
 * Language chip on top; the words as you speak and a pulsing microphone in the middle; the
 * keyboard (back to keys) and backspace at the bottom. The first time, Android's own
 * "Allow microphone?" dialog appears over the app; no other page opens.
 */
@Composable
fun VoicePanel(
    state: VoiceState,
    languageTag: String,
    availability: VoiceAvailability?,
    permissionBlocked: Boolean,
    colors: PanelColors,
    actions: VoicePanelActions,
) {
    val bangla = languageTag.startsWith("bn")
    val listening = state is VoiceState.Listening || state is VoiceState.Starting

    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Top: language (tap to switch) and how it will work.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface)
                    .clickable(onClickLabel = "Switch voice language") { actions.switchLanguage() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ms_language), null, Modifier.size(18.dp), tint = colors.text)
                Spacer(Modifier.width(6.dp))
                Text(if (bangla) "বাংলা" else "English", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Text(if (bangla) "· EN" else "· বাংলা", color = colors.hint, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            availability?.let { Text(it.label, color = colors.hint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }

        // Middle: what you said (or what to do), and the microphone.
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val (message, strong) = when (state) {
                VoiceState.Idle -> "Tap to speak" to false
                VoiceState.Starting -> "Speak now" to false
                is VoiceState.Listening -> (state.partial.ifEmpty { "Speak now" }) to state.partial.isNotEmpty()
                VoiceState.NoPermission ->
                    (if (permissionBlocked) "Microphone is blocked for Jolpai. Allow it in Android settings." else "Allow the microphone to type with your voice") to false
                VoiceState.Unavailable -> "This phone has no speech recognition. Turn on Google's speech services." to false
                is VoiceState.Error -> state.message to false
            }
            Text(
                message,
                color = if (strong) colors.text else colors.hint,
                fontSize = if (strong) 20.sp else 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            )

            if (state == VoiceState.NoPermission) {
                Text(
                    if (permissionBlocked) "Open settings" else "Allow microphone",
                    color = colors.onAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent)
                        .clickable { if (permissionBlocked) actions.openPermissionSettings() else actions.requestPermission() }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                )
            } else {
                MicButton(listening, (state as? VoiceState.Listening)?.level ?: 0f, colors) { actions.toggleListening() }
            }
        }

        // Bottom: back to keys, spoken punctuation hint, delete last word.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(R.drawable.ms_keyboard, "Back to the keys", colors) { actions.close() }
            Text(
                if (bangla) "বলুন “কমা”, “দাঁড়ি”, “নতুন লাইন”" else "Say “comma”, “full stop”, “new line”",
                color = colors.hint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            RoundIcon(R.drawable.ms_backspace, "Delete last word", colors) { actions.deleteLastWord() }
        }
    }
}

/** The big microphone: pulses gently while listening and grows with your voice. */
@Composable
private fun MicButton(listening: Boolean, level: Float, colors: PanelColors, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ring",
    )
    val voice by animateFloatAsState(if (listening) 1f + level.coerceIn(0f, 1f) * 0.45f else 1f, tween(120), label = "level")
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            // Soft rings behind the button: one breathing, one following the voice level.
            Box(Modifier.size(84.dp).scale(voice * 1.25f).clip(CircleShape).background(colors.accent.copy(alpha = 0.16f)))
            Box(Modifier.size(84.dp).scale(pulse).clip(CircleShape).background(colors.accent.copy(alpha = 0.22f)))
        }
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (listening) colors.accent else colors.surface)
                .semantics {
                    contentDescription = if (listening) "Stop listening" else "Start listening"
                    role = Role.Button
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ms_mic),
                null,
                Modifier.size(36.dp),
                tint = if (listening) colors.onAccent else colors.text,
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: Int, description: String, colors: PanelColors, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = colors.text)
    }
}
