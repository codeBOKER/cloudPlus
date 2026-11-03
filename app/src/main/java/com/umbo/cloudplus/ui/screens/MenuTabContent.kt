package com.umbo.cloudplus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.umbo.cloudplus.ui.components.PasscodeSetupDialog

@Composable
fun MenuTabContent(
    settings: Settings,
    onSaveSettings: (Settings) -> Unit,
    onTestConnection: (Settings, (Result<Boolean>) -> Unit) -> Unit,
    onSetPasscode: (String) -> Unit,
    onRemovePasscode: () -> Unit,
    totalFilesCount: Int,
    modifier: Modifier = Modifier
) {
    var botToken by remember(settings) { mutableStateOf(settings.botToken) }
    var chatId by remember(settings) { mutableStateOf(settings.chatId) }
    var isTokenVisible by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<Boolean>?>(null) }

    var showPasscodeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "إحصائيات التخزين السحابي",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "عدد الملفات المرفوعة: $totalFilesCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Security Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "إعدادات الأمان والحماية",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Lock Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "قفل التطبيق برمز (App Lock)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.isAppLockEnabled) "مفعّل - يتطلب رمز PIN عند فتح التطبيق" else "معطّل",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.isAppLockEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                showPasscodeDialog = true
                            } else {
                                onRemovePasscode()
                            }
                        }
                    )
                }

                if (settings.isAppLockEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showPasscodeDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تغيير رمز PIN")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Screen Security (FLAG_SECURE) Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "حماية الشاشة (FLAG_SECURE)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "منع لقطات الشاشة ومعاينة التطبيق في القائمة الأخيرة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.isScreenSecurityEnabled,
                        onCheckedChange = { isChecked ->
                            val updated = settings.copy(isScreenSecurityEnabled = isChecked)
                            onSaveSettings(updated)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Telegram Bot Config Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "إعدادات البوت والدردشة (Telegram)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token") },
                    placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRstuVWX") },
                    singleLine = true,
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Icon(
                                imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isTokenVisible) "إخفاء الرمز" else "إظهار الرمز"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Chat ID") },
                    placeholder = { Text("123456789 أو -100123456789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            isTestingConnection = true
                            testResult = null
                            val cleanSettings = settings.copy(
                                botToken = botToken.trim(),
                                chatId = chatId.trim()
                            )
                            onTestConnection(cleanSettings) { result ->
                                isTestingConnection = false
                                testResult = result
                            }
                        },
                        enabled = !isTestingConnection && botToken.isNotBlank() && chatId.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("اختبار الاتصال")
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val cleanSettings = settings.copy(
                                botToken = botToken.trim(),
                                chatId = chatId.trim()
                            )
                            onSaveSettings(cleanSettings)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ")
                    }
                }

                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    result.onSuccess {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تم الاتصال بالبوت ومعرف الدردشة بنجاح!",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }.onFailure { error ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error.message ?: "فشل الاتصال",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Telegram Setup Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "دليل الربط وحل خطأ (chat not found)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "1️⃣ إنشاء البوت (Bot Token):\n" +
                            "• ابحث في التلجرام عن @BotFather وأرسل له /newbot.\n" +
                            "• اتبع التعليمات ثم انسخ الـ HTTP API Token وقم بلصقه هنا.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "2️⃣ معرف الدردشة (Chat ID):\n" +
                            "• للمحادثات الخاصة: ابحث عن البوت الذي أنشأته واضغط Start (أرسل له /start). ثم ابحث عن بوت @userinfobot للتعرف على رقم الـ ID الخاص بك (مثال: 123456789).\n" +
                            "• للقنوات والمجموعات: أضف البوت للقناة كأدمن (Admin) مع صلاحية إرسال الرسائل، واستخدم معرف القناة الذي يبدأ بـ -100 (مثال: -1001234567890).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPasscodeDialog) {
        PasscodeSetupDialog(
            onDismiss = { showPasscodeDialog = false },
            onPasscodeSet = { pin ->
                onSetPasscode(pin)
                showPasscodeDialog = false
            }
        )
    }
}
