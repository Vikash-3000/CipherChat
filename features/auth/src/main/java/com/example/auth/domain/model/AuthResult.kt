package com.example.auth.domain.model

data class AuthResult(
    val isSuccess: Boolean,
    val message: String? = null
)