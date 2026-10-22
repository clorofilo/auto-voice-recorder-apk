package com.example.auto_voice_recorder.ui.settings

import com.example.auto_voice_recorder.service.VibrationPreferences

/** In-memory [VibrationPreferences] for JVM unit tests. */
class FakeVibrationPreferences(private var enabled: Boolean = false) : VibrationPreferences {

    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
