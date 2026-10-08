package com.keybox.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 密码条目。
 * 敏感字段（password、username、email、phone、notes、totpSecret、customFields）
 * 在写入数据库前已经由 CryptoManager 加密，此处存的是密文。
 */
@Entity(tableName = "password_item")
data class PasswordItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val username: String,       // 密文
    val password: String,       // 密文
    val email: String,          // 密文
    val phone: String,          // 密文
    val totpSecret: String,     // 密文
    val notes: String,          // 密文
    val customFields: String,   // 密文 JSON
    val favorite: Boolean = false,
    val categoryId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
    val deletedAt: Long? = null  // 非空即进入回收站（软删除）
)
