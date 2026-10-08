package com.keybox.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PasswordItemEntity::class,
        CategoryEntity::class,
        PasswordHistoryEntity::class,
        TagEntity::class,
        PasswordItemTagEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KeyBoxDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun passwordHistoryDao(): PasswordHistoryDao
    abstract fun tagDao(): TagDao
    abstract fun passwordItemTagDao(): PasswordItemTagDao

    companion object {
        @Volatile
        private var INSTANCE: KeyBoxDatabase? = null

        /** v1 -> v2：新增 tag 和 password_item_tag 表。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tag (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS password_item_tag (" +
                        "passwordItemId INTEGER NOT NULL, " +
                        "tagId INTEGER NOT NULL, " +
                        "PRIMARY KEY(passwordItemId, tagId), " +
                        "FOREIGN KEY(passwordItemId) REFERENCES password_item(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(tagId) REFERENCES tag(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_password_item_tag_tagId ON password_item_tag(tagId)")
            }
        }

        fun getInstance(context: Context): KeyBoxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KeyBoxDatabase::class.java,
                    "keybox.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
