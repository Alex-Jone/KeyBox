package com.keybox.app.data.crypto

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

/**
 * 加密层单元测试（安全核心）。
 * 验证 Argon2id 密钥派生与 AES-256-GCM 加解密的正确性。
 */
class CryptoTest {

    // ===== AES-GCM =====

    @Test
    fun `AES-GCM 加密后能正确解密`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val plain = "我的密码 P@ssw0rd!".toByteArray(Charsets.UTF_8)

        val encrypted = AesGcm.encrypt(plain, key)
        val decrypted = AesGcm.decrypt(encrypted, key)

        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun `相同明文两次加密产生不同密文（随机 nonce）`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val plain = "same-password".toByteArray()

        val c1 = AesGcm.encrypt(plain, key)
        val c2 = AesGcm.encrypt(plain, key)

        assertFalse(c1.contentEquals(c2))
    }

    @Test
    fun `错误密钥解密失败（GCM 认证失败）`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrongKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encrypted = AesGcm.encrypt("secret".toByteArray(), key)

        try {
            AesGcm.decrypt(encrypted, wrongKey)
            fail("应当抛出异常")
        } catch (e: Exception) {
            // 预期：GCM 认证失败
        }
    }

    @Test
    fun `密文被篡改后解密失败（完整性校验）`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encrypted = AesGcm.encrypt("secret".toByteArray(), key)

        // 篡改密文最后一个字节
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        try {
            AesGcm.decrypt(encrypted, key)
            fail("应当抛出异常")
        } catch (e: Exception) {
            // 预期：AEAD 完整性校验失败
        }
    }

    @Test
    fun `字符串加解密往返`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val plain = "hello@example.com"

        val encoded = AesGcm.encryptString(plain, key)
        val decoded = AesGcm.decryptString(encoded, key)

        assertEquals(plain, decoded)
    }

    // ===== Argon2id =====

    @Test
    fun `Argon2id 相同输入派生相同密钥`() {
        val password = "correct-horse-battery-staple".toCharArray()
        val salt = Argon2Kdf.randomSalt()

        val k1 = Argon2Kdf.deriveUnlockKey(password, salt)
        val k2 = Argon2Kdf.deriveUnlockKey(password, salt)

        assertArrayEquals(k1, k2)
        assertEquals(32, k1.size) // 256-bit
    }

    @Test
    fun `Argon2id 不同密码派生不同密钥`() {
        val salt = Argon2Kdf.randomSalt()
        val k1 = Argon2Kdf.deriveUnlockKey("password-A".toCharArray(), salt)
        val k2 = Argon2Kdf.deriveUnlockKey("password-B".toCharArray(), salt)

        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun `Argon2id 不同盐派生不同密钥`() {
        val password = "same-password".toCharArray()
        val s1 = Argon2Kdf.randomSalt()
        val s2 = Argon2Kdf.randomSalt()

        val k1 = Argon2Kdf.deriveUnlockKey(password, s1)
        val k2 = Argon2Kdf.deriveUnlockKey(password, s2)

        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun `备份密钥与解锁密钥参数不同`() {
        val password = "test-password".toCharArray()
        val salt = Argon2Kdf.randomSalt()

        val unlock = Argon2Kdf.deriveUnlockKey(password, salt)
        val backup = Argon2Kdf.deriveBackupKey(password, salt)

        // 参数不同，派生结果也不同（都应是 256-bit）
        assertFalse(unlock.contentEquals(backup))
        assertEquals(32, unlock.size)
        assertEquals(32, backup.size)
    }

    // ===== 端到端：KDF → KEK → 加密 DEK =====

    @Test
    fun `端到端 DEK 封装与解封`() {
        val masterPassword = "my-master-password-123".toCharArray()
        val salt = Argon2Kdf.randomSalt()

        // 派生 KEK
        val kek = Argon2Kdf.deriveUnlockKey(masterPassword, salt)

        // 生成 DEK
        val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }

        // 用 KEK 加密（wrap）DEK
        val wrappedDek = AesGcm.encrypt(dek, kek)

        // 用 KEK 解密（unwrap）DEK
        val unwrappedDek = AesGcm.decrypt(wrappedDek, kek)

        assertArrayEquals(dek, unwrappedDek)
    }
}
