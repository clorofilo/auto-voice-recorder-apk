package com.example.auto_voice_recorder.security

/** Result of validating a new PIN entered twice. */
enum class PinValidation {
    OK,
    TOO_SHORT,
    NOT_DIGITS,
    MISMATCH,
}

/**
 * Rules for a new app-lock PIN, shared by the first-run PIN creation on the lock screen
 * and the PIN change in Settings: digits only (the lock screen only has a numeric
 * keypad) and at least [MIN_LENGTH] digits, entered identically twice.
 */
object PinRules {

    const val MIN_LENGTH = 4

    fun validate(pin: String, confirmation: String): PinValidation = when {
        pin.length < MIN_LENGTH -> PinValidation.TOO_SHORT
        !pin.all { it in '0'..'9' } -> PinValidation.NOT_DIGITS
        pin != confirmation -> PinValidation.MISMATCH
        else -> PinValidation.OK
    }

    /** User-facing message for a failed [validate] result, or null when it is [PinValidation.OK]. */
    fun errorMessage(result: PinValidation): String? = when (result) {
        PinValidation.OK -> null
        PinValidation.TOO_SHORT -> "El PIN debe tener al menos $MIN_LENGTH dígitos"
        PinValidation.NOT_DIGITS -> "El PIN solo puede contener números"
        PinValidation.MISMATCH -> "Los PIN no coinciden"
    }
}
