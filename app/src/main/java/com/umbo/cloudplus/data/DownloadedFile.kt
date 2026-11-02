package com.umbo.cloudplus.data

import java.util.UUID

data class DownloadedFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val size: Long,
    val downloadDate: Long = System.currentTimeMillis(),
    val localPath: String,
    val telegramFileId: String = ""
)
