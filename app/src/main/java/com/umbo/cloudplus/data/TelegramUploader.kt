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
import java.net.URLEncoder

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
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                tempFile.delete()
                Result.failure(Exception(formatTelegramError(errorStream, responseCode)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(settings: Settings): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = sanitizeToken(settings.botToken)
            val chatId = settings.chatId.trim()

            if (token.isBlank() || chatId.isBlank()) {
                return@withContext Result.failure(Exception("رمز البوت (Bot Token) أو معرف الدردشة (Chat ID) فارغ."))
            }

            val getMeUrl = URL("https://api.telegram.org/bot$token/getMe")
            val getMeConn = getMeUrl.openConnection() as HttpURLConnection
            getMeConn.requestMethod = "GET"
            getMeConn.connectTimeout = 10000
            getMeConn.readTimeout = 10000

            if (getMeConn.responseCode != 200) {
                val errorStr = getMeConn.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext Result.failure(Exception(formatTelegramError(errorStr, getMeConn.responseCode)))
            }

            val encodedChatId = URLEncoder.encode(chatId, "UTF-8")
            val getChatUrl = URL("https://api.telegram.org/bot$token/getChat?chat_id=$encodedChatId")
            val getChatConn = getChatUrl.openConnection() as HttpURLConnection
            getChatConn.requestMethod = "GET"
            getChatConn.connectTimeout = 10000
            getChatConn.readTimeout = 10000

            if (getChatConn.responseCode == 200) {
                Result.success(true)
            } else {
                val errorStr = getChatConn.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(Exception(formatTelegramError(errorStr, getChatConn.responseCode)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatTelegramError(errorJson: String?, httpCode: Int): String {
        if (errorJson.isNullOrBlank()) {
            return "HTTP Error $httpCode"
        }
        try {
            val json = JSONObject(errorJson)
            val desc = json.optString("description", "")
            return when {
                desc.contains("chat not found", ignoreCase = true) ->
                    "لم يتم العثور على Chat ID ($httpCode).\n• للمحادثة الشخصية: أرسل /start للبوت أولاً.\n• للقنوات: أضف البوت كأدمن وتأكد أن ID يبدأ بـ -100."
                desc.contains("Unauthorized", ignoreCase = true) ->
                    "رمز البوت (Bot Token) غير صحيح."
                desc.isNotBlank() ->
                    "خطأ التلجرام ($httpCode): $desc"
                else ->
                    "HTTP Error $httpCode"
            }
        } catch (_: Exception) {
            return "HTTP Error $httpCode: $errorJson"
        }
    }
}
