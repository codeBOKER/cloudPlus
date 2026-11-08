package com.umbo.cloudplus.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cloudplus_settings", Context.MODE_PRIVATE)

    fun getSettings(): Settings {
        val token = prefs.getString("bot_token", "") ?: ""
        val chatId = prefs.getString("chat_id", "") ?: ""
        val password = prefs.getString("encryption_password", "") ?: ""
        val jinaKey = prefs.getString("jina_api_key", "") ?: ""
        return Settings(
            botToken = token,
            chatId = chatId,
            encryptionPassword = password,
            jinaApiKey = jinaKey,
            isConfigured = token.isNotBlank() && chatId.isNotBlank()
        )
    }

    fun saveSettings(settings: Settings) {
        prefs.edit()
            .putString("bot_token", settings.botToken)
            .putString("chat_id", settings.chatId)
            .putString("encryption_password", settings.encryptionPassword)
            .putString("jina_api_key", settings.jinaApiKey)
            .apply()
    }
}
