package com.deepseek.lzjc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageEntity::class, MiniMaxSnapshotEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun miniMaxSnapshotDao(): MiniMaxSnapshotDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 第一次添加 MiniMax 快照表（旧版有 bug）
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 重建 MiniMax 快照表
                db.execSQL("DROP TABLE IF EXISTS minimax_snapshots")
                db.execSQL("""
                    CREATE TABLE minimax_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        modelName TEXT NOT NULL,
                        window TEXT NOT NULL,
                        usedCount INTEGER NOT NULL,
                        remainingCount INTEGER NOT NULL,
                        remainingPercent INTEGER NOT NULL,
                        status INTEGER NOT NULL,
                        totalQuota INTEGER NOT NULL
                    )
                """.trimIndent())
                // 不创建索引，避免 Room 校验时索引顺序问题
            }
        }
    }
}
