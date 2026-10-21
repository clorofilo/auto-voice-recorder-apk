package com.example.auto_voice_recorder.service

import com.example.auto_voice_recorder.data.FakeRecordingDao
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.data.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class RecordingOrchestratorTest {

    private lateinit var repository: RecordingsRepository
    private lateinit var recorder: FakeAudioRecorderController
    private val outputFile = File("/tmp/test-recording.m4a")

    private var permissionGranted = true
    private var syncIsEnabled = true
    private val savedStates = mutableListOf<SyncState>()

    @Before
    fun setUp() {
        repository = RecordingsRepository(FakeRecordingDao())
        recorder = FakeAudioRecorderController()
        permissionGranted = true
        syncIsEnabled = true
        savedStates.clear()
        // Reset shared singleton state between tests.
        RecordingStatus.setRecording(false)
    }

    private fun newOrchestrator(): RecordingOrchestrator = RecordingOrchestrator(
        repository = repository,
        recorder = recorder,
        outputFileFor = { outputFile },
        hasRecordPermission = { permissionGranted },
        syncEnabled = { syncIsEnabled },
        clock = { 1234L },
        onRecordingSaved = { savedStates += it }
    )

    @Test
    fun `stop notifies onRecordingSaved with the stored sync state so the caller can schedule a sync`() {
        syncIsEnabled = true
        val orchestrator = newOrchestrator()
        orchestrator.start()

        orchestrator.stop()

        assertEquals(listOf(SyncState.PENDING), savedStates)
    }

    @Test
    fun `stop while not recording does not notify onRecordingSaved`() {
        newOrchestrator().stop()

        assertTrue(savedStates.isEmpty())
    }

    @Test
    fun `start begins a recording and flips RecordingStatus isRecording to true`() {
        val orchestrator = newOrchestrator()

        orchestrator.start()

        assertEquals(1, recorder.startCallCount)
        assertTrue(RecordingStatus.isRecording.value)
    }

    @Test
    fun `start when RECORD_AUDIO permission is denied does not call recorder start and leaves isRecording false`() {
        permissionGranted = false
        val orchestrator = newOrchestrator()

        orchestrator.start()

        assertEquals(0, recorder.startCallCount)
        assertFalse(RecordingStatus.isRecording.value)
    }

    @Test
    fun `start while already recording is a no-op and does not call recorder start a second time`() {
        val orchestrator = newOrchestrator()

        orchestrator.start()
        orchestrator.start()

        assertEquals(1, recorder.startCallCount)
    }

    @Test
    fun `stop while recording stops the recorder persists a Recording with the returned duration and flips isRecording to false`() =
        runTest {
            recorder.durationMsToReturn = 4242L
            val orchestrator = newOrchestrator()
            orchestrator.start()

            orchestrator.stop()

            assertEquals(1, recorder.stopCallCount)
            assertFalse(RecordingStatus.isRecording.value)

            val stored = repository.observeAll().first().single()
            assertEquals(4242L, stored.durationMs)
            assertEquals(outputFile.path, stored.filePath)
            assertEquals(1234L, stored.createdAtEpochMs)
        }

    @Test
    fun `stop while not recording is a no-op and does not touch the repository`() = runTest {
        val orchestrator = newOrchestrator()

        orchestrator.stop()

        assertEquals(0, recorder.stopCallCount)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `stop persists syncState PENDING when syncEnabled is true, DISABLED when false`() = runTest {
        syncIsEnabled = true
        val enabledOrchestrator = newOrchestrator()
        enabledOrchestrator.start()
        enabledOrchestrator.stop()

        val pendingRecording = repository.observeAll().first().single()
        assertEquals(SyncState.PENDING, pendingRecording.syncState)

        // Fresh repository + recorder for the second scenario to avoid cross-contamination.
        repository = RecordingsRepository(FakeRecordingDao())
        recorder = FakeAudioRecorderController()
        syncIsEnabled = false
        val disabledOrchestrator = newOrchestrator()
        disabledOrchestrator.start()
        disabledOrchestrator.stop()

        val disabledRecording = repository.observeAll().first().single()
        assertEquals(SyncState.DISABLED, disabledRecording.syncState)
    }
}
