package com.umbo.cloudplus.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WhatsAppBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settingsRepository = SettingsRepository(context)
        val fileRepository = FileRepository(context)
        val uploader = TelegramUploader(context)
        val scanner = WhatsAppMediaScanner(context)

        val settings = settingsRepository.getSettings()
        if (!settings.isConfigured || !settings.isWhatsAppBackupEnabled) {
            return Result.success()
        }

        try {
            val mediaItems = scanner.scanWhatsAppMedia(settings.lastBackupTimestamp)
            if (mediaItems.isEmpty()) {
                settingsRepository.updateLastBackupTime()
                return Result.success()
            }

            for (item in mediaItems) {
                val uploadResult = uploader.uploadFile(
                    uri = item.uri,
                    fileName = item.name,
                    mimeType = item.mimeType,
                    fileSize = item.size,
                    settings = settings,
                    onProgress = {}
                )

                uploadResult.onSuccess { uploadedFile ->
                    fileRepository.saveFile(uploadedFile)
                }
            }

            settingsRepository.updateLastBackupTime()
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
