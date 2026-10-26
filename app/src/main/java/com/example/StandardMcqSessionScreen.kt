package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

@Composable
fun StandardMcqSessionScreen(
    viewModel: McqViewModel,
    profileViewModel: ProfileViewModel,
    appState: AppState,
    profileState: ProfileState,
    onNavigateBack: () -> Unit
) {
    val bookmarkState by viewModel.bookmarkUiState.collectAsStateWithLifecycle()
    val questions = (appState.uiState as? McqUiState.Success)?.questions ?: emptyList()

    if (appState.uiState is McqUiState.Loading) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    } else if (questions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "No Questions",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Questions Available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please check your filter criteria or sync the database to download standard question libraries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { 
                    onNavigateBack()
                }
            ) {
                Text("Reset Filters")
            }
        }
    } else if (appState.viewMode == "list") {
        McqListMode(
            questions = questions,
            selectedOptions = appState.selectedOptions,
            bookmarkedQuestionTexts = bookmarkState.bookmarkedQuestionTexts,
            onToggleBookmark = viewModel::toggleBookmark,
            onOptionSelected = viewModel::selectOption
        )
    } else {
        val index = appState.quizIndex.coerceIn(0, questions.size)
        if (index < questions.size) {
            val context = LocalContext.current
            LaunchedEffect(index, questions) {
                val preloadIndices = listOf(index + 1, index + 2, index + 3)
                preloadIndices.forEach { preloadIdx ->
                    if (preloadIdx < questions.size) {
                        val q = questions[preloadIdx]
                        if (!q.images.isNullOrBlank()) {
                            val urls = q.images.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            urls.forEach { url ->
                                val request = coil.request.ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCacheKey(url)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build()
                                coil.Coil.imageLoader(context).enqueue(request)
                            }
                        }
                    }
                }
            }

            val current = questions[index]
            val ttsManager = com.example.LocalTtsManager.current
            
            LaunchedEffect(current.question) {
                ttsManager?.stop()
            }

            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                val currentSelectedOpt = appState.selectedOptions[current.question]

                val answeredCount = questions.count { q -> appState.selectedOptions.containsKey(q.question) }
                val answeredStatus = questions.mapIndexedNotNull { i, q -> 
                    val selected = appState.selectedOptions[q.question]
                    if (selected != null) {
                        val isCorrect = if (appState.examSettings.isExamMode) null else {
                            val opt = selected.trim()
                            val correct = q.correct_answer.trim()
                            opt.startsWith(correct + ".", ignoreCase = true) ||
                            opt.startsWith(correct + ")", ignoreCase = true) ||
                            opt.startsWith(correct + " ", ignoreCase = true) ||
                            opt.equals(correct, ignoreCase = true)
                        }
                        i to isCorrect
                    } else null
                }.toMap()

                McqQuizMode(
                    question = current,
                    index = index,
                    total = questions.size,
                    selectedOption = currentSelectedOpt,
                    isBookmarked = bookmarkState.bookmarkedQuestionTexts.contains(current.question),
                    isExamMode = appState.examSettings.isExamMode,
                    sessionStartTimeMs = appState.examSettings.sessionStartTimeMs,
                    timeLimitMinutes = appState.examSettings.timeLimitMinutes,
                    secondsPerQuestion = appState.examSettings.secondsPerQuestion,
                    onToggleBookmark = { viewModel.toggleBookmark(current) },
                    onOptionSelected = { viewModel.selectOption(current.question, it) },
                    onNext = viewModel::nextQuizQuestion,
                    onPrev = viewModel::previousQuizQuestion,
                    onJumpToQuestion = viewModel::jumpToQuestion,
                    onEndExamEarly = viewModel::finishQuizEarly,
                    onExitQuiz = {
                        onNavigateBack()
                    },
                    answeredCount = answeredCount,
                    answeredStatus = answeredStatus,
                    isCardMode = appState.viewMode == "card"
                )
            }
        } else {
            SessionReviewScreen(
                allQuestions = questions,
                appState = appState,
                profileState = profileState,
                onReturn = {
                    viewModel.clearActiveSessionBackup()
                    onNavigateBack()
                },
                onReattemptMistakes = viewModel::startReattemptSession,
                onToggleBookmark = viewModel::toggleBookmark
            )
        }
    }
}
