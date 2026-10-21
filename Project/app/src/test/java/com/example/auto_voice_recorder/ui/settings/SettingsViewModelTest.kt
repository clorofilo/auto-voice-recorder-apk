package com.example.auto_voice_recorder.ui.settings

import com.example.auto_voice_recorder.ui.lock.FakePinManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `isDriveSyncEnabled reflects the current SyncPreferences value`() {
        val syncPreferences = FakeSyncPreferences(enabled = true)
        val viewModel = SettingsViewModel(syncPreferences, FakePinManager())

        assertTrue(viewModel.isDriveSyncEnabled.value)
    }

    @Test
    fun `setDriveSyncEnabled(true) persists through syncPreferences and updates isDriveSyncEnabled`() {
        val syncPreferences = FakeSyncPreferences(enabled = false)
        val viewModel = SettingsViewModel(syncPreferences, FakePinManager())

        viewModel.setDriveSyncEnabled(true)

        assertTrue(syncPreferences.isEnabled())
        assertTrue(viewModel.isDriveSyncEnabled.value)
    }

    @Test
    fun `changePin updates the PinManager so the new PIN verifies and the old one does not`() {
        val pinManager = FakePinManager(pin = "1234")
        val viewModel = SettingsViewModel(FakeSyncPreferences(), pinManager)

        viewModel.changePin("5678")

        assertTrue(pinManager.verifyPin("5678"))
        assertFalse(pinManager.verifyPin("1234"))
    }
}
