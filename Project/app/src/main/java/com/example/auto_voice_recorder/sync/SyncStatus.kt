package com.example.auto_voice_recorder.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide outcome of the latest Drive sync run, so the settings screen can show why
 * recordings are still pending without needing logcat. Reset when the process restarts.
 */
object SyncStatus {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun report(message: String) {
        _message.value = message
    }
}
