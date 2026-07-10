package com.chunyan.devpulsestudio

import com.chunyan.devpulsestudio.data.Article
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
}
