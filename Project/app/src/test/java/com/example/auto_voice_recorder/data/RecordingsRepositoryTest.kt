package com.example.auto_voice_recorder.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingsRepositoryTest {

    private val dao = FakeRecordingDao()
    private val repo = RecordingsRepository(dao)

    @Test
    fun `add stores a recording with the given initial sync state`() = runTest {
        val id = repo.add(
            filePath = "/rec/a.m4a",
            createdAtEpochMs = 1000L,
            durationMs = 5000L,
            initialState = SyncState.PENDING
        )

        val stored = repo.observeAll().first().single()
        assertEquals(id, stored.id)
        assertEquals("/rec/a.m4a", stored.filePath)
        assertEquals(1000L, stored.createdAtEpochMs)
        assertEquals(5000L, stored.durationMs)
        assertEquals(SyncState.PENDING, stored.syncState)
    }

    @Test
    fun `delete removes the recording by id`() = runTest {
        val keep = repo.add("/rec/keep.m4a", 1L, 1L, SyncState.SYNCED)
        val drop = repo.add("/rec/drop.m4a", 2L, 2L, SyncState.SYNCED)

        repo.delete(drop)

        val remaining = repo.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(keep, remaining.single().id)
    }

    @Test
    fun `markSynced transitions a PENDING recording to SYNCED`() = runTest {
        val id = repo.add("/rec/p.m4a", 1L, 1L, SyncState.PENDING)

        repo.markSynced(id)

        assertEquals(SyncState.SYNCED, repo.observeAll().first().single().syncState)
    }

    @Test
    fun `pendingSyncRecordings returns only PENDING recordings`() = runTest {
        repo.add("/rec/pending.m4a", 1L, 1L, SyncState.PENDING)
        repo.add("/rec/synced.m4a", 2L, 2L, SyncState.SYNCED)
        repo.add("/rec/disabled.m4a", 3L, 3L, SyncState.DISABLED)

        val pending = repo.pendingSyncRecordings()

        assertEquals(1, pending.size)
        assertTrue(pending.all { it.syncState == SyncState.PENDING })
        assertEquals("/rec/pending.m4a", pending.single().filePath)
    }
}
