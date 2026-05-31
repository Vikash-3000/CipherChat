package com.example.auth.data.repository

import com.example.auth.data.remote.FirebaseAuthSource
import com.example.auth.data.remote.FirebaseFirestoreSource
import com.example.auth.domain.model.AuthResult
import com.example.auth.domain.model.User
import com.example.auth.domain.repository.AuthRepository
import com.example.security.DeviceManager
import com.example.security.TokenManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuthSource: FirebaseAuthSource,
    private val tokenManager: TokenManager,
    private val firestoreSource: FirebaseFirestoreSource,
    private val deviceManager: DeviceManager
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

            val firebaseUser =
                FirebaseAuth.getInstance().currentUser

            firebaseUser?.let {

                firestoreSource.updateLoginInfo(
                    uid = firebaseUser.uid,
                    deviceId = deviceManager.getDeviceId()
                )
            }

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

            val firebaseUser =
                FirebaseAuth.getInstance().currentUser

            firebaseUser?.let {

                val user = User(
                    uid = it.uid,
                    email = it.email ?: "",
                    createdAt = System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis(),
                    deviceId = deviceManager.getDeviceId(),
                    isOnline = true
                )

                firestoreSource.saveUser(user)
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

    override fun logout() {

        FirebaseAuth
            .getInstance()
            .signOut()

        tokenManager.clearToken()
    }

    override suspend fun isCurrentDeviceValid(): Boolean {

        val firebaseUser =
            FirebaseAuth.getInstance().currentUser
                ?: return false

        val firestoreDeviceId =
            firestoreSource.getUserDeviceId(
                firebaseUser.uid
            )

        val currentDeviceId =
            deviceManager.getDeviceId()

        return firestoreDeviceId ==
                currentDeviceId
    }
}