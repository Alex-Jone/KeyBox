package com.keybox.app.data.backup

import com.keybox.app.data.crypto.AesGcm
import com.keybox.app.data.crypto.Argon2Kdf
import com.keybox.app.data.db.CategoryEntity
import com.keybox.app.data.db.PasswordHistoryEntity
import com.keybox.app.data.db.PasswordItemEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * .kbx 备份格式。
 *
 * 文件结构：
 *   magic(8) + formatVersion(4) + kdfSalt(16) + nonce(12) + ciphertext(含 authTag)
 *
 * Payload 为 JSON（categories / items / history），整体用备份级 KDF 派生密钥加密。
 */
object KbxFormat {

    private const val MAGIC = "KEYBOXKBX"
    private const val FORMAT_VERSION = 1

    data class Decoded(
        val items: List<PasswordItemEntity>,
        val categories: List<CategoryEntity>,
        val history: List<PasswordHistoryEntity>
    )

    /** 导出：使用主密码 + 独立随机 salt 加密（备份级参数）。 */
    fun encode(
        items: List<PasswordItemEntity>,
        categories: List<CategoryEntity>,
        history: List<PasswordHistoryEntity>,
        masterPassword: CharArray
    ): ByteArray {
        val payload = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("categories", JSONArray().apply {
                categories.forEach { c ->
                    put(JSONObject().apply {
                        put("name", c.name)
                        put("icon", c.icon)
                        put("sort", c.sort)
                        put("enabled", c.enabled)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                    })
                }
            })
            put("items", JSONArray().apply {
                items.forEach { it ->
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("url", it.url)
                        put("username", it.username)
                        put("password", it.password)
                        put("email", it.email)
                        put("phone", it.phone)
                        put("totpSecret", it.totpSecret)
                        put("notes", it.notes)
                        put("customFields", it.customFields)
                        put("favorite", it.favorite)
                        put("categoryId", it.categoryId ?: JSONObject.NULL)
                        put("createdAt", it.createdAt)
                        put("updatedAt", it.updatedAt)
                        put("lastUsedAt", it.lastUsedAt ?: JSONObject.NULL)
                        put("deletedAt", it.deletedAt ?: JSONObject.NULL)
                    })
                }
            })
            put("history", JSONArray().apply {
                history.forEach { h ->
                    put(JSONObject().apply {
                        put("passwordItemId", h.passwordItemId)
                        put("encryptedOldPassword", h.encryptedOldPassword)
                        put("replacedAt", h.replacedAt)
                    })
                }
            })
        }.toString().toByteArray(Charsets.UTF_8)

        val salt = Argon2Kdf.randomSalt()
        val key = Argon2Kdf.deriveBackupKey(masterPassword, salt)
        val encrypted = AesGcm.encrypt(payload, key)
        key.fill(0)

        return MAGIC.toByteArray(Charsets.UTF_8) +
            intToBytes(FORMAT_VERSION) +
            salt +
            encrypted
    }

    /** 解码并解密。返回 null 表示格式错误或密码错误。 */
    fun decode(data: ByteArray, masterPassword: CharArray): Decoded? {
        try {
            val magic = String(data.copyOfRange(0, 8), Charsets.UTF_8)
            if (magic != MAGIC) return null
            val version = bytesToInt(data.copyOfRange(8, 12))
            if (version != FORMAT_VERSION) return null
            val salt = data.copyOfRange(12, 28)
            val encrypted = data.copyOfRange(28, data.size)

            val key = Argon2Kdf.deriveBackupKey(masterPassword, salt)
            val payloadBytes = AesGcm.decrypt(encrypted, key)
            key.fill(0)

            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))

            val categories = mutableListOf<CategoryEntity>()
            val catArr = json.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until catArr.length()) {
                val c = catArr.getJSONObject(i)
                categories.add(
                    CategoryEntity(
                        name = c.getString("name"),
                        icon = c.optString("icon", ""),
                        sort = c.optInt("sort", 0),
                        enabled = c.optBoolean("enabled", true),
                        createdAt = c.optLong("createdAt"),
                        updatedAt = c.optLong("updatedAt")
                    )
                )
            }

            val items = mutableListOf<PasswordItemEntity>()
            val itemArr = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until itemArr.length()) {
                val it = itemArr.getJSONObject(i)
                items.add(
                    PasswordItemEntity(
                        id = it.optLong("id", 0),
                        name = it.getString("name"),
                        url = it.optString("url", ""),
                        username = it.optString("username", ""),
                        password = it.optString("password", ""),
                        email = it.optString("email", ""),
                        phone = it.optString("phone", ""),
                        totpSecret = it.optString("totpSecret", ""),
                        notes = it.optString("notes", ""),
                        customFields = it.optString("customFields", ""),
                        favorite = it.optBoolean("favorite", false),
                        categoryId = if (it.isNull("categoryId")) null else it.optLong("categoryId"),
                        createdAt = it.optLong("createdAt"),
                        updatedAt = it.optLong("updatedAt"),
                        lastUsedAt = if (it.isNull("lastUsedAt")) null else it.optLong("lastUsedAt"),
                        deletedAt = if (it.isNull("deletedAt")) null else it.optLong("deletedAt")
                    )
                )
            }

            val history = mutableListOf<PasswordHistoryEntity>()
            val histArr = json.optJSONArray("history") ?: JSONArray()
            for (i in 0 until histArr.length()) {
                val h = histArr.getJSONObject(i)
                history.add(
                    PasswordHistoryEntity(
                        passwordItemId = h.getLong("passwordItemId"),
                        encryptedOldPassword = h.getString("encryptedOldPassword"),
                        replacedAt = h.getLong("replacedAt")
                    )
                )
            }

            return Decoded(items, categories, history)
        } catch (e: Exception) {
            return null
        }
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
        (v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()
    )

    private fun bytesToInt(b: ByteArray): Int =
        ((b[0].toInt() and 0xFF) shl 24) or
        ((b[1].toInt() and 0xFF) shl 16) or
        ((b[2].toInt() and 0xFF) shl 8) or
        (b[3].toInt() and 0xFF)
}
