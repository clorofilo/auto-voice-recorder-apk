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
 *
 * Recorder failures never propagate: a failed start or stop releases the
 * recorder, deletes the partial file, leaves [RecordingStatus] at `false`
 * and persists nothing, then reports the error via [onError].
 */
class RecordingOrchestrator(
    private val repository: RecordingsRepository,
    private val recorder: AudioRecorderController,
    private val outputFileFor: () -> File,
    private val hasRecordPermission: () -> Boolean,
    private val syncEnabled: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Invoked after a finished recording is persisted, with the sync state it was stored in. */
    private val onRecordingSaved: (SyncState) -> Unit = {},
    /** Internal-log hook for recorder failures (kept out of this class so it stays JVM-testable). */
    private val onError: (message: String, error: Throwable) -> Unit = { _, _ -> }
) {

    private var currentFile: File? = null

    /**
     * Starts a recording unless one is already running or the permission is missing.
     *
     * @return `true` if a recording is in progress after the call, `false` if nothing is
     * recording (permission denied or the recorder failed to start) — the caller should
     * then tear down its foreground state.
     */
    fun start(): Boolean {
        if (RecordingStatus.isRecording.value) return true
        if (!hasRecordPermission()) return false

        var file: File? = null
        try {
            file = outputFileFor()
            recorder.start(file)
        } catch (e: Exception) {
            onError("Failed to start recording", e)
            recorder.release()
            file?.delete()
            return false
        }
        currentFile = file
        RecordingStatus.setRecording(true)
        return true
    }

    fun stop() {
        if (!RecordingStatus.isRecording.value) return

        val file = currentFile
        currentFile = null
        val durationMs = try {
            recorder.stop()
        } catch (e: Exception) {
            // Mic error, IO error, or stopped before any valid audio was captured:
            // the file is corrupt/empty, so discard it instead of persisting it.
            onError("Failed to stop recording; discarding it", e)
            recorder.release()
            file?.delete()
            null
        } finally {
            RecordingStatus.setRecording(false)
        }

        if (file != null && durationMs != null) {
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
