package com.yeamin.jolpai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.KeyboardStyle
import java.util.Locale

/** What the user wrote in the bug form. */
data class BugReportText(
    val summary: String,
    val whatHappened: String,
    val steps: String = "",
    val expected: String = "",
)

/**
 * A bug report as a GitHub issue: the user's words, then (if they agree) the phone, app and
 * keyboard details a developer needs to reproduce it. Never anything typed with the keyboard,
 * learned words or the clipboard. Plain Kotlin, so it is unit-tested on the PC.
 */
object BugReport {

    /** GitHub refuses very long "new issue" links; stay well under its ~8 KB limit. */
    const val MAX_URL_LENGTH = 7_000

    /** Details as label → value rows, in the order they appear in the issue. */
    fun details(
        info: DeviceInfo,
        extra: DeviceExtras,
        settings: KeyboardSettings,
        setup: SetupStatus,
    ): List<Pair<String, String>> = listOf(
        "App" to "Jolpai Keyboard ${info.appVersion} (${info.versionCode})",
        "Android" to "${info.androidRelease} (API ${info.sdk})",
        "Phone" to "${info.manufacturer} ${info.model} (${extra.device})",
        "Android build" to extra.build,
        "Processor" to info.abi,
        "Memory" to extra.ramText,
        "Screen" to extra.screen,
        "Phone language" to extra.locale,
        "Jolpai turned on / in use" to "${yesNo(setup.enabled)} / ${yesNo(setup.selected)}",
        "Layouts" to settings.enabledLayouts.joinToString(", ") { it.displayName },
        "Style" to (KeyboardStyle.of(settings)?.label ?: "Custom"),
        "Theme" to settings.themeId,
        "Height" to "${settings.heightPortrait}% portrait, ${settings.heightLandscape}% landscape",
        "Options" to listOfNotNull(
            "number row".takeIf { settings.numberRow },
            "one-handed ${settings.oneHanded.storageKey}".takeIf { settings.oneHanded.storageKey != "off" },
            "floating".takeIf { settings.floating },
            "autocorrect".takeIf { settings.autoCorrect },
            "suggestions".takeIf { settings.showSuggestions },
            "fix vowel signs".takeIf { settings.banglaAutoVowels },
            "swipe gestures".takeIf { settings.cursorSwipe },
            "gesture shortcuts".takeIf { settings.gestures.enabled },
            "hold ${settings.longPressMs} ms",
            "panels ${settings.panelPlacement.storageKey}",
        ).joinToString(", "),
    )

    /** The issue's title: the user's summary, or the start of what happened. */
    fun title(text: BugReportText): String {
        val s = text.summary.trim().ifEmpty { text.whatHappened.trim().lineSequence().firstOrNull().orEmpty() }
        return "Bug: " + (if (s.length > 80) s.take(79).trimEnd() + "…" else s)
    }

    /** The issue's body in GitHub Markdown. [details] null leaves the phone details out. */
    fun body(text: BugReportText, details: List<Pair<String, String>>?, whatHappenedLimit: Int = Int.MAX_VALUE): String = buildString {
        appendLine("**What happened?**")
        val what = text.whatHappened.trim()
        appendLine(if (what.length > whatHappenedLimit) what.take(whatHappenedLimit).trimEnd() + " … _(shortened to fit)_" else what.ifEmpty { "_(not given)_" })
        if (text.steps.isNotBlank()) {
            appendLine()
            appendLine("**Steps to reproduce**")
            appendLine(text.steps.trim())
        }
        if (text.expected.isNotBlank()) {
            appendLine()
            appendLine("**What did you expect?**")
            appendLine(text.expected.trim())
        }
        appendLine()
        if (details != null) {
            appendLine("**Phone and app**")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---|")
            for ((k, v) in details) appendLine("| $k | ${v.replace("|", "/").replace("\n", " ")} |")
            appendLine()
        }
        append("_Sent from Jolpai's bug form._")
    }

    /**
     * The link that opens GitHub's "new issue" page filled in. If the link would be too long,
     * "what happened" is shortened (the details always fit) so GitHub still opens it.
     */
    fun issueUrl(text: BugReportText, details: List<Pair<String, String>>?, encode: (String) -> String): String {
        fun url(limit: Int) = "${JolpaiLinks.NEW_ISSUE}?labels=bug&title=${encode(title(text))}&body=${encode(body(text, details, limit))}"
        var limit = text.whatHappened.length
        var u = url(limit)
        while (u.length > MAX_URL_LENGTH && limit > 0) {
            limit = (limit * 3 / 4).coerceAtMost(limit - 50).coerceAtLeast(0)
            u = url(limit)
        }
        return u
    }

    private fun yesNo(b: Boolean) = if (b) "yes" else "no"
}

/** Phone details beyond [DeviceInfo], read on the phone. */
data class DeviceExtras(
    val device: String,
    val build: String,
    val ramText: String,
    val screen: String,
    val locale: String,
)

fun deviceExtras(context: Context): DeviceExtras {
    val am = context.getSystemService(ActivityManager::class.java)
    val mem = ActivityManager.MemoryInfo().also { runCatching { am?.getMemoryInfo(it) } }
    val gb = mem.totalMem / (1024.0 * 1024 * 1024)
    val dm = context.resources.displayMetrics
    val cfg = context.resources.configuration
    return DeviceExtras(
        device = Build.DEVICE,
        build = Build.DISPLAY,
        ramText = if (mem.totalMem > 0) String.format(Locale.US, "%.1f GB%s", gb, if (am?.isLowRamDevice == true) " (low-RAM device)" else "") else "?",
        screen = "${dm.widthPixels}×${dm.heightPixels} px, ${dm.densityDpi} dpi, font scale ${cfg.fontScale}",
        locale = Locale.getDefault().toLanguageTag(),
    )
}
