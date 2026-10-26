package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileState(
    val bookmarkedQuestions: List<McqField> = emptyList(),
    val bookmarkedQuestionTexts: Set<String> = emptySet(),
    val categorySummary: Map<String, Map<String, TopicStats>> = emptyMap(),
    val sourceSummary: Map<String, Int> = emptyMap(),
    val streakCount: Int = 0,
    val answersToday: Int = 0,
    val lastActiveTime: Long = 0L,
    val displayName: String = "",
    val email: String = "",
    val dailyGoal: Int = 10,
    val customTheme: AppThemeMode = AppThemeMode.LIGHT,
    val isClinicalMode: Boolean = false,
    val clinicalFontFamily: String = "Sans",
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.5-flash-lite",
    val geminiPrompt: String = "",
    val geminiCorePrompt: String = "",
    val geminiImagePrompt: String = "",
    val ttsPitch: Float = 1.0f,
    val ttsSpeed: Float = 1.0f,
    val availableTtsVoices: List<TtsVoiceOption> = emptyList(),
    val selectedTtsVoiceName: String? = null,
    val notifications: List<AppNotification> = emptyList(),
    val dismissedNotificationIds: Set<String> = emptySet(),
    val simStreak: Int? = null,
    val simHoursAgo: Int? = null,
    val simAnswersToday: Int? = null,
    val simPharmaAccuracy: Int? = null,
    val simLowestSubject: String? = null,
    val studyStats: StudyStats = StudyStats(),
    val isSubscribed: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MyApplication).container
    private val repository = container.mcqRepository
    private val settingsRepository = container.settingsRepository
    private val statsRepository = container.statsRepository

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadProfile()
            observeBookmarks()
            observeStatistics()
            observeSubscription()
        }
    }
    
    private fun observeSubscription() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            val isSub = snapshot?.getBoolean("isSubscribed") ?: false
            _state.update { it.copy(isSubscribed = isSub) }
        }
    }

    private fun loadProfile() {
        val today = com.example.utils.DateUtils.getTodayDateString()
        val yesterday = com.example.utils.DateUtils.getYesterdayDateString()
        val lastDate = statsRepository.getLastAnsweredDate()
        
        var currentStreak = statsRepository.getStreakCount()
        var currentAnswers = statsRepository.getAnswersToday()
        
        if (lastDate != today && lastDate != yesterday && lastDate.isNotEmpty()) {
            currentStreak = 0
            currentAnswers = 0
            statsRepository.saveStreakCount(0)
            statsRepository.saveAnswersToday(0)
        } else if (lastDate != today) {
            currentAnswers = 0
            statsRepository.saveAnswersToday(0)
        }

        val lastActive = statsRepository.getLastActiveTime()
        val dismissedStr = settingsRepository.prefs.getString("dismissedNotifications", "") ?: ""
        val dismissed = if (dismissedStr.isBlank()) emptySet() else dismissedStr.split(",").toSet()
        
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentDisplayName = firebaseUser?.displayName ?: settingsRepository.getDisplayName()
        val currentEmail = firebaseUser?.email ?: settingsRepository.getEmail()
        
        _state.update {
            it.copy(
                displayName = currentDisplayName,
                email = currentEmail,
                dailyGoal = settingsRepository.getDailyGoal(),
                customTheme = settingsRepository.getCustomTheme(),
                isClinicalMode = settingsRepository.isClinicalMode(),
                clinicalFontFamily = settingsRepository.getClinicalFontFamily(),
                streakCount = currentStreak,
                answersToday = currentAnswers,
                lastActiveTime = if (lastActive == 0L) System.currentTimeMillis() else lastActive,
                geminiApiKey = settingsRepository.getGeminiApiKey(),
                geminiModel = settingsRepository.getGeminiModel(),
                geminiPrompt = settingsRepository.getGeminiPrompt(),
                geminiCorePrompt = settingsRepository.getGeminiCorePrompt(),
                geminiImagePrompt = settingsRepository.getGeminiImagePrompt(),
                ttsPitch = settingsRepository.getTtsPitch(),
                ttsSpeed = settingsRepository.getTtsSpeed(),
                selectedTtsVoiceName = settingsRepository.getTtsVoiceName(),
                dismissedNotificationIds = dismissed
            )
        }
        rebuildNotifications()
    }

    private fun observeBookmarks() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getAllBookmarks().collect { fields ->
                _state.update { 
                    it.copy(
                        bookmarkedQuestions = fields,
                        bookmarkedQuestionTexts = fields.map { b -> b.question }.toSet()
                    )
                }
                rebuildNotifications()
            }
        }
    }

    private fun observeStatistics() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                repository.getSubjectTopicCounts().distinctUntilChanged(),
                repository.getAllProgress().distinctUntilChanged()
            ) { counts, progress ->
                val progressGrouped = progress.groupBy {
                    val s = if (it.subject.isNullOrBlank()) "General" else it.subject
                    val t = if (it.topic.isNullOrBlank()) "Untracked" else it.topic
                    s to t
                }
                val catSum = mutableMapOf<String, MutableMap<String, TopicStats>>()
                counts.forEach { c ->
                    val s = if (c.subject.isNullOrBlank()) "General" else c.subject
                    val t = if (c.topic.isNullOrBlank()) "Untracked" else c.topic
                    if (!catSum.containsKey(s)) catSum[s] = mutableMapOf()
                    val finishedCount = progressGrouped[s to t]?.size ?: 0
                    catSum[s]!![t] = TopicStats(total = c.count, finished = finishedCount)
                }
                val totalStr = progress.size
                val correctStr = progress.count { it.isCorrect }
                val accuracyStr = if (totalStr > 0) (correctStr * 100) / totalStr else 0
                val calculatedStudyStats = StudyStats(totalAnswered = totalStr, correctCount = correctStr, accuracyPercentage = accuracyStr)
                
                catSum to calculatedStudyStats
            }.collect { (catSum, studyStats) ->
                _state.update { it.copy(categorySummary = catSum, studyStats = studyStats) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.getSourceCounts().collect { counts ->
                val srcSum = counts.associate { (it.sourceUrl ?: "Legacy / Untagged") to it.count }
                _state.update { it.copy(sourceSummary = srcSum) }
            }
        }
    }

    // Toggle Bookmark
    fun toggleBookmark(question: McqField) {
        viewModelScope.launch(Dispatchers.IO) {
            val isBookmarked = _state.value.bookmarkedQuestionTexts.contains(question.question)
            repository.toggleBookmark(question, !isBookmarked)
        }
    }

    // Settings
    fun updateProfile(name: String, email: String) {
        settingsRepository.saveProfile(name, email)
        _state.update { it.copy(displayName = name, email = email) }
    }

    fun setDailyGoal(goal: Int) {
        settingsRepository.saveDailyGoal(goal)
        _state.update { it.copy(dailyGoal = goal) }
        rebuildNotifications()
    }

    fun setThemeMode(theme: AppThemeMode) {
        settingsRepository.saveTheme(theme)
        _state.update { it.copy(customTheme = theme) }
    }

    fun setClinicalMode(enabled: Boolean) {
        settingsRepository.setClinicalMode(enabled)
        _state.update { it.copy(isClinicalMode = enabled) }
    }

    fun setClinicalFontFamily(fontFamily: String) {
        settingsRepository.setClinicalFontFamily(fontFamily)
        _state.update { it.copy(clinicalFontFamily = fontFamily) }
    }

    fun setGeminiApiKey(key: String) {
        settingsRepository.saveGeminiApiKey(key)
        _state.update { it.copy(geminiApiKey = key) }
    }

    fun setGeminiModel(model: String) {
        settingsRepository.saveGeminiModel(model)
        _state.update { it.copy(geminiModel = model) }
    }

    fun setGeminiPrompt(prompt: String) {
        settingsRepository.saveGeminiPrompt(prompt)
        _state.update { it.copy(geminiPrompt = prompt) }
    }

    fun setGeminiCorePrompt(prompt: String) {
        settingsRepository.saveGeminiCorePrompt(prompt)
        _state.update { it.copy(geminiCorePrompt = prompt) }
    }

    fun setGeminiImagePrompt(prompt: String) {
        settingsRepository.saveGeminiImagePrompt(prompt)
        _state.update { it.copy(geminiImagePrompt = prompt) }
    }

    fun updateTtsSettings(pitch: Float, speed: Float) {
        settingsRepository.prefs.edit().putFloat("ttsPitch", pitch).putFloat("ttsSpeed", speed).apply()
        _state.update { it.copy(ttsPitch = pitch, ttsSpeed = speed) }
    }

    fun setSelectedTtsVoice(voiceName: String?, ttsManager: TtsManager? = null) {
        settingsRepository.saveTtsVoiceName(voiceName)
        _state.update { it.copy(selectedTtsVoiceName = voiceName) }
        if (voiceName == null) {
            ttsManager?.reapplyAutoVoice()
        } else {
            ttsManager?.setCustomVoice(voiceName)
        }
    }

    fun refreshAvailableVoices(ttsManager: TtsManager) {
        val voices = ttsManager.listEnglishVoices()
        _state.update { it.copy(availableTtsVoices = voices) }
    }

    // Simulation / Notifications
    private fun formatRelativeTime(lastActiveTime: Long, simHoursAgo: Int?): String {
        return if (simHoursAgo != null) {
            if (simHoursAgo >= 24) "${simHoursAgo / 24} hours ago"
            else "$simHoursAgo hours ago"
        } else {
            val h = (System.currentTimeMillis() - lastActiveTime) / 3600000
            "$h hours ago"
        }
    }

    fun setSimulationOverrides(
        streak: Int? = null,
        hoursAgo: Int? = null,
        answersToday: Int? = null,
        pharmaAccuracy: Int? = null,
        lowestSubject: String? = null
    ) {
        _state.update {
            it.copy(
                simStreak = streak,
                simHoursAgo = hoursAgo,
                simAnswersToday = answersToday,
                simPharmaAccuracy = pharmaAccuracy,
                simLowestSubject = lowestSubject
            )
        }
        rebuildNotifications()
    }

    fun resetSimulationOverrides() {
        setSimulationOverrides(null, null, null, null, null)
    }

    fun rebuildNotifications() {
        val current = _state.value
        val list = mutableListOf<AppNotification>()

        val streak = current.streakCount
        val solved = current.answersToday
        val goal = current.dailyGoal
        val lastActiveTime = current.lastActiveTime

        // 1. Goal Notification
        if (solved >= goal) {
            list.add(
                AppNotification(
                    id = "goal_status",
                    title = "Daily Goal Achieved! 🎉",
                    text = "Fantastic job! You've completed $solved/$goal MCQs today. Keep it up!",
                    category = "goal",
                    urgency = NotificationUrgency.LOW,
                    actionLabel = "See Analytics",
                    actionType = "see_stats"
                )
            )
        } else {
            val remaining = goal - solved
            list.add(
                AppNotification(
                    id = "goal_status",
                    title = "Daily Goal Progress ($solved/$goal) 🩺",
                    text = "You are $remaining MCQs away from your daily goal. Consistent practice is key to clinical mastery!",
                    category = "goal",
                    urgency = if (solved > 0) NotificationUrgency.MEDIUM else NotificationUrgency.HIGH,
                    actionLabel = "Start Studying",
                    actionType = "practice_subject"
                )
            )
        }

        // 2. Streak Notification
        if (streak > 0) {
            list.add(
                AppNotification(
                    id = "streak_status",
                    title = "Study Streak Active! 🔥",
                    text = "You're on a $streak-day study streak. Keep the momentum going!",
                    category = "streak",
                    urgency = NotificationUrgency.LOW,
                    actionLabel = "Keep Building",
                    actionType = "practice_subject"
                )
            )
        } else {
            list.add(
                AppNotification(
                    id = "streak_status",
                    title = "Start Your Streak! 📈",
                    text = "A blank slate is a perfect opportunity. Answer questions today to start your streak!",
                    category = "streak",
                    urgency = NotificationUrgency.MEDIUM,
                    actionLabel = "Practice Now",
                    actionType = "practice_subject"
                )
            )
        }

        // 3. Time status (if active in past/recent)
        if (lastActiveTime > 0L) {
            val rTime = formatRelativeTime(lastActiveTime, null)
            list.add(
                AppNotification(
                    id = "time_status",
                    title = "Last Activity",
                    text = "Your last practice session was $rTime.",
                    category = "last_active",
                    urgency = NotificationUrgency.LOW,
                    actionLabel = "Resume Study",
                    actionType = "practice_subject"
                )
            )
        }

        // 4. Bookmarks tracker (if any exists)
        val bookmarksCount = current.bookmarkedQuestionTexts.size
        if (bookmarksCount > 0) {
            list.add(
                AppNotification(
                    id = "bookmarks_tracker",
                    title = "Review Bookmarked Questions 📚",
                    text = "You have $bookmarksCount important medical concepts saved for active recall.",
                    category = "general",
                    urgency = NotificationUrgency.LOW,
                    actionLabel = "Review Bookmarks",
                    actionType = "view_bookmarks"
                )
            )
        }

        val finalNotifications = list.filter { it.id !in current.dismissedNotificationIds }
        _state.update { it.copy(notifications = finalNotifications) }
    }

    fun dismissNotification(id: String) {
        val s = _state.value
        val updatedDismissed = s.dismissedNotificationIds + id
        settingsRepository.prefs.edit().putString("dismissedNotifications", updatedDismissed.joinToString(",")).apply()
        _state.update { it.copy(dismissedNotificationIds = updatedDismissed) }
        rebuildNotifications()
    }

    fun clearDismissedNotifications() {
        settingsRepository.prefs.edit().putString("dismissedNotifications", "").apply()
        _state.update { it.copy(dismissedNotificationIds = emptySet()) }
        rebuildNotifications()
    }

    fun handleNotificationAction(
        notification: AppNotification,
        onNavigateToProfile: () -> Unit,
        onNavigateToStats: () -> Unit
    ) {
        dismissNotification(notification.id)
        when (notification.actionLabel) {
            "View Stats" -> onNavigateToStats()
            "View Profile" -> onNavigateToProfile()
            else -> onNavigateToStats()
        }
    }

    fun saveGeminiSettings(key: String, model: String, prompt: String, corePrompt: String = "", imagePrompt: String = "") {
        setGeminiApiKey(key)
        setGeminiModel(model)
        setGeminiPrompt(prompt)
        setGeminiCorePrompt(corePrompt)
        if (imagePrompt.isNotEmpty()) setGeminiImagePrompt(imagePrompt)
    }

    fun saveTtsSettings(pitch: Float, speed: Float) {
        updateTtsSettings(pitch, speed)
    }

    fun saveCustomTheme(theme: AppThemeMode) {
        setThemeMode(theme)
    }

    fun saveProfile(name: String, email: String) {
        updateProfile(name, email)
    }

    fun reloadProfile() {
        loadProfile()
    }
}
