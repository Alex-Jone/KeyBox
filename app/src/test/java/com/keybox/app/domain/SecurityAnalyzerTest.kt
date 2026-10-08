package com.keybox.app.domain

import com.keybox.app.data.repository.PasswordRepository
import org.junit.Assert.*
import org.junit.Test

class SecurityAnalyzerTest {

    private fun makeItem(
        id: Long,
        name: String,
        password: String,
        updatedAt: Long = System.currentTimeMillis()
    ) = PasswordRepository.PasswordItem(
        id = id,
        name = name,
        url = "",
        username = "user$id",
        password = password,
        email = "",
        phone = "",
        notes = "",
        totpSecret = "",
        favorite = false,
        categoryId = null,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        lastUsedAt = null
    )

    @Test
    fun `无问题的密码库返回空报告`() {
        val items = listOf(
            makeItem(1, "A", "StrongPass!123"),
            makeItem(2, "B", "AnotherStrong!456")
        )
        val report = SecurityAnalyzer.analyze(items)
        assertEquals(0, report.totalIssues)
        assertTrue(report.weakPasswords.isEmpty())
        assertTrue(report.duplicatePasswords.isEmpty())
        assertTrue(report.stalePasswords.isEmpty())
    }

    @Test
    fun `检测弱密码`() {
        val items = listOf(
            makeItem(1, "Weak", "123456"),   // 弱
            makeItem(2, "Strong", "StrongPass!123")
        )
        val report = SecurityAnalyzer.analyze(items)
        assertEquals(1, report.weakPasswords.size)
        assertEquals("Weak", report.weakPasswords[0].itemName)
    }

    @Test
    fun `检测重复密码`() {
        val items = listOf(
            makeItem(1, "A", "SamePassword!123"),
            makeItem(2, "B", "SamePassword!123"),
            makeItem(3, "C", "SamePassword!123")
        )
        val report = SecurityAnalyzer.analyze(items)
        assertEquals(1, report.duplicatePasswords.size)
        assertEquals(3, report.duplicatePasswords[0].count)
    }

    @Test
    fun `检测长期未修改`() {
        val staleTime = System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000 // 200 天前
        val items = listOf(
            makeItem(1, "Stale", "StrongPass!123", updatedAt = staleTime),
            makeItem(2, "Fresh", "StrongPass!456")
        )
        val report = SecurityAnalyzer.analyze(items)
        assertEquals(1, report.stalePasswords.size)
        assertEquals("Stale", report.stalePasswords[0].itemName)
    }

    @Test
    fun `重复密码计数正确（count-1 个冗余）`() {
        val items = listOf(
            makeItem(1, "A", "Dup!123456"),
            makeItem(2, "B", "Dup!123456")
        )
        val report = SecurityAnalyzer.analyze(items)
        // 2 个账号用同一密码 = 1 个冗余问题
        assertEquals(1, report.totalIssues - report.weakPasswords.size - report.stalePasswords.size)
    }
}
