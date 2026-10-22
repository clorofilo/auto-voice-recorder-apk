package com.example.auto_voice_recorder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.security.PinManager
import com.example.auto_voice_recorder.security.PinRules
import com.example.auto_voice_recorder.service.VibrationPreferences
import com.example.auto_voice_recorder.security.PinValidation
import com.example.auto_voice_recorder.sync.SyncPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Drives the settings screen: Drive sync toggle, vibration toggle and PIN change. */
class SettingsViewModel(
    private val syncPreferences: SyncPreferences,
    private val vibrationPreferences: VibrationPreferences,
    private val pinManager: PinManager,
    private val repository: RecordingsRepository,
    private val scheduleDriveSync: () -> Unit,
) : ViewModel() {

    private val _isDriveSyncEnabled = MutableStateFlow(syncPreferences.isEnabled())
    val isDriveSyncEnabled: StateFlow<Boolean> = _isDriveSyncEnabled.asStateFlow()

    private val _isVibrationEnabled = MutableStateFlow(vibrationPreferences.isEnabled())
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationPreferences.setEnabled(enabled)
        _isVibrationEnabled.value = enabled
    }

    fun setDriveSyncEnabled(enabled: Boolean) {
        syncPreferences.setEnabled(enabled)
        _isDriveSyncEnabled.value = enabled
        if (enabled) {
            viewModelScope.launch {
                // Recordings made while sync was off were stored as DISABLED; once the
                // user opts in they must become uploadable too, then kick off a sync run.
                repository.promoteDisabledToPending()
                scheduleDriveSync()
            }
        }
    }

    /**
     * Replaces the PIN only if [newPin] passes [PinRules] and matches [confirmation], so a
     * typo can't lock out a user without biometrics.
     */
    fun changePin(newPin: String, confirmation: String): PinValidation {
        val result = PinRules.validate(newPin, confirmation)
        if (result == PinValidation.OK) {
            pinManager.setPin(newPin)
        }
        return result
    }
}
