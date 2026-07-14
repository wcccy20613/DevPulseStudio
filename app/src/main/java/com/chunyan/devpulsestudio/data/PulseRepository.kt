package com.chunyan.devpulsestudio.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

import com.chunyan.devpulsestudio.data.local.DiscoveryCacheDao
import com.chunyan.devpulsestudio.data.local.DiscoveryCacheEntity
import com.chunyan.devpulsestudio.data.local.IgnoredRecommendationDao
import com.chunyan.devpulsestudio.data.local.IgnoredRecommendationEntity
import com.chunyan.devpulsestudio.data.local.ReadmeAnalysisDao
import com.chunyan.devpulsestudio.data.local.ReadmeAnalysisEntity
import com.chunyan.devpulsestudio.data.local.SavedArticleDao
import com.chunyan.devpulsestudio.data.local.SearchHistoryDao
import com.chunyan.devpulsestudio.data.local.SearchHistoryEntity
import com.chunyan.devpulsestudio.data.remote.PulseApi
import com.chunyan.devpulsestudio.data.remote.GatewayInsightRequest
import com.chunyan.devpulsestudio.data.remote.GatewayInsightResponse
import com.chunyan.devpulsestudio.data.remote.InsightGatewayApi
import com.chunyan.devpulsestudio.data.remote.StaticCatalogApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.time.LocalDate
import java.time.Instant
import java.util.Base64

sealed interface LoadResult {
    data class Success(val articles: List<Article>, val total: Int, val fromCache: Boolean, val syncedAt: Long) : LoadResult
    data class Failure(val reason: FailureReason, val cached: List<Article> = emptyList(), val syncedAt: Long? = null) : LoadResult
}

sealed interface ReadmeLoadResult {
    data class Success(val brief: AiBrief, val fromCache: Boolean, val syncedAt: Long) : ReadmeLoadResult
    data class Failure(val reason: FailureReason, val fallback: AiBrief) : ReadmeLoadResult
}

enum class FailureReason { NETWORK, RATE_LIMIT, NOT_FOUND, UNKNOWN }

class PulseRepository(
    private val appContext: Context,
    private val api: PulseApi,
    private val savedArticleDao: SavedArticleDao,
    private val discoveryCacheDao: DiscoveryCacheDao,
    private val readmeAnalysisDao: ReadmeAnalysisDao,
    private val ignoredRecommendationDao: IgnoredRecommendationDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val insightGatewayApi: InsightGatewayApi?,
    private val staticCatalogApi: StaticCatalogApi?,
    private val gson: Gson,
) {
    fun observeSaved(): Flow<List<Article>> = savedArticleDao.observeAll().map { entries -> entries.map { it.toArticle() } }
    fun observeSearchHistory(): Flow<List<String>> = searchHistoryDao.observeRecent().map { entries -> entries.map { it.query } }

    suspend fun loadDailyProjects(): List<DailyProject> {
        val catalog = runCatching { staticCatalogApi?.catalog() }.getOrNull() ?: return emptyList()
        val daily = runCatching { staticCatalogApi?.daily() }.getOrNull() ?: return emptyList()
        if (catalog.schemaVersion != 1 || daily.schemaVersion != 1) return emptyList()
        val byId = catalog.items.associateBy(Article::id)
        return buildList {
            addAll(daily.newProjects.take(5).mapNotNull { byId[it] }.map { DailyProject(it, "新晋开源") })
            addAll(daily.growing.take(5).mapNotNull { byId[it] }.map { DailyProject(it, "热度攀升") })
            addAll(daily.releases.take(5).mapNotNull { byId[it] }.map { DailyProject(it, "新版本") })
            addAll(daily.popular.take(5).mapNotNull { byId[it] }.map { DailyProject(it, "今日热门") })
        }.distinctBy { it.article.id }
    }

    suspend fun loadDiscoveries(ranking: Ranking, track: AiTrack, query: String, page: Int, forceRefresh: Boolean): LoadResult {
        // v2 invalidates historical empty snapshots created by the retired multi-topic OR query.
        val cacheKey = "feed_v3_${ranking.name}_${track.name}_${query.trim().lowercase()}_$page"
        val cached = discoveryCacheDao.find(cacheKey)?.toCacheEntry()
        val cacheAge = System.currentTimeMillis() - (cached?.syncedAt ?: 0L)
        if (!appContext.isNetworkAvailable()) {
            return LoadResult.Failure(FailureReason.NETWORK, cached?.articles.orEmpty(), cached?.syncedAt)
        }
        if (!forceRefresh && cached != null && cacheAge < DISCOVERY_TTL) {
            return LoadResult.Success(cached.articles, cached.total, true, cached.syncedAt)
        }
        return try {
            val result = loadStaticDiscoveries(ranking, track, query, page) ?: loadGitHubDiscoveries(ranking, track, query, page)
            val entry = CacheEntry(result.articles, result.total, result.syncedAt)
            discoveryCacheDao.save(DiscoveryCacheEntity(cacheKey, gson.toJson(entry.articles), entry.total, entry.syncedAt))
            discoveryCacheDao.deleteOlderThan(System.currentTimeMillis() - MAX_CACHE_AGE)
            LoadResult.Success(entry.articles, entry.total, false, entry.syncedAt)
        } catch (error: Exception) {
            LoadResult.Failure(error.toFailureReason(), cached?.articles.orEmpty(), cached?.syncedAt)
        }
    }

    suspend fun loadReadmeBrief(article: Article, forceRefresh: Boolean = false): ReadmeLoadResult {
        val cached = readmeAnalysisDao.find(article.id)
        val cacheSourceVersion = "${article.updatedAt}|$README_ANALYSIS_VERSION"
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.cachedAt < README_TTL && cached.sourceUpdatedAt == cacheSourceVersion) {
            return runCatching { ReadmeLoadResult.Success(gson.fromJson(cached.payload, AiBrief::class.java), true, cached.cachedAt) }
                .getOrElse { ReadmeLoadResult.Failure(FailureReason.UNKNOWN, article.brief) }
        }
        val parts = article.title.split('/', limit = 2)
        if (parts.size != 2) return ReadmeLoadResult.Failure(FailureReason.NOT_FOUND, article.brief)
        return try {
            val response = api.getReadme(parts[0], parts[1])
            val text = response.content.decodeReadme(response.encoding)
            val brief = requestGatewayInsight(article, text) ?: LocalAiInterpreter.interpret(article, text)
            val now = System.currentTimeMillis()
            readmeAnalysisDao.save(ReadmeAnalysisEntity(article.id, gson.toJson(brief), cacheSourceVersion, now))
            readmeAnalysisDao.deleteOlderThan(now - MAX_CACHE_AGE)
            ReadmeLoadResult.Success(brief, false, now)
        } catch (error: Exception) {
            val fallback = cached
                ?.takeIf { it.sourceUpdatedAt == cacheSourceVersion }
                ?.let { runCatching { gson.fromJson(it.payload, AiBrief::class.java) }.getOrNull() }
            if (fallback != null) ReadmeLoadResult.Success(fallback, true, cached.cachedAt)
            else ReadmeLoadResult.Failure(error.toFailureReason(), article.brief)
        }
    }

    suspend fun toggleSaved(article: Article) {
        if (savedArticleDao.isSaved(article.id)) savedArticleDao.remove(article.id)
        else savedArticleDao.save(article.copy(savedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun moveSavedToCollection(repositoryId: Long, collection: String) = savedArticleDao.moveToCollection(repositoryId, collection.trim().ifBlank { "未分类" })
    suspend fun updateLearningStatus(repositoryId: Long, status: LearningStatus) = savedArticleDao.updateLearningStatus(repositoryId, status.name)
    suspend fun ignoreRecommendation(repositoryId: Long) = ignoredRecommendationDao.ignore(IgnoredRecommendationEntity(repositoryId, System.currentTimeMillis()))
    suspend fun rememberSearch(query: String) {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.length >= 2) searchHistoryDao.save(SearchHistoryEntity(normalized, System.currentTimeMillis()))
    }
    suspend fun clearSearchHistory() = searchHistoryDao.clear()

    /**
     * A transparent V1 recommender: it only ranks cached, real repositories by explicit overlap.
     * It is intentionally local until a server-side model can supply accountable personalization.
     */
    suspend fun similarProjects(project: Article, limit: Int = 8): List<Article> {
        val ignored = ignoredRecommendationDao.allIds().toSet() + project.id
        val candidates = discoveryCacheDao.all()
            .mapNotNull { it.toCacheEntry() }
            .flatMap { it.articles }
        return LocalRecommendationRanker.rank(project, candidates, ignored, limit)
    }
    suspend fun exportSaved(): String = gson.toJson(savedArticleDao.getAll())

    suspend fun importSaved(json: String): Int {
        val type = object : TypeToken<List<com.chunyan.devpulsestudio.data.local.SavedArticleEntity>>() {}.type
        val entries: List<com.chunyan.devpulsestudio.data.local.SavedArticleEntity> = gson.fromJson(json, type) ?: emptyList()
        savedArticleDao.saveAll(entries)
        return entries.size
    }

    suspend fun clearCaches() {
        discoveryCacheDao.clear()
        readmeAnalysisDao.clear()
    }

    private fun buildQuery(ranking: Ranking, track: AiTrack, query: String): String = buildList {
        // GitHub Search does not reliably combine multiple topic qualifiers with OR. Use one
        // documented topic baseline per track so the default feed remains a real, non-empty set.
        add(track.searchBaseline())
        if (query.isNotBlank()) add(query.trim().split(Regex("\\s+")).joinToString(" "))
        // Minimum star threshold: quality filter for browsing, lighter for time-constrained rankings.
        // When the user is actively searching we skip the star floor so niche queries still work.
        if (query.isBlank()) {
            when (ranking) {
                Ranking.OVERALL, Ranking.UPDATED -> add("stars:>=50")
                Ranking.MONTHLY -> add("stars:>=10")
                Ranking.WEEKLY -> add("stars:>=5")
                Ranking.DAILY -> { /* no floor — 24h repos are inherently fresh */ }
            }
        }
        val now = LocalDate.now()
        when (ranking) {
            Ranking.DAILY -> add("pushed:>=$now")
            Ranking.WEEKLY -> add("created:>=${now.minusDays(7)}")
            Ranking.MONTHLY -> add("created:>=${now.minusDays(30)}")
            else -> Unit
        }
    }.joinToString(" ")

    private fun AiTrack.searchBaseline() = when (this) {
        AiTrack.ALL -> "topic:machine-learning"
        AiTrack.AGENT -> "topic:agent"
        AiTrack.LLM -> "topic:llm"
        AiTrack.RAG -> "topic:retrieval-augmented-generation"
        AiTrack.MCP -> "topic:model-context-protocol"
        AiTrack.IMAGE -> "topic:generative-ai"
        AiTrack.VIDEO -> "topic:video-generation"
        AiTrack.CODING -> "topic:code-generation"
        AiTrack.ANDROID -> "topic:android artificial-intelligence"
        AiTrack.WEB -> "topic:web artificial-intelligence"
    }

    private fun Ranking.sort() = if (this == Ranking.UPDATED) "updated" else "stars"

    private suspend fun loadStaticDiscoveries(ranking: Ranking, track: AiTrack, query: String, page: Int): DiscoveryPage? {
        val catalog = runCatching { staticCatalogApi?.catalog() }.getOrNull() ?: return null
        if (catalog.schemaVersion != 1) return null
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        val threshold = when (ranking) {
            Ranking.DAILY -> System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            Ranking.WEEKLY -> System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            Ranking.MONTHLY -> System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
            else -> Long.MIN_VALUE
        }
        val filtered = catalog.items.asSequence()
            .filter { track == AiTrack.ALL || it.track == track }
            .filter { article -> ranking !in setOf(Ranking.DAILY, Ranking.WEEKLY, Ranking.MONTHLY) || article.createdAt.toEpochMillis() >= threshold }
            .filter { article ->
                when {
                    query.isNotBlank() -> {
                        val searchable = listOf(article.title, article.author, article.summary, article.language, article.track.label, *article.topics.toTypedArray()).joinToString(" ").lowercase()
                        terms.all(searchable::contains)
                    }
                    ranking == Ranking.OVERALL || ranking == Ranking.UPDATED -> article.stars >= 50
                    ranking == Ranking.MONTHLY -> article.stars >= 10
                    ranking == Ranking.WEEKLY -> article.stars >= 5
                    else -> true // DAILY: no floor
                }
            }
            .sortedWith(
                when (ranking) {
                    Ranking.UPDATED -> compareByDescending<Article> { it.updatedAt.toEpochMillis() }
                    else -> compareByDescending<Article> { it.stars }
                },
            )
            .toList()
        // Static catalog may be stale for time-constrained rankings — fall through to GitHub.
        if (filtered.isEmpty() && ranking in setOf(Ranking.DAILY, Ranking.WEEKLY, Ranking.MONTHLY) && query.isBlank()) {
            return null
        }
        val start = ((page - 1).coerceAtLeast(0)) * PAGE_SIZE
        return DiscoveryPage(filtered.drop(start).take(PAGE_SIZE), filtered.size, catalog.generatedAt.toEpochMillis().takeIf { it > 0 } ?: System.currentTimeMillis())
    }

    private suspend fun loadGitHubDiscoveries(ranking: Ranking, track: AiTrack, query: String, page: Int): DiscoveryPage {
        val response = api.searchRepositories(buildQuery(ranking, track, query), ranking.sort(), "desc", PAGE_SIZE, page)
        val articles = response.items.map { remote ->
            val language = remote.language ?: "未标注"
            val detectedTrack = LocalAiInterpreter.classify(remote.fullName, remote.description, remote.topics, language)
            Article(
                id = remote.id, title = remote.fullName, summary = remote.description.orEmpty(), author = remote.owner.login,
                language = language, stars = remote.stars, url = remote.htmlUrl, avatarUrl = remote.owner.avatarUrl,
                forks = remote.forks, openIssues = remote.openIssues, license = remote.license?.spdxId ?: "未声明",
                updatedAt = remote.updatedAt, createdAt = remote.createdAt, topics = remote.topics, track = detectedTrack,
                archived = remote.archived, isRisky = LocalAiInterpreter.isRisky(remote.fullName, remote.description, remote.topics),
            )
        }
        return DiscoveryPage(articles, response.totalCount, System.currentTimeMillis())
    }
    private fun String?.decodeReadme(encoding: String?): String {
        if (isNullOrBlank()) return ""
        val decoded = if (encoding.equals("base64", ignoreCase = true)) {
            runCatching { Base64.getMimeDecoder().decode(this).toString(Charsets.UTF_8) }.getOrDefault("")
        } else this
        return decoded.take(60_000)
    }

    private suspend fun requestGatewayInsight(article: Article, readme: String): AiBrief? {
        if (readme.isBlank()) return null
        val response = runCatching {
            insightGatewayApi?.analyze(
                GatewayInsightRequest(article.title, article.url, article.language, article.topics, readme)
            )
        }.getOrNull() ?: return null
        return response.toBrief()
    }

    private fun GatewayInsightResponse.toBrief() = AiBrief(
        oneLiner = oneLiner.trim().ifBlank { "云端解读未提供项目概述。" },
        capabilities = capabilities.map(String::trim).filter(String::isNotBlank).distinct().take(5),
        audience = audience.trim().ifBlank { "请结合 README 判断适配人群。" },
        strengths = strengths.trim().ifBlank { "请以项目公开证据为准。" },
        limitations = limitations.trim().ifBlank { "模型结论不等同于代码审计或生产保证。" },
        score = score.coerceIn(1, 10),
        evidence = evidence.trim().ifBlank { "云端解读应以 README 与公开仓库元数据为依据。" },
        sourceLabel = "AI 云端解读${modelVersion?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
        readmeHighlights = readmeHighlights.map(String::trim).filter(String::isNotBlank).distinct().take(5),
    )

    private fun DiscoveryCacheEntity.toCacheEntry(): CacheEntry? = runCatching {
        val type = object : TypeToken<List<Article>>() {}.type
        CacheEntry(gson.fromJson(payload, type) ?: emptyList(), total, syncedAt)
    }.getOrNull()

    private fun String.toEpochMillis(): Long = runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)

    private fun Exception.toFailureReason() = when {
        this is HttpException && (code() == 403 || code() == 429) -> FailureReason.RATE_LIMIT
        this is HttpException && code() == 404 -> FailureReason.NOT_FOUND
        this is java.io.IOException -> FailureReason.NETWORK
        else -> FailureReason.UNKNOWN
    }

    private fun Context.isNetworkAvailable(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private data class CacheEntry(val articles: List<Article>, val total: Int, val syncedAt: Long)
    private data class DiscoveryPage(val articles: List<Article>, val total: Int, val syncedAt: Long)

    companion object {
        const val PAGE_SIZE = 20
        const val DISCOVERY_TTL = 12 * 60 * 60 * 1000L
        private const val README_ANALYSIS_VERSION = 6
        const val README_TTL = 7 * 24 * 60 * 60 * 1000L
        const val MAX_CACHE_AGE = 30 * 24 * 60 * 60 * 1000L
    }
}
