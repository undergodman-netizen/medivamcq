package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.designsystem.AppStatusColors
import com.example.ui.designsystem.WedikaCard

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: McqViewModel,
    profileViewModel: ProfileViewModel,
    appState: AppState,
    profileState: ProfileState,
    onStartReview: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val bookmarkState by viewModel.bookmarkUiState.collectAsStateWithLifecycle()
    val bookmarkedQuestions = bookmarkState.bookmarkedQuestions

    // Group bookmarks by subject
    val subjectCounts = remember(bookmarkedQuestions) {
        bookmarkedQuestions.groupBy { it.subject ?: "Other" }.mapValues { it.value.size }
    }

    var selectedSubject by remember { mutableStateOf<String?>("All") }
    var isShuffled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (bookmarkedQuestions.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearBookmarksBySubject(null) }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Bookmarks", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (bookmarkedQuestions.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text("Shuffle Questions", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = isShuffled, onCheckedChange = { isShuffled = it })
                        }
                        
                        Button(
                            onClick = {
                                val subjectToReview = if (selectedSubject == "All") null else selectedSubject
                                viewModel.startCustomQuiz(
                                    subject = subjectToReview,
                                    keyword = "",
                                    isBookmarked = true,
                                    status = 0,
                                    shuffle = isShuffled,
                                    navigateToQuiz = onStartReview
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Start Review (${if (selectedSubject == "All") bookmarkedQuestions.size else subjectCounts[selectedSubject] ?: 0})",
                                fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (bookmarkedQuestions.isEmpty()) {
                com.example.ui.components.EmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = "No Bookmarks Yet",
                    subtitle = "Questions you bookmark will appear here for review.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Subjects Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    item {
                        SubjectBookmarkCard(
                            subjectName = "All Bookmarks",
                            count = bookmarkedQuestions.size,
                            isSelected = selectedSubject == "All",
                            onClick = { selectedSubject = "All" },
                            onClear = { viewModel.clearBookmarksBySubject(null) }
                        )
                    }

                    items(subjectCounts.keys.sorted()) { subject ->
                        SubjectBookmarkCard(
                            subjectName = subject,
                            count = subjectCounts[subject] ?: 0,
                            isSelected = selectedSubject == subject,
                            onClick = { selectedSubject = subject },
                            onClear = {
                                viewModel.clearBookmarksBySubject(subject)
                                if (selectedSubject == subject) {
                                    selectedSubject = "All"
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectBookmarkCard(
    subjectName: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    WedikaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (subjectName == "All Bookmarks") Icons.Default.Language else Icons.Default.Folder,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count questions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isSelected || subjectName != "All Bookmarks") {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Bookmarks for this subject",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
