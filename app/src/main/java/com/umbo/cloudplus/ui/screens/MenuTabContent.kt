package com.umbo.cloudplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
    onReopenOnboarding: () -> Unit,
    totalFilesCount: Int
) {
    var botToken by remember(settings.botToken) { mutableStateOf(settings.botToken) }
    var chatId by remember(settings.chatId) { mutableStateOf(settings.chatId) }
    var encryptionPassword by remember(settings.encryptionPassword) { mutableStateOf(settings.encryptionPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Telegram Bot & Security Settings",
                    style = MaterialTheme.typography.titleMedium
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
                    text = "🔐 All files are automatically encrypted (AES-256-GCM) before uploading to Telegram. If a custom password is provided, it will be required to decrypt the files upon download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                testResultText?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.contains("Success", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val newSettings = Settings(
                                botToken = botToken.trim(),
                                chatId = chatId.trim(),
                                encryptionPassword = encryptionPassword
                            )
                            onSaveSettings(newSettings)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Settings")
                    }

                    Button(
                        onClick = {
                            isTesting = true
                            testResultText = "Testing connection..."
                            val testSettings = Settings(
                                botToken = botToken.trim(),
                                chatId = chatId.trim(),
                                encryptionPassword = encryptionPassword
                            )
                            onTestConnection(testSettings) { result ->
                                isTesting = false
                                result.onSuccess {
                                    testResultText = "Success: Connected to Telegram!"
                                }.onFailure { error ->
                                    testResultText = "Error: ${error.message}"
                                }
                            }
                        },
                        enabled = !isTesting && botToken.isNotBlank() && chatId.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isTesting) "Testing..." else "Test Connection")
                    }
                }
            }
        }

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
                    text = "دليل الاستخدام (App Guide)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "أعد مشاهدة الشرح التفاعلي لطريقة استخدام التطبيق وتشفير الملفات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onReopenOnboarding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("عرض شرح استخدام التطبيق")
                }
            }
        }
    }
}
