package com.example.auto_voice_recorder.ui.settings

import androidx.lifecycle.ViewModel
import com.example.auto_voice_recorder.security.PinManager
import com.example.auto_voice_recorder.sync.SyncPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Drives the settings screen: Drive sync toggle and PIN change. */
class SettingsViewModel(
    private val syncPreferences: SyncPreferences,
    private val pinManager: PinManager,
) : ViewModel() {

    private val _isDriveSyncEnabled = MutableStateFlow(syncPreferences.isEnabled())
    val isDriveSyncEnabled: StateFlow<Boolean> = _isDriveSyncEnabled.asStateFlow()

    fun setDriveSyncEnabled(enabled: Boolean) {
        syncPreferences.setEnabled(enabled)
        _isDriveSyncEnabled.value = enabled
    }

    fun changePin(newPin: String) {
        pinManager.setPin(newPin)
    }
}
