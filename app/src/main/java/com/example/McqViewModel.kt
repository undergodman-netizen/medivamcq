package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.serialization.json.*
// Firebase imports removed

val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

data class SessionConfig(
    val subject: String? = null,
    val topic: String? = null,
    val source: String? = null,
    val customModules: Map<String, Set<String>> = emptyMap()
)

data class AppState(
    val uiState: McqUiState = McqUiState.Loading,
    val searchQuery: String = "",
    val viewMode: String = "quiz",
    val quizIndex: Int = 0,
    val filterMode: String = "subject", // "subject" or "source"
    val selectedOptions: Map<String, String> = emptyMap(),
    val examSettings: ExamSettings = ExamSettings(),
    val progressHistory: List<com.example.local.ProgressEntity> = emptyList(),
    val totalQuestionCount: Int = 0,
    val isSearchVisible: Boolean = false,
    val dashboardSearchQuery: String = "",
    val isDashboardSearchVisible: Boolean = false,
    val isLoggedIn: Boolean = true,
    val isFirebaseEnabled: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val importProgress: Float? = null,
    val importDiagnostics: List<String>? = null,
    val chatMessages: Map<String, List<ChatMessage>> = emptyMap(),
    val plainChatHistories: Map<String, String> = emptyMap(),
    val isDownloadingDb: Boolean = false,
    val hasResumableSession: Boolean = false,
    val isQuizActive: Boolean = false,
    val sourceTopics: Map<String, Map<String, TopicStats>> = emptyMap(),
    val activeSessionConfig: SessionConfig? = null
) {
    val isSessionMode: Boolean
        get() = examSettings.isExamMode || (activeSessionConfig?.customModules?.isNotEmpty() == true) || examSettings.questionCount > 0
}

class McqViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MyApplication).container
    private val repository = container.mcqRepository
    private val settingsRepository = container.settingsRepository
    private val statsRepository = container.statsRepository
    private val fileRepository = container.fileRepository
    private val firestoreSyncManager = container.firestoreSyncManager
    private val syncRepository = container.syncRepository

    fun downloadAndImportDbFromCloud(context: android.content.Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (_state.value.isDownloadingDb) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isDownloadingDb = true, importProgress = 0f, importDiagnostics = null) }
            
            val success = syncRepository.downloadAndImportDbFromCloud(context) { progress, diagnostics ->
                _state.update { 
                    it.copy(
                        importProgress = progress,
                        importDiagnostics = diagnostics ?: it.importDiagnostics
                    ) 
                }
            }
            
            _state.update { it.copy(isDownloadingDb = false, importProgress = if (success) 1.0f else null) }
            if (success) {
                kotlinx.coroutines.delay(1000)
                _state.update { it.copy(importProgress = null) }
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(success) }
        }
    }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val bookmarkUiState: StateFlow<BookmarkUiState> = repository.getAllBookmarks()
        .map { list ->
            BookmarkUiState(
                bookmarkedQuestions = list,
                bookmarkedQuestionTexts = list.map { it.question }.toSet(),
                bookmarkCount = list.size
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = BookmarkUiState()
        )

    val customSources: StateFlow<List<com.example.local.SourceCount>> =
        repository.getCustomSourceCountsFlow()
            .stateIn(viewModelScope,
                     kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                     emptyList())

    val collections: StateFlow<List<McqCollectionItem>> =
        repository.getAllCollectionsFlow()
            .stateIn(viewModelScope,
                     kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                     emptyList())

    fun getSectionsForCollectionFlow(collectionId: String): StateFlow<List<McqSectionItem>> =
        repository.getSectionsForCollectionFlow(collectionId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun getSetsForCollectionFlow(collectionId: String): StateFlow<List<McqSetItem>> =
        repository.getSetsForCollectionFlow(collectionId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun getSetsForSectionFlow(sectionId: String): StateFlow<List<McqSetItem>> =
        repository.getSetsForSectionFlow(sectionId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun getUnsectionedSetsForCollectionFlow(collectionId: String): StateFlow<List<McqSetItem>> =
        repository.getUnsectionedSetsForCollectionFlow(collectionId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun startSetSession(setId: String, setTitle: String, setType: String = "PRACTICE", durationMinutes: Int? = null, navigateToQuiz: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(uiState = McqUiState.Loading) }
            val questions = repository.getQuestionsForSet(setId)
            val isExam = setType == "TEST" || setType == "MOCK" || setType == "EXAM"
            val settings = ExamSettings(
                isExamMode = isExam,
                timeLimitMinutes = durationMinutes ?: 0,
                questionCount = questions.size,
                sessionStartTimeMs = System.currentTimeMillis()
            )
            _state.update {
                it.copy(
                    activeSessionConfig = SessionConfig(subject = setTitle, topic = if (isExam) "Timed Test" else "Practice Set"),
                    examSettings = settings,
                    uiState = McqUiState.Success(questions),
                    quizIndex = 0,
                    viewMode = "quiz",
                    isQuizActive = true,
                    hasResumableSession = true
                )
            }
            withContext(Dispatchers.Main) {
                navigateToQuiz()
            }
        }
    }

    fun deleteSet(setId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSet(setId)
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCollection(collectionId)
        }
    }


    fun toggleBookmark(question: McqField) {
        viewModelScope.launch(Dispatchers.IO) {
            val isBookmarked = bookmarkUiState.value.bookmarkedQuestionTexts.contains(question.question)
            repository.toggleBookmark(question, !isBookmarked)
        }
    }

    fun clearBookmarksBySubject(subject: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val listToRemove = if (subject == null) {
                bookmarkUiState.value.bookmarkedQuestions
            } else {
                bookmarkUiState.value.bookmarkedQuestions.filter { it.subject == subject }
            }
            listToRemove.forEach { repository.toggleBookmark(it, false) }
        }
    }

    data class SessionData(
        val stateConfig: AppState,
        val questions: List<McqField>,
        val config: SessionConfig?,
        val examSettings: ExamSettings,
        val quizIndex: Int,
        val isQuizActive: Boolean,
        val selectedOptions: Map<String, String>
    )

    private fun extractRelevantSessionFields(state: AppState, questions: List<McqField>): SessionData {
        return SessionData(
            stateConfig = state,
            questions = questions,
            config = state.activeSessionConfig,
            examSettings = state.examSettings,
            quizIndex = state.quizIndex,
            isQuizActive = state.isQuizActive,
            selectedOptions = state.selectedOptions
        )
    }

    val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, exception ->
        android.util.Log.e("McqViewModel", "Coroutine crash", exception)
        _state.update { it.copy(uiState = McqUiState.Error(exception.message ?: "Unknown error: ${exception.javaClass.simpleName}")) }
    }

    init {
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        _state.update { it.copy(isLoggedIn = fbUser != null) }

        viewModelScope.launch(Dispatchers.IO) {
            val hasSaved = settingsRepository.prefs.contains("active_session_json")
            _state.update { it.copy(hasResumableSession = hasSaved) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            state
                .map { extractRelevantSessionFields(it, (it.uiState as? McqUiState.Success)?.questions ?: emptyList()) }
                .distinctUntilChanged()
                .debounce(800)
                .collect { sessionData ->
                    if (sessionData.isQuizActive && sessionData.questions.isNotEmpty()) {
                        saveActiveSessionStateDirect(sessionData.stateConfig, sessionData.questions)
                    }
                }
        }

        viewModelScope.launch(coroutineExceptionHandler) {
            withContext(Dispatchers.IO) {
                // Initialize stuff if needed
            }
        }
        observeData()
    }

    fun login() {
        _state.update { it.copy(isLoggedIn = true) }
    }

    fun logout() {
        _state.update { it.copy(isLoggedIn = false) }
    }

    private fun observeData() {


        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.getTotalQuestionCount()
                .distinctUntilChanged()
                .collect { count ->
                _state.update { it.copy(totalQuestionCount = count) }
            }
        }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.getAllProgress()
                .distinctUntilChanged()
                .collect { progressList ->
                val optionsMap = progressList.associate { it.question to it.selectedOption }
                _state.update { 
                    it.copy(
                        progressHistory = progressList,
                        selectedOptions = if (it.isSessionMode) it.selectedOptions else optionsMap
                    )
                }
            }
        }


    }

    private val analyticsService by lazy { com.example.data.AnalyticsService(statsRepository) }

    private fun trackAnswerActivity() {
        analyticsService.trackAnswerActivity()
    }

    fun selectOption(questionText: String, option: String) {
        // Track the response times, streak logging, etc.
        trackAnswerActivity()

        _state.update { st ->
            val newOptions = st.selectedOptions + (questionText to option)

            st.copy(
                selectedOptions = newOptions
            )
        }
    }

    fun commitSession() {
        val currentState = _state.value
        if (!currentState.isSessionMode) return
        val sessionOptions = currentState.selectedOptions
        if (sessionOptions.isEmpty()) return

        val currentUiQuestions = (currentState.uiState as? McqUiState.Success)?.questions ?: emptyList()
        val progressEntities = sessionOptions.mapNotNull { (qText, opt) ->
            val qObj = currentUiQuestions.find { it.question == qText }
            if (qObj != null) {
                com.example.local.ProgressEntity(
                    question = qText,
                    subject = qObj.subject,
                    topic = qObj.topic,
                    selectedOption = opt,
                    isCorrect = isCorrect(qObj, opt)
                )
            } else null
        }

        if (progressEntities.isNotEmpty()) {
            viewModelScope.launch {
                repository.saveProgressList(progressEntities)
            }
        }
    }

    private fun isCorrect(question: McqField, selectedOption: String): Boolean {
        // Since we normalized correct_answer parsing in the DB layer, we can do direct evaluation in UI layer
        return question.correct_answer.equals(selectedOption, ignoreCase = true)
    }

    private val aiExplanationService = container.aiExplanationService
    
    fun sendChatMessage(question: McqField, userText: String) {
        val qid = question.question
        
        // 1. Add user message
        val currentMessages = _state.value.chatMessages[qid] ?: emptyList()
        val userMsg = ChatMessage(ChatSender.USER, userText)
        val initialAiMsg = ChatMessage(ChatSender.AI, "", isGenerating = true) // loading bubble
        
        _state.update { 
            it.copy(
                chatMessages = it.chatMessages + (qid to (currentMessages + userMsg + initialAiMsg))
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val h = _state.value.plainChatHistories[qid] ?: ""
            
            // Build text context for the model.
            val contextText = question.explanation ?: question.ai_explanation ?: "No existing explanation."
            
            val flow = aiExplanationService.generateChatResponseStream(
                question = question.question,
                explanationContext = contextText,
                chatHistory = h,
                userMessage = userText,
                providedApiKey = settingsRepository.getGeminiApiKey(),
                modelName = settingsRepository.getGeminiModel(),
                customPrompt = settingsRepository.getGeminiPrompt(),
                // Use default chat core to avoid long rigid explanations in follow-ups
                customCore = "" 
            )
            
            if (flow != null) {
                var fullAiResponse = ""
                flow.collect { chunk ->
                    fullAiResponse += chunk
                    
                    // Update the loading bubble with streamed text
                    _state.update { current ->
                        val msgs = (current.chatMessages[qid] ?: emptyList()).toMutableList()
                        if (msgs.isNotEmpty() && msgs.last().sender == ChatSender.AI) {
                            msgs[msgs.lastIndex] = msgs.last().copy(text = fullAiResponse, isGenerating = true)
                        }
                        current.copy(chatMessages = current.chatMessages + (qid to msgs))
                    }
                }
                
                // Finalize the bubble (isGenerating = false)
                _state.update { current ->
                    val msgs = (current.chatMessages[qid] ?: emptyList()).toMutableList()
                    if (msgs.isNotEmpty() && msgs.last().sender == ChatSender.AI) {
                        msgs[msgs.lastIndex] = msgs.last().copy(text = fullAiResponse, isGenerating = false)
                    }
                    // Update chat history
                    val newHistory = h + "\nUser: $userText\nAI: $fullAiResponse"
                    current.copy(
                        chatMessages = current.chatMessages + (qid to msgs),
                        plainChatHistories = current.plainChatHistories + (qid to newHistory)
                    )
                }
            } else {
                // Remove loading bubble and show error
                _state.update { current ->
                    val msgs = (current.chatMessages[qid] ?: emptyList()).toMutableList()
                    if (msgs.isNotEmpty() && msgs.last().sender == ChatSender.AI) {
                        msgs[msgs.lastIndex] = msgs.last().copy(text = "Failed to communicate with AI.", isGenerating = false)
                    }
                    current.copy(chatMessages = current.chatMessages + (qid to msgs))
                }
            }
        }
    }
    
    fun generateAndSaveExplanation(question: McqField, onLoading: (Boolean) -> Unit, onChunk: (String) -> Unit, onComplete: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onLoading(true)
            val flow = aiExplanationService.generateExplanationStream(
                question.question,
                question.options,
                question.correct_answer,
                settingsRepository.getGeminiApiKey(),
                settingsRepository.getGeminiModel(),
                settingsRepository.getGeminiPrompt(),
                customCore = settingsRepository.getGeminiCorePrompt()
            )
            if (flow != null) {
                var fullText = ""
                flow.collect { chunk ->
                    fullText += chunk
                    val displayChunk = fullText
                        .removePrefix("```html")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    onChunk(displayChunk)
                }
                val finalCleanText = fullText
                        .removePrefix("```html")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                val isBookmarked = repository.getAllBookmarks().first().any { it.question == question.question }
                repository.updateAiExplanation(question.question, finalCleanText, isBookmarked)
                onComplete(finalCleanText)
            } else {
                onComplete(null)
            }
            onLoading(false)
        }
    }

    fun startCustomQuiz(subject: String?, keyword: String, isBookmarked: Boolean, status: Int, shuffle: Boolean = false, navigateToQuiz: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(uiState = McqUiState.Loading) }
            val bookmarkVal = if (isBookmarked) 1 else 0
            val qs = repository.getAdvancedFilteredQuestions(subject, keyword, bookmarkVal, status, 200)
            val finalQs = if (shuffle) qs.shuffled() else qs
            _state.update { 
                it.copy(
                    activeSessionConfig = SessionConfig(subject = subject ?: "Custom Quiz", topic = "Custom"),
                    uiState = McqUiState.Success(finalQs),
                    quizIndex = 0,
                    viewMode = "quiz",
                    isQuizActive = true,
                    hasResumableSession = true
                )
            }
            navigateToQuiz()
        }
    }

    suspend fun getFilteredQuestionsCount(subject: String?, keyword: String, isBookmarked: Boolean, status: Int): Int {
        val bookmarkVal = if (isBookmarked) 1 else 0
        return try {
            repository.getAdvancedFilteredQuestionsCount(subject, keyword, bookmarkVal, status)
        } catch (e: Exception) {
            0
        }
    }

    fun clearSelections() {
        viewModelScope.launch {
            repository.clearProgress()
            _state.update { 
                it.copy(
                    selectedOptions = emptyMap()
                )
            }
        }
    }

    fun deleteQuestionsBySource(sourceName: String) {
        viewModelScope.launch {
            try {
                if (sourceName == "Legacy / Untagged") {
                    repository.deleteQuestionsBySource(null)
                } else {
                    repository.deleteQuestionsBySource(sourceName)
                }
            } catch (e: Exception) {
                android.util.Log.e("McqViewModel", "Failed to delete source: $sourceName", e)
            }
        }
    }

    fun loadMultipleJsonsFromUris(context: android.content.Context, uris: List<android.net.Uri>) {
        _state.update { it.copy(uiState = McqUiState.Loading, importProgress = 0.0f, importDiagnostics = null) }
        viewModelScope.launch {
            try {
                val totalImportedQuestions = syncRepository.loadMultipleJsonsFromUris(
                    context = context,
                    uris = uris,
                    fileRepository = fileRepository,
                    onProgress = { progress -> 
                        _state.update { it.copy(importProgress = progress) } 
                    },
                    onError = { errors -> 
                        _state.update { it.copy(importDiagnostics = errors) } 
                    }
                )

                if (totalImportedQuestions == 0) {
                    _state.update { it.copy(uiState = McqUiState.Error("No valid questions found in selected files."), importProgress = null) }
                } else {
                    _state.update { it.copy(importProgress = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(uiState = McqUiState.Error("Import fail: ${e.localizedMessage}"), importProgress = null) }
            }
        }
    }

    fun loadJson(jsonString: String, sourceName: String? = null) {
        _state.update { it.copy(uiState = McqUiState.Loading, importDiagnostics = null) }
        viewModelScope.launch {
            try {
                syncRepository.loadJson(
                    jsonString = jsonString, 
                    sourceName = sourceName, 
                    onError = { errors -> 
                        _state.update { it.copy(importDiagnostics = errors) } 
                    }
                )
                // Navigation handles refreshing state
            } catch (e: Exception) {
                _state.update { it.copy(uiState = McqUiState.Error("Import fail: ${e.localizedMessage}")) }
            }
        }
    }

    fun loadCustomJsonsFromUris(context: Context, uris: List<Uri>) {
        _state.update { it.copy(uiState = McqUiState.Loading,
                                importProgress = 0f,
                                importDiagnostics = null) }
        viewModelScope.launch {
            try {
                val n = syncRepository.loadMultipleJsonsFromUris(
                    context = context, uris = uris,
                    fileRepository = fileRepository,
                    onProgress = { p -> _state.update { it.copy(importProgress = p) } },
                    onError   = { e -> _state.update { it.copy(importDiagnostics = e) } },
                    sourcePrefix = "custom://"
                )
                _state.update { it.copy(importProgress = null,
                    uiState = if (n == 0)
                        McqUiState.Error("No valid questions found in selected files.")
                    else it.uiState) }
            } catch (e: Exception) {
                _state.update { it.copy(uiState = McqUiState.Error(
                    "Import failed: ${e.localizedMessage}"), importProgress = null) }
            }
        }
    }

    fun loadCustomZipFromUri(context: Context, uri: Uri) {
        _state.update {
            it.copy(
                uiState = McqUiState.Loading,
                importProgress = 0f,
                importDiagnostics = null
            )
        }
        viewModelScope.launch {
            try {
                val totalImported = syncRepository.loadZipFromUri(
                    context = context,
                    uri = uri,
                    fileRepository = fileRepository,
                    onProgress = { p -> _state.update { it.copy(importProgress = p) } },
                    onError = { e -> _state.update { it.copy(importDiagnostics = e) } }
                )
                _state.update {
                    it.copy(
                        importProgress = null,
                        uiState = if (totalImported == 0)
                            McqUiState.Error("No valid questions found in ZIP archive.")
                        else it.uiState
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        uiState = McqUiState.Error("ZIP import failed: ${e.localizedMessage}"),
                        importProgress = null
                    )
                }
            }
        }
    }

    fun loadPastedCustomJson(displayName: String, json: String) {
        val safeName = displayName.trim().ifBlank { "Custom Set" }
            .replace(Regex("[^A-Za-z0-9 _-]"), "_").take(60)
        val sourceName = "custom://${safeName}_${System.currentTimeMillis()}.json"
        loadJson(json, sourceName = sourceName)  // existing method
    }

    fun dismissImportDiagnostics() {
        _state.update { it.copy(importDiagnostics = null) }
    }




    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setViewMode(mode: String) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun setSearchVisible(visible: Boolean) {
        _state.update { it.copy(isSearchVisible = visible) }
        if (!visible) onSearchQueryChanged("")
    }

    fun setDashboardSearchVisible(visible: Boolean) {
        _state.update { it.copy(isDashboardSearchVisible = visible) }
        if (!visible) onDashboardSearchQueryChanged("")
    }

    fun onDashboardSearchQueryChanged(query: String) {
        _state.update { it.copy(dashboardSearchQuery = query) }
    }

    private var sourceTopicJob: Job? = null

    fun loadSourceTopics(source: String) {
        sourceTopicJob?.cancel()
        val dbSource = if (source == "Legacy / Untagged") "" else source
        sourceTopicJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.flow.combine(
                repository.getTopicsForSource(dbSource).distinctUntilChanged(),
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
                catSum
            }.collect { catSum ->
                _state.update { it.copy(sourceTopics = catSum) }
            }
        }
    }

    fun setFilterMode(mode: String) {
        _state.update { it.copy(filterMode = mode) }
    }

    fun loadQuestionsForCategory(subject: String?, topic: String?, source: String?, query: String? = null) {
        _state.update { it.copy(uiState = McqUiState.Loading) }
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val qs = repository.getFilteredQuestions(
                subject = subject,
                topic = topic,
                sourceUrl = if (source == "Legacy / Untagged") "" else source,
                searchQuery = query ?: "",
                limit = 2500
            )
            _state.update { it.copy(uiState = McqUiState.Success(qs)) }
        }
    }

    fun startSession(config: SessionConfig, settings: ExamSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            commitSession()
            val savedIndex = settingsRepository.getSavedQuizIndex(config.subject, config.topic)
            val updatedSettings = settings.copy(sessionStartTimeMs = System.currentTimeMillis())
            _state.update { 
                it.copy(
                    activeSessionConfig = config,
                    examSettings = updatedSettings,
                    quizIndex = if (config.customModules.isNotEmpty()) 0 else savedIndex,
                    selectedOptions = emptyMap(),
                    viewMode = "quiz",
                    isQuizActive = true,
                    hasResumableSession = true,
                    uiState = McqUiState.Loading
                ) 
            }
            
            val rawFiltered = if (config.customModules.isNotEmpty()) {
                val subjects = config.customModules.keys.toList()
                val dbSubjects = subjects.map { if (it == "General") "" else it }
                repository.getQuestionsBySubjects(dbSubjects)
            } else {
                repository.getFilteredQuestions(
                    subject = config.subject,
                    topic = config.topic,
                    sourceUrl = if (config.source == "Legacy / Untagged") "" else config.source,
                    searchQuery = "",
                    limit = 2500
                )
            }

            val filtered = rawFiltered.filter { q ->
                when {
                    config.customModules.isNotEmpty() -> {
                        val subName = if (q.subject.isNullOrBlank()) "General" else q.subject
                        val topName = if (q.topic.isNullOrBlank()) "Untracked" else q.topic
                        val topicsForSub = config.customModules[subName]
                        topicsForSub != null && topicsForSub.contains(topName)
                    }
                    else -> true
                }
            }

            val finalFiltered = if ((config.topic != null || config.customModules.isNotEmpty()) && updatedSettings.questionCount > 0) {
                val count = updatedSettings.questionCount
                if (count < filtered.size) {
                    filtered.shuffled().take(count)
                } else {
                    filtered.shuffled()
                }
            } else if (updatedSettings.randomizeQuestions) {
                filtered.shuffled()
            } else {
                filtered
            }

            _state.update { state -> 
                state.copy(
                    uiState = McqUiState.Success(finalFiltered),
                    quizIndex = if (config.customModules.isNotEmpty()) 0 else savedIndex.coerceIn(0, maxOf(0, finalFiltered.size - 1))
                )
            }
        }
    }



    fun startReattemptSession(mistakeQuestions: List<McqField>) {
        commitSession()
        val updatedSettings = ExamSettings(
            isExamMode = false,
            questionCount = mistakeQuestions.size,
            sessionStartTimeMs = System.currentTimeMillis()
        )
        _state.update { 
            it.copy(
                activeSessionConfig = null,
                examSettings = updatedSettings,
                quizIndex = 0,
                selectedOptions = emptyMap(),
                filterMode = "review",
                uiState = McqUiState.Success(mistakeQuestions),
                isQuizActive = true,
                hasResumableSession = true
            ) 
        }
    }

    fun finishQuizEarly() {
        val max = (_state.value.uiState as? McqUiState.Success)?.questions?.size ?: 0
        _state.update { it.copy(quizIndex = max, isQuizActive = false) }
    }

    fun nextQuizQuestion() {
        val max = (_state.value.uiState as? McqUiState.Success)?.questions?.size ?: 0
        val currentState = _state.value
        if (currentState.quizIndex < max) {
            val newIndex = currentState.quizIndex + 1
            _state.update { it.copy(quizIndex = newIndex) }
            settingsRepository.saveQuizIndex(currentState.activeSessionConfig?.subject, currentState.activeSessionConfig?.topic, newIndex)
        }
    }

    fun previousQuizQuestion() {
        val currentState = _state.value
        if (currentState.quizIndex > 0) {
            val newIndex = currentState.quizIndex - 1
            _state.update { it.copy(quizIndex = newIndex) }
            settingsRepository.saveQuizIndex(currentState.activeSessionConfig?.subject, currentState.activeSessionConfig?.topic, newIndex)
        }
    }

    fun jumpToQuestion(index: Int) {
        val currentState = _state.value
        val max = (currentState.uiState as? McqUiState.Success)?.questions?.size?.minus(1) ?: 0
        if (max >= 0) {
            val validIndex = index.coerceIn(0, max)
            _state.update { it.copy(quizIndex = validIndex) }
            settingsRepository.saveQuizIndex(currentState.activeSessionConfig?.subject, currentState.activeSessionConfig?.topic, validIndex)
        }
    }

    private fun saveActiveSessionStateDirect(currentState: AppState, questions: List<McqField>) {
        try {
            val root = org.json.JSONObject()
            root.put("selectedSubject", currentState.activeSessionConfig?.subject)
            root.put("selectedTopic", currentState.activeSessionConfig?.topic)
            root.put("selectedSource", currentState.activeSessionConfig?.source)
            root.put("viewMode", currentState.viewMode)
            root.put("filterMode", currentState.filterMode)
            
            val modulesObj = org.json.JSONObject()
            (currentState.activeSessionConfig?.customModules ?: emptyMap()).forEach { (k, v) ->
                val arr = org.json.JSONArray()
                v.forEach { arr.put(it) }
                modulesObj.put(k, arr)
            }
            root.put("customModules", modulesObj)
            
            val settingsObj = org.json.JSONObject()
            settingsObj.put("isExamMode", currentState.examSettings.isExamMode)
            settingsObj.put("questionCount", currentState.examSettings.questionCount)
            settingsObj.put("timeLimitMinutes", currentState.examSettings.timeLimitMinutes)
            settingsObj.put("sessionStartTimeMs", currentState.examSettings.sessionStartTimeMs)
            settingsObj.put("randomizeQuestions", currentState.examSettings.randomizeQuestions)
            settingsObj.put("practiceTimerLimitSec", currentState.examSettings.practiceTimerLimitSec)
            root.put("examSettings", settingsObj)
            
            root.put("quizIndex", currentState.quizIndex)
            
            val optionsObj = org.json.JSONObject()
            if (currentState.isSessionMode) {
                currentState.selectedOptions.forEach { (k, v) ->
                    optionsObj.put(k, v)
                }
            }
            root.put("sessionSelectedOptions", optionsObj)
            
            val questionsArr = org.json.JSONArray()
            questions.forEach { questionsArr.put(it.question) }
            root.put("questionsList", questionsArr)
            
            settingsRepository.prefs.edit().putString("active_session_json", root.toString()).apply()
            
            if (!currentState.hasResumableSession) {
                _state.update { it.copy(hasResumableSession = true) }
            }
        } catch (e: Exception) {
            android.util.Log.e("McqViewModel", "Failed to save active session", e)
        }
    }

    fun clearActiveSessionBackup() {
        settingsRepository.prefs.edit().remove("active_session_json").apply()
        _state.update { it.copy(hasResumableSession = false, isQuizActive = false) }
    }

    fun resumeActiveSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonStr = settingsRepository.prefs.getString("active_session_json", null)
            if (jsonStr == null) {
                clearActiveSessionBackup()
                return@launch
            }
            try {
            val root = org.json.JSONObject(jsonStr)
            val selectedSubject = if (root.isNull("selectedSubject")) null else root.getString("selectedSubject")
            val selectedTopic = if (root.isNull("selectedTopic")) null else root.getString("selectedTopic")
            val selectedSource = if (root.isNull("selectedSource")) null else root.getString("selectedSource")
            val restoredViewMode = root.optString("viewMode", "quiz")
            val restoredFilterMode = root.optString("filterMode", "subject")
            
            val customModules = mutableMapOf<String, Set<String>>()
            if (!root.isNull("customModules")) {
                val modulesObj = root.getJSONObject("customModules")
                val keys = modulesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = modulesObj.getJSONArray(key)
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                    customModules[key] = set
                }
            }
            
            val examSettings = if (!root.isNull("examSettings")) {
                val s = root.getJSONObject("examSettings")
                ExamSettings(
                    isExamMode = s.optBoolean("isExamMode", false),
                    questionCount = s.optInt("questionCount", 0),
                    timeLimitMinutes = s.optInt("timeLimitMinutes", 0),
                    sessionStartTimeMs = s.optLong("sessionStartTimeMs", 0L),
                    randomizeQuestions = s.optBoolean("randomizeQuestions", false),
                    practiceTimerLimitSec = s.optInt("practiceTimerLimitSec", 60)
                )
            } else ExamSettings()
            
            val quizIndex = root.optInt("quizIndex", 0)
            
            val loadedSelectedOptions = mutableMapOf<String, String>()
            if (!root.isNull("sessionSelectedOptions")) {
                val s = root.getJSONObject("sessionSelectedOptions")
                val keys = s.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    loadedSelectedOptions[key] = s.getString(key)
                }
            }
            
            val questionTexts = mutableListOf<String>()
            if (!root.isNull("questionsList")) {
                val arr = root.getJSONArray("questionsList")
                for (i in 0 until arr.length()) {
                    questionTexts.add(arr.getString(i))
                }
            }
            
            _state.update { state -> 
                state.copy(
                    activeSessionConfig = SessionConfig(selectedSubject, selectedTopic, selectedSource, customModules),
                    examSettings = examSettings,
                    quizIndex = quizIndex,
                    selectedOptions = loadedSelectedOptions,
                    viewMode = restoredViewMode,
                    filterMode = restoredFilterMode,
                    uiState = McqUiState.Loading,
                    isQuizActive = true,
                    hasResumableSession = true
                )
            }
            
            viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                val finalQuestions = if (questionTexts.isNotEmpty()) {
                    val fullQs = repository.getQuestionsByIds(questionTexts)
                    // Keep original order
                    questionTexts.mapNotNull { id -> fullQs.find { it.question == id } }
                } else {
                    val rawFiltered = if (customModules.isNotEmpty()) {
                        val subjects = customModules.keys.toList()
                        val dbSubjects = subjects.map { if (it == "General") "" else it }
                        repository.getQuestionsBySubjects(dbSubjects)
                    } else {
                        repository.getFilteredQuestions(
                            subject = selectedSubject,
                            topic = selectedTopic,
                            sourceUrl = if (selectedSource == "Legacy / Untagged") "" else selectedSource,
                            searchQuery = "",
                            limit = 2500
                        )
                    }
                    
                    val filtered = rawFiltered.filter { q ->
                        when {
                            customModules.isNotEmpty() -> {
                                val subName = if (q.subject.isNullOrBlank()) "General" else q.subject
                                val topName = if (q.topic.isNullOrBlank()) "Untracked" else q.topic
                                val topicsForSub = customModules[subName]
                                topicsForSub != null && topicsForSub.contains(topName)
                            }
                            else -> true
                        }
                    }
                    filtered
                }
                
                _state.update { state -> 
                    state.copy(
                        uiState = McqUiState.Success(finalQuestions),
                        quizIndex = quizIndex.coerceIn(0, maxOf(0, finalQuestions.size - 1))
                    )
                }
            }
        } catch (e: Exception) {
            clearActiveSessionBackup()
            android.util.Log.e("McqViewModel", "Failed to resume active session", e)
        }
        }
    }
}

