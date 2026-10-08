package com.keybox.app.data.backup

import com.keybox.app.data.crypto.CryptoManager
import com.keybox.app.data.db.PasswordItemEntity
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

/**
 * 文本 / 表格导入器。
 *
 * 支持两种来源：
 *  1. TXT 文本：既支持「字段:值」逐行格式，也支持「每条记录用空行分隔的多行块」格式。
 *     字段名支持中英文别名（name/名称/标题, url/网址/网站, username/账号/用户名,
 *     password/密码, email/邮箱, phone/手机, notes/备注/note）。
 *  2. Excel（.xlsx / .xls）：首行为表头，按列名匹配上述字段。
 *
 * 与 CSV 导入一致：仅导入，不提供明文导出。
 */
object TextTableImporter {

    /** 字段键（归一化后的英文 key）。 */
    private const val KEY_NAME = "name"
    private const val KEY_URL = "url"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHONE = "phone"
    private const val KEY_NOTES = "notes"

    /** 字段别名映射：归一化为英文 key。 */
    private val FIELD_ALIASES: Map<String, String> = buildMap {
        fun put(keys: List<String>, value: String) = keys.forEach { put(it, value) }
        put(listOf("name", "title", "名称", "标题", "名字", "网站名", "应用名", "平台"), KEY_NAME)
        put(listOf("url", "website", "网址", "网站", "链接", "地址", "登录地址"), KEY_URL)
        put(listOf("username", "user", "login_username", "账号", "用户名", "登录名", "账户", "登录账号"), KEY_USER)
        put(listOf("password", "pass", "login_password", "密码", "口令"), KEY_PASS)
        put(listOf("email", "邮箱", "邮件", "电子邮箱"), KEY_EMAIL)
        put(listOf("phone", "mobile", "手机", "手机号", "电话"), KEY_PHONE)
        put(listOf("notes", "note", "extra", "备注", "说明", "注释"), KEY_NOTES)
    }

    /** 归一化字段名（去空格、去冒号、转小写）。 */
    private fun normalize(field: String): String? {
        val trimmed = field.trim().removeSuffix(":").removeSuffix("：").trim().lowercase()
        return FIELD_ALIASES[trimmed]
    }

    // ===== TXT 导入 =====

    /**
     * 解析 TXT 文本。
     *
     * 支持两种格式（自动识别）：
     *  - 「字段:值」逐行：每行一个字段，空行分隔不同记录，例如
     *      name: 百度
     *      username: zhangsan
     *      password: 123456
     *
     *      url: google.com
     *      username: lisi
     *      password: abcdef
     *  - 每行一条记录（无字段前缀时，按 name,url,username,password 顺序切分）。
     */
    fun parseTxt(text: String, crypto: CryptoManager): List<PasswordItemEntity> {
        val lines = text.lines()
        val hasKeyValue = lines.any { line ->
            val idx = line.indexOf(':').let { i -> if (i < 0) line.indexOf('：') else i }
            idx > 0 && normalize(line.substring(0, idx)) != null
        }

        if (hasKeyValue) return parseTxtKeyValue(lines, crypto)
        return parseTxtPlain(lines, crypto)
    }

    /** 「字段:值」逐行格式。 */
    private fun parseTxtKeyValue(lines: List<String>, crypto: CryptoManager): List<PasswordItemEntity> {
        val records = mutableListOf<MutableMap<String, String>>()
        var current = mutableMapOf<String, String>()

        fun flush() {
            if (current.isNotEmpty()) records.add(current)
            current = mutableMapOf()
        }

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                flush()
                return@forEach
            }
            val colonIdx = line.indexOf(':').let { i -> if (i < 0) line.indexOf('：') else i }
            if (colonIdx <= 0) {
                // 无法识别为字段行，跳过
                return@forEach
            }
            val key = normalize(line.substring(0, colonIdx)) ?: return@forEach
            val value = line.substring(colonIdx + 1).trim()
            current[key] = value
        }
        flush()

        return records.mapNotNull { map ->
            val name = map[KEY_NAME] ?: map[KEY_USER] ?: map[KEY_URL] ?: "未命名"
            val username = map[KEY_USER] ?: ""
            val password = map[KEY_PASS] ?: ""
            // 至少要有用户名或密码之一，才认为是有效记录
            if (username.isBlank() && password.isBlank()) return@mapNotNull null
            buildEntity(name, map[KEY_URL] ?: "", username, password, map[KEY_EMAIL] ?: "", map[KEY_PHONE] ?: "", map[KEY_NOTES] ?: "", crypto)
        }
    }

    /** 无字段前缀的纯文本：每行一条记录。 */
    private fun parseTxtPlain(lines: List<String>, crypto: CryptoManager): List<PasswordItemEntity> {
        return lines.mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
            // 用制表符、竖线、或连续多空格切分
            val cols = line.split(Regex("[\\t|]|\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
            if (cols.size < 3) return@mapNotNull null
            // 列顺序适配：
            //   >=4 列：name, url, username, password[, email, phone, notes]
            //   3 列：  name, username, password（名字/账号/密码）
            val name: String
            val url: String
            val username: String
            val password: String
            var email = ""
            var phone = ""
            var notes = ""
            if (cols.size >= 4) {
                name = cols[0]
                url = cols[1]
                username = cols[2]
                password = cols[3]
                email = cols.getOrElse(4) { "" }
                phone = cols.getOrElse(5) { "" }
                notes = cols.getOrElse(6) { "" }
            } else {
                name = cols[0]
                url = ""
                username = cols[1]
                password = cols[2]
            }
            if (username.isBlank() && password.isBlank()) return@mapNotNull null
            buildEntity(name, url, username, password, email, phone, notes, crypto)
        }
    }

    // ===== Excel 导入 =====

    /**
     * 解析 Excel（.xlsx / .xls）。首行为表头，按列名匹配字段。
     */
    fun parseExcel(input: InputStream, crypto: CryptoManager): List<PasswordItemEntity> {
        val result = mutableListOf<PasswordItemEntity>()
        WorkbookFactory.create(input).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            if (sheet == null || sheet.physicalNumberOfRows < 2) return result

            val formatter = DataFormatter()
            val headerRow = sheet.getRow(0) ?: return result
            // 建立 列索引 -> 字段key 映射
            val colMap = mutableMapOf<Int, String>()
            for (cell in headerRow) {
                val key = normalize(cellValue(cell, formatter)) ?: continue
                if (key !in colMap.values) colMap[cell.columnIndex] = key
            }
            // 至少要识别出 密码 或 账号 列
            if (KEY_PASS !in colMap.values && KEY_USER !in colMap.values) return result

            for (i in 1 until sheet.physicalNumberOfRows) {
                val row: Row = sheet.getRow(i) ?: continue
                val values = mutableMapOf<String, String>()
                for ((colIdx, key) in colMap) {
                    val cell: Cell? = row.getCell(colIdx)
                    if (cell != null) values[key] = cellValue(cell, formatter).trim()
                }
                val name = values[KEY_NAME].orEmpty()
                    .ifBlank { values[KEY_USER].orEmpty().ifBlank { values[KEY_URL].orEmpty().ifBlank { "未命名" } } }
                val username = values[KEY_USER].orEmpty()
                val password = values[KEY_PASS].orEmpty()
                if (username.isBlank() && password.isBlank()) continue
                result.add(
                    buildEntity(
                        name, values[KEY_URL].orEmpty(), username, password,
                        values[KEY_EMAIL].orEmpty(), values[KEY_PHONE].orEmpty(), values[KEY_NOTES].orEmpty(),
                        crypto
                    )
                )
            }
        }
        return result
    }

    private fun cellValue(cell: Cell, formatter: DataFormatter): String {
        return when (cell.cellType) {
            CellType.NUMERIC -> formatter.formatCellValue(cell)
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> formatter.formatCellValue(cell)
            else -> cell.stringCellValue
        }
    }

    // ===== 公共 =====

    private fun buildEntity(
        name: String, url: String, username: String, password: String,
        email: String, phone: String, notes: String,
        crypto: CryptoManager
    ): PasswordItemEntity {
        val now = System.currentTimeMillis()
        return PasswordItemEntity(
            name = name,
            url = url,
            username = crypto.encryptString(username),
            password = crypto.encryptString(password),
            email = crypto.encryptString(email),
            phone = crypto.encryptString(phone),
            totpSecret = "",
            notes = crypto.encryptString(notes),
            customFields = "",
            favorite = false,
            categoryId = null,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null
        )
    }
}
