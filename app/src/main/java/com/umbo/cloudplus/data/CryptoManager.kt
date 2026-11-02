package com.umbo.cloudplus.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val MAGIC_HEADER = "CPENC1"
    private const val KEYTYPE_KEYSTORE: Byte = 0x01
    private const val KEYTYPE_PASSWORD: Byte = 0x02

    private const val KEYSTORE_ALIAS = "CloudPlusAESKey"

    private fun getOrCreateKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val builder = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    private fun derivePasswordKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun getSecretKey(password: String?, salt: ByteArray, keyType: Byte): SecretKey {
        return if (keyType == KEYTYPE_PASSWORD && !password.isNullOrBlank()) {
            derivePasswordKey(password, salt)
        } else {
            getOrCreateKeyStoreKey()
        }
    }

    fun encryptFile(inputFile: File, outputFile: File, password: String?): Result<Unit> {
        return try {
            val hasPassword = !password.isNullOrBlank()
            val keyType = if (hasPassword) KEYTYPE_PASSWORD else KEYTYPE_KEYSTORE

            val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

            val secretKey = if (!password.isNullOrBlank()) {
                derivePasswordKey(password, salt)
            } else {
                getOrCreateKeyStoreKey()
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            FileOutputStream(outputFile).use { fos ->
                fos.write(MAGIC_HEADER.toByteArray(Charsets.US_ASCII))
                fos.write(byteArrayOf(keyType))
                fos.write(iv)
                fos.write(salt)

                FileInputStream(inputFile).use { fis ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val output = cipher.update(buffer, 0, bytesRead)
                        if (output != null && output.isNotEmpty()) {
                            fos.write(output)
                        }
                    }
                    val finalOutput = cipher.doFinal()
                    if (finalOutput != null && finalOutput.isNotEmpty()) {
                        fos.write(finalOutput)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun decryptFile(inputFile: File, outputFile: File, password: String?): Result<Unit> {
        return try {
            FileInputStream(inputFile).use { fis ->
                val magicBytes = ByteArray(6)
                val read = fis.read(magicBytes)
                if (read != 6 || String(magicBytes, Charsets.US_ASCII) != MAGIC_HEADER) {
                    return Result.failure(Exception("الملف غير مشفر بصيغة CloudPlus أو الترويسة غير صحيحة."))
                }

                val keyTypeByte = fis.read()
                if (keyTypeByte == -1) {
                    return Result.failure(Exception("ترويسة الملف تالفة."))
                }
                val keyType = keyTypeByte.toByte()

                val iv = ByteArray(12)
                if (fis.read(iv) != 12) {
                    return Result.failure(Exception("قيمة IV تالفة في ترويسة الملف."))
                }

                val salt = ByteArray(16)
                if (fis.read(salt) != 16) {
                    return Result.failure(Exception("قيمة Salt تالفة في ترويسة الملف."))
                }

                if (keyType == KEYTYPE_PASSWORD && password.isNullOrBlank()) {
                    return Result.failure(Exception("هذا الملف مشفر بكلمة سر. يرجى إدخال كلمة السر في قسم الإعدادات لفك التشفير."))
                }

                val secretKey = getSecretKey(password, salt, keyType)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val output = cipher.update(buffer, 0, bytesRead)
                        if (output != null && output.isNotEmpty()) {
                            fos.write(output)
                        }
                    }
                    val finalOutput = cipher.doFinal()
                    if (finalOutput != null && finalOutput.isNotEmpty()) {
                        fos.write(finalOutput)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: javax.crypto.AEADBadTagException) {
            Result.failure(Exception("فشل فك التشفير: كلمة السر غير صحيحة أو البيانات تالفة."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
