package com.keybox.app.data.backup

import com.keybox.app.data.crypto.CryptoManager
import com.keybox.app.data.db.PasswordItemEntity

/**
 * CSV 导入：仅支持导入（从浏览器/其他密码管理器迁移）。
 * 第一版不提供明文导出。
 *
 * 支持两种常见格式：
 *   - 带表头：name,url,username,password,notes（或类似列名）
 *   - 无表头：name,url,username,password 顺序
 */
object CsvImporter {

    fun parse(csvText: String, crypto: CryptoManager): List<PasswordItemEntity> {
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val first = lines[0].split(",").map { it.trim() }
        val hasHeader = first.any { it.equals("name", true) || it.equals("username", true) }

        val dataLines = if (hasHeader) {
            val header = first.map { it.lowercase() }
            val nameIdx = header.indexOfFirst { it == "name" || it == "title" }
            val urlIdx = header.indexOfFirst { it == "url" || it == "website" || it == "login_uri" }
            val userIdx = header.indexOfFirst { it == "username" || it == "login_username" || it == "user" }
            val passIdx = header.indexOfFirst { it == "password" || it == "login_password" || it == "pass" }
            val noteIdx = header.indexOfFirst { it == "notes" || it == "note" || it == "extra" }

            lines.drop(1).mapNotNull { line ->
                val cols = splitCsv(line)
                if (cols.size <= maxOf(nameIdx, userIdx, passIdx)) return@mapNotNull null
                buildEntity(cols, nameIdx, urlIdx, userIdx, passIdx, noteIdx, crypto)
            }
        } else {
            lines.mapNotNull { line ->
                val cols = splitCsv(line)
                if (cols.size < 4) return@mapNotNull null
                buildEntity(cols, 0, 1, 2, 3, 4, crypto)
            }
        }
        return dataLines
    }

    private fun buildEntity(
        cols: List<String>,
        nameIdx: Int, urlIdx: Int, userIdx: Int, passIdx: Int, noteIdx: Int,
        crypto: CryptoManager
    ): PasswordItemEntity {
        val now = System.currentTimeMillis()
        fun get(i: Int): String = if (i in cols.indices && i >= 0) cols[i].trim() else ""
        return PasswordItemEntity(
            name = get(nameIdx).ifBlank { "未命名" },
            url = get(urlIdx),
            username = crypto.encryptString(get(userIdx)),
            password = crypto.encryptString(get(passIdx)),
            email = "",
            phone = "",
            totpSecret = "",
            notes = crypto.encryptString(get(noteIdx)),
            customFields = "",
            favorite = false,
            categoryId = null,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null
        )
    }

    /** 简单 CSV 行切分（支持引号包裹字段）。 */
    private fun splitCsv(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
