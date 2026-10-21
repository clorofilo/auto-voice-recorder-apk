package com.example.auto_voice_recorder.sync

/** Whether Drive backup sync is enabled. */
interface SyncPreferences {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
