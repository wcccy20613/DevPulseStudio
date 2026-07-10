package com.chunyan.devpulsestudio.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.databinding.ItemArticleBinding

class ArticleAdapter(
    private val onOpen: (Article) -> Unit,
    private val onSave: (Article) -> Unit,
) : ListAdapter<Article, ArticleAdapter.ArticleViewHolder>(ArticleDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder = ArticleViewHolder(
        ItemArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ArticleViewHolder(private val binding: ItemArticleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(article: Article) = with(binding) {
            title.text = article.title
            summary.text = article.summary
            metadata.text = root.context.getString(R.string.article_metadata, article.language, article.stars, article.author)
            avatar.load(article.avatarUrl) {
                crossfade(true)
                placeholder(com.chunyan.devpulsestudio.R.drawable.ic_article_placeholder)
                error(com.chunyan.devpulsestudio.R.drawable.ic_article_placeholder)
            }
            saveButton.setOnClickListener { onSave(article) }
            root.setOnClickListener { onOpen(article) }
        }
    }

    private object ArticleDiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Article, newItem: Article) = oldItem == newItem
    }
}
