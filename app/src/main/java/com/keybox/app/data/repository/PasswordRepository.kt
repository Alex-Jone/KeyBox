package com.keybox.app.data.repository

import com.keybox.app.data.crypto.CryptoManager
import com.keybox.app.data.db.CategoryEntity
import com.keybox.app.data.db.KeyBoxDatabase
import com.keybox.app.data.db.PasswordHistoryEntity
import com.keybox.app.data.db.PasswordItemEntity
import com.keybox.app.data.db.TagEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 密码库仓库：负责业务逻辑，结合加密层与数据层。
 * 对外暴露解密后的领域模型，内部处理加解密。
 */
class PasswordRepository(
    private val db: KeyBoxDatabase,
    private val crypto: CryptoManager
) {
    private val passwordDao = db.passwordDao()
    private val categoryDao = db.categoryDao()
    private val historyDao = db.passwordHistoryDao()
    private val tagDao = db.tagDao()
    private val itemTagDao = db.passwordItemTagDao()

    // ===== 领域模型（解密后） =====
    data class PasswordItem(
        val id: Long,
        val name: String,
        val url: String,
        val username: String,
        val password: String,
        val email: String,
        val phone: String,
        val notes: String,
        val totpSecret: String,
        val favorite: Boolean,
        val categoryId: Long?,
        val createdAt: Long,
        val updatedAt: Long,
        val lastUsedAt: Long?
    )

    data class Category(
        val id: Long,
        val name: String,
        val icon: String
    )

    data class Tag(
        val id: Long,
        val name: String
    )

    // ===== 分类 =====

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list ->
            list.map { Category(it.id, it.name, it.icon) }
        }

    suspend fun getCategories(): List<Category> =
        categoryDao.getAll().map { Category(it.id, it.name, it.icon) }

    suspend fun ensureDefaultCategories() {
        if (categoryDao.count() > 0) return
        val now = System.currentTimeMillis()
        val defaults = listOf("社交", "邮箱", "工作", "开发", "金融", "购物", "娱乐", "云服务", "其他")
        defaults.forEachIndexed { index, name ->
            categoryDao.insert(
                CategoryEntity(name = name, sort = index, createdAt = now, updatedAt = now)
            )
        }
    }

    // ===== 密码条目 =====

    fun observeActiveItems(): Flow<List<PasswordItem>> =
        passwordDao.observeActive().map { list ->
            list.map { decrypt(it) }
        }

    fun observeTrashItems(): Flow<List<PasswordItem>> =
        passwordDao.observeTrash().map { list ->
            list.map { decrypt(it) }
        }

    suspend fun getItem(id: Long): PasswordItem? =
        passwordDao.getActiveById(id)?.let { decrypt(it) }

    /** 保存条目，返回条目 id（新增时为新插入的 id，编辑时为原 id）。 */
    suspend fun saveItem(item: PasswordItem): Long {
        val now = System.currentTimeMillis()
        return if (item.id == 0L) {
            passwordDao.insert(
                PasswordItemEntity(
                    name = item.name,
                    url = item.url,
                    username = crypto.encryptString(item.username),
                    password = crypto.encryptString(item.password),
                    email = crypto.encryptString(item.email),
                    phone = crypto.encryptString(item.phone),
                    notes = crypto.encryptString(item.notes),
                    totpSecret = crypto.encryptString(item.totpSecret),
                    customFields = "",
                    favorite = item.favorite,
                    categoryId = item.categoryId,
                    createdAt = now,
                    updatedAt = now,
                    lastUsedAt = item.lastUsedAt
                )
            )
        } else {
            val existing = passwordDao.getById(item.id) ?: return item.id
            // 密码变更时写入历史
            val oldPassword = crypto.decryptString(existing.password)
            if (oldPassword != item.password) {
                historyDao.insert(
                    PasswordHistoryEntity(
                        passwordItemId = item.id,
                        encryptedOldPassword = existing.password,
                        replacedAt = now
                    )
                )
                historyDao.trimForItem(item.id, 10)
            }
            passwordDao.update(
                existing.copy(
                    name = item.name,
                    url = item.url,
                    username = crypto.encryptString(item.username),
                    password = crypto.encryptString(item.password),
                    email = crypto.encryptString(item.email),
                    phone = crypto.encryptString(item.phone),
                    notes = crypto.encryptString(item.notes),
                    totpSecret = crypto.encryptString(item.totpSecret),
                    favorite = item.favorite,
                    categoryId = item.categoryId,
                    updatedAt = now,
                    lastUsedAt = item.lastUsedAt
                )
            )
            item.id
        }
    }

    suspend fun moveToTrash(id: Long) {
        passwordDao.setDeleted(id, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: Long) {
        val item = passwordDao.getById(id) ?: return
        passwordDao.update(item.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
    }

    suspend fun permanentlyDelete(id: Long) {
        historyDao.deleteForItem(id)
        passwordDao.delete(id)
    }

    /** 清理回收站中超过 30 天的条目。 */
    suspend fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val trash = passwordDao.observeTrash().first()
        trash.filter { (it.deletedAt ?: 0) < cutoff }.forEach { item ->
            permanentlyDelete(item.id)
        }
    }

    suspend fun markUsed(id: Long) {
        val item = passwordDao.getById(id) ?: return
        passwordDao.update(item.copy(lastUsedAt = System.currentTimeMillis()))
    }

    suspend fun getPasswordHistory(itemId: Long): List<Pair<String, Long>> =
        historyDao.getForItem(itemId).map {
            crypto.decryptString(it.encryptedOldPassword) to it.replacedAt
        }

    // ===== 导入 =====

    suspend fun importAll(
        items: List<PasswordItemEntity>,
        categories: List<CategoryEntity>,
        mode: ImportMode
    ): ImportResult {
        return db.withTransaction {
            var added = 0
            var updated = 0
            var skipped = 0

            // 分类按 name 去重
            val existingByName = categoryDao.getAll().associateBy { it.name }
            categories.forEach { cat ->
                if (cat.name !in existingByName) {
                    categoryDao.insert(cat.copy(id = 0))
                }
            }

            when (mode) {
                ImportMode.OVERWRITE -> {
                    passwordDao.getAll().forEach { passwordDao.delete(it.id) }
                    items.forEach { passwordDao.insert(it.copy(id = 0)) }
                    added = items.size
                }
                ImportMode.MERGE -> {
                    val existing = passwordDao.getAll().associateBy { it.id }
                    items.forEach { incoming ->
                        val current = existing[incoming.id]
                        if (current == null) {
                            passwordDao.insert(incoming.copy(id = 0))
                            added++
                        } else if (incoming.updatedAt > current.updatedAt) {
                            passwordDao.update(incoming.copy(id = current.id))
                            updated++
                        } else {
                            skipped++
                        }
                    }
                }
            }
            ImportResult(added, updated, skipped)
        }
    }

    enum class ImportMode { OVERWRITE, MERGE }
    data class ImportResult(val added: Int, val updated: Int, val skipped: Int)

    // ===== 标签 =====

    fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { list -> list.map { Tag(it.id, it.name) } }

    suspend fun getTags(): List<Tag> =
        tagDao.getAll().map { Tag(it.id, it.name) }

    /** 创建标签（重名则返回已有标签）。 */
    suspend fun createTag(name: String): Tag {
        val trimmed = name.trim()
        val existing = tagDao.getByName(trimmed)
        return if (existing != null) {
            Tag(existing.id, existing.name)
        } else {
            val id = tagDao.insert(TagEntity(name = trimmed))
            Tag(id, trimmed)
        }
    }

    suspend fun deleteTag(id: Long) {
        tagDao.delete(id)
    }

    /** 获取某条目的所有标签。 */
    suspend fun getTagsForItem(itemId: Long): List<Tag> =
        itemTagDao.getTagsForItem(itemId).map { Tag(it.id, it.name) }

    /** 设置某条目的标签（整体替换）。 */
    suspend fun setItemTags(itemId: Long, tagIds: List<Long>) {
        itemTagDao.replaceTagsForItem(itemId, tagIds)
    }

    /** 按标签筛选条目。 */
    suspend fun getItemsByTag(tagId: Long): List<PasswordItem> {
        val ids = itemTagDao.getItemIdsByTag(tagId)
        return ids.mapNotNull { id ->
            passwordDao.getActiveById(id)?.let { decrypt(it) }
        }
    }

    // ===== 内部 =====

    private fun decrypt(entity: PasswordItemEntity): PasswordItem =
        PasswordItem(
            id = entity.id,
            name = entity.name,
            url = entity.url,
            username = crypto.decryptString(entity.username),
            password = crypto.decryptString(entity.password),
            email = crypto.decryptString(entity.email),
            phone = crypto.decryptString(entity.phone),
            notes = crypto.decryptString(entity.notes),
            totpSecret = crypto.decryptString(entity.totpSecret),
            favorite = entity.favorite,
            categoryId = entity.categoryId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastUsedAt = entity.lastUsedAt
        )
}
