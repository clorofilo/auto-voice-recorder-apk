package com.example.auto_voice_recorder.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test-only [GestureScheduler] that records the last scheduled
 * `(delayMs, action)` pair and lets tests manually fire it to simulate
 * elapsed time, instead of relying on real timers.
 */
class FakeGestureScheduler : GestureScheduler {

    var lastDelayMs: Long? = null
        private set
    var lastAction: (() -> Unit)? = null
        private set
    var cancelCallCount: Int = 0
        private set

    override fun scheduleAfter(delayMs: Long, action: () -> Unit): Cancellable {
        lastDelayMs = delayMs
        lastAction = action
        var cancelled = false
        return object : Cancellable {
            override fun cancel() {
                cancelled = true
                cancelCallCount++
            }
        }.also {
            // Track cancellation state via the outer `cancelled` flag so
            // fireIfPending() can still invoke the action if it was never
            // cancelled, even though the Cancellable itself holds no
            // mutable test-visible state.
            isLastCancelled = { cancelled }
        }
    }

    private var isLastCancelled: () -> Boolean = { false }

    /** Manually invokes the most recently scheduled action, as if its delay had elapsed. */
    fun fireIfPending() {
        if (!isLastCancelled()) {
            lastAction?.invoke()
        }
    }
}

class VolumeGestureDetectorTest {

    private lateinit var scheduler: FakeGestureScheduler
    private lateinit var detector: VolumeGestureDetector
    private var holdThresholdReachedCallCount = 0

    @Before
    fun setUp() {
        scheduler = FakeGestureScheduler()
        detector = VolumeGestureDetector(holdDurationMs = 3000L, scheduler = scheduler)
        holdThresholdReachedCallCount = 0
    }

    private fun onHoldThresholdReached() {
        holdThresholdReachedCallCount++
    }

    @Test
    fun `onVolumeDownKeyDown schedules the hold callback after holdDurationMs`() {
        detector.onVolumeDownKeyDown(::onHoldThresholdReached)

        assertEquals(3000L, scheduler.lastDelayMs)
    }

    @Test
    fun `firing the scheduled action after the full hold invokes onHoldThresholdReached`() {
        detector.onVolumeDownKeyDown(::onHoldThresholdReached)

        scheduler.fireIfPending()

        assertEquals(1, holdThresholdReachedCallCount)
    }

    @Test
    fun `onVolumeDownKeyUp before the hold elapses cancels the pending callback`() {
        detector.onVolumeDownKeyDown(::onHoldThresholdReached)

        detector.onVolumeDownKeyUp()
        scheduler.fireIfPending()

        assertEquals(0, holdThresholdReachedCallCount)
        assertTrue(scheduler.cancelCallCount >= 1)
    }

    @Test
    fun `a second onVolumeDownKeyDown while one is already pending replaces rather than stacks the pending callback`() {
        detector.onVolumeDownKeyDown(::onHoldThresholdReached)
        detector.onVolumeDownKeyDown(::onHoldThresholdReached)

        scheduler.fireIfPending()

        assertEquals(1, holdThresholdReachedCallCount)
        assertFalse(scheduler.cancelCallCount == 0)
    }
}
