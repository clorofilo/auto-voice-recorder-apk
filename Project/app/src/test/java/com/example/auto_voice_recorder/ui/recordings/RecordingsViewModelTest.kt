package com.example.auto_voice_recorder.ui.recordings

import com.example.auto_voice_recorder.data.FakeRecordingDao
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.data.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `recordings reflects everything the repository holds, mapped to RecordingUiModel`() = runTest(testDispatcher) {
        val repository = RecordingsRepository(FakeRecordingDao())
        val a = repository.add("/rec/a.m4a", 1L, 10L, SyncState.PENDING)
        val b = repository.add("/rec/b.m4a", 2L, 20L, SyncState.SYNCED)
        val viewModel = RecordingsViewModel(
            repository,
            MutableStateFlow(false),
            FakeSystemStatusChecker(),
        )

        assertEquals(
            listOf(
                RecordingUiModel(a, 1L, 10L, SyncState.PENDING),
                RecordingUiModel(b, 2L, 20L, SyncState.SYNCED),
            ),
            viewModel.recordings.value,
        )
    }

    @Test
    fun `delete removes the recording from the repository and from the exposed recordings list`() = runTest(testDispatcher) {
        val repository = RecordingsRepository(FakeRecordingDao())
        val a = repository.add("/rec/a.m4a", 1L, 10L, SyncState.PENDING)
        val b = repository.add("/rec/b.m4a", 2L, 20L, SyncState.SYNCED)
        val viewModel = RecordingsViewModel(
            repository,
            MutableStateFlow(false),
            FakeSystemStatusChecker(),
        )

        viewModel.delete(a)

        assertEquals(
            listOf(RecordingUiModel(b, 2L, 20L, SyncState.SYNCED)),
            viewModel.recordings.value,
        )
        assertEquals(false, repository.pendingSyncRecordings().any { it.id == a })
    }

    @Test
    fun `isRecordingNow mirrors the provided isRecording flow`() = runTest(testDispatcher) {
        val isRecording = MutableStateFlow(false)
        val viewModel = RecordingsViewModel(
            RecordingsRepository(FakeRecordingDao()),
            isRecording,
            FakeSystemStatusChecker(),
        )

        assertEquals(false, viewModel.isRecordingNow.value)

        isRecording.value = true

        assertEquals(true, viewModel.isRecordingNow.value)
    }

    @Test
    fun `setupWarning is MissingMicPermission when hasRecordAudioPermission is false`() = runTest(testDispatcher) {
        val statusChecker = FakeSystemStatusChecker(permissionGranted = false, accessibilityEnabled = true)
        val viewModel = RecordingsViewModel(
            RecordingsRepository(FakeRecordingDao()),
            MutableStateFlow(false),
            statusChecker,
        )

        assertEquals(SetupWarning.MissingMicPermission, viewModel.setupWarning.value)
    }

    @Test
    fun `setupWarning is AccessibilityDisabled when the permission is granted but the accessibility service is not enabled`() = runTest(testDispatcher) {
        val statusChecker = FakeSystemStatusChecker(permissionGranted = true, accessibilityEnabled = false)
        val viewModel = RecordingsViewModel(
            RecordingsRepository(FakeRecordingDao()),
            MutableStateFlow(false),
            statusChecker,
        )

        assertEquals(SetupWarning.AccessibilityDisabled, viewModel.setupWarning.value)
    }

    @Test
    fun `setupWarning is null when both permission and accessibility service are granted-enabled`() = runTest(testDispatcher) {
        val statusChecker = FakeSystemStatusChecker(permissionGranted = true, accessibilityEnabled = true)
        val viewModel = RecordingsViewModel(
            RecordingsRepository(FakeRecordingDao()),
            MutableStateFlow(false),
            statusChecker,
        )

        assertNull(viewModel.setupWarning.value)
    }
}
