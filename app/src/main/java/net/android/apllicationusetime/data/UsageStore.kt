package net.android.apllicationusetime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "usage_history")

object UsageStore {

    private val KEY_WEEKLY_HISTORY = stringPreferencesKey("weekly_history")
    private val KEY_DAILY_APP_DETAILS = stringPreferencesKey("daily_app_details")

    // ================== 每日摘要存储 ==================

    suspend fun saveTodaySummary(context: Context, summary: DaySummary) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[KEY_WEEKLY_HISTORY] ?: "[]"
            val arr = JSONArray(existingJson)
            val dateKey = getDateKey(summary.dateTimestamp)
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("date") != dateKey) newArr.put(obj)
            }
            newArr.put(JSONObject().apply {
                put("date", dateKey)
                put("timestamp", summary.dateTimestamp)
                put("totalTimeMs", summary.totalTimeMs)
                put("appCount", summary.appCount)
                put("topApp", summary.topApp?.appName ?: "")
            })
            val start = maxOf(0, newArr.length() - 14)
            val finalArr = JSONArray()
            for (i in start until newArr.length()) finalArr.put(newArr.getJSONObject(i))
            prefs[KEY_WEEKLY_HISTORY] = finalArr.toString()
        }
    }

    fun getHistoryFlow(context: Context): Flow<List<DaySummary>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[KEY_WEEKLY_HISTORY] ?: "[]"
            parseHistory(json)
        }
    }

    // ================== 每日应用详情存储（全历史排行数据源） ==================

    suspend fun saveDailyAppDetails(context: Context, dateKey: String, apps: List<AppUsage>) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[KEY_DAILY_APP_DETAILS] ?: "{}"
            val root = JSONObject(existingJson)

            val appArr = JSONArray()
            apps.forEach { app ->
                appArr.put(JSONObject().apply {
                    put("packageName", app.packageName)
                    put("appName", app.appName)
                    put("category", app.category.name)
                    put("usageTimeMs", app.usageTimeMs)
                })
            }

            root.put(dateKey, appArr)
            // 限制保留最近30天数据
            if (root.length() > 30) {
                val keys = root.keys().asSequence().toList().sorted()
                for (k in keys.take(root.length() - 30)) root.remove(k)
            }
            prefs[KEY_DAILY_APP_DETAILS] = root.toString()
        }
    }

    /**
     * 全历史排行数据类
     */
    data class AppAllTimeRank(
        val packageName: String,
        val appName: String,
        val totalAllMs: Long
    )

    /**
     * Flow 方式获取全历史排行（替代 Room getAllTimeAppRank）
     */
    fun getAllTimeRankFlow(context: Context): Flow<List<AppAllTimeRank>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[KEY_DAILY_APP_DETAILS] ?: "{}"
            val root = JSONObject(json)
            data class AggEntry(val name: String, var totalMs: Long)
            val accMap = mutableMapOf<String, AggEntry>()

            val dayKeys = root.keys().asSequence().toList()
            for (dayKey in dayKeys) {
                val appArr = root.getJSONArray(dayKey)
                for (i in 0 until appArr.length()) {
                    val obj = appArr.getJSONObject(i)
                    val pkg = obj.getString("packageName")
                    val name = obj.getString("appName")
                    val ms = obj.optLong("usageTimeMs", 0L)

                    val entry = accMap.getOrPut(pkg) { AggEntry(name, 0L) }
                    entry.totalMs += ms
                    if (name.isNotEmpty() && entry.name.isEmpty()) entry.totalMs // no-op to avoid warning
                }
            }

            accMap.map { (pkg, entry) ->
                AppAllTimeRank(pkg, entry.name, entry.totalMs)
            }.sortedByDescending { it.totalAllMs }
        }
    }

    // ================== 工具方法 ==================

    private fun getDateKey(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    fun getTodayDateKey(): String = getDateKey(System.currentTimeMillis())

    private fun parseHistory(json: String): List<DaySummary> {
        val arr = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        val list = mutableListOf<DaySummary>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(DaySummary(
                dateTimestamp = obj.optLong("timestamp"),
                totalTimeMs = obj.optLong("totalTimeMs"),
                appCount = obj.optInt("appCount"),
                topApp = null
            ))
        }
        return list
    }
}