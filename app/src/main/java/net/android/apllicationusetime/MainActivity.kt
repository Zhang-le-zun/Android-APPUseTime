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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.android.apllicationusetime.data.*
import net.android.apllicationusetime.data.UsageStore.AppAllTimeRank
import net.android.apllicationusetime.data.supabase.AuthManager
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary
import net.android.apllicationusetime.ui.screens.LoginScreen
import net.android.apllicationusetime.ui.screens.MainScreen
import net.android.apllicationusetime.ui.screens.PermissionScreen
import net.android.apllicationusetime.ui.screens.RegisterScreen
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
                AppContent(this@MainActivity, hasPermission, currentPage,
                    onPageChange = { currentPage = it })
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

@Composable
private fun AppContent(
    activity: MainActivity,
    hasPermission: Boolean,
    currentPage: Int,
    onPageChange: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()

    // 认证状态
    var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }
    var showRegister by remember { mutableStateOf(false) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var currentUsername by remember { mutableStateOf("") }
    var currentEmail by remember { mutableStateOf("") }

    // 使用数据状态
    var usageData by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0L) }
    var yesterdayData by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var hourlyDistribution by remember { mutableStateOf<List<Long>>(List(24) { 0L }) }
    var weeklySummaries by remember { mutableStateOf<List<DaySummary>>(emptyList()) }

    // 检查登录状态
    val loginState by AuthManager.isLoggedInFlow(activity)
        .collectAsState(initial = false)
    val savedUsername by AuthManager.getUsernameFlow(activity)
        .collectAsState(initial = null)
    val savedEmail by AuthManager.getUserEmailFlow(activity)
        .collectAsState(initial = null)

    LaunchedEffect(loginState) {
        isLoggedIn = loginState
        currentUsername = savedUsername ?: ""
        currentEmail = savedEmail ?: ""
    }

    // 应用权限检查
    val actualHasPermission by remember { mutableStateOf(hasPermission) }
    // 更新 permission（onResume 导致的状态变化需重新读取）
    var effectivePermission by remember(hasPermission) { mutableStateOf(hasPermission) }
    LaunchedEffect(hasPermission) {
        effectivePermission = hasPermission
        if (hasPermission) refreshTrigger = System.currentTimeMillis()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

        when {
            isLoggedIn == null -> {
                // 正在检查登录状态
            }
            !isLoggedIn!! -> {
                // ---- 未登录 ----
                if (showRegister) {
                    RegisterScreen(
                        isLoading = authLoading,
                        errorMessage = authError,
                        onRegister = { email, password, username ->
                            authLoading = true
                            authError = null
                            scope.launch(Dispatchers.IO) {
                                val result = AuthManager.signUp(activity, email, password, username)
                                launch(Dispatchers.Main) {
                                    authLoading = false
                                    if (result.success && result.accessToken != null) {
                                        isLoggedIn = true
                                        currentUsername = result.username ?: username
                                        currentEmail = result.email ?: email
                                    } else if (result.success) {
                                        authError = result.error ?: "注册成功！请检查邮箱确认"
                                    } else {
                                        authError = result.error ?: "注册失败"
                                    }
                                }
                            }
                        },
                        onNavigateToLogin = {
                            showRegister = false
                            authError = null
                        }
                    )
                } else {
                    LoginScreen(
                        isLoading = authLoading,
                        errorMessage = authError,
                        onLogin = { email, password ->
                            authLoading = true
                            authError = null
                            scope.launch(Dispatchers.IO) {
                                val result = AuthManager.signIn(activity, email, password)
                                launch(Dispatchers.Main) {
                                    authLoading = false
                                    if (result.success) {
                                        isLoggedIn = true
                                        currentUsername = result.username ?: ""
                                        currentEmail = result.email ?: email
                                    } else {
                                        authError = result.error ?: "登录失败"
                                    }
                                }
                            }
                        },
                        onNavigateToRegister = {
                            showRegister = true
                            authError = null
                        }
                    )
                }
            }
            !effectivePermission -> {
                // ---- 已登录但未授权 ----
                PermissionScreen(onRequestPermission = {
                    activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                })
            }
            else -> {
                // ---- 已登录 + 已授权 ----
                val allTimeRankFlow = remember(refreshTrigger) {
                    UsageStatsRepository.getAllTimeRankFlow(activity)
                }
                val allTimeRank by allTimeRankFlow.collectAsState(initial = emptyList())

                LaunchedEffect(refreshTrigger) {
                    isLoading = true
                    withContext(Dispatchers.IO) {
                        val today = UsageStatsRepository.getTodayUsageStats(activity)
                        val yesterday = UsageStatsRepository.getUsageForDay(activity, daysAgo = 1)
                        val hourly = UsageStatsRepository.getHourlyDistribution(activity)
                        val summaries = UsageStatsRepository.getDailySummaryForDays(activity, 7)

                        val todayMs = today.sumOf { it.usageTimeMs }
                        val todaySummary = DaySummary(
                            dateTimestamp = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }.timeInMillis,
                            totalTimeMs = todayMs, appCount = today.size,
                            topApp = today.firstOrNull()
                        )
                        UsageStore.saveTodaySummary(activity, todaySummary)

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
                    onPageChange = onPageChange,
                    username = currentUsername.ifEmpty { currentEmail },
                    onLogout = {
                        scope.launch {
                            AuthManager.signOut(activity)
                            isLoggedIn = false
                        }
                    }
                )
            }
        }
    }
}