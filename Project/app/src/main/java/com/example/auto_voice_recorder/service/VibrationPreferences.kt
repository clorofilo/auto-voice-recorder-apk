package com.example.auto_voice_recorder.service

import android.content.SharedPreferences

/** Whether the phone vibrates when a recording starts and when it stops. */
interface VibrationPreferences {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}

/** [VibrationPreferences] backed by a boolean flag in [SharedPreferences], disabled by default. */
class DefaultVibrationPreferences(private val prefs: SharedPreferences) : VibrationPreferences {

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_ENABLED = "vibrate_on_recording_enabled"
    }
}
