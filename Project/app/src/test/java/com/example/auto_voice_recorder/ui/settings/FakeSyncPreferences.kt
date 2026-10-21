package com.example.auto_voice_recorder.ui.settings

import com.example.auto_voice_recorder.sync.SyncPreferences

/** In-memory [SyncPreferences] for JVM unit tests. */
class FakeSyncPreferences(private var enabled: Boolean = false) : SyncPreferences {

    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
