package net.android.apllicationusetime.data.supabase

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.android.apllicationusetime.model.AppUsage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云端同步管理器 —— 将应用使用数据同步到 Supabase
 *
 * 使用设备 Android ID 作为用户标识，无需登录。
 * 需要网络权限和 INTERNET 权限。
 */
object SyncManager {

    private const val TAG = "SyncManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    // ================== 公共入口 ==================

    /**
     * 同步今天的数据到 Supabase（由 UsageStatsRepository 调用）
     */
    suspend fun syncTodayData(
        context: Context,
        dateKey: String,
        totalTimeMs: Long,
        appCount: Int,
        topApp: String?,
        apps: List<AppUsage>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId(context)
                Log.d(TAG, "Syncing date=$dateKey device=$deviceId ${apps.size} apps")

                // 上传每日摘要
                upsertDailySummary(deviceId, dateKey, System.currentTimeMillis(), totalTimeMs, appCount, topApp)

                // 上传应用详情
                if (apps.isNotEmpty()) {
                    uploadAppDetails(deviceId, dateKey, apps)
                }

                Log.d(TAG, "Sync completed for $dateKey")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for $dateKey: ${e.message}", e)
            }
        }
    }

    // ================== 每日摘要上传 ==================

    private fun upsertDailySummary(
        deviceId: String,
        dateKey: String,
        dateTimestamp: Long,
        totalTimeMs: Long,
        appCount: Int,
        topApp: String?
    ) {
        val body = JSONObject().apply {
            put("user_id", deviceId)
            put("date_key", dateKey)
            put("date_timestamp", dateTimestamp)
            put("total_time_ms", totalTimeMs)
            put("app_count", appCount)
            put("top_app", topApp ?: "")
        }

        val request = Request.Builder()
            .url("${SupabaseConfig.REST_URL}/${SupabaseConfig.TABLE_DAILY_SUMMARIES}")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeRequest(request, "upsert summary")
    }

    // ================== 应用详情上传 ==================

    private fun uploadAppDetails(deviceId: String, dateKey: String, apps: List<AppUsage>) {
        // 先删旧数据，再批量插入
        deleteExistingAppDetails(deviceId, dateKey)

        val rows = JSONArray()
        apps.forEach { app ->
            rows.put(JSONObject().apply {
                put("user_id", deviceId)
                put("date_key", dateKey)
                put("package_name", app.packageName)
                put("app_name", app.appName)
                put("category", app.category.name)
                put("usage_time_ms", app.usageTimeMs)
            })
        }

        if (rows.length() == 0) return

        val request = Request.Builder()
            .url("${SupabaseConfig.REST_URL}/${SupabaseConfig.TABLE_APP_USAGE_DETAILS}")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(rows.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeRequest(request, "upload app details (${apps.size})")
    }

    private fun deleteExistingAppDetails(deviceId: String, dateKey: String) {
        val request = Request.Builder()
            .url("${SupabaseConfig.REST_URL}/${SupabaseConfig.TABLE_APP_USAGE_DETAILS}?" +
                    "user_id=eq.$deviceId&date_key=eq.$dateKey")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .delete()
            .build()

        try {
            val response = client.newCall(request).execute()
            response.close()
        } catch (_: Exception) { }
    }

    // ================== 云端查询 ==================

    /**
     * 从云端查询某天的摘要
     */
    fun fetchSummaryFromCloud(deviceId: String, dateKey: String): JSONObject? {
        val request = Request.Builder()
            .url("${SupabaseConfig.REST_URL}/${SupabaseConfig.TABLE_DAILY_SUMMARIES}?" +
                    "user_id=eq.$deviceId&date_key=eq.$dateKey&select=*")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (body != null) {
                val arr = JSONArray(body)
                if (arr.length() > 0) arr.getJSONObject(0) else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Fetch summary failed: ${e.message}")
            null
        }
    }

    /**
     * 从云端查询某天的应用详情列表
     */
    fun fetchAppDetailsFromCloud(deviceId: String, dateKey: String): List<JSONObject> {
        val request = Request.Builder()
            .url("${SupabaseConfig.REST_URL}/${SupabaseConfig.TABLE_APP_USAGE_DETAILS}?" +
                    "user_id=eq.$deviceId&date_key=eq.$dateKey&select=*&order=usage_time_ms.desc")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (body != null) {
                val arr = JSONArray(body)
                (0 until arr.length()).map { arr.getJSONObject(it) }
            } else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Fetch app details failed: ${e.message}")
            emptyList()
        }
    }

    // ================== 内部工具 ==================

    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
    }

    private fun executeRequest(request: Request, label: String) {
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "$label failed: ${response.code} ${response.body?.string()}")
            } else {
                Log.d(TAG, "$label success: ${response.code}")
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "$label error: ${e.message}", e)
        }
    }
}