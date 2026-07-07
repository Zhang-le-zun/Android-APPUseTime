package net.android.apllicationusetime.model

import android.graphics.Bitmap

enum class AppCategory {
    SOCIAL,   // 社交
    VIDEO,    // 视频
    TOOL,     // 工具
    GAME,     // 游戏
    OTHER     // 其他
}

data class AppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val category: AppCategory = AppCategory.OTHER,
    val icon: Bitmap? = null
)

/**
 * 单日使用摘要（用于历史对比和趋势图）
 */
data class DaySummary(
    val dateTimestamp: Long,
    val totalTimeMs: Long,
    val appCount: Int,
    val topApp: AppUsage?
)
