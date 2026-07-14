package com.chunyan.devpulsestudio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.chunyan.devpulsestudio.PulseApplication
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.data.AiBrief
import com.chunyan.devpulsestudio.data.AiTrack
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.data.ReadmeLoadResult
import com.chunyan.devpulsestudio.databinding.FragmentArticleDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ArticleDetailFragment : Fragment(R.layout.fragment_article_detail) {

    private val viewModel: PulseViewModel by activityViewModels {
        PulseViewModelFactory(
            (requireActivity().application as PulseApplication).container.repository
        )
    }

    private val repository
        get() = (requireActivity().application as PulseApplication).container.repository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentArticleDetailBinding.bind(view)
        val article = argumentsToArticle()
        var isSaved = false

        // Toolbar
        binding.detailToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Header
        binding.title.text = article.title.substringAfter('/')
        binding.projectByline.text = getString(
            R.string.project_byline, article.author, article.track.label,
        )

        // Risk warning
        binding.riskBanner.visibility = if (article.isRisky) View.VISIBLE else View.GONE
        if (article.isRisky) showRiskDialog()

        // Facts
        binding.facts.text = getString(
            R.string.facts_format,
            article.stars,
            article.forks,
            article.language,
            article.license,
            article.updatedAt.take(10).ifBlank { getString(R.string.unknown) },
        )

        binding.projectPreview.visibility = View.GONE

        // AI brief
        renderBrief(binding, article.brief, article.brief.sourceLabel)

        // Action buttons
        binding.openSource.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
        }
        binding.copyLink.setOnClickListener { copyLink(article) }
        binding.shareProject.setOnClickListener {
            share(article, article.brief.oneLiner)
        }
binding.saveProject.setOnClickListener {
        viewModel.toggleSaved(article)
        Toast.makeText(
            requireContext(),
            if (isSaved) R.string.unsaved_toast else R.string.saved_toast,
            Toast.LENGTH_SHORT,
        ).show()
    }

    // Similar projects
        lateinit var similarAdapter: SimilarProjectAdapter
        similarAdapter = SimilarProjectAdapter(
            onOpen = { candidate ->
                findNavController().navigate(
                    R.id.articleDetailFragment,
                    candidate.detailArguments(),
                )
            },
            onIgnore = { candidate ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.ignoreRecommendation(candidate.id)
                    val updated: List<Article> = similarAdapter.currentList.filterNot { c -> c.id == candidate.id }
                    similarAdapter.submitList(updated)
                    binding.similarSection.visibility = if (updated.isEmpty()) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }
            },
        )
        binding.similarList.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false,
        )
        binding.similarList.adapter = similarAdapter

        // Observe saved state for button text
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saved.collect { saved ->
                    isSaved = saved.any { it.id == article.id }
                    binding.saveProject.setText(
                        if (isSaved) R.string.remove_from_learning
                        else R.string.add_to_learning,
                    )
                    // Visual distinction: filled tonal when saved
                    if (isSaved) {
                        binding.saveProject.setBackgroundColor(
                            requireContext().getColor(R.color.accent_container),
                        )
                        binding.saveProject.setTextColor(
                            requireContext().getColor(R.color.on_accent_container),
                        )
                    } else {
                        binding.saveProject.backgroundTintList = null
                        binding.saveProject.setTextColor(
                            requireContext().getColor(R.color.brand),
                        )
                    }
                }
            }
        }

        // Load README
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.loadReadmeBrief(article)) {
                is ReadmeLoadResult.Success -> {
                    val source = getString(
                        if (result.fromCache) R.string.readme_cached
                        else R.string.readme_live,
                        result.brief.sourceLabel,
                    )
                    renderBrief(binding, result.brief, source)
                }
                is ReadmeLoadResult.Failure -> {
                    binding.readmeHighlights.setText(R.string.readme_unavailable)
                }
            }
        }

        // Load similar
        viewLifecycleOwner.lifecycleScope.launch {
            val similar = repository.similarProjects(article)
            similarAdapter.submitList(similar)
            binding.similarSection.visibility = if (similar.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun renderBrief(
        binding: FragmentArticleDetailBinding,
        brief: AiBrief,
        source: String,
    ) {
        with(binding) {
            score.text = getString(R.string.score_format, brief.score)
            summary.text = brief.oneLiner
            capabilities.text = brief.capabilities.joinToString(" · ")
            audience.text = brief.audience
            strengths.text = brief.strengths
            limitations.text = brief.limitations
            analysisSource.text = source
            evidence.text = brief.evidence

            if (brief.previewImageUrl != null) {
                projectPreview.visibility = View.VISIBLE
                projectPreview.load(brief.previewImageUrl) {
                    crossfade(true)
                    listener(onError = { _, _ -> projectPreview.visibility = View.GONE })
                }
            } else {
                projectPreview.visibility = View.GONE
            }

            readmeHighlights.text = if (brief.readmeHighlights.isEmpty()) {
                getString(R.string.readme_unavailable)
            } else {
                brief.readmeHighlights.joinToString("\n\n")
            }
            readmeHighlights.setLineSpacing(6f, 1f)
        }
    }

    private fun argumentsToArticle(): Article {
        val args = requireArguments()
        return Article(
            id = args.getLong("article_id"),
            title = args.getString("title").orEmpty(),
            summary = args.getString("summary").orEmpty(),
            url = args.getString("url").orEmpty(),
            author = args.getString("author").orEmpty(),
            language = args.getString("language").orEmpty(),
            stars = args.getInt("stars"),
            forks = args.getInt("forks"),
            openIssues = args.getInt("issues"),
            license = args.getString("license") ?: "未声明",
            updatedAt = args.getString("updated").orEmpty(),
            createdAt = args.getString("created").orEmpty(),
            topics = args.getStringArrayList("topics").orEmpty(),
            avatarUrl = args.getString("avatar").orEmpty(),
            track = runCatching { AiTrack.valueOf(args.getString("track").orEmpty()) }
                .getOrDefault(AiTrack.LLM),
            archived = args.getBoolean("archived"),
            isRisky = args.getBoolean("risk"),
        )
    }

    private fun copyLink(article: Article) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(article.title, article.url))
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun share(article: Article, summary: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${article.title}\n$summary\n${article.url}")
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_project)))
    }

    private fun showRiskDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.risk_title)
            .setMessage(R.string.risk_message)
            .setPositiveButton(R.string.continue_view, null)
            .show()
    }
}
