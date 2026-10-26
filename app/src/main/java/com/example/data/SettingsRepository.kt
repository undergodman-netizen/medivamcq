package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.AppThemeMode

class SettingsRepository(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("mcq_prefs", Context.MODE_PRIVATE)

    // ponytail: minimal safe async commit to avoid apply() ANRs during rapid updates
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private inline fun editSafe(crossinline block: SharedPreferences.Editor.() -> Unit) {
        executor.execute {
            prefs.edit().apply(block).commit()
        }
    }

    fun getDisplayName(): String = prefs.getString("display_name", "Guest User") ?: "Guest User"
    fun getEmail(): String = prefs.getString("email", "") ?: ""
    fun getDailyGoal(): Int = prefs.getInt("daily_goal", 10)
    
    fun getCustomTheme(): AppThemeMode {
        val name = prefs.getString("custom_theme", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name
        return try {
            AppThemeMode.valueOf(name)
        } catch (e: Exception) {
            AppThemeMode.LIGHT
        }
    }

    fun isClinicalMode(): Boolean = prefs.getBoolean("clinical_mode", false)
    fun getClinicalFontFamily(): String = prefs.getString("clinical_font_family", "Sans") ?: "Sans"
    fun getGeminiApiKey(): String {
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        val buildKey = com.example.BuildConfig.GEMINI_API_KEY
        if (savedKey.isBlank() && buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return savedKey
    }
    fun getGeminiModel(): String = prefs.getString("gemini_model", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite"
    fun getGeminiPrompt(): String = prefs.getString("gemini_prompt", "") ?: ""
    fun getGeminiCorePrompt(): String = prefs.getString("gemini_core_prompt", "") ?: ""
    fun getGeminiImagePrompt(): String = prefs.getString("gemini_image_prompt", "A clear, highly detailed 3D anatomical concept photo of the {topic}, soft lighting, educational textbook style") ?: "A clear, highly detailed 3D anatomical concept photo of the {topic}, soft lighting, educational textbook style"
    fun getLastSyncTime(): Long = prefs.getLong("last_sync_time", 0L)

    fun getTtsVoiceName(): String? = prefs.getString("tts_voice_name", null)

    fun saveTtsVoiceName(name: String?) {
        editSafe {
            if (name != null) {
                putString("tts_voice_name", name)
            } else {
                remove("tts_voice_name")
            }
        }
    }

    fun getTtsSpeed(): Float = prefs.getFloat("ttsSpeed", 1.0f)
    fun getTtsPitch(): Float = prefs.getFloat("ttsPitch", 1.0f)

    fun getSavedQuizIndex(subject: String?, topic: String?): Int {
        val key = "quiz_index_${subject ?: "default"}_${topic ?: "default"}"
        return prefs.getInt(key, 0)
    }

    fun saveProfile(name: String, email: String) {
        editSafe {
            putString("display_name", name)
            putString("email", email)
        }
    }

    fun saveDailyGoal(goal: Int) {
        editSafe { putInt("daily_goal", goal) }
    }

    fun saveTheme(theme: AppThemeMode) {
        editSafe { putString("custom_theme", theme.name) }
    }

    fun setClinicalMode(enabled: Boolean) {
        editSafe { putBoolean("clinical_mode", enabled) }
    }

    fun setClinicalFontFamily(fontFamily: String) {
        editSafe { putString("clinical_font_family", fontFamily) }
    }

    fun saveGeminiApiKey(key: String) {
        editSafe { putString("gemini_api_key", key) }
    }

    fun saveGeminiModel(model: String) {
        editSafe { putString("gemini_model", model) }
    }

    fun saveGeminiPrompt(prompt: String) {
        editSafe { putString("gemini_prompt", prompt) }
    }

    fun saveGeminiCorePrompt(prompt: String) {
        editSafe { putString("gemini_core_prompt", prompt) }
    }

    fun saveGeminiImagePrompt(prompt: String) {
        editSafe { putString("gemini_image_prompt", prompt) }
    }

    fun saveLastSyncTime(time: Long) {
        editSafe { putLong("last_sync_time", time) }
    }

    fun saveQuizIndex(subject: String?, topic: String?, index: Int) {
        val key = "quiz_index_${subject ?: "default"}_${topic ?: "default"}"
        editSafe { putInt(key, index) }
    }
}
