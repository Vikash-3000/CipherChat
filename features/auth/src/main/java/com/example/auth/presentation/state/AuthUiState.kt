package com.example.auth.presentation.state

data class AuthUiState(

    val isLoading: Boolean = false,

    val isSuccess: Boolean = false,

    val error: String? = null
)