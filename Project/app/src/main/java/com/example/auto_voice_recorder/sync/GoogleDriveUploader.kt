package com.example.auto_voice_recorder.sync

import android.content.Context
import com.example.auto_voice_recorder.data.Recording
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real [DriveUploader] backed by [com.google.api.services.drive.Drive].
 * Uploads are always scoped to the app-private `appDataFolder` space — this
 * app never requests or uses general Drive access. Not unit tested: it needs
 * real network access and a signed-in Google account, per the task spec.
 */
class GoogleDriveUploader(
    context: Context,
    account: GoogleSignInAccount
) : DriveUploader {

    private val drive: Drive = run {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            setOf(DriveScopes.DRIVE_APPDATA)
        ).apply { selectedAccount = account.account }

        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    override suspend fun upload(file: File, recording: Recording): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val metadata = DriveFile().apply {
                    name = file.name
                    // Restricts the uploaded file to the app's private
                    // appDataFolder space, not general Drive storage.
                    parents = listOf("appDataFolder")
                }
                val content = FileContent(AUDIO_MIME_TYPE, file)
                drive.files()
                    .create(metadata, content)
                    .setFields("id")
                    .execute()
                Unit
            }
        }

    private companion object {
        const val APPLICATION_NAME = "Auto Voice Recorder"
        const val AUDIO_MIME_TYPE = "audio/mp4"
    }
}
