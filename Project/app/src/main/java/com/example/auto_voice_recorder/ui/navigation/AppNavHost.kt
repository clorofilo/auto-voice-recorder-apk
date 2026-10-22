package com.example.auto_voice_recorder.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auto_voice_recorder.AppContainer
import com.example.auto_voice_recorder.service.RecordingStatus
import com.example.auto_voice_recorder.ui.lock.AppLockController
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
 *
 * The app re-locks whenever the activity goes to the background (not on
 * rotation), and [AppLockController] starts locked in every new process, so a
 * back stack restored after process death can't skip the lock screen either:
 * while locked, protected destinations render nothing and the host navigates
 * back to [ROUTE_LOCK], clearing the back stack.
 */
@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    appLock: AppLockController = AppLockController.process,
) {
    val navController = rememberNavController()
    val isLocked by appLock.isLocked.collectAsState()
    val activity = LocalContext.current as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, appLock) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    appLock.onAppBackgrounded(activity?.isChangingConfigurations == true)

                Lifecycle.Event.ON_RESUME -> appLock.onAppResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_LOCK,
        // MainActivity is edge-to-edge: keep every screen out of the status/navigation bars and IME.
        modifier = modifier.safeDrawingPadding(),
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
                    appLock.unlock()
                    navController.navigate(ROUTE_RECORDINGS) {
                        popUpTo(ROUTE_LOCK) { inclusive = true }
                    }
                },
            )
        }

        composable(ROUTE_RECORDINGS) {
            // Never show protected content while locked (e.g. a back stack restored
            // after process death); the LaunchedEffect below navigates to the lock.
            if (isLocked) return@composable
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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                        Text("Ajustes")
                    }
                }
                RecordingsListScreen(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        composable(ROUTE_SETTINGS) {
            if (isLocked) return@composable
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            syncPreferences = container.syncPreferences,
                            vibrationPreferences = container.vibrationPreferences,
                            pinManager = container.pinManager,
                            lockModePreferences = container.lockModePreferences,
                            repository = container.recordingsRepository,
                            scheduleDriveSync = container::scheduleDriveSync,
                        )
                    }
                },
            )
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { navController.popBackStack() }) {
                        Text("Volver")
                    }
                }
                SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Runs after NavHost has set the graph. Whenever the app becomes locked while
    // showing anything but the lock screen, reset the whole back stack to it.
    LaunchedEffect(isLocked) {
        if (isLocked && navController.currentDestination?.route != ROUTE_LOCK) {
            navController.navigate(ROUTE_LOCK) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
