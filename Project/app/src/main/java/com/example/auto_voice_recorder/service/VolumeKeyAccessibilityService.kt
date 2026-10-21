package com.example.auto_voice_recorder.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.auto_voice_recorder.input.HandlerGestureScheduler
import com.example.auto_voice_recorder.input.VolumeGestureDetector
import com.example.auto_voice_recorder.input.VolumeKeyCommandHandler

/**
 * Thin [AccessibilityService] wrapper that turns volume-key events into
 * recording start/stop intents for [RecordingService].
 *
 * Only `KEYCODE_VOLUME_DOWN` (hold for the gesture's hold duration to
 * start) and `KEYCODE_VOLUME_UP` (press to stop) are handled; the
 * power/lock button is never intercepted. [onKeyEvent] always returns
 * `false` so the system still applies the normal volume change alongside
 * the recording control. The gating rules (permission check, only stopping
 * while recording, ignoring key auto-repeat) live in [VolumeKeyCommandHandler].
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    private lateinit var handler: VolumeKeyCommandHandler

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler = VolumeKeyCommandHandler(
            detector = VolumeGestureDetector(scheduler = HandlerGestureScheduler()),
            hasRecordPermission = ::hasRecordAudioPermission,
            isRecording = { RecordingStatus.isRecording.value },
            startRecording = { sendCommand(RecordingService.ACTION_START_RECORDING) },
            stopRecording = { sendCommand(RecordingService.ACTION_STOP_RECORDING) },
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::handler.isInitialized) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> handler.onVolumeDownPressed(isRepeat = event.repeatCount > 0)
                    KeyEvent.ACTION_UP -> handler.onVolumeDownReleased()
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    handler.onVolumeUpPressed()
                }
            }
        }
        return false
    }

    override fun onInterrupt() {
        // No ongoing state to tear down beyond the gesture detector itself,
        // which is cancelled in onUnbind below.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // The service is being disabled/torn down. If the user is mid-hold,
        // cancel the pending scheduled callback so it can't fire a start
        // command against this dying instance afterwards.
        if (::handler.isInitialized) {
            handler.cancel()
        }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service only cares about key events (handled in
        // onKeyEvent); it does not inspect accessibility tree events, and
        // accessibilityEventTypes is set to typeAllMask only because the
        // framework requires a value, not because events are consumed here.
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun sendCommand(action: String) {
        val intent = Intent(this, RecordingService::class.java).setAction(action)
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException / SecurityException:
            // never let a key press crash the accessibility service's process.
            Log.e(TAG, "Could not deliver $action to RecordingService", e)
        }
    }

    private companion object {
        const val TAG = "VolumeKeyA11yService"
    }
}
