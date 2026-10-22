package com.example.auto_voice_recorder.ui.lock

import com.example.auto_voice_recorder.security.LockModePreferences
import com.example.auto_voice_recorder.security.PinManager
import com.example.auto_voice_recorder.security.PinValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockViewModelTest {

    private fun newViewModel(
        pinManager: PinManager = FakePinManager(pin = "1234"),
        lockModePreferences: LockModePreferences = FakeLockModePreferences(),
    ) = LockViewModel(pinManager, lockModePreferences)

    @Test
    fun `submitPin with the correct PIN moves uiState to Unlocked`() {
        val viewModel = newViewModel(pinManager = FakePinManager(pin = "1234"))

        viewModel.submitPin("1234")

        assertEquals(LockUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `submitPin with an incorrect PIN moves uiState to Error`() {
        val viewModel = newViewModel(pinManager = FakePinManager(pin = "1234"))

        viewModel.submitPin("0000")

        assertEquals(LockUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `onBiometricSuccess moves uiState to Unlocked regardless of PIN state`() {
        val viewModel = newViewModel(pinManager = FakePinManager(pin = null))

        viewModel.onBiometricSuccess()

        assertEquals(LockUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `needsPinSetup is true only when no PIN has been set`() {
        assertTrue(newViewModel(pinManager = FakePinManager(pin = null)).needsPinSetup.value)
        assertFalse(newViewModel(pinManager = FakePinManager(pin = "1234")).needsPinSetup.value)
    }

    @Test
    fun `createPin with matching valid PINs stores the PIN and unlocks`() {
        val pinManager = FakePinManager(pin = null)
        val viewModel = newViewModel(pinManager = pinManager)

        viewModel.createPin("2468", "2468")

        assertTrue(pinManager.verifyPin("2468"))
        assertFalse(viewModel.needsPinSetup.value)
        assertEquals(LockUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `createPin with a mismatched confirmation stores nothing and reports MISMATCH`() {
        val pinManager = FakePinManager(pin = null)
        val viewModel = newViewModel(pinManager = pinManager)

        viewModel.createPin("2468", "2469")

        assertFalse(pinManager.isPinSet())
        assertTrue(viewModel.needsPinSetup.value)
        assertEquals(LockUiState.PinSetupError(PinValidation.MISMATCH), viewModel.uiState.value)
    }

    @Test
    fun `createPin with a too-short PIN stores nothing and reports TOO_SHORT`() {
        val pinManager = FakePinManager(pin = null)
        val viewModel = newViewModel(pinManager = pinManager)

        viewModel.createPin("12", "12")

        assertFalse(pinManager.isPinSet())
        assertEquals(LockUiState.PinSetupError(PinValidation.TOO_SHORT), viewModel.uiState.value)
    }

    @Test
    fun `createPin cannot overwrite an existing PIN or unlock`() {
        val pinManager = FakePinManager(pin = "1234")
        val viewModel = newViewModel(pinManager = pinManager)

        viewModel.createPin("9999", "9999")

        assertTrue(pinManager.verifyPin("1234"))
        assertFalse(pinManager.verifyPin("9999"))
        assertNotEquals(LockUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `uiState starts as Idle`() {
        val viewModel = newViewModel(pinManager = FakePinManager(pin = "1234"))

        assertEquals(LockUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `isCalculatorLockEnabled reflects the stored preference`() {
        val viewModel = newViewModel(
            lockModePreferences = FakeLockModePreferences(enabled = true),
        )

        assertTrue(viewModel.isCalculatorLockEnabled.value)
    }

    @Test
    fun `submitCalculatorCode with the correct PIN unlocks`() {
        val viewModel = newViewModel(pinManager = FakePinManager(pin = "1234"))

        viewModel.submitCalculatorCode("1234")

        assertEquals(LockUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `submitCalculatorCode with an incorrect code leaves the state Idle without an error`() {
        val viewModel = newViewModel(pinManager = FakePinManager(pin = "1234"))

        viewModel.submitCalculatorCode("0000")

        assertEquals(LockUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `submitCalculatorCode with null does nothing`() {
        val viewModel = newViewModel()

        viewModel.submitCalculatorCode(null)

        assertEquals(LockUiState.Idle, viewModel.uiState.value)
    }
}
