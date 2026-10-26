package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.designsystem.WedikaCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.*

import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class TopicStat(
    val subject: String,
    val topic: String,
    val attempted: Int,
    val correct: Int,
    val accuracy: Float
)

@Composable
fun UserStatisticsScreen(
    modifier: Modifier = Modifier,
    statsViewModel: StatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onTopicSelected: ((String, String) -> Unit)? = null
) {
    val statsState by statsViewModel.uiState.collectAsStateWithLifecycle()
    
    // Dynamically calculate stats based on the latest state
    var topicToStart by remember { mutableStateOf<TopicStat?>(null) }
    val totalQuestionsCount = statsState.totalQuestionsCount
    val totalAttempted = statsState.totalAttempted
    val totalCorrect = statsState.totalCorrect
    val computedTopicStats = statsState.computedTopicStats
    val overallAccuracy = statsState.overallAccuracy
    val completionPercentage = statsState.completionPercentage
    val performanceSummary = statsState.performanceSummary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Study Dashboard Header
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Study Analytics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "$totalQuestionsCount total questions | ${String.format(java.util.Locale.US, "%.1f", completionPercentage)}% database completion",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (performanceSummary.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = performanceSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (totalAttempted > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${overallAccuracy.toInt()}% Accuracy",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main Dashboard card detailing Attempted, Correct, Failed
        item {
            WedikaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "Overall Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom circular accuracy indicator
                        Box(
                            modifier = Modifier.size(84.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val backgroundRingColor = com.example.ui.designsystem.AppStatusColors.unanswered.copy(alpha = 0.2f)
                            val activeProgressColor = if (overallAccuracy >= 60) com.example.ui.designsystem.AppStatusColors.correct else com.example.ui.designsystem.AppStatusColors.incorrect
                            val animatedSweep by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = (overallAccuracy / 100f) * 360f,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                label = "accuracy_sweep"
                            )
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = backgroundRingColor,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = activeProgressColor,
                                    startAngle = -90f,
                                    sweepAngle = animatedSweep,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "${overallAccuracy.toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Stats side items
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Attempted", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalAttempted", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Correct", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalCorrect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.designsystem.AppStatusColors.correct)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Failed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${totalAttempted - totalCorrect}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.designsystem.AppStatusColors.incorrect)
                            }
                        }
                    }
                }
            }
        }

        if (computedTopicStats.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).padding(bottom = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            "No questions studied yet.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Complete custom study sessions to see details here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Topic Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(computedTopicStats) { stat ->
            WedikaCard(
                     modifier = Modifier.fillMaxWidth(),
                     onClick = if (onTopicSelected != null) {
                         { topicToStart = stat }
                     } else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = com.example.utils.formatTopicDisplayName(stat.topic),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = stat.subject,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            // Minimal bar chart
                            LinearProgressIndicator(
                                progress = { stat.accuracy / 100f },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f) // 80% to leave space
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.5f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${stat.accuracy.toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${stat.correct}/${stat.attempted} correct",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (onTopicSelected != null) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Start Topic Quiz",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    topicToStart?.let { targetTopic ->
        AppAlertDialog(
            onDismissRequest = { topicToStart = null },
            title = "Start Topic Session",
            confirmText = "Confirm",
            onConfirm = {
                val (subj, top) = targetTopic.subject to targetTopic.topic
                topicToStart = null
                onTopicSelected?.invoke(subj, top)
            },
            dismissText = "Cancel",
            onDismiss = { topicToStart = null },
            icon = Icons.Default.WorkspacePremium,
            iconColor = MaterialTheme.colorScheme.primary,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Would you like to launch a custom study session for the following topic?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.utils.formatTopicDisplayName(targetTopic.topic),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Subject: ${targetTopic.subject}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }
}
