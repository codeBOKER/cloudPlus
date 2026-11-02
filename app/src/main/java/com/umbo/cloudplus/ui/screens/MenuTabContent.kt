package com.umbo.cloudplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umbo.cloudplus.data.Settings

@Composable
fun MenuTabContent(
    settings: Settings,
    onSaveSettings: (Settings) -> Unit,
    onTestConnection: (Settings, (Result<Unit>) -> Unit) -> Unit,
    totalFilesCount: Int
) {
    var botToken by remember(settings.botToken) { mutableStateOf(settings.botToken) }
    var chatId by remember(settings.chatId) { mutableStateOf(settings.chatId) }
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
                    text = "Telegram Bot Settings",
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

                testResultText?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.contains("Success", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val newSettings = Settings(botToken = botToken.trim(), chatId = chatId.trim())
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
                            val testSettings = Settings(botToken = botToken.trim(), chatId = chatId.trim())
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
    }
}
