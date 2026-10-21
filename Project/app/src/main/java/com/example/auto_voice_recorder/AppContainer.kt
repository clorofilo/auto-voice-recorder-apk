package com.example.auto_voice_recorder

import android.content.Context
import com.example.auto_voice_recorder.data.AppDatabase
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.security.DefaultPinManager
import com.example.auto_voice_recorder.security.EncryptedPrefsFactory
import com.example.auto_voice_recorder.security.PinManager
import com.example.auto_voice_recorder.sync.DefaultSyncPreferences
import com.example.auto_voice_recorder.sync.DriveSyncScheduler
import com.example.auto_voice_recorder.sync.SyncPreferences
import com.example.auto_voice_recorder.ui.recordings.DefaultSystemStatusChecker
import com.example.auto_voice_recorder.ui.recordings.SystemStatusChecker

/**
 * Hand-rolled dependency container: builds and holds the app's singletons so
 * [MainActivity] and the navigation graph can share one instance of each.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Shared process-wide instance, so UI Flows see writes made by the service/worker. */
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val recordingsRepository: RecordingsRepository by lazy {
        RecordingsRepository(database.recordingDao())
    }

    val pinManager: PinManager by lazy {
        DefaultPinManager(EncryptedPrefsFactory().create(appContext))
    }

    val syncPreferences: SyncPreferences by lazy {
        DefaultSyncPreferences(EncryptedPrefsFactory().create(appContext))
    }

    val systemStatusChecker: SystemStatusChecker by lazy {
        DefaultSystemStatusChecker(appContext)
    }

    /** Enqueues a [com.example.auto_voice_recorder.sync.DriveSyncWorker] run. */
    fun scheduleDriveSync() {
        DriveSyncScheduler.enqueue(appContext)
    }
}
