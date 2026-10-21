package com.example.auto_voice_recorder.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.auto_voice_recorder.input.HandlerGestureScheduler
import com.example.auto_voice_recorder.input.VolumeGestureDetector

/**
 * Thin [AccessibilityService] wrapper that turns volume-key events into
 * recording start/stop intents for [RecordingService].
 *
 * Only `KEYCODE_VOLUME_DOWN` (hold for the gesture's hold duration to
 * start) and `KEYCODE_VOLUME_UP` (press to stop) are handled; the
 * power/lock button is never intercepted. [onKeyEvent] always returns
 * `false` so the system still applies the normal volume change alongside
 * the recording control.
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    private lateinit var detector: VolumeGestureDetector

    override fun onServiceConnected() {
        super.onServiceConnected()
        detector = VolumeGestureDetector(scheduler = HandlerGestureScheduler())
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> detector.onVolumeDownKeyDown { sendStartRecording() }
                    KeyEvent.ACTION_UP -> detector.onVolumeDownKeyUp()
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    // Always sent, even when nothing is recording:
                    // RecordingService/RecordingOrchestrator treat stop as an
                    // idempotent no-op in that case.
                    sendStopRecording()
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
        // cancel the pending scheduled callback so it can't fire
        // sendStartRecording() against this dying instance afterwards.
        if (::detector.isInitialized) {
            detector.onVolumeDownKeyUp()
        }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service only cares about key events (handled in
        // onKeyEvent); it does not inspect accessibility tree events, and
        // accessibilityEventTypes is set to typeAllMask only because the
        // framework requires a value, not because events are consumed here.
    }

    private fun sendStartRecording() {
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START_RECORDING)
        startForegroundService(intent)
    }

    private fun sendStopRecording() {
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_STOP_RECORDING)
        startForegroundService(intent)
    }
}
