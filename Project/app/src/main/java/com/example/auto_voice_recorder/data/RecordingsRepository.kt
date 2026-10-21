package com.example.auto_voice_recorder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingsRepository(
    private val dao: RecordingDao,
    /** Deletes the audio file at the given path; injectable so tests don't touch the disk. */
    private val deleteAudioFile: (String) -> Unit = { path -> File(path).delete() }
) {

    suspend fun add(
        filePath: String,
        createdAtEpochMs: Long,
        durationMs: Long,
        initialState: SyncState
    ): Long = dao.insert(
        Recording(
            filePath = filePath,
            createdAtEpochMs = createdAtEpochMs,
            durationMs = durationMs,
            syncState = initialState
        )
    )

    /** Removes the recording's row and its audio file on disk. */
    suspend fun delete(id: Long) {
        val filePath = dao.findById(id)?.filePath
        dao.delete(id)
        if (filePath != null) {
            withContext(Dispatchers.IO) { deleteAudioFile(filePath) }
        }
    }

    fun observeAll(): Flow<List<Recording>> = dao.observeAll()

    suspend fun markSynced(id: Long) = dao.updateSyncState(id, SyncState.SYNCED)

    suspend fun pendingSyncRecordings(): List<Recording> = dao.pendingSync()

    /**
     * Makes every recording captured while Drive sync was off ([SyncState.DISABLED])
     * eligible for upload ([SyncState.PENDING]). Called when the user turns sync on.
     */
    suspend fun promoteDisabledToPending(): Int =
        dao.updateAllSyncStates(from = SyncState.DISABLED, to = SyncState.PENDING)
}
