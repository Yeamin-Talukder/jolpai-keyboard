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

    SettingsScaffold(tr("Report a problem", "সমস্যা জানান"), onBack) {
        Text(
            tr(
                "Tell us what went wrong. You then check it and submit it on GitHub (a free account is needed).",
                "কী সমস্যা হয়েছে লিখুন। এরপর GitHub-এ দেখে নিয়ে পাঠাবেন (একটি ফ্রি GitHub অ্যাকাউন্ট লাগবে)।",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Field(
            summary, { summary = it },
            tr("Short summary", "এক লাইনে সমস্যাটি"),
            tr("e.g. ৎ doesn't type in WhatsApp", "যেমন: WhatsApp-এ ৎ টাইপ হয় না"),
            singleLine = true,
        )
        Field(
            what, { what = it },
            tr("What happened?", "কী হয়েছিল?"),
            tr("What you did and what went wrong", "আপনি কী করছিলেন, আর কী ভুল হলো"),
            minLines = 4,
        )
        Field(
            steps, { steps = it },
            tr("Steps to make it happen (optional)", "কোন ধাপে সমস্যা হয় (ঐচ্ছিক)"),
            tr("1. Open …  2. Tap …", "১. খুলুন …  ২. চাপুন …"),
            minLines = 2,
        )
        Field(expected, { expected = it }, tr("What did you expect? (optional)", "আপনি কী আশা করেছিলেন? (ঐচ্ছিক)"), null, minLines = 2)

        SettingsGroup {
            SettingsSwitch(
                tr("Include phone and app details", "ফোন ও অ্যাপের তথ্য যোগ করুন"),
                tr(
                    "Phone model, Android version and your settings. Never your typing, learned words or clipboard.",
                    "ফোনের মডেল, Android ভার্সন ও আপনার সেটিংস। আপনার লেখা, শেখা শব্দ বা ক্লিপবোর্ড কখনো নয়।",
                ),
                includeDetails, { includeDetails = it }, UiR.drawable.ms_info,
            )
            if (includeDetails) {
                TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        if (showDetails) tr("Hide what's included", "কী কী যাচ্ছে লুকান")
                        else tr("See exactly what's included", "ঠিক কী কী যাচ্ছে দেখুন"),
                    )
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
        SettingsNote(
            tr(
                "Don't include passwords or private messages: GitHub issues are public.",
                "পাসওয়ার্ড বা ব্যক্তিগত মেসেজ লিখবেন না: GitHub-এ সবাই এটি দেখতে পারে।",
            ),
        )
        Button(
            onClick = {
                val text = BugReportText(summary, what, steps, expected)
                openLink(context, BugReport.issueUrl(text, details.takeIf { includeDetails }) { Uri.encode(it) })
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            MsIcon(UiR.drawable.ms_open_in_new, null)
            Text(tr("  Continue on GitHub", "  GitHub-এ এগিয়ে যান"), style = MaterialTheme.typography.titleMedium)
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
