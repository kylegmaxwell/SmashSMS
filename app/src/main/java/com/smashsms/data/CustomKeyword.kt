package com.smashsms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_keywords")
data class CustomKeyword(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val isRegex: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
