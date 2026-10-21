package com.example.auto_voice_recorder.service

import com.example.auto_voice_recorder.audio.AudioRecorderController
import java.io.File

/**
 * In-memory [AudioRecorderController] for unit tests. Records calls to
 * start/stop/release and returns a settable fixed duration from [stop].
 */
class FakeAudioRecorderController : AudioRecorderController {

    var durationMsToReturn: Long = 0L

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
    }

    override fun stop(): Long {
        stopCallCount++
        return durationMsToReturn
    }

    override fun release() {
        releaseCallCount++
    }
}
