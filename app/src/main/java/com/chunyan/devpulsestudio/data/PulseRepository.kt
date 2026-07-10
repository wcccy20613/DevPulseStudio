package com.chunyan.devpulsestudio.data

import com.chunyan.devpulsestudio.data.local.SavedArticleDao
import com.chunyan.devpulsestudio.data.remote.PulseApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PulseRepository(
    private val api: PulseApi,
    private val savedArticleDao: SavedArticleDao,
) {
    fun observeSaved(): Flow<List<Article>> = savedArticleDao.observeAll().map { entries ->
        entries.map { it.toArticle() }
    }

    suspend fun loadDiscoveries(): List<Article> = runCatching {
        api.searchRepositories("topic:android language:kotlin stars:>1000").items.map { repository ->
            Article(
                id = repository.id,
                title = repository.fullName,
                summary = repository.description ?: "A Kotlin project worth studying.",
                author = repository.owner.login,
                language = repository.language ?: "Kotlin",
                stars = repository.stars,
                url = repository.htmlUrl,
                avatarUrl = repository.owner.avatarUrl,
            )
        }
    }.getOrElse { curatedFallback }

    suspend fun toggleSaved(article: Article) {
        if (savedArticleDao.isSaved(article.id)) {
            savedArticleDao.remove(article.id)
        } else {
            savedArticleDao.save(article.toEntity())
        }
    }

    private val curatedFallback = listOf(
        Article(1, "Compose performance notes", "A practical checklist for measuring recomposition and startup time.", "DevPulse", "Kotlin", 1280, "https://developer.android.com/jetpack/compose/performance", ""),
        Article(2, "Offline-first field guide", "Patterns for keeping a local source of truth with Room and Flow.", "DevPulse", "Android", 960, "https://developer.android.com/topic/architecture/data-layer/offline-first", ""),
        Article(3, "Release readiness", "A compact release checklist covering quality, privacy, and store delivery.", "DevPulse", "Engineering", 740, "https://developer.android.com/studio/publish", ""),
    )
}
