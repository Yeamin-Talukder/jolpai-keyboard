package com.yeamin.jolpai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.ui.R as UiR

/*
 * "Try the keyboard", one tap away on every page: a floating keyboard button at the bottom
 * right opens a panel with a text field that is already focused, so the keyboard comes up at
 * once and a change made on any settings page can be tried straight away.
 */

/** The floating keyboard button. [expanded] adds its label (on the home page). */
@Composable
fun TryKeyboardButton(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { MsIcon(UiR.drawable.ms_keyboard, if (expanded) null else tr("Try the keyboard", "কীবোর্ড চালিয়ে দেখুন")) },
        text = { Text(tr("Try keyboard", "টাইপ করে দেখুন")) },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    )
}

/** Kinds of field to try: each asks the keyboard for a different mode. */
private enum class TryFieldKind(
    val en: String,
    val bn: String,
    val options: KeyboardOptions,
    val singleLine: Boolean = true,
    val password: Boolean = false,
) {
    TEXT("Text", "লেখা", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = false),
    NUMBER("Number", "নম্বর", KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)),
    PHONE("Phone", "ফোন নম্বর", KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)),
    EMAIL("Email", "ইমেইল", KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)),
    PASSWORD("Password", "পাসওয়ার্ড", KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), password = true),
}

/**
 * The panel behind the floating button: slides up over a dimmed page and sits right above the
 * keyboard. Tapping outside, the close button or Back closes it (and the keyboard).
 */
@Composable
fun TryKeyboardSheet(
    visible: Boolean,
    keyboardReady: Boolean,
    onPickKeyboard: () -> Unit,
    onMoreFields: () -> Unit,
    onClose: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val close = {
        keyboard?.hide()
        focus.clearFocus(force = true)
        onClose()
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { close() } },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
        ) {
            // Composed only while open, so this registers last and wins over the pages' Back.
            BackHandler { close() }
            TryPanel(keyboardReady, onPickKeyboard, { close(); onMoreFields() }, close)
        }
    }
}

@Composable
private fun TryPanel(keyboardReady: Boolean, onPickKeyboard: () -> Unit, onMoreFields: () -> Unit, onClose: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(TryFieldKind.TEXT) }
    var texts by remember { mutableStateOf(mapOf<String, String>()) }
    val focusRequester = remember { FocusRequester() }
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            // Swallow taps so they don't reach the dimmed area behind and close the panel.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    UiR.drawable.ms_keyboard,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Try the keyboard", "কীবোর্ড চালিয়ে দেখুন"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        tr("Type anything to see how it feels", "যা খুশি লিখে দেখুন কেমন লাগে"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { MsIcon(UiR.drawable.ms_close, tr("Close", "বন্ধ করুন")) }
            }
            if (!keyboardReady) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.padding(end = 8.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Jolpai isn't your keyboard right now", "জলপাই এখন আপনার কীবোর্ড নয়"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onPickKeyboard) { Text(tr("Switch", "বদলান")) }
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TryFieldKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(tr(k.en, k.bn)) })
                }
            }
            // A new field for each kind, so the keyboard restarts in that kind's mode.
            key(kind) {
                val value = texts[kind.name].orEmpty()
                OutlinedTextField(
                    value = value,
                    onValueChange = { texts = texts + (kind.name to it) },
                    placeholder = {
                        Text(
                            when (kind) {
                                TryFieldKind.TEXT -> tr("Type here… e.g. ami tomake bhalobashi", "এখানে লিখুন… যেমন ami tomake bhalobashi")
                                TryFieldKind.NUMBER -> tr("Numbers only", "শুধু সংখ্যা")
                                TryFieldKind.PHONE -> tr("Phone number", "ফোন নম্বর")
                                TryFieldKind.EMAIL -> tr("name@example.com", "name@example.com")
                                TryFieldKind.PASSWORD -> tr("Nothing here is learned or saved", "এখানে লেখা কিছুই শেখা বা সেভ হয় না")
                            },
                        )
                    },
                    trailingIcon = if (value.isEmpty()) null else {
                        { IconButton(onClick = { texts = texts - kind.name }) { MsIcon(UiR.drawable.ms_backspace, tr("Clear", "মুছে ফেলুন")) } }
                    },
                    keyboardOptions = kind.options,
                    singleLine = kind.singleLine,
                    minLines = if (kind.singleLine) 1 else 3,
                    maxLines = if (kind.singleLine) 1 else 6,
                    visualTransformation = if (kind.password) PasswordVisualTransformation() else VisualTransformation.None,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
            // After switching keyboards in the chooser, focus comes back for the new keyboard.
            LaunchedEffect(keyboardReady) { if (keyboardReady) runCatching { focusRequester.requestFocus() } }
            TextButton(onClick = onMoreFields) { Text(tr("More kinds of fields", "আরও ধরনের ঘর")) }
        }
    }
}
