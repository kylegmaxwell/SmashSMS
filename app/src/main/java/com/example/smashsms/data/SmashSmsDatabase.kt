package com.example.smashsms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QuarantinedMessage::class],
    version = 1,
    exportSchema = false,
)
abstract class SmashSmsDatabase : RoomDatabase() {
    abstract fun quarantinedMessageDao(): QuarantinedMessageDao

    companion object {
        @Volatile
        private var instance: SmashSmsDatabase? = null

        fun getInstance(context: Context): SmashSmsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmashSmsDatabase::class.java,
                    "smash_sms.db",
                ).build().also { instance = it }
            }
        }
    }
}
