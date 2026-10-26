package com.example.data

import android.content.Context
import android.net.Uri
import com.example.local.ProgressEntity
import com.example.utils.ZipExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SyncRepository(private val mcqRepository: McqRepository) {

    private val DEFAULT_CLOUD_DB_ZIP_URLS = listOf(
        "https://github.com/undergodman-netizen/vedika/releases/download/raggccyc/dbmedivamcq.zip",
        "https://github.com/undergodman-netizen/vedika/releases/download/raggccyc/dbmedivamcqprep.zip"
    )

    private suspend fun getCloudUrlsFromFirestore(): List<String> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("config").document("sync_settings")
            val snapshot = docRef.get().await()
            val urls = snapshot.get("github_links") as? List<String>
            if (urls != null && urls.isNotEmpty()) {
                urls
            } else {
                // Save the defaults to Firestore for testing/future use
                docRef.set(hashMapOf("github_links" to DEFAULT_CLOUD_DB_ZIP_URLS), com.google.firebase.firestore.SetOptions.merge())
                DEFAULT_CLOUD_DB_ZIP_URLS
            }
        } catch (e: Exception) {
            DEFAULT_CLOUD_DB_ZIP_URLS
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // 5 minutes read timeout for large files
        .followRedirects(true)
        .addInterceptor(object : okhttp3.Interceptor {
            override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                var response: okhttp3.Response? = null
                var tryCount = 0
                val maxLimit = 3
                var lastException: Exception? = null
                
                while (tryCount < maxLimit) {
                    try {
                        response = chain.proceed(request)
                        if (response.isSuccessful || response.code in 400..499) {
                            return response
                        }
                        response.close()
                    } catch (e: Exception) {
                        lastException = e
                    }
                    tryCount++
                    if (tryCount < maxLimit) {
                        Thread.sleep(2000L * tryCount) // backoff
                    }
                }
                return response ?: throw lastException ?: java.io.IOException("Failed after $maxLimit attempts")
            }
        })
        .build()

    suspend fun downloadAndImportDbFromCloud(
        context: Context,
        onProgress: (Float, List<String>?) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cloudUrls = getCloudUrlsFromFirestore()
        var hasWiped = false
        val wipeMutex = Mutex()
        val progresses = FloatArray(cloudUrls.size) { 0f }
        var diagnostics = listOf<String>()

        val deferreds = cloudUrls.mapIndexed { index, url ->
            async {
                var success = false
                val tempFile = File(context.cacheDir, "cloud_db_download_$index.zip")
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful && response.body != null) {
                            val body = response.body!!
                            val totalSize = body.contentLength()
                            var downloadedSize = 0L

                            body.byteStream().use { input ->
                                tempFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                        downloadedSize += bytesRead
                                        if (totalSize > 0) {
                                            progresses[index] = downloadedSize.toFloat() / totalSize
                                            val overallProgress = progresses.sum() / cloudUrls.size
                                            onProgress(overallProgress * 0.5f, null)
                                        }
                                    }
                                }
                            }

                            val uri = Uri.fromFile(tempFile)
                            val extractSuccess = ZipExportUtils.importDatabaseFromZip(
                                context,
                                uri,
                                onQuestionsChunk = { chunk ->
                                    wipeMutex.withLock {
                                        if (!hasWiped) {
                                            mcqRepository.deleteAllQuestions()
                                            hasWiped = true
                                        }
                                    }
                                    val fields = chunk.map { q ->
                                        com.example.McqField(
                                            subject = q.subject,
                                            topic = q.topic,
                                            question = q.question,
                                            options = q.options,
                                            correct_answer = q.correct_answer,
                                            explanation = q.explanation,
                                            ai_explanation = q.ai_explanation,
                                            images = q.images,
                                            audio = q.audio,
                                            source_url = q.source_url
                                        )
                                    }
                                    mcqRepository.insertQuestions(fields)
                                },
                                onProgressChunk = { chunk ->
                                    val progressEntities = chunk.map { p ->
                                        ProgressEntity(
                                            question = p.question,
                                            subject = p.subject,
                                            topic = p.topic,
                                            selectedOption = p.selectedOption,
                                            isCorrect = p.isCorrect
                                        )
                                    }
                                    mcqRepository.saveProgressList(progressEntities)
                                }
                            )
                            success = extractSuccess
                            if (!success) {
                                diagnostics = diagnostics + "Failed to parse ZIP from URL $index"
                                onProgress(progresses.sum() / cloudUrls.size, diagnostics)
                            }
                        } else {
                            val errorCode = response.code
                            android.util.Log.e("CloudSync", "HTTP Error $errorCode from $url")
                            diagnostics = diagnostics + "HTTP Error $errorCode from URL $index"
                            onProgress(progresses.sum() / cloudUrls.size, diagnostics)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CloudSync", "Failed to download DB from $url", e)
                    diagnostics = diagnostics + "Download Error: ${e.message} from URL $index"
                    onProgress(progresses.sum() / cloudUrls.size, diagnostics)
                } finally {
                    tempFile.takeIf { it.exists() }?.delete()
                }
                success
            }
        }

        val results = deferreds.map { it.await() }
        val anySuccess = results.any { it }
        anySuccess
    }

    suspend fun loadMultipleJsonsFromUris(
        context: Context,
        uris: List<Uri>,
        fileRepository: FileRepository,
        onProgress: (Float) -> Unit,
        onError: (List<String>) -> Unit,
        sourcePrefix: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val totalBytes = uris.sumOf { fileRepository.getFileSize(it).coerceAtLeast(0L) }
        var accumulatedBytesRead = 0L
        val errors = ArrayList<String>()
        var totalImportedQuestions = 0

        uris.forEachIndexed { idx, uri ->
            try {
                val name = fileRepository.getFileName(uri) ?: "imported_questions_$idx.json"
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val sourceName = if (sourcePrefix != null) "$sourcePrefix$name" else name
                    val canonicalCol = com.example.data.McqImporter.parseToCanonicalCollection(content, sourceName, errors)
                    mcqRepository.saveCanonicalCollection(canonicalCol)
                    val qCount = canonicalCol.unsectionedSets.sumOf { it.questions.size } +
                            canonicalCol.sections.sumOf { sec -> sec.sets.sumOf { s -> s.questions.size } }
                    totalImportedQuestions += qCount
                }
            } catch (e: Exception) {
                errors.add("Error processing file $idx: ${e.localizedMessage}")
            }
            val progressVal = ((idx + 1).toFloat() / uris.size.toFloat()).coerceIn(0.1f, 0.95f)
            onProgress(progressVal)
        }

        if (errors.isNotEmpty()) {
            onError(errors)
        }

        return@withContext totalImportedQuestions
    }

    suspend fun loadJson(
        jsonString: String,
        sourceName: String? = null,
        onError: (List<String>) -> Unit,
        sourcePrefix: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val errors = ArrayList<String>()
        val finalSourceName = if (sourcePrefix != null && sourceName != null && !sourceName.startsWith(sourcePrefix)) {
            "$sourcePrefix$sourceName"
        } else {
            sourceName
        }
        val canonicalCol = com.example.data.McqImporter.parseToCanonicalCollection(jsonString, finalSourceName, errors)
        mcqRepository.saveCanonicalCollection(canonicalCol)
        val totalQuestions = canonicalCol.unsectionedSets.sumOf { it.questions.size } +
                canonicalCol.sections.sumOf { sec -> sec.sets.sumOf { s -> s.questions.size } }
        if (errors.isNotEmpty()) {
            onError(errors)
        }
        return@withContext totalQuestions
    }




    class ProgressInputStream(
        private val delegate: java.io.InputStream,
        private val totalBytes: Long,
        private val onProgress: (Float) -> Unit
    ) : java.io.InputStream() {
        private var bytesRead = 0L
        private var lastPercent = -1

        override fun read(): Int {
            val result = delegate.read()
            if (result != -1) {
                bytesRead++
                notifyProgress()
            }
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val result = delegate.read(b, off, len)
            if (result != -1) {
                bytesRead += result
                notifyProgress()
            }
            return result
        }

        private fun notifyProgress() {
            if (totalBytes > 0) {
                val progress = bytesRead.toFloat() / totalBytes
                val percent = (progress * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(progress)
                }
            }
        }

        override fun close() {
            delegate.close()
        }
    }
}
