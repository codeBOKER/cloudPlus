package com.umbo.cloudplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umbo.cloudplus.data.UploadedFile
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.material.icons.filled.AutoAwesome

@Composable
fun FilesTabContent(
    searchQuery: String,
    files: List<UploadedFile>,
    vectorScores: Map<String, Float> = emptyMap(),
    isVectorSearching: Boolean = false,
    onDownloadFile: (UploadedFile) -> Unit,
    onDeleteFile: (String) -> Unit,
    onUploadClick: () -> Unit
) {
    val filteredFiles = remember(searchQuery, files, vectorScores) {
        if (searchQuery.isBlank()) {
            files
        } else if (vectorScores.isNotEmpty()) {
            // Sort all files by vector similarity score descending so description search always shows ranked results
            files.sortedByDescending { file ->
                val score = vectorScores[file.id] ?: 0f
                val nameMatchBonus = if (file.name.contains(searchQuery, ignoreCase = true)) 1.0f else 0.0f
                score + nameMatchBonus
            }
        } else {
            files.filter { file ->
                file.name.contains(searchQuery, ignoreCase = true) ||
                file.mimeType.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                if (isVectorSearching) {
                    CircularProgressIndicator()
                    Text("جاري البحث باستخدام Jina Vector Search...")
                } else {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (searchQuery.isBlank()) "لا توجد ملفات مرفوعة بعد" else "لا توجد نتائج مطابقة لـ \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (searchQuery.isBlank()) {
                        Button(onClick = onUploadClick) {
                            Text("رفع ملف الآن")
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isVectorSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (searchQuery.isNotBlank() && vectorScores.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "تم تطبيق Jina Vector Search (البحث بالمعنى والتشابه التضميني)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFiles, key = { it.id }) { file ->
                    val similarityScore = vectorScores[file.id]
                    FileItemCard(
                        file = file,
                        similarityScore = similarityScore,
                        onDownload = { onDownloadFile(file) },
                        onDelete = { onDeleteFile(file.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileItemCard(
    file: UploadedFile,
    similarityScore: Float? = null,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(file.uploadDate) { dateFormat.format(Date(file.uploadDate)) }
    val formattedSize = remember(file.size) { formatFileSize(file.size) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (file.isEncrypted) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (similarityScore != null && similarityScore > 0f) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = "⚡ ${(similarityScore * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedSize • $formattedDate" +
                            if (file.isEncrypted) " • Encrypted AES-256" else "" +
                            if (file.vector != null) " • Vector Indexed" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download & Decrypt",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.1f GB", gb)
}
