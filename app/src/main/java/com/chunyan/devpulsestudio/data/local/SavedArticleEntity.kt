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
    val forks: Int = 0,
    val openIssues: Int = 0,
    val license: String = "未声明",
    val updatedAt: String = "",
    val createdAt: String = "",
    val topics: String = "",
    val track: String = "LLM",
    val archived: Boolean = false,
    val isRisky: Boolean = false,
    val savedAt: Long = System.currentTimeMillis(),
    val collection: String = "未分类",
    val learningStatus: String = "TO_LEARN",
    val aiBriefJson: String = "",
)
