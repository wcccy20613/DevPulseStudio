package com.chunyan.devpulsestudio.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chunyan.devpulsestudio.data.DailyProject
import com.chunyan.devpulsestudio.databinding.ItemDailyProjectBinding

class DailyProjectAdapter(
    private val onOpen: (DailyProject) -> Unit,
) : ListAdapter<DailyProject, DailyProjectAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemDailyProjectBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(
        private val b: ItemDailyProjectBinding,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: DailyProject) {
            with(b) {
                dailyReason.text = item.reason
                dailyTitle.text = item.article.title.substringAfter('/')
                dailySummary.text = item.article.brief.oneLiner
                root.setOnClickListener { onOpen(item) }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DailyProject>() {
        override fun areItemsTheSame(a: DailyProject, b: DailyProject) =
            a.article.id == b.article.id

        override fun areContentsTheSame(a: DailyProject, b: DailyProject) = a == b
    }
}
