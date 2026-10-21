package com.example.auto_voice_recorder.service

import com.example.auto_voice_recorder.audio.AudioRecorderController
import java.io.File

/**
 * In-memory [AudioRecorderController] for unit tests. Records calls to
 * start/stop/release and returns a settable fixed duration from [stop].
 * Set [startError] / [stopError] to simulate recorder failures.
 */
class FakeAudioRecorderController : AudioRecorderController {

    var durationMsToReturn: Long = 0L
    var startError: Exception? = null
    var stopError: Exception? = null

    var startCallCount: Int = 0
        private set
    var stopCallCount: Int = 0
        private set
    var releaseCallCount: Int = 0
        private set
    var lastStartedFile: File? = null
        private set

    override fun start(outputFile: File) {
        startCallCount++
        lastStartedFile = outputFile
        startError?.let { throw it }
    }

    override fun stop(): Long {
        stopCallCount++
        stopError?.let { throw it }
        return durationMsToReturn
    }

    override fun release() {
        releaseCallCount++
    }
}
