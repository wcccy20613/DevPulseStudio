package com.chunyan.devpulsestudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadmeAnalysisDao {
    @Query("SELECT * FROM readme_analyses WHERE repositoryId = :repositoryId LIMIT 1")
    suspend fun find(repositoryId: Long): ReadmeAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: ReadmeAnalysisEntity)

    @Query("DELETE FROM readme_analyses WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM readme_analyses")
    suspend fun clear()
}
