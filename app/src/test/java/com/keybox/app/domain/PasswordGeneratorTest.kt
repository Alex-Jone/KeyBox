package com.keybox.app.domain

import org.junit.Assert.*
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `随机密码长度正确`() {
        val pwd = PasswordGenerator.generateRandom(length = 20)
        assertEquals(20, pwd.length)
    }

    @Test
    fun `随机密码包含所选的字符集`() {
        val pwd = PasswordGenerator.generateRandom(
            length = 32,
            useUpper = true, useLower = true, useDigit = true, useSymbol = true
        )
        assertTrue(pwd.any { it.isUpperCase() })
        assertTrue(pwd.any { it.isLowerCase() })
        assertTrue(pwd.any { it.isDigit() })
        assertTrue(pwd.any { !it.isLetterOrDigit() })
    }

    @Test
    fun `两次生成密码不同`() {
        val p1 = PasswordGenerator.generateRandom(length = 20)
        val p2 = PasswordGenerator.generateRandom(length = 20)
        assertNotEquals(p1, p2)
    }

    @Test
    fun `排除易混淆字符`() {
        val pwd = PasswordGenerator.generateRandom(length = 40, excludeAmbiguous = true)
        val ambiguous = "O0Il1|`'\",;:."
        assertTrue(pwd.none { it in ambiguous })
    }

    @Test
    fun `密码短语格式正确`() {
        val phrase = PasswordGenerator.generatePassphrase(wordCount = 5, separator = "-")
        val words = phrase.split("-")
        assertEquals(5, words.size)
        assertTrue(words.all { it.isNotBlank() })
    }

    @Test
    fun `强度检测：弱密码`() {
        val (level, _) = PasswordStrength.evaluate("123456")
        assertEquals(PasswordStrength.Level.WEAK, level)
    }

    @Test
    fun `强度检测：强密码`() {
        val (level, _) = PasswordStrength.evaluate("Tr0ub4dor&3X!amplePass")
        assertTrue(level == PasswordStrength.Level.STRONG || level == PasswordStrength.Level.VERY_STRONG)
    }

    @Test
    fun `主密码最低长度校验`() {
        assertFalse(PasswordStrength.isMasterPasswordAcceptable("short"))
        assertTrue(PasswordStrength.isMasterPasswordAcceptable("this-is-long-enough"))
    }
}
