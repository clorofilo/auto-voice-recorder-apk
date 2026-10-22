package com.example.auto_voice_recorder.ui.lock

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keypad glyphs, row by row. Shared by the layout maths and the keypad composable. */
internal val CALCULATOR_KEYPAD_ROWS = listOf(
    listOf("C", "±", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "⌫", "="),
)

/** Below this height the screen switches to compact paddings, spacing and typography. */
private val COMPACT_HEIGHT_THRESHOLD = 480.dp

/** Material's minimum touch target: keys never shrink below this. */
private val MIN_KEY_HEIGHT = 48.dp

/** Portrait target height for a key. */
private val MAX_KEY_HEIGHT = 64.dp

private val VERTICAL_PADDING = 16.dp
private val COMPACT_VERTICAL_PADDING = 8.dp
private val DISPLAY_SPACING = 16.dp
private val COMPACT_DISPLAY_SPACING = 8.dp
private val ROW_SPACING = 8.dp
private val COMPACT_ROW_SPACING = 6.dp

/** Height reserved for the display when it is not allowed to take the remaining space. */
private val DISPLAY_HEIGHT = 56.dp
private val COMPACT_DISPLAY_HEIGHT = 44.dp

/** Padding, spacing and key size that keep the whole keypad inside [maxHeight]. */
internal data class CalculatorLayout(
    val compact: Boolean,
    val verticalPadding: Dp,
    val displaySpacing: Dp,
    val rowSpacing: Dp,
    val displayHeight: Dp,
    val keyHeight: Dp,
    val scrollable: Boolean,
)

/**
 * Fits the display and the five keypad rows into [maxHeight]. Compact windows trim the fixed
 * parts and the key height down to the 48 dp touch target; below that the screen scrolls
 * rather than clipping the `=` key.
 */
internal fun calculatorLayout(maxHeight: Dp): CalculatorLayout {
    val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
    val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else VERTICAL_PADDING
    val displaySpacing = if (compact) COMPACT_DISPLAY_SPACING else DISPLAY_SPACING
    val rowSpacing = if (compact) COMPACT_ROW_SPACING else ROW_SPACING
    val displayHeight = if (compact) COMPACT_DISPLAY_HEIGHT else DISPLAY_HEIGHT
    val gaps = CALCULATOR_KEYPAD_ROWS.size - 1
    val availableForKeypad = maxHeight - verticalPadding * 2 - displayHeight - displaySpacing -
        rowSpacing * gaps
    val rawKeyHeight = availableForKeypad / CALCULATOR_KEYPAD_ROWS.size
    return CalculatorLayout(
        compact = compact,
        verticalPadding = verticalPadding,
        displaySpacing = displaySpacing,
        rowSpacing = rowSpacing,
        displayHeight = displayHeight,
        keyHeight = rawKeyHeight.coerceIn(MIN_KEY_HEIGHT, MAX_KEY_HEIGHT),
        // When the raw height reaches the minimum, the fixed parts plus the keypad sum exactly
        // to maxHeight; otherwise the 48 dp floor overflows and the screen must scroll.
        scrollable = rawKeyHeight < MIN_KEY_HEIGHT,
    )
}
