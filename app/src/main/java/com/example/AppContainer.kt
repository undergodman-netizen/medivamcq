package com.example

import android.app.Application
import com.example.local.AppDatabase
import com.example.data.*

class AppContainer(private val application: Application) {
    val db: AppDatabase by lazy { AppDatabase.getInstance(application) }
    val mcqRepository: McqRepository by lazy { McqRepository(application, db) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(application) }
    val statsRepository: StatsRepository by lazy { StatsRepository(application) }
    val fileRepository: FileRepository by lazy { FileRepository(application) }
    val firestoreSyncManager: FirestoreSyncManager by lazy { FirestoreSyncManager(application, mcqRepository, settingsRepository) }
    val syncRepository: SyncRepository by lazy { SyncRepository(mcqRepository) }
    val authRepository: AuthRepository by lazy { AuthRepository(settingsRepository) }
    val aiExplanationService: AiExplanationService by lazy { AiExplanationService() }
}
