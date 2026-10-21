package com.example.auto_voice_recorder.input

/** A handle to a scheduled action that can be cancelled before it fires. */
interface Cancellable {
    fun cancel()
}

/**
 * Schedules an [action] to run after [delayMs] milliseconds, returning a
 * [Cancellable] that cancels the pending action. Abstracted so
 * [VolumeGestureDetector] can be unit tested without real timers.
 */
interface GestureScheduler {
    fun scheduleAfter(delayMs: Long, action: () -> Unit): Cancellable
}
