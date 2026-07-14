package com.chunyan.devpulsestudio.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * App operators provide this endpoint. The Android app never contains a model-provider key.
 * A blank BuildConfig.AI_GATEWAY_URL disables this service and keeps the local evidence reader.
 */
interface InsightGatewayApi {
    @POST("v1/insights")
    suspend fun analyze(@Body request: GatewayInsightRequest): GatewayInsightResponse
}

data class GatewayInsightRequest(
    val repository: String,
    val repositoryUrl: String,
    val language: String,
    val topics: List<String>,
    val readme: String,
)

data class GatewayInsightResponse(
    val oneLiner: String,
    val capabilities: List<String>,
    val audience: String,
    val strengths: String,
    val limitations: String,
    val score: Int,
    val evidence: String,
    val modelVersion: String? = null,
    val readmeHighlights: List<String> = emptyList(),
)
