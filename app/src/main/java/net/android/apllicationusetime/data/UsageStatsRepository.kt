package net.android.apllicationusetime.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.android.apllicationusetime.data.supabase.SyncManager
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary
import java.util.Calendar
import kotlin.math.max

object UsageStatsRepository {

    private const val TAG = "UsageStatsRepo"

    fun getTodayUsageStats(context: Context): List<AppUsage> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        val result = queryAndBuild(context, start, end)
        // 异步持久化到 DataStore
        persistToStore(context, UsageStore.getTodayDateKey(), result)
        return result
    }

    fun getUsageForDay(context: Context, daysAgo: Int): List<AppUsage> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return queryAndBuild(context, start, cal.timeInMillis)
    }

    fun getHourlyDistribution(context: Context, daysAgo: Int = 0): List<Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return List(24) { 0L }
        val aggregated = usm.queryAndAggregateUsageStats(start, end)
        val self = context.packageName
        val h = LongArray(24)
        for ((pkg, stats) in aggregated) {
            if (pkg == self || stats.totalTimeInForeground <= 0) continue
            val hour = Calendar.getInstance().apply { timeInMillis = stats.lastTimeUsed }
                .get(Calendar.HOUR_OF_DAY)
            h[hour] += stats.totalTimeInForeground
        }
        return h.toList()
    }

    fun getDailySummaryForDays(context: Context, days: Int): List<DaySummary> {
        val cal = Calendar.getInstance()
        val list = mutableListOf<DaySummary>()
        for (i in 0 until days) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            val apps = queryAndBuild(context, start, cal.timeInMillis)
            list.add(DaySummary(start, apps.sumOf { it.usageTimeMs }, apps.size, apps.firstOrNull()))
        }
        return list
    }

    // ================== 核心查询 ==================

    /**
     * 获取今天到现在的最大可能前台时长（用于截断全天数据）
     */
    private fun getTodayRangeMs(): Long {
        val now = Calendar.getInstance()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return now.timeInMillis - startOfDay.timeInMillis
    }

    private fun queryAndBuild(context: Context, startTime: Long, endTime: Long): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val self = context.packageName
        val pm = context.packageManager

        // 计算时间范围上限，防止系统返回跨日累计数据
        val rangeLimit = endTime - startTime  // 如0:50时约为 3,000,000ms ≈ 50分钟

        // 方案一：queryEvents —— 最精确，按事件时间戳累加
        try {
            val events = usm.queryEvents(startTime, endTime)
            val fg = mutableMapOf<String, Long>()
            val total = mutableMapOf<String, Long>()
            while (events.hasNextEvent()) {
                val e = android.app.usage.UsageEvents.Event()
                events.getNextEvent(e)
                if (e.packageName == self) continue
                when (e.eventType) {
                    1, 6 -> fg[e.packageName] = e.timeStamp  // MOVE_TO_FOREGROUND / ACTIVITY_STARTED
                    2, 7, 8 -> {  // MOVE_TO_BACKGROUND / ACTIVITY_STOPPED / ACTIVITY_DESTROYED
                        val t = fg.remove(e.packageName) ?: continue
                        val d = e.timeStamp - t
                        if (d in 1L..86400000L) {
                            total[e.packageName] = total.getOrDefault(e.packageName, 0L) + d
                        }
                    }
                }
            }
            val result = total.filter { it.value > 0 }
                .map { (pkg, time) -> buildAppUsage(pkg, time, pm) }
                .filterNotNull()
                .sortedByDescending { it.usageTimeMs }
            if (result.isNotEmpty()) {
                Log.d(TAG, "queryEvents returned ${result.size} apps (precise)")
                return result
            }
        } catch (_: Exception) { }

        // 方案二：queryAndAggregateUsageStats (API 28+) —— 按包名聚合，但需截断
        try {
            val aggregated = usm.queryAndAggregateUsageStats(startTime, endTime)
            val result = aggregated
                .filter { (pkg, stats) -> pkg != self && stats.totalTimeInForeground > 0 }
                .map { (pkg, stats) ->
                    // 截断到时间范围上限，防止跨日累计
                    val clipped = stats.totalTimeInForeground.coerceAtMost(rangeLimit)
                    buildAppUsage(pkg, clipped, pm)
                }
                .filterNotNull()
                .sortedByDescending { it.usageTimeMs }
            if (result.isNotEmpty()) {
                Log.d(TAG, "queryAndAggregate returned ${result.size} apps (clipped to $rangeLimit)")
                return result
            }
        } catch (_: Exception) { }

        // 方案三：queryUsageStats + 手动聚合 + 截断
        return try {
            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            val merged = statsList
                .filter { it.packageName != self && it.totalTimeInForeground > 0 }
                .groupBy { it.packageName }
                .map { (pkg, list) ->
                    val total = list.sumOf { it.totalTimeInForeground }
                    val clipped = total.coerceAtMost(rangeLimit)
                    buildAppUsage(pkg, clipped, pm)
                }
                .filterNotNull()
                .sortedByDescending { it.usageTimeMs }
            if (merged.isNotEmpty()) {
                Log.d(TAG, "Manual merged ${merged.size} apps (clipped to $rangeLimit)")
                merged
            } else {
                emptyList()
            }
        } catch (e: Exception) { Log.e(TAG, "queryUsageStats failed: ${e.message}"); emptyList() }
    }

    private fun buildAppUsage(pkg: String, ms: Long, pm: PackageManager): AppUsage? {
        val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            catch (_: Exception) { pkg }
        val isSystem = try {
            (pm.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) { true }
        if (isSystem) return null
        val icon = try { pm.getApplicationIcon(pkg).toBitmap() } catch (_: Exception) { null }
        return AppUsage(pkg, name, ms, AppClassifier.classify(pkg, name), icon)
    }

    // ================== DataStore 持久化 ==================

    private fun persistToStore(context: Context, dateKey: String, apps: List<AppUsage>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UsageStore.saveDailyAppDetails(context, dateKey, apps)
                Log.d(TAG, "Persisted ${apps.size} apps to DataStore")
                // 异步同步到 Supabase
                val totalMs = apps.sumOf { it.usageTimeMs }
                SyncManager.syncTodayData(context, dateKey, totalMs, apps.size, apps.firstOrNull()?.appName, apps)
            } catch (e: Exception) {
                Log.w(TAG, "DataStore persist failed: ${e.message}")
            }
        }
    }

    // ================== 便捷查询 ==================

    fun getAllTimeRankFlow(context: Context) = UsageStore.getAllTimeRankFlow(context)

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0分钟"
        val totalMinutes = ms / 1000 / 60
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when { h > 0 && m > 0 -> "${h}小时${m}分钟"; h > 0 -> "${h}小时"; else -> "${m}分钟" }
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = if (intrinsicWidth > 0) intrinsicWidth else 96
        val h = if (intrinsicHeight > 0) intrinsicHeight else 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); setBounds(0, 0, c.width, c.height); draw(c); return bmp
    }
}