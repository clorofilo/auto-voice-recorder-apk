package com.example.auto_voice_recorder.ui.lock

import com.example.auto_voice_recorder.security.PinManager

/** In-memory [PinManager] for JVM unit tests. Stores the raw PIN, which is fine for tests only. */
class FakePinManager(private var pin: String? = null) : PinManager {

    override fun isPinSet(): Boolean = pin != null

    override fun setPin(pin: String) {
        this.pin = pin
    }

    override fun verifyPin(pin: String): Boolean = this.pin != null && this.pin == pin
}
