package com.chunyan.devpulsestudio.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.chunyan.devpulsestudio.PulseApplication
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.data.LearningStatus
import com.chunyan.devpulsestudio.databinding.FragmentArticleListBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SavedFragment : Fragment(R.layout.fragment_article_list) {

    private val viewModel: PulseViewModel by activityViewModels {
        PulseViewModelFactory(
            (requireActivity().application as PulseApplication).container.repository
        )
    }

    private var allArticles = emptyList<Article>()
    private var collectionFilter: String? = null
    private var statusFilter: LearningStatus? = null
    private var binding: FragmentArticleListBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentArticleListBinding.bind(view)
        binding = b

        // Setup RecyclerView
        val adapter = ArticleAdapter(
            onOpen = ::openArticle,
            onSave = viewModel::toggleSaved,
            onManage = ::manageArticle,
        )
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.list.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220
            moveDuration = 220
            removeDuration = 180
        }
        b.swipeRefresh.isEnabled = false

        // Toolbar
        b.toolbar.title = getString(R.string.saved_title)
        b.toolbar.subtitle = getString(R.string.saved_subtitle)
        b.toolbar.menu.clear()

        // This screen reuses the discovery layout, but its filters have library semantics.
        b.trackLabel.setText(R.string.collection_filter_label)
        b.rankingLabel.setText(R.string.learning_status_filter_label)


        // Search
        b.searchLayout.hint = getString(R.string.search_saved)
        b.retryButton.setText(R.string.browse_discover)
        b.retryButton.setOnClickListener {
            findNavController().navigate(R.id.exploreFragment)
        }
        b.emptyIcon.setImageResource(R.drawable.ic_empty_saved)

        fun applyFilter(text: String) {
            val query = text.trim()
            val visible = allArticles.filter { article ->
                (collectionFilter == null || article.collection == collectionFilter) &&
                    (statusFilter == null || article.learningStatus == statusFilter) &&
                    (query.isBlank() || matchesQuery(article, query))
            }
            adapter.submitList(visible)
            b.emptyContainer.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
            val isLibraryEmpty = allArticles.isEmpty()
            b.emptyMessage.text = if (isLibraryEmpty) {
                getString(R.string.empty_saved)
            } else {
                getString(R.string.empty_saved_search)
            }
            b.retryButton.visibility = if (isLibraryEmpty) View.VISIBLE else View.GONE
        }

        b.searchInput.doOnTextChanged { text, _, _, _ ->
            applyFilter(text?.toString().orEmpty())
        }

        // Observe saved articles
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saved.collect { articles ->
                    allArticles = articles
                    b.progress.visibility = View.GONE
                    adapter.updateSavedIds(articles.map(Article::id).toSet())

                    b.statusText.text = getString(
                        R.string.saved_library_summary,
                        articles.size,
                        articles.groupBy { it.collection }.size,
                        articles.count { it.learningStatus == LearningStatus.LEARNING },
                    )

                    renderFilters(b, ::applyFilter)
                    applyFilter(b.searchInput.text?.toString().orEmpty())
                }
            }
        }
    }

    private fun matchesQuery(article: Article, query: String): Boolean {
        val fields = listOf(
            article.title, article.author, article.summary,
            article.language, article.track.label, article.collection,
        )
        return fields.any { it.contains(query, ignoreCase = true) }
    }

    private fun renderFilters(
        b: FragmentArticleListBinding,
        onFilter: (String) -> Unit,
    ) {
        b.trackChips.removeAllViews()
        b.rankingChips.removeAllViews()

        // Collection filter chips
        val collections = listOf(null) + allArticles.map { it.collection }.distinct().sorted()
        collections.forEach { collection ->
            b.trackChips.addView(Chip(requireContext()).apply {
                text = collection ?: getString(R.string.all_collections)
                isCheckable = true
                isChecked = collection == collectionFilter
                setOnClickListener {
                    collectionFilter = collection
                    onFilter(b.searchInput.text?.toString().orEmpty())
                }
            })
        }

        // Status filter chips
        val statuses = listOf<LearningStatus?>(null) + LearningStatus.entries
        statuses.forEach { status ->
            b.rankingChips.addView(Chip(requireContext()).apply {
                text = status?.label ?: getString(R.string.all_statuses)
                isCheckable = true
                isChecked = status == statusFilter
                setOnClickListener {
                    statusFilter = status
                    onFilter(b.searchInput.text?.toString().orEmpty())
                }
            })
        }
    }

    private fun manageArticle(article: Article) {
        val actions = arrayOf(
            getString(R.string.mark_to_learn),
            getString(R.string.mark_learning),
            getString(R.string.mark_learned),
            getString(R.string.move_collection),
            getString(R.string.remove_from_learning),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(article.title.substringAfter('/'))
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> viewModel.setLearningStatus(article, LearningStatus.TO_LEARN)
                    1 -> viewModel.setLearningStatus(article, LearningStatus.LEARNING)
                    2 -> viewModel.setLearningStatus(article, LearningStatus.LEARNED)
                    3 -> editCollection(article)
                    4 -> viewModel.toggleSaved(article)
                }
            }
            .show()
    }

    private fun editCollection(article: Article) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.collection_hint)
            setText(article.collection)
            setSelectAllOnFocus(true)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.move_collection)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.moveSavedToCollection(article, input.text.toString())
            }
            .show()
    }

    private fun openArticle(article: Article) {
        findNavController().navigate(R.id.articleDetailFragment, article.detailArguments())
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
