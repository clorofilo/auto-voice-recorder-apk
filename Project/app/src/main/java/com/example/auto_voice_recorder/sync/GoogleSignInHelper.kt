package com.example.auto_voice_recorder.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

/**
 * Thin wrapper around Google Sign-In. Requests only the app-private
 * [DriveScopes.DRIVE_APPDATA] scope — never general Drive access. Not unit
 * tested: it is a thin wrapper over Play Services, which unit tests cannot
 * exercise.
 */
class GoogleSignInHelper(context: Context) {

    private val client: GoogleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    )

    /** Launches the Google Sign-In flow for [activity] via [launcher]. */
    fun signIn(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(client.signInIntent)
    }

    /** The most recently signed-in account with the appdata scope granted, if any. */
    fun lastAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)
}
