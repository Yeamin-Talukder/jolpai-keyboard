package com.yeamin.jolpai

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Where Jolpai lives on GitHub. Updates, bug reports and sharing all point here. */
object JolpaiLinks {
    const val DEVELOPER_NAME = "Md Yeamin Talukder"
    const val DEVELOPER_GITHUB = "https://github.com/Yeamin-Talukder"
    const val REPO = "https://github.com/Yeamin-Talukder/jolpai-keyboard"
    const val RELEASES = "$REPO/releases"
    const val LATEST_RELEASE = "$REPO/releases/latest"
    const val NEW_ISSUE = "$REPO/issues/new"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/Yeamin-Talukder/jolpai-keyboard/releases/latest"
}

/**
 * One release in the in-app changelog ("What's new"). [date] is null for work that is built but
 * not released yet. When releasing: give it the version and date, and start a new null entry.
 */
data class ChangelogRelease(val version: String, val date: String?, val changes: List<String>) {
    val released: Boolean get() = date != null
}

object Changelog {
    /** Newest first. */
    val RELEASES: List<ChangelogRelease> = listOf(
        ChangelogRelease(
            "0.10.1", "7 Nov 2026",
            listOf(
                "Check for updates works even when GitHub's hourly limit is used up (it said \"Couldn't reach GitHub\" before)",
                "Clearer messages when an update can't be checked or downloaded",
            ),
        ),
        ChangelogRelease(
            "0.10.0", "7 Nov 2026",
            listOf(
                "Update right inside Jolpai: About → Update downloads the new version with a progress ring (percent, size, speed, time left) and installs it after Android's confirmation",
                "Every download is checked before it installs: the checksum must match the release, and it must be Jolpai, newer, and signed with the same key",
            ),
        ),
        ChangelogRelease(
            "0.9.0", "7 Nov 2026",
            listOf(
                "Check offers the fix plus up to 2 other likely words to tap, ranked by what you usually type; a misspelt last word is no longer missed",
                "About: a new header card, Check for updates (asks GitHub only when you tap), What's new, Report a problem and Share Jolpai",
                "New app ID com.yeamin.jolpai (was com.yeamin.lipi): installs as a new app once; move your data with Backup & restore",
            ),
        ),
        ChangelogRelease(
            "0.8.0", "7 Nov 2026",
            listOf(
                "Check: finds spelling, punctuation and grammar mistakes in English and বাংলা, offline; fix one, fix all, ignore or add to your dictionary",
                "Translator: mistakes show as tap-to-fix chips; cut, copy and paste work inside the box",
                "More accurate English autocorrect that never changes correct words",
                "Better Bangla suggestions: common words first, likelier spelling fixes",
                "Typing stats: words per day, English vs বাংলা, typing speed and accuracy (counts only)",
                "Emoji and clipboard open above the top bar, with redesigned pages",
                "Swipe down to close Translate, Check, emoji and clipboard",
                "Key fonts, sound packs and a split keyboard",
                "AI proofreading in Check on phones with on-device Gemini Nano",
            ),
        ),
        ChangelogRelease(
            "0.7.0", "7 Nov 2026",
            listOf(
                "First public release, signed with the Jolpai release key",
                "Avro phonetic, National (জাতীয়) and English layouts with one-tap switching",
                "On-device suggestions, autocorrect and next-word prediction in English and Bangla",
                "Offline English ⇄ Bangla translation inside the keyboard",
                "Voice typing, emoji search in English and Bangla, stickers and GIFs",
                "Clipboard history with categories, snippets and the editing panel",
                "64 themes: Olive night is the default, plus soft dark themes and Material You",
            ),
        ),
    )
}

/**
 * The newest release on GitHub. [apkUrl] / [apkName] / [apkSize] describe the APK for this
 * phone's processor, when found; [sumsUrl] is the release's SHA256SUMS.txt, used to check the
 * download before installing it.
 */
data class LatestRelease(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String? = null,
    val apkSize: Long = 0,
    val sumsUrl: String? = null,
)

/** Facts about this install, for bug reports. Never anything the user typed. */
data class DeviceInfo(
    val appVersion: String,
    val versionCode: Long,
    val androidRelease: String,
    val sdk: Int,
    val manufacturer: String,
    val model: String,
    val abi: String,
)

/**
 * Update checks against GitHub Releases and the text for bug reports. The parsing and comparing
 * here is plain Kotlin so it is unit-tested on the PC; only [fetchLatest] touches the network.
 */
object AppUpdates {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compares "0.7.0", "v0.10.1" or "1.0.0-beta" by their numbers: negative if [a] is older,
     * 0 if the same, positive if newer. Missing parts count as 0 ("1.0" == "1.0.0").
     */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(latest: String, installed: String): Boolean = compareVersions(latest, installed) > 0

    /**
     * Reads GitHub's "latest release" JSON. Picks the APK whose file name contains the first of
     * [abis] that matches (the phone's preferred processor type comes first), or null.
     */
    fun parseLatest(body: String, abis: List<String>): LatestRelease? = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        val tag = o["tag_name"]?.jsonPrimitive?.content ?: return null
        val page = o["html_url"]?.jsonPrimitive?.content ?: JolpaiLinks.LATEST_RELEASE
        class Asset(val name: String, val url: String, val size: Long)
        val assets = o["assets"]?.jsonArray.orEmpty().mapNotNull { a ->
            val obj = a.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Asset(name, url, obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
        }
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val apk = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.name.contains("-$abi.") || it.name.contains("-$abi-") } }
        val sums = assets.firstOrNull { it.name.equals("SHA256SUMS.txt", ignoreCase = true) }
        LatestRelease(tag.removePrefix("v"), page, apk?.url, apk?.name, apk?.size ?: 0L, sums?.url)
    }.getOrNull()

    /** The SHA-256 (lower-case hex) listed for [fileName] in a SHA256SUMS.txt body, or null. */
    fun checksumFor(sums: String, fileName: String): String? = sums.lineSequence()
        .map { it.trim().split(Regex("\\s+"), limit = 2) }
        .firstOrNull { it.size == 2 && it[1].removePrefix("*") == fileName && it[0].length == 64 }
        ?.get(0)?.lowercase()

    /** A GitHub issue body with this install's details filled in and room for the problem. */
    fun issueBody(info: DeviceInfo): String = """
        |**What happened?**
        |
        |
        |**Steps to reproduce**
        |1.
        |
        |**What did you expect?**
        |
        |
        |---
        |- App: Jolpai Keyboard ${info.appVersion} (${info.versionCode})
        |- Android: ${info.androidRelease} (API ${info.sdk})
        |- Phone: ${info.manufacturer} ${info.model}
        |- Processor: ${info.abi}
        |
        |_Please don't paste passwords or other private text._
    """.trimMargin()

    /** The link that opens a new, pre-filled issue on GitHub. [encode] URL-encodes a value. */
    fun issueUrl(info: DeviceInfo, encode: (String) -> String): String =
        "${JolpaiLinks.NEW_ISSUE}?labels=bug&title=${encode("Problem: ")}&body=${encode(issueBody(info))}"

    /** Processor types the release workflow builds an APK for (file names end in -<abi>.apk). */
    val RELEASE_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /**
     * The tag in the address GitHub's ".../releases/latest" page redirects to
     * (".../releases/tag/v0.10.0" → "v0.10.0"), or null (e.g. no release yet: ".../releases").
     */
    fun tagFromReleaseUrl(location: String?): String? =
        location?.let { Regex("/releases/tag/([^/?#]+)/?(?:[?#].*)?$").find(it)?.groupValues?.get(1) }
            ?.takeIf { it.isNotBlank() }

    /**
     * The release for [tag] built from the release workflow's fixed file names, for when GitHub's
     * API can't be used. The size is unknown (0): the download's own length is used.
     */
    fun releaseFromTag(tag: String, abis: List<String>): LatestRelease {
        val version = tag.removePrefix("v")
        val abi = abis.firstOrNull { it in RELEASE_ABIS }
        val base = "${JolpaiLinks.REPO}/releases/download/$tag"
        val apkName = abi?.let { "jolpai-keyboard-$version-$it.apk" }
        return LatestRelease(
            version = version,
            pageUrl = "${JolpaiLinks.REPO}/releases/tag/$tag",
            apkUrl = apkName?.let { "$base/$it" },
            apkName = apkName,
            apkSize = 0,
            sumsUrl = "$base/SHA256SUMS.txt",
        )
    }

    /**
     * Asks GitHub for the newest release, off the main thread.
     *
     * GitHub's API allows only 60 requests an hour per internet connection without an account
     * (shared by everyone on the same Wi-Fi, and by the website's download button). When it
     * refuses (403/429) or fails, the plain ".../releases/latest" page is used instead: it isn't
     * rate-limited, and its redirect names the newest tag.
     */
    suspend fun lookupLatest(installedVersion: String): LatestLookup = withContext(Dispatchers.IO) {
        val abis = Build.SUPPORTED_ABIS.toList()
        var connected = false
        // 1. The API: exact file names and sizes.
        try {
            val conn = URL(JolpaiLinks.LATEST_RELEASE_API).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "JolpaiKeyboard/$installedVersion")
                val code = conn.responseCode
                connected = true
                if (code == 200) {
                    val body = conn.inputStream.use { it.readBytes().decodeToString() }
                    parseLatest(body, abis)?.let { return@withContext LatestLookup.Found(it) }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: java.io.IOException) {
            // Try the web page below.
        }
        // 2. The release page's redirect.
        try {
            val conn = URL(JolpaiLinks.LATEST_RELEASE).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "JolpaiKeyboard/$installedVersion")
                val code = conn.responseCode
                connected = true
                if (code in 300..399) {
                    tagFromReleaseUrl(conn.getHeaderField("Location"))?.let {
                        return@withContext LatestLookup.Found(releaseFromTag(it, abis))
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: java.io.IOException) {
            // Nothing reachable.
        }
        if (connected) LatestLookup.Unavailable else LatestLookup.Offline
    }
}

/** What [AppUpdates.lookupLatest] found. */
sealed interface LatestLookup {
    data class Found(val release: LatestRelease) : LatestLookup

    /** The phone couldn't connect to GitHub at all. */
    data object Offline : LatestLookup

    /** GitHub answered, but not with a release (busy, or no release published). */
    data object Unavailable : LatestLookup
}

/** This install's details for [AppUpdates.issueBody]. */
fun deviceInfo(context: Context): DeviceInfo {
    val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    @Suppress("DEPRECATION")
    val code = when {
        pkg == null -> 0L
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> pkg.longVersionCode
        else -> pkg.versionCode.toLong()
    }
    return DeviceInfo(
        appVersion = pkg?.versionName ?: "?",
        versionCode = code,
        androidRelease = Build.VERSION.RELEASE ?: "?",
        sdk = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        model = Build.MODEL,
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
    )
}

/** Opens [url] in the browser; does nothing if the phone has no app for links. */
fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Android's share sheet with a short pitch and the download link. */
fun shareJolpai(context: Context) {
    val text = "Jolpai Keyboard (জলপাই) — a free, private Bangla & English keyboard for Android: " +
        "Avro phonetic, the National (জাতীয়) layout, offline translation and 64 themes. No ads.\n" +
        "Download: ${JolpaiLinks.LATEST_RELEASE}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Jolpai Keyboard")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share Jolpai").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
