package com.example

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable data object Intro : Screen()
    @Serializable data object Home : Screen()
    
    // New Navigation Engine Routes
    @Serializable data class Subject(val groupName: String? = null) : Screen()
    @Serializable data class Topic(val subjectId: String) : Screen()
    @Serializable data class Source(val subjectId: String, val topicId: String? = null) : Screen() // topics can be optional depending on how source is accessed
    @Serializable data class McqList(val subjectId: String? = null, val topicId: String? = null, val sourceId: String? = null, val query: String? = null) : Screen()
    @Serializable data class Session(val sessionId: String = "") : Screen()

    @Serializable data class Profile(val initialSheet: String? = null) : Screen()
    @Serializable data object Statistics : Screen()
    @Serializable data object Auth : Screen()
    @Serializable data object JsonUpload : Screen()
    @Serializable data object AiMilestones : Screen()
    @Serializable data object Bookmarks : Screen()
    @Serializable data class CustomModule(val initialSelectionJson: String? = null) : Screen()
    @Serializable data object Flashcards : Screen()
    @Serializable data object CustomList : Screen()
}

@Serializable
data class McqResponse(
    val status: String? = null,
    val saved_to: String? = null,
    val results: List<McqResult> = emptyList()
)

@Serializable
data class McqResult(
    val url: String? = null,
    val fields: List<McqField> = emptyList()
)

@Serializable
data class McqField(
    val subject: String? = null,
    val topic: String? = null,
    val question: String = "",
    val options: List<String> = emptyList(),
    val correct_answer: String = "",
    val explanation: String? = null,
    val ai_explanation: String? = null,
    val images: String? = null,
    val audio: String? = null,
    val source_url: String? = null
)

enum class AppThemeMode(val title: String) {
    SYSTEM("System Theme"),
    LIGHT("Sandal Light"),
    DARK("Sandal Dark"),
    NORD("Nord Calm"),
    OCEAN("Deep Ocean"),
    SEPIA("Sepia Reading"),
    AMOLED("Pure Black"),
    CLINICAL("Clinical White"),
    SLATE("Midnight Slate")
}

@Serializable
enum class NotificationUrgency {
    LOW, MEDIUM, HIGH
}

@Serializable
data class AppNotification(
    val id: String,
    val title: String,
    val text: String,
    val category: String, // "goal", "streak", "last_active", "subject", "general"
    val urgency: NotificationUrgency = NotificationUrgency.MEDIUM,
    val actionLabel: String? = null,
    val actionType: String? = null, // "practice_subject", "set_goal", "see_stats", "view_bookmarks", "reset_progress"
    val actionParam: String? = null
)

data class BookmarkUiState(
    val bookmarkedQuestions: List<McqField> = emptyList(),
    val bookmarkedQuestionTexts: Set<String> = emptySet(),
    val bookmarkCount: Int = 0
)

sealed class McqUiState {
    data object Loading : McqUiState()
    data class Success(val questions: List<McqField>) : McqUiState()
    data class Error(val message: String) : McqUiState()
}

data class StudyStats(
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
    val accuracyPercentage: Int = 0
)

data class TopicStats(
    val total: Int = 0,
    val finished: Int = 0
)

data class ExamSettings(
    val isExamMode: Boolean = false,
    val questionCount: Int = 0,
    val timeLimitMinutes: Int = 0,
    val sessionStartTimeMs: Long = 0L,
    val randomizeQuestions: Boolean = false,
    val practiceTimerLimitSec: Int = 60,
    val secondsPerQuestion: Int = 0
)

fun formatSourceDisplayName(source: String?): String {
    if (source.isNullOrEmpty()) return "Unknown Source"
    if (source == "Legacy / Untagged" || source == "Quick Prep Mocks / Legacy") return "Quick Prep Mocks / Legacy"
    
    return try {
        val clean = source.substringAfterLast('/').substringBefore('?').substringBefore('#')
        val name = clean.ifBlank { source.substringAfter("://").substringBefore('/') }.ifBlank { source }
        val nameWithoutSuffix = name.replace(Regex("(?i)\\.(json|csv|txt|pdf|xml)$"), "")
        val decoded = java.net.URLDecoder.decode(nameWithoutSuffix, "UTF-8").replace(Regex("[_\\-]"), " ")
        decoded.split(' ').joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }.trim().ifBlank { source }
    } catch (e: Exception) {
        source
    }
}

@Serializable
data class McqCollectionItem(
    val id: String,
    val name: String,
    val sourceProvider: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val setCounts: Int = 0,
    val totalQuestions: Int = 0
)

@Serializable
data class McqSectionItem(
    val id: String,
    val collectionId: String,
    val name: String,
    val sectionType: String? = "subject",
    val displayOrder: Int = 0
)

@Serializable
data class McqSetItem(
    val id: String,
    val collectionId: String,
    val sectionId: String? = null,
    val sourceSetId: String? = null,
    val title: String,
    val setType: String = "PRACTICE", // "PRACTICE" or "TEST"
    val subject: String? = null,
    val topic: String? = null,
    val questionCount: Int = 0,
    val durationMinutes: Int? = null,
    val totalMarks: Int? = null,
    val displayOrder: Int = 0
)

data class McqSetWithDetails(
    val setItem: McqSetItem,
    val sectionName: String? = null,
    val collectionName: String = ""
)

@Serializable
data class SetSummaryPreview(
    val setId: String,
    val title: String,
    val sectionName: String? = null,
    val setType: String = "PRACTICE",
    val questionCount: Int = 0,
    val durationMinutes: Int? = null
)

@Serializable
data class ImportPreviewReport(
    val collectionName: String,
    val collectionsCount: Int = 1,
    val sectionsCount: Int = 0,
    val setsCount: Int = 0,
    val totalQuestions: Int = 0,
    val setSummaries: List<SetSummaryPreview> = emptyList(),
    val warnings: List<String> = emptyList()
)


