package net.android.apllicationusetime.data

import net.android.apllicationusetime.model.AppCategory
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary

data class UsageInsight(
    val topApps: List<AppUsage>,
    val heavyUsageApp: AppUsage?,
    val socialRatio: Float,
    val gameRatio: Float,
    val videoRatio: Float,
    val totalTimeMs: Long,
    val isFragmented: Boolean
)

data class UsageSuggestion(
    val title: String,
    val description: String
)

/**
 * 趋势对比结果
 */
data class TrendComparison(
    val todayTotalMs: Long,
    val yesterdayTotalMs: Long,
    val changeMs: Long,           // 正=增多，负=减少
    val changePercent: Float,     // 变化百分比
    val isIncrease: Boolean
)

/**
 * 时段画像标签
 */
data class TimeProfile(
    val label: String,            // 如"夜猫子型"
    val description: String,
    val iconEmoji: String
)

/**
 * 周报数据
 */
data class WeeklyReport(
    val summaries: List<DaySummary>,
    val totalWeekMs: Long,
    val avgDailyMs: Long,
    val bestDay: DaySummary?,     // 使用最少的日
    val worstDay: DaySummary?,    // 使用最多的日
    val categoryDistribution: Map<AppCategory, Float>,
    val trend: List<DaySummary>   // 按日期排序的每日总时长
)

object UsageAnalyzer {

    fun analyze(usageList: List<AppUsage>): UsageInsight {
        if (usageList.isEmpty()) {
            return UsageInsight(
                topApps = emptyList(),
                heavyUsageApp = null,
                socialRatio = 0f,
                gameRatio = 0f,
                videoRatio = 0f,
                totalTimeMs = 0L,
                isFragmented = false
            )
        }

        val totalTimeMs = usageList.sumOf { it.usageTimeMs }
        val topApps = usageList.take(3)

        // 重度使用检测：单应用占比超过50%
        val heavyUsageApp = if (totalTimeMs > 0 && usageList.isNotEmpty()) {
            val first = usageList.first()
            if (first.usageTimeMs.toFloat() / totalTimeMs > 0.5f) first else null
        } else null

        // 各分类占比
        fun categoryRatio(category: AppCategory): Float {
            if (totalTimeMs == 0L) return 0f
            return usageList.filter { it.category == category }
                .sumOf { it.usageTimeMs }.toFloat() / totalTimeMs
        }

        // 碎片化分析
        val isFragmented = usageList.size >= 8 && totalTimeMs > 0
                && usageList.first().usageTimeMs.toFloat() / totalTimeMs < 0.3f

        return UsageInsight(
            topApps = topApps,
            heavyUsageApp = heavyUsageApp,
            socialRatio = categoryRatio(AppCategory.SOCIAL),
            gameRatio = categoryRatio(AppCategory.GAME),
            videoRatio = categoryRatio(AppCategory.VIDEO),
            totalTimeMs = totalTimeMs,
            isFragmented = isFragmented
        )
    }

    /**
     * 趋势对比：今天 vs 昨天
     */
    fun compareTrend(todayMs: Long, yesterdayMs: Long): TrendComparison {
        val changeMs = todayMs - yesterdayMs
        val changePercent = if (yesterdayMs > 0) {
            (changeMs.toFloat() / yesterdayMs) * 100f
        } else {
            if (todayMs > 0) 100f else 0f
        }

        return TrendComparison(
            todayTotalMs = todayMs,
            yesterdayTotalMs = yesterdayMs,
            changeMs = changeMs,
            changePercent = changePercent,
            isIncrease = changeMs > 0
        )
    }

    /**
     * 时段画像：根据 hourlyData 分析用户类型
     */
    fun analyzeTimeProfile(hourlyData: List<Long>): TimeProfile {
        if (hourlyData.size < 24) {
            return TimeProfile("数据不足", "需要更多使用数据才能分析", "📊")
        }

        val morning = hourlyData.subList(6, 12).sum()     // 6-11点
        val afternoon = hourlyData.subList(12, 18).sum()  // 12-17点
        val evening = hourlyData.subList(18, 22).sum()    // 18-21点
        val night = hourlyData.subList(22, 24).sum() + hourlyData.subList(0, 6).sum()  // 22-5点

        val total = morning + afternoon + evening + night
        if (total == 0L) return TimeProfile("新用户", "今天还没有使用记录，多使用后可以生成画像", "🌱")

        val nightRatio = night.toFloat() / total
        val morningRatio = morning.toFloat() / total

        return when {
            nightRatio > 0.35f -> TimeProfile(
                label = "夜猫子型",
                description = "深夜使用占${(nightRatio * 100).toInt()}%，建议晚上减少屏幕时间以保护睡眠",
                iconEmoji = "🦉"
            )
            morningRatio > 0.3f -> TimeProfile(
                label = "晨起型",
                description = "上午使用占比高，习惯在早晨处理事务",
                iconEmoji = "🌅"
            )
            afternoon > evening && afternoon > morning -> TimeProfile(
                label = "午后活跃型",
                description = "下午是您的使用高峰，工作间歇可能较多使用手机",
                iconEmoji = "☀️"
            )
            else -> TimeProfile(
                label = "晚间休闲型",
                description = "主要使用时间集中在晚上，注意控制熬夜使用",
                iconEmoji = "🌙"
            )
        }
    }

    /**
     * 周报分析
     */
    fun generateWeeklyReport(summaries: List<DaySummary>, todayApps: List<AppUsage>): WeeklyReport {
        val sorted = summaries.sortedBy { it.dateTimestamp }
        val weekMs = summaries.sumOf { it.totalTimeMs }
        val avgMs = if (summaries.isNotEmpty()) weekMs / summaries.size else 0L
        val best = summaries.minByOrNull { it.totalTimeMs }
        val worst = summaries.maxByOrNull { it.totalTimeMs }

        // 分类分布基于今日数据
        val catMap = mutableMapOf<AppCategory, Float>()
        val todayTotal = todayApps.sumOf { it.usageTimeMs }
        if (todayTotal > 0) {
            AppCategory.entries.forEach { cat ->
                val catMs = todayApps.filter { it.category == cat }.sumOf { it.usageTimeMs }
                catMap[cat] = catMs.toFloat() / todayTotal
            }
        }

        return WeeklyReport(
            summaries = sorted,
            totalWeekMs = weekMs,
            avgDailyMs = avgMs,
            bestDay = best,
            worstDay = worst,
            categoryDistribution = catMap,
            trend = sorted
        )
    }

    fun generateSuggestions(insight: UsageInsight): List<UsageSuggestion> {
        val suggestions = mutableListOf<UsageSuggestion>()

        if (insight.topApps.isEmpty()) return suggestions

        val topApp = insight.topApps.first()

        // 第一名App使用超2小时
        if (topApp.usageTimeMs > 2 * 60 * 60 * 1000) {
            suggestions.add(
                UsageSuggestion(
                    title = "减少${topApp.appName}使用时间",
                    description = "${topApp.appName}已使用${UsageStatsRepository.formatDuration(topApp.usageTimeMs)}，尝试减少20分钟使用时间"
                )
            )
        }

        // 社交类占比超40%
        if (insight.socialRatio > 0.4f) {
            suggestions.add(
                UsageSuggestion(
                    title = "设置晚间社交停用窗口",
                    description = "社交应用使用占比达到${(insight.socialRatio * 100).toInt()}%，建议晚上10点后减少社交应用使用"
                )
            )
        }

        // 游戏类使用超1小时
        if (insight.gameRatio > 0 && insight.totalTimeMs * insight.gameRatio > 60 * 60 * 1000) {
            suggestions.add(
                UsageSuggestion(
                    title = "设置每日游戏时间上限",
                    description = "今天游戏时间较长，考虑设置系统级游戏时间限制"
                )
            )
        }

        // 总使用时长超5小时
        if (insight.totalTimeMs > 5 * 60 * 60 * 1000) {
            suggestions.add(
                UsageSuggestion(
                    title = "适当休息眼睛",
                    description = "今天使用手机${UsageStatsRepository.formatDuration(insight.totalTimeMs)}，建议每45分钟休息5分钟"
                )
            )
        }

        // 碎片化提醒
        if (insight.isFragmented) {
            suggestions.add(
                UsageSuggestion(
                    title = "减少切换频率",
                    description = "您今天使用较为碎片化，尝试集中处理任务，减少应用间频繁切换"
                )
            )
        }

        // 兜底建议
        if (suggestions.isEmpty()) {
            suggestions.add(
                UsageSuggestion(
                    title = "保持良好习惯",
                    description = "良好的使用习惯能让您更高效地平衡工作与生活"
                )
            )
        }

        return suggestions
    }
}