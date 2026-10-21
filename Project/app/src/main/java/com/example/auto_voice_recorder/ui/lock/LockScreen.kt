package com.example.auto_voice_recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.auto_voice_recorder.security.BiometricAuthHelper
import com.example.auto_voice_recorder.security.PinRules

private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
)

/**
 * Lock screen with a PIN pad and a biometric unlock button. When no PIN has been created
 * yet it shows a first-run "create PIN" flow (enter + confirm) instead, with no biometric
 * shortcut, so a PIN always exists before the app can be opened. Calls [onUnlocked] once
 * the [viewModel] reaches [LockUiState.Unlocked].
 */
@Composable
fun LockScreen(
    viewModel: LockViewModel,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val needsPinSetup by viewModel.needsPinSetup.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState == LockUiState.Unlocked) onUnlocked()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (needsPinSetup) {
            CreatePinContent(viewModel = viewModel, uiState = uiState)
        } else {
            UnlockContent(viewModel = viewModel, uiState = uiState)
        }
    }
}

@Composable
private fun UnlockContent(viewModel: LockViewModel, uiState: LockUiState) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricAuthHelper() }
    val biometricAvailable = remember(context) {
        biometricHelper.isBiometricAvailable(context)
    }
    var pin by rememberSaveable { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }

    Text(
        text = "Introduce tu PIN",
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "*".repeat(pin.length),
        style = MaterialTheme.typography.headlineMedium,
    )
    if (uiState == LockUiState.Error) {
        ErrorText("PIN incorrecto")
    }
    biometricError?.let { ErrorText(it) }
    Spacer(Modifier.height(24.dp))

    PinKeypad(
        okEnabled = pin.isNotEmpty(),
        onDigit = { pin += it },
        onDelete = { pin = pin.dropLast(1) },
        onOk = {
            viewModel.submitPin(pin)
            pin = ""
        },
    )

    if (biometricAvailable && activity != null) {
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                biometricError = null
                biometricHelper.authenticate(
                    activity = activity,
                    onSuccess = { viewModel.onBiometricSuccess() },
                    onError = { message -> biometricError = message },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Usar huella/Face")
        }
    }
}

/** Two-step PIN creation: enter a new PIN, then enter it again to confirm. */
@Composable
private fun CreatePinContent(viewModel: LockViewModel, uiState: LockUiState) {
    var firstPin by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    val confirming = firstPin != null

    Text(
        text = if (confirming) "Confirma tu nuevo PIN" else "Crea un PIN para proteger la app",
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Mínimo ${PinRules.MIN_LENGTH} dígitos",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "*".repeat(pin.length),
        style = MaterialTheme.typography.headlineMedium,
    )
    if (uiState is LockUiState.PinSetupError) {
        PinRules.errorMessage(uiState.reason)?.let { ErrorText(it) }
    }
    Spacer(Modifier.height(24.dp))

    PinKeypad(
        okEnabled = pin.length >= PinRules.MIN_LENGTH,
        onDigit = { pin += it },
        onDelete = { pin = pin.dropLast(1) },
        onOk = {
            val first = firstPin
            if (first == null) {
                firstPin = pin
            } else {
                viewModel.createPin(first, pin)
                // On failure start over from the first entry.
                firstPin = null
            }
            pin = ""
        },
    )
}

@Composable
private fun PinKeypad(
    okEnabled: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
) {
    KEYPAD_ROWS.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { digit ->
                Button(onClick = { onDigit(digit) }) { Text(digit) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDelete) { Text("Borrar") }
        Button(onClick = { onDigit("0") }) { Text("0") }
        Button(onClick = onOk, enabled = okEnabled) { Text("OK") }
    }
}

@Composable
private fun ErrorText(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
