package com.example.auto_voice_recorder.security

/** Stores and verifies the app-lock PIN. Implementations must never persist the raw PIN. */
interface PinManager {
    fun isPinSet(): Boolean
    fun setPin(pin: String)
    fun verifyPin(pin: String): Boolean
}
