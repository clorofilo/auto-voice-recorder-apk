package com.example.auto_voice_recorder.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.auto_voice_recorder.data.AppDatabase
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.security.EncryptedPrefsFactory

/**
 * [CoroutineWorker] that drives a single [DriveSyncUseCase] run against the
 * real repository, [GoogleDriveUploader] and [DefaultSyncPreferences].
 * Enqueued via [DriveSyncScheduler].
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
        if (account == null) {
            SyncStatus.report("Sincronización omitida: no hay cuenta de Google con permiso de Drive.")
            return Result.success()
        }

        val repository = RecordingsRepository(AppDatabase.getInstance(context).recordingDao())
        val uploader = GoogleDriveUploader(context, account)
        // Same encrypted preferences file that the Settings screen and
        // RecordingService read/write, so the sync toggle is actually honoured here.
        val syncPreferences = DefaultSyncPreferences(EncryptedPrefsFactory().create(context))

        val useCase = DriveSyncUseCase(
            repository = repository,
            uploader = uploader,
            syncEnabled = syncPreferences::isEnabled
        )

        val summary = useCase.syncPending()
        // Don't overwrite the uploader's specific error message when something failed.
        if (summary.failed == 0) {
            SyncStatus.report("Última sincronización: ${summary.uploaded} subidas.")
        }
        return if (summary.failed == 0) Result.success() else Result.retry()
    }
}
