package com.yeamin.lipi.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.edit

/**
 * An invisible screen whose only job is Android's "Allow microphone?" dialog. A keyboard can't
 * ask for a permission itself, so it opens this (no page is shown, just the system dialog over
 * the app being typed in) and it closes again straight away. The keyboard then comes back and
 * starts listening (see [LipiImeService]).
 */
class MicPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        if (savedInstanceState == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        // Denied without the dialog being allowed to show again: only Android's settings can change it.
        val blocked = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        prefs(this).edit { putBoolean(KEY_BLOCKED, blocked).putBoolean(KEY_START_AFTER, granted) }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQUEST = 1
        private const val PREFS = "voice"
        private const val KEY_BLOCKED = "mic_blocked"
        private const val KEY_START_AFTER = "start_after_grant"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun intent(context: Context): Intent = Intent(context, MicPermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        /** True if the user blocked the dialog (it can only be allowed in Android's settings now). */
        fun isBlocked(context: Context): Boolean = prefs(context).getBoolean(KEY_BLOCKED, false)

        /** True once after permission was just granted: the keyboard should start listening. */
        fun consumeStartAfterGrant(context: Context): Boolean {
            val p = prefs(context)
            val start = p.getBoolean(KEY_START_AFTER, false)
            if (start) p.edit { putBoolean(KEY_START_AFTER, false) }
            return start
        }

        fun clearBlocked(context: Context) = prefs(context).edit { putBoolean(KEY_BLOCKED, false) }
    }
}
