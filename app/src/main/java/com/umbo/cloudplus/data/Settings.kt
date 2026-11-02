package com.umbo.cloudplus.data

data class Settings(
    val botToken: String = "",
    val chatId: String = "",
    val isConfigured: Boolean = botToken.isNotBlank() && chatId.isNotBlank()
)
