package com.keybox.app.domain

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP（RFC 6238）实现，基于 HOTP（RFC 4226）+ HMAC-SHA1。
 * 纯标准库实现，无需额外依赖。
 *
 * 用于 2FA：输入 Base32 编码的 secret 后，生成 6 位动态验证码。
 */
object Totp {

    private const val PERIOD = 30L // 时间步长 30 秒
    private const val DIGITS = 6   // 6 位验证码

    /**
     * 生成当前时间点的 6 位验证码。
     * @param base32Secret Base32 编码的 TOTP secret
     */
    fun generateCode(base32Secret: String, timestamp: Long = System.currentTimeMillis()): String {
        val secret = base32Decode(base32Secret)
        val counter = timestamp / 1000L / PERIOD
        return hotp(secret, counter, DIGITS)
    }

    /** 距下一次刷新的剩余秒数。 */
    fun remainingSeconds(timestamp: Long = System.currentTimeMillis()): Int {
        return (PERIOD - (timestamp / 1000L % PERIOD)).toInt()
    }

    /** HOTP 核心算法（RFC 4226）。 */
    private fun hotp(key: ByteArray, counter: Long, digits: Int): String {
        // counter 转为 8 字节大端
        val counterBytes = ByteArray(8)
        for (i in 0 until 8) {
            counterBytes[7 - i] = (counter shr (8 * i)).toByte()
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(counterBytes)

        // 动态截断（RFC 4226 §5.3）
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val mod = Math.pow(10.0, digits.toDouble()).toInt()
        val otp = binary % mod
        return otp.toString().padStart(digits, '0')
    }

    /** Base32 解码（RFC 4648）。 */
    private fun base32Decode(input: String): ByteArray {
        val clean = input.uppercase().filter { it !in " =-\n\r\t" }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = java.io.ByteArrayOutputStream()

        var buffer = 0
        var bitsLeft = 0
        for (c in clean) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.write((buffer shr bitsLeft) and 0xFF)
            }
        }
        return output.toByteArray()
    }

    /**
     * 校验 TOTP secret 格式（Base32 字符集）。
     * @return 规范化后的 secret，格式非法则返回 null
     */
    fun normalizeSecret(input: String): String? {
        val clean = input.uppercase().filter { !it.isWhitespace() && it != '-' && it != '=' }
        if (clean.isEmpty()) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        if (clean.any { it !in alphabet }) return null
        return clean
    }
}
