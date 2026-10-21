package com.example.auto_voice_recorder.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPinManagerTest {

    private fun newManager(): DefaultPinManager = DefaultPinManager(FakeSharedPreferences())

    @Test
    fun `isPinSet is false before any PIN is set`() {
        val manager = newManager()

        assertFalse(manager.isPinSet())
    }

    @Test
    fun `after setPin, isPinSet is true`() {
        val manager = newManager()

        manager.setPin("1234")

        assertTrue(manager.isPinSet())
    }

    @Test
    fun `verifyPin returns true for the exact PIN that was set`() {
        val manager = newManager()

        manager.setPin("1234")

        assertTrue(manager.verifyPin("1234"))
    }

    @Test
    fun `verifyPin returns false for any other PIN`() {
        val manager = newManager()

        manager.setPin("1234")

        assertFalse(manager.verifyPin("0000"))
        assertFalse(manager.verifyPin("12345"))
        assertFalse(manager.verifyPin(""))
    }

    @Test
    fun `setPin followed by setPin with a new value invalidates the old PIN`() {
        val manager = newManager()

        manager.setPin("1234")
        manager.setPin("9876")

        assertFalse(manager.verifyPin("1234"))
        assertTrue(manager.verifyPin("9876"))
    }
}
