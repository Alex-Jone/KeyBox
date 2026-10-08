package com.keybox.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "",
    val sort: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
