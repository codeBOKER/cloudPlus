package com.umbo.cloudplus.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class TelegramUploader(private val context: Context) {

    private fun sanitizeToken(token: String): String {
        var cleaned = token.trim()
        if (cleaned.startsWith("https://api.telegram.org/bot", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("https://api.telegram.org/bot")
        } else if (cleaned.startsWith("api.telegram.org/bot", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("api.telegram.org/bot")
        }
        if (cleaned.startsWith("bot", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("bot")
        }
        return cleaned.trim()
    }

    suspend fun uploadFile(
        uri: Uri,
        fileName: String,
        mimeType: String,
        fileSize: Long,
        settings: Settings,
        onProgress: (Float) -> Unit
    ): Result<UploadedFile> = withContext(Dispatchers.IO) {
        var tempPlainFile: File? = null
        var tempEncryptedFile: File? = null
        try {
            val token = sanitizeToken(settings.botToken)
            val chatId = settings.chatId.trim()

            if (token.isBlank() || chatId.isBlank()) {
                return@withContext Result.failure(Exception("لم يتم ضبط Telegram Bot Token أو Chat ID في الإعدادات"))
            }

            // Step 1: Copy from Uri to plain temp file
            tempPlainFile = File(context.cacheDir, "plain_${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPlainFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (fileSize > 0) {
                            val copyProgress = (totalRead.toFloat() / fileSize.toFloat()) * 0.15f
                            onProgress(copyProgress.coerceIn(0f, 0.15f))
                        }
                    }
                }
            } ?: return@withContext Result.failure(Exception("فشل فتح ملف المصدر"))

            // Step 2: Encrypt file using AES-256
            onProgress(0.15f)
            tempEncryptedFile = File(context.cacheDir, "enc_${System.currentTimeMillis()}_$fileName")
            val encryptResult = CryptoManager.encryptFile(
                inputFile = tempPlainFile,
                outputFile = tempEncryptedFile,
                password = settings.encryptionPassword
            )

            if (encryptResult.isFailure) {
                return@withContext Result.failure(
                    encryptResult.exceptionOrNull() ?: Exception("فشل تشفير الملف")
                )
            }
            onProgress(0.3f)

            // Step 3: Upload encrypted file to Telegram sendDocument API
            val encryptedFileSize = tempEncryptedFile.length()
            val boundary = "Boundary_" + System.currentTimeMillis()
            val urlStr = "https://api.telegram.org/bot$token/sendDocument"
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            connection.outputStream.use { outputStream ->
                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".toByteArray())
                outputStream.write("$chatId\r\n".toByteArray())

                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\n".toByteArray())
                outputStream.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())

                tempEncryptedFile.inputStream().use { fileInput ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalUploaded = 0L
                    while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalUploaded += bytesRead
                        if (encryptedFileSize > 0) {
                            val uploadProgress = 0.3f + (totalUploaded.toFloat() / encryptedFileSize.toFloat()) * 0.7f
                            onProgress(uploadProgress.coerceIn(0.3f, 1f))
                        }
                    }
                }
                outputStream.write("\r\n".toByteArray())
                outputStream.write("--$boundary--\r\n".toByteArray())
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                var telegramFileId = ""
                try {
                    val json = JSONObject(responseText)
                    if (json.optBoolean("ok")) {
                        val resultObj = json.optJSONObject("result")
                        val docObj = resultObj?.optJSONObject("document")
                            ?: resultObj?.optJSONObject("video")
                            ?: resultObj?.optJSONObject("audio")
                        telegramFileId = docObj?.optString("file_id", "") ?: ""
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val uploadedFile = UploadedFile(
                    name = fileName,
                    mimeType = mimeType,
                    size = fileSize,
                    telegramFileId = telegramFileId,
                    isEncrypted = true
                )
                onProgress(1f)
                Result.success(uploadedFile)
            } else {
                Result.failure(Exception("خطأ في Telegram API ($responseCode): $responseText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempPlainFile?.delete()
            tempEncryptedFile?.delete()
        }
    }

    suspend fun downloadAndDecryptFile(
        uploadedFile: UploadedFile,
        settings: Settings,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var tempEncryptedFile: File? = null
        try {
            val token = sanitizeToken(settings.botToken)
            if (token.isBlank()) {
                return@withContext Result.failure(Exception("لم يتم ضبط Telegram Bot Token في الإعدادات"))
            }
            if (uploadedFile.telegramFileId.isBlank()) {
                return@withContext Result.failure(Exception("معرف الملف على التلجرام غير متوفر (File ID missing)"))
            }

            onProgress(0.05f)

            // Step 1: getFile API to get file_path from Telegram
            val getFileUrl = "https://api.telegram.org/bot$token/getFile?file_id=${uploadedFile.telegramFileId}"
            val getFileConn = (URL(getFileUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

            val getFileCode = getFileConn.responseCode
            if (getFileCode != HttpURLConnection.HTTP_OK) {
                val err = getFileConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $getFileCode"
                return@withContext Result.failure(Exception("فشل الحصول على معلومات الملف من تلجرام: $err"))
            }

            val getFileResponseBody = getFileConn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(getFileResponseBody)
            if (!json.optBoolean("ok")) {
                val desc = json.optString("description", "خطأ غير معروف")
                return@withContext Result.failure(Exception("خطأ من تلجرام: $desc"))
            }

            val resultObj = json.getJSONObject("result")
            val filePath = resultObj.getString("file_path")
            val downloadUrl = "https://api.telegram.org/file/bot$token/$filePath"

            onProgress(0.15f)

            // Step 2: Download encrypted file
            tempEncryptedFile = File(context.cacheDir, "dl_enc_${System.currentTimeMillis()}_${uploadedFile.name}")
            val downloadConn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
            }

            val downloadCode = downloadConn.responseCode
            if (downloadCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("فشل تنزيل الملف من تلجرام (HTTP $downloadCode)"))
            }

            val totalSize = downloadConn.contentLengthLong.takeIf { it > 0 } ?: uploadedFile.size
            downloadConn.inputStream.use { input ->
                FileOutputStream(tempEncryptedFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        if (totalSize > 0) {
                            val dlProgress = 0.15f + (totalDownloaded.toFloat() / totalSize.toFloat()) * 0.65f
                            onProgress(dlProgress.coerceIn(0.15f, 0.8f))
                        }
                    }
                }
            }

            onProgress(0.85f)

            // Step 3: Decrypt encrypted file to destination
            val decryptResult = CryptoManager.decryptFile(
                inputFile = tempEncryptedFile,
                outputFile = destinationFile,
                password = settings.encryptionPassword
            )

            if (decryptResult.isFailure) {
                destinationFile.delete()
                return@withContext Result.failure(
                    decryptResult.exceptionOrNull() ?: Exception("فشل فك تشفير الملف")
                )
            }

            onProgress(1.0f)
            Result.success(destinationFile)
        } catch (e: Exception) {
            destinationFile.delete()
            Result.failure(e)
        } finally {
            tempEncryptedFile?.delete()
        }
    }

    suspend fun testConnection(settings: Settings): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sanitizeToken(settings.botToken)
            val chatId = settings.chatId.trim()

            if (token.isBlank() || chatId.isBlank()) {
                return@withContext Result.failure(Exception("Bot Token and Chat ID cannot be empty"))
            }
            val urlStr = "https://api.telegram.org/bot$token/getMe"
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid Bot Token (HTTP $responseCode)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
