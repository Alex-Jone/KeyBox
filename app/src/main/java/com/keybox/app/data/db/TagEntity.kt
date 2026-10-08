package com.keybox.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 标签。 */
@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
