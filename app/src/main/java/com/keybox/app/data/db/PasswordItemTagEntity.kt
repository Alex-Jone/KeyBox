package com.keybox.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** 条目-标签多对多关联。 */
@Entity(
    tableName = "password_item_tag",
    primaryKeys = ["passwordItemId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = PasswordItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["passwordItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class PasswordItemTagEntity(
    val passwordItemId: Long,
    val tagId: Long
)
