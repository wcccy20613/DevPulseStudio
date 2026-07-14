package com.chunyan.devpulsestudio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached evidence-based README reading result; it never stores a hidden model prompt or key. */
@Entity(tableName = "readme_analyses")
data class ReadmeAnalysisEntity(
    @PrimaryKey val repositoryId: Long,
    val payload: String,
    val sourceUpdatedAt: String,
    val cachedAt: Long,
)
