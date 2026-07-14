package com.chunyan.devpulsestudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY usedAt DESC LIMIT 8")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
