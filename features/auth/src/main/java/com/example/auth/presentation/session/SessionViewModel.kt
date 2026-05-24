package com.example.auth.presentation.session

import androidx.lifecycle.ViewModel
import com.example.security.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _isLoggedIn =
        MutableStateFlow(false)

    val isLoggedIn =
        _isLoggedIn.asStateFlow()

    init {

        checkSession()
    }

    private fun checkSession() {

        _isLoggedIn.value =
            tokenManager.getToken() != null
    }
}