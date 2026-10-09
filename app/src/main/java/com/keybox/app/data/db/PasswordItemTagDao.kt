package com.keybox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PasswordItemTagDao {
    @Insert
    suspend fun insert(rel: PasswordItemTagEntity)

    @Query("DELETE FROM password_item_tag WHERE passwordItemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    @Query("DELETE FROM password_item_tag WHERE tagId = :tagId")
    suspend fun deleteForTag(tagId: Long)

    @Query("SELECT t.* FROM tag t INNER JOIN password_item_tag pit ON t.id = pit.tagId WHERE pit.passwordItemId = :itemId ORDER BY t.name ASC")
    suspend fun getTagsForItem(itemId: Long): List<TagEntity>

    @Query("SELECT pit.tagId FROM password_item_tag pit WHERE pit.passwordItemId = :itemId")
    suspend fun getTagIdsForItem(itemId: Long): List<Long>

    /** 返回所有「条目 → 标签名」关联（用于搜索时按标签名匹配）。 */
    @Query("SELECT pit.passwordItemId AS itemId, t.name AS tagName FROM password_item_tag pit INNER JOIN tag t ON t.id = pit.tagId")
    suspend fun getAllItemTagNames(): List<ItemTagName>

    data class ItemTagName(val itemId: Long, val tagName: String)

    @Query("SELECT pi.id FROM password_item pi INNER JOIN password_item_tag pit ON pi.id = pit.passwordItemId WHERE pit.tagId = :tagId AND pi.deletedAt IS NULL")
    suspend fun getItemIdsByTag(tagId: Long): List<Long>

    /** 替换某条目的所有标签。 */
    @Transaction
    suspend fun replaceTagsForItem(itemId: Long, tagIds: List<Long>) {
        deleteForItem(itemId)
        tagIds.forEach { tagId ->
            insert(PasswordItemTagEntity(passwordItemId = itemId, tagId = tagId))
        }
    }
}
