package com.chunyan.devpulsestudio.ui

import androidx.core.os.bundleOf
import com.chunyan.devpulsestudio.data.Article

/** Keeps the detail screen independent from a fragile shared in-memory list. */
fun Article.detailArguments() = bundleOf(
    "article_id" to id,
    "title" to title,
    "summary" to summary,
    "url" to url,
    "author" to author,
    "language" to language,
    "stars" to stars,
    "forks" to forks,
    "issues" to openIssues,
    "license" to license,
    "updated" to updatedAt,
    "created" to createdAt,
    "topics" to ArrayList(topics),
    "track" to track.name,
    "archived" to archived,
    "risk" to isRisky,
    "avatar" to avatarUrl,
)
