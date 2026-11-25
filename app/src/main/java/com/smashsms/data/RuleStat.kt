package com.smashsms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rule_stats")
data class RuleStat(
    @PrimaryKey val ruleName: String,
    val blockedCount: Int = 0,
    val overrideCount: Int = 0,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
)
