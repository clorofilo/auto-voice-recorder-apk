package com.example.auto_voice_recorder.security

import android.content.SharedPreferences

/** Whether the app lock is shown as a calculator instead of the PIN/biometric screen. */
interface LockModePreferences {
    fun isCalculatorLockEnabled(): Boolean
    fun setCalculatorLockEnabled(enabled: Boolean)
}

/** [LockModePreferences] backed by a boolean flag in [SharedPreferences], disabled by default. */
class DefaultLockModePreferences(private val prefs: SharedPreferences) : LockModePreferences {

    override fun isCalculatorLockEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setCalculatorLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_ENABLED = "calculator_lock_enabled"
    }
}
