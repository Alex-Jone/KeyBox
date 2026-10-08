package com.keybox.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 密码历史：每次修改密码时保存旧密码（密文）。 */
@Entity(tableName = "password_history")
data class PasswordHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val passwordItemId: Long,
    val encryptedOldPassword: String,
    val replacedAt: Long
)
