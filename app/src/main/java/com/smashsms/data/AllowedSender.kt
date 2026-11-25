package com.smashsms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_senders")
data class AllowedSender(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val normalizedKey: String = normalize(sender),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun matches(candidate: String?): Boolean {
        val candidateKey = normalize(candidate.orEmpty())
        if (candidateKey.isEmpty() || normalizedKey.isEmpty()) return false
        return candidateKey == normalizedKey
    }

    companion object {
        fun normalize(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val digits = trimmed.filter { it.isDigit() }
            val looksNumeric = digits.isNotEmpty() && trimmed.all {
                it.isDigit() || it in setOf('+', '-', '(', ')', ' ', '.')
            }
            return if (looksNumeric) {
                if (digits.length >= 10) digits.takeLast(10) else digits
            } else {
                trimmed.lowercase()
            }
        }
    }
}
