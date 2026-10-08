package com.keybox.app.domain

import com.keybox.app.data.repository.PasswordRepository

/**
 * 密码安全分析（安全中心）。
 * 全部在本地完成，不上传任何密码。
 *
 * 检查项：
 *   - 弱密码：强度为 WEAK 或 MEDIUM 的密码
 *   - 重复密码：多个条目使用相同密码
 *   - 长期未修改：updatedAt 距今超过 N 天
 */
object SecurityAnalyzer {

    const val STALE_DAYS = 180L // 长期未修改阈值：180 天

    data class SecurityReport(
        val weakPasswords: List<Issue>,
        val duplicatePasswords: List<DuplicateGroup>,
        val stalePasswords: List<Issue>
    ) {
        val totalIssues: Int
            get() = weakPasswords.size + duplicatePasswords.sumOf { it.count - 1 } + stalePasswords.size
    }

    data class Issue(
        val itemId: Long,
        val itemName: String,
        val detail: String
    )

    data class DuplicateGroup(
        val password: String,
        val itemNames: List<String>,
        val itemIds: List<Long>
    ) {
        val count: Int get() = itemNames.size
    }

    fun analyze(items: List<PasswordRepository.PasswordItem>): SecurityReport {
        val now = System.currentTimeMillis()

        // 1. 弱密码
        val weak = items.mapNotNull { item ->
            val level = PasswordStrength.evaluate(item.password).first
            if (level == PasswordStrength.Level.WEAK || level == PasswordStrength.Level.MEDIUM) {
                Issue(
                    itemId = item.id,
                    itemName = item.name,
                    detail = "密码强度：${level.label}"
                )
            } else null
        }

        // 2. 重复密码
        val groups = items.groupBy { it.password }
            .filter { it.value.size > 1 }
            .map { (pwd, list) ->
                DuplicateGroup(
                    password = pwd,
                    itemNames = list.map { it.name },
                    itemIds = list.map { it.id }
                )
            }

        // 3. 长期未修改
        val stale = items.mapNotNull { item ->
            val daysSinceUpdate = (now - item.updatedAt) / (24 * 60 * 60 * 1000L)
            if (daysSinceUpdate >= STALE_DAYS) {
                Issue(
                    itemId = item.id,
                    itemName = item.name,
                    detail = "已 ${daysSinceUpdate} 天未修改"
                )
            } else null
        }

        return SecurityReport(weak, groups, stale)
    }
}
