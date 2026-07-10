package com.chunyan.devpulsestudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.data.PulseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val articles: List<Article> = emptyList(),
)

class PulseViewModel(private val repository: PulseRepository) : ViewModel() {
    private val _discover = MutableStateFlow(DiscoverUiState())
    val discover: StateFlow<DiscoverUiState> = _discover.asStateFlow()

    val saved: StateFlow<List<Article>> = repository.observeSaved().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _discover.value = _discover.value.copy(isLoading = true)
        _discover.value = DiscoverUiState(isLoading = false, articles = repository.loadDiscoveries())
    }

    fun toggleSaved(article: Article) = viewModelScope.launch {
        repository.toggleSaved(article)
    }
}

class PulseViewModelFactory(private val repository: PulseRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PulseViewModel::class.java))
        return PulseViewModel(repository) as T
    }
}
