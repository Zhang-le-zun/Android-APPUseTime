# ApplicationUseTime 项目架构总结

```mermaid
graph TB
    subgraph "入口层 [MainActivity]"
        A[MainActivity] -->|onCreate| B[权限检查]
        A -->|onResume| B
        B -->|无权限| C[PermissionScreen]
        B -->|有权限| D[MainScreen]
    end

    subgraph "UI层 [Jetpack Compose + Material3]"
        D --> E[SummaryCard - 今日摘要]
        D --> F[AppRankingList - 应用排行]
        D --> G[HourlyChart - 24h分布图]
        D --> H[TrendCard - 趋势对比]
        D --> I[WeeklyReportSection - 周报]
        I --> I1[QuickStatCard]
        I --> I2[TrendLineChart]
        I --> I3[CategoryBreakdown]
        D --> J[InsightSection - 洞察建议]
    end

    subgraph "数据层"
        K[UsageStatsRepository] -->|queryAndBuild| L[UsageStatsManager 系统API]
        K -->|方案一| L1[queryAndAggregateUsageStats - API28+]
        K -->|方案二| L2[queryUsageStats + 手动聚合]
        K -->|方案三| L3[queryEvents 事件流]
        L1 --> M[去重聚合]
        L2 --> M
        L3 --> M
        M --> N[AppUsage 列表]
    end

    subgraph "分析层"
        O[UsageAnalyzer] --> O1[compareTrend - 趋势对比]
        O --> O2[analyzeTimeProfile - 时间画像]
        O --> O3[generateWeeklyReport - 周报生成]
        O --> O4[generateSuggestions - 建议生成]
        P[AppClassifier] --> P1[classify - 应用分类]
    end

    subgraph "持久化层 [DataStore Preferences]"
        Q[UsageStore] --> Q1[saveTodaySummary - 每日摘要JSON]
        Q --> Q2[saveDailyAppDetails - 每日详情JSON]
        Q --> Q3[getAllTimeRankFlow - 全历史排行]
        Q --> Q4[getHistoryFlow - 历史数据流]
    end

    subgraph "云端同步 [WIP]"
        R[SyncManager - 文件缺失] -->|OkHttp 4.12.0| S[(Supabase 后端)]
        S --> T[数据库 - 待确认状态]
    end

    subgraph "数据模型 [model]"
        U1[AppUsage: packageName, appName, category, usageTimeMs, icon]
        U2[DaySummary: dateTimestamp, totalTimeMs, appCount, topApp]
        U3[AppCategory 枚举: social, game, tool, entertainment, productivity, other]
    end

    %% 数据流连接
    N -->|传入| D
    N -->|持久化| Q
    Q -->|历史数据| O
    O -->|分析结果| J
    K -->|异步同步| R
```

## 数据流图

```mermaid
sequenceDiagram
    participant User as 用户
    participant UI as UI层(Compose)
    participant Act as MainActivity
    participant Repo as UsageStatsRepository
    participant Sys as 系统UsageStatsManager
    participant Store as UsageStore(DataStore)
    participant Ana as UsageAnalyzer
    participant Cloud as SyncManager(未完成)

    User->>Act: 打开App
    Act->>Act: 检查PACKAGE_USAGE_STATS权限
    alt 无权限
        Act->>UI: 显示PermissionScreen
        User->>UI: 点击"授予权限"
        UI->>Act: openUsageAccessSettings()
    else 有权限
        Act->>Repo: getTodayUsageStats()
        Repo->>Sys: queryAndAggregateUsageStats()
        Sys-->>Repo: 聚合数据
        Repo->>Repo: 去重+分类+构建AppUsage列表
        Repo-->>Act: today数据
        Act->>Repo: getUsageForDay(昨天)
        Repo-->>Act: yesterday数据
        Act->>Repo: getHourlyDistribution()
        Repo-->>Act: hourly分布
        Act->>Repo: getDailySummaryForDays(7天)
        Repo-->>Act: weekly摘要
        Act->>Store: saveTodaySummary()
        Store-->>Act: 持久化完成
        Repo-->>Cloud: syncDayDataFromStore() [异步]
        Act->>UI: 渲染MainScreen
        UI->>Ana: 分析/生成建议
        Ana-->>UI: insights + suggestions
    end
```

## 关键指标

| 指标 | 值 |
|------|-----|
| Kotlin文件数 | 633 |
| 总代码节点 | 6,658 |
| 关系边数 | 11,163 |
| 最热函数 | `formatDuration` (被6处调用) |
| 核心类 | `UsageStatsRepository` (7处调用者) |
| 编译SDK | 37 (Android 15) |
| 最小SDK | 24 (Android 7.0) |
| 当前状态 | 本地功能完整，云端同步未完成 |

## 待办问题

1. **SyncManager.kt 缺失** — UsageStatsRepository 第168行调用了 SyncManager.syncDayDataFromStore()，但该文件不存在
2. **Supabase SDK 未集成** — 当前仅依赖 OkHttp 自定义实现，未使用 supabase-kt SDK
3. **无 ViewModel** — 状态全在 MainActivity, 可重构为 ViewModel + StateFlow 模式
4. **无测试覆盖** — 仅有 ExampleUnitTest 占位测试