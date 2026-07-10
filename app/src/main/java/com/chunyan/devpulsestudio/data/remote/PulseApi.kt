package com.chunyan.devpulsestudio.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface PulseApi {
    @Headers("Accept: application/vnd.github+json")
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") pageSize: Int = 20,
    ): RepositorySearchResponse
}

data class RepositorySearchResponse(val items: List<RemoteRepository>)

data class RemoteRepository(
    val id: Long,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("stargazers_count") val stars: Int,
    val language: String?,
    val owner: RemoteOwner,
)

data class RemoteOwner(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String,
)
