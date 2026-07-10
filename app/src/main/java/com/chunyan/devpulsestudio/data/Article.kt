package com.chunyan.devpulsestudio.data

import com.chunyan.devpulsestudio.data.local.SavedArticleEntity

data class Article(
    val id: Long,
    val title: String,
    val summary: String,
    val author: String,
    val language: String,
    val stars: Int,
    val url: String,
    val avatarUrl: String,
)

fun Article.toEntity() = SavedArticleEntity(
    repositoryId = id,
    title = title,
    summary = summary,
    author = author,
    language = language,
    stars = stars,
    url = url,
    avatarUrl = avatarUrl,
)

fun SavedArticleEntity.toArticle() = Article(
    id = repositoryId,
    title = title,
    summary = summary,
    author = author,
    language = language,
    stars = stars,
    url = url,
    avatarUrl = avatarUrl,
)
