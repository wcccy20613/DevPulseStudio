package com.chunyan.devpulsestudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedArticleDao {
    @Query("SELECT * FROM saved_articles ORDER BY savedAt DESC, stars DESC")
    fun observeAll(): Flow<List<SavedArticleEntity>>

    @Query("SELECT * FROM saved_articles ORDER BY savedAt DESC, stars DESC")
    suspend fun getAll(): List<SavedArticleEntity>

    @Query("SELECT * FROM saved_articles WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY savedAt DESC")
    fun observeMatching(query: String): Flow<List<SavedArticleEntity>>

    @Query("SELECT collection, COUNT(*) AS count FROM saved_articles GROUP BY collection ORDER BY count DESC")
    suspend fun collectionCounts(): List<CollectionCount>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_articles WHERE repositoryId = :repositoryId)")
    suspend fun isSaved(repositoryId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(article: SavedArticleEntity)

    @Query("DELETE FROM saved_articles WHERE repositoryId = :repositoryId")
    suspend fun remove(repositoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(articles: List<SavedArticleEntity>)

    @Query("UPDATE saved_articles SET collection = :collection WHERE repositoryId = :repositoryId")
    suspend fun moveToCollection(repositoryId: Long, collection: String)

    @Query("UPDATE saved_articles SET learningStatus = :status WHERE repositoryId = :repositoryId")
    suspend fun updateLearningStatus(repositoryId: Long, status: String)
}

data class CollectionCount(val collection: String, val count: Int)
