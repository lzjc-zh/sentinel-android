package com.deepseek.lzjc.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MiniMax 配额快照 - 每次刷新时记录各模型的剩余/已用次数
 * 用于计算每日用量趋势（30天折线图）
 */
@Entity(tableName = "minimax_snapshots")
data class MiniMaxSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val date: String,          // yyyy-MM-dd
    val modelName: String,      // general, video, etc.
    val window: String,         // "hour" or "week"
    val usedCount: Long,        // 已用次数
    val remainingCount: Long,   // 剩余次数
    val remainingPercent: Int,  // 剩余百分比
    val status: Int,            // 1=正常, 2=已用完, 3=其他
    val totalQuota: Long        // 估算的总配额
)
