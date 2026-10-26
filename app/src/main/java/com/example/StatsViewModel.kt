package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.local.AppDatabase
import com.example.data.McqRepository
import com.example.data.StatsRepository
import com.example.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StatsUiState(
    val totalAttempted: Int = 0,
    val totalCorrect: Int = 0,
    val overallAccuracy: Float = 0f,
    val completionPercentage: Float = 0f,
    val streakCount: Int = 0,
    val answersToday: Int = 0,
    val dailyGoal: Int = 10,
    val computedTopicStats: List<TopicStat> = emptyList(),
    val performanceSummary: String = "",
    val totalQuestionsCount: Int = 0
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MyApplication).container
    private val repository = container.mcqRepository
    private val statsRepository = container.statsRepository
    private val settingsRepository = container.settingsRepository

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val totalQuestions = repository.getQuestionsCountSync()
            
            repository.getAllProgress()
                .distinctUntilChanged()
                .collect { progressList ->
                    val goal = settingsRepository.getDailyGoal()
                    val attempted = progressList.size
                    val correct = progressList.count { it.isCorrect }
                    val overallAcc = if (attempted > 0) (correct.toFloat() / attempted) * 100 else 0f

                    // completion % based on unique answered questions
                    val uniqueAttempted = progressList.map { it.question }.distinct().size
                    val completionPercent = if (totalQuestions > 0) (uniqueAttempted.toFloat() / totalQuestions) * 100 else 0f

                    // Topic Stats calculation
                    val topicStatsMap = mutableMapOf<String, TopicStat>()
                    for (progress in progressList) {
                        val sSubject = progress.subject ?: "Unknown"
                        val sTopic = progress.topic ?: "Unknown"
                        val key = "${sSubject}|${sTopic}"
                        
                        val current = topicStatsMap[key] ?: TopicStat(sSubject, sTopic, 0, 0, 0f)
                        topicStatsMap[key] = current.copy(
                            attempted = current.attempted + 1,
                            correct = current.correct + (if (progress.isCorrect) 1 else 0)
                        )
                    }

                    val computed = topicStatsMap.values.map {
                        it.copy(accuracy = if (it.attempted > 0) (it.correct.toFloat() / it.attempted) * 100 else 0f)
                    }.sortedByDescending { it.attempted }

                    // Streak calculations & answers today from stats repository
                    val streak = statsRepository.getStreakCount()
                    val answers = statsRepository.getAnswersToday()

                    // Performance Summary
                    val perfSummary = when {
                        attempted == 0 -> "Blank Slate! Jump into your first quiz to kickstart clinical analytics."
                        overallAcc >= 85f -> "Master Class! Your diagnostic accuracy is outstanding (${String.format(java.util.Locale.US, "%.1f", overallAcc)}%). Focus on maintaining your active streak."
                        overallAcc >= 65f -> "Strong Foundation! You're making steady progress. Consider reviewing your incorrect answers in bookmarks."
                        else -> "Keep Practicing! Consistent daily study is the key to medical mastery. Focus on your weaker topics."
                    }

                    _uiState.update {
                        StatsUiState(
                            totalAttempted = attempted,
                            totalCorrect = correct,
                            overallAccuracy = overallAcc,
                            completionPercentage = completionPercent,
                            streakCount = streak,
                            answersToday = answers,
                            dailyGoal = goal,
                            computedTopicStats = computed,
                            performanceSummary = perfSummary,
                            totalQuestionsCount = totalQuestions
                        )
                    }
                }
        }
    }

    fun refreshStreakAndToday() {
        viewModelScope.launch(Dispatchers.IO) {
            val streak = statsRepository.getStreakCount()
            val answers = statsRepository.getAnswersToday()
            _uiState.update {
                it.copy(
                    streakCount = streak,
                    answersToday = answers
                )
            }
        }
    }
}
