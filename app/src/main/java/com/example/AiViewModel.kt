package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MyApplication).container
    private val aiExplanationService = container.aiExplanationService
    private val settingsRepository = container.settingsRepository
    private val repository = container.mcqRepository

    private val _aiExplanationStates = MutableStateFlow<Map<String, AiState>>(emptyMap())
    val aiExplanationStates: StateFlow<Map<String, AiState>> = _aiExplanationStates.asStateFlow()

    fun generateAndSaveExplanationWithState(question: McqField) {
        val currentState = _aiExplanationStates.value[question.question]
        if (currentState is AiState.Loading || currentState is AiState.Success) {
            return
        }

        _aiExplanationStates.value = _aiExplanationStates.value + (question.question to AiState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            val currentApiKey = settingsRepository.getGeminiApiKey()
            val geminiModel = settingsRepository.getGeminiModel()
            val geminiPrompt = settingsRepository.getGeminiPrompt()
            val geminiCorePrompt = settingsRepository.getGeminiCorePrompt()
            
            val options = question.options
            val correctAnswer = question.correct_answer

            val stream = aiExplanationService.generateExplanationStream(
                question = question.question,
                options = options,
                correctAnswer = correctAnswer,
                providedApiKey = currentApiKey,
                modelName = geminiModel,
                customPrompt = geminiPrompt,
                customCore = geminiCorePrompt
            )
            
            if (stream == null) {
                _aiExplanationStates.value = _aiExplanationStates.value + (question.question to AiState.Error("Failed to initialize AI."))
            } else {
                var fullResponse = ""
                try {
                    stream.collect { chunk ->
                        fullResponse += chunk
                        val cleanChunk = fullResponse.removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
                        _aiExplanationStates.value = _aiExplanationStates.value + (question.question to AiState.Success(cleanChunk))
                    }
                    val finalCleanText = fullResponse.removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
                    if (finalCleanText.isBlank()) {
                        _aiExplanationStates.value = _aiExplanationStates.value + (question.question to AiState.Error("Received empty response from AI."))
                    } else {
                        val isBookmarked = repository.getAllBookmarks().firstOrNull()?.any { it.question == question.question } ?: false
                        repository.updateAiExplanation(question.question, finalCleanText, isBookmarked)
                        _aiExplanationStates.value = _aiExplanationStates.value + (question.question to AiState.Success(finalCleanText))
                    }
                } catch (e: Exception) {
                    _aiExplanationStates.value = _aiExplanationStates.value + (question.question to AiState.Error("Generation failed: ${e.message}"))
                }
            }
        }
    }

    fun retryAiExplanation(question: McqField) {
        _aiExplanationStates.value = _aiExplanationStates.value.filterKeys { it != question.question }
        generateAndSaveExplanationWithState(question)
    }

}
