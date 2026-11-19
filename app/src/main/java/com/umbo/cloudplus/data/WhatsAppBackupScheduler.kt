package com.umbo.cloudplus.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WhatsAppBackupScheduler {
    private const val WORK_NAME = "WhatsAppBackupPeriodicWork"
    private const val ONE_TIME_WORK_NAME = "WhatsAppBackupOneTimeWork"

    fun updateBackupSchedule(context: Context, settings: Settings) {
        val workManager = WorkManager.getInstance(context)

        if (!settings.isWhatsAppBackupEnabled || !settings.isConfigured) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val days = when (settings.backupIntervalMonths) {
            1 -> 30L
            3 -> 90L  // Default: Every 3 Months
            6 -> 180L
            else -> 90L
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<WhatsAppBackupWorker>(
            days, TimeUnit.DAYS,
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }

    fun runImmediateBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<WhatsAppBackupWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )
    }

    fun cancelSchedule(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
