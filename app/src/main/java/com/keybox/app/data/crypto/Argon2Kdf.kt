package com.keybox.app.data.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

/**
 * KDF 层：Argon2id（BouncyCastle 纯 Java 实现，Android 可用）。
 * 用于从主密码派生 KEK（Key Encryption Key）。
 * 解锁使用标准参数，备份导出使用更强参数。
 */
object Argon2Kdf {

    const val SALT_SIZE = 16       // 128-bit
    const val KEY_SIZE = 32        // 256-bit

    // 解锁档：目标 ~500ms
    private const val MEMORY_UNLOCK = 64 * 1024   // 64 MB (KiB)
    private const val ITER_UNLOCK = 3
    private const val PARALLEL_UNLOCK = 4

    // 备份档：目标 2~3s
    private const val MEMORY_BACKUP = 256 * 1024  // 256 MB
    private const val ITER_BACKUP = 4
    private const val PARALLEL_BACKUP = 4

    fun randomSalt(): ByteArray = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }

    /** 派生解锁 KEK。 */
    fun deriveUnlockKey(password: CharArray, salt: ByteArray): ByteArray {
        return derive(password, salt, MEMORY_UNLOCK, ITER_UNLOCK, PARALLEL_UNLOCK)
    }

    /** 派生备份加密密钥（更强参数）。 */
    fun deriveBackupKey(password: CharArray, salt: ByteArray): ByteArray {
        return derive(password, salt, MEMORY_BACKUP, ITER_BACKUP, PARALLEL_BACKUP)
    }

    private fun derive(password: CharArray, salt: ByteArray, memory: Int, iter: Int, parallel: Int): ByteArray {
        val passwordBytes = String(password).toByteArray(Charsets.UTF_8)
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(parallel)
            .withMemoryAsKB(memory)
            .withIterations(iter)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val result = ByteArray(KEY_SIZE)
        generator.generateBytes(passwordBytes, result)
        // 清除密码字节
        passwordBytes.fill(0)
        return result
    }
}
