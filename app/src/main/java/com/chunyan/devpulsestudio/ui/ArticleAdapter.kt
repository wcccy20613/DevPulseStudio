package com.chunyan.devpulsestudio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.databinding.ItemArticleBinding
import java.text.NumberFormat

class ArticleAdapter(
    private val onOpen: (Article) -> Unit,
    private val onSave: (Article) -> Unit,
    private val onManage: ((Article) -> Unit)? = null,
) : ListAdapter<Article, ArticleAdapter.Holder>(Diff) {

    private var savedIds: Set<Long> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemArticleBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateSavedIds(ids: Set<Long>) {
        if (savedIds == ids) return
        savedIds = ids
        notifyDataSetChanged()
    }

    inner class Holder(private val b: ItemArticleBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: Article) {
            with(b) {
                title.text = item.title.substringAfter('/')
                author.text = item.author
                summary.text = item.brief.oneLiner
                track.text = item.track.label

                metadata.text = root.context.getString(
                    R.string.project_metadata,
                    NumberFormat.getIntegerInstance().format(item.stars),
                    NumberFormat.getIntegerInstance().format(item.forks),
                    item.language,
                    item.license,
                    item.updatedAt.take(10).ifBlank { "未知" },
                )

                avatar.load(item.avatarUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_article_placeholder)
                    error(R.drawable.ic_article_placeholder)
                }

                val isSaved = savedIds.contains(item.id)
                // Saved: accent orange full-opacity. Unsaved: muted outline, low alpha.
                if (isSaved) {
                    saveButton.imageTintList = android.content.res.ColorStateList.valueOf(
                        root.context.getColor(R.color.accent),
                    )
                    saveButton.alpha = 1f
                } else {
                    saveButton.imageTintList = android.content.res.ColorStateList.valueOf(
                        root.context.getColor(R.color.text_secondary),
                    )
                    saveButton.alpha = 0.45f
                }
                saveButton.contentDescription = root.context.getString(
                    if (isSaved) R.string.remove_from_learning
                    else R.string.save_article,
                )
                manageButton.isVisible = onManage != null

                manageButton.setOnClickListener { onManage?.invoke(item) }


                saveButton.setOnClickListener {
                    val willBeSaved = !isSaved
                    onSave(item)
                    Toast.makeText(
                        root.context,
                        if (willBeSaved) R.string.saved_toast else R.string.unsaved_toast,
                        Toast.LENGTH_SHORT,
                    ).show()
                    // Tap feedback: scale bounce
                    saveButton.animate()
                        .scaleX(0.7f).scaleY(0.7f)
                        .setDuration(100)
                        .withEndAction {
                            saveButton.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(150)
                                .start()
                        }
                        .start()
                }

                // Hidden copy/share compatibility
                copyButton.setOnClickListener {
                    val clipboard = root.context
                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(item.title, item.url),
                    )
                    Toast.makeText(root.context, R.string.copied, Toast.LENGTH_SHORT).show()
                }

                shareButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${item.title}\n${item.brief.oneLiner}\n学习优先级：${item.brief.score}/10\n${item.url}",
                        )
                    }
                    root.context.startActivity(
                        Intent.createChooser(intent, root.context.getString(R.string.share_project)),
                    )
                }

                githubButton.setOnClickListener {
                    root.context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(item.url)),
                    )
                }

                openButton.setOnClickListener { onOpen(item) }
                root.setOnClickListener { onOpen(item) }
                root.setOnLongClickListener {
                    onManage?.invoke(item)
                    onManage != null
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(a: Article, b: Article) = a.id == b.id
        override fun areContentsTheSame(a: Article, b: Article) = a == b
    }
}
