package com.example.auto_voice_recorder.ui.recordings

import android.content.Intent
import android.provider.Settings
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
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioPlayer = remember { AudioPlayerController() }
    var playingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var warningDismissed by rememberSaveable(setupWarning) { mutableStateOf(false) }

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
                            val intent = when (warning) {
                                SetupWarning.MissingMicPermission ->
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }

                                SetupWarning.AccessibilityDisabled ->
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Ir a ajustes")
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
                                audioPlayer.play(filePath) {
                                    if (playingId == recording.id) {
                                        playingId = null
                                    }
                                }
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

private fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
