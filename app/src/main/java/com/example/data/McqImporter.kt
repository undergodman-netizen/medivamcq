package com.example.data

import com.example.McqField
import com.example.McqCollectionItem
import com.example.McqSectionItem
import com.example.McqSetItem
import com.example.ImportPreviewReport
import com.example.SetSummaryPreview
import org.json.JSONArray
import org.json.JSONObject

data class CanonicalSet(
    val id: String,
    val sourceSetId: String? = null,
    val title: String,
    val setType: String = "PRACTICE", // "PRACTICE" or "TEST"
    val subject: String? = null,
    val topic: String? = null,
    val durationMinutes: Int? = null,
    val totalMarks: Int? = null,
    val displayOrder: Int = 0,
    val questions: List<McqField> = emptyList()
)

data class CanonicalSection(
    val id: String,
    val name: String,
    val sectionType: String? = "subject",
    val displayOrder: Int = 0,
    val sets: List<CanonicalSet> = emptyList()
)

data class CanonicalCollection(
    val id: String,
    val name: String,
    val sourceProvider: String? = null,
    val sections: List<CanonicalSection> = emptyList(),
    val unsectionedSets: List<CanonicalSet> = emptyList()
)

object McqImporter {

    fun parseToCanonicalCollection(
        jsonString: String,
        sourceName: String? = null,
        errorCollector: MutableList<String>? = null
    ): CanonicalCollection {
        val trimmed = jsonString.trim()
        val defaultColName = sourceName?.removePrefix("custom://")
            ?.substringBeforeLast('.')
            ?.replace(Regex("[_\\-]"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() } ?: "Custom Question Bank"
        
        val colId = "col_" + (sourceName ?: defaultColName).hashCode().let { kotlin.math.abs(it) }

        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            return parseJsonArrayInput(jsonArray, colId, defaultColName, sourceName, errorCollector)
        } else if (trimmed.startsWith("{")) {
            val jsonObject = JSONObject(trimmed)
            return parseJsonObjectInput(jsonObject, colId, defaultColName, sourceName, errorCollector)
        } else {
            errorCollector?.add("File format error: input is not valid JSON")
            return CanonicalCollection(id = colId, name = defaultColName)
        }
    }

    private fun parseJsonArrayInput(
        array: JSONArray,
        colId: String,
        defaultColName: String,
        sourceName: String?,
        errorCollector: MutableList<String>?
    ): CanonicalCollection {
        if (array.length() == 0) return CanonicalCollection(id = colId, name = defaultColName)

        val firstElem = array.opt(0)
        if (firstElem is JSONObject && (firstElem.has("questions") || firstElem.has("set_name") || firstElem.has("title") || firstElem.has("sets"))) {
            // Array of Set objects
            val sets = mutableListOf<CanonicalSet>()
            for (i in 0 until array.length()) {
                val setObj = array.optJSONObject(i) ?: continue
                val set = parseSingleSetObject(setObj, colId, i, sourceName, errorCollector)
                if (set.questions.isNotEmpty()) {
                    sets.add(set)
                }
            }
            return CanonicalCollection(id = colId, name = defaultColName, unsectionedSets = sets)
        } else {
            // Flat question array
            val questions = mutableListOf<McqField>()
            for (i in 0 until array.length()) {
                val qObj = array.optJSONObject(i) ?: continue
                val field = parseSingleQuestionObject(qObj, sourceName, errorCollector)
                if (field != null) {
                    questions.add(field)
                }
            }
            return groupFlatQuestionsIntoCanonicalCollection(questions, colId, defaultColName, sourceName)
        }
    }

    private fun parseJsonObjectInput(
        obj: JSONObject,
        colId: String,
        defaultColName: String,
        sourceName: String?,
        errorCollector: MutableList<String>?
    ): CanonicalCollection {
        val collectionTitle = obj.optString("collection_name", obj.optString("name", obj.optString("title", defaultColName))).ifBlank { defaultColName }
        val provider = obj.optString("source_provider", obj.optString("provider", "Custom File"))

        // Check 1: Core BTR format (Qbank subjects + categorized Tests)
        if (obj.has("qbank") || obj.has("tests") || obj.has("Qbank") || obj.has("Tests")) {
            return parseCoreBtrFormat(obj, colId, collectionTitle, provider, sourceName, errorCollector)
        }

        // Check 2: Nested Collections / Subjects structure
        if (obj.has("subjects") || obj.has("sections") || obj.has("categories")) {
            val sectionsArr = obj.optJSONArray("subjects") ?: obj.optJSONArray("sections") ?: obj.optJSONArray("categories")
            if (sectionsArr != null) {
                val sections = mutableListOf<CanonicalSection>()
                for (i in 0 until sectionsArr.length()) {
                    val secObj = sectionsArr.optJSONObject(i) ?: continue
                    val secName = secObj.optString("name", secObj.optString("subject", "Section ${i + 1}"))
                    val secId = "${colId}_sec_$i"
                    val setsArr = secObj.optJSONArray("sets") ?: secObj.optJSONArray("topics")
                    val setsList = mutableListOf<CanonicalSet>()
                    if (setsArr != null) {
                        for (j in 0 until setsArr.length()) {
                            val setObj = setsArr.optJSONObject(j) ?: continue
                            val parsedSet = parseSingleSetObject(setObj, colId, j, sourceName, errorCollector, sectionId = secId)
                            if (parsedSet.questions.isNotEmpty()) setsList.add(parsedSet)
                        }
                    }
                    if (setsList.isNotEmpty()) {
                        sections.add(CanonicalSection(id = secId, name = secName, displayOrder = i, sets = setsList))
                    }
                }
                return CanonicalCollection(id = colId, name = collectionTitle, sourceProvider = provider, sections = sections)
            }
        }

        // Check 3: Single Set Object
        if (obj.has("questions") || obj.has("fields") || obj.has("results")) {
            val set = parseSingleSetObject(obj, colId, 0, sourceName, errorCollector)
            return CanonicalCollection(id = colId, name = collectionTitle, sourceProvider = provider, unsectionedSets = listOf(set))
        }

        // Fallback: check if the object itself is a single question
        val singleQ = parseSingleQuestionObject(obj, sourceName, errorCollector)
        if (singleQ != null) {
            val set = CanonicalSet(
                id = "${colId}_set_0",
                title = collectionTitle,
                questions = listOf(singleQ)
            )
            return CanonicalCollection(id = colId, name = collectionTitle, unsectionedSets = listOf(set))
        }

        errorCollector?.add("Could not find any valid questions or set structures in JSON object.")
        return CanonicalCollection(id = colId, name = collectionTitle)
    }

    private fun parseCoreBtrFormat(
        obj: JSONObject,
        colId: String,
        collectionTitle: String,
        provider: String,
        sourceName: String?,
        errorCollector: MutableList<String>?
    ): CanonicalCollection {
        val sections = mutableListOf<CanonicalSection>()
        var secOrder = 0

        // Parse Qbank subjects
        val qbankObj = obj.optJSONObject("qbank") ?: obj.optJSONObject("Qbank")
        if (qbankObj != null) {
            val keys = qbankObj.keys()
            while (keys.hasNext()) {
                val subName = keys.next()
                val secId = "${colId}_sec_qbank_${subName.hashCode().let { kotlin.math.abs(it) }}"
                val setsList = mutableListOf<CanonicalSet>()
                val setsVal = qbankObj.get(subName)
                if (setsVal is JSONArray) {
                    for (i in 0 until setsVal.length()) {
                        val setObj = setsVal.optJSONObject(i) ?: continue
                        val set = parseSingleSetObject(setObj, colId, i, sourceName, errorCollector, defaultSetType = "PRACTICE", defaultSubject = subName, sectionId = secId)
                        if (set.questions.isNotEmpty()) setsList.add(set)
                    }
                } else if (setsVal is JSONObject) {
                    val topicKeys = setsVal.keys()
                    var topicOrder = 0
                    while (topicKeys.hasNext()) {
                        val topicName = topicKeys.next()
                        val topicVal = setsVal.get(topicName)
                        val qList = mutableListOf<McqField>()
                        if (topicVal is JSONArray) {
                            for (qIdx in 0 until topicVal.length()) {
                                val qObj = topicVal.optJSONObject(qIdx) ?: continue
                                val q = parseSingleQuestionObject(qObj, sourceName, errorCollector, defaultSubject = subName, defaultTopic = topicName)
                                if (q != null) qList.add(q)
                            }
                        }
                        if (qList.isNotEmpty()) {
                            val setId = "${secId}_set_${topicName.hashCode().let { kotlin.math.abs(it) }}"
                            setsList.add(
                                CanonicalSet(
                                    id = setId,
                                    sourceSetId = topicName,
                                    title = topicName,
                                    setType = "PRACTICE",
                                    subject = subName,
                                    topic = topicName,
                                    displayOrder = topicOrder++,
                                    questions = qList
                                )
                            )
                        }
                    }
                }
                if (setsList.isNotEmpty()) {
                    sections.add(CanonicalSection(id = secId, name = "$subName (Qbank)", sectionType = "subject", displayOrder = secOrder++, sets = setsList))
                }
            }
        }

        // Parse Tests categories (Grand Tests, Mini Tests, etc.)
        val testsObj = obj.optJSONObject("tests") ?: obj.optJSONObject("Tests")
        if (testsObj != null) {
            val categoryKeys = testsObj.keys()
            while (categoryKeys.hasNext()) {
                val catName = categoryKeys.next()
                val secId = "${colId}_sec_test_${catName.hashCode().let { kotlin.math.abs(it) }}"
                val setsList = mutableListOf<CanonicalSet>()
                val testsVal = testsObj.get(catName)
                if (testsVal is JSONArray) {
                    for (i in 0 until testsVal.length()) {
                        val testObj = testsVal.optJSONObject(i) ?: continue
                        val set = parseSingleSetObject(testObj, colId, i, sourceName, errorCollector, defaultSetType = "TEST", defaultSubject = catName, sectionId = secId)
                        if (set.questions.isNotEmpty()) setsList.add(set)
                    }
                }
                if (setsList.isNotEmpty()) {
                    sections.add(CanonicalSection(id = secId, name = catName, sectionType = "test_category", displayOrder = secOrder++, sets = setsList))
                }
            }
        }

        return CanonicalCollection(id = colId, name = collectionTitle, sourceProvider = provider, sections = sections)
    }

    private fun parseSingleSetObject(
        obj: JSONObject,
        colId: String,
        order: Int,
        sourceName: String?,
        errorCollector: MutableList<String>?,
        defaultSetType: String = "PRACTICE",
        defaultSubject: String? = null,
        sectionId: String? = null
    ): CanonicalSet {
        val sourceSetId = obj.optString("id", obj.optString("set_id", obj.optString("code", null)))
        val title = obj.optString("title", obj.optString("set_name", obj.optString("name", "Set ${order + 1}")))
        val setType = obj.optString("set_type", obj.optString("type", defaultSetType)).uppercase()
        val subject = obj.optString("subject", defaultSubject ?: "General")
        val topic = obj.optString("topic", "General")
        val duration = if (obj.has("duration_minutes")) obj.optInt("duration_minutes") else if (obj.has("time_limit_mins")) obj.optInt("time_limit_mins") else null
        val marks = if (obj.has("total_marks")) obj.optInt("total_marks") else null

        val questionsArr = obj.optJSONArray("questions") ?: obj.optJSONArray("fields") ?: obj.optJSONArray("results")
        val qList = mutableListOf<McqField>()

        if (questionsArr != null) {
            for (i in 0 until questionsArr.length()) {
                val qObj = questionsArr.optJSONObject(i) ?: continue
                val q = parseSingleQuestionObject(qObj, sourceName, errorCollector, defaultSubject = subject, defaultTopic = topic)
                if (q != null) qList.add(q)
            }
        }

        val setId = "${colId}_set_${sourceSetId ?: title.hashCode().let { kotlin.math.abs(it) }}_$order"

        return CanonicalSet(
            id = setId,
            sourceSetId = sourceSetId,
            title = title,
            setType = if (setType == "TEST" || setType == "MOCK" || setType == "EXAM") "TEST" else "PRACTICE",
            subject = subject,
            topic = topic,
            durationMinutes = duration,
            totalMarks = marks,
            displayOrder = order,
            questions = qList
        )
    }

    fun parseSingleQuestionObject(
        obj: JSONObject,
        sourceName: String?,
        errorCollector: MutableList<String>?,
        defaultSubject: String? = null,
        defaultTopic: String? = null
    ): McqField? {
        // Field normalization with alias lookup
        val questionText = optStringAlias(obj, "question", "text", "question_text", "stem", "title")?.trim() ?: ""
        if (questionText.isBlank()) return null

        val subjectText = optStringAlias(obj, "subject", "category", "domain") ?: defaultSubject ?: "General"
        val topicText = optStringAlias(obj, "topic", "subtopic", "unit") ?: defaultTopic ?: "General"
        val explanationText = optStringAlias(obj, "explanation", "exp_html", "solution", "explain", "rationale")
        val aiExplanationText = optStringAlias(obj, "ai_explanation", "aiExplanation")
        val imagesText = optStringAlias(obj, "images", "image", "question_images")
        val audioText = optStringAlias(obj, "audio", "sound")
        val sourceUrlText = optStringAlias(obj, "source_url", "source", "url") ?: sourceName ?: "custom://import"

        // Process Options and Correct Answer
        val optionsList = mutableListOf<String>()
        var correctAnswerText = ""

        if (obj.has("options") || obj.has("choices") || obj.has("answers")) {
            val optVal = obj.opt("options") ?: obj.opt("choices") ?: obj.opt("answers")
            if (optVal is JSONArray) {
                for (i in 0 until optVal.length()) {
                    val item = optVal.get(i)
                    if (item is JSONObject) {
                        val optText = optStringAlias(item, "text", "option", "choice", "label") ?: ""
                        if (optText.isNotBlank()) {
                            optionsList.add(optText)
                            val isCorrect = item.optBoolean("is_correct", item.optBoolean("correct", false))
                            if (isCorrect) {
                                correctAnswerText = optText
                            }
                        }
                    } else {
                        val optText = item.toString().trim()
                        if (optText.isNotBlank()) {
                            optionsList.add(optText)
                        }
                    }
                }
            }
        }

        if (correctAnswerText.isBlank()) {
            correctAnswerText = optStringAlias(obj, "correct_answer", "correctAnswer", "correct_option", "answer") ?: ""
        }

        // Schema validation & default fallback
        val validOptions = when {
            optionsList.isEmpty() -> listOf("True", "False")
            optionsList.size == 1 -> listOf(optionsList[0], "None of the above")
            else -> optionsList
        }

        val normalizedCorrect = when {
            correctAnswerText.isNotBlank() -> {
                validOptions.find { opt ->
                    opt.startsWith("$correctAnswerText.", ignoreCase = true) ||
                    opt.startsWith("$correctAnswerText)", ignoreCase = true) ||
                    opt.startsWith("$correctAnswerText ", ignoreCase = true) ||
                    opt.equals(correctAnswerText, ignoreCase = true)
                } ?: correctAnswerText
            }
            else -> validOptions.first()
        }

        return McqField(
            subject = subjectText,
            topic = topicText,
            question = questionText,
            options = validOptions,
            correct_answer = normalizedCorrect,
            explanation = explanationText,
            ai_explanation = aiExplanationText,
            images = imagesText,
            audio = audioText,
            source_url = sourceUrlText
        )
    }

    private fun optStringAlias(obj: JSONObject, vararg aliases: String): String? {
        for (alias in aliases) {
            if (obj.has(alias) && !obj.isNull(alias)) {
                val valStr = obj.optString(alias, "").trim()
                if (valStr.isNotBlank()) return valStr
            }
        }
        return null
    }

    private fun groupFlatQuestionsIntoCanonicalCollection(
        questions: List<McqField>,
        colId: String,
        defaultColName: String,
        sourceName: String?
    ): CanonicalCollection {
        if (questions.isEmpty()) return CanonicalCollection(id = colId, name = defaultColName)

        val groupedBySubject = questions.groupBy { it.subject ?: "General" }
        if (groupedBySubject.size > 1) {
            val sections = mutableListOf<CanonicalSection>()
            var secOrder = 0
            groupedBySubject.forEach { (subName, subQuestions) ->
                val secId = "${colId}_sec_${subName.hashCode().let { kotlin.math.abs(it) }}"
                val topicGroups = subQuestions.groupBy { it.topic ?: "General" }
                val setsList = mutableListOf<CanonicalSet>()
                var setOrder = 0
                topicGroups.forEach { (topName, topQuestions) ->
                    val setId = "${secId}_set_${topName.hashCode().let { kotlin.math.abs(it) }}"
                    setsList.add(
                        CanonicalSet(
                            id = setId,
                            title = topName,
                            setType = "PRACTICE",
                            subject = subName,
                            topic = topName,
                            displayOrder = setOrder++,
                            questions = topQuestions
                        )
                    )
                }
                sections.add(
                    CanonicalSection(
                        id = secId,
                        name = subName,
                        displayOrder = secOrder++,
                        sets = setsList
                    )
                )
            }
            return CanonicalCollection(id = colId, name = defaultColName, sections = sections)
        } else {
            // Single subject, group by topic or single set
            val subName = groupedBySubject.keys.first()
            val topicGroups = questions.groupBy { it.topic ?: "General" }
            val setsList = mutableListOf<CanonicalSet>()
            var setOrder = 0
            topicGroups.forEach { (topName, topQuestions) ->
                val setId = "${colId}_set_${topName.hashCode().let { kotlin.math.abs(it) }}"
                setsList.add(
                    CanonicalSet(
                        id = setId,
                        title = if (topName != "General") topName else defaultColName,
                        setType = "PRACTICE",
                        subject = subName,
                        topic = topName,
                        displayOrder = setOrder++,
                        questions = topQuestions
                    )
                )
            }
            return CanonicalCollection(id = colId, name = defaultColName, unsectionedSets = setsList)
        }
    }

    fun buildPreviewReport(collection: CanonicalCollection, warnings: List<String> = emptyList()): ImportPreviewReport {
        val summaries = mutableListOf<SetSummaryPreview>()
        var totalQs = 0

        val allSets = collection.unsectionedSets + collection.sections.flatMap { sec ->
            sec.sets.map { set -> set to sec.name }
        }.map { (set, secName) -> set }

        collection.unsectionedSets.forEach { set ->
            totalQs += set.questions.size
            summaries.add(
                SetSummaryPreview(
                    setId = set.id,
                    title = set.title,
                    sectionName = null,
                    setType = set.setType,
                    questionCount = set.questions.size,
                    durationMinutes = set.durationMinutes
                )
            )
        }

        collection.sections.forEach { sec ->
            sec.sets.forEach { set ->
                totalQs += set.questions.size
                summaries.add(
                    SetSummaryPreview(
                        setId = set.id,
                        title = set.title,
                        sectionName = sec.name,
                        setType = set.setType,
                        questionCount = set.questions.size,
                        durationMinutes = set.durationMinutes
                    )
                )
            }
        }

        return ImportPreviewReport(
            collectionName = collection.name,
            collectionsCount = 1,
            sectionsCount = collection.sections.size,
            setsCount = summaries.size,
            totalQuestions = totalQs,
            setSummaries = summaries,
            warnings = warnings
        )
    }
}
