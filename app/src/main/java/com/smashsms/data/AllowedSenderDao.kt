package com.smashsms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedSenderDao {
    @Insert
    suspend fun insert(allowedSender: AllowedSender): Long

    @Query("SELECT * FROM allowed_senders ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<AllowedSender>>

    @Query("SELECT * FROM allowed_senders")
    suspend fun getAll(): List<AllowedSender>

    @Query("DELETE FROM allowed_senders WHERE normalizedKey = :normalizedKey")
    suspend fun deleteByNormalizedKey(normalizedKey: String)

    @Query("DELETE FROM allowed_senders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM allowed_senders")
    suspend fun clearAll()
}
