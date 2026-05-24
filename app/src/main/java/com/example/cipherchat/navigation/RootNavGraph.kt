package com.example.cipherchat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.presentation.login.LoginScreen
import com.example.auth.presentation.session.SessionViewModel
import com.example.auth.presentation.signup.SignupScreen
import com.example.chat.presentation.home.HomeScreen

@Composable
fun RootNavGraph() {

    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel =
        hiltViewModel()

    val isLoggedIn by
    sessionViewModel.isLoggedIn.collectAsState()

    NavHost(
        navController = navController,
        startDestination =
            if (isLoggedIn)
                NavRoutes.HOME
            else
                NavRoutes.LOGIN
    ) {

        composable(NavRoutes.LOGIN) {

            LoginScreen(

                onLoginClick = {
                    navController.navigate(NavRoutes.HOME) {

                        popUpTo(NavRoutes.LOGIN) {
                            inclusive = true
                        }
                    }
                },

                onSignupClick = {
                    navController.navigate(NavRoutes.SIGNUP)
                }
            )
        }

        composable(NavRoutes.SIGNUP) {

            SignupScreen(

                onSignupSuccess = {
                    navController.popBackStack()
                },

                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(NavRoutes.HOME) {

            HomeScreen()
        }
    }
}