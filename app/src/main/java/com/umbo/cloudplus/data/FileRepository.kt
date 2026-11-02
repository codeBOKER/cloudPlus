package com.umbo.cloudplus.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cloudplus_files", Context.MODE_PRIVATE)

    fun getUploadedFiles(): List<UploadedFile> {
        val jsonString = prefs.getString("files_json", "[]") ?: "[]"
        val list = mutableListOf<UploadedFile>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UploadedFile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Unknown"),
                        mimeType = obj.optString("mimeType", "application/octet-stream"),
                        size = obj.optLong("size", 0L),
                        uploadDate = obj.optLong("uploadDate", System.currentTimeMillis()),
                        telegramFileId = obj.optString("telegramFileId", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.uploadDate }
    }

    fun saveFile(file: UploadedFile) {
        val current = getUploadedFiles().toMutableList()
        current.add(0, file)
        saveList(current)
    }

    fun deleteFile(id: String) {
        val current = getUploadedFiles().toMutableList()
        current.removeAll { it.id == id }
        saveList(current)
    }

    private fun saveList(files: List<UploadedFile>) {
        val jsonArray = JSONArray()
        for (file in files) {
            val obj = JSONObject().apply {
                put("id", file.id)
                put("name", file.name)
                put("mimeType", file.mimeType)
                put("size", file.size)
                put("uploadDate", file.uploadDate)
                put("telegramFileId", file.telegramFileId)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString("files_json", jsonArray.toString()) }
    }
}
