package com.chunyan.devpulsestudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IgnoredRecommendationDao {
    @Query("SELECT repositoryId FROM ignored_recommendations")
    suspend fun allIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun ignore(item: IgnoredRecommendationEntity)
}
