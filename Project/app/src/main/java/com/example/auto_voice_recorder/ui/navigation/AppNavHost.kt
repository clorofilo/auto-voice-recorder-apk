package com.example.auto_voice_recorder.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auto_voice_recorder.AppContainer
import com.example.auto_voice_recorder.service.RecordingStatus
import com.example.auto_voice_recorder.ui.lock.LockScreen
import com.example.auto_voice_recorder.ui.lock.LockViewModel
import com.example.auto_voice_recorder.ui.recordings.RecordingsListScreen
import com.example.auto_voice_recorder.ui.recordings.RecordingsViewModel
import com.example.auto_voice_recorder.ui.settings.SettingsScreen
import com.example.auto_voice_recorder.ui.settings.SettingsViewModel

private const val ROUTE_LOCK = "lock"
private const val ROUTE_RECORDINGS = "recordings"
private const val ROUTE_SETTINGS = "settings"

/**
 * Top-level navigation graph: lock screen gates entry, then recordings and
 * settings can navigate back and forth between each other.
 */
@Composable
fun AppNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_LOCK,
        modifier = modifier,
    ) {
        composable(ROUTE_LOCK) {
            val viewModel: LockViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { LockViewModel(container.pinManager) }
                },
            )
            LockScreen(
                viewModel = viewModel,
                onUnlocked = {
                    navController.navigate(ROUTE_RECORDINGS) {
                        popUpTo(ROUTE_LOCK) { inclusive = true }
                    }
                },
            )
        }

        composable(ROUTE_RECORDINGS) {
            val viewModel: RecordingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        RecordingsViewModel(
                            container.recordingsRepository,
                            RecordingStatus.isRecording,
                            container.systemStatusChecker,
                        )
                    }
                },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                RecordingsListScreen(viewModel = viewModel)
                Button(
                    onClick = { navController.navigate(ROUTE_SETTINGS) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Text("Ajustes")
                }
            }
        }

        composable(ROUTE_SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(container.syncPreferences, container.pinManager)
                    }
                },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                SettingsScreen(viewModel = viewModel)
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                ) {
                    Text("Volver")
                }
            }
        }
    }
}
