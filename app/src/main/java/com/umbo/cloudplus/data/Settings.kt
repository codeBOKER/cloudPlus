package com.umbo.cloudplus.data

data class Settings(
    val botToken: String = "",
    val chatId: String = "",
    val isAppLockEnabled: Boolean = false,
    val passcodeHash: String = "",
    val passcodeSalt: String = "",
    val isScreenSecurityEnabled: Boolean = false,
    val isConfigured: Boolean = botToken.isNotBlank() && chatId.isNotBlank()
) {
    val cleanBotToken: String
        get() = botToken.trim()

    val cleanChatId: String
        get() = chatId.trim()

    val hasPasscodeSet: Boolean
        get() = passcodeHash.isNotBlank() && passcodeSalt.isNotBlank()
}
