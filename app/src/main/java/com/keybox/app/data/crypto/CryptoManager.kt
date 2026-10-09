package com.keybox.app.data.crypto

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/**
 * 密码库加密管理器。
 *
 * 密钥层级：
 *   主密码 --Argon2id--> KEK --> (unwrap) DEK --> AES-256-GCM --> 敏感字段
 *
 * DEK 是随机生成的 256-bit 密钥，负责加密所有数据；
 * KEK 由主密码派生，仅用于加密/解密 DEK（存在 SharedPreferences 中，密文形式）。
 *
 * 安全说明：
 *   - DEK 明文仅存活于内存（本对象的字段），锁定/退出时清零；
 *   - 修改主密码 = 用新 KEK 重新加密 DEK，无需重加密数据；
 *   - 本对象不持有主密码。
 */
class CryptoManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("keybox_crypto", Context.MODE_PRIVATE)

    @Volatile
    private var dek: ByteArray? = null

    companion object {
        private const val KEY_SALT = "kdf_salt"           // Base64
        private const val KEY_WRAPPED_DEK = "wrapped_dek" // Base64
        private const val KEY_VERIFIER = "verifier"       // Base64，用于校验主密码
    }

    /** 密码库是否已初始化（是否已设置主密码）。 */
    fun isInitialized(): Boolean = prefs.contains(KEY_WRAPPED_DEK)

    /**
     * 首次设置主密码：
     * 1. 生成随机 DEK
     * 2. 生成随机 salt，派生 KEK
     * 3. 用 KEK 加密 DEK 存盘
     * 4. 写入校验值
     */
    fun initialize(masterPassword: CharArray) {
        val salt = Argon2Kdf.randomSalt()
        val kek = Argon2Kdf.deriveUnlockKey(masterPassword, salt)

        val newDek = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrappedDek = AesGcm.encrypt(newDek, kek)

        prefs.edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_WRAPPED_DEK, b64(wrappedDek))
            .putString(KEY_VERIFIER, b64(AesGcm.encrypt(byteArrayOf(0x4B, 0x42), kek))) // "KB"
            .apply()

        dek = newDek
        kek.fill(0)
    }

    /**
     * 用主密码解锁，成功后 DEK 驻留内存。
     * @return 是否成功
     */
    fun unlock(masterPassword: CharArray): Boolean {
        val salt = try { unb64(prefs.getString(KEY_SALT, null) ?: return false) } catch (e: Exception) { return false }
        val kek = Argon2Kdf.deriveUnlockKey(masterPassword, salt)
        val wrappedDek = try { unb64(prefs.getString(KEY_WRAPPED_DEK, null) ?: return false) } catch (e: Exception) { return false }

        return try {
            val decrypted = AesGcm.decrypt(wrappedDek, kek)
            dek = decrypted
            true
        } catch (e: Exception) {
            kek.fill(0)
            false
        }
    }

    /** 校验主密码是否正确（不改变当前状态）。 */
    fun verify(masterPassword: CharArray): Boolean {
        val salt = try { unb64(prefs.getString(KEY_SALT, null) ?: return false) } catch (e: Exception) { return false }
        val kek = Argon2Kdf.deriveUnlockKey(masterPassword, salt)
        val verifier = try { unb64(prefs.getString(KEY_VERIFIER, null) ?: return false) } catch (e: Exception) { return false }
        return try {
            val out = AesGcm.decrypt(verifier, kek)
            val ok = out.contentEquals(byteArrayOf(0x4B, 0x42))
            kek.fill(0)
            ok
        } catch (e: Exception) {
            kek.fill(0)
            false
        }
    }

    /** 修改主密码：用旧密码验证并解出 DEK，再用新密码重新 wrap。 */
    fun changeMasterPassword(oldPassword: CharArray, newPassword: CharArray): Boolean {
        if (!verify(oldPassword)) return false
        val currentDek = dek ?: return false

        val newSalt = Argon2Kdf.randomSalt()
        val newKek = Argon2Kdf.deriveUnlockKey(newPassword, newSalt)
        val wrappedDek = AesGcm.encrypt(currentDek, newKek)

        prefs.edit()
            .putString(KEY_SALT, b64(newSalt))
            .putString(KEY_WRAPPED_DEK, b64(wrappedDek))
            .putString(KEY_VERIFIER, b64(AesGcm.encrypt(byteArrayOf(0x4B, 0x42), newKek)))
            .apply()

        newKek.fill(0)
        return true
    }

    /** 是否已解锁。 */
    fun isUnlocked(): Boolean = dek != null

    /** 加密字符串字段。 */
    fun encryptString(plain: String): String {
        val key = dek ?: throw IllegalStateException("未解锁")
        return AesGcm.encryptString(plain, key)
    }

    /** 解密字符串字段。空字符串、未解锁、或解密失败时返回空字符串（容错，避免崩溃）。 */
    fun decryptString(encoded: String): String {
        if (encoded.isEmpty()) return ""
        val key = dek ?: return ""  // 未解锁时返回空串，避免 Flow 解密崩溃
        return try {
            AesGcm.decryptString(encoded, key)
        } catch (e: Exception) {
            // 密文损坏或格式非法时返回空字符串，避免单条数据导致整个列表崩溃
            ""
        }
    }

    /** 锁定：清零内存 DEK。 */
    fun lock() {
        dek?.fill(0)
        dek = null
    }

    /** 获取当前 DEK 的副本（供生物识别绑定使用）。 */
    fun getDek(): ByteArray? = dek?.copyOf()

    /** 设置 DEK（生物识别成功解出后调用）。 */
    fun setDek(dekBytes: ByteArray) {
        dek?.fill(0)
        dek = dekBytes.copyOf()
    }

    private fun b64(data: ByteArray): String =
        android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

    private fun unb64(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
}
