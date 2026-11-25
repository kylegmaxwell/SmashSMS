package com.example.smashsms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuarantinedMessageDao {
    @Insert
    suspend fun insert(message: QuarantinedMessage)

    @Query("SELECT * FROM quarantined_messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<QuarantinedMessage>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM quarantined_messages " +
            "WHERE sender = :sender AND body = :body AND timestamp >= :sinceTimestamp)"
    )
    suspend fun existsRecent(sender: String, body: String, sinceTimestamp: Long): Boolean

    @Query("DELETE FROM quarantined_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM quarantined_messages")
    suspend fun clearAll()
}
