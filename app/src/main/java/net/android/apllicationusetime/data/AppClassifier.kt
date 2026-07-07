package net.android.apllicationusetime.data

import net.android.apllicationusetime.model.AppCategory

object AppClassifier {

    private val packageRules = mapOf(
        // 社交类
        "tencent.mm" to AppCategory.SOCIAL,
        "com.tencent.mobileqq" to AppCategory.SOCIAL,
        "com.tencent.qqlite" to AppCategory.SOCIAL,
        "com.sina.weibo" to AppCategory.SOCIAL,
        "com.ss.android.ugc.aweme" to AppCategory.SOCIAL,       // 抖音
        "com.kuaishou" to AppCategory.SOCIAL,
        "com.smile.gifmaker" to AppCategory.SOCIAL,
        "com.kuaishou.nebula" to AppCategory.SOCIAL,
        "com.xingin.xhs" to AppCategory.SOCIAL,                  // 小红书
        "com.zhihu.android" to AppCategory.SOCIAL,
        "com.baidu.tieba" to AppCategory.SOCIAL,
        "org.telegram" to AppCategory.SOCIAL,
        "com.whatsapp" to AppCategory.SOCIAL,
        "com.instagram" to AppCategory.SOCIAL,
        "com.twitter.android" to AppCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AppCategory.SOCIAL,         // TikTok
        "com.job.android" to AppCategory.SOCIAL,                  // 脉脉
        // 视频类
        "com.youku.phone" to AppCategory.VIDEO,
        "com.qiyi.video" to AppCategory.VIDEO,
        "com.tencent.qqlive" to AppCategory.VIDEO,
        "tv.danmaku.bili" to AppCategory.VIDEO,
        "com.google.android.youtube" to AppCategory.VIDEO,
        "com.netflix.mediaclient" to AppCategory.VIDEO,
        "com.hunantv.imgo.activity" to AppCategory.VIDEO,         // 芒果TV
        "com.duowan.kiwi" to AppCategory.VIDEO,                   // 虎牙
        "air.tv.douyu.android" to AppCategory.VIDEO,              // 斗鱼
        // 工具类
        "com.android.chrome" to AppCategory.TOOL,
        "org.mozilla.firefox" to AppCategory.TOOL,
        "com.microsoft.emmx" to AppCategory.TOOL,
        "com.UCMobile" to AppCategory.TOOL,
        "com.quark.browser" to AppCategory.TOOL,
        "com.baidu.searchbox" to AppCategory.TOOL,
        "com.android.settings" to AppCategory.TOOL,
        "com.android.vending" to AppCategory.TOOL,
        "com.google.android.apps.maps" to AppCategory.TOOL,
        "com.autonavi.minimap" to AppCategory.TOOL,
        // 游戏类
        "com.tencent.tmgp.sgame" to AppCategory.GAME,
        "com.tencent.tmgp.pubgmhd" to AppCategory.GAME,
        "com.miHoYo.Yuanshen" to AppCategory.GAME,
        "com.hypergryph.arknights" to AppCategory.GAME,
        "com.netease.wyclx" to AppCategory.GAME,
        "com.mojang.minecraftpe" to AppCategory.GAME,
        "com.roblox.client" to AppCategory.GAME,
        "com.tencent.lolm" to AppCategory.GAME,
    )

    private val nameKeywords = mapOf(
        AppCategory.SOCIAL to listOf("微信", "QQ", "微博", "抖音", "快手", "小红书", "知乎", "贴吧", "推特", "telegram", "instagram", "whatsapp", "line"),
        AppCategory.VIDEO to listOf("视频", "影视", "TV", "tv", "直播", "bilibili", "youtube", "netflix", "优酷", "爱奇艺", "腾讯视频"),
        AppCategory.TOOL to listOf("浏览器", "地图", "文件", "计算器", "笔记", "天气", "输入法", "翻译", "时钟", "日历"),
        AppCategory.GAME to listOf("游戏", "game", "GAME", "Game")
    )

    fun classify(packageName: String, appName: String): AppCategory {
        // 优先包名匹配
        packageRules[packageName]?.let { return it }

        // 包名关键词匹配
        val lowerPackage = packageName.lowercase()
        if (lowerPackage.contains("game") || lowerPackage.contains("play")) return AppCategory.GAME

        // 应用名关键词匹配
        for ((category, keywords) in nameKeywords) {
            for (keyword in keywords) {
                if (appName.contains(keyword, ignoreCase = true)) {
                    return category
                }
            }
        }

        return AppCategory.OTHER
    }
}
