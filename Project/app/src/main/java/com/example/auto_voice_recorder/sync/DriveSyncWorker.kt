package com.example.auto_voice_recorder.sync

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.auto_voice_recorder.data.AppDatabase
import com.example.auto_voice_recorder.data.RecordingsRepository

/**
 * [CoroutineWorker] that drives a single [DriveSyncUseCase] run against the
 * real repository, [GoogleDriveUploader] and [DefaultSyncPreferences].
 */
class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // No signed-in Google account is a stable precondition, not a
        // transient failure — retrying would back off forever without ever
        // resolving it. Treat it the same as syncEnabled() == false inside
        // DriveSyncUseCase: nothing to sync right now, not an error.
        val account = GoogleSignInHelper(context).lastAccount(context)
            ?: return Result.success()

        val repository = RecordingsRepository(getDatabase(context).recordingDao())
        val uploader = GoogleDriveUploader(context, account)
        val syncPreferences = DefaultSyncPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )

        val useCase = DriveSyncUseCase(
            repository = repository,
            uploader = uploader,
            syncEnabled = syncPreferences::isEnabled
        )

        val summary = useCase.syncPending()
        return if (summary.failed == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val PREFS_NAME = "drive_sync_prefs"

        @Volatile
        private var databaseInstance: AppDatabase? = null

        /**
         * Lazily-created singleton [AppDatabase]. A full DI container is out
         * of scope for this task; mirrors the same pattern used by
         * [com.example.auto_voice_recorder.service.RecordingService].
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
