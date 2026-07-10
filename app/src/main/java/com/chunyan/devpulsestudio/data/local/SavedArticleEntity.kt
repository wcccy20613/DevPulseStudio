package com.chunyan.devpulsestudio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_articles")
data class SavedArticleEntity(
    @PrimaryKey val repositoryId: Long,
    val title: String,
    val summary: String,
    val author: String,
    val language: String,
    val stars: Int,
    val url: String,
    val avatarUrl: String,
)
