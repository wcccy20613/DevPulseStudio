package com.chunyan.devpulsestudio.data.remote

import com.chunyan.devpulsestudio.data.Article
import retrofit2.http.GET

/** Public, generated data from the scheduled pipeline. It contains no user-specific state. */
interface StaticCatalogApi {
    @GET("catalog.json")
    suspend fun catalog(): StaticCatalog

    @GET("daily.json")
    suspend fun daily(): StaticDaily
}

data class StaticCatalog(
    val schemaVersion: Int,
    val generatedAt: String,
    val items: List<Article>,
)

data class StaticDaily(
    val schemaVersion: Int,
    val generatedAt: String,
    val popular: List<Long> = emptyList(),
    val newProjects: List<Long> = emptyList(),
    val growing: List<Long> = emptyList(),
    val releases: List<Long> = emptyList(),
)
