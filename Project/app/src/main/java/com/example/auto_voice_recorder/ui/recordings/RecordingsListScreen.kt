package com.example.auto_voice_recorder.ui.recordings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.auto_voice_recorder.ui.lock.AppLockController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main recordings screen: a "recording now" banner, a dismissible setup-warning
 * banner, and the list of recordings with play/stop and delete controls.
 */
@Composable
fun RecordingsListScreen(
    viewModel: RecordingsViewModel,
    modifier: Modifier = Modifier,
) {
    val recordings by viewModel.recordings.collectAsState()
    val isRecordingNow by viewModel.isRecordingNow.collectAsState()
    val setupWarning by viewModel.setupWarning.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioPlayer = remember { AudioPlayerController() }
    var playingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var warningDismissed by rememberSaveable(setupWarning) { mutableStateOf(false) }
    var permissionsAutoRequested by rememberSaveable { mutableStateOf(false) }
    var openSettingsIfMicDenied by remember { mutableStateOf(false) }
    var micRationaleBeforeRequest by remember { mutableStateOf(false) }

    // RECORD_AUDIO (so the volume gesture can record) and, on Android 13+,
    // POST_NOTIFICATIONS (so the mandatory "Recording" notification is visible)
    // are requested together.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshStatus()
        val micDenied = !hasPermission(context, Manifest.permission.RECORD_AUDIO)
        // When the user explicitly tapped the banner but Android no longer shows the
        // dialog (permanently denied: no rationale before nor after the request), fall
        // back to the app's system settings page so the permission can still be granted.
        if (openSettingsIfMicDenied && micDenied && !micRationaleBeforeRequest &&
            !shouldShowMicRationale(activity)
        ) {
            context.startActivity(appDetailsSettingsIntent(context))
        }
        openSettingsIfMicDenied = false
    }

    fun requestMissingPermissions(userInitiated: Boolean): Boolean {
        val missing = missingRuntimePermissions(context)
        if (missing.isEmpty()) return false
        openSettingsIfMicDenied = userInitiated
        micRationaleBeforeRequest = shouldShowMicRationale(activity)
        // The system permission dialog must not count as leaving the app.
        AppLockController.process.skipNextBackgroundRelock()
        permissionLauncher.launch(missing.toTypedArray())
        return true
    }

    // Ask once when the recordings screen is first shown after unlocking.
    LaunchedEffect(Unit) {
        if (!permissionsAutoRequested) {
            permissionsAutoRequested = true
            requestMissingPermissions(userInitiated = false)
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioPlayer.stop()
        }
    }

    fun stopPlayback() {
        audioPlayer.stop()
        playingId = null
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isRecordingNow) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = "Grabando ahora",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        val warning = setupWarning
        if (warning != null && !warningDismissed) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (warning) {
                            SetupWarning.MissingMicPermission -> "Concede permiso de micrófono"
                            SetupWarning.AccessibilityDisabled -> "Activa el servicio de accesibilidad"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            when (warning) {
                                SetupWarning.MissingMicPermission ->
                                    if (!requestMissingPermissions(userInitiated = true)) {
                                        context.startActivity(appDetailsSettingsIntent(context))
                                    }

                                SetupWarning.AccessibilityDisabled ->
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }) {
                            Text(
                                when (warning) {
                                    SetupWarning.MissingMicPermission -> "Conceder permiso"
                                    SetupWarning.AccessibilityDisabled -> "Ir a ajustes"
                                },
                            )
                        }
                        OutlinedButton(onClick = { warningDismissed = true }) {
                            Text("Descartar")
                        }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(recordings, key = { it.id }) { recording ->
                RecordingRow(
                    recording = recording,
                    isPlaying = playingId == recording.id,
                    onTogglePlay = {
                        if (playingId == recording.id) {
                            stopPlayback()
                        } else {
                            val filePath = viewModel.filePathFor(recording.id)
                            if (filePath != null) {
                                playingId = recording.id
                                audioPlayer.play(
                                    filePath = filePath,
                                    onCompletion = {
                                        if (playingId == recording.id) {
                                            playingId = null
                                        }
                                    },
                                    onError = {
                                        if (playingId == recording.id) {
                                            playingId = null
                                        }
                                        Toast.makeText(
                                            context,
                                            "No se pudo reproducir la grabación",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                    },
                    onDelete = {
                        if (playingId == recording.id) {
                            stopPlayback()
                        }
                        viewModel.delete(recording.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: RecordingUiModel,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = formatTimestamp(recording.createdAtEpochMs),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = formatDuration(recording.durationMs) + " · " + recording.syncState.name,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTogglePlay) {
                    Text(if (isPlaying) "Detener" else "Reproducir")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("Eliminar")
                }
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Runtime permissions the app needs that are not yet granted. */
private fun missingRuntimePermissions(context: Context): List<String> =
    buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.filterNot { hasPermission(context, it) }

private fun shouldShowMicRationale(activity: Activity?): Boolean =
    activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

private fun appDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${context.packageName}".toUri()
    }

private fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
