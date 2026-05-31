package com.example.auth.domain.repository

import com.example.auth.domain.model.AuthResult

interface AuthRepository {

    suspend fun login(
        email: String,
        password: String
    ): AuthResult

    suspend fun signup(
        email: String,
        password: String
    ): AuthResult

    fun logout()

    suspend fun isCurrentDeviceValid(): Boolean
}