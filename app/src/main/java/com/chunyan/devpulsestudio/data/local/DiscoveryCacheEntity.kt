package com.chunyan.devpulsestudio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A complete, query-specific discovery snapshot. Its payload only contains GitHub data. */
@Entity(tableName = "discovery_cache")
data class DiscoveryCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val total: Int,
    val syncedAt: Long,
)
