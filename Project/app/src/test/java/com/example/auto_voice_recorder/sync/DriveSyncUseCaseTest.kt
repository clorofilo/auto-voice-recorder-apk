package com.example.auto_voice_recorder.sync

import com.example.auto_voice_recorder.data.FakeRecordingDao
import com.example.auto_voice_recorder.data.RecordingsRepository
import com.example.auto_voice_recorder.data.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSyncUseCaseTest {

    private val dao = FakeRecordingDao()
    private val repository = RecordingsRepository(dao)

    @Test
    fun `syncPending with syncEnabled true uploads every PENDING recording and marks each SYNCED`() = runTest {
        val a = repository.add("/rec/a.m4a", 1L, 1L, SyncState.PENDING)
        val b = repository.add("/rec/b.m4a", 2L, 2L, SyncState.PENDING)
        val uploader = FakeDriveUploader()
        val useCase = DriveSyncUseCase(repository, uploader, syncEnabled = { true })

        useCase.syncPending()

        assertEquals(2, uploader.uploadedRecordings.size)
        assertTrue(repository.pendingSyncRecordings().isEmpty())
        val all = repository.observeAll().first()
        assertEquals(SyncState.SYNCED, all.first { it.id == a }.syncState)
        assertEquals(SyncState.SYNCED, all.first { it.id == b }.syncState)
    }

    @Test
    fun `syncPending with syncEnabled false uploads nothing and leaves all recordings PENDING`() = runTest {
        repository.add("/rec/a.m4a", 1L, 1L, SyncState.PENDING)
        repository.add("/rec/b.m4a", 2L, 2L, SyncState.PENDING)
        val uploader = FakeDriveUploader()
        val useCase = DriveSyncUseCase(repository, uploader, syncEnabled = { false })

        useCase.syncPending()

        assertTrue(uploader.uploadedRecordings.isEmpty())
        assertEquals(2, repository.pendingSyncRecordings().size)
    }

    @Test
    fun `syncPending does not mark a recording SYNCED when its upload fails`() = runTest {
        val failing = repository.add("/rec/fail.m4a", 1L, 1L, SyncState.PENDING)
        val uploader = FakeDriveUploader(shouldSucceed = { false })
        val useCase = DriveSyncUseCase(repository, uploader, syncEnabled = { true })

        useCase.syncPending()

        assertEquals(1, repository.pendingSyncRecordings().size)
        assertEquals(failing, repository.pendingSyncRecordings().single().id)
    }

    @Test
    fun `syncPending returns a SyncSummary with correct uploaded and failed counts`() = runTest {
        repository.add("/rec/ok1.m4a", 1L, 1L, SyncState.PENDING)
        val fail = repository.add("/rec/fail.m4a", 2L, 2L, SyncState.PENDING)
        repository.add("/rec/ok2.m4a", 3L, 3L, SyncState.PENDING)
        val uploader = FakeDriveUploader(shouldSucceed = { recording -> recording.id != fail })
        val useCase = DriveSyncUseCase(repository, uploader, syncEnabled = { true })

        val summary = useCase.syncPending()

        assertEquals(2, summary.uploaded)
        assertEquals(1, summary.failed)
    }
}
