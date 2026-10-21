package com.example.auto_voice_recorder.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [RecordingDao] for unit tests. Not annotated with @Dao, so Room's
 * annotation processor ignores it.
 */
class FakeRecordingDao : RecordingDao {
    private val recordings = mutableListOf<Recording>()
    private var nextId = 1L
    private val state: MutableStateFlow<List<Recording>> = MutableStateFlow(emptyList())

    private fun publish() {
        state.value = recordings.toList()
    }

    override suspend fun insert(recording: Recording): Long {
        val id = nextId++
        recordings.add(recording.copy(id = id))
        publish()
        return id
    }

    override suspend fun findById(id: Long): Recording? = recordings.find { it.id == id }

    override suspend fun delete(id: Long) {
        recordings.removeAll { it.id == id }
        publish()
    }

    override suspend fun updateSyncState(id: Long, state: SyncState) {
        val index = recordings.indexOfFirst { it.id == id }
        if (index >= 0) {
            recordings[index] = recordings[index].copy(syncState = state)
            publish()
        }
    }

    override suspend fun updateAllSyncStates(from: SyncState, to: SyncState): Int {
        var changed = 0
        recordings.replaceAll { recording ->
            if (recording.syncState == from) {
                changed++
                recording.copy(syncState = to)
            } else {
                recording
            }
        }
        if (changed > 0) publish()
        return changed
    }

    override fun observeAll(): Flow<List<Recording>> = state

    override suspend fun pendingSync(): List<Recording> =
        recordings.filter { it.syncState == SyncState.PENDING }
}
