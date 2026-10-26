package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object ZipExportUtils {

    data class QuestionJson(
        val subject: String,
        val topic: String,
        val question: String,
        val options: List<String>,
        val correct_answer: String,
        val explanation: String? = null,
        val ai_explanation: String? = null,
        val images: String? = null,
        val audio: String? = null,
        val source_url: String? = null
    )

    data class ProgressJson(
        val question: String,
        val subject: String,
        val topic: String,
        val selectedOption: String,
        val isCorrect: Boolean
    )



    suspend fun importDatabaseFromZip(
        context: Context,
        uri: Uri,
        onQuestionsChunk: suspend (List<QuestionJson>) -> Unit,
        onProgressChunk: suspend (List<ProgressJson>) -> Unit
    ): Boolean {
        return try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return false
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".json")) {
                        try {
                            val reader = JsonReader(InputStreamReader(zipStream))
                            reader.isLenient = true
                            parseJsonStream(reader, onQuestionsChunk, onProgressChunk)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun parseJsonStream(
        reader: JsonReader,
        onQuestionsChunk: suspend (List<QuestionJson>) -> Unit,
        onProgressChunk: suspend (List<ProgressJson>) -> Unit
    ) {
        if (!reader.hasNext()) return
        val token = reader.peek()
        if (token == JsonToken.BEGIN_OBJECT) {
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                when (name) {
                    "questions" -> parseQuestionsArray(reader, onQuestionsChunk)
                    "progress" -> parseProgressArray(reader, onProgressChunk)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } else if (token == JsonToken.BEGIN_ARRAY) {
            parseQuestionsArray(reader, onQuestionsChunk)
        }
    }

    private suspend fun parseQuestionsArray(
        reader: JsonReader,
        onQuestionsChunk: suspend (List<QuestionJson>) -> Unit
    ) {
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            val chunk = mutableListOf<QuestionJson>()
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    val q = parseSingleQuestion(reader)
                    if (q != null) chunk.add(q)
                    if (chunk.size >= 1000) {
                        onQuestionsChunk(chunk.toList())
                        chunk.clear()
                    }
                } else {
                    reader.skipValue()
                }
            }
            reader.endArray()
            if (chunk.isNotEmpty()) {
                onQuestionsChunk(chunk)
            }
        } else {
            reader.skipValue()
        }
    }

    private suspend fun parseProgressArray(
        reader: JsonReader,
        onProgressChunk: suspend (List<ProgressJson>) -> Unit
    ) {
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            val chunk = mutableListOf<ProgressJson>()
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    val p = parseSingleProgress(reader)
                    if (p != null) chunk.add(p)
                    if (chunk.size >= 1000) {
                        onProgressChunk(chunk.toList())
                        chunk.clear()
                    }
                } else {
                    reader.skipValue()
                }
            }
            reader.endArray()
            if (chunk.isNotEmpty()) {
                onProgressChunk(chunk)
            }
        } else {
            reader.skipValue()
        }
    }

    private fun parseSingleQuestion(reader: JsonReader): QuestionJson? {
        var subject = ""
        var topic = ""
        var question = ""
        var correct_answer = ""
        var explanation: String? = null
        var ai_explanation: String? = null
        var images: String? = null
        var audio: String? = null
        var source_url: String? = null
        val options = mutableListOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "subject" -> subject = nextStringOrSkip(reader) ?: ""
                "topic" -> topic = nextStringOrSkip(reader) ?: ""
                "question" -> question = nextStringOrSkip(reader) ?: ""
                "correct_answer" -> correct_answer = nextStringOrSkip(reader) ?: ""
                "explanation" -> explanation = nextStringOrSkip(reader)
                "ai_explanation" -> ai_explanation = nextStringOrSkip(reader)
                "images" -> images = nextStringOrSkip(reader)
                "audio" -> audio = nextStringOrSkip(reader)
                "source_url" -> source_url = nextStringOrSkip(reader)
                "options" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val opt = nextStringOrSkip(reader)
                            if (opt != null) options.add(opt)
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        
        if (question.isBlank()) return null
        
        return QuestionJson(
            subject = subject,
            topic = topic,
            question = question,
            options = options,
            correct_answer = correct_answer,
            explanation = explanation,
            ai_explanation = ai_explanation,
            images = images,
            audio = audio,
            source_url = source_url
        )
    }

    private fun parseSingleProgress(reader: JsonReader): ProgressJson? {
        var question = ""
        var subject = ""
        var topic = ""
        var selectedOption = ""
        var isCorrect = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "question" -> question = nextStringOrSkip(reader) ?: ""
                "subject" -> subject = nextStringOrSkip(reader) ?: ""
                "topic" -> topic = nextStringOrSkip(reader) ?: ""
                "selectedOption" -> selectedOption = nextStringOrSkip(reader) ?: ""
                "isCorrect" -> isCorrect = nextBooleanOrSkip(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (question.isBlank()) return null
        return ProgressJson(question, subject, topic, selectedOption, isCorrect)
    }

    private fun nextStringOrSkip(reader: JsonReader): String? {
        return try {
            val token = reader.peek()
            when (token) {
                JsonToken.STRING -> reader.nextString()
                JsonToken.NUMBER -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                JsonToken.NULL -> {
                    reader.nextNull()
                    null
                }
                else -> {
                    reader.skipValue()
                    null
                }
            }
        } catch (e: Exception) {
            try { reader.skipValue() } catch (ignored: Exception) {}
            null
        }
    }

    private fun nextBooleanOrSkip(reader: JsonReader): Boolean {
        return try {
            val token = reader.peek()
            when (token) {
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.STRING -> reader.nextString().toBoolean()
                else -> {
                    reader.skipValue()
                    false
                }
            }
        } catch (e: Exception) {
            try { reader.skipValue() } catch (ignored: Exception) {}
            false
        }
    }
}
