package com.yeamin.lipi.ui

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.yeamin.lipi.core.model.KeyStyle
import com.yeamin.lipi.core.settings.HapticLevel
import com.yeamin.lipi.core.settings.KeyboardSettings

/**
 * Vibration and click sounds for key presses, following the user's settings and the phone's:
 * - "Phone default" vibration uses the system keyboard haptic, which obeys the system
 *   touch-feedback setting; custom strengths are only used when that setting is on.
 * - Sounds play only in normal ringer mode and when Do Not Disturb isn't silencing things.
 * - Space, Enter and Backspace sound (and feel) slightly different from letters.
 */
class KeyFeedback(context: Context, private val settings: () -> KeyboardSettings) {

    private val appContext = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun keyDown(view: View, style: KeyStyle) {
        val extra = if (style == KeyStyle.CHARACTER) 0L else 3L
        haptic(view, HapticFeedbackConstants.KEYBOARD_TAP, extra)
        sound(style)
    }

    /** One step of a space-bar or backspace swipe. */
    fun tick(view: View) = haptic(view, HapticFeedbackConstants.CLOCK_TICK, -4)

    fun longPress(view: View) = haptic(view, HapticFeedbackConstants.LONG_PRESS, 10)

    private fun haptic(view: View, systemConstant: Int, extraMillis: Long) {
        val level = settings().haptics
        when (level) {
            HapticLevel.OFF -> Unit
            HapticLevel.SYSTEM -> view.performHapticFeedback(systemConstant)
            else -> {
                if (!systemTouchFeedbackOn()) return
                val v = vibrator?.takeIf { it.hasVibrator() } ?: return
                val ms = (level.millis + extraMillis).coerceAtLeast(4)
                val amplitude = if (v.hasAmplitudeControl()) level.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            }
        }
    }

    private fun systemTouchFeedbackOn(): Boolean =
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1

    private fun sound(style: KeyStyle) {
        val s = settings()
        if (!s.keySound || s.soundVolume <= 0f) return
        val a = audio ?: return
        if (a.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (notifications != null && notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return
        val effect = when (style) {
            KeyStyle.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            KeyStyle.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyStyle.ENTER, KeyStyle.ENTER_ACTION -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        a.playSoundEffect(effect, s.soundVolume)
    }
}
