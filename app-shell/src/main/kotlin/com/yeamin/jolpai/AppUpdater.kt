package com.yeamin.jolpai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where an in-app update stands. The About screen draws one card for each. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String) : UpdateState
    data class Available(val release: LatestRelease) : UpdateState

    /** [total] is 0 while the size isn't known yet. [bytesPerSecond] is a smoothed speed. */
    data class Downloading(val release: LatestRelease, val downloaded: Long, val total: Long, val bytesPerSecond: Long) : UpdateState {
        val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** Checking the download's checksum and signature. */
    data class Verifying(val release: LatestRelease) : UpdateState

    /** Handed to Android's installer; its "Update this app?" prompt is on screen. */
    data class Installing(val release: LatestRelease) : UpdateState

    /** Android needs "Allow from this source" for Jolpai before it can install. */
    data class NeedsPermission(val release: LatestRelease) : UpdateState

    /** The new APK is signed with a different key than the installed app (e.g. a developer build). */
    data class SignedDifferently(val release: LatestRelease) : UpdateState

    data class Failed(val message: String, val release: LatestRelease?) : UpdateState
}

/**
 * Downloads a new Jolpai release from GitHub and installs it without leaving the app.
 *
 * 1. [check] asks GitHub for the newest release (only when the user taps).
 * 2. [download] streams the APK for this phone's processor into the app's cache, with progress.
 * 3. The file's SHA-256 must match the release's SHA256SUMS.txt, and the APK must be Jolpai,
 *    newer, and signed with the same key as the installed app. Otherwise nothing is installed.
 * 4. It goes to Android's [PackageInstaller], which shows the system "Update this app?" prompt;
 *    Android itself refuses an APK from a different signer, so this can't replace Jolpai with
 *    something else.
 *
 * The state lives here, not in the screen, so a download keeps going if the user leaves About.
 */
object AppUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    private const val DIR = "updates"

    fun check(context: Context, installedVersion: String) {
        if (_state.value is UpdateState.Checking || busy()) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            val latest = AppUpdates.fetchLatest(installedVersion)
            _state.value = when {
                latest == null -> UpdateState.Failed("Couldn't reach GitHub. Check the internet and try again.", null)
                AppUpdates.isNewer(latest.version, installedVersion) -> UpdateState.Available(latest)
                else -> UpdateState.UpToDate(latest.version)
            }
            clearCache(context.applicationContext)
        }
    }

    /** Downloads [release] and installs it, asking for the install permission first if needed. */
    fun download(context: Context, release: LatestRelease) {
        val app = context.applicationContext
        if (busy()) return
        if (release.apkUrl == null || release.apkName == null) {
            _state.value = UpdateState.Failed("This release has no APK for your phone's processor.", release)
            return
        }
        if (!canInstall(app)) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        _state.value = UpdateState.Downloading(release, 0, release.apkSize, 0)
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { fetch(app, release) }
                _state.value = UpdateState.Verifying(release)
                val problem = withContext(Dispatchers.IO) { verify(app, release, file) }
                when (problem) {
                    null -> {
                        _state.value = UpdateState.Installing(release)
                        withContext(Dispatchers.IO) { install(app, file) }
                    }
                    Problem.SIGNER -> {
                        file.delete()
                        _state.value = UpdateState.SignedDifferently(release)
                    }
                    else -> {
                        file.delete()
                        _state.value = UpdateState.Failed(problem.message, release)
                    }
                }
            } catch (e: CancellationException) {
                clearCache(app)
                _state.value = UpdateState.Available(release)
                throw e
            } catch (e: IOException) {
                clearCache(app)
                _state.value = UpdateState.Failed("The download stopped. Check the internet and try again.", release)
            } catch (e: Exception) {
                clearCache(app)
                _state.value = UpdateState.Failed("The update couldn't be installed (${e.javaClass.simpleName}).", release)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Back to the plain "Check for updates" row (after a failure the user dismissed). */
    fun reset() {
        if (!busy()) _state.value = UpdateState.Idle
    }

    /** Android's "Install unknown apps" page for Jolpai. */
    fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** The user came back from settings: continue if they allowed it. */
    fun onResume(context: Context) {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && canInstall(context)) download(context, s.release)
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun busy() = _state.value.let {
        it is UpdateState.Downloading || it is UpdateState.Verifying || it is UpdateState.Installing
    }

    // ---------------------------------------------------------------- download

    private suspend fun fetch(context: Context, release: LatestRelease): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, release.apkName!!)
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true // GitHub redirects downloads to its file storage
            conn.setRequestProperty("User-Agent", "JolpaiKeyboard")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            val coroutine = kotlin.coroutines.coroutineContext
            var done = 0L
            var lastEmit = 0L
            var speedMark = SystemClock.elapsedRealtime()
            var speedBytes = 0L
            var speed = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        speedBytes += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - speedMark >= 500) {
                            val instant = speedBytes * 1000 / (now - speedMark)
                            speed = if (speed == 0L) instant else (speed * 6 + instant * 4) / 10
                            speedMark = now
                            speedBytes = 0
                        }
                        if (now - lastEmit >= 80 || done == total) {
                            lastEmit = now
                            _state.value = UpdateState.Downloading(release, done, total, speed)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("incomplete: $done of $total")
            _state.value = UpdateState.Downloading(release, done, maxOf(total, done), speed)
            return file
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------- verify

    private enum class Problem(val message: String) {
        CHECKSUM("The download didn't match the release's checksum, so it wasn't installed. Try again."),
        NO_CHECKSUM("Couldn't read the release's checksum file, so the download wasn't installed. Try again."),
        NOT_JOLPAI("The downloaded file isn't Jolpai, so it wasn't installed."),
        NOT_NEWER("The downloaded version isn't newer than the one you have."),
        SIGNER("Signed with a different key."),
    }

    private fun verify(context: Context, release: LatestRelease, file: File): Problem? {
        // 1. Checksum from the release's SHA256SUMS.txt.
        val sums = release.sumsUrl?.let { readText(it) } ?: return Problem.NO_CHECKSUM
        val expected = AppUpdates.checksumFor(sums, release.apkName!!) ?: return Problem.NO_CHECKSUM
        if (sha256(file) != expected) return Problem.CHECKSUM

        // 2. It is Jolpai, newer, and signed like the installed app.
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return Problem.NOT_JOLPAI
        if (archive.packageName != context.packageName) return Problem.NOT_JOLPAI
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (versionCode(archive) <= versionCode(installed)) return Problem.NOT_NEWER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val a = signers(archive)
            val b = signers(installed)
            if (a.isNotEmpty() && b.isNotEmpty() && a != b) return Problem.SIGNER
        }
        return null
    }

    private fun readText(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "JolpaiKeyboard")
            if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

    private fun signers(p: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptySet()
        val certs = p.signingInfo?.apkContentsSigners ?: return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return certs.map { md.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    // ---------------------------------------------------------------- install

    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("jolpai.apk", 0, file.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            session.commit(result.intentSender)
        }
    }

    /** From [InstallResultReceiver]. On success Android restarts Jolpai as the new version. */
    internal fun onInstallResult(context: Context, status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        clearCache(context)
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                release?.let { UpdateState.SignedDifferently(it) } ?: UpdateState.Failed("Android couldn't install the update.", null)
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                UpdateState.Failed("Not enough free space to install the update.", release)
            else -> UpdateState.Failed("Android couldn't install the update${message?.let { ": $it" } ?: "."}", release)
        }
    }

    private fun clearCache(context: Context) {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }
}

/** Receives the result of an install session; shows Android's confirmation when it asks for it. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { AppUpdater.onInstallResult(context, PackageInstaller.STATUS_FAILURE, it.message) }
            }
            return
        }
        AppUpdater.onInstallResult(context, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
