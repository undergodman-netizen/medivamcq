package com.example

sealed class AiState {
    object Idle : AiState()
    object Loading : AiState()
    data class Success(val text: String) : AiState()
    data class Error(val msg: String) : AiState()
}
