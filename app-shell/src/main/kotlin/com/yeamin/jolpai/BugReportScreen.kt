package com.yeamin.jolpai

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.ui.R as UiR

/**
 * Report a bug: the user describes it, sees exactly which phone and app details go with it,
 * and continues on GitHub, where the issue opens filled in (labelled "bug") for them to submit.
 * Nothing is sent from the app itself, so no GitHub key is ever stored in it.
 */
@Composable
fun BugReportScreen(settings: KeyboardSettings, setup: SetupStatus, onBack: () -> Unit) {
    val context = LocalContext.current
    val info = remember { deviceInfo(context) }
    val extras = remember { deviceExtras(context) }
    var summary by rememberSaveable { mutableStateOf("") }
    var what by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var includeDetails by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val details = BugReport.details(info, extras, settings, setup)
    val ready = summary.isNotBlank() || what.isNotBlank()

    SettingsScaffold("Report a bug", onBack) {
        Text(
            "Tell us what went wrong. Jolpai fills in a GitHub issue for you; you check it and tap Submit there (a free GitHub account is needed).",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Field(summary, { summary = it }, "Short summary", "e.g. ৎ doesn't type in WhatsApp", singleLine = true)
        Field(what, { what = it }, "What happened?", "What you did and what went wrong", minLines = 4)
        Field(steps, { steps = it }, "Steps to reproduce (optional)", "1. Open …  2. Tap …", minLines = 2)
        Field(expected, { expected = it }, "What did you expect? (optional)", null, minLines = 2)

        SettingsGroup {
            SettingsSwitch(
                "Include phone and app details",
                "Phone model, Android version, memory, screen, and your Jolpai settings. Never what you type, learned words or the clipboard.",
                includeDetails, { includeDetails = it }, UiR.drawable.ms_info,
            )
            if (includeDetails) {
                TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (showDetails) "Hide what's included" else "See exactly what's included")
                }
                AnimatedVisibility(showDetails) {
                    Column(Modifier.padding(start = 20.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((k, v) in details) {
                            Row {
                                Text("$k: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        SettingsNote("Don't include passwords or private messages: GitHub issues are public.")
        Button(
            onClick = {
                val text = BugReportText(summary, what, steps, expected)
                openLink(context, BugReport.issueUrl(text, details.takeIf { includeDetails }) { Uri.encode(it) })
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            MsIcon(UiR.drawable.ms_open_in_new, null)
            Text("  Continue on GitHub", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
