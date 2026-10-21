package com.example.auto_voice_recorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val syncState: SyncState
)
