package com.chunyan.devpulsestudio.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface PulseApi {
    @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") pageSize: Int = 20,
        @Query("page") page: Int = 1,
    ): RepositorySearchResponse

    @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repository: String,
    ): RemoteReadme
}

data class RepositorySearchResponse(@SerializedName("total_count") val totalCount: Int, val items: List<RemoteRepository>)

data class RemoteRepository(
    val id: Long,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("stargazers_count") val stars: Int,
    @SerializedName("forks_count") val forks: Int,
    @SerializedName("open_issues_count") val openIssues: Int,
    val language: String?,
    val owner: RemoteOwner,
    val license: RemoteLicense?,
    val topics: List<String> = emptyList(),
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("created_at") val createdAt: String,
    val archived: Boolean = false,
)

data class RemoteOwner(val login: String, @SerializedName("avatar_url") val avatarUrl: String)
data class RemoteLicense(@SerializedName("spdx_id") val spdxId: String?)
data class RemoteReadme(val content: String?, val encoding: String?, @SerializedName("html_url") val htmlUrl: String?)
