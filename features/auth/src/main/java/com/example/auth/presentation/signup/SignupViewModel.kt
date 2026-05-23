package com.example.auth.presentation.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.domain.usecase.SignupUseCase
import com.example.auth.presentation.state.AuthUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val signupUseCase: SignupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())

    val uiState = _uiState.asStateFlow()

    fun signup(
        email: String,
        password: String
    ) {

        viewModelScope.launch {

            _uiState.value = AuthUiState(
                isLoading = true
            )

            val result = signupUseCase(
                email,
                password
            )

            _uiState.value = AuthUiState(
                isSuccess = result.isSuccess,
                error = result.message
            )
        }
    }
}