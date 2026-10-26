package com.example.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.McqField
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import androidx.room.Index

import androidx.room.ForeignKey

@Entity(
    tableName = "mcq_questions",
    indices = [
        Index(value = ["subject", "topic"]),
        Index(value = ["sourceUrl"])
    ]
)
data class McqEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String?,
    val topic: String?,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String?,
    val aiExplanation: String?,
    val images: String?,
    val audio: String?,
    val sourceUrl: String?
)

@Entity(tableName = "mcq_collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceProvider: String?,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mcq_sections",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId")]
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val name: String,
    val sectionType: String? = "subject",
    val displayOrder: Int = 0
)

@Entity(
    tableName = "mcq_sets",
    indices = [Index("collectionId"), Index("sectionId"), Index("sourceSetId")]
)
data class McqSetEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val sectionId: String?,
    val sourceSetId: String?,
    val title: String,
    val setType: String = "PRACTICE", // "PRACTICE" or "TEST"
    val subject: String?,
    val topic: String?,
    val questionCount: Int,
    val durationMinutes: Int?,
    val totalMarks: Int?,
    val displayOrder: Int = 0
)

@Entity(
    tableName = "set_question_cross_ref",
    primaryKeys = ["setId", "questionId", "displayOrder"],
    indices = [Index("setId"), Index("questionId")]
)
data class SetQuestionCrossRef(
    val setId: String,
    val questionId: Int,
    val displayOrder: Int = 0
)

class McqTypeConverters {
    @TypeConverter
    fun fromList(list: List<String>): String {
        return Json.encodeToString(list)
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class SubjectTopicCount(
    val subject: String?,
    val topic: String?,
    val count: Int
)

data class SourceCount(
    val sourceUrl: String?,
    val count: Int
)

data class McqLightweightEntity(
    val question: String,
    val subject: String?,
    val topic: String?,
    val sourceUrl: String?
)

@Dao
interface McqDao {
    @Query("SELECT * FROM mcq_questions ORDER BY id DESC")
    fun getAllQuestions(): Flow<List<McqEntity>>

    @Query("SELECT question, subject, topic, sourceUrl FROM mcq_questions ORDER BY id DESC")
    suspend fun getAllQuestionIndexesSync(): List<McqLightweightEntity>

    @Query("SELECT subject, topic, COUNT(*) as count FROM mcq_questions GROUP BY subject, topic")
    fun getSubjectTopicCounts(): Flow<List<SubjectTopicCount>>

    @Query("SELECT sourceUrl, COUNT(*) as count FROM mcq_questions GROUP BY sourceUrl")
    fun getSourceCounts(): Flow<List<SourceCount>>

    @Query("SELECT sourceUrl, COUNT(*) as count FROM mcq_questions WHERE sourceUrl LIKE 'custom://%' GROUP BY sourceUrl ORDER BY sourceUrl DESC")
    fun getCustomSourceCounts(): Flow<List<SourceCount>>

    @Query("""
        SELECT subject, topic, COUNT(*) as count 
        FROM mcq_questions 
        WHERE (:sourceUrl IS NULL OR sourceUrl = :sourceUrl) OR (:sourceUrl = '' AND (sourceUrl IS NULL OR sourceUrl = ''))
        GROUP BY subject, topic
    """)
    fun getTopicsForSource(sourceUrl: String): Flow<List<SubjectTopicCount>>

    @Query("""
        SELECT * FROM mcq_questions 
        WHERE subject = :subject
        AND topic = :topic
        ORDER BY id DESC LIMIT :limit
    """)
    suspend fun getQuestionsBySubjectAndTopic(subject: String, topic: String, limit: Int): List<McqEntity>

    @Query("""
        SELECT * FROM mcq_questions 
        WHERE subject = :subject
        ORDER BY id DESC LIMIT :limit
    """)
    suspend fun getQuestionsBySubject(subject: String, limit: Int): List<McqEntity>

    @Query("""
        SELECT * FROM mcq_questions 
        WHERE (:subject IS NULL OR subject = :subject)
        AND (:topic IS NULL OR topic = :topic)
        AND ((:sourceUrl IS NULL OR sourceUrl = :sourceUrl) OR (:sourceUrl = '' AND (sourceUrl IS NULL OR sourceUrl = '')))
        AND (:searchQuery = '' OR question LIKE '%' || :searchQuery || '%' OR subject LIKE '%' || :searchQuery || '%' OR topic LIKE '%' || :searchQuery || '%')
        ORDER BY id DESC LIMIT :limit
    """)
    suspend fun getFilteredQuestions(subject: String?, topic: String?, sourceUrl: String?, searchQuery: String, limit: Int): List<McqEntity>

    @Query("""
        SELECT * FROM mcq_questions 
        WHERE (:subject IS NULL OR subject = :subject)
        AND (:searchQuery = '' OR explanation LIKE '%' || :searchQuery || '%' OR question LIKE '%' || :searchQuery || '%')
        AND (:isBookmarked = 0 OR question IN (SELECT question FROM bookmarks))
        AND (:status = 0 OR (:status = 1 AND question NOT IN (SELECT question FROM progress_history)) OR (:status = 2 AND question IN (SELECT question FROM progress_history WHERE isCorrect = 1)) OR (:status = 3 AND question IN (SELECT question FROM progress_history WHERE isCorrect = 0)))
        ORDER BY RANDOM() LIMIT :limit
    """)
    suspend fun getAdvancedFilteredQuestions(subject: String?, searchQuery: String, isBookmarked: Int, status: Int, limit: Int): List<McqEntity>

    @Query("""
        SELECT COUNT(*) FROM mcq_questions 
        WHERE (:subject IS NULL OR subject = :subject)
        AND (:searchQuery = '' OR explanation LIKE '%' || :searchQuery || '%' OR question LIKE '%' || :searchQuery || '%')
        AND (:isBookmarked = 0 OR question IN (SELECT question FROM bookmarks))
        AND (:status = 0 OR (:status = 1 AND question NOT IN (SELECT question FROM progress_history)) OR (:status = 2 AND question IN (SELECT question FROM progress_history WHERE isCorrect = 1)) OR (:status = 3 AND question IN (SELECT question FROM progress_history WHERE isCorrect = 0)))
    """)
    suspend fun getAdvancedFilteredQuestionsCount(subject: String?, searchQuery: String, isBookmarked: Int, status: Int): Int

    @Query("SELECT * FROM mcq_questions ORDER BY id DESC LIMIT 500")
    fun getInitialQuestions(): Flow<List<McqEntity>>

    @Query("SELECT COUNT(*) FROM mcq_questions")
    fun getTotalQuestionCount(): Flow<Int>

    @Query("SELECT * FROM mcq_questions")
    suspend fun getAllQuestionsSync(): List<McqEntity>

    @Query("SELECT COUNT(*) FROM mcq_questions")
    suspend fun getQuestionsCountSync(): Int

    @Query("SELECT * FROM mcq_questions LIMIT :limit OFFSET :offset")
    suspend fun getQuestionsChunk(limit: Int, offset: Int): List<McqEntity>

    @Query("SELECT * FROM mcq_questions WHERE subject IN (:subjects) OR ('' IN (:subjects) AND (subject IS NULL OR subject = '')) LIMIT 2500")
    suspend fun getQuestionsBySubjects(subjects: List<String>): List<McqEntity>

    @Query("SELECT * FROM mcq_questions WHERE question IN (:questions)")
    suspend fun getQuestionsByIds(questions: List<String>): List<McqEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(questions: List<McqEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestionSingle(question: McqEntity): Long

    @Query("SELECT * FROM mcq_questions WHERE question = :questionText LIMIT 1")
    suspend fun findQuestionByText(questionText: String): McqEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<CollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<McqSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: McqSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<SetQuestionCrossRef>)

    @Query("SELECT * FROM mcq_collections ORDER BY importedAt DESC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM mcq_sections WHERE collectionId = :collectionId ORDER BY displayOrder ASC, name ASC")
    fun getSectionsForCollection(collectionId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM mcq_sets WHERE collectionId = :collectionId ORDER BY displayOrder ASC, title ASC")
    fun getSetsForCollection(collectionId: String): Flow<List<McqSetEntity>>

    @Query("SELECT * FROM mcq_sets WHERE sectionId = :sectionId ORDER BY displayOrder ASC, title ASC")
    fun getSetsForSection(sectionId: String): Flow<List<McqSetEntity>>

    @Query("SELECT * FROM mcq_sets WHERE collectionId = :collectionId AND (sectionId IS NULL OR sectionId = '') ORDER BY displayOrder ASC, title ASC")
    fun getUnsectionedSetsForCollection(collectionId: String): Flow<List<McqSetEntity>>

    @Query("SELECT * FROM mcq_sets WHERE id = :setId")
    suspend fun getSetById(setId: String): McqSetEntity?

    @Query("SELECT q.* FROM mcq_questions q INNER JOIN set_question_cross_ref ref ON q.id = ref.questionId WHERE ref.setId = :setId ORDER BY ref.displayOrder ASC")
    suspend fun getQuestionsForSet(setId: String): List<McqEntity>

    @Query("DELETE FROM mcq_sets WHERE id = :setId")
    suspend fun deleteSetById(setId: String)

    @Query("DELETE FROM set_question_cross_ref WHERE setId = :setId")
    suspend fun deleteCrossRefsForSet(setId: String)

    @Query("DELETE FROM mcq_collections WHERE id = :collectionId")
    suspend fun deleteCollectionById(collectionId: String)

    @Query("DELETE FROM mcq_sections WHERE collectionId = :collectionId")
    suspend fun deleteSectionsForCollection(collectionId: String)

    @Query("DELETE FROM mcq_sets WHERE collectionId = :collectionId")
    suspend fun deleteSetsForCollection(collectionId: String)

    @Query("DELETE FROM mcq_questions")
    suspend fun deleteAll()
    
    @Query("DELETE FROM mcq_questions WHERE subject = :subject")
    suspend fun deleteBySubject(subject: String)

    @Query("DELETE FROM mcq_questions WHERE sourceUrl = :sourceUrl")
    suspend fun deleteBySource(sourceUrl: String)

    @Query("DELETE FROM mcq_questions WHERE sourceUrl IS NULL OR sourceUrl = ''")
    suspend fun deleteNullSource()

    @Query("UPDATE mcq_questions SET aiExplanation = :aiExplanation WHERE question = :question")
    suspend fun updateAiExplanation(question: String, aiExplanation: String)

    @Query("UPDATE mcq_questions SET audio = :audio WHERE question = :question")
    suspend fun updateVoiceAudio(question: String, audio: String)

    @Query("UPDATE mcq_questions SET aiExplanation = :aiExplanation, audio = :audio WHERE question = :question")
    suspend fun updateAiExplanationAndAudio(question: String, aiExplanation: String, audio: String)
}

@Entity(tableName = "progress_history")
data class ProgressEntity(
    @PrimaryKey val question: String,
    val subject: String?,
    val topic: String?,
    val selectedOption: String,
    val isCorrect: Boolean,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress_history")
    suspend fun getAllProgress(): List<ProgressEntity>

    @Query("SELECT * FROM progress_history WHERE lastUpdated > :sinceTime ORDER BY lastUpdated ASC LIMIT :limit")
    suspend fun getUpdatedProgressSince(sinceTime: Long, limit: Int = 100): List<ProgressEntity>

    @Query("SELECT COUNT(*) FROM progress_history")
    suspend fun getProgressCountSync(): Int

    @Query("SELECT * FROM progress_history LIMIT :limit OFFSET :offset")
    suspend fun getProgressChunk(limit: Int, offset: Int): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressList(progressList: List<ProgressEntity>)

    @Query("SELECT * FROM progress_history")
    fun getAllProgressFlow(): Flow<List<ProgressEntity>>

    @Query("DELETE FROM progress_history")
    suspend fun deleteAll()
}

@Database(
    entities = [
        McqEntity::class,
        BookmarkEntity::class,
        ProgressEntity::class,
        CollectionEntity::class,
        SectionEntity::class,
        McqSetEntity::class,
        SetQuestionCrossRef::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(McqTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mcqDao(): McqDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE progress_history ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcq_collections (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        sourceProvider TEXT,
                        importedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcq_sections (
                        id TEXT NOT NULL PRIMARY KEY,
                        collectionId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        sectionType TEXT,
                        displayOrder INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(collectionId) REFERENCES mcq_collections(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mcq_sections_collectionId ON mcq_sections(collectionId)")
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcq_sets (
                        id TEXT NOT NULL PRIMARY KEY,
                        collectionId TEXT NOT NULL,
                        sectionId TEXT,
                        sourceSetId TEXT,
                        title TEXT NOT NULL,
                        setType TEXT NOT NULL DEFAULT 'PRACTICE',
                        subject TEXT,
                        topic TEXT,
                        questionCount INTEGER NOT NULL DEFAULT 0,
                        durationMinutes INTEGER,
                        totalMarks INTEGER,
                        displayOrder INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mcq_sets_collectionId ON mcq_sets(collectionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mcq_sets_sectionId ON mcq_sets(sectionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mcq_sets_sourceSetId ON mcq_sets(sourceSetId)")
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS set_question_cross_ref (
                        setId TEXT NOT NULL,
                        questionId INTEGER NOT NULL,
                        displayOrder INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(setId, questionId, displayOrder)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_set_question_cross_ref_setId ON set_question_cross_ref(setId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_set_question_cross_ref_questionId ON set_question_cross_ref(questionId)")

                database.execSQL("""
                    INSERT OR IGNORE INTO mcq_collections (id, name, sourceProvider, importedAt)
                    VALUES ('col_legacy', 'Default Library', 'App Internal', 0)
                """.trimIndent())

                database.execSQL("""
                    INSERT OR IGNORE INTO mcq_sets (id, collectionId, sectionId, sourceSetId, title, setType, subject, topic, questionCount, durationMinutes, totalMarks, displayOrder)
                    SELECT 
                        'set_legacy_default',
                        'col_legacy',
                        NULL,
                        'legacy_default',
                        'All Questions',
                        'PRACTICE',
                        'General',
                        'General',
                        COUNT(*),
                        NULL,
                        NULL,
                        0
                    FROM mcq_questions
                """.trimIndent())

                database.execSQL("""
                    INSERT OR IGNORE INTO set_question_cross_ref (setId, questionId, displayOrder)
                    SELECT 'set_legacy_default', id, id
                    FROM mcq_questions
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mcq_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}


@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val question: String,
    val subject: String?,
    val topic: String?,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String?,
    val aiExplanation: String?,
    val images: String?,
    val audio: String?,
    val sourceUrl: String?,
    val bookmarkedAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY bookmarkedAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE lastUpdated > :sinceTime ORDER BY lastUpdated ASC LIMIT :limit")
    suspend fun getUpdatedBookmarksSince(sinceTime: Long, limit: Int = 100): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE question = :question")
    suspend fun deleteBookmark(question: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE question = :question)")
    fun isBookmarked(question: String): Flow<Boolean>

    @Query("SELECT * FROM bookmarks WHERE question = :question")
    suspend fun getBookmarkSync(question: String): BookmarkEntity?

    @Query("UPDATE bookmarks SET aiExplanation = :aiExplanation, lastUpdated = :lastUpdated WHERE question = :question")
    suspend fun updateAiExplanation(question: String, aiExplanation: String, lastUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET audio = :audio, lastUpdated = :lastUpdated WHERE question = :question")
    suspend fun updateVoiceAudio(question: String, audio: String, lastUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET aiExplanation = :aiExplanation, audio = :audio, lastUpdated = :lastUpdated WHERE question = :question")
    suspend fun updateAiExplanationAndAudio(question: String, aiExplanation: String, audio: String, lastUpdated: Long = System.currentTimeMillis())
}

fun McqField.toEntity(): McqEntity {
    return McqEntity(
        subject = this.subject ?: "General",
        topic = this.topic ?: "General",
        question = this.question,
        options = this.options,
        correctAnswer = this.correct_answer,
        explanation = this.explanation,
        aiExplanation = this.ai_explanation,
        images = this.images,
        audio = this.audio,
        sourceUrl = this.source_url
    )
}

fun McqField.toBookmarkEntity(): BookmarkEntity {
    return BookmarkEntity(
        question = this.question,
        subject = this.subject ?: "General",
        topic = this.topic ?: "General",
        options = this.options,
        correctAnswer = this.correct_answer,
        explanation = this.explanation,
        aiExplanation = this.ai_explanation,
        images = this.images,
        audio = this.audio,
        sourceUrl = this.source_url
    )
}

fun McqEntity.toField(): McqField {
    val normalizedCorrect = options.find { opt ->
        val corr = this.correctAnswer
        if (corr.isBlank()) false
        else opt.startsWith("$corr.", ignoreCase = true) ||
             opt.startsWith("$corr)", ignoreCase = true) ||
             opt.startsWith("$corr ", ignoreCase = true) ||
             opt.equals(corr, ignoreCase = true)
    } ?: this.correctAnswer

    return McqField(
        subject = this.subject,
        topic = this.topic,
        question = this.question,
        options = this.options,
        correct_answer = normalizedCorrect,
        explanation = this.explanation,
        ai_explanation = this.aiExplanation,
        images = this.images,
        audio = this.audio,
        source_url = this.sourceUrl
    )
}

fun BookmarkEntity.toField(): McqField {
    val normalizedCorrect = options.find { opt ->
        val corr = this.correctAnswer
        if (corr.isBlank()) false
        else opt.startsWith("$corr.", ignoreCase = true) ||
             opt.startsWith("$corr)", ignoreCase = true) ||
             opt.startsWith("$corr ", ignoreCase = true) ||
             opt.equals(corr, ignoreCase = true)
    } ?: this.correctAnswer

    return McqField(
        subject = this.subject,
        topic = this.topic,
        question = this.question,
        options = this.options,
        correct_answer = normalizedCorrect,
        explanation = this.explanation,
        ai_explanation = this.aiExplanation,
        images = this.images,
        audio = this.audio,
        source_url = this.sourceUrl
    )
}
