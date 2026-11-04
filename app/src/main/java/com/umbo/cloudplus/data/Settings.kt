package com.umbo.cloudplus.data

data class Settings(
    val botToken: String = "",
    val chatId: String = "",
    val encryptionPassword: String = "",
    val jinaApiKey: String = "",
    val isConfigured: Boolean = botToken.isNotBlank() && chatId.isNotBlank(),
    val isJinaConfigured: Boolean = jinaApiKey.isNotBlank()
)
