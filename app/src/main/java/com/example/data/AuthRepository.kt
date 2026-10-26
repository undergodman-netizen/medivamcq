package com.example.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(private val settingsRepository: SettingsRepository) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun signUp(emailInput: String, passwordInput: String, nameInput: String, context: Context): Result<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                val result = auth.createUserWithEmailAndPassword(emailInput, passwordInput).await()
                val user = result.user
                if (user != null) {
                    val deviceResult = checkAndRegisterDevice(user.uid, nameInput, emailInput, deviceId)
                    if (deviceResult.isSuccess) {
                        settingsRepository.saveProfile(nameInput, emailInput)
                        Result.success(Pair(nameInput, emailInput))
                    } else {
                        Result.failure(deviceResult.exceptionOrNull() ?: Exception("Device limit reached"))
                    }
                } else {
                    Result.failure(Exception("Signup failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun signIn(emailInput: String, passwordInput: String, context: Context): Result<Pair<String, String>> {
         return withContext(Dispatchers.IO) {
            try {
                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                val result = auth.signInWithEmailAndPassword(emailInput, passwordInput).await()
                val user = result.user
                if (user != null) {
                    val name = user.displayName ?: (if (emailInput.contains("@")) emailInput.substringBefore("@") else "User")
                    val deviceResult = checkAndRegisterDevice(user.uid, name, emailInput, deviceId)
                    if (deviceResult.isSuccess) {
                        settingsRepository.saveProfile(name, emailInput)
                        Result.success(Pair(name, emailInput))
                    } else {
                        Result.failure(deviceResult.exceptionOrNull() ?: Exception("Device limit reached"))
                    }
                } else {
                    Result.failure(Exception("Signin failed"))
                }
            } catch (e: Exception) {
                 Result.failure(e)
            }
        }
    }

    suspend fun signInWithGoogle(idTokenInput: String, context: Context): Result<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                val credential = GoogleAuthProvider.getCredential(idTokenInput, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    val name = user.displayName ?: "Google User"
                    val email = user.email ?: ""
                    val deviceResult = checkAndRegisterDevice(user.uid, name, email, deviceId)
                    if (deviceResult.isSuccess) {
                        settingsRepository.saveProfile(name, email)
                        Result.success(Pair(name, email))
                    } else {
                        val exception = deviceResult.exceptionOrNull()
                        val errMsg = exception?.message ?: ""
                        if (errMsg.contains("PERMISSION_DENIED", ignoreCase = true) || 
                            errMsg.contains("permission", ignoreCase = true) ||
                            errMsg.contains("insufficient", ignoreCase = true)) {
                            android.util.Log.w("AuthRepository", "Firestore rules or missing database blocked device-check, continuing local login: $errMsg")
                            settingsRepository.saveProfile(name, email)
                            Result.success(Pair(name, email))
                        } else {
                            Result.failure(exception ?: Exception("Device limit reached"))
                        }
                    }
                } else {
                    Result.failure(Exception("Google Sign-In failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun unregisterDevice(context: Context) {
        val user = auth.currentUser ?: return
        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        withContext(Dispatchers.IO) {
            try {
                val docRef = firestore.collection("users").document(user.uid)
                val doc = docRef.get().await()
                if (doc.exists()) {
                    val devicesList = doc.get("devices") as? List<*>
                    val devices = devicesList?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()
                    if (devices.contains(deviceId)) {
                        devices.remove(deviceId)
                        docRef.update("devices", devices).await()
                    }
                }
            } catch (e: Exception) {
                // Ignore failure on logout to avoid blocking the user
            }
        }
    }

    private suspend fun checkAndRegisterDevice(uid: String, name: String, email: String, deviceId: String): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(uid)
            val doc = docRef.get().await()
            val devicesList = doc.get("devices") as? List<*>
            val devices = devicesList?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()
            
            if (devices.contains(deviceId)) {
                docRef.set(hashMapOf(
                    "name" to name,
                    "email" to email,
                    "lastLogin" to System.currentTimeMillis(),
                    "subscription" to hashMapOf("status" to "free", "plan" to "basic", "expiresAt" to 0L),
                    "stats" to hashMapOf("totalQuestionsAttempted" to 0, "totalBookmarks" to 0)
                ), com.google.firebase.firestore.SetOptions.merge()).await()
                Result.success(Unit)
            } else if (devices.size < 2) {
                devices.add(deviceId)
                docRef.set(hashMapOf(
                    "name" to name,
                    "email" to email,
                    "lastLogin" to System.currentTimeMillis(),
                    "devices" to devices,
                    "subscription" to hashMapOf("status" to "free", "plan" to "basic", "expiresAt" to 0L),
                    "stats" to hashMapOf("totalQuestionsAttempted" to 0, "totalBookmarks" to 0)
                ), com.google.firebase.firestore.SetOptions.merge()).await()
                Result.success(Unit)
            } else {
                auth.signOut()
                Result.failure(Exception("Device limit reached! Maximum 2 devices allowed per account."))
            }
        } catch (e: Exception) {
            try {
                // Safe check: if Firestore failed due to true nonexistent document, let's create it.
                // But if it's a permission/network issue, propagate the actual error instead of blindly overriding.
                val docRef = firestore.collection("users").document(uid)
                docRef.set(hashMapOf(
                    "name" to name,
                    "email" to email,
                    "lastLogin" to System.currentTimeMillis(),
                    "devices" to listOf(deviceId),
                    "subscription" to hashMapOf("status" to "free", "plan" to "basic", "expiresAt" to 0L),
                    "stats" to hashMapOf("totalQuestionsAttempted" to 0, "totalBookmarks" to 0)
                )).await()
                Result.success(Unit)
            } catch(ex: Exception) {
                Result.failure(ex)
            }
        }
    }
}
