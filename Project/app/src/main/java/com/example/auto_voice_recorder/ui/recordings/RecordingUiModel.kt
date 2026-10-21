package com.example.auto_voice_recorder.ui.recordings

import com.example.auto_voice_recorder.data.SyncState

/** Display-ready projection of a [com.example.auto_voice_recorder.data.Recording]. */
data class RecordingUiModel(
    val id: Long,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val syncState: SyncState,
)
