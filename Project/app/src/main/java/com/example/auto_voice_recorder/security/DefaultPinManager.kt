package com.example.auto_voice_recorder.security

import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * [PinManager] that stores a salted SHA-256 hash of the PIN. The salt is random per
 * [setPin] call and is kept in the same preferences as the hash.
 */
class DefaultPinManager(private val prefs: SharedPreferences) : PinManager {

    override fun isPinSet(): Boolean =
        prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    override fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_HASH, Base64.getEncoder().encodeToString(hash))
            .apply()
    }

    override fun verifyPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.getDecoder().decode(saltB64)
        val expected = Base64.getDecoder().decode(hashB64)
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
        const val SALT_BYTES = 16
    }
}
