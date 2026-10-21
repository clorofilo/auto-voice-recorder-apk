package com.example.auto_voice_recorder.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockViewModelTest {

    @Test
    fun `submitPin with the correct PIN moves uiState to Unlocked`() {
        val viewModel = LockViewModel(FakePinManager(pin = "1234"))

        viewModel.submitPin("1234")

        assertEquals(LockUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `submitPin with an incorrect PIN moves uiState to Error`() {
        val viewModel = LockViewModel(FakePinManager(pin = "1234"))

        viewModel.submitPin("0000")

        assertEquals(LockUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `onBiometricSuccess moves uiState to Unlocked regardless of PIN state`() {
        val viewModel = LockViewModel(FakePinManager(pin = null))

        viewModel.onBiometricSuccess()

        assertEquals(LockUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `uiState starts as Idle`() {
        val viewModel = LockViewModel(FakePinManager(pin = "1234"))

        assertEquals(LockUiState.Idle, viewModel.uiState.value)
    }
}
