package com.example.auth.domain.model

data class User(
    val uid: String = "",
    val email: String = "",
    val createdAt: Long = 0L,
    val lastLogin: Long = 0L,
    val deviceId: String = "",
    val isOnline: Boolean = false
)