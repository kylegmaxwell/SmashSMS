package com.smashsms.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleStatDao {
    @Query("SELECT * FROM rule_stats ORDER BY blockedCount DESC, overrideCount DESC, ruleName ASC")
    fun observeAll(): Flow<List<RuleStat>>

    @Query("SELECT * FROM rule_stats ORDER BY blockedCount DESC, overrideCount DESC, ruleName ASC")
    suspend fun getAll(): List<RuleStat>

    @Query("SELECT * FROM rule_stats WHERE ruleName = :ruleName")
    suspend fun getByRuleName(ruleName: String): RuleStat?

    @Upsert
    suspend fun upsert(stat: RuleStat)

    @Query("DELETE FROM rule_stats")
    suspend fun clearAll()

    @Transaction
    suspend fun incrementBlocked(ruleName: String, now: Long = System.currentTimeMillis()) {
        val trimmed = ruleName.trim()
        if (trimmed.isEmpty()) return
        val current = getByRuleName(trimmed)
        if (current == null) {
            upsert(
                RuleStat(
                    ruleName = trimmed,
                    blockedCount = 1,
                    overrideCount = 0,
                    lastUpdatedAt = now,
                ),
            )
        } else {
            upsert(
                current.copy(
                    blockedCount = current.blockedCount + 1,
                    lastUpdatedAt = now,
                ),
            )
        }
    }

    @Transaction
    suspend fun incrementOverride(ruleName: String, now: Long = System.currentTimeMillis()) {
        val trimmed = ruleName.trim()
        if (trimmed.isEmpty()) return
        val current = getByRuleName(trimmed)
        if (current == null) {
            upsert(
                RuleStat(
                    ruleName = trimmed,
                    blockedCount = 0,
                    overrideCount = 1,
                    lastUpdatedAt = now,
                ),
            )
        } else {
            upsert(
                current.copy(
                    overrideCount = current.overrideCount + 1,
                    lastUpdatedAt = now,
                ),
            )
        }
    }
}
