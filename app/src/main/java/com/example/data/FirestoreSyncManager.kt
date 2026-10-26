package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import java.util.concurrent.TimeUnit

class FirestoreSyncManager(
    private val context: Context,
    private val mcqRepository: McqRepository,
    private val settingsRepository: SettingsRepository
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Flag to easily pause/disconnect progress syncing since it handles so many writes
    private val isProgressSyncEnabled = false
    private val isBookmarksSyncEnabled = false
    
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncIfNeeded() {
        val lastSyncTime = settingsRepository.getLastSyncTime()
        val currentTime = System.currentTimeMillis()
        
        // Only sync if more than 12 hours have passed, or to force initially
        val hoursPassed = TimeUnit.MILLISECONDS.toHours(currentTime - lastSyncTime)
        if (hoursPassed >= 12 || lastSyncTime == 0L) {
            scheduleBackgroundSync()
        }
    }

    fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(
            4, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "FirestoreDeltaSync",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    suspend fun performDeltaSync(): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) return@withContext false
        
        val currentUser = auth.currentUser ?: return@withContext false
        val userId = currentUser.uid
        val lastSyncTime = settingsRepository.getLastSyncTime()
        val currentTime = System.currentTimeMillis()
        
        try {
            val userDocRef = firestore.collection("users").document(userId)

            // Sync overall user stats
            val totalAttempted = mcqRepository.getProgressCountSync()
            userDocRef.set(hashMapOf(
                "stats" to hashMapOf(
                    "totalQuestionsAttempted" to totalAttempted,
                    "lastSyncAt" to currentTime
                )
            ), SetOptions.merge()).await()

            // 1. Sync local updates to Firestore (Push)
            val updatedProgress = if (isProgressSyncEnabled) mcqRepository.getUpdatedProgressSince(lastSyncTime) else emptyList()
            val updatedBookmarks = if (isBookmarksSyncEnabled) mcqRepository.getUpdatedBookmarksSince(lastSyncTime) else emptyList()
            
            val progressCollection = userDocRef.collection("progress_v2")
            val bookmarksCollection = userDocRef.collection("bookmarks_v2")

            // Refactored: Chunking into strictly exactly 499 limits per batch
            val progressChunks = updatedProgress.chunked(499)
            for (chunk in progressChunks) {
                val batch = firestore.batch()
                for (progress in chunk) {
                    val docRef = progressCollection.document(progress.question.hashCode().toString())
                    val pData = HashMap<String, Any>()
                    pData["q"] = progress.question
                    progress.subject?.let { pData["s"] = it }
                    progress.topic?.let { pData["t"] = it }
                    pData["o"] = progress.selectedOption
                    pData["c"] = progress.isCorrect
                    pData["lu"] = progress.lastUpdated
                    batch.set(docRef, pData, SetOptions.merge())
                }
                batch.commit().await()
            }
            
            val bookmarkChunks = updatedBookmarks.chunked(499)
            for (chunk in bookmarkChunks) {
                val batch = firestore.batch()
                for (bm in chunk) {
                    val docRef = bookmarksCollection.document(bm.question.hashCode().toString())
                    val bData = HashMap<String, Any>()
                    bData["q"] = bm.question
                    bm.subject?.let { bData["s"] = it }
                    bm.topic?.let { bData["t"] = it }
                    bData["ba"] = bm.bookmarkedAt
                    bData["lu"] = bm.lastUpdated
                    bm.aiExplanation?.let { bData["ai"] = it }
                    bm.audio?.let { bData["au"] = it }
                    bm.images?.let { bData["im"] = it }
                    batch.set(docRef, bData, SetOptions.merge())
                }
                batch.commit().await()
            }
            
            // 2. Fetch new updates from Firestore (Pull)
            // Using a scalable whereGreaterThan sync rather than individual doc requests
            try {
                if (isProgressSyncEnabled) {
                    val remoteProgress = progressCollection.whereGreaterThan("lu", lastSyncTime).get().await()
                    remoteProgress.documents.forEach { doc ->
                        val lu = (doc.getLong("lu")) ?: return@forEach
                        if (lu > lastSyncTime) {
                            val q = doc.getString("q") ?: return@forEach
                            mcqRepository.saveProgress(
                                com.example.local.ProgressEntity(
                                    question = q,
                                    subject = doc.getString("s"),
                                    topic = doc.getString("t"),
                                    selectedOption = doc.getString("o") ?: "",
                                    isCorrect = doc.getBoolean("c") ?: false,
                                    lastUpdated = lu
                                )
                            )
                        }
                    }
                }
                
                if (isBookmarksSyncEnabled) {
                    val remoteBookmarks = bookmarksCollection.whereGreaterThan("lu", lastSyncTime).get().await()
                    remoteBookmarks.documents.forEach { doc ->
                        val lu = (doc.getLong("lu")) ?: return@forEach
                        if (lu > lastSyncTime) {
                            val q = doc.getString("q") ?: return@forEach
                            val existingBm = mcqRepository.getBookmarkSync(q)
                            
                            if (existingBm == null || lu > existingBm.lastUpdated) {
                                val newBm = com.example.local.BookmarkEntity(
                                    question = q,
                                    subject = doc.getString("s"),
                                    topic = doc.getString("t"),
                                    options = existingBm?.options ?: emptyList(),
                                    correctAnswer = existingBm?.correctAnswer ?: "",
                                    explanation = existingBm?.explanation,
                                    aiExplanation = doc.getString("ai") ?: existingBm?.aiExplanation,
                                    images = doc.getString("im") ?: existingBm?.images,
                                    audio = doc.getString("au") ?: existingBm?.audio,
                                    sourceUrl = existingBm?.sourceUrl,
                                    bookmarkedAt = doc.getLong("ba") ?: currentTime,
                                    lastUpdated = lu
                                )
                                mcqRepository.saveBookmarkSync(newBm)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Pull sync failed", e)
                return@withContext false
            }
            
            settingsRepository.saveLastSyncTime(currentTime)
            Log.d("FirestoreSyncManager", "Sync completed successfully")
            true
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Push sync failed", e)
            false
        }
    }
    
}
