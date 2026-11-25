package com.example.smashsms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quarantined_messages")
data class QuarantinedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val matchedRule: String,
    val source: String,
    val deletedFromTelephony: Boolean,
)
