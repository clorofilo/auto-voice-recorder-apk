package com.example.auto_voice_recorder.ui.lock

import androidx.lifecycle.ViewModel
import com.example.auto_voice_recorder.security.PinManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LockUiState {
    data object Idle : LockUiState
    data object Error : LockUiState
    data object Unlocked : LockUiState
}

/** Drives the lock screen: checks PIN attempts and biometric success against the app lock state. */
class LockViewModel(private val pinManager: PinManager) : ViewModel() {

    private val _uiState = MutableStateFlow<LockUiState>(LockUiState.Idle)
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    fun submitPin(pin: String) {
        _uiState.value = if (pinManager.verifyPin(pin)) {
            LockUiState.Unlocked
        } else {
            LockUiState.Error
        }
    }

    fun onBiometricSuccess() {
        _uiState.value = LockUiState.Unlocked
    }
}
