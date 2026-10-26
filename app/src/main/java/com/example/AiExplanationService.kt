package com.example

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class ChatSender { USER, AI }

data class ChatMessage(
    val sender: ChatSender,
    val text: String,
    val isGenerating: Boolean = false
)

class AiExplanationService {

    companion object {
        const val DEFAULT_PERSONA = """Act as an elite NEET PG professor and clinician.

Teach every topic using deep conceptual understanding, clinical reasoning, and exam-oriented thinking. Build knowledge from first principles, not rote memorization."""

        const val DEFAULT_CORE = """Goal: Make the learner understand, retain, differentiate, recall under pressure, and solve NEET PG/INI-CET MCQs confidently.

OUTPUT ONLY CLEAN MARKDOWN (Do avoiding HTML completely):
Use **bold** for emphasis, *italics*, bullet points (-), and standard markdown tables.

FLOW:

🧠 **Big Picture:**
- What is it?
- Explain why in a nice conceptual sentence
- Entire topic of that MCQ explained for PG NEET preparation as a teaching answer

⚙️ **Concept Engine:**
- Normal physiology → Pathology → Clinical findings.
- Explain the mechanism behind every major sign, symptom, investigation, and treatment.

🩺 **Clinical Pattern Recognition:**
- Typical patient.
- Classic presentation.
- Most important diagnostic clue.
- Common exam vignette.

🔬 **Investigation & Treatment Logic:**
- First-line investigation.
- Best investigation.
- Gold standard.
- First-line treatment.
- Emergency and definitive management.

❌ **Differentials & Distractor Demolition:**
- Why similar diseases/options are wrong.
- How to differentiate them quickly in MCQs.

🎯 **Exam High-Yield:**
- PYQ concepts.
- Buzzwords.
- Common traps.
- Image-based clues.

🧠 **Memory Anchors:**
- Mnemonic, analogy, or visual association.

⚡ **Rapid Revision:**
- 5 must-know facts.
- 3 examiner favorites.
- 3 rapid-fire MCQs with explanations."""
        const val DEFAULT_CHAT_CORE = """Goal: Respond directly and adaptively to the user's follow-up request.

OUTPUT ONLY CLEAN MARKDOWN:
Use **bold**, *italics*, lists, and tables where appropriate. Avoid HTML.

GUIDELINES:
- Adapt the structure to the user's prompt (e.g., provide a markdown table for comparisons, list for options, brief text for direct questions).
- Do not force the rigid multi-step framework used for the main MCQ explanation. Focus only on what the user asked.
- Maintain the core clinical persona and depth, but keep the response concise, targeted, and highly scannable."""
    }

    private fun logError(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) {
                Log.e(tag, msg, tr)
            } else {
                Log.e(tag, msg)
            }
        } catch (e: Throwable) {
            println("[$tag] ERROR: $msg" + (tr?.let { "\n${it.stackTraceToString()}" } ?: ""))
        }
    }

    suspend fun generateChatResponseStream(
        question: String,
        explanationContext: String,
        chatHistory: String,
        userMessage: String,
        providedApiKey: String = "",
        modelName: String = "gemini-3.5-flash-lite",
        customPrompt: String = "",
        customCore: String = ""
    ): Flow<String>? {
        return withContext(Dispatchers.IO) {
            val apiKey = providedApiKey.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
                ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            if (apiKey.isNullOrEmpty()) {
                logError("AiExplanationService", "Gemini API key is not set. Check Secrets Panel.")
                return@withContext kotlinx.coroutines.flow.flowOf("""
                    <b>Gemini API Key is Not Set:</b><br>
                    No valid API key was found in your workspace environment. Please configure it in Settings.
                """.trimIndent())
            }

            val resolvedCore = if (customCore.isNotBlank()) customCore else DEFAULT_CHAT_CORE
            val finalInstruction = if (customPrompt.isNotBlank()) customPrompt else "$DEFAULT_PERSONA\n\n$resolvedCore"

            val promptText = """
                Original Question: $question
                Explanation Context: $explanationContext
                
                --- Conversation History ---
                $chatHistory
                --- End of History ---
                
                Current User Message: $userMessage
                
                Please answer the user's latest message based on the clinical context provided and the exact formatting rules in your system instructions.
            """.trimIndent()

            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f
                },
                systemInstruction = content {
                    text(finalInstruction)
                }
            )

            try {
                generativeModel.generateContentStream(promptText).map { response ->
                    response.text ?: ""
                }.catch { e ->
                    logError("AiExplanationService", "Network/Parsing error in chat", e)
                    emit("<br><br><b>Error:</b><br>${e.message}")
                }
            } catch (e: Exception) {
                logError("AiExplanationService", "Initialization error", e)
                null
            }
        }
    }

    suspend fun generateExplanationStream(
        question: String,
        options: List<String>,
        correctAnswer: String,
        providedApiKey: String = "",
        modelName: String = "gemini-3.5-flash-lite",
        customPrompt: String = "",
        customCore: String = ""
    ): Flow<String>? {
        return withContext(Dispatchers.IO) {
            val apiKey = providedApiKey.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
                ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            if (apiKey.isNullOrEmpty()) {
                logError("AiExplanationService", "Gemini API key is not set. Check Secrets Panel.")
                return@withContext kotlinx.coroutines.flow.flowOf("""
                    <b>Gemini API Key is Not Set:</b><br>
                    No valid API key was found in your workspace environment.<br><br>
                    <b>How to configure your API key securely:</b><br>
                    1. Go to Profile Settings.<br>
                    2. Add your API key there.<br>
                    3. Try again.
                """.trimIndent())
            }

            val promptText = """
                Question: $question
                Options: ${options.joinToString(", ")}
                Correct Answer: $correctAnswer

                Please explain this question and analyze why incorrect options are wrong step-by-step. You must follow your system instructions for clinical expert persona, teaching logic, formatting boundaries, and the exact structured response flow.
            """.trimIndent()

            val resolvedCore = if (customCore.isNotBlank()) customCore else DEFAULT_CORE

            val finalInstruction = if (customPrompt.isNotBlank()) {
                customPrompt
            } else {
                "$DEFAULT_PERSONA\n\n$resolvedCore"
            }

            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f
                },
                systemInstruction = content {
                    text(finalInstruction)
                }
            )

            try {
                generativeModel.generateContentStream(promptText).map { response ->
                    response.text ?: ""
                }.catch { e ->
                    logError("AiExplanationService", "Network/Parsing error", e)
                    emit("<br><br><b>Error:</b><br>${e.message}")
                }
            } catch (e: Exception) {
                logError("AiExplanationService", "Initialization error", e)
                null
            }
        }
    }
}
