package app.cameraroll.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.cameraroll.R
import app.cameraroll.data.CloudSettings
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "sync"
private const val PERIODIC_WORK = "cloud-sync-periodic"

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = CloudSettings.get(applicationContext)
        if (!config.isConfigured) return Result.success()

        setForeground(createForegroundInfo())
        val result = SyncEngine(applicationContext).sync()

        applicationContext.getSystemService(NotificationManager::class.java)
            ?.notify(42, syncNotification(applicationContext.getString(R.string.sync_notification_completed), result.summary(applicationContext)).build())
        return Result.success(workDataOf("summary" to result.summary(applicationContext)))
    }

    private fun createForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notification = syncNotification(applicationContext.getString(R.string.app_name), applicationContext.getString(R.string.sync_notification_syncing)).build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(41, notification)
        }
    }

    private fun syncNotification(title: String, text: String) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.sync_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SyncWorker>().build())
        }
    }
}
