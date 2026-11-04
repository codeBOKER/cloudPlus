package com.umbo.cloudplus.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cloudplus_settings_v2", Context.MODE_PRIVATE)

    fun getSettings(): Settings {
        val encryptedToken = prefs.getString("bot_token", "") ?: ""
        val encryptedChatId = prefs.getString("chat_id", "") ?: ""

        val token = SecurityUtils.decrypt(encryptedToken)
        val chatId = SecurityUtils.decrypt(encryptedChatId)

        val isAppLockEnabled = prefs.getBoolean("is_app_lock_enabled", false)
        val passcodeHash = prefs.getString("passcode_hash", "") ?: ""
        val passcodeSalt = prefs.getString("passcode_salt", "") ?: ""
        val isScreenSecurityEnabled = prefs.getBoolean("is_screen_security_enabled", false)

        val isWhatsAppBackupEnabled = prefs.getBoolean("is_whatsapp_backup_enabled", false)
        val backupIntervalMonths = prefs.getInt("backup_interval_months", 3)
        val lastBackupTimestamp = prefs.getLong("last_backup_timestamp", 0L)

        return Settings(
            botToken = token,
            chatId = chatId,
            isAppLockEnabled = isAppLockEnabled,
            passcodeHash = passcodeHash,
            passcodeSalt = passcodeSalt,
            isScreenSecurityEnabled = isScreenSecurityEnabled,
            isWhatsAppBackupEnabled = isWhatsAppBackupEnabled,
            backupIntervalMonths = backupIntervalMonths,
            lastBackupTimestamp = lastBackupTimestamp
        )
    }

    fun saveSettings(settings: Settings) {
        val encryptedToken = SecurityUtils.encrypt(settings.botToken)
        val encryptedChatId = SecurityUtils.encrypt(settings.chatId)

        prefs.edit()
            .putString("bot_token", encryptedToken)
            .putString("chat_id", encryptedChatId)
            .putBoolean("is_app_lock_enabled", settings.isAppLockEnabled)
            .putString("passcode_hash", settings.passcodeHash)
            .putString("passcode_salt", settings.passcodeSalt)
            .putBoolean("is_screen_security_enabled", settings.isScreenSecurityEnabled)
            .putBoolean("is_whatsapp_backup_enabled", settings.isWhatsAppBackupEnabled)
            .putInt("backup_interval_months", settings.backupIntervalMonths)
            .putLong("last_backup_timestamp", settings.lastBackupTimestamp)
            .apply()
    }

    fun updateLastBackupTime(timestamp: Long = System.currentTimeMillis()) {
        val current = getSettings()
        saveSettings(current.copy(lastBackupTimestamp = timestamp))
    }

    fun setPasscode(pin: String): Settings {
        val current = getSettings()
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPasscode(pin, salt)
        val updated = current.copy(
            isAppLockEnabled = true,
            passcodeHash = hash,
            passcodeSalt = salt
        )
        saveSettings(updated)
        return updated
    }

    fun removePasscode(): Settings {
        val current = getSettings()
        val updated = current.copy(
            isAppLockEnabled = false,
            passcodeHash = "",
            passcodeSalt = ""
        )
        saveSettings(updated)
        return updated
    }

    fun verifyPasscode(enteredPin: String): Boolean {
        val settings = getSettings()
        return SecurityUtils.verifyPasscode(enteredPin, settings.passcodeHash, settings.passcodeSalt)
    }
}
