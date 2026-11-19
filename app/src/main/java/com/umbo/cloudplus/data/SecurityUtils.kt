package com.umbo.cloudplus.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPasscode(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$pin:$salt".toByteArray(Charsets.UTF_8)
        var hash = digest.digest(combined)
        for (i in 0 until 1000) {
            digest.reset()
            hash = digest.digest(hash)
        }
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPasscode(enteredPin: String, storedHash: String, storedSalt: String): Boolean {
        if (storedHash.isBlank() || storedSalt.isBlank()) return false
        val computedHash = hashPasscode(enteredPin, storedSalt)
        return MessageDigest.isEqual(computedHash.toByteArray(), storedHash.toByteArray())
    }
}
