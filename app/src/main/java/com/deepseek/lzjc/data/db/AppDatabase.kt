package com.deepseek.lzjc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_records ADD COLUMN cacheHitTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE usage_records ADD COLUMN cacheMissTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE usage_records ADD COLUMN requestCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_records ADD COLUMN platform TEXT NOT NULL DEFAULT 'deepseek'")
            }
        }
    }
}
