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
import net.android.apllicationusetime.model.DaySummary
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "usage_history")

object UsageStore {

    private val KEY_WEEKLY_HISTORY = stringPreferencesKey("weekly_history")

    /**
     * 保存今日摘要到本地历史（保留最近14天）
     */
    suspend fun saveTodaySummary(context: Context, summary: DaySummary) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[KEY_WEEKLY_HISTORY] ?: "[]"
            val arr = JSONArray(existingJson)
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(summary.dateTimestamp))

            // 移除已存在的同日记录
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("date") != dateKey) {
                    newArr.put(obj)
                }
            }
            newArr.put(JSONObject().apply {
                put("date", dateKey)
                put("timestamp", summary.dateTimestamp)
                put("totalTimeMs", summary.totalTimeMs)
                put("appCount", summary.appCount)
                put("topApp", summary.topApp?.appName ?: "")
            })
            // 只保留最近14天
            val finalArr = JSONArray()
            val start = maxOf(0, newArr.length() - 14)
            for (i in start until newArr.length()) {
                finalArr.put(newArr.getJSONObject(i))
            }
            prefs[KEY_WEEKLY_HISTORY] = finalArr.toString()
        }
    }

    /**
     * 读取历史摘要 Flow
     */
    fun getHistoryFlow(context: Context): Flow<List<DaySummary>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[KEY_WEEKLY_HISTORY] ?: "[]"
            parseHistory(json)
        }
    }

    private fun parseHistory(json: String): List<DaySummary> {
        val arr = try {
            JSONArray(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val list = mutableListOf<DaySummary>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                DaySummary(
                    dateTimestamp = obj.optLong("timestamp"),
                    totalTimeMs = obj.optLong("totalTimeMs"),
                    appCount = obj.optInt("appCount"),
                    topApp = null
                )
            )
        }
        return list
    }
}