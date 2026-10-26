package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.McqField
import com.example.AppThemeMode
import com.example.local.AppDatabase
import com.example.local.BookmarkEntity
import com.example.local.SubjectTopicCount
import com.example.local.SourceCount
import com.example.local.toBookmarkEntity
import com.example.local.toEntity
import com.example.local.toField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import androidx.room.withTransaction

class McqRepository(private val context: Context, private val db: AppDatabase) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mcq_prefs", Context.MODE_PRIVATE)
    private val mcqDao = db.mcqDao()
    private val bookmarkDao = db.bookmarkDao()
    private val progressDao = db.progressDao()

    fun getTotalQuestionCount(): Flow<Int> = mcqDao.getTotalQuestionCount()

    suspend fun getUpdatedProgressSince(sinceTime: Long): List<com.example.local.ProgressEntity> = progressDao.getUpdatedProgressSince(sinceTime)
    
    suspend fun getUpdatedBookmarksSince(sinceTime: Long): List<com.example.local.BookmarkEntity> = bookmarkDao.getUpdatedBookmarksSince(sinceTime)
    
    suspend fun getBookmarkSync(question: String): com.example.local.BookmarkEntity? = bookmarkDao.getBookmarkSync(question)
    
    suspend fun saveBookmarkSync(bookmark: com.example.local.BookmarkEntity) = bookmarkDao.insertBookmark(bookmark)

    fun getAllProgress(): Flow<List<com.example.local.ProgressEntity>> = progressDao.getAllProgressFlow()
    
    suspend fun getAllProgressSync(): List<com.example.local.ProgressEntity> = progressDao.getAllProgress()
    
    suspend fun getAllQuestionsSync(): List<McqField> = mcqDao.getAllQuestionsSync().map { it.toField() }
    
    suspend fun getQuestionsCountSync(): Int = mcqDao.getQuestionsCountSync()
    suspend fun getAllQuestionIndexesSync(): List<com.example.local.McqLightweightEntity> = mcqDao.getAllQuestionIndexesSync()
    suspend fun getQuestionsChunk(limit: Int, offset: Int): List<McqField> = mcqDao.getQuestionsChunk(limit, offset).map { it.toField() }
    
    suspend fun getQuestionsByIds(ids: List<String>): List<McqField> {
        if (ids.isEmpty()) return emptyList()
        return mcqDao.getQuestionsByIds(ids).map { it.toField() }
    }
    
    suspend fun getProgressCountSync(): Int = progressDao.getProgressCountSync()
    suspend fun getProgressChunk(limit: Int, offset: Int): List<com.example.local.ProgressEntity> = progressDao.getProgressChunk(limit, offset)

    suspend fun getQuestionsBySubjects(subjects: List<String>): List<McqField> = mcqDao.getQuestionsBySubjects(subjects).map { it.toField() }

    suspend fun saveProgress(progress: com.example.local.ProgressEntity) {
        progressDao.insertProgress(progress)
    }

    suspend fun saveProgressList(progressList: List<com.example.local.ProgressEntity>) {
        progressDao.insertProgressList(progressList)
    }

    suspend fun clearProgress() {
        progressDao.deleteAll()
    }

    fun getAllQuestions(): Flow<List<McqField>> {
        return mcqDao.getInitialQuestions().map { entities -> entities.map { it.toField() } }
    }

    suspend fun getFilteredQuestions(subject: String?, topic: String?, sourceUrl: String?, searchQuery: String, limit: Int = 100): List<McqField> {
        if (searchQuery.isBlank() && sourceUrl.isNullOrBlank()) {
            if (subject != null && topic != null) {
                return mcqDao.getQuestionsBySubjectAndTopic(subject, topic, limit).map { it.toField() }
            } else if (subject != null && topic == null) {
                return mcqDao.getQuestionsBySubject(subject, limit).map { it.toField() }
            }
        }
        return mcqDao.getFilteredQuestions(subject, topic, sourceUrl, searchQuery, limit).map { it.toField() }
    }

    suspend fun getAdvancedFilteredQuestions(subject: String?, searchQuery: String, isBookmarked: Int, status: Int, limit: Int = 100): List<McqField> {
        return mcqDao.getAdvancedFilteredQuestions(subject, searchQuery, isBookmarked, status, limit).map { it.toField() }
    }

    fun getSubjectTopicCounts(): Flow<List<SubjectTopicCount>> {
        return mcqDao.getSubjectTopicCounts()
    }

    fun getSourceCounts(): Flow<List<SourceCount>> {
        return mcqDao.getSourceCounts()
    }

    fun getCustomSourceCountsFlow(): Flow<List<SourceCount>> =
        mcqDao.getCustomSourceCounts()

    fun getTopicsForSource(sourceUrl: String): Flow<List<SubjectTopicCount>> {
        return mcqDao.getTopicsForSource(sourceUrl)
    }

    fun getAllBookmarks(): Flow<List<McqField>> {
        return bookmarkDao.getAllBookmarks().map { entities -> entities.map { it.toField() } }
    }

    suspend fun insertQuestions(fields: List<McqField>) {
        mcqDao.insertAll(fields.map { it.toEntity() })
    }

    suspend fun deleteAllQuestions() {
        mcqDao.deleteAll()
    }

    suspend fun deleteQuestionsBySource(sourceName: String?) {
        if (sourceName == null) {
            mcqDao.deleteNullSource()
        } else {
            mcqDao.deleteBySource(sourceName)
        }
    }

    suspend fun toggleBookmark(question: McqField, isBookmarked: Boolean) {
        if (isBookmarked) {
            bookmarkDao.insertBookmark(question.toBookmarkEntity())
        } else {
            bookmarkDao.deleteBookmark(question.question)
        }
    }

    suspend fun updateAiExplanation(question: String, aiExplanation: String, isBookmarked: Boolean) {
        mcqDao.updateAiExplanation(question, aiExplanation)
        if (isBookmarked) {
            bookmarkDao.updateAiExplanation(question, aiExplanation)
        }
    }

    suspend fun updateVoiceAudio(question: String, audio: String) {
        mcqDao.updateVoiceAudio(question, audio)
        bookmarkDao.updateVoiceAudio(question, audio)
    }

    suspend fun getAdvancedFilteredQuestionsCount(subject: String?, searchQuery: String, isBookmarked: Int, status: Int): Int {
        return mcqDao.getAdvancedFilteredQuestionsCount(subject, searchQuery, isBookmarked, status)
    }

    suspend fun updateAiExplanationAndAudio(question: String, aiExplanation: String, audio: String) {
        mcqDao.updateAiExplanationAndAudio(question, aiExplanation, audio)
        bookmarkDao.updateAiExplanationAndAudio(question, aiExplanation, audio)
    }

    fun getAllCollectionsFlow(): Flow<List<com.example.McqCollectionItem>> {
        return mcqDao.getAllCollections().map { list ->
            list.map { col ->
                com.example.McqCollectionItem(
                    id = col.id,
                    name = col.name,
                    sourceProvider = col.sourceProvider,
                    importedAt = col.importedAt
                )
            }
        }
    }

    fun getSectionsForCollectionFlow(collectionId: String): Flow<List<com.example.McqSectionItem>> {
        return mcqDao.getSectionsForCollection(collectionId).map { list ->
            list.map { sec ->
                com.example.McqSectionItem(
                    id = sec.id,
                    collectionId = sec.collectionId,
                    name = sec.name,
                    sectionType = sec.sectionType,
                    displayOrder = sec.displayOrder
                )
            }
        }
    }

    fun getSetsForCollectionFlow(collectionId: String): Flow<List<com.example.McqSetItem>> {
        return mcqDao.getSetsForCollection(collectionId).map { list ->
            list.map { s ->
                com.example.McqSetItem(
                    id = s.id,
                    collectionId = s.collectionId,
                    sectionId = s.sectionId,
                    sourceSetId = s.sourceSetId,
                    title = s.title,
                    setType = s.setType,
                    subject = s.subject,
                    topic = s.topic,
                    questionCount = s.questionCount,
                    durationMinutes = s.durationMinutes,
                    totalMarks = s.totalMarks,
                    displayOrder = s.displayOrder
                )
            }
        }
    }

    fun getSetsForSectionFlow(sectionId: String): Flow<List<com.example.McqSetItem>> {
        return mcqDao.getSetsForSection(sectionId).map { list ->
            list.map { s ->
                com.example.McqSetItem(
                    id = s.id,
                    collectionId = s.collectionId,
                    sectionId = s.sectionId,
                    sourceSetId = s.sourceSetId,
                    title = s.title,
                    setType = s.setType,
                    subject = s.subject,
                    topic = s.topic,
                    questionCount = s.questionCount,
                    durationMinutes = s.durationMinutes,
                    totalMarks = s.totalMarks,
                    displayOrder = s.displayOrder
                )
            }
        }
    }

    fun getUnsectionedSetsForCollectionFlow(collectionId: String): Flow<List<com.example.McqSetItem>> {
        return mcqDao.getUnsectionedSetsForCollection(collectionId).map { list ->
            list.map { s ->
                com.example.McqSetItem(
                    id = s.id,
                    collectionId = s.collectionId,
                    sectionId = s.sectionId,
                    sourceSetId = s.sourceSetId,
                    title = s.title,
                    setType = s.setType,
                    subject = s.subject,
                    topic = s.topic,
                    questionCount = s.questionCount,
                    durationMinutes = s.durationMinutes,
                    totalMarks = s.totalMarks,
                    displayOrder = s.displayOrder
                )
            }
        }
    }

    suspend fun getQuestionsForSet(setId: String): List<McqField> {
        val entities = mcqDao.getQuestionsForSet(setId)
        if (entities.isNotEmpty()) {
            return entities.map { it.toField() }
        }
        // Fallback: check if sourceUrl matches custom://
        return mcqDao.getFilteredQuestions(null, null, setId, "", 2500).map { it.toField() }
    }

    suspend fun saveCanonicalCollection(collection: com.example.data.CanonicalCollection) {
        db.withTransaction {
            val colEntity = com.example.local.CollectionEntity(
                id = collection.id,
                name = collection.name,
                sourceProvider = collection.sourceProvider
            )
            mcqDao.insertCollection(colEntity)

            collection.sections.forEach { sec ->
                val secEntity = com.example.local.SectionEntity(
                    id = sec.id,
                    collectionId = collection.id,
                    name = sec.name,
                    sectionType = sec.sectionType,
                    displayOrder = sec.displayOrder
                )
                mcqDao.insertSections(listOf(secEntity))

                sec.sets.forEach { set ->
                    saveCanonicalSetInternal(collection.id, sec.id, set)
                }
            }

            collection.unsectionedSets.forEach { set ->
                saveCanonicalSetInternal(collection.id, null, set)
            }
        }
    }

    private suspend fun saveCanonicalSetInternal(collectionId: String, sectionId: String?, set: com.example.data.CanonicalSet) {
        val setEntity = com.example.local.McqSetEntity(
            id = set.id,
            collectionId = collectionId,
            sectionId = sectionId,
            sourceSetId = set.sourceSetId,
            title = set.title,
            setType = set.setType,
            subject = set.subject,
            topic = set.topic,
            questionCount = set.questions.size,
            durationMinutes = set.durationMinutes,
            totalMarks = set.totalMarks,
            displayOrder = set.displayOrder
        )
        mcqDao.insertSet(setEntity)

        // Clear existing cross refs if updating set
        mcqDao.deleteCrossRefsForSet(set.id)

        val crossRefs = mutableListOf<com.example.local.SetQuestionCrossRef>()
        set.questions.forEachIndexed { order, qField ->
            val entity = qField.toEntity()
            val existing = mcqDao.findQuestionByText(qField.question)
            val qId = if (existing != null) {
                existing.id
            } else {
                mcqDao.insertQuestionSingle(entity).toInt()
            }
            if (qId > 0) {
                crossRefs.add(com.example.local.SetQuestionCrossRef(setId = set.id, questionId = qId, displayOrder = order))
            }
        }
        if (crossRefs.isNotEmpty()) {
            mcqDao.insertCrossRefs(crossRefs)
        }
    }

    suspend fun deleteSet(setId: String) {
        db.withTransaction {
            mcqDao.deleteCrossRefsForSet(setId)
            mcqDao.deleteSetById(setId)
            if (setId.startsWith("custom://")) {
                mcqDao.deleteBySource(setId)
            }
        }
    }

    suspend fun deleteCollection(collectionId: String) {
        db.withTransaction {
            mcqDao.deleteSetsForCollection(collectionId)
            mcqDao.deleteSectionsForCollection(collectionId)
            mcqDao.deleteCollectionById(collectionId)
        }
    }


// Refactored settings & stats into SettingsRepository and StatsRepository
}

