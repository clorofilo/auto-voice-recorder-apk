package com.example.auto_voice_recorder.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auto_voice_recorder.data.Recording
import com.example.auto_voice_recorder.data.RecordingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Preconditions missing before the recorder can actually capture audio. */
sealed interface SetupWarning {
    data object MissingMicPermission : SetupWarning
    data object AccessibilityDisabled : SetupWarning
}

/** Drives the recordings list screen: the list itself, live recording state, and setup warnings. */
class RecordingsViewModel(
    private val repository: RecordingsRepository,
    isRecordingNow: StateFlow<Boolean>,
    private val statusChecker: SystemStatusChecker,
) : ViewModel() {

    private val allRecordings: StateFlow<List<Recording>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recordings: StateFlow<List<RecordingUiModel>> = allRecordings
        .map { list -> list.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isRecordingNow: StateFlow<Boolean> = isRecordingNow

    private val _setupWarning = MutableStateFlow(computeSetupWarning())
    val setupWarning: StateFlow<SetupWarning?> = _setupWarning.asStateFlow()

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun refreshStatus() {
        _setupWarning.value = computeSetupWarning()
    }

    /** File path for a recording [id], for the UI to hand to [AudioPlayerController.play]. */
    fun filePathFor(id: Long): String? = allRecordings.value.find { it.id == id }?.filePath

    private fun computeSetupWarning(): SetupWarning? = when {
        !statusChecker.hasRecordAudioPermission() -> SetupWarning.MissingMicPermission
        !statusChecker.isAccessibilityServiceEnabled() -> SetupWarning.AccessibilityDisabled
        else -> null
    }

    private fun Recording.toUiModel() = RecordingUiModel(
        id = id,
        createdAtEpochMs = createdAtEpochMs,
        durationMs = durationMs,
        syncState = syncState,
    )
}
