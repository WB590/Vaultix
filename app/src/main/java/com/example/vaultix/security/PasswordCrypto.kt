package com.example.vaultix.security

import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object PasswordCrypto {
    private const val KDF_ITERATIONS = 120_000
    private const val KDF_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val saltBytes = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(saltBytes)
        return toBase64(saltBytes)
    }

    fun deriveMasterKey(masterPassword: String, userSaltBase64: String): SecretKeySpec {
        val salt = fromBase64(userSaltBase64)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(
            masterPassword.toCharArray(),
            salt,
            KDF_ITERATIONS,
            KDF_KEY_LENGTH
        )
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, key: SecretKeySpec): EncryptedPayload {
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val params = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, params)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedPayload(
            cipherTextBase64 = toBase64(encryptedBytes),
            ivBase64 = toBase64(iv)
        )
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String, key: SecretKeySpec): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val params = GCMParameterSpec(GCM_TAG_LENGTH, fromBase64(ivBase64))
        cipher.init(Cipher.DECRYPT_MODE, key, params)

        val decrypted = cipher.doFinal(fromBase64(cipherTextBase64))
        return String(decrypted, Charsets.UTF_8)
    }

    private fun toBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    private fun fromBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}

data class EncryptedPayload(
    val cipherTextBase64: String,
    val ivBase64: String
)
