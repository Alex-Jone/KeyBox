package com.keybox.app.autofill

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Autofill 跨进程 DEK 存储。
 *
 * 主 App 解锁后调用 [storeDek]，将 DEK 用 Keystore 密钥加密写入共享存储；
 * AutofillService 进程调用 [getDek] 解密还原。
 *
 * 该 Keystore 密钥用于 Autofill 场景，setUserAuthenticationRequired=false
 * （Autofill 弹出时无法要求用户额外认证），但安全由以下保证：
 *   - 只有主 App 显式启用 Autofill 时才写入 DEK；
 *   - 主 App 锁定/关闭 Autofill 时立即清除 DEK 密文；
 *   - 密文存储在主 App 私有 SharedPreferences，其他 App 不可访问。
 */
object AutoFillKeyStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "keybox_autofill_key"
    private const val PREF_NAME = "keybox_autofill"
    private const val PREF_DEK = "autofill_dek"
    private const val PREF_NONCE = "autofill_nonce"

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

    /** 主 App 解锁后调用：用 Keystore 密钥加密 DEK 写入共享存储。 */
    fun storeDek(context: Context, dek: ByteArray) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val wrapped = cipher.doFinal(dek)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_DEK, b64(wrapped))
            .putString(PREF_NONCE, b64(cipher.iv))
            .apply()
    }

    /** AutofillService 进程调用：解密并返回 DEK。 */
    fun getDek(context: Context): ByteArray? {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val wrapped = unb64(prefs.getString(PREF_DEK, null) ?: return null)
            val nonce = unb64(prefs.getString(PREF_NONCE, null) ?: return null)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.doFinal(wrapped)
        } catch (e: Exception) {
            null
        }
    }

    /** 主 App 锁定/关闭 Autofill 时调用：清除 DEK 密文。 */
    fun clearDek(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /** 是否已启用 Autofill（主 App 解锁后曾写入 DEK）。 */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREF_DEK)
    }

    private fun b64(data: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(data)

    private fun unb64(s: String): ByteArray =
        java.util.Base64.getDecoder().decode(s)
}
