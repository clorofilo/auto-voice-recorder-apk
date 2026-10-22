package com.example.auto_voice_recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Calculator-shaped lock screen: a working basic calculator where typing the PIN as a plain
 * number and pressing `=` unlocks the app. Wrong codes are silent, so the screen gives no hint
 * that a lock exists.
 *
 * The state is kept with [remember], never `rememberSaveable`, so a typed PIN is not written to
 * Android's saved instance state. It is also reset on [Lifecycle.Event.ON_STOP], so a half-typed
 * entry is not left on screen when the app comes back.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Text(
                text = state.display,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KEYPAD_ROWS.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        CalculatorKey(
                            key = key,
                            onClick = {
                                if (key == "=") {
                                    val result = CalculatorEngine.pressEquals(state)
                                    state = result.state
                                    onEquals(result.typedCode)
                                } else {
                                    state = pressKey(state, key)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                        )
                    }
                }
            }
        }
    }
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
