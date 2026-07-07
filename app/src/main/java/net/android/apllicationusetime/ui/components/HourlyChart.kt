package net.android.apllicationusetime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.android.apllicationusetime.data.TimeProfile
import net.android.apllicationusetime.data.UsageAnalyzer
import net.android.apllicationusetime.data.UsageStatsRepository

@Composable
fun HourlyChart(
    hourlyData: List<Long>,
    modifier: Modifier = Modifier
) {
    val maxMs = remember(hourlyData) { hourlyData.maxOrNull() ?: 1L }
    val profile: TimeProfile = remember(hourlyData) { UsageAnalyzer.analyzeTimeProfile(hourlyData) }
    val peakHour = remember(hourlyData) { hourlyData.indices.maxByOrNull { hourlyData[it] } ?: -1 }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83D\uDD52",
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "时段分布",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 用户画像标签
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = profile.iconEmoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = profile.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 柱状图
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 时段标签行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0时", "6时", "12时", "18时", "23时").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Canvas 柱状图
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    val barCount = hourlyData.size
                    val barWidth = (chartWidth / barCount) * 0.7f
                    val gap = (chartWidth / barCount) * 0.3f

                    for (i in 0 until barCount) {
                        val ratio = if (maxMs > 0) hourlyData[i].toFloat() / maxMs else 0f
                        val barHeight = chartHeight * ratio.coerceAtLeast(0.02f) // 最少2%高度以可见

                        val x = i * (chartWidth / barCount) + gap / 2f
                        val y = chartHeight - barHeight

                        val color = when {
                            i == peakHour -> Color(0xFFFF7043)      // 峰值橙色
                            i in 22..23 || i in 0..5 -> Color(0xFF5C6BC0)  // 夜间紫色
                            i in 6..12 -> Color(0xFF66BB6A)         // 上午绿色
                            i in 13..18 -> Color(0xFFFFA726)        // 下午橙黄
                            else -> Color(0xFF42A5F5)               // 晚间蓝色
                        }

                        drawRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 峰值信息
                if (peakHour >= 0 && maxMs > 0) {
                    Text(
                        text = "\uD83D\uDD25 使用高峰：${peakHour}:00 - ${peakHour + 1}:00（${UsageStatsRepository.formatDuration(hourlyData[peakHour])}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}