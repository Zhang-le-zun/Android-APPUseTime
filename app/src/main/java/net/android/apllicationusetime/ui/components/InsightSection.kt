package net.android.apllicationusetime.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.android.apllicationusetime.data.UsageAnalyzer
import net.android.apllicationusetime.data.UsageInsight

@Composable
fun InsightSection(
    insight: UsageInsight,
    modifier: Modifier = Modifier
) {
    val suggestions = UsageAnalyzer.generateSuggestions(insight)

    Column(modifier = modifier) {
        // 使用习惯概览
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83D\uDCCA",
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "使用习惯概览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 高频应用
                if (insight.topApps.isNotEmpty()) {
                    val topAppNames = insight.topApps.joinToString("、") { it.appName }
                    InsightItem("高频应用：$topAppNames")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 重度使用提醒
                val heavy = insight.heavyUsageApp
                if (heavy != null) {
                    InsightItem(
                        "重度提醒：${heavy.appName} 占用了今天超过一半的使用时间，" +
                                "建议适当调整使用习惯"
                    )
                }

                // 碎片化提醒
                if (insight.isFragmented) {
                    Spacer(modifier = Modifier.height(8.dp))
                    InsightItem("碎片化提示：您今天切换应用较为频繁，使用模式偏碎片化")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 个性化建议
        if (suggestions.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\uD83D\uDCA1",
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "个性化建议",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            suggestions.forEach { suggestion ->
                SuggestionItem(suggestion.title, suggestion.description)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InsightItem(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuggestionItem(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}
