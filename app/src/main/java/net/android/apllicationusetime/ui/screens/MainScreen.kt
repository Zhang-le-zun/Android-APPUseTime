package net.android.apllicationusetime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.android.apllicationusetime.data.UsageAnalyzer
import net.android.apllicationusetime.data.UsageStatsRepository
import net.android.apllicationusetime.data.UsageStore.AppAllTimeRank
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary
import net.android.apllicationusetime.ui.components.AppRankingList
import net.android.apllicationusetime.ui.components.EmptyStateView
import net.android.apllicationusetime.ui.components.HourlyChart
import net.android.apllicationusetime.ui.components.InsightSection
import net.android.apllicationusetime.ui.components.SummaryCard
import net.android.apllicationusetime.ui.components.TrendCard
import net.android.apllicationusetime.ui.components.WeeklyReportSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    usageData: List<AppUsage>,
    yesterdayData: List<AppUsage>,
    hourlyDistribution: List<Long>,
    weeklySummaries: List<DaySummary>,
    allTimeRank: List<AppAllTimeRank>,
    isLoading: Boolean,
    padding: PaddingValues,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    username: String = "",
    onLogout: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 确认注销弹窗
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("注销登录") },
            text = { Text("确定要注销登录吗？注销后需要重新登录才能查看数据。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("确认注销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // ---- 顶部栏：用户信息 + 注销 ----
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = username.ifEmpty { "用户" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showLogoutDialog = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "注销",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- 内容区域 ----
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(120.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加载使用数据...", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        // 如果今日无数据且不在排行页和社区页，显示空状态
        if (usageData.isEmpty() && currentPage !in listOf(2, 3) && allTimeRank.isEmpty()) {
            EmptyStateView(modifier = Modifier.fillMaxSize())
            return
        }

        val insight = remember(usageData) { UsageAnalyzer.analyze(usageData) }
        val totalTimeMs = usageData.sumOf { it.usageTimeMs }
        val yesterdayMs = yesterdayData.sumOf { it.usageTimeMs }
        val trend = remember(totalTimeMs, yesterdayMs) {
            UsageAnalyzer.compareTrend(totalTimeMs, yesterdayMs)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (currentPage) {
                0 -> {
                    // ---- 今日页面 ----
                    Text("使用分析", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryCard(totalDuration = UsageStatsRepository.formatDuration(totalTimeMs),
                        appCount = usageData.size)
                    Spacer(modifier = Modifier.height(12.dp))
                    TrendCard(trend = trend)
                    Spacer(modifier = Modifier.height(20.dp))
                    HourlyChart(hourlyData = hourlyDistribution)
                    Spacer(modifier = Modifier.height(24.dp))
                    AppRankingList(apps = usageData, totalTimeMs = totalTimeMs)
                    Spacer(modifier = Modifier.height(24.dp))
                    InsightSection(insight = insight)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                1 -> {
                    // ---- 周报页面 ----
                    WeeklyReportSection(summaries = weeklySummaries, todayApps = usageData)
                }
                2 -> {
                    // ---- 全历史总排行 ----
                    AllTimeRankScreen(rankList = allTimeRank)
                }
                3 -> {
                    // ---- 社区页面 ----
                    CommunityScreen(
                        currentUsername = username,
                        onRefresh = { /* 自动刷新 */ }
                    )
                }
            }
        }

        // 底部导航（四个 Tab）
        NavigationBar {
            NavigationBarItem(
                selected = currentPage == 0,
                onClick = { onPageChange(0) },
                icon = { Icon(Icons.Filled.Today, contentDescription = "今日") },
                label = { Text("今日") }
            )
            NavigationBarItem(
                selected = currentPage == 1,
                onClick = { onPageChange(1) },
                icon = { Icon(Icons.Filled.DateRange, contentDescription = "周报") },
                label = { Text("周报") }
            )
            NavigationBarItem(
                selected = currentPage == 2,
                onClick = { onPageChange(2) },
                icon = { Icon(Icons.Filled.Star, contentDescription = "排行") },
                label = { Text("排行") }
            )
            NavigationBarItem(
                selected = currentPage == 3,
                onClick = { onPageChange(3) },
                icon = { Icon(Icons.Filled.Groups, contentDescription = "社区") },
                label = { Text("社区") }
            )
        }
    }
}