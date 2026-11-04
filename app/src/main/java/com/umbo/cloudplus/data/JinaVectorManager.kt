package com.umbo.cloudplus.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

class JinaVectorManager(private val context: Context) {

    suspend fun getEmbedding(
        text: String,
        apiKey: String,
        task: String = "retrieval.passage"
    ): Result<List<Float>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Jina API Key is empty"))
        }

        try {
            val url = URL("https://api.jina.ai/v1/embeddings")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $cleanKey")
            }

            val requestJson = JSONObject().apply {
                put("model", "jina-embeddings-v3")
                val inputArr = JSONArray()
                // Limit text length to prevent payload overflow
                inputArr.put(text.take(4000))
                put("input", inputArr)
                put("task", task)
            }

            connection.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val json = JSONObject(responseText)
                val dataArray = json.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val firstItem = dataArray.getJSONObject(0)
                    val embeddingArray = firstItem.getJSONArray("embedding")
                    val vectorList = mutableListOf<Float>()
                    for (i in 0 until embeddingArray.length()) {
                        vectorList.add(embeddingArray.getDouble(i).toFloat())
                    }
                    Result.success(vectorList)
                } else {
                    Result.failure(Exception("No embedding data in Jina response"))
                }
            } else {
                var detail = responseText
                try {
                    val json = JSONObject(responseText)
                    detail = json.optString("detail", json.optString("message", responseText))
                } catch (_: Exception) {}
                Result.failure(Exception("Jina API error ($responseCode): $detail"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractTextFromFile(
        uri: Uri,
        fileName: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("File Name: ").append(fileName).append("\n")
        sb.append("Mime Type: ").append(mimeType).append("\n")

        val isTextType = mimeType.startsWith("text/", ignoreCase = true) ||
                mimeType.contains("json", ignoreCase = true) ||
                mimeType.contains("xml", ignoreCase = true) ||
                mimeType.contains("csv", ignoreCase = true) ||
                fileName.endsWith(".txt", ignoreCase = true) ||
                fileName.endsWith(".md", ignoreCase = true) ||
                fileName.endsWith(".json", ignoreCase = true) ||
                fileName.endsWith(".csv", ignoreCase = true) ||
                fileName.endsWith(".kt", ignoreCase = true) ||
                fileName.endsWith(".java", ignoreCase = true) ||
                fileName.endsWith(".py", ignoreCase = true) ||
                fileName.endsWith(".html", ignoreCase = true) ||
                fileName.endsWith(".xml", ignoreCase = true) ||
                fileName.endsWith(".js", ignoreCase = true)

        if (isTextType) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = inputStream.bufferedReader()
                    val charBuffer = CharArray(2048)
                    val readChars = reader.read(charBuffer, 0, 2048)
                    if (readChars > 0) {
                        sb.append("Content Preview:\n")
                        sb.append(String(charBuffer, 0, readChars))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        sb.toString()
    }

    fun computeCosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.isEmpty() || vectorB.isEmpty() || vectorA.size != vectorB.size) {
            return 0f
        }
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vectorA.indices) {
            val a = vectorA[i]
            val b = vectorB[i]
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        val denominator = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        return if (denominator > 0.0) {
            (dotProduct / denominator).toFloat()
        } else {
            0f
        }
    }

    suspend fun testConnection(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val testResult = getEmbedding("Test connection string", apiKey, task = "text-matching")
        if (testResult.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(testResult.exceptionOrNull() ?: Exception("Jina test failed"))
        }
    }
}
