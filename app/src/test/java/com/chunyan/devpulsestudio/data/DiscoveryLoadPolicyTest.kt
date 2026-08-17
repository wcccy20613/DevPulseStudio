package com.chunyan.devpulsestudio.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryLoadPolicyTest {
    private val ttlMillis = 12 * 60 * 60 * 1000L
    private val nowMillis = 1_000_000_000L
    private val policy = DiscoveryLoadPolicy(ttlMillis)

    @Test
    fun beforeRemote_onlineWithoutCache_fetchesRemote() {
        assertEquals(
            DiscoveryLoadDecision.FETCH_REMOTE,
            policy.beforeRemote(networkAvailable = true, forceRefresh = false, cachedSyncedAt = null, nowMillis = nowMillis),
        )
    }

    @Test
    fun beforeRemote_onlineFreshCache_usesCache() {
        assertEquals(
            DiscoveryLoadDecision.USE_FRESH_CACHE,
            policy.beforeRemote(networkAvailable = true, forceRefresh = false, cachedSyncedAt = nowMillis - ttlMillis + 1, nowMillis = nowMillis),
        )
    }

    @Test
    fun beforeRemote_cacheAtTtl_fetchesRemote() {
        assertEquals(
            DiscoveryLoadDecision.FETCH_REMOTE,
            policy.beforeRemote(networkAvailable = true, forceRefresh = false, cachedSyncedAt = nowMillis - ttlMillis, nowMillis = nowMillis),
        )
    }

    @Test
    fun beforeRemote_forceRefreshBypassesFreshCache() {
        assertEquals(
            DiscoveryLoadDecision.FETCH_REMOTE,
            policy.beforeRemote(networkAvailable = true, forceRefresh = true, cachedSyncedAt = nowMillis - 1, nowMillis = nowMillis),
        )
    }

    @Test
    fun beforeRemote_staleCache_fetchesRemote() {
        assertEquals(
            DiscoveryLoadDecision.FETCH_REMOTE,
            policy.beforeRemote(networkAvailable = true, forceRefresh = false, cachedSyncedAt = nowMillis - ttlMillis - 1, nowMillis = nowMillis),
        )
    }

    @Test
    fun beforeRemote_offlineWithAnyCache_usesStaleFallback() {
        assertEquals(
            DiscoveryLoadDecision.USE_STALE_FALLBACK,
            policy.beforeRemote(networkAvailable = false, forceRefresh = false, cachedSyncedAt = nowMillis - 30L * 24 * 60 * 60 * 1000, nowMillis = nowMillis),
        )
    }

    @Test
    fun beforeRemote_offlineWithoutCache_showsError() {
        assertEquals(
            DiscoveryLoadDecision.SHOW_ERROR,
            policy.beforeRemote(networkAvailable = false, forceRefresh = false, cachedSyncedAt = null, nowMillis = nowMillis),
        )
    }

    @Test
    fun afterRemoteFailure_withCache_usesStaleFallback() {
        assertEquals(DiscoveryLoadDecision.USE_STALE_FALLBACK, policy.afterRemoteFailure(nowMillis - ttlMillis))
    }

    @Test
    fun afterRemoteFailure_withoutCache_showsError() {
        assertEquals(DiscoveryLoadDecision.SHOW_ERROR, policy.afterRemoteFailure(null))
    }
}
