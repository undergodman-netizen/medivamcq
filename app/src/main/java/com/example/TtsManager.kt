package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

val LocalTtsManager = staticCompositionLocalOf<TtsManager?> { null }

enum class AudioState {
    IDLE, PLAYING, PAUSED
}

data class TtsVoiceOption(
    val name: String,
    val displayName: String,
    val quality: Int,
    val isNetwork: Boolean
)

val Voice.isInstalled: Boolean
    get() = features == null || !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

fun pickBestVoice(voices: List<Voice>, savedName: String?): Voice? {
    if (!savedName.isNullOrBlank()) {
        val saved = voices.firstOrNull { it.name == savedName && !it.name.contains("compact", ignoreCase = true) && it.isInstalled }
        if (saved != null) return saved
    }
    return voices
        .filter { it.isInstalled && it.locale?.language == "en" && !it.name.contains("compact", ignoreCase = true) }
        .sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenByDescending { it.isNetworkConnectionRequired }
                .thenBy { it.name.length }
        )
        .firstOrNull()
}

fun sortEnglishVoices(voices: List<Voice>): List<Voice> {
    return voices
        .filter { it.isInstalled && it.locale?.language == "en" && !it.name.contains("compact", ignoreCase = true) }
        .sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenByDescending { it.isNetworkConnectionRequired }
                .thenBy { it.name.length }
        )
}

fun formatVoiceDisplayName(voice: Voice): String {
    val lower = voice.name.lowercase()
    val region = when {
        lower.startsWith("en-us") -> "US English"
        lower.startsWith("en-gb") -> "UK English"
        lower.startsWith("en-in") -> "Indian English"
        lower.startsWith("en-au") -> "Australian English"
        lower.startsWith("en-ca") -> "Canadian English"
        else -> voice.locale?.displayName?.takeIf { it.isNotBlank() } ?: "English"
    }

    val gender = when {
        lower.contains("female") -> "Female"
        lower.contains("male") -> "Male"
        else -> null
    }

    val isHighFidelity = voice.quality >= 400 || lower.contains("high") || lower.contains("network") || lower.contains("#female_2") || lower.contains("#male_2")

    return when {
        gender != null && isHighFidelity -> "$region — $gender (High Fidelity)"
        gender != null -> "$region — $gender"
        isHighFidelity && region != "English" -> "$region (High Fidelity)"
        else -> region
    }
}

class TtsManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: Any? = null

    private val _currentTag = MutableStateFlow<String?>(null)
    val currentTag: StateFlow<String?> = _currentTag.asStateFlow()

    private val _audioState = MutableStateFlow(AudioState.IDLE)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    val activeRange: StateFlow<IntRange?> = MutableStateFlow<IntRange?>(null).asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    var ttsPitch = 1.0f
    var ttsSpeed = 1.0f

    var ttsLanguageUnavailable = false
        private set

    private var lastTag: String? = null
    private var lastText: String? = null
    private var lastPitch: Float = 1.0f
    private var lastSpeed: Float = 1.0f

    private val chunkCountByTag = mutableMapOf<String, Int>()
    private val completedChunksByTag = mutableMapOf<String, Int>()

    private var voiceBeforePreview: Voice? = null

    init {
        val pm = appContext.packageManager
        val ttsIntent = Intent("android.intent.action.TTS_SERVICE").setPackage("com.google.android.tts")
        val hasGoogleTts = pm.queryIntentServices(ttsIntent, 0).isNotEmpty()

        if (hasGoogleTts) {
            android.util.Log.d("TtsManager", "Targeting Google TTS engine: com.google.android.tts")
            tts = TextToSpeech(appContext, { status -> onTtsInit(status) }, "com.google.android.tts")
        } else {
            android.util.Log.d("TtsManager", "Google TTS engine not detected; using system default")
            tts = TextToSpeech(appContext) { status -> onTtsInit(status) }
        }
    }

    private fun onTtsInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attributes)
            } catch (e: Exception) {
                android.util.Log.w("TtsManager", "Failed to set AudioAttributes: ${e.message}")
            }

            val localesToTry = listOf(Locale.US, Locale.UK, Locale.forLanguageTag("en-IN"))
            var langSet = false
            for (loc in localesToTry) {
                val result = tts?.setLanguage(loc)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    android.util.Log.d("TtsManager", "TTS language negotiated successfully: $loc (result=$result)")
                    langSet = true
                    break
                }
            }

            if (!langSet) {
                android.util.Log.e("TtsManager", "All candidate English locales failed")
                ttsLanguageUnavailable = true
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(appContext, "Install an English voice pack in system TTS settings", android.widget.Toast.LENGTH_LONG).show()
                }
            }

            val savedVoiceName = com.example.data.SettingsRepository(appContext).getTtsVoiceName()
            applyBestVoice(savedVoiceName)

            isTtsReady = true
            setupTtsListener()
            android.util.Log.d("TtsManager", "engine=${tts?.defaultEngine} voice=${tts?.voice?.name}")
        } else {
            android.util.Log.e("TtsManager", "TextToSpeech init failed with status: $status")
        }
    }

    fun applyBestVoice(savedVoiceName: String?) {
        val voices = tts?.voices?.toList() ?: return
        if (!savedVoiceName.isNullOrBlank()) {
            val candidate = voices.firstOrNull { it.name == savedVoiceName && it.isInstalled }
            if (candidate != null) {
                val result = tts?.setVoice(candidate)
                android.util.Log.d("TtsManager", "Attempting saved voice: ${candidate.name}, quality=${candidate.quality}, network=${candidate.isNetworkConnectionRequired}, result=$result")
                if (result == TextToSpeech.SUCCESS) return
            }
        }

        val candidates = sortEnglishVoices(voices)
        for (candidate in candidates) {
            val result = tts?.setVoice(candidate)
            android.util.Log.d("TtsManager", "Attempting auto voice: ${candidate.name}, quality=${candidate.quality}, network=${candidate.isNetworkConnectionRequired}, result=$result")
            if (result == TextToSpeech.SUCCESS) {
                break
            }
        }
    }

    fun reapplyAutoVoice() {
        val voices = tts?.voices?.toList() ?: return
        val candidates = sortEnglishVoices(voices)
        for (candidate in candidates) {
            val result = tts?.setVoice(candidate)
            if (result == TextToSpeech.SUCCESS) break
        }
    }

    fun setCustomVoice(voiceName: String) {
        val voices = tts?.voices?.toList() ?: return
        val target = voices.firstOrNull { it.name == voiceName }
        if (target != null) {
            tts?.setVoice(target)
        }
    }

    fun listEnglishVoices(): List<TtsVoiceOption> {
        val voices = tts?.voices?.toList() ?: return emptyList()
        val sorted = sortEnglishVoices(voices)
        return sorted.map { voice ->
            TtsVoiceOption(
                name = voice.name,
                displayName = formatVoiceDisplayName(voice),
                quality = voice.quality,
                isNetwork = voice.isNetworkConnectionRequired
            )
        }
    }

    fun previewVoice(voiceName: String?) {
        stopPreview()
        voiceBeforePreview = tts?.voice

        if (voiceName != null) {
            val target = tts?.voices?.firstOrNull { it.name == voiceName }
            if (target != null) {
                tts?.setVoice(target)
            }
        } else {
            reapplyAutoVoice()
        }

        val previewText = "This is a preview of the selected voice. Hypertension is defined as persistently elevated arterial blood pressure."
        tts?.speak(previewText, TextToSpeech.QUEUE_FLUSH, null, "__preview__")
    }

    fun stopPreview() {
        if (tts != null) {
            tts?.stop()
            restoreVoiceAfterPreview()
        }
    }

    // ponytail: preview restores previous voice via voiceBeforePreview because setVoice is global to the engine instance
    private fun restoreVoiceAfterPreview() {
        voiceBeforePreview?.let {
            tts?.voice = it
            voiceBeforePreview = null
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null || utteranceId == "__preview__") return
                val baseTag = utteranceId.substringBefore("#")
                if (_currentTag.value == baseTag) {
                    _audioState.value = AudioState.PLAYING
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                if (utteranceId == "__preview__") {
                    restoreVoiceAfterPreview()
                    return
                }
                val baseTag = utteranceId.substringBefore("#")
                if (_currentTag.value == baseTag) {
                    val total = chunkCountByTag[baseTag] ?: 1
                    val done = (completedChunksByTag[baseTag] ?: 0) + 1
                    completedChunksByTag[baseTag] = done
                    if (done >= total) {
                        abandonAudioFocus()
                        chunkCountByTag.remove(baseTag)
                        completedChunksByTag.remove(baseTag)
                        _audioState.value = AudioState.IDLE
                        _currentTag.value = null
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId == null) return
                if (utteranceId == "__preview__") {
                    restoreVoiceAfterPreview()
                    return
                }
                val baseTag = utteranceId.substringBefore("#")
                if (_currentTag.value == baseTag) {
                    abandonAudioFocus()
                    chunkCountByTag.remove(baseTag)
                    completedChunksByTag.remove(baseTag)
                    _audioState.value = AudioState.IDLE
                    _currentTag.value = null
                }
            }
        })
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stop()
                    }
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange -> if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stop() },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {}
    }

    fun cleanText(rawText: String): String {
        var cleaned = rawText
        cleaned = cleaned.replace(Regex("(?i)<br\\s*/?>"), " ")
        cleaned = cleaned.replace(Regex("(?i)</p>"), " ")
        cleaned = cleaned.replace(Regex("(?i)</div>"), " ")
        if (cleaned.trim().startsWith("{") && cleaned.trim().endsWith("}")) {
            try { cleaned = cleaned.replace(Regex("[\"{}]"), "") } catch (e: Exception) {}
        }
        cleaned = com.example.utils.stripHtmlForTts(cleaned)

        cleaned = cleaned.replace("±", " plus or minus ")
        cleaned = cleaned.replace("%", " percent ")
        cleaned = cleaned.replace("+", " plus ")
        cleaned = cleaned.replace(">", " greater than ")
        cleaned = cleaned.replace("<", " less than ")
        cleaned = cleaned.replace("=", " equals ")
        cleaned = cleaned.replace("&", " and ")
        cleaned = cleaned.replace("μ", " micro ")
        cleaned = cleaned.replace("α", " alpha ")
        cleaned = cleaned.replace("β", " beta ")
        cleaned = cleaned.replace("γ", " gamma ")
        cleaned = cleaned.replace("°", " degrees ")
        cleaned = cleaned.replace("−", " minus ")

        cleaned = cleaned.replace(Regex("\\*\\*|__"), "")
        cleaned = cleaned.replace(Regex("\\*|_"), "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*#+\\s+"), "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*-\\s+"), "")
        cleaned = cleaned.replace(Regex("`{1,3}"), "")
        cleaned = cleaned.replace(Regex("[^a-zA-Z0-9\\s,.!?'’]"), " ")
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        return cleaned.trim()
    }

    fun togglePlayPause(
        tag: String,
        text: String,
        pitch: Float,
        speed: Float
    ) {
        if (_currentTag.value == tag) {
            if (_audioState.value == AudioState.PLAYING) {
                pause()
            } else if (_audioState.value == AudioState.PAUSED) {
                resume()
            } else {
                startNew(tag, text, pitch, speed)
            }
        } else {
            stop()
            startNew(tag, text, pitch, speed)
        }
    }

    private fun splitIntoChunks(text: String, maxChunkSize: Int = 3000): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        val delimiters = listOf(". ", "? ", "! ", ".\n", "?\n", "!\n", "\n\n", "\n")

        while (remaining.length > maxChunkSize) {
            var splitIdx = -1
            for (delimiter in delimiters) {
                val idx = remaining.lastIndexOf(delimiter, maxChunkSize)
                if (idx != -1) {
                    val candidate = idx + delimiter.length
                    if (candidate > splitIdx) {
                        splitIdx = candidate
                    }
                }
            }
            if (splitIdx <= 0) {
                val spaceIdx = remaining.lastIndexOf(' ', maxChunkSize)
                splitIdx = if (spaceIdx > 0) spaceIdx + 1 else maxChunkSize
            }
            val chunk = remaining.substring(0, splitIdx).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }
            remaining = remaining.substring(splitIdx).trim()
        }
        if (remaining.isNotEmpty()) {
            chunks.add(remaining)
        }
        return chunks
    }

    private fun startNew(tag: String, text: String, pitch: Float, speed: Float) {
        lastTag = tag
        lastText = text
        lastPitch = pitch
        lastSpeed = speed
        _currentTag.value = tag

        if (!isTtsReady || tts == null) {
            _audioState.value = AudioState.IDLE
            _currentTag.value = null
            return
        }

        requestAudioFocus()
        val cleaned = cleanText(text)
        if (cleaned.isBlank()) {
            abandonAudioFocus()
            _audioState.value = AudioState.IDLE
            _currentTag.value = null
            return
        }

        tts?.let { engine ->
            engine.setPitch(pitch)
            engine.setSpeechRate(speed)

            if (cleaned.length <= 3500) {
                chunkCountByTag[tag] = 1
                completedChunksByTag[tag] = 0
                engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, tag)
            } else {
                val chunks = splitIntoChunks(cleaned, 3000)
                chunkCountByTag[tag] = chunks.size
                completedChunksByTag[tag] = 0
                chunks.forEachIndexed { index, chunk ->
                    val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val chunkId = "$tag#$index"
                    engine.speak(chunk, queueMode, null, chunkId)
                }
            }
        }
    }

    fun pause() {
        tts?.stop()
        _audioState.value = AudioState.PAUSED
    }

    fun resume() {
        // ponytail: pause = stop + restart (Android TextToSpeech has no true pause)
        val tag = lastTag
        val text = lastText
        if (tag != null && text != null) {
            startNew(tag, text, lastPitch, lastSpeed)
        } else {
            _audioState.value = AudioState.IDLE
        }
    }

    fun stop() {
        abandonAudioFocus()
        tts?.stop()
        chunkCountByTag.clear()
        completedChunksByTag.clear()
        _audioState.value = AudioState.IDLE
        _currentTag.value = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
    }
}
