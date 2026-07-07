package net.android.apllicationusetime

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.android.apllicationusetime.data.*
import net.android.apllicationusetime.data.UsageStore.AppAllTimeRank
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary
import net.android.apllicationusetime.ui.screens.MainScreen
import net.android.apllicationusetime.ui.screens.PermissionScreen
import net.android.apllicationusetime.ui.theme.ApllicationUseTimeTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)
    private var usageData by mutableStateOf<List<AppUsage>>(emptyList())
    private var isLoading by mutableStateOf(true)
    private var refreshTrigger by mutableStateOf(0L)

    private var yesterdayData by mutableStateOf<List<AppUsage>>(emptyList())
    private var hourlyDistribution by mutableStateOf<List<Long>>(List(24) { 0L })
    private var weeklySummaries by mutableStateOf<List<DaySummary>>(emptyList())
    private var currentPage by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasPermission = checkUsagePermission()

        setContent {
            ApllicationUseTimeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!hasPermission) {
                        PermissionScreen(onRequestPermission = { openUsageAccessSettings() })
                    } else {
                        // 从 Room 实时收集全历史排行
                        val allTimeRankFlow = remember(refreshTrigger) {
                            UsageStatsRepository.getAllTimeRankFlow(this@MainActivity)
                        }
                        val allTimeRank by allTimeRankFlow.collectAsState(initial = emptyList())

                        LaunchedEffect(refreshTrigger) {
                            isLoading = true
                            withContext(Dispatchers.IO) {
                                val today = UsageStatsRepository.getTodayUsageStats(this@MainActivity)
                                val yesterday = UsageStatsRepository.getUsageForDay(this@MainActivity, daysAgo = 1)
                                val hourly = UsageStatsRepository.getHourlyDistribution(this@MainActivity)
                                val summaries = UsageStatsRepository.getDailySummaryForDays(this@MainActivity, 7)

                                val todayMs = today.sumOf { it.usageTimeMs }
                                val todaySummary = DaySummary(
                                    dateTimestamp = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis,
                                    totalTimeMs = todayMs, appCount = today.size,
                                    topApp = today.firstOrNull()
                                )
                                UsageStore.saveTodaySummary(this@MainActivity, todaySummary)

                                withContext(Dispatchers.Main) {
                                    usageData = today
                                    yesterdayData = yesterday
                                    hourlyDistribution = hourly
                                    weeklySummaries = summaries
                                    isLoading = false
                                }
                            }
                        }

                        MainScreen(
                            usageData = usageData,
                            yesterdayData = yesterdayData,
                            hourlyDistribution = hourlyDistribution,
                            weeklySummaries = weeklySummaries,
                            allTimeRank = allTimeRank,
                            isLoading = isLoading,
                            padding = innerPadding,
                            currentPage = currentPage,
                            onPageChange = { currentPage = it }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val newPermission = checkUsagePermission()
        if (newPermission != hasPermission) hasPermission = newPermission
        if (newPermission) refreshTrigger = System.currentTimeMillis()
    }

    private fun checkUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}