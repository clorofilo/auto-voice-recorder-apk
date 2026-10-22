package com.example.auto_voice_recorder.sync

import android.content.Context
import android.util.Log
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
 * Recordings go into a visible `voice-recorder` folder in My Drive. With the
 * `drive.file` scope the app can only see files and folders it created itself,
 * so it creates that folder on first use and finds it again by name afterwards
 * (even if the user moves it elsewhere). Not unit tested: it needs
 * real network access and a signed-in Google account, per the task spec.
 */
class GoogleDriveUploader(
    context: Context,
    account: GoogleSignInAccount
) : DriveUploader {

    private val drive: Drive = run {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            setOf(DriveScopes.DRIVE_FILE)
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
                    parents = listOf(recordingsFolderId())
                }
                val content = FileContent(AUDIO_MIME_TYPE, file)
                drive.files()
                    .create(metadata, content)
                    .setFields("id")
                    .execute()
                Unit
            }.onFailure {
                Log.e(TAG, "Drive upload failed for ${file.name}", it)
                SyncStatus.report("Error al subir: ${it.javaClass.simpleName}: ${it.message}")
            }
        }

    /** Finds the app-created recordings folder, creating it if this is the first upload. */
    private fun recordingsFolderId(): String {
        val existing = drive.files().list()
            .setQ("mimeType = '$FOLDER_MIME_TYPE' and name = '$FOLDER_NAME' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
            .files
            .firstOrNull()
        if (existing != null) return existing.id

        val folder = DriveFile().apply {
            name = FOLDER_NAME
            mimeType = FOLDER_MIME_TYPE
        }
        return drive.files().create(folder).setFields("id").execute().id
    }

    private companion object {
        const val FOLDER_NAME = "voice-recorder"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val TAG = "DriveSync"
        const val APPLICATION_NAME = "Auto Voice Recorder"
        const val AUDIO_MIME_TYPE = "audio/mp4"
    }
}
