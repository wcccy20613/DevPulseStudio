package com.chunyan.devpulsestudio.data

/**
 * Keeps discovery-cache decisions independent from Room, Retrofit and the UI.
 * The repository owns serialization and error mapping; this policy only decides
 * whether a cached snapshot is fresh enough, usable as a fallback, or absent.
 */
internal class DiscoveryLoadPolicy(
    private val freshTtlMillis: Long = DEFAULT_FRESH_TTL_MILLIS,
) {
    fun beforeRemote(
        networkAvailable: Boolean,
        forceRefresh: Boolean,
        cachedSyncedAt: Long?,
        nowMillis: Long,
    ): DiscoveryLoadDecision {
        if (!networkAvailable) {
            return if (cachedSyncedAt == null) DiscoveryLoadDecision.SHOW_ERROR
            else DiscoveryLoadDecision.USE_STALE_FALLBACK
        }

        val cacheAgeMillis = cachedSyncedAt?.let { nowMillis - it }
        return if (!forceRefresh && cacheAgeMillis != null && cacheAgeMillis < freshTtlMillis) {
            DiscoveryLoadDecision.USE_FRESH_CACHE
        } else {
            DiscoveryLoadDecision.FETCH_REMOTE
        }
    }

    fun afterRemoteFailure(cachedSyncedAt: Long?): DiscoveryLoadDecision =
        if (cachedSyncedAt == null) DiscoveryLoadDecision.SHOW_ERROR
        else DiscoveryLoadDecision.USE_STALE_FALLBACK

    private companion object {
        const val DEFAULT_FRESH_TTL_MILLIS = 12 * 60 * 60 * 1000L
    }
}

internal enum class DiscoveryLoadDecision {
    USE_FRESH_CACHE,
    FETCH_REMOTE,
    USE_STALE_FALLBACK,
    SHOW_ERROR,
}
