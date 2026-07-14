package com.chunyan.devpulsestudio.data

import com.chunyan.devpulsestudio.data.local.SavedArticleEntity
import java.util.Locale

enum class AiTrack(val label: String) {
    ALL("全部"), AGENT("Agent"), LLM("LLM"), RAG("RAG"), MCP("MCP"),
    IMAGE("图像"), VIDEO("视频"), CODING("AI 编程"), ANDROID("安卓 AI"), WEB("前端 AI")
}

enum class Ranking(val label: String) {
    OVERALL("总榜"), DAILY("24h 活跃"), WEEKLY("7日新晋"), MONTHLY("30日新晋"), UPDATED("最近更新")
}

enum class LearningStatus(val label: String) {
    TO_LEARN("待研读"), LEARNING("学习中"), LEARNED("已学习")
}

data class AiBrief(
    val oneLiner: String,
    val capabilities: List<String>,
    val audience: String,
    val strengths: String,
    val limitations: String,
    val score: Int,
    val evidence: String,
    val sourceLabel: String,
    val readmeHighlights: List<String> = emptyList(),
    val previewImageUrl: String? = null,
)

data class Article(
    val id: Long,
    val title: String,
    val summary: String,
    val author: String,
    val language: String,
    val stars: Int,
    val url: String,
    val avatarUrl: String,
    val forks: Int = 0,
    val openIssues: Int = 0,
    val license: String = "未声明",
    val updatedAt: String = "",
    val createdAt: String = "",
    val topics: List<String> = emptyList(),
    val track: AiTrack = AiTrack.LLM,
    val archived: Boolean = false,
    val isRisky: Boolean = false,
    val savedAt: Long = 0L,
    val collection: String = "未分类",
    val learningStatus: LearningStatus = LearningStatus.TO_LEARN,
    val aiBrief: AiBrief? = null,
) {
    val brief: AiBrief get() = aiBrief ?: LocalAiInterpreter.interpret(this)
}

object LocalAiInterpreter {
    /**
     * V1's local reader is deliberately evidence-bound: it only organises public repository
     * metadata and supplied README text. A cloud model may replace this implementation later.
     */
    fun interpret(article: Article, readme: String? = null): AiBrief {
        val description = article.summary.trim().takeIf { it.isNotBlank() }
        val cleanReadme = readme?.replace(Regex("```[\\s\\S]*?```"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val readmeLead = cleanReadme.takeIf { it.isNotBlank() }?.take(180)
        val oneLiner = chineseOneLiner(article, description, readmeLead)
        val capabilities = buildList {
            add("聚焦 ${article.track.label} 方向")
            if (article.language.isNotBlank()) add("主要使用 ${article.language}")
            if (article.topics.isNotEmpty()) add("含 ${article.topics.size} 个公开话题标签")
        }.distinct().take(3)
        val freshness = article.updatedAt.take(10).ifBlank { "未知" }
        val evidenceCount = listOf(description, readmeLead, article.language.takeIf { it.isNotBlank() }, article.license.takeIf { it != "未声明" })
            .count { it != null } + article.topics.size.coerceAtMost(2)
        val score = (5 + when {
            article.stars >= 20_000 -> 2
            article.stars >= 2_000 -> 1
            else -> 0
        } + if (article.archived) -2 else 0 + if (evidenceCount >= 4) 1 else 0).coerceIn(1, 9)
        return AiBrief(
            oneLiner = oneLiner,
            capabilities = capabilities,
            audience = "适合关注 ${article.track.label}、希望评估开源方案的开发者",
            strengths = "已有 ${article.stars} Stars、${article.forks} Forks；最近更新：$freshness。",
            limitations = when {
                article.archived -> "仓库已归档，不建议直接用于新的生产项目。"
                description == null && readmeLead == null -> "公开描述与 README 依据不足，无法判断成熟度与部署成本。"
                article.license == "未声明" -> "未检测到明确开源许可，商用前需核实授权。"
                readmeLead == null -> "尚未获取 README；生产采用前仍需验证文档、Issue 与许可证。"
                else -> "这是基于公开 README 的证据化速读，不等同于代码审计或生产可用性保证。"
            },
            score = score,
            evidence = "基于 GitHub 公开描述、话题标签、Stars、Forks、许可证${if (readmeLead != null) "与 README 文本" else "与更新时间"}生成，不等同于完整代码审计。",
            sourceLabel = if (readmeLead != null) "文档证据化速读" else "公开信息速读",
            readmeHighlights = extractReadmeHighlights(readme),
            previewImageUrl = extractPreviewImage(readme),
        )
    }

    fun extractReadmeHighlights(readme: String?): List<String> {
        val lines = readme.orEmpty().lines().map { it.trim() }
        if (lines.isEmpty()) return emptyList()
        val sections = mutableListOf<Pair<String, String>>()
        var currentTitle: String? = null
        val currentBody = StringBuilder()
        for (line in lines) {
            if (line.startsWith("#") && !line.lowercase(Locale.ROOT).contains("license")) {
                if (currentTitle != null && currentBody.isNotBlank()) {
                    sections.add(currentTitle!! to currentBody.toString().trim())
                }
                currentTitle = line.trimStart('#', ' ').take(48)
                currentBody.clear()
            } else if (currentTitle != null && line.isNotBlank() && !line.startsWith("![") && !line.startsWith("```")) {
                if (currentBody.length < 120) {
                    if (currentBody.isNotEmpty()) currentBody.append(" ")
                    currentBody.append(line.take(80))
                }
            }
        }
        if (currentTitle != null && currentBody.isNotBlank()) {
            sections.add(currentTitle!! to currentBody.toString().trim())
        }
        return sections.take(5).map { (title, body) ->
            val content = body.chineseExcerpt() ?: sectionGuidance(title, body)
            "▸ ${localizeSectionTitle(title)}\n   $content"
        }
    }

    private fun chineseOneLiner(article: Article, description: String?, readmeLead: String?): String {
        val source = description ?: readmeLead
        if (source?.containsChinese() == true) return source
        val language = article.language.takeIf { it.isNotBlank() && it != "未标注" }
        val topicLabels = article.topics.mapNotNull(::localizeTopic).distinct().take(2)
        return buildString {
            append(projectPurpose(article, source.orEmpty()))
            language?.let { append("主要技术语言为 $it。") }
            if (topicLabels.isNotEmpty()) append("涉及${topicLabels.joinToString("、")}等方向。")
        }
    }

    private fun projectPurpose(article: Article, text: String): String {
        val signal = text.lowercase(Locale.ROOT)
        return when {
            listOf("agent", "tool calling", "function calling").any(signal::contains) ->
                "这是一个用于构建智能体、编排任务并调用外部工具的开源项目。"
            listOf("rag", "retrieval", "vector database", "embedding").any(signal::contains) ->
                "这是一个用于构建检索增强生成流程、处理知识库与向量检索的开源项目。"
            listOf("llm", "language model", "inference", "transformer").any(signal::contains) ->
                "这是一个围绕大语言模型推理、应用开发或模型能力扩展的开源项目。"
            listOf("image generation", "stable diffusion", "diffusion").any(signal::contains) ->
                "这是一个用于图像生成或视觉内容处理的开源项目。"
            listOf("video generation", "text-to-video").any(signal::contains) ->
                "这是一个用于视频生成或视频内容处理的开源项目。"
            listOf("android", "kotlin", "jetpack compose").any(signal::contains) ->
                "这是一个面向安卓端 AI 能力集成或移动应用开发的开源项目。"
            listOf("react", "frontend", "web app", "web application").any(signal::contains) ->
                "这是一个面向前端应用或 Web AI 交互开发的开源项目。"
            listOf("code generation", "coding assistant", "copilot").any(signal::contains) ->
                "这是一个用于代码生成、编程辅助或开发工作流提效的开源项目。"
            else -> when (article.track) {
                AiTrack.AGENT -> "这是一个用于构建和运行 AI 智能体的开源项目。"
                AiTrack.RAG -> "这是一个用于知识检索与检索增强生成的开源项目。"
                AiTrack.MCP -> "这是一个用于模型、工具与上下文连接的开源项目。"
                AiTrack.IMAGE -> "这是一个用于图像生成或视觉 AI 的开源项目。"
                AiTrack.VIDEO -> "这是一个用于视频生成或视频 AI 的开源项目。"
                AiTrack.CODING -> "这是一个用于辅助编程与提升开发效率的开源项目。"
                AiTrack.ANDROID -> "这是一个用于安卓端 AI 应用开发的开源项目。"
                AiTrack.WEB -> "这是一个用于前端 AI 应用开发的开源项目。"
                else -> "这是一个围绕大语言模型与 AI 应用开发的开源项目。"
            }
        }
    }

    private fun localizeSectionTitle(title: String): String {
        if (title.containsChinese()) return title
        val normalized = title.lowercase(Locale.ROOT)
        return when {
            normalized.contains("getting started") || normalized.contains("quick start") -> "快速开始"
            normalized.contains("install") -> "安装与配置"
            normalized.contains("usage") || normalized.contains("how to use") -> "使用方式"
            normalized.contains("feature") -> "核心功能"
            normalized.contains("overview") || normalized.contains("introduction") || normalized.contains("about") -> "项目概览"
            normalized.contains("requirement") || normalized.contains("prerequisite") || normalized.contains("dependenc") -> "环境与依赖"
            normalized.contains("config") || normalized.contains("setup") -> "配置说明"
            normalized.contains("example") || normalized.contains("demo") -> "示例"
            normalized.contains("api") -> "接口说明"
            normalized.contains("security") -> "安全说明"
            normalized.contains("contribut") -> "参与贡献"
            normalized.contains("faq") || normalized.contains("question") -> "常见问题"
            normalized.contains("roadmap") -> "后续计划"
            else -> "项目说明"
        }
    }

    private fun sectionGuidance(title: String, body: String): String {
        val text = "$title $body".lowercase(Locale.ROOT)
        return when {
            listOf("install", "pip install", "npm install", "gradle", "requirement").any(text::contains) ->
                "介绍项目所需的依赖、安装步骤和基础配置。"
            listOf("docker", "deploy", "deployment", "kubernetes", "serve").any(text::contains) ->
                "说明容器化部署、服务启动或线上运行方式。"
            listOf("usage", "example", "quick start", "command", "cli").any(text::contains) ->
                "展示项目启动、参数设置和典型调用流程。"
            listOf("agent", "tool calling", "function calling").any(text::contains) ->
                "说明智能体如何规划任务、调用工具并组织执行流程。"
            listOf("rag", "retrieval", "vector", "embedding").any(text::contains) ->
                "说明知识库处理、向量检索和检索增强生成的实现思路。"
            listOf("llm", "language model", "model", "inference").any(text::contains) ->
                "说明模型选择、推理方式或模型能力的使用场景。"
            listOf("benchmark", "evaluation", "metric", "accuracy").any(text::contains) ->
                "介绍项目的评测方法、性能指标或实验结果。"
            listOf("api", "endpoint", "request", "response").any(text::contains) ->
                "说明接口调用、请求参数与返回结果的组织方式。"
            listOf("security", "privacy", "license").any(text::contains) ->
                "介绍项目的安全、隐私或开源许可相关说明。"
            else -> "概述该部分涉及的功能、工作流程与适用范围。"
        }
    }

    private fun localizeTopic(topic: String): String? = when (topic.lowercase(Locale.ROOT)) {
        "agent", "ai-agent", "agents" -> "智能体"
        "llm", "large-language-model" -> "大语言模型"
        "rag", "retrieval-augmented-generation" -> "检索增强生成"
        "mcp", "model-context-protocol" -> "模型上下文协议"
        "android" -> "安卓开发"
        "kotlin" -> "Kotlin"
        "python" -> "Python"
        "react" -> "React"
        "computer-vision" -> "计算机视觉"
        "image-generation" -> "图像生成"
        "video-generation" -> "视频生成"
        else -> null
    }
    private fun String.chineseExcerpt(): String? {
        if (!containsChinese()) return null
        return replace(Regex("\\s+"), " ").trim().take(160)
    }

    private fun String.containsChinese(): Boolean = any { it in '\u4e00'..'\u9fff' }
    /** Only uses an explicit absolute README image URL; relative paths need a branch-aware resolver. */
    fun extractPreviewImage(readme: String?): String? {
        val markdown = Regex("!\\[[^]]*]\\((https?://[^)\\s]+)", RegexOption.IGNORE_CASE).find(readme.orEmpty())?.groupValues?.getOrNull(1)
        val html = Regex("<img[^>]+src=[\"'](https?://[^\"']+)", RegexOption.IGNORE_CASE).find(readme.orEmpty())?.groupValues?.getOrNull(1)
        return markdown ?: html
    }

    fun classify(name: String, description: String?, topics: List<String>, language: String?): AiTrack {
        val text = (listOf(name, description.orEmpty(), language.orEmpty()) + topics).joinToString(" ").lowercase(Locale.ROOT)
        return when {
            listOf("deepfake", "face-swap", "face swap").any(text::contains) -> AiTrack.IMAGE
            listOf("mcp", "model-context-protocol").any(text::contains) -> AiTrack.MCP
            listOf("rag", "retrieval-augmented", "vector database").any(text::contains) -> AiTrack.RAG
            listOf("agent", "autonomous").any(text::contains) -> AiTrack.AGENT
            listOf("stable-diffusion", "image-generation", "comfyui", "text-to-image").any(text::contains) -> AiTrack.IMAGE
            listOf("video-generation", "text-to-video").any(text::contains) -> AiTrack.VIDEO
            listOf("android", "kotlin").any(text::contains) -> AiTrack.ANDROID
            listOf("copilot", "code-generation", "coding-assistant").any(text::contains) -> AiTrack.CODING
            listOf("web", "frontend", "react").any(text::contains) -> AiTrack.WEB
            else -> AiTrack.LLM
        }
    }

    fun isRisky(name: String, description: String?, topics: List<String>): Boolean {
        val text = (listOf(name, description.orEmpty()) + topics).joinToString(" ").lowercase(Locale.ROOT)
        return listOf("deepfake", "deep-fake", "face-swap", "faceswap", "换脸").any(text::contains)
    }
}

/** Deterministic recommendation baseline; every score is explainable from public metadata. */
object LocalRecommendationRanker {
    fun rank(source: Article, candidates: Collection<Article>, ignoredIds: Set<Long>, limit: Int = 8): List<Article> = candidates
        .distinctBy(Article::id)
        .filterNot { it.id == source.id || it.id in ignoredIds || it.archived }
        .map { candidate -> candidate to score(source, candidate) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(compareByDescending<Pair<Article, Int>> { it.second }.thenByDescending { it.first.stars })
        .take(limit)
        .map(Pair<Article, Int>::first)

    private fun score(source: Article, candidate: Article): Int {
        val sourceTopics = source.topics.map { it.lowercase(Locale.ROOT) }.toSet()
        val candidateTopics = candidate.topics.map { it.lowercase(Locale.ROOT) }.toSet()
        val commonTopics = sourceTopics.intersect(candidateTopics).size
        return (if (source.track == candidate.track) 4 else 0) +
            (if (source.language.equals(candidate.language, ignoreCase = true) && source.language != "未标注") 2 else 0) +
            commonTopics.coerceAtMost(3)
    }
}

fun Article.toEntity() = SavedArticleEntity(
    id, title, summary, author, language, stars, url, avatarUrl, forks, openIssues, license,
    updatedAt, createdAt, topics.joinToString(","), track.name, archived, isRisky,
    savedAt, collection, learningStatus.name, aiBrief?.let(ArticleJson::toJson).orEmpty(),
)

fun SavedArticleEntity.toArticle() = Article(
    id = repositoryId, title = title, summary = summary, author = author, language = language,
    stars = stars, url = url, avatarUrl = avatarUrl,
    forks = forks, openIssues = openIssues, license = license, updatedAt = updatedAt, createdAt = createdAt,
    topics = topics.split(',').filter(String::isNotBlank),
    track = runCatching { AiTrack.valueOf(track) }.getOrDefault(AiTrack.LLM),
    archived = archived, isRisky = isRisky,
    savedAt = savedAt,
    collection = collection,
    learningStatus = runCatching { LearningStatus.valueOf(learningStatus) }.getOrDefault(LearningStatus.TO_LEARN),
    aiBrief = aiBriefJson.takeIf(String::isNotBlank)?.let { runCatching { ArticleJson.fromJson(it, AiBrief::class.java) }.getOrNull() },
)

private object ArticleJson {
    private val gson = com.google.gson.Gson()
    fun toJson(value: Any): String = gson.toJson(value)
    fun <T> fromJson(value: String, type: Class<T>): T = gson.fromJson(value, type)
}
