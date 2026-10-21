package com.example.auto_voice_recorder.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.auto_voice_recorder.R
import com.example.auto_voice_recorder.audio.MediaRecorderController
import com.example.auto_voice_recorder.data.AppDatabase
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.security.EncryptedPrefsFactory
import com.example.auto_voice_recorder.sync.DefaultSyncPreferences
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
        RecordingsRepository(getDatabase(applicationContext).recordingDao())
    }

    private val orchestrator by lazy {
        RecordingOrchestrator(
            repository = repository,
            recorder = recorder,
            outputFileFor = ::createOutputFile,
            hasRecordPermission = ::hasRecordAudioPermission,
            syncEnabled = ::isSyncEnabled
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
                startForegroundWithNotification()
                serviceScope.launch { orchestrator.start() }
            }

            ACTION_STOP_RECORDING -> {
                // startForegroundService() always requires startForeground()
                // within ~5s of service creation (API 29+), even when a
                // fresh instance was created just to be told to stop (e.g.
                // volume-up pressed while nothing is recording). Must
                // happen synchronously on the main thread, same as the
                // start branch above, before any backgrounded work.
                startForegroundWithNotification()
                serviceScope.launch {
                    orchestrator.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
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

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1

        @Volatile
        private var databaseInstance: AppDatabase? = null

        /**
         * Lazily-created singleton [AppDatabase]. A full DI container is out of
         * scope for this task and arrives later; this keeps RecordingService
         * self-contained in the meantime.
         */
        private fun getDatabase(context: Context): AppDatabase =
            databaseInstance ?: synchronized(this) {
                databaseInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recordings.db"
                ).build().also { databaseInstance = it }
            }
    }
}
