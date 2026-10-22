package com.example.auto_voice_recorder.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

/**
 * Thin wrapper around Google Sign-In. Requests only the per-file
 * [DriveScopes.DRIVE_FILE] scope (files and folders this app creates) — never general
 * Drive access. Not unit
 * tested: it is a thin wrapper over Play Services, which unit tests cannot
 * exercise.
 */
class GoogleSignInHelper(context: Context) {

    private val client: GoogleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    )

    /** Launches the Google Sign-In flow for [activity] via [launcher]. */
    fun signIn(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(client.signInIntent)
    }

    /**
     * The most recently signed-in account with the Drive file scope granted, if any. Sessions
     * whose [GoogleSignInAccount.getAccount] is null (created before [requestEmail] was
     * added) are ignored: Drive's credential cannot use them, so the user must sign in again.
     */
    fun lastAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)
            ?.takeIf { it.account != null }
            ?.takeIf { GoogleSignIn.hasPermissions(it, Scope(DriveScopes.DRIVE_FILE)) }

    /**
     * Reads the sign-in result regardless of the activity result code: on failure Play
     * Services still returns the cause (e.g. DEVELOPER_ERROR) in [data] with RESULT_CANCELED.
     */
    fun accountFromResult(data: Intent?): Result<GoogleSignInAccount> =
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java) }

    /** Human-readable cause for a failed [accountFromResult]. */
    fun describeError(error: Throwable): String {
        val code = (error as? ApiException)?.statusCode
        return when (code) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Error de configuración (código 10): falta registrar el paquete y la huella SHA-1 en Google Cloud."
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Inicio de sesión cancelado."
            CommonStatusCodes.NETWORK_ERROR -> "Sin conexión de red."
            null -> error.message ?: "Error desconocido."
            else -> "No se pudo conectar (código $code)."
        }
    }
}
