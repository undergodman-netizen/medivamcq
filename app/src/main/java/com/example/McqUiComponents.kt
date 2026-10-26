@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example

import android.content.Context
import androidx.compose.ui.text.style.TextAlign
import android.os.Build
import com.example.utils.fuzzyMatch
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.compose.animation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.designsystem.WedikaCard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import com.example.utils.*


import com.example.ui.components.*



@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun McqDashboard(
    summary: Map<String, Map<String, TopicStats>>,
    sourceSummary: Map<String, Int>,
    filterMode: String,
    onFilterModeChanged: (String) -> Unit,
    onSourceSelected: (String) -> Unit,
    onCategorySelected: (String?, String?) -> Unit,
    onResetProgress: () -> Unit,
    onSearchClick: () -> Unit,
    dashboardSearchQuery: String = "",
    isDashboardSearchVisible: Boolean = false,
    onDashboardSearchQueryChanged: (String) -> Unit = {},
    onToggleDashboardSearch: (Boolean) -> Unit = {},
    onStartCustomSession: (Map<String, Set<String>>, ExamSettings) -> Unit = { _, _ -> },
    onResumeActiveSession: () -> Unit = {},
    appState: AppState,
    profileState: ProfileState,
    onNotificationAction: (AppNotification) -> Unit = {},
    onNotificationDismiss: (String) -> Unit = {},
    onNavigateToCustomModule: (String?) -> Unit = {},
    onNavigateToFlashcards: () -> Unit = {},
    onNavigateToCustomList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalQuestions = remember(summary) { summary.values.sumOf { it.values.sumOf { t -> t.total } } }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }

    val groupedSubjects by produceState(initialValue = emptyList<GroupedSubject>(), summary, dashboardSearchQuery) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            groupSubjects(summary, dashboardSearchQuery)
        }
    }

    val effectivelyExpandedFolders = remember(expandedFolders, dashboardSearchQuery, groupedSubjects) {
        if (dashboardSearchQuery.isNotBlank()) {
            groupedSubjects.map { it.displayName }.toSet()
        } else {
            expandedFolders
        }
    }


    val filteredSummary = remember(summary, dashboardSearchQuery) {
        if (dashboardSearchQuery.isBlank()) summary
        else {
            summary.filter { (subject, topics) ->
                subject.fuzzyMatch(dashboardSearchQuery) ||
                topics.keys.any { it.fuzzyMatch(dashboardSearchQuery) }
            }
        }
    }

    val filteredSourceSummary = remember(sourceSummary, dashboardSearchQuery) {
        if (dashboardSearchQuery.isBlank()) sourceSummary
        else {
            sourceSummary.filter { (source, _) ->
                source.fuzzyMatch(dashboardSearchQuery) ||
                formatSourceDisplayName(source).fuzzyMatch(dashboardSearchQuery)
            }
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver, key = "dashboard_list_state") { LazyListState() }

    LaunchedEffect(dashboardSearchQuery, groupedSubjects) {
        if (dashboardSearchQuery.isNotBlank()) {
            val firstMatchIndex = groupedSubjects.indexOfFirst { group ->
                group.rawMatches.any { (subject, topics) ->
                    subject.fuzzyMatch(dashboardSearchQuery) ||
                    topics.keys.any { it.fuzzyMatch(dashboardSearchQuery) }
                }
            }
            if (firstMatchIndex != -1) {
                kotlinx.coroutines.delay(100)
                // Offset index by 2 because of the static item headers in the LazyColumn
                listState.scrollToItem(firstMatchIndex + 2)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (summary.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WedikaCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        onClick = { onNavigateToCustomModule(null) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Custom Modules",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (appState.hasResumableSession) {
                        WedikaCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            onClick = { onResumeActiveSession() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Resume",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WedikaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        onClick = { onNavigateToFlashcards() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Flash Cards",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        } else {
            item {
                com.example.ui.components.EmptyState(
                    icon = Icons.Default.CloudUpload,
                    title = "No Questions Found",
                    subtitle = "Go to Profile > Database to upload your question bank JSON files.",
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                )
            }
        }

        if (summary.isNotEmpty()) {
            item {
                Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDashboardSearchVisible) {
                    OutlinedTextField(
                        value = dashboardSearchQuery,
                        onValueChange = onDashboardSearchQueryChanged,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        placeholder = { Text("Filter subjects...", style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            IconButton(onClick = { onToggleDashboardSearch(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Filter", modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(
                            if (filterMode == "source") "Filter by File" else "Filter by Subject",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onToggleDashboardSearch(true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Filter subjects",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary // highlighted to match user request better
                            )
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterMode == "subject",
                        onClick = { onFilterModeChanged("subject") },
                        label = { Text("Subject", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = filterMode == "source",
                        onClick = { onFilterModeChanged("source") },
                        label = { Text("File", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (filterMode == "source") {
            val sourcesList = filteredSourceSummary.toList().sortedWith(compareBy { (src, _) ->
                if (src == "Legacy / Untagged") 1 else 0
            })
            
            if (sourcesList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (dashboardSearchQuery.isNotBlank()) "No files match your filter." else "No file sources available.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(sourcesList, key = { it.first }) { (sourceName, count) ->
                    val isLegacy = sourceName == "Legacy / Untagged"
                    WedikaCard(
                        onClick = { onSourceSelected(sourceName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLegacy) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isLegacy) {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = if (isLegacy) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isLegacy) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (isLegacy) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = formatSourceDisplayName(sourceName),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isLegacy) "Pre-installed standard practice sets" else "Questions imported from file",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "$count Qs",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (groupedSubjects.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (dashboardSearchQuery.isNotBlank()) "No subjects match your filter." else "No subjects available.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                groupedSubjects.forEach { group ->
                    val isExpanded = effectivelyExpandedFolders.contains(group.displayName)
                    val totalSetsInGroup = group.rawMatches.size
                    val totalQsInGroup = group.rawMatches.sumOf { it.second.values.sumOf { t -> t.total } }
                    val finishedQsInGroup = group.rawMatches.sumOf { it.second.values.sumOf { t -> t.finished } }
                    
                    item(key = "group_${group.displayName}") {
                        WedikaCard(
                            onClick = {
                                onCategorySelected(group.displayName, null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = group.icon,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(14.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        HighlightedText(
                                            text = group.displayName,
                                            query = dashboardSearchQuery,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${totalSetsInGroup} ${if (totalSetsInGroup == 1) "folder" else "folders"} • ${totalQsInGroup} MCQ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (finishedQsInGroup > 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.padding(end = 6.dp)
                                            ) {
                                                Text(
                                                    text = "$finishedQsInGroup done",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
                                            contentDescription = "Open",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                
                                if (isExpanded) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        
                                        group.rawMatches.forEach { (sub, topics) ->
                                            topics.forEach { (topic, stats) ->
                                                val isMatch = dashboardSearchQuery.isNotBlank() && topic.fuzzyMatch(dashboardSearchQuery)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .then(if (isMatch) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier)
                                                        .clickable {
                                                            onCategorySelected(sub, topic)
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        HighlightedText(
                                                            text = formatTopicDisplayName(topic),
                                                            query = dashboardSearchQuery,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "${stats.total} questions",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (filterMode == "subject") {
                item(key = "custom_import_card") {
                    // ponytail: custom sources appear under their real subjects
                    // too; if that becomes noise, filter by source_url NOT LIKE 'custom://%'
                    // in getSubjectTopicCounts.
                    CustomImportCard(onClick = onNavigateToCustomList)
                }
            }
        }
        }
    }
}

@Composable
fun CustomImportCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    WedikaCard(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .height(56.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Custom",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun SubSubjectsScreen(
    subjectGroup: String,
    summary: Map<String, Map<String, TopicStats>>,
    onSubjectSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    val grouped = remember(summary) { groupSubjects(summary, "") }
    val groupMatch = grouped.find { it.displayName == subjectGroup }
    
    val rawMatchesForGroup = groupMatch?.rawMatches ?: emptyList()

    val filteredSubjects = remember(rawMatchesForGroup, searchQuery) {
        if (searchQuery.isBlank()) rawMatchesForGroup
        else rawMatchesForGroup.filter { (sub, topics) ->
            sub.fuzzyMatch(searchQuery) ||
            topics.keys.any { it.fuzzyMatch(searchQuery) }
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver, key = "sub_subjects_list_state_$subjectGroup") { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = "Modules / $subjectGroup",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Select a Module",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = { Text("Search modules...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                )
            }
        }

        if (filteredSubjects.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No modules found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filteredSubjects, key = { it.first }) { (rawSubject, topics) ->
                val finishedRawQs = topics.values.sumOf { it.finished }
                val totalRawQs = topics.values.sumOf { it.total }
                
                WedikaCard(
                    onClick = { onSubjectSelected(rawSubject) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                HighlightedText(
                                    text = rawSubject,
                                    query = searchQuery,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${topics.size} topics • $totalRawQs MCQ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (finishedRawQs > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "$finishedRawQs done",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Open",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun SubjectCard(
    subject: String,
    totalTopics: Int,
    totalQuestions: Int,
    finishedQuestions: Int = 0,
    onSubjectSelected: () -> Unit
) {
    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "scale"
    )

    WedikaCard(
        onClick = { onSubjectSelected() },
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "$totalTopics topics • $totalQuestions questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (finishedQuestions > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$finishedQuestions done",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun McqTopicList(
    subject: String,
    topicCounts: Map<String, TopicStats>,
    onTopicSelected: (String) -> Unit,
    onOpenCustomModule: (Map<String, Set<String>>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedTopics by rememberSaveable { mutableStateOf(setOf<String>()) }
    val haptic = LocalHapticFeedback.current

    val filteredTopicsState = produceState(initialValue = emptyList<Pair<String, TopicStats>>(), topicCounts, searchQuery) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            if (searchQuery.isBlank()) {
                topicCounts.toList().sortedBy { it.first }
            } else {
                topicCounts.toList().filter { (topic, _) ->
                    topic.fuzzyMatch(searchQuery)
                }.sortedBy { it.first }
            }
        }
    }
    val filteredTopics = filteredTopicsState.value

    val listState = rememberSaveable(saver = LazyListState.Saver, key = "topic_list_state_$subject") { LazyListState() }

    LaunchedEffect(searchQuery, filteredTopics) {
        if (searchQuery.isNotBlank() && filteredTopics.isNotEmpty()) {
            val firstMatchIndex = filteredTopics.indexOfFirst { (topic, _) ->
                topic.fuzzyMatch(searchQuery)
            }
            if (firstMatchIndex != -1) {
                kotlinx.coroutines.delay(100)
                // Add 1 for the header item in LazyColumn
                listState.scrollToItem(firstMatchIndex + 1)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (isSelectionMode) 120.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = "Subjects / $subject",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isSelectionMode) "Select Topics" else "Select a Topic",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (isSelectionMode) {
                            TextButton(onClick = { 
                                isSelectionMode = false
                                selectedTopics = emptySet()
                            }) {
                                Text("Cancel")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search topics...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            if (filteredTopics.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No topics found matching search query.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredTopics, key = { it.first }) { (topic, stats) ->
                    val isSelected = selectedTopics.contains(topic)
                    WedikaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedTopics = if (isSelected) {
                                            selectedTopics - topic
                                        } else {
                                            selectedTopics + topic
                                        }
                                    } else {
                                        onTopicSelected(topic)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSelectionMode = true
                                        selectedTopics = selectedTopics + topic
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Optional: Toggle selection on long click in selection mode as well
                                        selectedTopics = if (isSelected) {
                                            selectedTopics - topic
                                        } else {
                                            selectedTopics + topic
                                        }
                                    }
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatTopicDisplayName(topic),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${stats.total} MCQs / ${stats.finished} over",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else if (stats.finished > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (stats.finished > 0) FontWeight.Medium else FontWeight.Normal,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null, // Handled by Card click
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedTopics.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { onOpenCustomModule(mapOf(subject to selectedTopics)) },
                        enabled = selectedTopics.isNotEmpty(),
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text("Configure Module")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun McqFileTopicList(
    sourceName: String,
    appState: AppState,
    onTopicSelected: (subject: String, topic: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val srcTopics = appState.sourceTopics

    val subjectsInFile = remember(srcTopics) {
        srcTopics.keys.toList().sorted()
    }

    var selectedSubjectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val topicsInFile = remember(srcTopics, selectedSubjectFilter, appState.selectedOptions, searchQuery) {
        val mapped = mutableListOf<FileTopicItem>()
        for ((sub, topics) in srcTopics) {
            if (selectedSubjectFilter == null || sub == selectedSubjectFilter) {
                for ((top, stats) in topics) {
                    if (searchQuery.isBlank() || top.fuzzyMatch(searchQuery)) {
                        mapped.add(FileTopicItem(
                            subject = sub,
                            topic = top,
                            totalCount = stats.total,
                            finishedCount = stats.finished
                        ))
                    }
                }
            }
        }
        mapped.sortedWith(compareBy({ it.subject }, { it.topic }))
    }

    val listState = rememberSaveable(saver = LazyListState.Saver, key = "file_topic_list_state_$sourceName") { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(end = 8.dp).size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text(
                            text = "Files / ${formatSourceDisplayName(sourceName)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Browse Topics in File",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text("Search topics in file...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                )
                
                if (subjectsInFile.isNotEmpty()) {
                    var isSubjectsExpanded by rememberSaveable { mutableStateOf(false) }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isSubjectsExpanded = !isSubjectsExpanded }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Filter by Subject:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (!isSubjectsExpanded) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = selectedSubjectFilter ?: "All Subjects",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        
                        IconButton(
                            onClick = { isSubjectsExpanded = !isSubjectsExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isSubjectsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isSubjectsExpanded) "Collapse subjects" else "Expand subjects",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    if (isSubjectsExpanded) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = selectedSubjectFilter == null,
                                onClick = { selectedSubjectFilter = null },
                                label = { Text("All Subjects") },
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            subjectsInFile.forEach { subject ->
                                FilterChip(
                                    selected = selectedSubjectFilter == subject,
                                    onClick = { selectedSubjectFilter = subject },
                                    label = { Text(subject) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                        }
                    }
                }
            }
        }

        if (topicsInFile.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No topics found matching current filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                count = topicsInFile.size,
                key = { index -> "${topicsInFile[index].subject}_${topicsInFile[index].topic}" }
            ) { index ->
                val item = topicsInFile[index]
                val isMatch = searchQuery.isNotBlank() && item.topic.fuzzyMatch(searchQuery)

                WedikaCard(
                    onClick = { onTopicSelected(item.subject, item.topic) },
                    modifier = Modifier.fillMaxWidth().then(if (isMatch) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)) else Modifier),
                    colors = CardDefaults.cardColors(containerColor = if (isMatch) Color.Transparent else MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            HighlightedText(
                                text = formatTopicDisplayName(item.topic),
                                query = searchQuery,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subject,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${item.totalCount} MCQs / ${item.finishedCount} over",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.finishedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (item.finishedCount > 0) FontWeight.Medium else FontWeight.Normal,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Start topic",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

data class FileTopicItem(
    val subject: String,
    val topic: String,
    val totalCount: Int,
    val finishedCount: Int
)

@Composable
fun ModeSelectionDialog(
    topic: String,
    availableQuestions: Int,
    onDismiss: () -> Unit,
    onStart: (isExamMode: Boolean, questionCount: Int, timeLimitMinutes: Int, secondsPerQuestion: Int, randomize: Boolean) -> Unit
) {
    var selectedMode by remember { mutableStateOf("Practice") }
    var isAllSelected by remember { mutableStateOf(true) }
    var customCountText by remember { mutableStateOf((10.coerceAtMost(availableQuestions)).toString()) }
    val selectedCount = if (isAllSelected) availableQuestions else (customCountText.toIntOrNull() ?: 10).coerceIn(1, availableQuestions)
    
    var randomize by remember { mutableStateOf(true) }
    var showAdvanced by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Configure Session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Topic summary
                Text(
                    text = formatTopicDisplayName(topic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Mode Selection
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Practice", "Timed", "Exam").forEach { mode ->
                        val isSelected = selectedMode == mode
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedMode = mode }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mode,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Mode rules / description helper content
                val modeDesc = when (selectedMode) {
                    "Practice" -> "Practice"
                    "Timed" -> "Practice with timer (60sec)"
                    "Exam" -> "Only 60sec for MCQ"
                    else -> "Practice"
                }

                Text(
                    text = modeDesc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
                if (selectedMode == "Exam") {
                    Text(
                        text = "Scoring: +4 for correct, -1 for incorrect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                
                // Advanced Settings Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Settings", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Advanced Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (showAdvanced) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Questions Limit inside Advanced Settings
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f)).padding(12.dp)
                        ) {
                            Text(
                                text = "Questions Limit", 
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Custom Input button/field
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (!isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(if (!isAllSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { isAllSelected = false }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = customCountText,
                                        onValueChange = { input ->
                                            isAllSelected = false
                                            val clean = input.filter { it.isDigit() }
                                            if (clean.length <= 4) {
                                                customCountText = clean
                                            }
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        ),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                        modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 20.dp, max = 50.dp)
                                    )
                                    Text(
                                        text = "/$availableQuestions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (!isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // All button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { isAllSelected = true }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "All ($availableQuestions)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Shuffling & Hands-Free config options
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Shuffle Questions", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = randomize,
                                    onCheckedChange = { randomize = it },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isExamMode = selectedMode == "Exam"
                    val finalTimeLimit = if (selectedMode == "Timed" || selectedMode == "Exam") (selectedCount * 60) / 60 else 0
                    val secondsPerQuestion = if (selectedMode == "Timed" || selectedMode == "Exam") 60 else 0
                    onStart(isExamMode, selectedCount, finalTimeLimit, secondsPerQuestion, randomize)
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                Text("Start $selectedMode")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun McqListMode(
    questions: List<McqField>,
    selectedOptions: Map<String, String>,
    bookmarkedQuestionTexts: Set<String>,
    onOptionSelected: (String, String) -> Unit,
    onToggleBookmark: (McqField) -> Unit,
    modifier: Modifier = Modifier
) {
    if (questions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No questions match your search.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val totalCount = questions.size
        val answeredCount = questions.count { q -> selectedOptions.containsKey(q.question) }
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$totalCount MCQs / $answeredCount over",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            items(questions) { question ->
                McqItemCard(
                    question = question,
                    selectedOption = selectedOptions[question.question],
                    isBookmarked = bookmarkedQuestionTexts.contains(question.question),
                    onToggleBookmark = { onToggleBookmark(question) },
                    onOptionSelected = { option -> onOptionSelected(question.question, option) }
                )
            }
        }
    }
}

@Composable
fun SubduedTimer(
    totalSeconds: Int,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeString = String.format("%02d:%02d", mins, secs)
    val isCritical = remainingSeconds in 1..5

    Box(
        modifier = modifier
            .background(
                if (isCritical) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isCritical) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCritical) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ExamTimer(
    sessionStartTimeMs: Long,
    timeLimitMinutes: Int,
    questionKey: Any?,
    onTimeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (timeLimitMinutes <= 0) return

    var remainingGlobalSeconds by remember { mutableStateOf(0) }
    var remainingQuestionSeconds by remember(questionKey) { mutableStateOf(60) }
    var showGlobalTimer by remember { mutableStateOf(false) }

    LaunchedEffect(sessionStartTimeMs, timeLimitMinutes) {
        val durationMillis = timeLimitMinutes * 60 * 1000L
        while (true) {
            val elapsed = System.currentTimeMillis() - sessionStartTimeMs
            val rem = durationMillis - elapsed
            if (rem <= 0) {
                remainingGlobalSeconds = 0
                onTimeUp()
                break
            } else {
                remainingGlobalSeconds = (rem / 1000).toInt()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    LaunchedEffect(questionKey) {
        remainingQuestionSeconds = 60
        while (remainingQuestionSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            remainingQuestionSeconds--
        }
    }

    val displaySeconds = if (showGlobalTimer || questionKey == null) remainingGlobalSeconds else remainingQuestionSeconds
    val totalSeconds = if (showGlobalTimer || questionKey == null) timeLimitMinutes * 60 else 60

    SubduedTimer(
        totalSeconds = totalSeconds,
        remainingSeconds = displaySeconds,
        modifier = modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null
        ) {
            if (questionKey != null && !showGlobalTimer) {
                showGlobalTimer = true
            }
        }
    )

    LaunchedEffect(showGlobalTimer) {
        if (showGlobalTimer) {
            kotlinx.coroutines.delay(3000L)
            showGlobalTimer = false
        }
    }
}

@Composable
fun QuestionTimer(
    questionKey: Any,
    durationSeconds: Int = 60,
    onTimeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remainingSeconds by remember(questionKey, durationSeconds) { mutableStateOf(durationSeconds) }

    LaunchedEffect(questionKey, durationSeconds) {
        remainingSeconds = durationSeconds
        while (remainingSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            remainingSeconds--
        }
        onTimeUp()
    }

    SubduedTimer(
        totalSeconds = durationSeconds,
        remainingSeconds = remainingSeconds,
        modifier = modifier
    )
}

@Composable
fun McqQuizMode(
    question: McqField,
    index: Int,
    total: Int,
    selectedOption: String?,
    isBookmarked: Boolean,
    isExamMode: Boolean,
    onToggleBookmark: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onJumpToQuestion: (Int) -> Unit = {},
    sessionStartTimeMs: Long = 0L,
    timeLimitMinutes: Int = 0,
    secondsPerQuestion: Int = 0,
    onEndExamEarly: (() -> Unit)? = null,
    onExitQuiz: (() -> Unit)? = null,
    answeredCount: Int = -1,
    answeredStatus: Map<Int, Boolean?> = emptyMap(),
    isCardMode: Boolean = false,
    isReviewMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expandedGrid by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    if (!isReviewMode) {
        androidx.activity.compose.BackHandler(enabled = true) {
            showExitConfirmDialog = true
        }
    }

    if (showExitConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("End Session?") },
            text = { Text("Resume session?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = {
                        showExitConfirmDialog = false
                        onEndExamEarly?.invoke()
                    }
                ) {
                    Text("End", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = {
                        showExitConfirmDialog = false
                        onExitQuiz?.invoke()
                    }
                ) {
                    Text("Resume")
                }
            }
        )
    }
    
    LaunchedEffect(expandedGrid) {
        if (expandedGrid) {
            kotlinx.coroutines.delay(7000L)
            expandedGrid = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Use a weight and vertical scroll for the question area
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = question,
                transitionSpec = {
                    fadeIn(androidx.compose.animation.core.tween(150)) togetherWith
                    fadeOut(androidx.compose.animation.core.tween(150))
                },
                label = "question_transition"
            ) { targetQuestion ->
                val highlightRange: IntRange? = null
                
                val headerContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "(${index + 1}/$total)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { expandedGrid = !expandedGrid }
                                    .padding(4.dp)
                            )
                            if (isExamMode && timeLimitMinutes > 0) {
                                ExamTimer(
                                    sessionStartTimeMs = sessionStartTimeMs,
                                    timeLimitMinutes = timeLimitMinutes,
                                    questionKey = targetQuestion.question,
                                    onTimeUp = { onEndExamEarly?.invoke() ?: onNext() }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } else if (!isExamMode && secondsPerQuestion > 0 && !isCardMode) {
                                QuestionTimer(
                                    questionKey = targetQuestion.question,
                                    durationSeconds = secondsPerQuestion,
                                    onTimeUp = {}
                                )
                            }
                        }
                    }
                }

                if (expandedGrid) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { expandedGrid = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = { expandedGrid = false }
                                ),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(24.dp),
                                shadowElevation = 12.dp,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null,
                                        onClick = {} // Capture clicks inside dialog
                                    )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Jump to Question",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(48.dp),
                                        contentPadding = PaddingValues(bottom = 8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(total) { i ->
                                            val status = answeredStatus[i]
                                            val isAnswered = status != null
                                            val isCurrent = i == index
                                            
                                            val bgColor = if (isCurrent) {
                                                com.example.ui.designsystem.AppStatusColors.current
                                            } else if (status == true) {
                                                com.example.ui.designsystem.AppStatusColors.correct.copy(alpha = 0.25f)
                                            } else if (status == false) {
                                                com.example.ui.designsystem.AppStatusColors.incorrect.copy(alpha = 0.25f)
                                            } else if (isAnswered) {
                                                com.example.ui.designsystem.AppStatusColors.current.copy(alpha = 0.2f)
                                            } else {
                                                com.example.ui.designsystem.AppStatusColors.unanswered.copy(alpha = 0.4f)
                                            }
                                            
                                            val textColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else if (status == true) com.example.ui.designsystem.AppStatusColors.correct
                                                            else if (status == false) com.example.ui.designsystem.AppStatusColors.incorrect
                                                            else if (isAnswered) MaterialTheme.colorScheme.onSecondaryContainer
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
        
                                            Box(
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(bgColor)
                                                    .clickable {
                                                        onJumpToQuestion(i)
                                                        expandedGrid = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${i + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                var offsetX by remember(targetQuestion.question) { mutableStateOf(0f) }

                val modifierWithSwipe = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                    .pointerInput(targetQuestion.question) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val minDistancePx = 50.dp.toPx()
                                val triggerNext = offsetX < -minDistancePx && index < total - 1
                                val triggerPrev = offsetX > minDistancePx && index > 0

                                if (triggerPrev) {
                                    onPrev()
                                } else if (triggerNext) {
                                    onNext()
                                } else {
                                    coroutineScope.launch {
                                        androidx.compose.animation.core.animate(
                                            initialValue = offsetX,
                                            targetValue = 0f,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = 300,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        ) { value, _ ->
                                            offsetX = value
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    androidx.compose.animation.core.animate(
                                        initialValue = offsetX,
                                        targetValue = 0f,
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = 300,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    ) { value, _ ->
                                        offsetX = value
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount
                            }
                        )
                    }

                if (isCardMode) {
                    var isFlipped by remember(targetQuestion.question) { mutableStateOf(false) }
                    val rotation by animateFloatAsState(
                        targetValue = if (isFlipped) 180f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "flipRotation"
                    )
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    
                    Box(modifier = modifierWithSwipe
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density
                        }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isReviewMode || selectedOption != null) {
                                isFlipped = !isFlipped
                            }
                        }
                    ) {
                        if (rotation <= 90f) {
                            WedikaCard(
                                modifier = Modifier.fillMaxSize(),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                                ),
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(
                                    defaultElevation = if (isDark) 0.dp else 2.dp
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                                )
                            ) {
                                McqItemCard(
                                    question = targetQuestion,
                                    selectedOption = selectedOption,
                                    isBookmarked = isBookmarked,
                                    isExamMode = isExamMode,
                                    onToggleBookmark = onToggleBookmark,
                                    onOptionSelected = onOptionSelected,
                                    modifier = Modifier.fillMaxSize(),
                                    isScrollable = true,
                                    cleanStyle = false,
                                    highlightRange = highlightRange,
                                    forceHideExplanation = true,
                                    forceHideFrontContent = false,
                                    headerContent = headerContent,
                                    onCardTap = if (isReviewMode || selectedOption != null) { { isFlipped = !isFlipped } } else null,
                                    isReviewMode = isReviewMode
                                )
                            }
                        } else {
                            WedikaCard(
                                modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                                ),
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(
                                    defaultElevation = if (isDark) 0.dp else 2.dp
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
                                )
                            ) {
                                McqItemCard(
                                    question = targetQuestion,
                                    selectedOption = selectedOption,
                                    isBookmarked = isBookmarked,
                                    isExamMode = isExamMode,
                                    onToggleBookmark = onToggleBookmark,
                                    onOptionSelected = onOptionSelected,
                                    modifier = Modifier.fillMaxSize(),
                                    isScrollable = true,
                                    cleanStyle = false,
                                    highlightRange = highlightRange,
                                    forceHideExplanation = false,
                                    forceHideFrontContent = true,
                                    headerContent = null,
                                    onCardTap = { isFlipped = !isFlipped },
                                    isReviewMode = isReviewMode
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = modifierWithSwipe) {
                        McqItemCard(
                            question = targetQuestion,
                            selectedOption = selectedOption,
                            isBookmarked = isBookmarked,
                            isExamMode = isExamMode,
                            onToggleBookmark = onToggleBookmark,
                            onOptionSelected = onOptionSelected,
                            modifier = Modifier.fillMaxSize(),
                            isScrollable = true,
                            cleanStyle = false,
                            highlightRange = highlightRange,
                            forceHideExplanation = false,
                            forceHideFrontContent = false,
                            headerContent = headerContent,
                            isReviewMode = isReviewMode
                        )
                    }
                }
            }
        }
        
        var showSubmitConfirmation by remember { mutableStateOf(false) }
        var showFinishConfirmation by remember { mutableStateOf(false) }

        if (showFinishConfirmation) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showFinishConfirmation = false },
                title = { Text("Finish Session?") },
                text = { Text("Are you sure you want to finish and view the summary?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = {
                            showFinishConfirmation = false
                            onNext()
                        }
                    ) {
                        Text("Finish", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = { showFinishConfirmation = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showSubmitConfirmation) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSubmitConfirmation = false },
                title = { Text("Submit Exam?") },
                text = { Text("Are you sure you want to end the exam early? Unanswered questions will be marked incorrect.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = {
                            showSubmitConfirmation = false
                            onEndExamEarly?.invoke()
                        }
                    ) {
                        Text("Submit", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = { showSubmitConfirmation = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Bottom Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPrev,
                    enabled = index > 0,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                }
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove Bookmark" else "Bookmark Question",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (isExamMode && onEndExamEarly != null) {
                androidx.compose.material3.TextButton(
                    onClick = { showSubmitConfirmation = true },
                    modifier = Modifier.padding(end = 4.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
            
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    if (index < total - 1) {
                        onNext()
                    } else {
                        showFinishConfirmation = true
                    }
                },
                enabled = index < total,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 5.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (index < total - 1) {
                    Text("Next", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                } else {
                    Text("Finish", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Check, contentDescription = "Finish")
                }
            }
        }
    }
}

@Composable
fun McqOptionRow(
    option: String,
    isSelected: Boolean,
    isCorrectAnswer: Boolean,
    showFeedback: Boolean,
    isRevealed: Boolean,
    isExamMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDictated: Boolean = false
) {
    val profileViewModel: com.example.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val profileState by profileViewModel.state.collectAsState()

    val clinicalFont = if (profileState.isClinicalMode) {
        if (profileState.clinicalFontFamily.equals("Serif", ignoreCase = true)) {
            androidx.compose.ui.text.font.FontFamily.Serif
        } else {
            androidx.compose.ui.text.font.FontFamily.SansSerif
        }
    } else {
        androidx.compose.ui.text.font.FontFamily.Default
    }

    var isStruckThrough by remember(option) { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val strikeThroughAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isStruckThrough) 0.40f else 1.0f,
        label = "strikeThroughAlpha"
    )

    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else if (isDictated) 1.03f else if (!showFeedback && isSelected) 0.98f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "scaleTransition"
    )

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val backgroundColor by animateColorAsState(when {
        isDictated -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        !showFeedback && isSelected -> com.example.ui.designsystem.AppStatusColors.current.copy(alpha = 0.45f)
        !showFeedback -> com.example.ui.designsystem.AppStatusColors.unanswered.copy(alpha = if (isDark) 0.15f else 0.3f)
        isCorrectAnswer -> com.example.ui.designsystem.AppStatusColors.correct.copy(alpha = 0.2f)
        isSelected && !isCorrectAnswer -> com.example.ui.designsystem.AppStatusColors.incorrect.copy(alpha = 0.2f)
        else -> com.example.ui.designsystem.AppStatusColors.unanswered.copy(alpha = 0.08f)
    }, label = "bgTransition")

    val borderColor by animateColorAsState(when {
        isDictated -> MaterialTheme.colorScheme.tertiary
        !showFeedback && isSelected -> MaterialTheme.colorScheme.primary
        !showFeedback -> Color.Transparent
        isCorrectAnswer -> com.example.ui.designsystem.AppStatusColors.correct
        isSelected && !isCorrectAnswer -> com.example.ui.designsystem.AppStatusColors.incorrect
        else -> Color.Transparent
    }, label = "borderTransition")

    val textColor by animateColorAsState(when {
        isDictated -> MaterialTheme.colorScheme.onTertiaryContainer
        !showFeedback && isSelected -> MaterialTheme.colorScheme.primary
        !showFeedback -> MaterialTheme.colorScheme.onSurface
        isCorrectAnswer -> com.example.ui.designsystem.AppStatusColors.correct
        isSelected && !isCorrectAnswer -> com.example.ui.designsystem.AppStatusColors.incorrect
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.8f else 0.65f)
    }, label = "textTransition")

    val optionPrefix = remember(option) {
        if (option.length >= 3 && option[1] == '.' && option[2] == ' ') {
            option.substring(0, 2)
        } else if (option.length >= 3 && option[1] == ')' && option[2] == ' ') {
            option.substring(0, 2)
        } else {
            ""
        }
    }
    val optionBody = remember(option, optionPrefix) {
        if (optionPrefix.isNotEmpty()) {
            option.substring(3)
        } else {
            option
        }
    }

    Row(
        modifier = modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = strikeThroughAlpha
            }
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = (!isRevealed || isExamMode),
                onClick = {
                    if (isStruckThrough) {
                        isStruckThrough = false
                    }
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100L)
                        onClick()
                    }
                },
                onLongClick = {
                    isStruckThrough = !isStruckThrough
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (optionPrefix.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        when {
                            isDictated -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            !showFeedback && isSelected -> MaterialTheme.colorScheme.primary
                            showFeedback && isCorrectAnswer -> Color(0xFF10B981)
                            showFeedback && isSelected && !isCorrectAnswer -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isDictated -> MaterialTheme.colorScheme.tertiary
                            !showFeedback && isSelected -> Color.Transparent
                            showFeedback && isCorrectAnswer -> Color.Transparent
                            showFeedback && isSelected && !isCorrectAnswer -> Color.Transparent
                            else -> Color.Transparent
                        },
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                Text(
                    text = optionPrefix.replace(".", "").replace(")", "").trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        !showFeedback && isSelected -> MaterialTheme.colorScheme.onPrimary
                        showFeedback && isCorrectAnswer -> Color.White
                        showFeedback && isSelected && !isCorrectAnswer -> MaterialTheme.colorScheme.onError
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = optionBody,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = if (profileState.isClinicalMode) 26.sp else 22.sp,
                fontFamily = clinicalFont,
                fontSize = if (profileState.isClinicalMode) 17.sp else MaterialTheme.typography.bodyLarge.fontSize,
                textDecoration = if (isStruckThrough) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
            ),
            color = textColor,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
        if (showFeedback) {
            if (isCorrectAnswer) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Correct",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
            } else if (isSelected) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = "Incorrect",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun triggerAnswerHaptic(context: Context, isCorrect: Boolean) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (isCorrect) {
                // Dual-tier vibration: two short, light vibration pulses
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 50, 80, 50)
                    val amplitudes = intArrayOf(0, 100, 0, 100) // Light pulses
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 50, 80, 50), -1)
                }
            } else {
                // A very short, delicate pulse or quiet tap for incorrect feedback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 40)
                    val amplitudes = intArrayOf(0, 45) // Short, gentle vibration intensity
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(40)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun ShimmerImagePlaceholder(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "image_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "image_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading Diagram...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ErrorImagePlaceholder(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            )
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onRetry() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error Loading Diagram",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Failed to load diagram",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to retry loading",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ShimmerExplanationSkeleton(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {} // Consume touch events to block user interaction
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.15f),
                    shape = RoundedCornerShape(6.dp)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.15f),
                    shape = RoundedCornerShape(6.dp)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.15f),
                    shape = RoundedCornerShape(6.dp)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.15f),
                    shape = RoundedCornerShape(6.dp)
                )
        )
    }
}

@Composable
fun McqItemCard(
    question: McqField,
    selectedOption: String?,
    isBookmarked: Boolean = false,
    isExamMode: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = false,
    cleanStyle: Boolean = false,
    highlightRange: IntRange? = null,
    forceHideExplanation: Boolean = false,
    forceHideFrontContent: Boolean = false,
    isReviewMode: Boolean = false,
    headerContent: @Composable (() -> Unit)? = null,
    onCardTap: (() -> Unit)? = null
) {
    val isRevealed = isReviewMode || selectedOption != null
    val showFeedback = isRevealed && !isExamMode
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val viewModel: com.example.McqViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val aiViewModel: com.example.AiViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val appState by viewModel.state.collectAsStateWithLifecycle()
    val profileViewModel: com.example.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    
    val ttsManager = com.example.LocalTtsManager.current
    val currentTag by ttsManager?.currentTag?.collectAsStateWithLifecycle(null) ?: remember { mutableStateOf(null) }
    val audioState by ttsManager?.audioState?.collectAsStateWithLifecycle(com.example.AudioState.IDLE) ?: remember { mutableStateOf(com.example.AudioState.IDLE) }
    val activeTtsRange by ttsManager?.activeRange?.collectAsStateWithLifecycle(null) ?: remember { mutableStateOf(null) }

    val aiExplanationStates by aiViewModel.aiExplanationStates.collectAsStateWithLifecycle()
    val aiExplanationState = aiExplanationStates[question.question] ?: if (!question.ai_explanation.isNullOrBlank()) AiState.Success(question.ai_explanation) else AiState.Idle
    val isGeneratingAiExplanation = aiExplanationState is AiState.Loading

    var isExplanationExpanded by rememberSaveable(question.question) { mutableStateOf(!question.explanation.isNullOrBlank() || !question.ai_explanation.isNullOrBlank()) }
    var localAiExplanation by rememberSaveable(question.question) { mutableStateOf(question.ai_explanation) }
    var showAiExplanationMode by rememberSaveable(question.question) { mutableStateOf(question.explanation.isNullOrBlank() && !localAiExplanation.isNullOrBlank()) }
    var fullScreenImageUrl by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(aiExplanationState) {
        if (aiExplanationState is AiState.Success) {
            localAiExplanation = aiExplanationState.text
        }
    }

    val manualOptionsRanges = remember(question.question, question.options) {
        val ranges = mutableListOf<IntRange>()
        var currentPos = question.question.length + ". \n".length
        question.options.forEachIndexed { idx, opt ->
            val start = currentPos
            val end = currentPos + opt.length
            ranges.add(start..end)
            currentPos = end + ". ".length
        }
        ranges
    }

    val activeSpokenOptionIndex = remember(currentTag, activeTtsRange, manualOptionsRanges, question.question) {
        val currentActiveRange = activeTtsRange
        if (currentTag == "q_${question.question}" && currentActiveRange != null) {
            val rangeFirst = currentActiveRange.first
            val index = manualOptionsRanges.indexOfFirst { rangeFirst in it }
            if (index != -1) index else null
        } else null
    }

    val itemYOffsets = remember { mutableStateMapOf<String, Float>() }
    var parentCoordinates: androidx.compose.ui.layout.LayoutCoordinates? by remember { mutableStateOf(null) }

    if (isScrollable) {
        LaunchedEffect(question) {
            scrollState.scrollTo(0)
            itemYOffsets.clear()
        }
        LaunchedEffect(highlightRange) {
            if (highlightRange != null && question.question.isNotEmpty()) {
                val progress = highlightRange.first.toFloat() / question.question.length
                val maxScroll = scrollState.maxValue
                if (maxScroll > 0) {
                    scrollState.animateScrollTo((maxScroll * progress).toInt())
                }
            }
        }
        LaunchedEffect(activeSpokenOptionIndex) {
            val idx = activeSpokenOptionIndex
            if (idx != null) {
                val yPos = itemYOffsets["option_$idx"]
                if (yPos != null) {
                    val targetScroll = (yPos - 120).coerceAtLeast(0f)
                    scrollState.animateScrollTo(targetScroll.toInt())
                }
            }
        }
    }

    val scrollModifier = if (isScrollable) {
        Modifier.verticalScroll(scrollState)
    } else Modifier

    val handleReveal = { opt: String ->
        if (!isRevealed || isExamMode) {
            val isCorrect = opt.startsWith(question.correct_answer + ".", ignoreCase = true) || 
                            opt.startsWith(question.correct_answer + ")", ignoreCase = true) || 
                            opt.startsWith(question.correct_answer + " ", ignoreCase = true) || 
                            opt.equals(question.correct_answer, ignoreCase = true)
            
            triggerAnswerHaptic(context, isCorrect)
            onOptionSelected(opt)
        }
    }

    val contentBox = @Composable {
        // Compute images first to avoid scope issues
        val imageUrls = remember(question.images) {
            if (!question.images.isNullOrBlank()) {
                question.images.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        }
        val questionImages = if (imageUrls.size > 1) imageUrls.take(1) else imageUrls
        val explanationImages = if (imageUrls.size > 1) imageUrls.drop(1) else emptyList()
        val options = question.options

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(scrollModifier)
                .clickable(
                    enabled = onCardTap != null,
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onCardTap?.invoke() }
                .onGloballyPositioned { parentCoordinates = it }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val questionTag = "q_${question.question}"
            val isQuestionPlaying = currentTag == questionTag && audioState == com.example.AudioState.PLAYING
            val isQuestionPaused = currentTag == questionTag && audioState == com.example.AudioState.PAUSED
            val isCurrentSessionActive = currentTag == questionTag

            if (!forceHideFrontContent) {
                // Beautiful header row combining "Question X of Y" (or custom header) AND action icons on the SAME horizontal line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: header text (e.g., Question X of Y, hands-free mic, timer)
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f, fill = false)) {
                    headerContent?.invoke()
                }

                // Right side: Audio (Speaker) and Bookmark action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isQuestionPlaying) {
                        AudioSpectrumVisualizer(
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    val textToRead = com.example.utils.stripHtmlForTts(question.question + ". \n" + question.options.joinToString(". "))
                    val speakerBgColor = if (isCurrentSessionActive && (isQuestionPlaying || isQuestionPaused)) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    }
                    androidx.compose.foundation.layout.Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(speakerBgColor)
                            .then(
                                if (isCurrentSessionActive && (isQuestionPlaying || isQuestionPaused)) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                } else Modifier
                            )
                            .clickable {
                                ttsManager?.togglePlayPause(
                                    tag = questionTag,
                                    text = textToRead,
                                    pitch = profileState.ttsPitch,
                                    speed = profileState.ttsSpeed
                                )
                            }
                    ) {
                        Icon(
                            imageVector = if (isQuestionPlaying) {
                                Icons.Default.Pause
                            } else if (isQuestionPaused) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isQuestionPlaying) {
                                "Pause"
                            } else if (isQuestionPaused) {
                                "Resume"
                            } else {
                                "Read Aloud"
                            },
                            tint = if (isCurrentSessionActive && (isQuestionPlaying || isQuestionPaused)) {
                                MaterialTheme.colorScheme.secondary
                            } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // High readability question text. Spans 100% full width of the card.
            val combinedHighlightRange = highlightRange ?: (if (currentTag == "q_${question.question}") activeTtsRange else null)
            val questionText = question.question
            val annotatedQuestion = androidx.compose.ui.text.buildAnnotatedString {
                append(questionText)
                if (combinedHighlightRange != null) {
                    val safeStart = combinedHighlightRange.first.coerceIn(0, questionText.length)
                    val safeEnd = combinedHighlightRange.last.coerceIn(safeStart, questionText.length)
                    if (safeStart < safeEnd) {
                        addStyle(
                            style = androidx.compose.ui.text.SpanStyle(
                                background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                color = MaterialTheme.colorScheme.primary
                            ),
                            start = safeStart,
                            end = safeEnd
                        )
                    }
                }
            }

            val clinicalFont = if (profileState.isClinicalMode) {
                if (profileState.clinicalFontFamily.equals("Serif", ignoreCase = true)) {
                    androidx.compose.ui.text.font.FontFamily.Serif
                } else {
                    androidx.compose.ui.text.font.FontFamily.SansSerif
                }
            } else {
                androidx.compose.ui.text.font.FontFamily.Default
            }

            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = annotatedQuestion,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        lineHeight = if (profileState.isClinicalMode) 32.sp else 28.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = clinicalFont,
                        fontSize = if (profileState.isClinicalMode) 20.sp else MaterialTheme.typography.headlineSmall.fontSize
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (profileState.isClinicalMode) 24.dp else 16.dp)
                )
            }

            // Images
            if (questionImages.isNotEmpty()) {
                val context = LocalContext.current
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    questionImages.forEach { url ->
                        val retryState = remember(url) { mutableStateOf(0) }
                        val retryCount = retryState.value
                        var isFailed by remember(url, retryCount) { mutableStateOf(false) }
                        if (!isFailed) {
                            val imageRequest = remember(url, context, retryCount) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(true)
                                    .memoryCacheKey("$url-$retryCount")
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build()
                            }
                            SubcomposeAsyncImage(
                                model = imageRequest,
                                contentDescription = "Question Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp, max = 500.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { fullScreenImageUrl = url },
                                loading = {
                                    ShimmerImagePlaceholder()
                                },
                                error = {
                                    LaunchedEffect(Unit) {
                                        isFailed = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.forEachIndexed { idx, option ->
                    key(option) {
                        val isCorrectAnswer = remember(option, question.correct_answer) {
                            option.startsWith(question.correct_answer + ".", ignoreCase = true) 
                                    || option.startsWith(question.correct_answer + ")", ignoreCase = true)
                                    || option.startsWith(question.correct_answer + " ", ignoreCase = true)
                                    || option.equals(question.correct_answer, ignoreCase = true)
                        }

                        val isSelected = selectedOption == option
                        val isDictated = activeSpokenOptionIndex == idx

                        McqOptionRow(
                            option = option,
                            isSelected = isSelected,
                            isCorrectAnswer = isCorrectAnswer,
                            showFeedback = showFeedback,
                            isRevealed = isRevealed,
                            isExamMode = isExamMode,
                            isDictated = isDictated,
                            onClick = { handleReveal(option) },
                            modifier = Modifier.onGloballyPositioned { childCoordinates ->
                                val pCoords = parentCoordinates
                                if (pCoords != null && pCoords.isAttached && childCoordinates.isAttached) {
                                    val position = pCoords.localPositionOf(childCoordinates, androidx.compose.ui.geometry.Offset.Zero)
                                    val absoluteY = position.y + scrollState.value
                                    itemYOffsets["option_$idx"] = absoluteY
                                }
                            }
                        )
                    }
                }
            }
            }

            // Explanation
            if (showFeedback && !forceHideExplanation) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { childCoordinates ->
                            val pCoords = parentCoordinates
                            if (pCoords != null && pCoords.isAttached && childCoordinates.isAttached) {
                                val position = pCoords.localPositionOf(childCoordinates, androidx.compose.ui.geometry.Offset.Zero)
                                val absoluteY = position.y + scrollState.value
                                itemYOffsets["explanation"] = absoluteY
                            }
                        }
                        .padding(top = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary, 
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (showAiExplanationMode) "AI Explanation" else "Explanation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isGeneratingAiExplanation) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            IconButton(onClick = {
                                if (!localAiExplanation.isNullOrBlank() && aiExplanationState !is AiState.Error) {
                                    showAiExplanationMode = !showAiExplanationMode
                                } else {
                                    showAiExplanationMode = true
                                    aiViewModel.generateAndSaveExplanationWithState(question)
                                }
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Toggle AI Explanation",
                                    tint = if (!localAiExplanation.isNullOrBlank() && showAiExplanationMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        val explanationTag = "e_${question.question}_${if(showAiExplanationMode) "ai" else "db"}"
                        val isExplanationPlaying = currentTag == explanationTag && audioState == com.example.AudioState.PLAYING
                        val isExplanationPaused = currentTag == explanationTag && audioState == com.example.AudioState.PAUSED
                        val isCurrentExpActive = currentTag == explanationTag
                        
                        val textToRead = if (showAiExplanationMode) {
                            com.example.utils.stripHtmlForTts(localAiExplanation ?: "Generating...")
                        } else {
                            com.example.utils.stripHtmlForTts(question.explanation ?: "No explanation available.")
                        }
                        
                        val expSpeakerBgColor = if (isCurrentExpActive && (isExplanationPlaying || isExplanationPaused)) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                        
                        if (isExplanationPlaying) {
                            AudioSpectrumVisualizer(
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        androidx.compose.foundation.layout.Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(expSpeakerBgColor)
                                .then(
                                    if (isCurrentExpActive && (isExplanationPlaying || isExplanationPaused)) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                    } else Modifier
                                )
                                .clickable {
                                    ttsManager?.togglePlayPause(
                                        tag = explanationTag,
                                        text = textToRead,
                                        pitch = profileState.ttsPitch,
                                        speed = profileState.ttsSpeed
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = if (isExplanationPlaying) {
                                    Icons.Default.Pause
                                } else if (isExplanationPaused) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isExplanationPlaying) "Pause" else "Read Explanation",
                                tint = if (isCurrentExpActive && (isExplanationPlaying || isExplanationPaused)) {
                                    MaterialTheme.colorScheme.secondary
                                } else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                                   Column(modifier = Modifier.padding(top = 4.dp)) {
                            if (explanationImages.isNotEmpty()) {
                                val context = LocalContext.current
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                                    explanationImages.forEach { url ->
                                        val retryState = remember(url) { mutableStateOf(0) }
                                        val retryCount = retryState.value
                                        var isFailed by remember(url, retryCount) { mutableStateOf(false) }
                                        if (!isFailed) {
                                            val imageRequest = remember(url, context, retryCount) {
                                                coil.request.ImageRequest.Builder(context)
                                                    .data(url)
                                                    .crossfade(true)
                                                    .memoryCacheKey("$url-$retryCount")
                                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .build()
                                            }
                                            SubcomposeAsyncImage(
                                                model = imageRequest,
                                                contentDescription = "Explanation Image",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 200.dp, max = 500.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { fullScreenImageUrl = url },
                                                loading = {
                                                    ShimmerImagePlaceholder()
                                                },
                                                error = {
                                                    LaunchedEffect(Unit) {
                                                        isFailed = true
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Crossfade(
                                targetState = showAiExplanationMode,
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            ) { isAi ->
                                if (isAi) {
                                    when (val state = aiExplanationState) {
                                        is AiState.Loading -> {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                LinearProgressIndicator(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                )
                                                Text(
                                                    "Consulting clinical guidelines...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                ShimmerExplanationSkeleton(modifier = Modifier.padding(top = 8.dp))
                                            }
                                        }
                                        is AiState.Error -> {
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(12.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Warning,
                                                        contentDescription = "Error",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        state.msg.takeIf { it.isNotBlank() } ?: "Connection dropped.",
                                                        modifier = Modifier.weight(1f),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    
                                                    androidx.compose.material3.TextButton(onClick = { aiViewModel.retryAiExplanation(question) }) {
                                                        Text("Retry")
                                                    }
                                                }
                                            }
                                        }
                                        is AiState.Success -> {
                                            if (state.text.isNotEmpty()) {
                                                androidx.compose.foundation.text.selection.SelectionContainer {
                                                    HtmlText(
                                                        html = state.text,
                                                        activeWord = null,
                                                        activeRange = if (currentTag == "e_${question.question}_ai") {
                                                            activeTtsRange
                                                        } else null,
                                                        onTapText = onCardTap
                                                    )
                                                }
                                            } else {
                                                Text("Failed to generate AI explanation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        else -> {
                                            if (!localAiExplanation.isNullOrBlank()) {
                                                androidx.compose.foundation.text.selection.SelectionContainer {
                                                    HtmlText(
                                                        html = localAiExplanation!!,
                                                        activeWord = null,
                                                        activeRange = if (currentTag == "e_${question.question}_ai") {
                                                            activeTtsRange
                                                        } else null,
                                                        onTapText = onCardTap
                                                    )
                                                }
                                            } else {
                                                Text("No AI solution generated yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                } else {
                                    val docExplanation = question.explanation
                                    if (!docExplanation.isNullOrBlank()) {
                                        androidx.compose.foundation.text.selection.SelectionContainer {
                                            HtmlText(
                                                html = docExplanation,
                                                activeWord = null,
                                                activeRange = if (currentTag == "e_${question.question}_db") {
                                                    activeTtsRange
                                                } else null,
                                                onTapText = onCardTap
                                            )
                                        }
                                    } else {
                                        Text("No official explanation provided.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }          
                            // Chat Messages
                            val chatMessages = appState.chatMessages[question.question] ?: emptyList()

                            if (chatMessages.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Deeper Concepts", 
                                    style = MaterialTheme.typography.labelMedium, 
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    chatMessages.forEach { msg ->
                                        val isUser = msg.sender == com.example.ChatSender.USER
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                        ) {
                                            androidx.compose.foundation.layout.Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.92f)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(
                                                        topStart = 16.dp, 
                                                        topEnd = 16.dp, 
                                                        bottomStart = if (isUser) 16.dp else 4.dp, 
                                                        bottomEnd = if (isUser) 4.dp else 16.dp
                                                    ))
                                                    .background(if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f))
                                                    .padding(14.dp)
                                            ) {
                                                if (msg.isGenerating && msg.text.isEmpty()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Thinking...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                } else {
                                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                                        HtmlText(html = msg.text)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Auto-scroll to the bottom of the screen when new messages arrive
                                LaunchedEffect(chatMessages.size, chatMessages.lastOrNull()?.text?.length) {
                                    if (chatMessages.isNotEmpty()) {
                                        kotlinx.coroutines.delay(100)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            }

                            if (isRevealed && !isExamMode && !forceHideExplanation) {
                                var chatInputText by remember(question.question) { mutableStateOf("") }
                                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                                var showMiniPills by remember(question.question) { mutableStateOf(false) }
                                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                                Spacer(modifier = Modifier.height(12.dp))

                                AnimatedVisibility(
                                    visible = showMiniPills,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Pill 1: 3 MCQ
                                        Surface(
                                            onClick = {
                                                viewModel.sendChatMessage(
                                                    question,
                                                    "Create 3 multiple choice questions (MCQ) about the topic of this question. After giving the 3 MCQs, provide the correct answers along with a clean, detailed explanation for each."
                                                )
                                                showMiniPills = false
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Psychology,
                                                    contentDescription = "3 MCQ",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "3 MCQ",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Pill 2: Topic Expl
                                        Surface(
                                            onClick = {
                                                viewModel.sendChatMessage(
                                                    question,
                                                    "Provide a clear, detailed, exam-oriented explanation of the clinical concept/topic discussed in this question."
                                                )
                                                showMiniPills = false
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lightbulb,
                                                    contentDescription = "Topic Expl",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Topic Expl",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Pill 3: Rapid FC
                                        Surface(
                                            onClick = {
                                                viewModel.sendChatMessage(
                                                    question,
                                                    "Create a single-page, highly condensed, compact 'Rapid Flashcard' summary of the high-yield information for the topic of this question."
                                                )
                                                showMiniPills = false
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = "Rapid FC",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.tertiary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Rapid FC",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .pointerInput(question) {
                                            detectTapGestures(
                                                onTap = {
                                                    focusRequester.requestFocus()
                                                },
                                                onDoubleTap = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showMiniPills = !showMiniPills
                                                },
                                                onLongPress = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showMiniPills = !showMiniPills
                                                }
                                            )
                                        },
                                    shape = RoundedCornerShape(32.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = chatInputText,
                                        onValueChange = { chatInputText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                            .focusRequester(focusRequester),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                        decorationBox = { innerTextField ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        showMiniPills = !showMiniPills
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = "Quick Concepts",
                                                        tint = if (showMiniPills) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                        modifier = Modifier.size(15.dp)
                                                     )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(modifier = Modifier.weight(1f)) {
                                                    if (chatInputText.isEmpty()) {
                                                        Text("Ask for deeper clinical concepts...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                    }
                                                    innerTextField()
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (chatInputText.isNotBlank()) {
                                                            viewModel.sendChatMessage(question, chatInputText)
                                                            chatInputText = ""
                                                            focusManager.clearFocus()
                                                        }
                                                    },
                                                    enabled = chatInputText.isNotBlank() && (chatMessages.isEmpty() || !chatMessages.last().isGenerating),
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send, 
                                                        contentDescription = "Send", 
                                                        tint = if (chatInputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        maxLines = 3,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

    if (fullScreenImageUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    
                    val context = LocalContext.current
                    val imageRequest = remember(fullScreenImageUrl, context) {
                        coil.request.ImageRequest.Builder(context)
                            .data(fullScreenImageUrl)
                            .crossfade(true)
                            .memoryCacheKey(fullScreenImageUrl)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                    }
                    
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Full Screen Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = if (scale > 1f) 1f else 2.5f
                                        offset = androidx.compose.ui.geometry.Offset.Zero
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                                detectTransformGestures { centroid, pan, zoom, _ ->
                                                    val oldScale = scale
                                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                                    // Scale pan based on zoom level to ensure smooth dragging
                                                    val fractionalPan = pan * (scale / oldScale)
                                                    offset += fractionalPan
                                                    if (scale <= 1f) {
                                                        offset = androidx.compose.ui.geometry.Offset.Zero
                                                    }
                                                }
                                            }
                                            .pointerInput(Unit) {
                                                var dragAmount = 0f
                                                detectVerticalDragGestures(
                                                    onDragStart = { _ -> dragAmount = 0f },
                                                    onDragEnd = {
                                                        if (dragAmount > 100.dp.toPx()) {
                                                            fullScreenImageUrl = null
                                                        }
                                                    },
                                                    onVerticalDrag = { _, amount ->
                                                        if (scale <= 1f) {
                                                            dragAmount += amount
                                                        }
                                                    }
                                                )
                                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                    
                    IconButton(
                        onClick = { fullScreenImageUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
            contentBox()
        }
    }
}

@Composable
fun CircularMetricsPanel(
    accuracyPercent: Int,
    correctCount: Int,
    wrongCount: Int,
    unansweredCount: Int,
    scorePercent: Int,
    isExamMode: Boolean,
    scoreAchieved: Float,
    maxScore: Int,
    avgSeconds: Float,
    onReattemptMistakes: (() -> Unit)?,
    onReturn: () -> Unit
) {
    WedikaCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Performance Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Circular Accuracy Center
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    val primaryColor = if (accuracyPercent >= 60) com.example.ui.designsystem.AppStatusColors.correct else com.example.ui.designsystem.AppStatusColors.incorrect
                    val trackColor = com.example.ui.designsystem.AppStatusColors.unanswered.copy(alpha = 0.2f)
                    val errorColor = com.example.ui.designsystem.AppStatusColors.incorrect
                    val correctPercent = accuracyPercent.toFloat()
                    val animatedSweep by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = (correctPercent / 100f) * 240f,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "accuracy_sweep"
                    )
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = trackColor,
                            startAngle = -210f,
                            sweepAngle = 240f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 10.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                        drawArc(
                            color = primaryColor,
                            startAngle = -210f,
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 10.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$accuracyPercent%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (accuracyPercent >= 60) primaryColor else errorColor
                        )
                        Text(
                            text = "Accuracy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Breakdown list
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricIndicatorRow(com.example.ui.designsystem.AppStatusColors.correct, "Correct", "$correctCount")
                    MetricIndicatorRow(com.example.ui.designsystem.AppStatusColors.incorrect, "Incorrect", "$wrongCount")
                    MetricIndicatorRow(MaterialTheme.colorScheme.outline, "Skipped", "$unansweredCount")
                    
                    val scoreText = if (scoreAchieved == scoreAchieved.toInt().toFloat()) "${scoreAchieved.toInt()}" else String.format("%.2f", scoreAchieved)
                    
                    MetricIndicatorRow(
                        color = MaterialTheme.colorScheme.primary,
                        label = "Net Score",
                        value = "$scoreText / $maxScore",
                        isHighlight = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isExamMode) "$scorePercent%" else String.format(java.util.Locale.US, "%.1fs/Q", avgSeconds),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isExamMode) "Exam Score" else "Avg Speed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun MetricIndicatorRow(color: Color, label: String, value: String, isHighlight: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SegmentedReviewFilter(
    selectedMode: String,
    onSelectedModeChange: (String) -> Unit,
    totalCount: Int,
    mistakesCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selectedMode == "All") MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { onSelectedModeChange("All") }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "All Questions ($totalCount)",
                color = if (selectedMode == "All") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selectedMode == "Wrong") MaterialTheme.colorScheme.error else Color.Transparent)
                .clickable { onSelectedModeChange("Wrong") }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "My Mistakes ($mistakesCount)",
                color = if (selectedMode == "Wrong") MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun InteractiveDeckReview(
    filteredQuestions: List<McqField>,
    selectedOptions: Map<String, String>,
    bookmarkedQuestionTexts: Set<String>,
    onToggleBookmark: (McqField) -> Unit
) {
    if (filteredQuestions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No questions match this filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var deckIndex by remember(filteredQuestions) { mutableStateOf(0) }
    deckIndex = deckIndex.coerceIn(0, maxOf(0, filteredQuestions.size - 1))
    val q = filteredQuestions[deckIndex]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mistake Review ${deckIndex + 1} of ${filteredQuestions.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(
                onClick = { onToggleBookmark(q) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (bookmarkedQuestionTexts.contains(q.question)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Toggle Bookmark",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        WedikaCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                if (!q.topic.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatTopicDisplayName(q.topic),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = q.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                val selectedOpt = selectedOptions[q.question]
                val correctPrefix = q.correct_answer.trim()

                q.options.forEach { rawOption ->
                    val option = rawOption.trim()
                    val isCorrect = option.startsWith(correctPrefix + ".", ignoreCase = true)
                            || option.startsWith(correctPrefix + ")", ignoreCase = true)
                            || option.startsWith(correctPrefix + " ", ignoreCase = true)
                            || option.equals(correctPrefix, ignoreCase = true)

                    val isSelected = selectedOpt != null && (
                            selectedOpt.startsWith(option, ignoreCase = true) ||
                            option.startsWith(selectedOpt, ignoreCase = true)
                    )

                    val bgColor = when {
                        isCorrect -> com.example.ui.designsystem.AppStatusColors.correct.copy(alpha = 0.2f)
                        isSelected -> com.example.ui.designsystem.AppStatusColors.incorrect.copy(alpha = 0.2f)
                        else -> com.example.ui.designsystem.AppStatusColors.unanswered.copy(alpha = 0.15f)
                    }

                    val borderColor = when {
                        isCorrect -> com.example.ui.designsystem.AppStatusColors.correct
                        isSelected -> com.example.ui.designsystem.AppStatusColors.incorrect
                        else -> Color.Transparent
                    }

                    val textColor = when {
                        isCorrect -> com.example.ui.designsystem.AppStatusColors.correct
                        isSelected -> com.example.ui.designsystem.AppStatusColors.incorrect
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isCorrect -> Icons.Default.CheckCircle
                                    isSelected -> Icons.Default.Cancel
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = when {
                                    isCorrect -> Color(0xFF2E7D32)
                                    isSelected -> Color(0xFFC62828)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                fontWeight = if (isCorrect || isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!q.explanation.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Explanation",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation Note",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = q.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (deckIndex > 0) deckIndex-- },
                enabled = deckIndex > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Previous")
            }

            Button(
                onClick = { if (deckIndex < filteredQuestions.size - 1) deckIndex++ },
                enabled = deckIndex < filteredQuestions.size - 1,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SessionReviewScreen(
    allQuestions: List<McqField>,
    appState: AppState,
    profileState: ProfileState,
    onReturn: () -> Unit,
    onReattemptMistakes: (List<McqField>) -> Unit,
    onToggleBookmark: (McqField) -> Unit
) {
    val selectedOptions = appState.selectedOptions
    val total = allQuestions.size
    val answeredCount = allQuestions.count { selectedOptions.containsKey(it.question) }
    val unansweredCount = total - answeredCount
    var correctCount = 0
    var wrongCount = 0
    
    val results = remember(allQuestions, selectedOptions) {
        allQuestions.associate { q ->
            val opt = selectedOptions[q.question]
            val isCorrect = if (opt != null) {
                opt.startsWith(q.correct_answer + ".", ignoreCase = true) 
                        || opt.startsWith(q.correct_answer + ")", ignoreCase = true)
                        || opt.startsWith(q.correct_answer + " ", ignoreCase = true)
                        || opt.equals(q.correct_answer, ignoreCase = true)
            } else null
            
            q.question to isCorrect
        }
    }
    
    results.values.forEach { 
        if (it == true) correctCount++
        else if (it == false) wrongCount++
    }
    
    val isExamMode = appState.examSettings.isExamMode
    val accuracyPercent = if (answeredCount > 0) (correctCount * 100) / answeredCount else 0
    val scoreAchieved = (correctCount * 4) - wrongCount
    val maxScore = total * 4
    val scorePercent = if (maxScore > 0) ((scoreAchieved.toFloat() / maxScore) * 100f).coerceAtLeast(0f).toInt() else 0
    
    val durationMs = if (appState.examSettings.sessionStartTimeMs > 0) System.currentTimeMillis() - appState.examSettings.sessionStartTimeMs else 0
    val avgTimeMs = if (answeredCount > 0) durationMs / answeredCount else 0
    val avgSeconds = avgTimeMs / 1000f
    
    var activeReviewFilter by rememberSaveable { mutableStateOf<String?>(null) }
    
    val isEditingReview = remember(appState.quizIndex, allQuestions) {
        allQuestions.isNotEmpty() && appState.quizIndex >= allQuestions.size
    }
    
    androidx.activity.compose.BackHandler(enabled = isEditingReview) {
        if (activeReviewFilter != null) {
            activeReviewFilter = null
        } else {
            onReturn()
        }
    }

    val wrongQuestions = remember(allQuestions, results) {
        allQuestions.filter { results[it.question] == false }
    }
    
    val bookmarkedQuestions = remember(allQuestions, profileState.bookmarkedQuestionTexts) {
        allQuestions.filter { profileState.bookmarkedQuestionTexts.contains(it.question) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (activeReviewFilter == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                CircularMetricsPanel(
                    accuracyPercent = accuracyPercent,
                    correctCount = correctCount,
                    wrongCount = wrongCount,
                    unansweredCount = unansweredCount,
                    scorePercent = scorePercent,
                    isExamMode = isExamMode,
                    scoreAchieved = scoreAchieved.toFloat(),
                    maxScore = maxScore,
                    avgSeconds = avgSeconds,
                    onReattemptMistakes = if (wrongQuestions.isNotEmpty()) {
                        { onReattemptMistakes(wrongQuestions) }
                    } else null,
                    onReturn = onReturn
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Review Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { activeReviewFilter = "All" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Whole Review\n(${allQuestions.size})", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        
                        FilledTonalButton(
                            onClick = { activeReviewFilter = "Mistakes" },
                            modifier = Modifier.weight(1f),
                            enabled = wrongQuestions.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (wrongQuestions.isNotEmpty()) MaterialTheme.colorScheme.errorContainer.copy(alpha=0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (wrongQuestions.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Mistakes\n(${wrongQuestions.size})", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    
                    FilledTonalButton(
                        onClick = { activeReviewFilter = "Bookmarked" },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = bookmarkedQuestions.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Bookmarked Only (${bookmarkedQuestions.size})")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onReturn,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("End Session")
                    }
                }
            }
        } else {
            val listToShow = when (activeReviewFilter) {
                "Mistakes" -> wrongQuestions
                "Bookmarked" -> bookmarkedQuestions
                else -> allQuestions
            }
            
            var reviewIndex by rememberSaveable(activeReviewFilter) { mutableStateOf(0) }
            val current = listToShow.getOrNull(reviewIndex)
            
            Column(modifier = Modifier.fillMaxSize()) {
                if (current != null) {
                    val answeredStatus = listToShow.mapIndexedNotNull { i, q -> 
                        val selected = selectedOptions[q.question]
                        if (selected != null) {
                            val opt = selected.trim()
                            val correct = q.correct_answer.trim()
                            val isCorrect = opt.startsWith(correct + ".", ignoreCase = true) ||
                                            opt.startsWith(correct + ")", ignoreCase = true) ||
                                            opt.startsWith(correct + " ", ignoreCase = true) ||
                                            opt.equals(correct, ignoreCase = true)
                            i to isCorrect
                        } else null
                    }.toMap()
                    McqQuizMode(
                        question = current,
                        index = reviewIndex,
                        total = listToShow.size,
                        selectedOption = selectedOptions[current.question],
                        isBookmarked = profileState.bookmarkedQuestionTexts.contains(current.question),
                        isExamMode = false,
                        onToggleBookmark = { onToggleBookmark(current) },
                        onOptionSelected = { },
                        onNext = { if (reviewIndex < listToShow.lastIndex) reviewIndex++ },
                        onPrev = { if (reviewIndex > 0) reviewIndex-- },
                        onJumpToQuestion = { reviewIndex = it.coerceIn(0, listToShow.lastIndex) },
                        onExitQuiz = { activeReviewFilter = null },
                        answeredStatus = answeredStatus,
                        isCardMode = appState.viewMode == "card",
                        isReviewMode = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartNotificationCenter(
    profileState: ProfileState,
    onNotificationAction: (AppNotification) -> Unit,
    onNotificationDismiss: (String) -> Unit,
    onRestoreDismissedAlerts: () -> Unit = {}
) {
    WedikaCard(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MEDIVA Diagnostic AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (profileState.notifications.isNotEmpty()) Color(0xFFD97706) else Color(0xFF059669)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (profileState.notifications.isNotEmpty()) 
                                    "${profileState.notifications.size} active alerts" 
                                    else "All diagnostics green",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notifications List
            if (profileState.notifications.isEmpty()) {
                WedikaCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🩺 Diagnostics Stable!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All indicators are green. Your studying pattern looks excellent! Keep practicing questions to stay on top.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        if (profileState.dismissedNotificationIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onRestoreDismissedAlerts) {
                                Text("Restore Dismissed Alerts", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profileState.notifications.forEach { notification ->
                        key(notification.id) {
                            NotificationItemRow(
                                notification = notification,
                                onAction = { onNotificationAction(notification) },
                                onDismiss = { onNotificationDismiss(notification.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun NotificationItemRow(
    notification: AppNotification,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val urgencyColor = when (notification.urgency) {
        NotificationUrgency.LOW -> Color(0xFF10B981)
        NotificationUrgency.MEDIUM -> Color(0xFFF59E0B)
        NotificationUrgency.HIGH -> Color(0xFFEF4444)
    }

    val iconValue = when (notification.category) {
        "goal" -> Icons.Default.CheckCircle
        "streak" -> Icons.Default.LocalFireDepartment
        "last_active" -> Icons.Default.Schedule
        "subject" -> Icons.Default.MedicalServices
        else -> Icons.Default.Info
    }

    WedikaCard(
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(urgencyColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = iconValue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Alert",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = notification.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            if (notification.actionLabel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = urgencyColor.copy(alpha = 0.12f),
                            contentColor = urgencyColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = notification.actionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomModulesCard(
    availableSubjectsCount: Int,
    onClick: () -> Unit
) {
    WedikaCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Custom Modules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

