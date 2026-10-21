package com.example.auto_voice_recorder.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Recording::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}
