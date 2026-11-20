package com.skypass.skytrack.data.sync

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skypass.skytrack.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Sends the queued actions (check-in, breaks, check-out, leave) to the server. Run by Android's
 * WorkManager, so it keeps going when the app is in the background or closed, waits for a working
 * network, retries with back-off and survives a restart of the phone.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun offlineSyncManager(): OfflineSyncManager
    }

    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .offlineSyncManager()
        val report = sync.syncPendingActions()
        return when {
            report.waitingForNetwork -> Result.retry()
            // Waiting for the employee to sign in again; the next sign-in starts a new sync
            else -> Result.success()
        }
    }

    // Before Android 12 an expedited job runs as a short foreground task and needs a notification
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createSyncChannel()
        val notification = NotificationCompat.Builder(applicationContext, SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Saving your attendance")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        return ForegroundInfo(FOREGROUND_ID, notification)
    }

    private fun createSyncChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            android.app.NotificationChannel(SYNC_CHANNEL_ID, "Saving attendance", android.app.NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val UNIQUE_NAME = "skytrack-sync"
        private const val SYNC_CHANNEL_ID = "skytrack_sync"
        private const val FOREGROUND_ID = 7201

        /**
         * Asks Android to deliver the queue as soon as there is a network. Each call adds a run after
         * any run in progress, so an action saved during a sync is never left behind.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
