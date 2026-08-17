package com.chunyan.devpulsestudio.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoveryCacheDaoInstrumentedTest {

    private lateinit var database: PulseDatabase
    private lateinit var dao: DiscoveryCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PulseDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.discoveryCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cacheRoundTrip_replacesSnapshotAndExpiresOnlyOlderEntries() = runBlocking {
        dao.save(
            DiscoveryCacheEntity(
                cacheKey = "llm:weekly:1",
                payload = "old payload",
                total = 1,
                syncedAt = 100,
            ),
        )
        dao.save(
            DiscoveryCacheEntity(
                cacheKey = "llm:weekly:1",
                payload = "latest payload",
                total = 2,
                syncedAt = 200,
            ),
        )
        dao.save(
            DiscoveryCacheEntity(
                cacheKey = "rag:daily:1",
                payload = "stale payload",
                total = 1,
                syncedAt = 99,
            ),
        )

        dao.deleteOlderThan(100)

        assertEquals(
            DiscoveryCacheEntity(
                cacheKey = "llm:weekly:1",
                payload = "latest payload",
                total = 2,
                syncedAt = 200,
            ),
            dao.find("llm:weekly:1"),
        )
        assertNull(dao.find("rag:daily:1"))
    }
}
