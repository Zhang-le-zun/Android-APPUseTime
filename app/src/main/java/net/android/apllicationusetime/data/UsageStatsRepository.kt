package net.android.apllicationusetime.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary
import java.util.Calendar
import java.util.concurrent.TimeUnit

object UsageStatsRepository {

    private const val TAG = "UsageStatsRepo"

    /**
     * 获取今日使用统计，结果已包含分类
     */
    fun getTodayUsageStats(context: Context): List<AppUsage> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        return queryAndBuild(context, startTime, endTime)
    }

    /**
     * 获取近 N 天的使用统计（按天聚合）
     */
    fun getDailySummaryForDays(context: Context, days: Int): List<DaySummary> {
        val calendar = Calendar.getInstance()
        val summaries = mutableListOf<DaySummary>()

        for (i in 0 until days) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val dayStart = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val dayEnd = calendar.timeInMillis

            val apps = queryAndBuild(context, dayStart, dayEnd)
            summaries.add(
                DaySummary(
                    dateTimestamp = dayStart,
                    totalTimeMs = apps.sumOf { it.usageTimeMs },
                    appCount = apps.size,
                    topApp = apps.firstOrNull()
                )
            )
        }
        return summaries
    }

    /**
     * 获取按小时分布的使用时长（0-23 点）
     */
    fun getHourlyDistribution(context: Context, daysAgo: Int = 0): List<Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return List(24) { 0L }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val dayEnd = calendar.timeInMillis

        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd
        )
        val selfPackageName = context.packageName

        val hourlyMs = LongArray(24)
        for (stats in statsList) {
            if (stats.packageName == selfPackageName) continue
            if (stats.totalTimeInForeground <= 0) continue
            val hour = Calendar.getInstance().apply { timeInMillis = stats.lastTimeUsed }
                .get(Calendar.HOUR_OF_DAY)
            hourlyMs[hour] = hourlyMs[hour] + stats.totalTimeInForeground
        }
        return hourlyMs.toList()
    }

    /**
     * 获取指定时间段最近一次的使用数据（用于趋势对比）
     */
    fun getUsageForDay(context: Context, daysAgo: Int): List<AppUsage> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endTime = calendar.timeInMillis

        return queryAndBuild(context, startTime, endTime)
    }

    // ---- 内部方法 ----

    private fun queryAndBuild(context: Context, startTime: Long, endTime: Long): List<AppUsage> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val selfPackageName = context.packageName
        val pm = context.packageManager

        // 方案一：queryUsageStats INTERVAL_DAILY
        try {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
            val fromStats = buildFromStatsList(statsList, pm, selfPackageName)
            if (fromStats.isNotEmpty()) {
                Log.d(TAG, "INTERVAL_DAILY returned ${fromStats.size} apps")
                return fromStats
            }
        } catch (e: Exception) {
            Log.e(TAG, "INTERVAL_DAILY failed: ${e.message}")
        }

        // 方案二：queryUsageStats INTERVAL_BEST
        try {
            val statsList2 = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            )
            val fromStats2 = buildFromStatsList(statsList2, pm, selfPackageName)
            if (fromStats2.isNotEmpty()) {
                Log.d(TAG, "INTERVAL_BEST returned ${fromStats2.size} apps")
                return fromStats2
            }
        } catch (e: Exception) {
            Log.e(TAG, "INTERVAL_BEST failed: ${e.message}")
        }

        // 方案三：通过 queryEvents 重建使用时长
        // 这次用更健壮的方式——按 app 分组后计算每个 RESUME→PAUSE 间隔
        try {
            Log.d(TAG, "queryEvents fallback from $startTime to $endTime")
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)

            // 按 packageName 存储 RESUME/PAUSE 事件时间戳栈
            val appEvents = mutableMapOf<String, MutableList<Pair<Long, Boolean>>>() // timeStamp, isResume

            while (usageEvents.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                usageEvents.getNextEvent(event)
                if (event.packageName == selfPackageName) continue

                val type = event.eventType
                // 记录所有可能的 resume/pause 类型
                val isResume = (type == 1 || type == 6)   // MOVE_TO_FOREGROUND, ACTIVITY_RESUMED
                val isPause = (type == 2 || type == 7 || type == 8) // MOVE_TO_BACKGROUND, ACTIVITY_PAUSED, ACTIVITY_STOPPED

                if (isResume || isPause) {
                    appEvents.getOrPut(event.packageName) { mutableListOf() }
                        .add(Pair(event.timeStamp, isResume))
                }
            }

            Log.d(TAG, "queryEvents: collected ${appEvents.size} packages")

            // 计算每个应用的累计前台时长
            val totalTimeMap = mutableMapOf<String, Long>()
            for ((pkg, events) in appEvents) {
                // 按时间戳排序
                val sorted = events.sortedBy { it.first }
                var resumeTime: Long? = null
                var totalMs = 0L
                var pairs = 0

                for ((ts, isResume) in sorted) {
                    if (isResume) {
                        resumeTime = ts
                    } else if (resumeTime != null) {
                        val duration = ts - resumeTime
                        if (duration in 1L..86400000L) {
                            totalMs += duration
                            pairs++
                        }
                        resumeTime = null
                    }
                }
                if (totalMs > 0) {
                    totalTimeMap[pkg] = totalMs
                    Log.d(TAG, "  $pkg: ${totalMs / 60000} min ($pairs sessions)")
                }
            }

            Log.d(TAG, "queryEvents result: ${totalTimeMap.size} apps with >0 time")

            if (totalTimeMap.isEmpty()) {
                // 调试：dump 一些原始事件类型
                val usageEvents2 = usageStatsManager.queryEvents(startTime, endTime)
                val typeCounts = mutableMapOf<Int, Int>()
                var sampleCount = 0
                while (usageEvents2.hasNextEvent() && sampleCount < 50) {
                    val ev = android.app.usage.UsageEvents.Event()
                    usageEvents2.getNextEvent(ev)
                    if (ev.packageName == selfPackageName) continue
                    typeCounts[ev.eventType] = typeCounts.getOrDefault(ev.eventType, 0) + 1
                    sampleCount++
                }
                Log.w(TAG, "Sample event types on this device: $typeCounts")
                Log.w(TAG, "Both queryUsageStats and queryEvents returned no valid time data on this device")
                Log.w(TAG, "vivo fix: user MUST enable 健康使用设备 in Settings, then grant usage access permission")
            }

            return totalTimeMap
                .filter { it.value > 0 }
                .toList()
                .sortedByDescending { it.second }
                .mapNotNull { (pkgName, totalTime) ->
                    buildAppUsage(pkgName, totalTime, pm)
                }
        } catch (e: Exception) {
            Log.e(TAG, "queryEvents failed: ${e.message}")
            return emptyList()
        }
    }

    private fun buildFromStatsList(
        statsList: List<android.app.usage.UsageStats>,
        pm: PackageManager,
        selfPackageName: String
    ): List<AppUsage> {
        return statsList
            .filter { it.totalTimeInForeground > 0 }
            .filter { it.packageName != selfPackageName }
            .sortedByDescending { it.totalTimeInForeground }
            .mapNotNull { stats ->
                buildAppUsage(stats.packageName, stats.totalTimeInForeground, pm)
            }
    }

    private fun buildAppUsage(pkgName: String, totalTimeMs: Long, pm: PackageManager): AppUsage? {
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkgName
        }

        val isSystemApp = try {
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            true
        }

        if (isSystemApp) return null

        val icon = try {
            val drawable = pm.getApplicationIcon(pkgName)
            drawable.toBitmap()
        } catch (e: Exception) {
            null
        }

        return AppUsage(
            packageName = pkgName,
            appName = appName,
            usageTimeMs = totalTimeMs,
            category = AppClassifier.classify(pkgName, appName),
            icon = icon
        )
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0分钟"
        val totalMinutes = ms / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${minutes}分钟"
        }
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }
        val width = if (intrinsicWidth > 0) intrinsicWidth else 96
        val height = if (intrinsicHeight > 0) intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}