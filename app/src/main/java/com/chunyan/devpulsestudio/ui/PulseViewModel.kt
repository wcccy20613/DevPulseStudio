package com.chunyan.devpulsestudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.data.AiTrack
import com.chunyan.devpulsestudio.data.DailyProject
import com.chunyan.devpulsestudio.data.FailureReason
import com.chunyan.devpulsestudio.data.LearningStatus
import com.chunyan.devpulsestudio.data.LoadResult
import com.chunyan.devpulsestudio.data.PulseRepository
import com.chunyan.devpulsestudio.data.Ranking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val isPaging: Boolean = false,
    val articles: List<Article> = emptyList(),
    val query: String = "",
    val track: AiTrack = AiTrack.ALL,
    val ranking: Ranking = Ranking.OVERALL,
    val page: Int = 1,
    val total: Int = 0,
    val fromCache: Boolean = false,
    val isStaleCache: Boolean = false,
    val syncedAt: Long? = null,
    val error: FailureReason? = null,
) {
    val canLoadMore: Boolean
        get() = !isLoading && !isPaging && error == null && articles.size < total
}

// ── ViewModel ─────────────────────────────────────────────

class PulseViewModel(
    private val repository: PulseRepository,
) : ViewModel() {

    private val _discover = MutableStateFlow(DiscoverUiState())
    val discover: StateFlow<DiscoverUiState> = _discover.asStateFlow()

    val saved: StateFlow<List<Article>> = repository
        .observeSaved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchHistory: StateFlow<List<String>> = repository
        .observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dailyProjects = MutableStateFlow<List<DailyProject>>(emptyList())
    val dailyProjects: StateFlow<List<DailyProject>> = _dailyProjects.asStateFlow()

    private val discoverRequests = LatestRequestCoordinator(viewModelScope)

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L
    }

    init {
        refresh()
        refreshDailyProjects()
    }

    // ── Filters ───────────────────────────────────────

    fun setQuery(value: String) {
        if (_discover.value.query == value) return
        _discover.update { it.copy(query = value) }
        requestFirstPage(debounceMs = SEARCH_DEBOUNCE_MS)
    }

    fun setTrack(value: AiTrack) {
        if (_discover.value.track == value) return
        _discover.update { it.copy(track = value) }
        refresh()
    }

    fun setRanking(value: Ranking) {
        if (_discover.value.ranking == value) return
        _discover.update { it.copy(ranking = value) }
        refresh()
    }

    // ── Data loading ──────────────────────────────────

    fun refresh(force: Boolean = false) {
        requestFirstPage(force = force)
    }

    fun loadMore() {
        val current = _discover.value
        if (!current.canLoadMore) return

        _discover.update { it.copy(isPaging = true) }
        discoverRequests.launchLatest { requestId ->
            val result = repository.loadDiscoveries(
                ranking = current.ranking,
                track = current.track,
                query = current.query,
                page = current.page + 1,
                forceRefresh = false,
            )
            currentCoroutineContext().ensureActive()
            if (discoverRequests.isCurrent(requestId)) {
                applyResult(result, append = true, query = current.query)
            }
        }
    }

    private fun requestFirstPage(force: Boolean = false, debounceMs: Long = 0L) {
        discoverRequests.launchLatest(debounceMs) { requestId ->
            val current = _discover.value
            _discover.value = current.copy(
                isLoading = true,
                isPaging = false,
                error = null,
                page = 1,
            )
            val result = repository.loadDiscoveries(
                ranking = current.ranking,
                track = current.track,
                query = current.query,
                page = 1,
                forceRefresh = force,
            )
            currentCoroutineContext().ensureActive()
            if (discoverRequests.isCurrent(requestId)) {
                applyResult(result, append = false, query = current.query)
                if (force) refreshDailyProjects()
            }
        }
    }

    private suspend fun applyResult(result: LoadResult, append: Boolean, query: String) {
        when (result) {
            is LoadResult.Success -> {
                _discover.update { old ->
                    old.copy(
                        isLoading = false,
                        isPaging = false,
                        articles = if (append) {
                            (old.articles + result.articles).distinctBy(Article::id)
                        } else {
                            result.articles
                        },
                        page = if (append) old.page + 1 else 1,
                        total = result.total,
                        fromCache = result.fromCache,
                        syncedAt = result.syncedAt,
                        isStaleCache = false,
                        error = null,
                    )
                }
            }
            is LoadResult.Failure -> {
                _discover.update { old ->
                    old.copy(
                        isLoading = false,
                        isPaging = false,
                        articles = if (append) old.articles else result.cached,
                        fromCache = result.cached.isNotEmpty(),
                        isStaleCache = result.cached.isNotEmpty(),
                        syncedAt = result.syncedAt,
                        error = if (result.cached.isEmpty()) result.reason else null,
                    )
                }
            }
        }
        if (result is LoadResult.Success && !append) {
            repository.rememberSearch(query)
        }
    }

    override fun onCleared() {
        discoverRequests.cancel()
        super.onCleared()
    }

    // ── Saved article actions ─────────────────────────

    fun toggleSaved(article: Article) = viewModelScope.launch {
        repository.toggleSaved(article)
    }

    fun moveSavedToCollection(article: Article, collection: String) = viewModelScope.launch {
        repository.moveSavedToCollection(article.id, collection)
    }

    fun setLearningStatus(article: Article, status: LearningStatus) = viewModelScope.launch {
        repository.updateLearningStatus(article.id, status)
    }

    fun clearSearchHistory() = viewModelScope.launch {
        repository.clearSearchHistory()
    }

    fun refreshDailyProjects() = viewModelScope.launch {
        _dailyProjects.value = repository.loadDailyProjects()
    }
}

// ── Factory ───────────────────────────────────────────────

class PulseViewModelFactory(
    private val repository: PulseRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PulseViewModel::class.java))
        return PulseViewModel(repository) as T
    }
}
