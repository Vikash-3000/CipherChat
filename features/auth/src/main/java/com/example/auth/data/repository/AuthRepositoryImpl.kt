package com.example.auth.data.repository

import com.example.auth.data.remote.FirebaseAuthSource
import com.example.auth.domain.model.AuthResult
import com.example.auth.domain.repository.AuthRepository
import com.example.security.TokenManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuthSource: FirebaseAuthSource,
    private val tokenManager: TokenManager
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

            val token = FirebaseAuth
                .getInstance()
                .currentUser
                ?.getIdToken(true)
                ?.await()
                ?.token

            token?.let {

                tokenManager.saveToken(it)
            }

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