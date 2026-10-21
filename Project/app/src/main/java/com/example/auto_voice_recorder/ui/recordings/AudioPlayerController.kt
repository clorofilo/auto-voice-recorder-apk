package com.example.auto_voice_recorder.ui.recordings

import android.media.MediaPlayer

/**
 * Thin wrapper around [MediaPlayer] that plays back a single `.m4a` recording
 * at a time. Starting a new [play] call stops whatever was already playing.
 */
class AudioPlayerController {

    private var mediaPlayer: MediaPlayer? = null

    fun play(filePath: String, onCompletion: () -> Unit, onError: (Exception) -> Unit = {}) {
        stop()
        val player = MediaPlayer()
        try {
            player.setDataSource(filePath)
            player.setOnCompletionListener {
                stop()
                onCompletion()
            }
            player.prepare()
            player.start()
            mediaPlayer = player
        } catch (e: Exception) {
            player.release()
            onError(e)
        }
    }

    fun stop() {
        mediaPlayer?.apply {
            runCatching { if (isPlaying) stop() }
            release()
        }
        mediaPlayer = null
    }
}
