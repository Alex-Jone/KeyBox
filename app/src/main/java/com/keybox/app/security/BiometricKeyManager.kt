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
 * 正确方案（对齐设计文档第 9 章）：
 *   - 首次主密码解锁后，在 Keystore 生成一个「生物识别绑定密钥」
 *     （setUserAuthenticationRequired=true）
 *   - 用该密钥加密 DEK 后存到 SharedPreferences
 *   - 之后用户通过 BiometricPrompt 认证，认证通过后 Keystore 才允许
 *     使用该密钥解密，从而还原 DEK
 *   - 用户新增/删除指纹后，该密钥自动失效（setInvalidatedByBiometricEnrollment=true），
 *     必须回退主密码重新绑定
 *
 * 因此生物识别不是「把主密码明文存下来」，而是「Keystore 保护下的 DEK 封装」。
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
            .setUserAuthenticationRequired(true)
            // 关键：新增/删除指纹后密钥失效，强制回退主密码
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** 是否已启用生物识别解锁。 */
    fun isEnabled(): Boolean = prefs.contains(PREF_WRAPPED_DEK)

    /** 生物识别密钥是否仍有效（未被 enrollment 变更作废）。 */
    fun isKeyValid(): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.getKey(KEY_ALIAS, null) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启用生物识别：用生物识别密钥加密 DEK 存盘。
     * 需在已解锁（DEK 已驻留）时调用。
     */
    fun enable(dek: ByteArray) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val wrapped = cipher.doFinal(dek)
        prefs.edit()
            .putString(PREF_WRAPPED_DEK, b64(wrapped))
            .putString(PREF_NONCE, b64(cipher.iv))
            .apply()
    }

    /**
     * 生物识别成功后调用：解密 DEK。
     * 返回 null 表示密钥已失效（enrollment 变更），需回退主密码。
     */
    fun decryptDek(cipher: Cipher): ByteArray? {
        return try {
            val wrapped = unb64(prefs.getString(PREF_WRAPPED_DEK, null) ?: return null)
            cipher.doFinal(wrapped)
        } catch (e: Exception) {
            null
        }
    }

    /** 获取一个已初始化 DECRYPT 模式、等待用户认证的 Cipher（交给 BiometricPrompt）。 */
    fun getCipherForAuth(): Cipher? {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(prefs.getString(PREF_NONCE, "") ?: return null)))
            cipher
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
