package com.umbo.cloudplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.umbo.cloudplus.data.Settings

@Composable
fun MenuTabContent(
    settings: Settings,
    onSaveSettings: (Settings) -> Unit,
    onTestConnection: (Settings, (Result<Unit>) -> Unit) -> Unit,
    onTestJinaConnection: (String, (Result<Unit>) -> Unit) -> Unit,
    onReindexFiles: (() -> Unit)? = null,
    filesWithoutVectorsCount: Int = 0,
    totalFilesCount: Int
) {
    var botToken by remember(settings.botToken) { mutableStateOf(settings.botToken) }
    var chatId by remember(settings.chatId) { mutableStateOf(settings.chatId) }
    var encryptionPassword by remember(settings.encryptionPassword) { mutableStateOf(settings.encryptionPassword) }
    var jinaApiKey by remember(settings.jinaApiKey) { mutableStateOf(settings.jinaApiKey) }

    var passwordVisible by remember { mutableStateOf(false) }
    var jinaKeyVisible by remember { mutableStateOf(false) }

    var telegramTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingTelegram by remember { mutableStateOf(false) }

    var jinaTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingJina by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cloud Stats Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cloud Stats",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Total uploaded files: $totalFilesCount",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (filesWithoutVectorsCount > 0) {
                    Text(
                        text = "Files pending vector indexing: $filesWithoutVectorsCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Unified Configuration Card Container
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Telegram Bot & Security Settings",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Chat ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = encryptionPassword,
                    onValueChange = { encryptionPassword = it },
                    label = { Text("Encryption Password (Optional)") },
                    placeholder = { Text("Leave blank for KeyStore auto-key") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "🔐 All files are automatically encrypted (AES-256-GCM) before uploading to Telegram.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Jina AI Vector Search Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Jina AI Vector Search Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                OutlinedTextField(
                    value = jinaApiKey,
                    onValueChange = { jinaApiKey = it },
                    label = { Text("Jina API Key") },
                    placeholder = { Text("jina_...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { jinaKeyVisible = !jinaKeyVisible }) {
                            Icon(
                                imageVector = if (jinaKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (jinaKeyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    visualTransformation = if (jinaKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "⚡ Vector embeddings are requested from Jina AI when uploading files and saved in local database for intelligent similarity search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Test Status Messages
                telegramTestResult?.let { result ->
                    Text(
                        text = "Telegram: $result",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.contains("Success", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                jinaTestResult?.let { result ->
                    Text(
                        text = "Jina AI: $result",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.contains("Success", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Unified Action Buttons Container
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val newSettings = Settings(
                                botToken = botToken.trim(),
                                chatId = chatId.trim(),
                                encryptionPassword = encryptionPassword,
                                jinaApiKey = jinaApiKey.trim()
                            )
                            onSaveSettings(newSettings)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("Save All Settings")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTestingTelegram = true
                                telegramTestResult = "Testing connection..."
                                val testSettings = Settings(
                                    botToken = botToken.trim(),
                                    chatId = chatId.trim(),
                                    encryptionPassword = encryptionPassword,
                                    jinaApiKey = jinaApiKey.trim()
                                )
                                onTestConnection(testSettings) { result ->
                                    isTestingTelegram = false
                                    result.onSuccess {
                                        telegramTestResult = "Success: Connected to Telegram!"
                                    }.onFailure { error ->
                                        telegramTestResult = "Error: ${error.message}"
                                    }
                                }
                            },
                            enabled = !isTestingTelegram && botToken.isNotBlank() && chatId.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isTestingTelegram) "Testing..." else "Test Telegram")
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingJina = true
                                jinaTestResult = "Testing API Key..."
                                onTestJinaConnection(jinaApiKey.trim()) { result ->
                                    isTestingJina = false
                                    result.onSuccess {
                                        jinaTestResult = "Success: Jina API Key is valid!"
                                    }.onFailure { error ->
                                        jinaTestResult = "Error: ${error.message}"
                                    }
                                }
                            },
                            enabled = !isTestingJina && jinaApiKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isTestingJina) "Testing..." else "Test Jina API")
                        }
                    }

                    if (onReindexFiles != null && filesWithoutVectorsCount > 0 && jinaApiKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = onReindexFiles,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Generate Vectors for $filesWithoutVectorsCount Existing Files")
                        }
                    }
                }
            }
        }
    }
}
