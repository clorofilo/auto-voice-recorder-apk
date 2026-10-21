package com.example.auto_voice_recorder.ui.lock

import androidx.lifecycle.ViewModel
import com.example.auto_voice_recorder.security.PinManager
import com.example.auto_voice_recorder.security.PinRules
import com.example.auto_voice_recorder.security.PinValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LockUiState {
    data object Idle : LockUiState
    data object Error : LockUiState
    data object Unlocked : LockUiState

    /** First-run PIN creation was rejected (too short, not digits, or confirmation mismatch). */
    data class PinSetupError(val reason: PinValidation) : LockUiState
}

/**
 * Drives the lock screen: checks PIN attempts and biometric success against the app lock
 * state, and — when no PIN exists yet — the first-run PIN creation flow.
 */
class LockViewModel(private val pinManager: PinManager) : ViewModel() {

    private val _uiState = MutableStateFlow<LockUiState>(LockUiState.Idle)
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private val _needsPinSetup = MutableStateFlow(!pinManager.isPinSet())

    /** True until a PIN has been created; the lock screen shows the create-PIN UI meanwhile. */
    val needsPinSetup: StateFlow<Boolean> = _needsPinSetup.asStateFlow()

    fun submitPin(pin: String) {
        _uiState.value = if (pinManager.verifyPin(pin)) {
            LockUiState.Unlocked
        } else {
            LockUiState.Error
        }
    }

    /**
     * Creates the initial PIN. Only allowed while no PIN is set, so it can't be used to
     * overwrite an existing PIN from the lock screen.
     */
    fun createPin(pin: String, confirmation: String) {
        if (pinManager.isPinSet()) {
            _needsPinSetup.value = false
            _uiState.value = LockUiState.Idle
            return
        }
        val result = PinRules.validate(pin, confirmation)
        if (result != PinValidation.OK) {
            _uiState.value = LockUiState.PinSetupError(result)
            return
        }
        pinManager.setPin(pin)
        _needsPinSetup.value = false
        _uiState.value = LockUiState.Unlocked
    }

    fun onBiometricSuccess() {
        _uiState.value = LockUiState.Unlocked
    }
}
