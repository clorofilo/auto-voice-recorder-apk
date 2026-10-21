package com.example.auto_voice_recorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Recording::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        private const val DATABASE_NAME = "recordings.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Process-wide singleton. [com.example.auto_voice_recorder.service.RecordingService],
         * [com.example.auto_voice_recorder.sync.DriveSyncWorker] and the UI's
         * [com.example.auto_voice_recorder.AppContainer] must all share this one instance:
         * Room only notifies `Flow` observers (e.g. the recordings list) of writes made
         * through the same [RoomDatabase] instance, so separate builders on the same file
         * leave the UI showing a stale list.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
