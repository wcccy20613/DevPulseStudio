package com.chunyan.devpulsestudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiscoveryCacheDao {
    @Query("SELECT * FROM discovery_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun find(key: String): DiscoveryCacheEntity?

    @Query("SELECT * FROM discovery_cache ORDER BY syncedAt DESC")
    suspend fun all(): List<DiscoveryCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: DiscoveryCacheEntity)

    @Query("DELETE FROM discovery_cache")
    suspend fun clear()

    @Query("DELETE FROM discovery_cache WHERE syncedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
