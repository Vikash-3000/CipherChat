package com.example.cipherchat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.presentation.login.LoginScreen
import com.example.auth.presentation.signup.SignupScreen

@Composable
fun RootNavGraph() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.LOGIN
    ) {

        composable(NavRoutes.LOGIN) {

            LoginScreen(

                onLoginClick = {

                },

                onSignupClick = {
                    navController.navigate(NavRoutes.SIGNUP)
                }
            )
        }

        composable(NavRoutes.SIGNUP) {

            SignupScreen(

                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}