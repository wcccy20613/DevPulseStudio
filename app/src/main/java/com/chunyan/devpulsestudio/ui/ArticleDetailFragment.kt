package com.chunyan.devpulsestudio.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.databinding.FragmentArticleDetailBinding

class ArticleDetailFragment : Fragment(R.layout.fragment_article_detail) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentArticleDetailBinding.bind(view)
        val title = requireArguments().getString("title").orEmpty()
        val summary = requireArguments().getString("summary").orEmpty()
        val url = requireArguments().getString("url").orEmpty()

        binding.title.text = title
        binding.summary.text = summary
        binding.openSource.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
