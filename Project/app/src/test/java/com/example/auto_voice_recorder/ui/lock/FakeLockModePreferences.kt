package com.example.auto_voice_recorder.ui.lock

import com.example.auto_voice_recorder.security.LockModePreferences

/** In-memory [LockModePreferences] for JVM unit tests. */
class FakeLockModePreferences(private var enabled: Boolean = false) : LockModePreferences {

    override fun isCalculatorLockEnabled(): Boolean = enabled

    override fun setCalculatorLockEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
