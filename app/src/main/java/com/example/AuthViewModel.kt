package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val isDownloadingDb: Boolean = false,
    val importProgress: Float? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MyApplication).container
    private val authRepository: AuthRepository = container.authRepository

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun setAuthError(msg: String?) {
        _state.update { it.copy(authErrorMessage = msg) }
    }

    private fun setLoading(loading: Boolean) {
        _state.update { it.copy(isAuthLoading = loading) }
    }

    fun signInWithGoogle(idToken: String, context: Context, onComplete: (Boolean, String?) -> Unit) {
        setLoading(true)
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken, context)
            setLoading(false)
            if (result.isSuccess) {
                setAuthError(null)
                onComplete(true, null)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Google Sign-In failed"
                setAuthError(msg)
                onComplete(false, msg)
            }
        }
    }
    
    fun signUp(emailInput: String, passwordInput: String, nameInput: String, context: Context, onResult: (Boolean, String?) -> Unit) {
        setLoading(true)
        viewModelScope.launch {
            val result = authRepository.signUp(emailInput, passwordInput, nameInput, context)
            setLoading(false)
            if (result.isSuccess) {
                setAuthError(null)
                onResult(true, null)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Sign-Up failed"
                setAuthError(msg)
                onResult(false, msg)
            }
        }
    }

    fun signIn(emailInput: String, passwordInput: String, context: Context, onResult: (Boolean, String?) -> Unit) {
        setLoading(true)
        viewModelScope.launch {
            val result = authRepository.signIn(emailInput, passwordInput, context)
            setLoading(false)
            if (result.isSuccess) {
                setAuthError(null)
                onResult(true, null)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Sign-In failed"
                setAuthError(msg)
                onResult(false, msg)
            }
        }
    }
    
    fun initFirebase() {
        // no-op or initialize if needed
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.unregisterDevice(getApplication())
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        }
    }
}
