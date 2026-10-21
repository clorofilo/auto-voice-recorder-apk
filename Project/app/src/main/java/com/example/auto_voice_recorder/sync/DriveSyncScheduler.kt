package com.example.auto_voice_recorder.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Enqueues [DriveSyncWorker] as unique work that only runs with a network connection and
 * retries with exponential backoff. Not unit tested: thin wrapper over WorkManager.
 */
object DriveSyncScheduler {

    const val UNIQUE_WORK_NAME = "drive_sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // APPEND_OR_REPLACE rather than KEEP: if a run is already in progress it may have
        // already fetched its PENDING list, so a recording saved now would be missed until
        // the next trigger. Appending queues one more pass after it (a cheap no-op when
        // nothing is left); a previously failed/cancelled chain is replaced instead.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
