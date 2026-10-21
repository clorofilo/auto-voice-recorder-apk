package com.example.auto_voice_recorder.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinRulesTest {

    @Test
    fun `matching digit-only PIN of minimum length is OK`() {
        assertEquals(PinValidation.OK, PinRules.validate("1234", "1234"))
    }

    @Test
    fun `PIN shorter than the minimum is TOO_SHORT`() {
        assertEquals(PinValidation.TOO_SHORT, PinRules.validate("123", "123"))
        assertEquals(PinValidation.TOO_SHORT, PinRules.validate("", ""))
    }

    @Test
    fun `PIN with non-digit characters is NOT_DIGITS`() {
        assertEquals(PinValidation.NOT_DIGITS, PinRules.validate("12a4", "12a4"))
    }

    @Test
    fun `PIN that does not match its confirmation is MISMATCH`() {
        assertEquals(PinValidation.MISMATCH, PinRules.validate("1234", "1235"))
    }
}
