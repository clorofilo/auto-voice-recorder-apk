package com.example.auto_voice_recorder.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.auto_voice_recorder.R
import com.example.auto_voice_recorder.audio.MediaRecorderController
import com.example.auto_voice_recorder.data.AppDatabase
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.data.SyncState
import com.example.auto_voice_recorder.security.EncryptedPrefsFactory
import com.example.auto_voice_recorder.sync.DefaultSyncPreferences
import com.example.auto_voice_recorder.sync.DriveSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that owns the [MediaRecorderController] and the
 * mandatory recording notification. Started/stopped via
 * [ACTION_START_RECORDING] / [ACTION_STOP_RECORDING] intents.
 */
class RecordingService : Service() {

    private val recorder by lazy { MediaRecorderController(applicationContext) }

    private val repository by lazy {
        RecordingsRepository(AppDatabase.getInstance(applicationContext).recordingDao())
    }

    private val orchestrator by lazy {
        RecordingOrchestrator(
            repository = repository,
            recorder = recorder,
            outputFileFor = ::createOutputFile,
            hasRecordPermission = ::hasRecordAudioPermission,
            syncEnabled = ::isSyncEnabled,
            onRecordingSaved = { state ->
                if (state == SyncState.PENDING) DriveSyncScheduler.enqueue(applicationContext)
            },
            onError = { message, error -> Log.e(TAG, message, error) }
        )
    }

    /**
     * Background scope for orchestrator calls, so Room I/O inside
     * [RecordingOrchestrator] never blocks the service's main thread.
     * Limited to one concurrent coroutine: [RecordingOrchestrator] has no
     * internal synchronization (unprotected `currentFile`/`RecordingStatus`
     * access, non-thread-safe `MediaRecorder`), so a fast start->stop from
     * back-to-back `onStartCommand` calls must still be serialized rather
     * than run on two different pool threads at once.
     */
    private val serviceScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                // Must happen synchronously on the main thread, promptly after
                // onStartCommand — Android requires it, so it is not launched
                // into the background scope below.
                if (!tryStartForegroundWithNotification()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                serviceScope.launch {
                    val recording = try {
                        orchestrator.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected failure starting recording", e)
                        RecordingStatus.setRecording(false)
                        false
                    }
                    if (!recording) {
                        // Permission missing or recorder failed: don't leave a
                        // foreground "Recording" notification up with nothing recording.
                        stopForegroundAndSelf()
                    }
                }
            }

            ACTION_STOP_RECORDING -> {
                // startForegroundService() always requires startForeground()
                // within ~5s of service creation (API 29+), even when a
                // fresh instance was created just to be told to stop. Must
                // happen synchronously on the main thread, same as the
                // start branch above, before any backgrounded work.
                if (!tryStartForegroundWithNotification()) {
                    // Can't go foreground; still stop/persist the recording.
                    Log.w(TAG, "Stopping without foreground notification")
                }
                serviceScope.launch {
                    try {
                        orchestrator.stop()
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected failure stopping recording", e)
                        recorder.release()
                        RecordingStatus.setRecording(false)
                    }
                    stopForegroundAndSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        recorder.release()
        super.onDestroy()
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun isSyncEnabled(): Boolean =
        DefaultSyncPreferences(EncryptedPrefsFactory().create(applicationContext)).isEnabled()

    private fun createOutputFile(): File {
        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        return File(recordingsDir, "recording_${System.currentTimeMillis()}.m4a")
    }

    /**
     * Wraps [startForegroundWithNotification] so a refusal from the system
     * (e.g. ForegroundServiceStartNotAllowedException, or a SecurityException
     * for the microphone FGS type without RECORD_AUDIO) doesn't crash the process.
     */
    private fun tryStartForegroundWithNotification(): Boolean =
        try {
            startForegroundWithNotification()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground recording service", e)
            false
        }

    private fun startForegroundWithNotification() {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentText(getString(R.string.notification_recording_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START_RECORDING = "com.example.auto_voice_recorder.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.auto_voice_recorder.action.STOP_RECORDING"

        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
    }
}
