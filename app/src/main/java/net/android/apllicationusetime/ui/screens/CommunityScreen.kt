package net.android.apllicationusetime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.android.apllicationusetime.data.supabase.SupabaseConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * 社区页面 —— 查看其他用户的模糊使用概况
 * 只能看到用户名 / 约X小时 / 使用天数，看不到具体App
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    currentUsername: String,
    onRefresh: () -> Unit
) {
    var userSummaries by remember { mutableStateOf<List<CommunityUserSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            userSummaries = fetchCommunitySummaries()
            isLoading = false
        } catch (e: Exception) {
            errorMsg = "加载失败: ${e.message}"
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题
        Text(
            text = "社区动态",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp)
        )
        Text(
            text = "查看其他用户的使用概况（仅显示模糊数据）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在加载社区数据...", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (errorMsg != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😵", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg ?: "", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onRefresh() }) {
                        Text("重试")
                    }
                }
            }
        } else if (userSummaries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌐", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无其他用户数据", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("当其他用户开始使用后，数据将显示在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // 统计卡片
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("社区总览", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("共 ${userSummaries.size} 位用户 · ${userSummaries.count { it.hasData }} 位有使用记录",
                                style = MaterialTheme.typography.bodyMedium)
                            if (userSummaries.any { it.approximateHours > 0 }) {
                                val top = userSummaries.maxByOrNull { it.approximateHours }
                                Text("今日最活跃: ${top?.username ?: ""} · 约 ${top?.approximateHours ?: 0} 小时",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // 注意：过滤掉当前用户自己
                val others = userSummaries.filter { it.username != currentUsername }
                if (others.isEmpty()) {
                    item {
                        Text("暂无其他用户数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp))
                    }
                } else {
                    items(others) { user ->
                        CommunityUserCard(user = user)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityUserCard(user: CommunityUserSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.username.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (user.hasData) {
                    Text(
                        text = "今日使用: 约 ${user.approximateHours} 小时",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (user.activeDays > 0) {
                        Text(
                            text = "活跃: ${user.activeDays} 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "今日暂无使用记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ================== 数据模型 ==================

data class CommunityUserSummary(
    val username: String,
    val hasData: Boolean,
    val approximateHours: Int,   // 向下取整到小时，只显示 "约 X 小时"
    val activeDays: Int           // 该用户有数据的天数
)

// ================== 数据获取 ==================

private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

/**
 * 从 daily_summaries 表查询所有用户的大致使用数据
 * 返回的数据是模糊的（小时取整、不暴露具体App）
 */
private fun fetchCommunitySummaries(): List<CommunityUserSummary> {
    // 获取今天的日期 key
    val cal = java.util.Calendar.getInstance()
    val todayKey = "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"

    // 1. 获取所有用户今天的摘要（不含具体App，只有总时长）
    val todayRequest = Request.Builder()
        .url("${SupabaseConfig.REST_URL}/${SupabaseConfig.TABLE_DAILY_SUMMARIES}?" +
                "date_key=eq.$todayKey&select=user_id,total_time_ms,app_count,top_app")
        .addHeader("apikey", SupabaseConfig.ANON_KEY)
        .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
        .get()
        .build()

    val todayData = mutableMapOf<String, Long>()
    val todayRequestCount = mutableMapOf<String, Int>() // 只用于计数，不暴露具体值
    try {
        val resp = client.newCall(todayRequest).execute()
        val body = resp.body?.string()
        resp.close()
        if (body != null) {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val userId = obj.optString("user_id", "")
                val totalMs = obj.optLong("total_time_ms", 0L)
                if (userId.isNotEmpty()) {
                    todayData[userId] = todayData.getOrDefault(userId, 0L) + totalMs
                    todayRequestCount[userId] = obj.optInt("app_count", 0) // 仅显示计数
                }
            }
        }
    } catch (_: Exception) { }

    // 2. 获取所有出现过的 user_id（最近30天内有数据的）
    val allUsers = mutableMapOf<String, Int>() // user_id -> active days count

    val daysRequest = Request.Builder()
        .url("${SupabaseConfig.REST_URL}/${SupabaseConfig.TABLE_DAILY_SUMMARIES}?" +
                "select=user_id,date_key&order=date_key.desc")
        .addHeader("apikey", SupabaseConfig.ANON_KEY)
        .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
        .get()
        .build()

    try {
        val resp = client.newCall(daysRequest).execute()
        val body = resp.body?.string()
        resp.close()
        if (body != null) {
            val arr = JSONArray(body)
            val seen = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val userId = obj.optString("user_id", "")
                val dateKey = obj.optString("date_key", "")
                if (userId.isNotEmpty() && dateKey.isNotEmpty()) {
                    val key = "$userId|$dateKey"
                    if (seen.add(key)) {
                        allUsers[userId] = allUsers.getOrDefault(userId, 0) + 1
                    }
                }
            }
        }
    } catch (_: Exception) { }

    // 3. 构造结果（用户名用 user_id 前缀，保持隐私）
    val result = allUsers.map { (userId, activeDays) ->
        val todayMs = todayData[userId] ?: 0L
        val approxHours = (todayMs / 3_600_000).toInt() // 取整到小时
        // 用 user_id 的前6位作为"用户名"显示（保护隐私）
        val displayName = userId.take(6)
        CommunityUserSummary(
            username = displayName,
            hasData = todayMs > 0,
            approximateHours = approxHours,
            activeDays = activeDays
        )
    }.sortedByDescending { it.approximateHours }

    return result
}