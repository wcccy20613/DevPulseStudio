package com.chunyan.devpulsestudio

import com.chunyan.devpulsestudio.data.*
import org.junit.Assert.*
import org.junit.Test

class LocalAiInterpreterTest {
    @Test fun `classifies MCP before generic LLM`() {
        assertEquals(AiTrack.MCP, LocalAiInterpreter.classify("server", "Model Context Protocol tools", listOf("mcp"), "Python"))
    }

    @Test fun `deepfake repository is flagged`() {
        assertTrue(LocalAiInterpreter.isRisky("face-swap", "real time deepfake", emptyList()))
    }

    @Test fun `missing evidence does not produce absolute recommendation`() {
        val article = Article(1, "owner/repo", "", "owner", "", 0, "https://github.com/owner/repo", "")
        val brief = article.brief
        assertTrue(brief.oneLiner.contains("开源项目"))
        assertTrue(brief.limitations.contains("不足"))
        assertTrue(brief.score < 8)
    }

    @Test fun `archived project receives explicit warning`() {
        val article = Article(1, "owner/repo", "AI toolkit", "owner", "Python", 30_000, "https://github.com/owner/repo", "", archived = true)
        assertTrue(article.brief.limitations.contains("归档"))
        assertTrue(article.brief.score <= 7)
    }

    @Test fun `README reader exposes headings as evidence rather than inventing capabilities`() {
        val article = Article(1, "owner/repo", "", "owner", "Python", 10, "https://github.com/owner/repo", "")
        val brief = LocalAiInterpreter.interpret(article, "# Project Name\n\nA verifiable local tool.\n\n## Installation\n\nInstall the package locally.\n\n## Usage\n\nRun the command.")

        assertEquals("文档证据化速读", brief.sourceLabel)
        assertTrue(brief.readmeHighlights.any { it.contains("安装与配置") })
        assertTrue(brief.evidence.contains("README"))
    }

    @Test fun `Chinese README sections preserve their useful Chinese content`() {
        val highlights = LocalAiInterpreter.extractReadmeHighlights(
            "# 项目说明\n\n这是面向开发者的中文使用说明，包含本地部署步骤和配置建议。",
        )

        assertTrue(highlights.single().contains("本地部署步骤和配置建议"))
        assertFalse(highlights.single().contains("README 包含可核验的项目文档"))
    }

    @Test fun `local recommender favors explained metadata overlap and honors negative feedback`() {
        val source = Article(1, "org/source", "", "org", "Kotlin", 100, "https://example.com/1", "", topics = listOf("mcp", "agent"), track = AiTrack.MCP)
        val strongest = Article(2, "org/strong", "", "org", "Kotlin", 50, "https://example.com/2", "", topics = listOf("mcp", "agent"), track = AiTrack.MCP)
        val weaker = Article(3, "org/weaker", "", "org", "Python", 500, "https://example.com/3", "", topics = listOf("mcp"), track = AiTrack.MCP)

        assertEquals(listOf(2L, 3L), LocalRecommendationRanker.rank(source, listOf(strongest, weaker), emptySet()).map(Article::id))
        assertEquals(listOf(3L), LocalRecommendationRanker.rank(source, listOf(strongest, weaker), setOf(2L)).map(Article::id))
    }
}
