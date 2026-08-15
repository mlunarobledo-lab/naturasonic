package com.naturasonic.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.naturasonic.app.ui.screens.alerts.AlertHistoryScreen
import com.naturasonic.app.ui.screens.home.HomeScreen
import com.naturasonic.app.ui.screens.performance.PerformanceScreen
import com.naturasonic.app.ui.screens.home.HomeViewModel
import com.naturasonic.app.ui.screens.onboarding.OnboardingScreen
import com.naturasonic.app.ui.screens.onboarding.OnboardingViewModel
import com.naturasonic.app.ui.screens.settings.SettingsScreen
import com.naturasonic.app.ui.screens.settings.SettingsViewModel
import com.naturasonic.app.ui.screens.anc.AncControlScreen
import com.naturasonic.app.ui.screens.audiogram.AudiogramTestScreen
import com.naturasonic.app.ui.screens.transcription.TranscriptionScreen
import com.naturasonic.app.ui.screens.transcription.TranscriptionViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TRANSCRIPTION = "transcription"
    const val SETTINGS = "settings"
    const val ALERT_HISTORY = "alert_history"
    const val PERFORMANCE = "performance"
    const val AUDIOGRAM_TEST = "audiogram_test"
    const val ANC_CONTROL = "anc_control"
}

@Composable
fun NaturaSonicNavHost() {
    val navController = rememberNavController()

    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState(initial = false)

    val startDestination = if (onboardingCompleted) Routes.HOME else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToTranscription = {
                    navController.navigate(Routes.TRANSCRIPTION)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToAlertHistory = {
                    navController.navigate(Routes.ALERT_HISTORY)
                }
            )
        }

        composable(Routes.TRANSCRIPTION) {
            TranscriptionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPerformance = {
                    navController.navigate(Routes.PERFORMANCE)
                },
                onNavigateToAudiogram = {
                    navController.navigate(Routes.AUDIOGRAM_TEST)
                },
                onNavigateToAnc = {
                    navController.navigate(Routes.ANC_CONTROL)
                }
            )
        }

        composable(Routes.PERFORMANCE) {
            PerformanceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ALERT_HISTORY) {
            AlertHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.AUDIOGRAM_TEST) {
            AudiogramTestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ANC_CONTROL) {
            AncControlScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
