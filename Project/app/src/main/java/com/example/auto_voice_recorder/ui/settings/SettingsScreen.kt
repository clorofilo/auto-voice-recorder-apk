package com.example.auto_voice_recorder.ui.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.auto_voice_recorder.security.PinRules
import com.example.auto_voice_recorder.security.PinValidation
import com.example.auto_voice_recorder.sync.GoogleSignInHelper
import com.example.auto_voice_recorder.ui.lock.AppLockController

/**
 * Settings screen: Drive sync toggle (gated by Google Sign-In), PIN change, and a link
 * to the system accessibility settings. Not unit tested — Compose UI, verified manually.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val isDriveSyncEnabled by viewModel.isDriveSyncEnabled.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val googleSignInHelper = remember(context) { GoogleSignInHelper(context) }
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    // (message, isError) shown under the PIN fields after an update attempt.
    var pinMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.setDriveSyncEnabled(true)
        }
    }

    // Shared by the switch and the "Conectar con Google" button so both go through the
    // same sign-in flow instead of duplicating it.
    fun launchGoogleSignIn() {
        if (activity != null) {
            // Returning from the sign-in activity must not relock the app, or the
            // result callback above would be lost along with this screen.
            AppLockController.process.skipNextBackgroundRelock()
            googleSignInHelper.signIn(activity, signInLauncher)
        }
    }

    // Enabling sync requires a linked Google account: if one is already present, turn
    // sync on directly; otherwise launch sign-in and let its success callback above
    // turn sync on once the account is actually linked.
    fun requestEnableDriveSync() {
        if (googleSignInHelper.lastAccount(context) != null) {
            viewModel.setDriveSyncEnabled(true)
        } else {
            launchGoogleSignIn()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(text = "Ajustes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Sincronizar grabaciones con Google Drive")
            Switch(
                checked = isDriveSyncEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) requestEnableDriveSync() else viewModel.setDriveSyncEnabled(false)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (activity != null) {
            Button(
                onClick = { launchGoogleSignIn() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Conectar con Google")
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(text = "Cambiar PIN", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPin,
            onValueChange = { newPin = it.filter(Char::isDigit); pinMessage = null },
            label = { Text("Nuevo PIN (mín. ${PinRules.MIN_LENGTH} dígitos)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { confirmPin = it.filter(Char::isDigit); pinMessage = null },
            label = { Text("Repite el nuevo PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        pinMessage?.let { (text, isError) ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val result = viewModel.changePin(newPin, confirmPin)
                pinMessage = PinRules.errorMessage(result)?.let { it to true }
                    ?: ("PIN actualizado" to false)
                if (result == PinValidation.OK) {
                    newPin = ""
                    confirmPin = ""
                }
            },
            enabled = newPin.isNotEmpty() && confirmPin.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Actualizar PIN")
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Activa \"Auto Voice Recorder\" en Accesibilidad para poder grabar con el " +
                "botón de volumen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ir a Accesibilidad")
        }
    }
}
