package com.chunyan.devpulsestudio.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.databinding.ItemSimilarProjectBinding

class SimilarProjectAdapter(
    private val onOpen: (Article) -> Unit,
    private val onIgnore: (Article) -> Unit,
) : ListAdapter<Article, SimilarProjectAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemSimilarProjectBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(
        private val b: ItemSimilarProjectBinding,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: Article) {
            with(b) {
                similarTitle.text = item.title.substringAfter('/')
                similarTrack.text = listOf(item.track.label, item.language)
                    .filter { it.isNotBlank() && it != "未标注" }
                    .joinToString(" · ")
                similarSummary.text = item.brief.oneLiner
                ignoreSimilar.setOnClickListener { onIgnore(item) }
                root.setOnClickListener { onOpen(item) }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(a: Article, b: Article) = a.id == b.id
        override fun areContentsTheSame(a: Article, b: Article) = a == b
    }
}
