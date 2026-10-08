package com.keybox.app.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * TOTP（RFC 6238）单元测试。
 * 使用 RFC 6238 附录 B 的标准测试向量验证算法正确性。
 *
 * RFC 6238 测试向量使用的 secret 是 ASCII "12345678901234567890"，
 * 其 Base32 编码为 "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"。
 * 本实现的 generateCode 接收 Base32 secret（实际使用场景），
 * 因此测试中使用 Base32 形式。
 */
class TotpTest {

    // ASCII "12345678901234567890" 的 Base32 编码
    private val secretBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `RFC 6238 测试向量 - T=59s`() {
        // RFC 6238: T=59s, 8位 TOTP = 94287082，6位 = 287082
        val code = Totp.generateCode(secretBase32, 59L * 1000L)
        assertEquals("287082", code)
    }

    @Test
    fun `RFC 6238 测试向量 - T=1111111109s`() {
        // RFC 6238: T=1111111109s, 8位 = 07081804，6位 = 081804
        val code = Totp.generateCode(secretBase32, 1111111109L * 1000L)
        assertEquals("081804", code)
    }

    @Test
    fun `RFC 6238 测试向量 - T=1234567890s`() {
        // RFC 6238: T=1234567890s, 8位 = 89005924，6位 = 005924
        val code = Totp.generateCode(secretBase32, 1234567890L * 1000L)
        assertEquals("005924", code)
    }

    @Test
    fun `相同时间戳生成相同验证码`() {
        val secret = "JBSWY3DPEHPK3PXP"
        val t = 1000000000L * 1000L
        val c1 = Totp.generateCode(secret, t)
        val c2 = Totp.generateCode(secret, t)
        assertEquals(c1, c2)
    }

    @Test
    fun `验证码是 6 位数字`() {
        val code = Totp.generateCode("JBSWY3DPEHPK3PXP")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `倒计时范围 1-30 秒`() {
        val remaining = Totp.remainingSeconds()
        assertTrue(remaining in 1..30)
    }

    @Test
    fun `normalizeSecret 规范化并去空格`() {
        val normalized = Totp.normalizeSecret(" jbsw y3dp ehpk 3pxp ")
        assertEquals("JBSWY3DPEHPK3PXP", normalized)
    }

    @Test
    fun `normalizeSecret 拒绝非法字符`() {
        assertNull(Totp.normalizeSecret("invalid-char-8"))
        assertNull(Totp.normalizeSecret(""))
    }
}
