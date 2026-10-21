package com.example.auto_voice_recorder.ui.settings

import com.example.auto_voice_recorder.data.FakeRecordingDao
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.data.SyncState
import com.example.auto_voice_recorder.security.PinManager
import com.example.auto_voice_recorder.security.PinValidation
import com.example.auto_voice_recorder.sync.SyncPreferences
import com.example.auto_voice_recorder.ui.lock.FakePinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: RecordingsRepository
    private var scheduleCallCount = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = RecordingsRepository(FakeRecordingDao())
        scheduleCallCount = 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        syncPreferences: SyncPreferences = FakeSyncPreferences(),
        pinManager: PinManager = FakePinManager(),
    ) = SettingsViewModel(
        syncPreferences = syncPreferences,
        pinManager = pinManager,
        repository = repository,
        scheduleDriveSync = { scheduleCallCount++ },
    )

    @Test
    fun `isDriveSyncEnabled reflects the current SyncPreferences value`() {
        val viewModel = newViewModel(syncPreferences = FakeSyncPreferences(enabled = true))

        assertTrue(viewModel.isDriveSyncEnabled.value)
    }

    @Test
    fun `setDriveSyncEnabled(true) persists through syncPreferences and updates isDriveSyncEnabled`() {
        val syncPreferences = FakeSyncPreferences(enabled = false)
        val viewModel = newViewModel(syncPreferences = syncPreferences)

        viewModel.setDriveSyncEnabled(true)

        assertTrue(syncPreferences.isEnabled())
        assertTrue(viewModel.isDriveSyncEnabled.value)
    }

    @Test
    fun `setDriveSyncEnabled(true) promotes DISABLED recordings to PENDING and schedules a sync`() =
        runTest(testDispatcher) {
            repository.add("/rec/off.m4a", 1L, 1L, SyncState.DISABLED)
            repository.add("/rec/done.m4a", 2L, 2L, SyncState.SYNCED)
            val viewModel = newViewModel()

            viewModel.setDriveSyncEnabled(true)

            val states = repository.observeAll().first().associate { it.filePath to it.syncState }
            assertEquals(SyncState.PENDING, states["/rec/off.m4a"])
            assertEquals(SyncState.SYNCED, states["/rec/done.m4a"])
            assertEquals(1, scheduleCallCount)
        }

    @Test
    fun `setDriveSyncEnabled(false) neither schedules a sync nor touches recordings`() =
        runTest(testDispatcher) {
            repository.add("/rec/off.m4a", 1L, 1L, SyncState.DISABLED)
            val viewModel = newViewModel(syncPreferences = FakeSyncPreferences(enabled = true))

            viewModel.setDriveSyncEnabled(false)

            assertEquals(SyncState.DISABLED, repository.observeAll().first().single().syncState)
            assertEquals(0, scheduleCallCount)
        }

    @Test
    fun `changePin updates the PinManager so the new PIN verifies and the old one does not`() {
        val pinManager = FakePinManager(pin = "1234")
        val viewModel = newViewModel(pinManager = pinManager)

        val result = viewModel.changePin("5678", "5678")

        assertEquals(PinValidation.OK, result)
        assertTrue(pinManager.verifyPin("5678"))
        assertFalse(pinManager.verifyPin("1234"))
    }

    @Test
    fun `changePin with a mismatched confirmation keeps the old PIN`() {
        val pinManager = FakePinManager(pin = "1234")
        val viewModel = newViewModel(pinManager = pinManager)

        val result = viewModel.changePin("5678", "5679")

        assertEquals(PinValidation.MISMATCH, result)
        assertTrue(pinManager.verifyPin("1234"))
    }

    @Test
    fun `changePin with a too-short PIN keeps the old PIN`() {
        val pinManager = FakePinManager(pin = "1234")
        val viewModel = newViewModel(pinManager = pinManager)

        val result = viewModel.changePin("12", "12")

        assertEquals(PinValidation.TOO_SHORT, result)
        assertTrue(pinManager.verifyPin("1234"))
    }
}
