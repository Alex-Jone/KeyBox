package com.keybox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM password_item WHERE deletedAt IS NULL ORDER BY favorite DESC, updatedAt DESC")
    fun observeActive(): Flow<List<PasswordItemEntity>>

    @Query("SELECT * FROM password_item WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<PasswordItemEntity>>

    @Query("SELECT * FROM password_item WHERE deletedAt IS NULL AND id = :id")
    suspend fun getActiveById(id: Long): PasswordItemEntity?

    @Query("SELECT * FROM password_item WHERE id = :id")
    suspend fun getById(id: Long): PasswordItemEntity?

    @Insert
    suspend fun insert(item: PasswordItemEntity): Long

    @Update
    suspend fun update(item: PasswordItemEntity)

    @Query("UPDATE password_item SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeleted(id: Long, deletedAt: Long)

    @Query("DELETE FROM password_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM password_item")
    suspend fun getAll(): List<PasswordItemEntity>

    @Query("SELECT COUNT(*) FROM password_item WHERE deletedAt IS NULL")
    suspend fun countActive(): Int
}
