package com.example.auto_voice_recorder.input

/**
 * Pure translation of volume-key presses into start/stop recording commands, with the
 * safety gates the accessibility service needs. Knows nothing about Android key events;
 * [com.example.auto_voice_recorder.service.VolumeKeyAccessibilityService] maps
 * `KeyEvent`s onto these calls.
 *
 * - Volume-down hold: only the initial press arms the [detector]. Auto-repeat key-down
 *   events while the key is held are ignored, so they can never cancel/reschedule the
 *   pending hold timer.
 * - Start is only sent if [hasRecordPermission] is true at the moment the hold threshold
 *   is reached, and nothing is already recording — otherwise a foreground microphone
 *   service would be started without permission.
 * - Stop is only sent if [isRecording] is true, so ordinary volume-up presses anywhere on
 *   the phone do not spin up the foreground service.
 */
class VolumeKeyCommandHandler(
    private val detector: VolumeGestureDetector,
    private val hasRecordPermission: () -> Boolean,
    private val isRecording: () -> Boolean,
    private val startRecording: () -> Unit,
    private val stopRecording: () -> Unit,
) {

    fun onVolumeDownPressed(isRepeat: Boolean) {
        if (isRepeat) return
        detector.onVolumeDownKeyDown {
            if (hasRecordPermission() && !isRecording()) {
                startRecording()
            }
        }
    }

    fun onVolumeDownReleased() {
        detector.onVolumeDownKeyUp()
    }

    fun onVolumeUpPressed() {
        if (isRecording()) {
            stopRecording()
        }
    }

    /** Cancels any pending hold callback, e.g. when the service is being torn down. */
    fun cancel() {
        detector.onVolumeDownKeyUp()
    }
}
