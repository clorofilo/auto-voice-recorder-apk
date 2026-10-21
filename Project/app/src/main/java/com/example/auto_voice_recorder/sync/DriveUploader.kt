package com.example.auto_voice_recorder.sync

import com.example.auto_voice_recorder.data.Recording
import java.io.File

/** Uploads a single recording's audio file to the app's Drive app-data folder. */
interface DriveUploader {
    suspend fun upload(file: File, recording: Recording): Result<Unit>
}
