package com.keybox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PasswordHistoryDao {
    @Query("SELECT * FROM password_history WHERE passwordItemId = :itemId ORDER BY replacedAt DESC LIMIT :limit")
    suspend fun getForItem(itemId: Long, limit: Int = 10): List<PasswordHistoryEntity>

    @Insert
    suspend fun insert(history: PasswordHistoryEntity)

    @Query("DELETE FROM password_history WHERE passwordItemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    @Query("DELETE FROM password_history WHERE passwordItemId = :itemId AND id NOT IN (SELECT id FROM password_history WHERE passwordItemId = :itemId ORDER BY replacedAt DESC LIMIT :keep)")
    suspend fun trimForItem(itemId: Long, keep: Int = 10)

    @Query("SELECT * FROM password_history")
    suspend fun getAll(): List<PasswordHistoryEntity>
}
