package com.deepseek.lzjc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MiniMaxSnapshotDao {

    @Insert
    suspend fun insert(snapshot: MiniMaxSnapshotEntity)

    @Query("SELECT * FROM minimax_snapshots ORDER BY timestamp DESC")
    suspend fun getAll(): List<MiniMaxSnapshotEntity>

    @Query("SELECT * FROM minimax_snapshots WHERE date >= :fromDate ORDER BY timestamp ASC")
    suspend fun getSince(fromDate: String): List<MiniMaxSnapshotEntity>

    /** 每日最大已用次数（用于折线图） */
    @Query("""
        SELECT date, MAX(usedCount) as usedCount
        FROM minimax_snapshots
        WHERE date >= :fromDate AND modelName = 'general' AND window = :window
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyMaxUsed(fromDate: String, window: String): List<DailyMaxUsed>

    /** 模型用量汇总（按 modelName，30天） */
    @Query("""
        SELECT modelName,
               MAX(usedCount) as maxUsed,
               MAX(totalQuota) as totalQuota
        FROM minimax_snapshots
        WHERE date >= :fromDate AND window = :window
        GROUP BY modelName
        ORDER BY maxUsed DESC
    """)
    suspend fun getModelUsageSummary(fromDate: String, window: String): List<ModelUsageRow>
}

data class DailyMaxUsed(
    val date: String,
    val usedCount: Long
)

data class ModelUsageRow(
    val modelName: String,
    val maxUsed: Long,
    val totalQuota: Long
)
