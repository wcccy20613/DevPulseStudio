package com.chunyan.devpulsestudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedArticleDao {
    @Query("SELECT * FROM saved_articles ORDER BY stars DESC")
    fun observeAll(): Flow<List<SavedArticleEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_articles WHERE repositoryId = :repositoryId)")
    suspend fun isSaved(repositoryId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(article: SavedArticleEntity)

    @Query("DELETE FROM saved_articles WHERE repositoryId = :repositoryId")
    suspend fun remove(repositoryId: Long)
}
