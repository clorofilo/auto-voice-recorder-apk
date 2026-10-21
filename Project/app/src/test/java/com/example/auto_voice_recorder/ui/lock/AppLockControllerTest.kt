package com.example.auto_voice_recorder.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {

    @Test
    fun `starts locked so a new process always shows the lock screen`() {
        assertTrue(AppLockController().isLocked.value)
    }

    @Test
    fun `going to the background after unlocking relocks`() {
        val lock = AppLockController()
        lock.unlock()

        lock.onAppBackgrounded(isChangingConfigurations = false)

        assertTrue(lock.isLocked.value)
    }

    @Test
    fun `a configuration change such as rotation does not relock`() {
        val lock = AppLockController()
        lock.unlock()

        lock.onAppBackgrounded(isChangingConfigurations = true)

        assertFalse(lock.isLocked.value)
    }

    @Test
    fun `skipNextBackgroundRelock exempts exactly one background transition`() {
        val lock = AppLockController()
        lock.unlock()
        lock.skipNextBackgroundRelock()

        lock.onAppBackgrounded(isChangingConfigurations = false)
        assertFalse(lock.isLocked.value)

        lock.onAppBackgrounded(isChangingConfigurations = false)
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun `an unused exemption is cleared when the app resumes`() {
        val lock = AppLockController()
        lock.unlock()
        lock.skipNextBackgroundRelock()

        lock.onAppResumed()
        lock.onAppBackgrounded(isChangingConfigurations = false)

        assertTrue(lock.isLocked.value)
    }
}
