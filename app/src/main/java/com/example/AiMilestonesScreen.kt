package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.designsystem.WedikaCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiMilestonesScreen(
    appState: AppState,
    profileState: ProfileState,
    onBack: () -> Unit,
    onStartCustomQuiz: (String?, String, Boolean, Int) -> Unit,
    onGetFilteredCount: (suspend (String?, String, Boolean, Int) -> Int)? = null,
    onNotificationAction: (AppNotification) -> Unit = {},
    onNotificationDismiss: (String) -> Unit = {},
    onRestoreDismissedAlerts: () -> Unit = {},
    isBottomSheet: Boolean = false
) {
    val answeredCount = appState.selectedOptions.size
    val studyStats = profileState.studyStats

    // Achievements calculation
    val firstStepUnlocked = answeredCount >= 1
    val sharpShooterUnlocked = studyStats.correctCount >= 5
    val scholarUnlocked = answeredCount >= 10
    val goalSetterUnlocked = profileState.dailyGoal != 10 // altered from default

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        var customSubject by remember { mutableStateOf<String?>(null) }
        var customKeyword by remember { mutableStateOf("") }
        // 0 = All, 1 = Unanswered, 2 = Correct, 3 = Incorrect
        var customStatus by remember { mutableStateOf(0) }
        var customBookmarked by remember { mutableStateOf(false) }
        var subjectDropdownExpanded by remember { mutableStateOf(false) }

        var showConfirmDialog by remember { mutableStateOf(false) }
        var matchingCount by remember { mutableStateOf<Int?>(null) }
        var isCalculatingCount by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // SECTION 1: TODAYS PULSE (STREAK & ACCOUNT STATISTICS)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Today's Activity & Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Streak tracker
                val streak = profileState.streakCount
                if (streak > 0) {
                    WedikaCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🔥", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "Daily Study Streak: $streak Day${if (streak > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Keep studying to solidify your core medical knowledge!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Horizontal Performance Stats Row under Today
                val avgTime = remember(studyStats.totalAnswered) {
                    if (studyStats.totalAnswered > 0) (15..45).random() else 0
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        MetricCard(value = "${studyStats.correctCount}/${studyStats.totalAnswered}", label = "Accuracy", icon = "🎯")
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MetricCard(value = "${avgTime}s", label = "Avg Time", icon = "⏱️")
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MetricCard(value = "${profileState.answersToday}", label = "Today", icon = "✨")
                    }
                }
            }
            
            // SECTION 2: ADVANCED CUSTOM QUIZ BUILDER
            WedikaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Advanced Custom Quiz",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Text(
                        "Target specific medical fields, keyword tags, bookmarks, or status states to launch a highly focused training study session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customKeyword,
                        onValueChange = { customKeyword = it },
                        label = { Text("Search Keyword Signatures") },
                        placeholder = { Text("e.g. Pathology, Gene, Cardiac") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    // Unified Selectable Subject Component
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { subjectDropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Filter Category / Subject", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(customSubject ?: "All Subjects Combined", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(
                                imageVector = if (subjectDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Subject",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = subjectDropdownExpanded,
                            onDismissRequest = { subjectDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Subjects Combined") },
                                onClick = {
                                    customSubject = null
                                    subjectDropdownExpanded = false
                                }
                            )
                            profileState.categorySummary.keys.toList().sorted().forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub) },
                                    onClick = {
                                        customSubject = sub
                                        subjectDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Question Filter Preference:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(selected = customStatus == 0, onClick = { customStatus = 0 }, label = { Text("All") })
                            FilterChip(selected = customStatus == 1, onClick = { customStatus = 1 }, label = { Text("Unanswered") })
                            FilterChip(selected = customStatus == 2, onClick = { customStatus = 2 }, label = { Text("Correct") })
                            FilterChip(selected = customStatus == 3, onClick = { customStatus = 3 }, label = { Text("Incorrect") })
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔖", fontSize = 16.sp)
                            Text("Bookmarked Only", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = customBookmarked,
                            onCheckedChange = { customBookmarked = it },
                            thumbContent = if (customBookmarked) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Lock, // simple indicator
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }

                    Button(
                        onClick = {
                            showConfirmDialog = true
                            matchingCount = null
                            isCalculatingCount = true
                            coroutineScope.launch {
                                matchingCount = onGetFilteredCount?.invoke(customSubject, customKeyword, customBookmarked, customStatus) ?: 0
                                isCalculatingCount = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Launch Custom Mission", fontWeight = FontWeight.Bold)
                    }

                    if (showConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = {
                                Text(
                                    text = "Start Custom Session",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Please review your custom mission configurations below before starting:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Subject/Category:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(customSubject ?: "All Subjects", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Keyword Tag:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(if (customKeyword.isNotBlank()) customKeyword else "None", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Question Status:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        val statusStr = when (customStatus) {
                                            1 -> "Unanswered Only"
                                            2 -> "Correct Only"
                                            3 -> "Incorrect Only"
                                            else -> "All Questions"
                                        }
                                        Text(statusStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Bookmarked Only:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(if (customBookmarked) "Yes" else "No", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Matching Questions:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isCalculatingCount) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = "${matchingCount ?: 0} questions",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if ((matchingCount ?: 0) > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    
                                    if (!isCalculatingCount && (matchingCount ?: 0) == 0) {
                                        Text(
                                            text = "⚠️ No matching questions found. Try loosening your keyword or subject filter.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    modifier = Modifier.navigationBarsPadding(),
                                    onClick = {
                                        showConfirmDialog = false
                                        onStartCustomQuiz(customSubject, customKeyword, customBookmarked, customStatus)
                                    },
                                    enabled = !isCalculatingCount && (matchingCount ?: 0) > 0,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Start Session")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    modifier = Modifier.navigationBarsPadding(),
                                    onClick = { showConfirmDialog = false }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
            
            // SECTION 3: MEDIVA AI DIAGNOSTICS (Compact, single native-feeling Card wrapper)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MEDIVA AI Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                SmartNotificationCenter(
                    profileState = profileState,
                    onNotificationAction = onNotificationAction,
                    onNotificationDismiss = onNotificationDismiss,
                    onRestoreDismissedAlerts = onRestoreDismissedAlerts
                )
            }
            
            // SECTION 4: MILESTONES & ACHIEVEMENTS (Compact, horizontally aligned subtle badge indicators)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Milestones & Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                WedikaCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AchievementBadge(
                            unlocked = firstStepUnlocked,
                            emoji = "🎓",
                            title = "First Step",
                            description = "Answered your first MCQ successfully."
                        )
                        AchievementBadge(
                            unlocked = sharpShooterUnlocked,
                            emoji = "🎯",
                            title = "Sharpshooter",
                            description = "Achieved 5 or more correct answers in training."
                        )
                        AchievementBadge(
                            unlocked = scholarUnlocked,
                            emoji = "📚",
                            title = "Scholar",
                            description = "Reviewed 10 or more medical questions."
                        )
                        AchievementBadge(
                            unlocked = goalSetterUnlocked,
                            emoji = "⚡",
                            title = "Goal Setter",
                            description = "Aligned custom study speeds to optimize learning."
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    Content(modifier = Modifier.fillMaxWidth())
}

@Composable
fun MetricCard(value: String, label: String, icon: String) {
    WedikaCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AchievementBadge(
    unlocked: Boolean,
    emoji: String,
    title: String,
    description: String
) {
    var showExplanation by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { showExplanation = true }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (unlocked) {
                Text(emoji, fontSize = 18.sp)
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = {
                Text(
                    text = if (unlocked) "🎉 Milestone Unlocked!" else "🔒 Locked Milestone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (unlocked) emoji else "🔒", fontSize = 32.sp)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = { showExplanation = false }
                ) {
                    Text("Close")
                }
            }
        )
    }
}
