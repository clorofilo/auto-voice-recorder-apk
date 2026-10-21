package com.example.auto_voice_recorder

import android.content.Context
import androidx.room.Room
import com.example.auto_voice_recorder.data.AppDatabase
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.security.DefaultPinManager
import com.example.auto_voice_recorder.security.EncryptedPrefsFactory
import com.example.auto_voice_recorder.security.PinManager
import com.example.auto_voice_recorder.sync.DefaultSyncPreferences
import com.example.auto_voice_recorder.sync.SyncPreferences
import com.example.auto_voice_recorder.ui.recordings.DefaultSystemStatusChecker
import com.example.auto_voice_recorder.ui.recordings.SystemStatusChecker

/**
 * Hand-rolled dependency container: builds and holds the app's singletons so
 * [MainActivity] and the navigation graph can share one instance of each.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "recordings.db").build()
    }

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
}
