package com.example.auto_voice_recorder.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Real [AudioRecorderController] backed by [android.media.MediaRecorder],
 * writing an M4A container (MPEG_4) with AAC audio. Not unit tested: it
 * depends on on-device hardware/codec resources.
 */
class MediaRecorderController(private val context: Context) : AudioRecorderController {

    private var recorder: MediaRecorder? = null
    private var startedAtElapsedRealtimeMs: Long = 0L

    override fun start(outputFile: File) {
        val mediaRecorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        startedAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
    }

    override fun stop(): Long {
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAtElapsedRealtimeMs
        recorder?.apply {
            stop()
            reset()
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
