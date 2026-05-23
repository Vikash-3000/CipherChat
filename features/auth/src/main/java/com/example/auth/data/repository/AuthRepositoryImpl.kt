package com.example.auth.data.repository

import com.example.auth.data.remote.FirebaseAuthSource
import com.example.auth.domain.model.AuthResult
import com.example.auth.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuthSource: FirebaseAuthSource
) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String
    ): AuthResult {

        return try {

            firebaseAuthSource.login(
                email,
                password
            )

            AuthResult(
                isSuccess = true
            )

        } catch (e: Exception) {

            AuthResult(
                isSuccess = false,
                message = e.message
            )
        }
    }

    override suspend fun signup(
        email: String,
        password: String
    ): AuthResult {

        return try {

            firebaseAuthSource.signup(
                email,
                password
            )

            AuthResult(
                isSuccess = true
            )

        } catch (e: Exception) {

            AuthResult(
                isSuccess = false,
                message = e.message
            )
        }
    }
}