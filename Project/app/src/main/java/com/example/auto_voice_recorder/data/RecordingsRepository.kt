package com.example.auto_voice_recorder.data

import kotlinx.coroutines.flow.Flow

class RecordingsRepository(private val dao: RecordingDao) {

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

    suspend fun delete(id: Long) = dao.delete(id)

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
