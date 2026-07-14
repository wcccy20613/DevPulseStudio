package com.chunyan.devpulsestudio

import com.chunyan.devpulsestudio.data.Article
import com.chunyan.devpulsestudio.data.AiBrief
import com.chunyan.devpulsestudio.data.toArticle
import com.chunyan.devpulsestudio.data.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleMappingTest {
    @Test
    fun `saved article keeps its display data`() {
        val article = Article(7, "Pulse", "Summary", "Wang", "Kotlin", 42, "https://example.com", "")

        assertEquals(article, article.toEntity().toArticle())
    }

    @Test
    fun `saved article preserves generated AI brief`() {
        val brief = AiBrief("Local RAG toolkit", listOf("Retrieval"), "Android developers", "Public README", "Verify deployment", 7, "README", "AI 定时解读")
        val article = Article(8, "owner/rag", "RAG", "owner", "Kotlin", 12, "https://example.com", "", aiBrief = brief)

        assertEquals(brief, article.toEntity().toArticle().aiBrief)
    }
}
