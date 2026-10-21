package com.example.auto_voice_recorder.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide recording state, observed by UI and updated by
 * [RecordingOrchestrator].
 */
object RecordingStatus {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    /**
     * Visible within the module (and to unit tests, which compile with friend
     * access to main) so orchestration logic can update the flag without
     * exposing a public mutator.
     */
    internal fun setRecording(recording: Boolean) {
        _isRecording.value = recording
    }
}
