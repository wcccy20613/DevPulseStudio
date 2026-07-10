package com.chunyan.devpulsestudio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SavedArticleEntity::class], version = 1, exportSchema = false)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun savedArticleDao(): SavedArticleDao
}
