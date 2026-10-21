package com.example.auto_voice_recorder.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Real [AudioRecorderController] backed by [android.media.MediaRecorder],
 * writing an M4A container (MPEG_4) with AAC audio. Not unit tested: it
 * depends on on-device hardware/codec resources.
 *
 * Failures (mic busy, IO error, a stop() too soon after start() with no valid
 * audio) are rethrown to the caller, but the underlying [MediaRecorder] is always
 * released first so a failed attempt never leaks the native recorder.
 */
class MediaRecorderController(private val context: Context) : AudioRecorderController {

    private var recorder: MediaRecorder? = null
    private var startedAtElapsedRealtimeMs: Long = 0L

    override fun start(outputFile: File) {
        // Drop any stale instance from a previous attempt before creating a new one.
        release()
        val mediaRecorder = createMediaRecorder()
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaRecorder.release()
            throw e
        }
        recorder = mediaRecorder
        startedAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
    }

    override fun stop(): Long {
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAtElapsedRealtimeMs
        val current = recorder ?: return elapsedMs
        try {
            // Throws RuntimeException if no valid audio was captured (e.g. stopped
            // immediately after starting); the caller discards the file in that case.
            current.stop()
        } finally {
            current.release()
            recorder = null
        }
        return elapsedMs
    }

    override fun release() {
        recorder?.release()
        recorder = null
    }

    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}
