package com.example.auto_voice_recorder.sync

import com.example.auto_voice_recorder.data.Recording
import java.io.File

/**
 * In-memory [DriveUploader] for unit tests. Records every recording it was
 * asked to upload and, per call, either succeeds or fails per [shouldSucceed].
 */
class FakeDriveUploader(
    private val shouldSucceed: (Recording) -> Boolean = { true }
) : DriveUploader {

    val uploadedRecordings = mutableListOf<Recording>()

    override suspend fun upload(file: File, recording: Recording): Result<Unit> {
        uploadedRecordings.add(recording)
        return if (shouldSucceed(recording)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("upload failed for recording ${recording.id}"))
        }
    }
}
