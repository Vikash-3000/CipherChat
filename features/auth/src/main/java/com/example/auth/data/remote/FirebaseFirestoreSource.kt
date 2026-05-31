package com.example.auth.data.remote

import com.example.auth.domain.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseFirestoreSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun saveUser(user: User) {

        firestore.collection("users")
            .document(user.uid)
            .set(user)
            .await()
    }

    suspend fun updateLoginInfo(
        uid: String,
        deviceId: String
    ) {

        firestore.collection("users")
            .document(uid)
            .update(
                mapOf(
                    "lastLogin" to System.currentTimeMillis(),
                    "deviceId" to deviceId,
                    "isOnline" to true
                )
            )
            .await()
    }

    suspend fun getUserDeviceId(
        uid: String
    ): String? {

        return firestore
            .collection("users")
            .document(uid)
            .get()
            .await()
            .getString("deviceId")
    }
}