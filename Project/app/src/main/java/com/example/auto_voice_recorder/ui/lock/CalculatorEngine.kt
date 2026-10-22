package com.example.auto_voice_recorder.ui.lock

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalculatorOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE }

/**
 * State of the calculator-shaped lock screen.
 *
 * [entryIsDirect] separates numbers typed digit by digit from values the calculator produced
 * on its own (results, sign flips, percentages). Only a directly typed number can unlock the
 * app, so a computed result that happens to match the PIN must never do it.
 *
 * The initial `0` is a placeholder, not a typed entry: [entryIsDirect] is false and
 * [waitingForOperand] is true, so the first digit starts a new entry.
 */
data class CalculatorState(
    val display: String = "0",
    val currentValue: BigDecimal = BigDecimal.ZERO,
    val entryIsDirect: Boolean = false,
    val waitingForOperand: Boolean = true,
    val accumulator: BigDecimal? = null,
    val pendingOperator: CalculatorOperator? = null,
    val lastOperator: CalculatorOperator? = null,
    val lastOperand: BigDecimal? = null,
    val isError: Boolean = false,
)

data class CalculatorEqualsResult(
    val state: CalculatorState,
    val typedCode: String?,
)

/**
 * Pure state machine behind the calculator-shaped lock screen.
 *
 * It uses immediate execution, like a basic phone calculator: each operator evaluates the
 * previous pending operation before storing itself. Staying free of Android imports keeps the
 * unlock decision testable on the JVM.
 */
object CalculatorEngine {

    private val MATH_CONTEXT = MathContext(16, RoundingMode.HALF_UP)
    private val ONE_HUNDRED = BigDecimal.valueOf(100)
    private const val ERROR_DISPLAY = "Error"

    /**
     * Appends [digit] to the current entry, or starts a new entry when the display is a
     * placeholder, a result, or an error. There is no digit limit so that any existing PIN,
     * including a long one, can still be typed.
     */
    fun pressDigit(state: CalculatorState, digit: Char): CalculatorState {
        if (state.isError || state.waitingForOperand || !state.entryIsDirect) {
            return startEntry(state, digit.toString(), BigDecimal(digit.toString()))
        }
        val display = state.display + digit
        return state.copy(
            display = display,
            currentValue = display.toBigDecimal(),
            entryIsDirect = true,
            waitingForOperand = false,
        )
    }

    /** Adds a decimal separator at most once per directly typed entry. */
    fun pressDecimal(state: CalculatorState): CalculatorState {
        if (state.isError || state.waitingForOperand || !state.entryIsDirect) {
            return startEntry(state, "0.", BigDecimal.ZERO)
        }
        if (state.display.contains('.')) return state
        return state.copy(display = state.display + ".")
    }

    /**
     * Stores [operator] as pending, evaluating the previous pending operation first so that
     * chained input behaves like a basic calculator (`2 + 3 × 4 =` gives `20`).
     */
    fun pressOperator(state: CalculatorState, operator: CalculatorOperator): CalculatorState {
        if (state.isError) return state

        val pending = state.pendingOperator
        if (pending == null) {
            return state.copy(
                accumulator = state.currentValue,
                pendingOperator = operator,
                entryIsDirect = false,
                waitingForOperand = true,
            )
        }
        if (state.waitingForOperand) {
            return state.copy(pendingOperator = operator)
        }

        val result = calculate(state.accumulator ?: BigDecimal.ZERO, pending, state.currentValue)
            ?: return errorState()
        return state.copy(
            display = formatResult(result),
            currentValue = result,
            accumulator = result,
            pendingOperator = operator,
            entryIsDirect = false,
            waitingForOperand = true,
        )
    }

    /**
     * Evaluates the pending operation, or repeats the last one. Only a directly typed plain
     * number with nothing pending yields a [CalculatorEqualsResult.typedCode]; computed values
     * never unlock the app.
     */
    fun pressEquals(state: CalculatorState): CalculatorEqualsResult {
        if (state.isError) return CalculatorEqualsResult(state, typedCode = null)

        val pending = state.pendingOperator
        if (pending != null) {
            val operand = state.currentValue
            val result = calculate(state.accumulator ?: BigDecimal.ZERO, pending, operand)
                ?: return CalculatorEqualsResult(errorState(), typedCode = null)
            return CalculatorEqualsResult(
                state = state.copy(
                    display = formatResult(result),
                    currentValue = result,
                    entryIsDirect = false,
                    waitingForOperand = false,
                    accumulator = null,
                    pendingOperator = null,
                    lastOperator = pending,
                    lastOperand = operand,
                ),
                typedCode = null,
            )
        }

        val lastOperator = state.lastOperator
        val lastOperand = state.lastOperand
        if (lastOperator != null && lastOperand != null && !state.entryIsDirect) {
            val result = calculate(state.currentValue, lastOperator, lastOperand)
                ?: return CalculatorEqualsResult(errorState(), typedCode = null)
            return CalculatorEqualsResult(
                state = state.copy(
                    display = formatResult(result),
                    currentValue = result,
                    entryIsDirect = false,
                    waitingForOperand = false,
                    accumulator = null,
                    pendingOperator = null,
                ),
                typedCode = null,
            )
        }

        val typedCode = if (
            state.entryIsDirect &&
            !state.waitingForOperand &&
            state.display.isNotEmpty() &&
            state.display.all { it in '0'..'9' }
        ) {
            state.display
        } else {
            null
        }
        return CalculatorEqualsResult(state, typedCode)
    }

    fun pressClear(): CalculatorState = CalculatorState()

    /**
     * Removes the last character of a direct entry. A result, an error, or an empty entry
     * returns to the initial state instead.
     */
    fun pressBackspace(state: CalculatorState): CalculatorState {
        if (state.isError || !state.entryIsDirect || state.waitingForOperand ||
            state.display.length <= 1
        ) {
            return CalculatorState()
        }
        val display = state.display.dropLast(1)
        return state.copy(display = display, currentValue = display.toBigDecimal())
    }

    /** Flips the sign of the current value; the entry stops being a typed code. */
    fun pressSign(state: CalculatorState): CalculatorState {
        if (state.isError) return state
        val value = state.currentValue.negate()
        return state.copy(
            display = formatResult(value),
            currentValue = value,
            entryIsDirect = false,
            waitingForOperand = false,
            lastOperator = null,
            lastOperand = null,
        )
    }

    /** Divides the current value by 100; the entry stops being a typed code. */
    fun pressPercent(state: CalculatorState): CalculatorState {
        if (state.isError) return state
        val value = state.currentValue.divide(ONE_HUNDRED, MATH_CONTEXT)
        return state.copy(
            display = formatResult(value),
            currentValue = value,
            entryIsDirect = false,
            waitingForOperand = false,
            lastOperator = null,
            lastOperand = null,
        )
    }

    private fun startEntry(
        state: CalculatorState,
        display: String,
        currentValue: BigDecimal,
    ): CalculatorState = state.copy(
        display = display,
        currentValue = currentValue,
        entryIsDirect = true,
        waitingForOperand = false,
        lastOperator = null,
        lastOperand = null,
        isError = false,
    )

    /** Returns null when the operation is a division by zero. */
    private fun calculate(
        left: BigDecimal,
        operator: CalculatorOperator,
        right: BigDecimal,
    ): BigDecimal? = when (operator) {
        CalculatorOperator.ADD -> left + right
        CalculatorOperator.SUBTRACT -> left - right
        CalculatorOperator.MULTIPLY -> left * right
        CalculatorOperator.DIVIDE ->
            if (right.signum() == 0) null else left.divide(right, MATH_CONTEXT)
    }

    private fun errorState(): CalculatorState =
        CalculatorState(display = ERROR_DISPLAY, isError = true)

    /**
     * Plain strings avoid scientific notation and locale-dependent separators. A zero is
     * always shown as `0` because a negative zero is not a meaningful result.
     */
    private fun formatResult(value: BigDecimal): String =
        if (value.signum() == 0) "0" else value.stripTrailingZeros().toPlainString()
}
