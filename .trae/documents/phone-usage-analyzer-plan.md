# 手机使用习惯分析APP - MVP实施计划

## 一、项目概况

基于现有 Kotlin + Jetpack Compose + Material3 空白项目，开发"手机使用习惯分析"Android MVP应用。不引入任何第三方复杂架构库（无Hilt、无Room、无网络库），所有数据本地处理。

### 当前项目状态
- **包名**: `net.android.apllicationusetime`
- **minSdk**: 24, **targetSdk/compileSdk**: 36
- **Kotlin**: 2.2.10, **AGP**: 9.2.1, **Compose BOM**: 2026.02.01
- **MainActivity.kt**: 当前为 Hello World 模板
- **AndroidManifest.xml**: 未声明 PACKAGE_USAGE_STATS 权限
- **现有依赖**: activity-compose, material3, compose-ui, lifecycle-runtime-ktx, core-ktx

---

## 二、文件变更清单

### 2.1 修改文件

| 文件 | 变更内容 |
|------|---------|
| `app/src/main/AndroidManifest.xml` | 新增 PACKAGE_USAGE_STATS 权限声明 |
| `app/src/main/java/net/android/apllicationusetime/MainActivity.kt` | 完全重写：权限检测 → 条件渲染（权限引导页 / 主数据页） |

### 2.2 新增文件

| 文件 | 用途 |
|------|------|
| `app/src/main/java/net/android/apllicationusetime/model/AppUsage.kt` | 数据模型：应用使用记录、分类枚举 |
| `app/src/main/java/net/android/apllicationusetime/data/UsageStatsRepository.kt` | 数据层：通过 UsageStatsManager 读取今日使用时长 |
| `app/src/main/java/net/android/apllicationusetime/data/AppClassifier.kt` | 分类逻辑：基于包名/应用名将应用分类 |
| `app/src/main/java/net/android/apllicationusetime/data/UsageAnalyzer.kt` | 分析逻辑：生成使用习惯总结与建议 |
| `app/src/main/java/net/android/apllicationusetime/ui/screens/PermissionScreen.kt` | 权限引导页面 Composable |
| `app/src/main/java/net/android/apllicationusetime/ui/screens/MainScreen.kt` | 主数据仪表盘页面 Composable |
| `app/src/main/java/net/android/apllicationusetime/ui/components/SummaryCard.kt` | 今日总使用时长卡片组件 |
| `app/src/main/java/net/android/apllicationusetime/ui/components/AppRankingList.kt` | 应用排行列表组件（图标+名称+时长+进度条） |
| `app/src/main/java/net/android/apllicationusetime/ui/components/InsightSection.kt` | 行为总结与建议区域组件 |
| `app/src/main/java/net/android/apllicationusetime/ui/components/EmptyStateView.kt` | 空数据状态兜底组件 |

---

## 三、架构设计

```
MainActivity.kt (单一入口，权限判断 + setContent)
├── [未授权] → PermissionScreen.kt
│   └── 权限说明 + "去开启权限"按钮 → startActivity(ACTION_USAGE_ACCESS_SETTINGS)
└── [已授权] → MainScreen.kt
    ├── SummaryCard.kt          (今日总时长卡片)
    ├── AppRankingList.kt       (应用排行列表)
    ├── InsightSection.kt       (习惯总结 + 建议)
    └── EmptyStateView.kt       (无数据时的兜底)

数据流:
UsageStatsRepository → 原始数据 → AppClassifier(分类) + UsageAnalyzer(分析)
→ MainScreen 直接消费 Compose State (无ViewModel，使用 LaunchedEffect + mutableStateOf)
```

### 设计原则
- **无ViewModel**: 不引入 lifecycle-viewmodel-compose 依赖，使用 Compose 原生 `LaunchedEffect` + `mutableStateOf` 管理状态
- **权限变化自动刷新**: 通过 `onResume` 生命周期重新检测权限状态，用户从系统设置返回后自动刷新
- **onResume 刷新**: 重写 `onResume()`，每次 Activity 回到前台时重新检测权限 + 重新加载数据

---

## 四、详细实现方案

### 4.1 AndroidManifest.xml 变更

在 `<manifest>` 层级添加：

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
```

### 4.2 MainActivity.kt 重写

**核心逻辑：**
1. `onCreate()` 中检测权限状态 → 设置 Compose 内容
2. `onResume()` 中重新检测权限并通知 Compose 层刷新
3. `hasUsagePermission()` 方法检查 `AppOpsManager` 权限状态
4. 使用 `mutableStateOf<Boolean>` 跟踪权限状态，`mutableStateOf<List<AppUsage>>` 跟踪数据

**Composable 结构：**
```kotlin
ApllicationUseTimeTheme {
    Scaffold { padding ->
        if (!hasPermission) {
            PermissionScreen(onRequestPermission = { openSystemUsageSettings() })
        } else {
            MainScreen(usageData = usageData, isLoading = isLoading, padding = padding)
        }
    }
}
```

### 4.3 数据模型 (model/AppUsage.kt)

```kotlin
enum class AppCategory { SOCIAL, VIDEO, TOOL, GAME, OTHER }

data class AppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,        // 使用时长（毫秒）
    val category: AppCategory,
    val icon: Bitmap? = null       // 应用图标
)
```

### 4.4 数据层 (data/UsageStatsRepository.kt)

**功能：**
- 查询今日 00:00:00 至当前时间的 UsageStats
- 获取应用的包名、名称、使用时长
- 加载应用图标 (PackageManager.getApplicationIcon)
- 过滤系统应用与自身应用
- 按使用时长降序排列
- 格式化时长：`formatDuration(ms: Long): String` → "X小时Y分钟" / "Y分钟Z秒"

**关键API：**
```kotlin
val usageStatsManager = context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
val calendar = Calendar.getInstance()
calendar.set(Calendar.HOUR_OF_DAY, 0)
calendar.set(Calendar.MINUTE, 0)
calendar.set(Calendar.SECOND, 0)
calendar.set(Calendar.MILLISECOND, 0)
val startTime = calendar.timeInMillis
val endTime = System.currentTimeMillis()
val stats = usageStatsManager.queryUsageStats(
    UsageStatsManager.INTERVAL_DAILY, startTime, endTime
)
```

### 4.5 分类器 (data/AppClassifier.kt)

基于包名和应用名规则分类：

| 类别 | 规则示例 |
|------|---------|
| 社交 (SOCIAL) | 微信、QQ、微博、抖音、快手、小红书、知乎、贴吧、Telegram、WhatsApp、Instagram、Twitter/X |
| 视频 (VIDEO) | 优酷、爱奇艺、腾讯视频、B站、YouTube、Netflix、芒果TV、虎牙、斗鱼 |
| 工具 (TOOL) | 浏览器类(chrome, firefox, edge)、文件管理、计算器、笔记、天气、系统设置、输入法 |
| 游戏 (GAME) | 王者荣耀、和平精英、原神、崩坏、LOL手游、我的世界、Roblox 及含 game/游戏 关键字 |

**规则实现：** 维护包名关键词映射表 + 应用名关键词匹配，优先包名匹配。

### 4.6 分析器 (data/UsageAnalyzer.kt)

生成两项内容：

1. **使用习惯总结** (对应 InsightSection 上半部分):
   - 高频应用识别：列出使用时长Top 3
   - 碎片化使用分析：统计高频应用的平均单次使用时长（用应用数量/总次数近似）
   - 重度使用提醒：若某应用使用超过总时长的50%，标记为"重度使用"

2. **个性化建议** (对应 InsightSection 下半部分):
   根据分析结果动态生成3-4条建议：
   - 第一名App使用 > 2小时 → "尝试减少 [name] 使用时间20分钟"
   - 社交类总占比 > 40% → "设置晚间社交应用停用窗口"
   - 游戏类使用 > 1小时 → "考虑设置每日游戏时间上限"
   - 总使用时长 > 5小时 → "今天使用手机时间较长，建议适当休息"
   - 默认兜底 → "良好的使用习惯能让您更高效地平衡工作与生活"

```kotlin
data class UsageInsight(
    val topApps: List<AppUsage>,          // 高频Top3
    val heavyUsageApp: AppUsage?,          // 重度使用App (占比>50%)
    val socialRatio: Float,               // 社交类占比
    val gameRatio: Float,                 // 游戏类占比
    val videoRatio: Float,                // 视频类占比
    val totalTimeMs: Long                 // 总使用时长
)

data class UsageSuggestion(
    val title: String,                    // 建议标题
    val description: String               // 建议详情
)
```

### 4.7 权限引导页 (ui/screens/PermissionScreen.kt)

- 图标（Key/Security 图标或 Shield 图标）
- 标题："需要使用情况访问权限"
- 说明文字：解释为什么需要此权限
- Material3 FilledButton："去开启权限" → 跳转系统设置
- 调用 `Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)`

### 4.8 主仪表盘 (ui/screens/MainScreen.kt)

整合三个核心组件：
- `SummaryCard` — 今日总使用时长、解锁应用数
- `AppRankingList` — LazyColumn 应用排行
- `InsightSection` — 使用总结与建议
- 空数据时显示 `EmptyStateView`

### 4.9 今日总时长卡片 (ui/components/SummaryCard.kt)

- Material3 Card 组件
- 大字体显示总使用时长（如 "5小时32分钟"）
- 副标题：应用数量统计（"今天使用了 X 个应用"）
- 使用 ContainerColor 或 SurfaceVariant 突出显示
- 圆角卡片 + elevation

### 4.10 应用排行列表 (ui/components/AppRankingList.kt)

每一行 (Row)：
- 应用图标 (Image bitmap, 48dp, CircleShape)
- 应用名称 (Text, weight 1f)
- 使用时长时间 (Text, right-aligned)
- LinearProgressIndicator 或自定义进度条（占比百分比）

实现使用 `LazyColumn`。

### 4.11 习惯总结与建议 (ui/components/InsightSection.kt)

分为两个子区域：

**使用习惯概览：**
- "高频应用"：展示Top 3应用名称
- "碎片化分析"：如有多款应用时均较短 → 提示"使用较为碎片化"
- "重度提醒"：如某应用占比过高 → 卡片式提醒

**个性化建议：**
- 每条建议一个 Row（建议图标 + 文字）
- 使用 Material3 的 ListItem 或自定义布局

### 4.12 空状态组件 (ui/components/EmptyStateView.kt)

- 居中图标 (HourglassEmpty 或类似)
- "暂无使用数据" 文字
- "请确保已授权后正常使用手机" 提示

---

## 五、实施步骤

### 阶段一：权限与数据读取基础
1. 修改 `AndroidManifest.xml` 添加 `PACKAGE_USAGE_STATS` 权限
2. 创建 `model/AppUsage.kt` 数据模型
3. 创建 `data/UsageStatsRepository.kt` 数据获取层
4. 创建 `ui/screens/PermissionScreen.kt` 权限引导页
5. 重写 `MainActivity.kt`：权限检测 + 条件渲染 + onResume 刷新

### 阶段二：成品主页UI
6. 创建 `ui/components/SummaryCard.kt` 总时长卡片
7. 创建 `ui/components/AppRankingList.kt` 排行列表
8. 创建 `ui/components/EmptyStateView.kt` 空状态
9. 创建 `ui/screens/MainScreen.kt` 整合组件

### 阶段三：习惯总结与规划建议
10. 创建 `data/AppClassifier.kt` 应用分类
11. 创建 `data/UsageAnalyzer.kt` 习惯分析
12. 创建 `ui/components/InsightSection.kt` 洞察组件
13. 将分析结果集成到 MainScreen 和 MainActivity 数据加载流程

### 阶段四：构建验证
14. 执行 `gradlew.bat :app:assembleDebug` 构建验证
15. 修复编译错误
16. 最终确认 APK 生成成功

---

## 六、技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 状态管理 | Compose `mutableStateOf` + `LaunchedEffect` | 不引入 ViewModel 依赖，保持极简 |
| 权限刷新 | `onResume()` 重新检测 | 用户从系统设置返回后自动刷新 |
| 应用图标 | `PackageManager.getApplicationIcon()` → Bitmap → ImageBitmap | 原生方式加载，无额外依赖 |
| 时长格式化 | 自实现工具函数 | 无第三方时间库依赖 |
| 列表组件 | `LazyColumn` | Compose 原生高效列表 |
| 分类规则 | 硬编码包名/关键词映射表 | 满足MVP需求，无需机器学习 |

---

## 七、边界条件处理

1. **UsageStats 返回空** → 显示 EmptyStateView
2. **用户快速切换权限状态** → onResume 刷新保证一致性
3. **某些应用无图标** → 显示默认占位图标 (Icons.Default.Apps)
4. **数据全为系统应用** → 过滤后可能为空，同样走空状态
5. **Extreme 使用情况** → 总时长为0或极大值，UI不溢出

---

## 八、验证方法

1. 安装 APK 后首次启动 → 应显示权限引导页
2. 点击"去开启权限" → 应跳转系统使用情况访问权限设置页
3. 授权后返回 App → 自动显示使用数据仪表盘
4. 总时长卡片数据与系统设置中 UsageStats 数据一致
5. 应用列表按使用时长降序排列，进度条比例正确
6. 习惯总结内容合理，建议可执行
7. 所有设备上无崩溃（minSdk 24+）
