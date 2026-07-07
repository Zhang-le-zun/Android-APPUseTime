package net.android.apllicationusetime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.android.apllicationusetime.data.UsageAnalyzer
import net.android.apllicationusetime.data.UsageStatsRepository
import net.android.apllicationusetime.data.WeeklyReport
import net.android.apllicationusetime.model.AppCategory
import net.android.apllicationusetime.model.AppUsage
import net.android.apllicationusetime.model.DaySummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeeklyReportSection(
    summaries: List<DaySummary>,
    todayApps: List<AppUsage>,
    modifier: Modifier = Modifier
) {
    val report = remember(summaries, todayApps) {
        UsageAnalyzer.generateWeeklyReport(summaries, todayApps)
    }

    Column(modifier = modifier) {
        Text(
            text = "📅 周报",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 本周概览卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "本周使用总时长",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = UsageStatsRepository.formatDuration(report.totalWeekMs),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "日均 ${UsageStatsRepository.formatDuration(report.avgDailyMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // 最佳/最差日
        if (report.bestDay != null || report.worstDay != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 最佳日
                report.bestDay?.let { best ->
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "✅",
                        label = "使用最少",
                        value = UsageStatsRepository.formatDuration(best.totalTimeMs),
                        subtitle = formatDay(best.dateTimestamp),
                        color = Color(0xFF4CAF50)
                    )
                }
                // 最差日
                report.worstDay?.let { worst ->
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "⚠️",
                        label = "使用最多",
                        value = UsageStatsRepository.formatDuration(worst.totalTimeMs),
                        subtitle = formatDay(worst.dateTimestamp),
                        color = Color(0xFFFF7043)
                    )
                }
            }
        }

        // 7天趋势折线图
        if (report.trend.size >= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            TrendLineChart(
                trend = report.trend,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 分类分布
        if (report.categoryDistribution.any { it.value > 0f }) {
            Spacer(modifier = Modifier.height(16.dp))
            CategoryBreakdown(distribution = report.categoryDistribution)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier,
    emoji: String,
    label: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TrendLineChart(
    trend: List<DaySummary>,
    modifier: Modifier
) {
    val maxMs = remember(trend) { trend.maxOf { it.totalTimeMs }.coerceAtLeast(1L) }
    val dateFormat = remember { SimpleDateFormat("M/d", Locale.getDefault()) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "7天趋势",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val chartWidth = size.width - 8.dp.toPx()
                val chartHeight = size.height - 16.dp.toPx()
                val startX = 8.dp.toPx()
                val points = trend.mapIndexed { index, day ->
                    val x = startX + (chartWidth * index / (trend.size - 1).coerceAtLeast(1))
                    val y = chartHeight - (chartHeight * day.totalTimeMs / maxMs)
                    Offset(x, y)
                }

                // 折线
                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF6366F1),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 数据点
                points.forEach { point ->
                    drawCircle(
                        color = Color(0xFF6366F1),
                        radius = 4.dp.toPx(),
                        center = point
                    )
                }
            }

            // 日期标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                trend.takeLast(7).forEach { day ->
                    Text(
                        text = dateFormat.format(Date(day.dateTimestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(
    distribution: Map<AppCategory, Float>
) {
    val categoryNames = mapOf(
        AppCategory.SOCIAL to "社交",
        AppCategory.VIDEO to "视频",
        AppCategory.TOOL to "工具",
        AppCategory.GAME to "游戏",
        AppCategory.OTHER to "其他"
    )
    val colors = mapOf(
        AppCategory.SOCIAL to Color(0xFFE91E63),
        AppCategory.VIDEO to Color(0xFF2196F3),
        AppCategory.TOOL to Color(0xFF4CAF50),
        AppCategory.GAME to Color(0xFFFF9800),
        AppCategory.OTHER to Color(0xFF9E9E9E)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "使用分布",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 堆叠条
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                var x = 0f
                distribution.entries.sortedByDescending { it.value }.forEach { (cat, ratio) ->
                    val width = size.width * ratio
                    if (width > 0) {
                        drawRect(color = colors[cat] ?: Color.Gray, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(width, size.height))
                        x += width
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                distribution.entries
                    .filter { it.value > 0f }
                    .sortedByDescending { it.value }
                    .forEach { (cat, ratio) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawCircle(color = colors[cat] ?: Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${categoryNames[cat]} ${(ratio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
            }
        }
    }
}

private fun formatDay(timestamp: Long): String {
    val sdf = SimpleDateFormat("M月d日 E", Locale.CHINESE)
    return sdf.format(Date(timestamp))
}