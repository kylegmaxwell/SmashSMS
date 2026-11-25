package com.smashsms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuarantinedMessage::class,
        CustomKeyword::class,
        AllowedSender::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SmashSmsDatabase : RoomDatabase() {
    abstract fun quarantinedMessageDao(): QuarantinedMessageDao
    abstract fun customKeywordDao(): CustomKeywordDao
    abstract fun allowedSenderDao(): AllowedSenderDao

    companion object {
        @Volatile
        private var instance: SmashSmsDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_keywords` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `keyword` TEXT NOT NULL,
                        `isRegex` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `allowed_senders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `normalizedKey` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun getInstance(context: Context): SmashSmsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmashSmsDatabase::class.java,
                    "smash_sms.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
