package com.example.auto_voice_recorder.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun CalculatorState.type(text: String): CalculatorState =
        text.fold(this) { state, digit -> CalculatorEngine.pressDigit(state, digit) }

    private fun CalculatorState.operator(operator: CalculatorOperator): CalculatorState =
        CalculatorEngine.pressOperator(this, operator)

    @Test
    fun `pressing digits builds the display and equals returns the typed code`() {
        val state = CalculatorState().type("1234")

        val result = CalculatorEngine.pressEquals(state)

        assertEquals("1234", result.state.display)
        assertEquals("1234", result.typedCode)
    }

    @Test
    fun `the first digit replaces the initial placeholder zero`() {
        val state = CalculatorState().type("7")

        assertEquals("7", state.display)
    }

    @Test
    fun `leading zeros typed explicitly are preserved in the code`() {
        val state = CalculatorState().type("0123")

        val result = CalculatorEngine.pressEquals(state)

        assertEquals("0123", result.state.display)
        assertEquals("0123", result.typedCode)
    }

    @Test
    fun `addition uses immediate execution`() {
        val state = CalculatorState().type("2").operator(CalculatorOperator.ADD).type("3")

        val result = CalculatorEngine.pressEquals(state)

        assertEquals("5", result.state.display)
        assertNull(result.typedCode)
    }

    @Test
    fun `chained operations evaluate as they are entered`() {
        val state = CalculatorState()
            .type("2")
            .operator(CalculatorOperator.ADD)
            .type("3")
            .operator(CalculatorOperator.MULTIPLY)
            .type("4")

        val result = CalculatorEngine.pressEquals(state)

        assertEquals("20", result.state.display)
    }

    @Test
    fun `subtraction can produce a negative result`() {
        val state = CalculatorState().type("2").operator(CalculatorOperator.SUBTRACT).type("5")

        assertEquals("-3", CalculatorEngine.pressEquals(state).state.display)
    }

    @Test
    fun `division keeps decimals`() {
        val state = CalculatorState().type("7").operator(CalculatorOperator.DIVIDE).type("2")

        assertEquals("3.5", CalculatorEngine.pressEquals(state).state.display)
    }

    @Test
    fun `division by zero shows an error and the next digit recovers`() {
        val error = CalculatorEngine.pressEquals(
            CalculatorState().type("1").operator(CalculatorOperator.DIVIDE).type("0"),
        ).state

        assertEquals("Error", error.display)
        assertTrue(error.isError)

        val recovered = CalculatorEngine.pressDigit(error, '7')

        assertFalse(recovered.isError)
        assertEquals("7", recovered.display)
    }

    @Test
    fun `clear resets the calculator`() {
        assertEquals(CalculatorState(), CalculatorEngine.pressClear())
    }

    @Test
    fun `backspace removes the last typed character`() {
        val state = CalculatorEngine.pressBackspace(CalculatorState().type("123"))

        assertEquals("12", state.display)
    }

    @Test
    fun `backspace after a result starts over from zero`() {
        val result = CalculatorEngine.pressEquals(
            CalculatorState().type("2").operator(CalculatorOperator.ADD).type("2"),
        ).state

        val state = CalculatorEngine.pressBackspace(result)

        assertEquals(CalculatorState(), state)
    }

    @Test
    fun `a decimal point is accepted only once per entry`() {
        val state = CalculatorState()
            .type("1")
            .let(CalculatorEngine::pressDecimal)
            .type("5")
            .let(CalculatorEngine::pressDecimal)
            .type("2")

        assertEquals("1.52", state.display)
        assertNull(CalculatorEngine.pressEquals(state).typedCode)
    }

    @Test
    fun `a computed result that matches the pin is not a typed code`() {
        val state = CalculatorState()
            .type("2")
            .operator(CalculatorOperator.ADD)
            .type("1232")

        val result = CalculatorEngine.pressEquals(state)

        assertEquals("1234", result.state.display)
        assertNull(result.typedCode)
    }

    @Test
    fun `sign and percent never produce a typed code`() {
        val signed = CalculatorEngine.pressSign(CalculatorState().type("1234"))
        val percent = CalculatorEngine.pressPercent(CalculatorState().type("1234"))

        assertNull(CalculatorEngine.pressEquals(signed).typedCode)
        assertNull(CalculatorEngine.pressEquals(percent).typedCode)
    }

    @Test
    fun `a pin longer than a normal calculator entry can still be typed`() {
        val longPin = "12345678901234567890"

        val result = CalculatorEngine.pressEquals(CalculatorState().type(longPin))

        assertEquals(longPin, result.state.display)
        assertEquals(longPin, result.typedCode)
    }

    @Test
    fun `repeated equals repeats the last operation`() {
        val first = CalculatorEngine.pressEquals(
            CalculatorState().type("2").operator(CalculatorOperator.ADD).type("3"),
        ).state

        val second = CalculatorEngine.pressEquals(first).state

        assertEquals("5", first.display)
        assertEquals("8", second.display)
    }

    @Test
    fun `a digit after equals starts a new entry`() {
        val result = CalculatorEngine.pressEquals(
            CalculatorState().type("2").operator(CalculatorOperator.ADD).type("2"),
        ).state

        val state = CalculatorEngine.pressDigit(result, '7')

        assertEquals("7", state.display)
    }

    @Test
    fun `typing after equals clears the repeat operation`() {
        val result = CalculatorEngine.pressEquals(
            CalculatorState().type("2").operator(CalculatorOperator.ADD).type("2"),
        ).state
        val typed = CalculatorEngine.pressDigit(result, '7')

        assertEquals("7", CalculatorEngine.pressEquals(typed).typedCode)
    }

    @Test
    fun `a digit after an operator starts a new entry`() {
        val state = CalculatorState().type("2").operator(CalculatorOperator.ADD).type("7")

        assertEquals("7", state.display)
    }

    @Test
    fun `pressing an operator twice replaces the pending operator`() {
        val state = CalculatorState()
            .type("5")
            .operator(CalculatorOperator.ADD)
            .operator(CalculatorOperator.MULTIPLY)
            .type("2")

        assertEquals("10", CalculatorEngine.pressEquals(state).state.display)
    }

    @Test
    fun `equals on the initial state returns no code`() {
        assertNull(CalculatorEngine.pressEquals(CalculatorState()).typedCode)
    }
}
