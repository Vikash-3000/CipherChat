package com.example.auth.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    suspend fun login(
        email: String,
        password: String
    ) {

        firebaseAuth.signInWithEmailAndPassword(
            email,
            password
        ).await()
    }

    suspend fun signup(
        email: String,
        password: String
    ) {

        firebaseAuth.createUserWithEmailAndPassword(
            email,
            password
        ).await()
    }
}