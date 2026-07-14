package com.chunyan.devpulsestudio.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chunyan.devpulsestudio.PulseApplication
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.data.AiTrack
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.data.FailureReason
import com.chunyan.devpulsestudio.data.Ranking
import com.chunyan.devpulsestudio.databinding.FragmentArticleListBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ExploreFragment : Fragment(R.layout.fragment_article_list) {

    private val viewModel: PulseViewModel by activityViewModels {
        PulseViewModelFactory(
            (requireActivity().application as PulseApplication).container.repository
        )
    }
    private var binding: FragmentArticleListBinding? = null

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val PAGING_THRESHOLD = 4
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentArticleListBinding.bind(view)
        binding = b

        // Setup RecyclerViews
        val adapter = ArticleAdapter(
            onOpen = ::openArticle,
            onSave = viewModel::toggleSaved,
        )
        val layoutManager = LinearLayoutManager(requireContext())
        b.list.layoutManager = layoutManager
        b.list.adapter = adapter
        b.list.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220
            moveDuration = 220
            removeDuration = 180
        }

        val dailyAdapter = DailyProjectAdapter { openArticle(it.article) }
        b.dailyList.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false,
        )
        b.dailyList.adapter = dailyAdapter

        // Setup filter chips
        setupChips(b)

        // Search input
        b.searchInput.setText(viewModel.discover.value.query)
        b.searchInput.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            val showHistory = query.isBlank() && viewModel.searchHistory.value.isNotEmpty()
            b.searchHistoryScroll.visibility = if (showHistory) View.VISIBLE else View.GONE
            viewModel.setQuery(query)
        }

        // Pull to refresh
        b.swipeRefresh.setColorSchemeResources(R.color.brand, R.color.accent)
        b.swipeRefresh.setOnRefreshListener { viewModel.refresh(force = true) }
        b.retryButton.setOnClickListener { viewModel.refresh(force = true) }

        // Toolbar menu
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    viewModel.refresh(force = true)
                    true
                }
                R.id.action_rules -> {
                    showRankingRules()
                    true
                }
                else -> false
            }
        }

        // Pagination on scroll
        b.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - PAGING_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
        })

        // Collect state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.discover.collect { render(b, adapter, it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saved.collect { saved ->
                    adapter.updateSavedIds(saved.map(Article::id).toSet())
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchHistory.collect { renderSearchHistory(b, it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dailyProjects.collect { projects ->
                    dailyAdapter.submitList(projects)
                    b.dailySection.visibility = if (projects.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun setupChips(b: FragmentArticleListBinding) {
        AiTrack.entries.forEach { track ->
            b.trackChips.addView(Chip(requireContext()).apply {
                text = track.label
                isCheckable = true
                isChecked = track == viewModel.discover.value.track
                setOnClickListener { viewModel.setTrack(track) }
            })
        }
        Ranking.entries.forEach { ranking ->
            b.rankingChips.addView(Chip(requireContext()).apply {
                text = ranking.label
                isCheckable = true
                isChecked = ranking == viewModel.discover.value.ranking
                setOnClickListener { viewModel.setRanking(ranking) }
            })
        }
    }

    private fun render(
        b: FragmentArticleListBinding,
        adapter: ArticleAdapter,
        state: DiscoverUiState,
    ) {
        b.progress.visibility = View.GONE
        b.skeletonContainer.visibility = if (state.isLoading && state.articles.isEmpty()) {
            startShimmer(b.skeletonContainer)
            View.VISIBLE
        } else {
            stopShimmer(b.skeletonContainer)
            View.GONE
        }
        b.swipeRefresh.isRefreshing = state.isLoading && state.articles.isNotEmpty()
        adapter.submitList(state.articles)

        val isEmpty = !state.isLoading && state.articles.isEmpty()
        b.emptyContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        b.offlineBanner.visibility = if (state.isStaleCache) View.VISIBLE else View.GONE

        b.retryButton.visibility = if (state.error != null) View.VISIBLE else View.GONE

        b.emptyMessage.text = when (state.error) {
            FailureReason.RATE_LIMIT -> getString(R.string.error_rate_limit)
            FailureReason.NETWORK -> getString(R.string.error_network)
            FailureReason.NOT_FOUND -> getString(R.string.empty_search)
            FailureReason.UNKNOWN -> getString(R.string.error_unknown)
            null -> getString(R.string.empty_search)
        }

        // Empty icon: search vs generic
        b.emptyIcon.setImageResource(
            if (state.error != null) R.drawable.ic_empty_search
            else R.drawable.ic_empty_search,
        )

        val time = state.syncedAt?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
        } ?: getString(R.string.never_synced)

        b.statusText.text = getString(
            when {
                state.isStaleCache -> R.string.stale_cache_status
                state.fromCache -> R.string.cache_status
                else -> R.string.live_status
            },
            state.total,
            time,
        )
    }

    private fun renderSearchHistory(
        b: FragmentArticleListBinding,
        history: List<String>,
    ) {
        b.searchHistoryChips.removeAllViews()
        history.forEach { query ->
            b.searchHistoryChips.addView(Chip(requireContext()).apply {
                text = query
                isCheckable = false
                setOnClickListener {
                    b.searchInput.setText(query)
                    b.searchInput.setSelection(query.length)
                }
            })
        }
        if (history.isNotEmpty()) {
            b.searchHistoryChips.addView(Chip(requireContext()).apply {
                text = getString(R.string.clear_search_history)
                isCheckable = false
                setOnClickListener { viewModel.clearSearchHistory() }
            })
        }
        val show = history.isNotEmpty() && b.searchInput.text.isNullOrBlank()
        b.searchHistoryScroll.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun startShimmer(container: ViewGroup) {
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.shimmer_pulse)
        for (i in 0 until container.childCount) {
            container.getChildAt(i).startAnimation(animation)
        }
    }

    private fun stopShimmer(container: ViewGroup) {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).clearAnimation()
        }
    }

    private fun showRankingRules() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ranking_rules_title)
            .setMessage(R.string.ranking_rules)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openArticle(article: Article) = navigate(article)

    private fun navigate(article: Article) {
        findNavController().navigate(R.id.articleDetailFragment, article.detailArguments())
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
