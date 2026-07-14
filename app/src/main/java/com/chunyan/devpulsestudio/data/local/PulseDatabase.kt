package com.chunyan.devpulsestudio.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SavedArticleEntity::class, DiscoveryCacheEntity::class, ReadmeAnalysisEntity::class, IgnoredRecommendationEntity::class, SearchHistoryEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun savedArticleDao(): SavedArticleDao
    abstract fun discoveryCacheDao(): DiscoveryCacheDao
    abstract fun readmeAnalysisDao(): ReadmeAnalysisDao
    abstract fun ignoredRecommendationDao(): IgnoredRecommendationDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        /** Migrates the original eight-field bookmark table without deleting a user's library. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN forks INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN openIssues INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN license TEXT NOT NULL DEFAULT '未声明'")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN createdAt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN topics TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN track TEXT NOT NULL DEFAULT 'LLM'")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN isRisky INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN savedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE saved_articles SET savedAt = (strftime('%s','now') * 1000) WHERE savedAt = 0")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN collection TEXT NOT NULL DEFAULT '未分类'")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN learningStatus TEXT NOT NULL DEFAULT 'TO_LEARN'")
                db.execSQL("CREATE TABLE IF NOT EXISTS discovery_cache (cacheKey TEXT NOT NULL, payload TEXT NOT NULL, total INTEGER NOT NULL, syncedAt INTEGER NOT NULL, PRIMARY KEY(cacheKey))")
                db.execSQL("CREATE TABLE IF NOT EXISTS readme_analyses (repositoryId INTEGER NOT NULL, payload TEXT NOT NULL, sourceUpdatedAt TEXT NOT NULL, cachedAt INTEGER NOT NULL, PRIMARY KEY(repositoryId))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ignored_recommendations (repositoryId INTEGER NOT NULL, ignoredAt INTEGER NOT NULL, PRIMARY KEY(repositoryId))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS search_history (query TEXT NOT NULL, usedAt INTEGER NOT NULL, PRIMARY KEY(query))")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN aiBriefJson TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
