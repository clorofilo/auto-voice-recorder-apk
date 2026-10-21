package com.example.auto_voice_recorder.sync

import com.example.auto_voice_recorder.data.RecordingsRepository
import java.io.File

/** Outcome of a single [DriveSyncUseCase.syncPending] run. */
data class SyncSummary(val uploaded: Int, val failed: Int)

/**
 * Uploads every [com.example.auto_voice_recorder.data.SyncState.PENDING]
 * recording to Drive, marking each successfully uploaded recording as
 * synced. Does nothing when [syncEnabled] returns `false`.
 */
class DriveSyncUseCase(
    private val repository: RecordingsRepository,
    private val uploader: DriveUploader,
    private val syncEnabled: () -> Boolean
) {

    suspend fun syncPending(): SyncSummary {
        if (!syncEnabled()) return SyncSummary(uploaded = 0, failed = 0)

        var uploaded = 0
        var failed = 0

        repository.pendingSyncRecordings().forEach { recording ->
            val result = uploader.upload(File(recording.filePath), recording)
            if (result.isSuccess) {
                repository.markSynced(recording.id)
                uploaded++
            } else {
                failed++
            }
        }

        return SyncSummary(uploaded = uploaded, failed = failed)
    }
}
