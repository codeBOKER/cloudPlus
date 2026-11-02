package com.umbo.cloudplus.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DownloadedFileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cloudplus_downloaded_files", Context.MODE_PRIVATE)

    fun getDownloadedFiles(): List<DownloadedFile> {
        val jsonString = prefs.getString("downloaded_files_json", "[]") ?: "[]"
        val list = mutableListOf<DownloadedFile>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DownloadedFile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Unknown"),
                        mimeType = obj.optString("mimeType", "application/octet-stream"),
                        size = obj.optLong("size", 0L),
                        downloadDate = obj.optLong("downloadDate", System.currentTimeMillis()),
                        localPath = obj.optString("localPath", ""),
                        telegramFileId = obj.optString("telegramFileId", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.downloadDate }
    }

    fun saveDownloadedFile(file: DownloadedFile) {
        val current = getDownloadedFiles().toMutableList()
        current.removeAll { it.localPath == file.localPath || (it.telegramFileId.isNotBlank() && it.telegramFileId == file.telegramFileId) }
        current.add(0, file)
        saveList(current)
    }

    fun deleteDownloadedFile(id: String, deletePhysicalFile: Boolean = true) {
        val current = getDownloadedFiles().toMutableList()
        val fileToDelete = current.find { it.id == id }
        if (fileToDelete != null) {
            if (deletePhysicalFile && fileToDelete.localPath.isNotBlank()) {
                try {
                    val f = File(fileToDelete.localPath)
                    if (f.exists()) f.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            current.remove(fileToDelete)
            saveList(current)
        }
    }

    private fun saveList(files: List<DownloadedFile>) {
        val jsonArray = JSONArray()
        for (file in files) {
            val obj = JSONObject().apply {
                put("id", file.id)
                put("name", file.name)
                put("mimeType", file.mimeType)
                put("size", file.size)
                put("downloadDate", file.downloadDate)
                put("localPath", file.localPath)
                put("telegramFileId", file.telegramFileId)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString("downloaded_files_json", jsonArray.toString()) }
    }
}
