package com.example.auto_voice_recorder.input

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VolumeKeyCommandHandlerTest {

    private lateinit var scheduler: FakeGestureScheduler
    private lateinit var handler: VolumeKeyCommandHandler
    private var permissionGranted = true
    private var recording = false
    private var startCount = 0
    private var stopCount = 0

    @Before
    fun setUp() {
        scheduler = FakeGestureScheduler()
        permissionGranted = true
        recording = false
        startCount = 0
        stopCount = 0
        handler = VolumeKeyCommandHandler(
            detector = VolumeGestureDetector(holdDurationMs = 3000L, scheduler = scheduler),
            hasRecordPermission = { permissionGranted },
            isRecording = { recording },
            startRecording = { startCount++ },
            stopRecording = { stopCount++ },
        )
    }

    @Test
    fun `holding volume-down past the threshold with permission sends start`() {
        handler.onVolumeDownPressed(isRepeat = false)
        scheduler.fireIfPending()

        assertEquals(1, startCount)
    }

    @Test
    fun `hold threshold reached without RECORD_AUDIO permission does not send start`() {
        permissionGranted = false

        handler.onVolumeDownPressed(isRepeat = false)
        scheduler.fireIfPending()

        assertEquals(0, startCount)
    }

    @Test
    fun `hold threshold reached while already recording does not send start again`() {
        recording = true

        handler.onVolumeDownPressed(isRepeat = false)
        scheduler.fireIfPending()

        assertEquals(0, startCount)
    }

    @Test
    fun `auto-repeat key-down events do not cancel or reschedule the pending hold timer`() {
        handler.onVolumeDownPressed(isRepeat = false)
        val cancelsAfterFirstPress = scheduler.cancelCallCount

        repeat(5) { handler.onVolumeDownPressed(isRepeat = true) }

        assertEquals(cancelsAfterFirstPress, scheduler.cancelCallCount)
        scheduler.fireIfPending()
        assertEquals(1, startCount)
    }

    @Test
    fun `volume-up while not recording does not send stop`() {
        handler.onVolumeUpPressed()

        assertEquals(0, stopCount)
    }

    @Test
    fun `volume-up while recording sends stop`() {
        recording = true

        handler.onVolumeUpPressed()

        assertEquals(1, stopCount)
    }
}
