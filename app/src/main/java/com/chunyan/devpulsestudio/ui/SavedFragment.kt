package com.chunyan.devpulsestudio.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chunyan.devpulsestudio.PulseApplication
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.databinding.FragmentArticleListBinding
import kotlinx.coroutines.launch

class SavedFragment : Fragment(R.layout.fragment_article_list) {
    private val viewModel: PulseViewModel by activityViewModels {
        PulseViewModelFactory((requireActivity().application as PulseApplication).container.repository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentArticleListBinding.bind(view)
        val adapter = ArticleAdapter(::openArticle, viewModel::toggleSaved)
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.swipeRefresh.isEnabled = false
        binding.emptyMessage.setText(R.string.empty_saved)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saved.collect { articles ->
                    binding.progress.visibility = View.GONE
                    binding.emptyMessage.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
                    adapter.submitList(articles)
                }
            }
        }
    }

    private fun openArticle(article: Article) {
        findNavController().navigate(R.id.articleDetailFragment, bundleOf(
            "title" to article.title,
            "summary" to article.summary,
            "url" to article.url,
        ))
    }
}
