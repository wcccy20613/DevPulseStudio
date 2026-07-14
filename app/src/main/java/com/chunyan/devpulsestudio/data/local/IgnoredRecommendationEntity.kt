package com.chunyan.devpulsestudio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Explicit negative feedback is local-only and never uploaded by the Android client. */
@Entity(tableName = "ignored_recommendations")
data class IgnoredRecommendationEntity(
    @PrimaryKey val repositoryId: Long,
    val ignoredAt: Long,
)
