package com.example.auto_voice_recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val KEYPAD_ROWS = listOf(
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

private val HORIZONTAL_PADDING = 16.dp
private val VERTICAL_PADDING = 16.dp
private val COMPACT_VERTICAL_PADDING = 8.dp
private val DISPLAY_SPACING = 16.dp
private val COMPACT_DISPLAY_SPACING = 8.dp
private val ROW_SPACING = 8.dp
private val COMPACT_ROW_SPACING = 6.dp

/** Height reserved for the display when it is not allowed to take the remaining space. */
private val DISPLAY_HEIGHT = 56.dp
private val COMPACT_DISPLAY_HEIGHT = 44.dp

/**
 * Calculator-shaped lock screen: a working basic calculator where typing the PIN as a plain
 * number and pressing `=` unlocks the app. Wrong codes are silent, so the screen gives no hint
 * that a lock exists.
 *
 * The state is kept with [remember], never `rememberSaveable`, so a typed PIN is not written to
 * Android's saved instance state. It is also reset on [Lifecycle.Event.ON_STOP], so a half-typed
 * entry is not left on screen when the app comes back.
 *
 * The keypad is 64 dp high per key in portrait, but shrinks in short windows (landscape phones)
 * so the display and the `=` key stay reachable. If even the 48 dp touch target does not fit,
 * the content scrolls instead of being clipped.
 */
@Composable
fun CalculatorLockScreen(
    onEquals: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(CalculatorState()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state = CalculatorState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layout = calculatorLayout(maxHeight)
        val displayStyle = if (layout.compact) {
            MaterialTheme.typography.headlineMedium
        } else {
            MaterialTheme.typography.displaySmall
        }
        val onKey: (String) -> Unit = { key ->
            if (key == "=") {
                val result = CalculatorEngine.pressEquals(state)
                state = result.state
                onEquals(result.typedCode)
            } else {
                state = pressKey(state, key)
            }
        }

        if (layout.scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HORIZONTAL_PADDING, vertical = layout.verticalPadding),
            ) {
                CalculatorDisplay(
                    display = state.display,
                    style = displayStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.displayHeight),
                )
                Spacer(Modifier.height(layout.displaySpacing))
                CalculatorKeypad(
                    keyHeight = layout.keyHeight,
                    rowSpacing = layout.rowSpacing,
                    onKey = onKey,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = HORIZONTAL_PADDING, vertical = layout.verticalPadding),
            ) {
                CalculatorDisplay(
                    display = state.display,
                    style = displayStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(layout.displaySpacing))
                CalculatorKeypad(
                    keyHeight = layout.keyHeight,
                    rowSpacing = layout.rowSpacing,
                    onKey = onKey,
                )
            }
        }
    }
}

@Composable
private fun CalculatorDisplay(
    display: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        Text(
            text = display,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CalculatorKeypad(
    keyHeight: Dp,
    rowSpacing: Dp,
    onKey: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        KEYPAD_ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    CalculatorKey(
                        key = key,
                        onClick = { onKey(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight),
                    )
                }
            }
        }
    }
}

/** Padding, spacing and key size that keep the whole keypad inside [maxHeight]. */
private data class CalculatorLayout(
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
private fun calculatorLayout(maxHeight: Dp): CalculatorLayout {
    val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
    val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else VERTICAL_PADDING
    val displaySpacing = if (compact) COMPACT_DISPLAY_SPACING else DISPLAY_SPACING
    val rowSpacing = if (compact) COMPACT_ROW_SPACING else ROW_SPACING
    val displayHeight = if (compact) COMPACT_DISPLAY_HEIGHT else DISPLAY_HEIGHT
    val gaps = KEYPAD_ROWS.size - 1
    val availableForKeypad = maxHeight - verticalPadding * 2 - displayHeight - displaySpacing -
        rowSpacing * gaps
    val rawKeyHeight = availableForKeypad / KEYPAD_ROWS.size
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

/** Applies one non-`=` key. Every key in [KEYPAD_ROWS] is handled explicitly. */
private fun pressKey(state: CalculatorState, key: String): CalculatorState = when (key) {
    "C" -> CalculatorEngine.pressClear()
    "±" -> CalculatorEngine.pressSign(state)
    "%" -> CalculatorEngine.pressPercent(state)
    "⌫" -> CalculatorEngine.pressBackspace(state)
    "." -> CalculatorEngine.pressDecimal(state)
    "+" -> CalculatorEngine.pressOperator(state, CalculatorOperator.ADD)
    "−" -> CalculatorEngine.pressOperator(state, CalculatorOperator.SUBTRACT)
    "×" -> CalculatorEngine.pressOperator(state, CalculatorOperator.MULTIPLY)
    "÷" -> CalculatorEngine.pressOperator(state, CalculatorOperator.DIVIDE)
    else -> CalculatorEngine.pressDigit(state, key.single())
}

/**
 * One keypad key. Material 3 buttons only, so colors come from the theme: `=` is the primary
 * action, the operators get the tonal style and the rest stay outlined.
 */
@Composable
private fun CalculatorKey(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (key) {
        "=" -> Button(onClick = onClick, modifier = modifier) { Text(key) }
        "÷", "×", "−", "+" ->
            FilledTonalButton(onClick = onClick, modifier = modifier) { Text(key) }
        else -> OutlinedButton(onClick = onClick, modifier = modifier) { Text(key) }
    }
}
