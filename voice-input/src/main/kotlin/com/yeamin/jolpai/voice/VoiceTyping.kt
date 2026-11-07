package com.yeamin.jolpai.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Starting : VoiceState
    data class Listening(val partial: String, val level: Float) : VoiceState
    data object NoPermission : VoiceState
    data object Unavailable : VoiceState
    data class Error(val message: String) : VoiceState
}

/** Whether the device can recognise a language, and where. */
enum class VoiceAvailability(val label: String) {
    ON_DEVICE("Works offline on this phone"),
    DOWNLOADABLE("Offline pack available to download — online until then"),
    DOWNLOADING("Offline pack downloading — online until then"),
    ONLINE("Needs an internet connection"),
    UNSUPPORTED("Not supported by this phone's speech service"),
    UNKNOWN("Offline support can't be checked on this Android version; online recognition is used"),
}

/**
 * Voice typing with Android's [SpeechRecognizer] (normally Google's speech service).
 *
 * - Partial results arrive through [onPartial] and final results through [onFinal], with
 *   spoken punctuation applied ([VoiceCommands]).
 * - Dictation is continuous: Android stops after a pause, so listening restarts until [stop].
 * - Jolpai does not claim offline Bangla: [checkAvailability] asks the speech service
 *   (Android 13+) and reports what it says.
 *
 * Must be used from the main thread.
 */
class VoiceTyping(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var language = "en-US"
    private var lastPartial = ""

    /**
     * Each recogniser run has a number; callbacks from older runs (a cancelled session that still
     * reports an error or result) are ignored, so they can't stop or confuse the current one.
     */
    private var session = 0

    /** Back-to-back failures of the speech service; reset once it is listening again. */
    private var failures = 0

    /** The one pending (re)start; cancelling it leaves other queued work alone. */
    private val listenLater = Runnable { listen() }

    /** The language being listened for (or that the next start will use). */
    val currentLanguage: String get() = language

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts (or restarts in another language) continuous listening. */
    fun start(languageTag: String) {
        language = languageTag
        if (!hasPermission()) {
            _state.value = VoiceState.NoPermission
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Unavailable
            return
        }
        active = true
        listen()
    }

    /**
     * Switches language while dictating. What was heard so far is kept, the old recogniser is
     * thrown away, and listening restarts after a short pause, so tapping the language button
     * several times quickly ends in one clean restart in the last language picked.
     */
    fun switchLanguage(languageTag: String) {
        language = languageTag
        if (!active) {
            start(languageTag)
            return
        }
        if (lastPartial.isNotEmpty()) {
            onFinal(lastPartial)
            lastPartial = ""
        }
        failures = 0
        recreate(SWITCH_SETTLE_MS)
    }

    /** Stops listening; anything heard so far is committed. */
    fun stop() {
        active = false
        session++
        main.removeCallbacks(listenLater)
        recognizer?.stopListening()
        recognizer?.cancel()
        if (lastPartial.isNotEmpty()) {
            onFinal(lastPartial)
            lastPartial = ""
        }
        if (_state.value !is VoiceState.NoPermission && _state.value !is VoiceState.Unavailable) _state.value = VoiceState.Idle
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!active) return
        main.removeCallbacks(listenLater)
        val id = ++session
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(Listener(id))
        _state.value = VoiceState.Starting
        lastPartial = ""
        runCatching { r.startListening(intent(language)) }.onFailure { retryAfterFailure() }
    }

    private fun restartSoon(delayMs: Long = 150) {
        if (!active) return
        main.removeCallbacks(listenLater)
        main.postDelayed(listenLater, delayMs)
    }

    /** Throws the recogniser away and starts a fresh one after [delayMs]. */
    private fun recreate(delayMs: Long) {
        session++ // anything the old one still says is ignored
        main.removeCallbacks(listenLater)
        recognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        recognizer = null
        _state.value = VoiceState.Starting
        if (active) main.postDelayed(listenLater, delayMs)
    }

    /** A transient failure (service busy, disconnected, our own restart racing): try again a few times. */
    private fun retryAfterFailure() {
        if (!active) return
        failures++
        if (failures > MAX_RETRIES) {
            active = false
            _state.value = VoiceState.Error("Voice typing couldn't restart. Tap the microphone to try again.")
            return
        }
        recreate(RETRY_BASE_MS * failures)
    }

    private inner class Listener(private val id: Int) : RecognitionListener {
        private val current: Boolean get() = id == session

        override fun onReadyForSpeech(params: Bundle?) {
            if (!current) return
            failures = 0
            _state.value = VoiceState.Listening("", 0f)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (!current) return
            val s = _state.value
            if (s is VoiceState.Listening) _state.value = s.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current) return
            val text = best(partialResults) ?: return
            lastPartial = VoiceCommands.apply(text)
            onPartial(lastPartial)
            val s = _state.value
            _state.value = VoiceState.Listening(lastPartial, (s as? VoiceState.Listening)?.level ?: 0f)
        }

        override fun onResults(results: Bundle?) {
            if (!current) return
            val text = best(results)?.let { VoiceCommands.apply(it) } ?: lastPartial
            lastPartial = ""
            onFinal(text)
            if (active) restartSoon() else _state.value = VoiceState.Idle
        }

        // Error codes from newer Android versions are plain numbers compiled in, safe on older phones.
        @SuppressLint("InlinedApi")
        override fun onError(error: Int) {
            if (!current) return
            when (error) {
                // Silence or nothing understood: keep dictating.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (lastPartial.isNotEmpty()) {
                        onFinal(lastPartial)
                        lastPartial = ""
                    }
                    restartSoon()
                }
                // Busy, a client error from a restart racing the previous session, too many
                // requests or a dropped connection to the speech service: start over cleanly.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                -> retryAfterFailure()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    active = false
                    _state.value = VoiceState.NoPermission
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    active = false
                    _state.value = VoiceState.Error("No internet connection. Voice typing for this language needs one on this phone.")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                    active = false
                    _state.value = VoiceState.Error("This phone's speech service doesn't support $language.")
                }
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    active = false
                    _state.value = VoiceState.Error("$language isn't available right now. Try again with an internet connection.")
                }
                else -> {
                    active = false
                    _state.value = VoiceState.Error("Voice typing stopped (error $error). Tap the microphone to try again.")
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Asks the speech service whether [languageTag] works offline, can be downloaded, or is
     * online-only. Android 13+ only; older versions report [VoiceAvailability.UNKNOWN].
     */
    fun checkAvailability(languageTag: String, result: (VoiceAvailability) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            result(VoiceAvailability.UNKNOWN)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            result(VoiceAvailability.UNSUPPORTED)
            return
        }
        val checker = SpeechRecognizer.createSpeechRecognizer(context)
        val executor = Executor { main.post(it) }
        checker.checkRecognitionSupport(
            intent(languageTag),
            executor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    checker.destroy()
                    result(availability(support, languageTag))
                }

                override fun onError(error: Int) {
                    checker.destroy()
                    result(VoiceAvailability.UNKNOWN)
                }
            },
        )
    }

    /** Asks the speech service to download the offline pack for [languageTag] (Android 13+). */
    fun downloadOffline(languageTag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.triggerModelDownload(intent(languageTag))
        main.postDelayed({ r.destroy() }, 5_000)
    }

    private fun intent(languageTag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Ask for automatic punctuation and capitalisation where the service supports it.
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        }
    }

    private companion object {
        /** Pause after a language switch, so quick repeated taps end in a single restart. */
        const val SWITCH_SETTLE_MS = 350L
        const val RETRY_BASE_MS = 300L
        const val MAX_RETRIES = 4

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun availability(s: RecognitionSupport, tag: String): VoiceAvailability {
            fun List<String>.has() = any { it.equals(tag, ignoreCase = true) || it.substringBefore('-').equals(tag.substringBefore('-'), true) && it.length <= 3 }
            return when {
                s.installedOnDeviceLanguages.has() -> VoiceAvailability.ON_DEVICE
                s.pendingOnDeviceLanguages.has() -> VoiceAvailability.DOWNLOADING
                s.supportedOnDeviceLanguages.has() -> VoiceAvailability.DOWNLOADABLE
                s.onlineLanguages.has() -> VoiceAvailability.ONLINE
                else -> VoiceAvailability.UNSUPPORTED
            }
        }
    }
}
