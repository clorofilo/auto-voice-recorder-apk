package com.example.auto_voice_recorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: Recording): Long

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recordings SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: Long, state: SyncState)

    /** Moves every recording currently in [from] to [to]; returns the number of rows changed. */
    @Query("UPDATE recordings SET syncState = :to WHERE syncState = :from")
    suspend fun updateAllSyncStates(from: SyncState, to: SyncState): Int

    @Query("SELECT * FROM recordings ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE syncState = 'PENDING' ORDER BY createdAtEpochMs ASC")
    suspend fun pendingSync(): List<Recording>
}
