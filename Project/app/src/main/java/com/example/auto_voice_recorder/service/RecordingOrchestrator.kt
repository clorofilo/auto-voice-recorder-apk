package com.example.auto_voice_recorder.service

import com.example.auto_voice_recorder.audio.AudioRecorderController
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.data.SyncState
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Coordinates starting/stopping audio recording, persisting the resulting
 * [com.example.auto_voice_recorder.data.Recording], and keeping
 * [RecordingStatus] in sync.
 */
class RecordingOrchestrator(
    private val repository: RecordingsRepository,
    private val recorder: AudioRecorderController,
    private val outputFileFor: () -> File,
    private val hasRecordPermission: () -> Boolean,
    private val syncEnabled: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Invoked after a finished recording is persisted, with the sync state it was stored in. */
    private val onRecordingSaved: (SyncState) -> Unit = {}
) {

    private var currentFile: File? = null

    fun start() {
        if (RecordingStatus.isRecording.value) return
        if (!hasRecordPermission()) return

        val file = outputFileFor()
        recorder.start(file)
        currentFile = file
        RecordingStatus.setRecording(true)
    }

    fun stop() {
        if (!RecordingStatus.isRecording.value) return

        val durationMs = recorder.stop()
        val file = currentFile
        currentFile = null
        RecordingStatus.setRecording(false)

        if (file != null) {
            val initialState = if (syncEnabled()) SyncState.PENDING else SyncState.DISABLED
            runBlocking {
                repository.add(
                    filePath = file.path,
                    createdAtEpochMs = clock(),
                    durationMs = durationMs,
                    initialState = initialState
                )
            }
            onRecordingSaved(initialState)
        }
    }
}
