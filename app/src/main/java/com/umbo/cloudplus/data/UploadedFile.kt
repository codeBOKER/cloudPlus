package com.umbo.cloudplus.data

import java.util.UUID

data class UploadedFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val size: Long,
    val uploadDate: Long = System.currentTimeMillis(),
    val telegramFileId: String = "",
    val isEncrypted: Boolean = true,
    val vector: List<Float>? = null
)
