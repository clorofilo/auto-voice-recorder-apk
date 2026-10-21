package com.example.auto_voice_recorder.audio

import java.io.File

/**
 * Abstraction over the platform audio recorder so that recording orchestration
 * logic can be unit tested without a real [android.media.MediaRecorder].
 */
interface AudioRecorderController {

    /** Starts recording audio into [outputFile]. */
    fun start(outputFile: File)

    /** Stops the current recording and returns its duration in milliseconds. */
    fun stop(): Long

    /** Releases any resources held by the underlying recorder. */
    fun release()
}
