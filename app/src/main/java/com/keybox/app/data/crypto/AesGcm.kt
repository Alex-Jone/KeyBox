package com.keybox.app.data.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 对称加密层：AES-256-GCM。
 * 每条记录使用独立随机 96-bit nonce，密文格式为 nonce(12) + ciphertext + authTag(16)。
 */
object AesGcm {

    private const val KEY_SIZE_BYTES = 32 // 256-bit
    private const val NONCE_SIZE = 12     // 96-bit GCM 推荐
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plainBytes: ByteArray, key: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "DEK 必须为 256-bit" }
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plainBytes)
        return nonce + ciphertext
    }

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "DEK 必须为 256-bit" }
        require(data.size > NONCE_SIZE) { "密文格式错误" }
        val nonce = data.copyOfRange(0, NONCE_SIZE)
        val ciphertext = data.copyOfRange(NONCE_SIZE, data.size)
        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    /** 将字符串明文加密为 Base64 字符串。 */
    fun encryptString(plain: String, key: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(
            encrypt(plain.toByteArray(Charsets.UTF_8), key)
        )
    }

    /** 解密 Base64 字符串密文。 */
    fun decryptString(encoded: String, key: ByteArray): String {
        val data = java.util.Base64.getDecoder().decode(encoded)
        return String(decrypt(data, key), Charsets.UTF_8)
    }
}
