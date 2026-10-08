package com.keybox.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 生物识别密钥管理。
 *
 * 方案说明（MVP 稳健版）：
 *   - 用 Keystore 生成 AES 密钥（不绑定用户认证，避免 enable 阶段
 *     setUserAuthenticationRequired 导致的 UserNotAuthenticatedException 崩溃）
 *   - 用该密钥加密 DEK 存到 SharedPreferences
 *   - 解锁时：先通过 BiometricPrompt 认证身份，认证成功后用 Keystore 密钥解密 DEK
 *
 * 安全性说明：
 *   - DEK 密文存于应用私有 SharedPreferences，其他 App 不可访问；
 *   - 解密密钥存于 Android Keystore（硬件/系统保护）；
 *   - 指纹是「触发解密的前置条件」，认证通过才允许解密 DEK。
 */
class BiometricKeyManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("keybox_biometric", Context.MODE_PRIVATE)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "keybox_biometric_key"
        private const val PREF_WRAPPED_DEK = "biometric_wrapped_dek"
        private const val PREF_NONCE = "biometric_nonce"
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** 是否已启用生物识别解锁。 */
    fun isEnabled(): Boolean = prefs.contains(PREF_WRAPPED_DEK)

    /** 生物识别密钥是否仍有效。 */
    fun isKeyValid(): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.getKey(KEY_ALIAS, null) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启用生物识别：用 Keystore 密钥加密 DEK 存盘。
     * 需在已解锁（DEK 已驻留）时调用。
     * @return 是否成功
     */
    fun enable(dek: ByteArray): Boolean {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val wrapped = cipher.doFinal(dek)
            prefs.edit()
                .putString(PREF_WRAPPED_DEK, b64(wrapped))
                .putString(PREF_NONCE, b64(cipher.iv))
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 生物识别认证成功后调用：用 Keystore 密钥解密 DEK。 */
    fun decryptDek(): ByteArray? {
        return try {
            val wrapped = unb64(prefs.getString(PREF_WRAPPED_DEK, null) ?: return null)
            val nonce = unb64(prefs.getString(PREF_NONCE, null) ?: return null)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.doFinal(wrapped)
        } catch (e: Exception) {
            null
        }
    }

    /** 关闭生物识别解锁。 */
    fun disable() {
        prefs.edit().clear().apply()
    }

    private fun b64(data: ByteArray): String =
        android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

    private fun unb64(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
}
