package com.example.auto_voice_recorder.input

/**
 * Pure, time-based detector for the "hold volume-down for [holdDurationMs]"
 * gesture. Knows nothing about Android key events or recording; callers
 * translate key events into [onVolumeDownKeyDown] / [onVolumeDownKeyUp]
 * calls.
 */
class VolumeGestureDetector(
    private val holdDurationMs: Long = 2000L,
    private val scheduler: GestureScheduler
) {

    private var pending: Cancellable? = null

    /**
     * Call when the volume-down key goes down. Schedules
     * [onHoldThresholdReached] to run after [holdDurationMs] ms. A second
     * call while one is already pending cancels the previous pending
     * callback and schedules a fresh one, rather than stacking callbacks.
     */
    fun onVolumeDownKeyDown(onHoldThresholdReached: () -> Unit) {
        pending?.cancel()
        pending = scheduler.scheduleAfter(holdDurationMs) {
            pending = null
            onHoldThresholdReached()
        }
    }

    /** Call when the volume-down key is released. Cancels any pending callback. */
    fun onVolumeDownKeyUp() {
        pending?.cancel()
        pending = null
    }
}
