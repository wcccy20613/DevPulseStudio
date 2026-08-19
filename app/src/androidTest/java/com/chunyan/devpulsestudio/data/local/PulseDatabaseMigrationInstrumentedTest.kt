package com.chunyan.devpulsestudio.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PulseDatabaseMigrationInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: PulseDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrateVersion1To6_preservesBookmarkAndAddsEverySchemaStage() = runBlocking {
        createVersion1Database()

        database = Room.databaseBuilder(context, PulseDatabase::class.java, TEST_DATABASE)
            .addMigrations(
                PulseDatabase.MIGRATION_1_2,
                PulseDatabase.MIGRATION_2_3,
                PulseDatabase.MIGRATION_3_4,
                PulseDatabase.MIGRATION_4_5,
                PulseDatabase.MIGRATION_5_6,
            )
            .build()

        val migrated = database.savedArticleDao().getAll().single()
        assertEquals(42L, migrated.repositoryId)
        assertEquals("Room migration sample", migrated.title)
        assertEquals(123, migrated.stars)
        assertEquals(0, migrated.forks)
        assertEquals(0, migrated.openIssues)
        assertEquals("未声明", migrated.license)
        assertEquals("LLM", migrated.track)
        assertFalse(migrated.archived)
        assertFalse(migrated.isRisky)
        assertTrue(migrated.savedAt > 0L)
        assertEquals("未分类", migrated.collection)
        assertEquals("TO_LEARN", migrated.learningStatus)
        assertEquals("", migrated.aiBriefJson)

        val tableNames = mutableSetOf<String>()
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).use { cursor ->
            while (cursor.moveToNext()) tableNames += cursor.getString(0)
        }
        assertTrue(tableNames.containsAll(EXPECTED_TABLES))
    }

    private fun createVersion1Database() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS saved_articles (
                                repositoryId INTEGER NOT NULL,
                                title TEXT NOT NULL,
                                summary TEXT NOT NULL,
                                author TEXT NOT NULL,
                                language TEXT NOT NULL,
                                stars INTEGER NOT NULL,
                                url TEXT NOT NULL,
                                avatarUrl TEXT NOT NULL,
                                PRIMARY KEY(repositoryId)
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.execSQL(
                """
                INSERT INTO saved_articles (
                    repositoryId, title, summary, author, language, stars, url, avatarUrl
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    42L,
                    "Room migration sample",
                    "legacy bookmark",
                    "android",
                    "Kotlin",
                    123,
                    "https://example.com/repository",
                    "https://example.com/avatar.png",
                ),
            )
        }
    }

    private companion object {
        const val TEST_DATABASE = "pulse-migration-test.db"
        val EXPECTED_TABLES = setOf(
            "saved_articles",
            "discovery_cache",
            "readme_analyses",
            "ignored_recommendations",
            "search_history",
        )
    }
}
