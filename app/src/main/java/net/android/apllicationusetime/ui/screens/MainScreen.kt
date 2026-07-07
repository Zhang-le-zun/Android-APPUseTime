package net.android.apllicationusetime.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    onPageChange: (Int) -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
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

    if (usageData.isEmpty() && currentPage != 2 && allTimeRank.isEmpty()) {
        EmptyStateView(modifier = Modifier.fillMaxSize().padding(padding))
        return
    }

    val insight = remember(usageData) { UsageAnalyzer.analyze(usageData) }
    val totalTimeMs = usageData.sumOf { it.usageTimeMs }
    val yesterdayMs = yesterdayData.sumOf { it.usageTimeMs }
    val trend = remember(totalTimeMs, yesterdayMs) {
        UsageAnalyzer.compareTrend(totalTimeMs, yesterdayMs)
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
            }
        }

        // 底部导航（三 Tab）
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
        }
    }
}