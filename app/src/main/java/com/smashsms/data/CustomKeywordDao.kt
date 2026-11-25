package com.smashsms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomKeywordDao {
    @Insert
    suspend fun insert(keyword: CustomKeyword): Long

    @Query("SELECT * FROM custom_keywords ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<CustomKeyword>>

    @Query("SELECT * FROM custom_keywords WHERE enabled = 1 ORDER BY createdAt DESC, id DESC")
    suspend fun getEnabled(): List<CustomKeyword>

    @Query("UPDATE custom_keywords SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM custom_keywords WHERE id = :id")
    suspend fun deleteById(id: Long)
}
