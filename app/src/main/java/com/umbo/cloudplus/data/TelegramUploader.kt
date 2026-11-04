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
    private val jinaVectorManager = JinaVectorManager(context)

    private fun sanitizeToken(token: String): String {
        var cleaned = token.trim()
        val prefixes = listOf(
            "https://api.telegram.org/bot",
            "http://api.telegram.org/bot",
            "api.telegram.org/bot"
        )
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
                break
            }
        }
        if (cleaned.startsWith("bot", ignoreCase = true) && cleaned.length > 3 && cleaned[3].isDigit()) {
            cleaned = cleaned.substring(3)
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

            val maxTelegramSize = 50 * 1024 * 1024L
            if (fileSize > maxTelegramSize) {
                return@withContext Result.failure(
                    Exception("حجم الملف ($fileName) يتجاوز الحد الأقصى المسموح به لـ Telegram Bot API (50 ميجابايت).")
                )
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
                connectTimeout = 20000
                readTimeout = 120000
                setChunkedStreamingMode(0)
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
                            ?: resultObj?.optJSONObject("animation")
                            ?: resultObj?.optJSONObject("voice")
                        telegramFileId = docObj?.optString("file_id", "") ?: ""
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (telegramFileId.isBlank()) {
                    return@withContext Result.failure(Exception("لم يتم العثور على معرف الملف (file_id) في استجابة التلجرام"))
                }

                var vectorEmbedding: List<Float>? = null
                if (settings.isJinaConfigured) {
                    try {
                        val textContent = jinaVectorManager.extractTextFromFile(uri, fileName, mimeType)
                        val jinaRes = jinaVectorManager.getEmbedding(
                            text = textContent,
                            apiKey = settings.jinaApiKey,
                            task = "retrieval.passage"
                        )
                        vectorEmbedding = jinaRes.getOrNull()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val uploadedFile = UploadedFile(
                    name = fileName,
                    mimeType = mimeType,
                    size = fileSize,
                    telegramFileId = telegramFileId,
                    isEncrypted = true,
                    vector = vectorEmbedding
                )
                onProgress(1f)
                Result.success(uploadedFile)
            } else {
                var errorDetail = responseText
                try {
                    val json = JSONObject(responseText)
                    errorDetail = json.optString("description", responseText)
                } catch (_: Exception) {}
                Result.failure(Exception("خطأ في Telegram API ($responseCode): $errorDetail"))
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

            destinationFile.parentFile?.mkdirs()

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
                val errText = getFileConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $getFileCode"
                var detail = errText
                try {
                    val json = JSONObject(errText)
                    detail = json.optString("description", errText)
                } catch (_: Exception) {}
                return@withContext Result.failure(Exception("فشل الحصول على معلومات الملف من تلجرام: $detail"))
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
                readTimeout = 120000
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

            // 1. Verify Bot Token
            val getMeUrl = "https://api.telegram.org/bot$token/getMe"
            val meConn = (URL(getMeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (meConn.responseCode != HttpURLConnection.HTTP_OK) {
                val errText = meConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                var detail = "Invalid Bot Token (HTTP ${meConn.responseCode})"
                try {
                    val json = JSONObject(errText)
                    detail = json.optString("description", detail)
                } catch (_: Exception) {}
                return@withContext Result.failure(Exception(detail))
            }

            // 2. Verify Chat ID access
            val getChatUrl = "https://api.telegram.org/bot$token/getChat?chat_id=$chatId"
            val chatConn = (URL(getChatUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (chatConn.responseCode != HttpURLConnection.HTTP_OK) {
                val errText = chatConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                var detail = "Chat ID error (HTTP ${chatConn.responseCode})"
                try {
                    val json = JSONObject(errText)
                    detail = json.optString("description", detail)
                } catch (_: Exception) {}
                return@withContext Result.failure(Exception("Bot cannot access Chat ID: $detail"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
