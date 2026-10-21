package com.example.auto_voice_recorder.sync

import android.content.SharedPreferences

/** [SyncPreferences] backed by a boolean flag in [SharedPreferences], disabled by default. */
class DefaultSyncPreferences(private val prefs: SharedPreferences) : SyncPreferences {

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_ENABLED = "drive_sync_enabled"
    }
}
