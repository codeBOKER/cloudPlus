package com.umbo.cloudplus.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        try {
            val token = sanitizeToken(settings.botToken)
            val chatId = settings.chatId.trim()

            if (token.isBlank() || chatId.isBlank()) {
                return@withContext Result.failure(Exception("Telegram Bot Token or Chat ID is not configured"))
            }

            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (fileSize > 0) {
                            val progress = totalRead.toFloat() / fileSize.toFloat()
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                }
            }

            val boundary = "Boundary_" + System.currentTimeMillis()
            val urlStr = "https://api.telegram.org/bot$token/sendDocument"
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            connection.outputStream.use { outputStream ->
                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".toByteArray())
                outputStream.write("$chatId\r\n".toByteArray())

                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\n".toByteArray())
                outputStream.write("Content-Type: $mimeType\r\n\r\n".toByteArray())

                tempFile.inputStream().use { fileInput ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                outputStream.write("\r\n".toByteArray())
                outputStream.write("--$boundary--\r\n".toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                tempFile.delete()
                val uploadedFile = UploadedFile(
                    name = fileName,
                    mimeType = mimeType,
                    size = fileSize
                )
                Result.success(uploadedFile)
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                tempFile.delete()
                Result.failure(Exception("Telegram API error ($responseCode): $errorStream"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
