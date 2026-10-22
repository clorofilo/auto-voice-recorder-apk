# Calculator Lock Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional calculator-shaped lock screen: the user types the PIN as a number, presses `=`, and the app opens; any other input behaves like a normal calculator with no error or hint.

**Architecture:** A pure Kotlin `CalculatorEngine` owns all calculator and unlock-candidate rules. `LockViewModel` verifies the candidate with the existing `PinManager` and exposes a mode flag read from a new `LockModePreferences`. `LockScreen` renders either the current PIN/biometric UI or a new `CalculatorLockScreen`. The mode is toggled from Settings and persisted in `EncryptedSharedPreferences`.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, JUnit 4.13.2, kotlinx-coroutines-test, AGP 9.2, Gradle 9.4.1, JDK 21.

**Spec:** `docs/superpowers/specs/2026-10-22-calculator-lock-mode-design.md`

## Global Constraints

- Work only in the `feat/calculator-lock-mode` worktree; do not touch `main`.
- `minSdk 29`, `target/compileSdk 36`, single module `Project/app`.
- No changes to Room schema, manifest, permissions, `exported`, backup rules, or notification behavior.
- The recording notification stays visible; this feature must never hide it.
- Wrong calculator codes are silent: no `LockUiState.Error`, message, color, vibration, or log.
- No PIN recovery: forgetting the PIN means clearing app data.
- Code, KDoc and comments in English; UI text and docs in Spanish.
- Logic in pure classes; Android wrappers stay thin. Tests use hand-written fakes, no mocks.
- Run commands from `Project/`; on Windows use `./gradlew.bat`.
- Before every commit: `./gradlew.bat assembleDebug testDebugUnitTest` must pass.
- Do not commit `local.properties`, keystores, `build/`, `graft/`, or secrets.
- Conventional Commits in English, one topic per commit.
- `AGENTS.md` and `CLAUDE.md` are intentionally not modified: they describe the main project policy.
  This variant's stance is documented in `README.md` and `docs/seguridad.md`.

## Review Focus

These are the failure modes most likely to bite a person, and where each is pinned:

1. **A typed PIN stays visible after backgrounding or rotation.** The calculator state must not use `rememberSaveable` and must reset on `ON_STOP`; manual check in Task 4.
2. **An existing long PIN cannot be entered, causing permanent lockout.** The engine must accept any number of digits; Task 1 test `a pin longer than a normal calculator entry can still be typed`.
3. **A PIN with leading zeros cannot be entered.** Explicit leading zeros must be preserved; Task 1 test `leading zeros typed explicitly are preserved in the code`.
4. **A computed result equal to the PIN unlocks the app.** Only a directly typed plain number is a candidate; Task 1 test `a computed result that matches the pin is not a typed code`.
5. **A wrong code shows an error or hint.** `submitCalculatorCode` must leave `uiState` untouched on failure; Task 3 test `submitCalculatorCode with an incorrect code leaves the state Idle without an error`.

---

### Task 1: Calculator Engine

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/CalculatorEngine.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/CalculatorEngineTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `enum class CalculatorOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE }`
  - `data class CalculatorState(display: String = "0", currentValue: BigDecimal = BigDecimal.ZERO, entryIsDirect: Boolean = false, waitingForOperand: Boolean = true, accumulator: BigDecimal? = null, pendingOperator: CalculatorOperator? = null, lastOperator: CalculatorOperator? = null, lastOperand: BigDecimal? = null, isError: Boolean = false)`
  - `data class CalculatorEqualsResult(val state: CalculatorState, val typedCode: String?)`
  - `object CalculatorEngine` with:
    - `fun pressDigit(state: CalculatorState, digit: Char): CalculatorState`
    - `fun pressDecimal(state: CalculatorState): CalculatorState`
    - `fun pressOperator(state: CalculatorState, operator: CalculatorOperator): CalculatorState`
    - `fun pressEquals(state: CalculatorState): CalculatorEqualsResult`
    - `fun pressClear(): CalculatorState`
    - `fun pressBackspace(state: CalculatorState): CalculatorState`
    - `fun pressSign(state: CalculatorState): CalculatorState`
    - `fun pressPercent(state: CalculatorState): CalculatorState`

- [ ] **Step 1: Write the failing test file**

Create `CalculatorEngineTest.kt` with this content:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `Project/`:

```bash
./gradlew.bat testDebugUnitTest --tests "*CalculatorEngineTest"
```

Expected: compilation failure with `Unresolved reference: CalculatorEngine` (the production file does not exist yet).

- [ ] **Step 3: Implement `CalculatorEngine.kt`**

Create the file with the types and signatures from the Interfaces block. Implement the state machine described in the spec, §8.4. Key rules:

- The initial `0` is a placeholder: `entryIsDirect = false` and `waitingForOperand = true`, so the first digit starts a new entry.
- Starting a new entry clears `lastOperator` and `lastOperand`.
- `entryIsDirect` means the display is a directly typed entry, not a result; `waitingForOperand` means the next digit must start a new entry.
- `pressSign` and `pressPercent` set `entryIsDirect = false` and clear `lastOperator`/`lastOperand`.
- `pressEquals` returns `typedCode = display` only when there is no pending operator, the entry is direct, the calculator is not waiting for an operand, and the display is all digits. It returns `typedCode = null` for every computed result.
- Use `BigDecimal`; format with `stripTrailingZeros().toPlainString()` and return `"0"` when `signum() == 0`. Divide with `MathContext(16, RoundingMode.HALF_UP)`.
- Division by zero returns a state with `display = "Error"` and `isError = true`. A digit press on that state starts a fresh entry; `pressClear`/`pressBackspace` reset it.
- `pressBackspace` returns `CalculatorState()` when the display is a result, an error, or an empty entry; otherwise it removes the last character.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew.bat testDebugUnitTest --tests "*CalculatorEngineTest"
```

Expected: `BUILD SUCCESSFUL`, all `CalculatorEngineTest` tests pass.

- [ ] **Step 5: Run the full unit suite**

```bash
./gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/CalculatorEngine.kt \
        Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/CalculatorEngineTest.kt
git commit -m "feat: add calculator engine"
```

---

### Task 2: Lock Mode Preference and Settings Toggle

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/security/LockModePreferences.kt`
- Create: `Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/FakeLockModePreferences.kt`
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/AppContainer.kt`
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/settings/SettingsViewModel.kt`
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/settings/SettingsScreen.kt`
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/navigation/AppNavHost.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/ui/settings/SettingsViewModelTest.kt`
- Docs: `docs/modelo-de-datos.md`, `docs/arquitectura.md`, `docs/ui-movil.md`

**Interfaces:**
- Consumes: `EncryptedPrefsFactory`, `PinManager`.
- Produces:
  - `interface LockModePreferences { fun isCalculatorLockEnabled(): Boolean; fun setCalculatorLockEnabled(enabled: Boolean) }`
  - `class DefaultLockModePreferences(prefs: SharedPreferences) : LockModePreferences`
  - `SettingsViewModel.isCalculatorLockEnabled: StateFlow<Boolean>`
  - `SettingsViewModel.setCalculatorLockEnabled(enabled: Boolean)`
  - `AppContainer.lockModePreferences: LockModePreferences`
  - `FakeLockModePreferences(enabled: Boolean = false)` in `ui.lock` for tests.

- [ ] **Step 1: Write the failing SettingsViewModel tests**

Add this fake first, in `Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/FakeLockModePreferences.kt`:

```kotlin
package com.example.auto_voice_recorder.ui.lock

import com.example.auto_voice_recorder.security.LockModePreferences

/** In-memory [LockModePreferences] for JVM unit tests. */
class FakeLockModePreferences(private var enabled: Boolean = false) : LockModePreferences {

    override fun isCalculatorLockEnabled(): Boolean = enabled

    override fun setCalculatorLockEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
```

In `SettingsViewModelTest.kt`, add the imports for `LockModePreferences` and `FakeLockModePreferences`, extend `newViewModel` with the new parameter, and add these tests:

```kotlin
private fun newViewModel(
    syncPreferences: SyncPreferences = FakeSyncPreferences(),
    pinManager: PinManager = FakePinManager(),
    vibrationPreferences: VibrationPreferences = FakeVibrationPreferences(),
    lockModePreferences: LockModePreferences = FakeLockModePreferences(),
) = SettingsViewModel(
    syncPreferences = syncPreferences,
    vibrationPreferences = vibrationPreferences,
    pinManager = pinManager,
    lockModePreferences = lockModePreferences,
    repository = repository,
    scheduleDriveSync = { scheduleCallCount++ },
)

@Test
fun `isCalculatorLockEnabled reflects the stored preference`() {
    val viewModel = newViewModel(
        lockModePreferences = FakeLockModePreferences(enabled = true),
    )

    assertTrue(viewModel.isCalculatorLockEnabled.value)
}

@Test
fun `setCalculatorLockEnabled(true) persists and updates the state`() {
    val preferences = FakeLockModePreferences(enabled = false)
    val viewModel = newViewModel(lockModePreferences = preferences)

    viewModel.setCalculatorLockEnabled(true)

    assertTrue(preferences.isCalculatorLockEnabled())
    assertTrue(viewModel.isCalculatorLockEnabled.value)
}

@Test
fun `setCalculatorLockEnabled(false) persists and updates the state`() {
    val preferences = FakeLockModePreferences(enabled = true)
    val viewModel = newViewModel(lockModePreferences = preferences)

    viewModel.setCalculatorLockEnabled(false)

    assertFalse(preferences.isCalculatorLockEnabled())
    assertFalse(viewModel.isCalculatorLockEnabled.value)
}

@Test
fun `setCalculatorLockEnabled(true) is ignored when no PIN is set`() {
    val preferences = FakeLockModePreferences(enabled = false)
    val viewModel = newViewModel(
        pinManager = FakePinManager(pin = null),
        lockModePreferences = preferences,
    )

    viewModel.setCalculatorLockEnabled(true)

    assertFalse(preferences.isCalculatorLockEnabled())
    assertFalse(viewModel.isCalculatorLockEnabled.value)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew.bat testDebugUnitTest --tests "*SettingsViewModelTest"
```

Expected: compilation failure because `LockModePreferences`, `FakeLockModePreferences`, `isCalculatorLockEnabled` and `setCalculatorLockEnabled` do not exist yet.

- [ ] **Step 3: Create `LockModePreferences.kt`**

```kotlin
package com.example.auto_voice_recorder.security

import android.content.SharedPreferences

/** Whether the app lock is shown as a calculator instead of the PIN/biometric screen. */
interface LockModePreferences {
    fun isCalculatorLockEnabled(): Boolean
    fun setCalculatorLockEnabled(enabled: Boolean)
}

/** [LockModePreferences] backed by a boolean flag in [SharedPreferences], disabled by default. */
class DefaultLockModePreferences(private val prefs: SharedPreferences) : LockModePreferences {

    override fun isCalculatorLockEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setCalculatorLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_ENABLED = "calculator_lock_enabled"
    }
}
```

- [ ] **Step 4: Wire the preference into `AppContainer` and `SettingsViewModel`**

In `AppContainer.kt`, add imports for `DefaultLockModePreferences` and `LockModePreferences`, and this property next to `pinManager`:

```kotlin
val lockModePreferences: LockModePreferences by lazy {
    DefaultLockModePreferences(EncryptedPrefsFactory().create(appContext))
}
```

In `SettingsViewModel.kt`:

- Add `private val lockModePreferences: LockModePreferences` to the constructor, after `pinManager`.
- Add:

```kotlin
private val _isCalculatorLockEnabled =
    MutableStateFlow(lockModePreferences.isCalculatorLockEnabled())
val isCalculatorLockEnabled: StateFlow<Boolean> = _isCalculatorLockEnabled.asStateFlow()

/**
 * Enables or disables the calculator lock. Enabling requires an existing PIN, because the
 * calculator is the only way in while the mode is active.
 */
fun setCalculatorLockEnabled(enabled: Boolean) {
    if (enabled && !pinManager.isPinSet()) return
    lockModePreferences.setCalculatorLockEnabled(enabled)
    _isCalculatorLockEnabled.value = enabled
}
```

- [ ] **Step 5: Add the toggle to `SettingsScreen` and wire `AppNavHost`**

In `SettingsScreen.kt`, collect the state:

```kotlin
val isCalculatorLockEnabled by viewModel.isCalculatorLockEnabled.collectAsState()
```

After the vibration row and before the "Cambiar PIN" section, add:

```kotlin
Spacer(Modifier.height(16.dp))
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text = "Desbloquear con calculadora")
    Switch(
        checked = isCalculatorLockEnabled,
        onCheckedChange = viewModel::setCalculatorLockEnabled,
    )
}
Spacer(Modifier.height(4.dp))
Text(
    text = "Al abrir la app aparecerá una calculadora. Escribe tu PIN y pulsa = para entrar. " +
        "Si olvidas el PIN no hay forma de recuperar el acceso.",
    style = MaterialTheme.typography.bodySmall,
)
```

In `AppNavHost.kt`, pass the new dependency to the settings view model:

```kotlin
SettingsViewModel(
    syncPreferences = container.syncPreferences,
    vibrationPreferences = container.vibrationPreferences,
    pinManager = container.pinManager,
    lockModePreferences = container.lockModePreferences,
    repository = container.recordingsRepository,
    scheduleDriveSync = container::scheduleDriveSync,
)
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew.bat testDebugUnitTest --tests "*SettingsViewModelTest"
./gradlew.bat assembleDebug
```

Expected: both commands `BUILD SUCCESSFUL`.

- [ ] **Step 7: Update the documentation that is already true after this task**

- `docs/modelo-de-datos.md`: add to the preferences table:

  ```markdown
  | `calculator_lock_enabled` | Boolean (def. `false`) | `DefaultLockModePreferences` | Bloqueo con calculadora activado |
  ```

- `docs/arquitectura.md`: add `LockModePreferences.kt` (interfaz + `DefaultLockModePreferences`) under `security/`, and mention `lockModePreferences` in the `AppContainer` paragraph.
- `docs/ui-movil.md`: add a short "Bloqueo con calculadora" subsection describing the Settings switch and the warning that there is no PIN recovery. The calculator screen details are added in Task 4.

- [ ] **Step 8: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/security/LockModePreferences.kt \
        Project/app/src/main/java/com/example/auto_voice_recorder/AppContainer.kt \
        Project/app/src/main/java/com/example/auto_voice_recorder/ui/settings/SettingsViewModel.kt \
        Project/app/src/main/java/com/example/auto_voice_recorder/ui/settings/SettingsScreen.kt \
        Project/app/src/main/java/com/example/auto_voice_recorder/ui/navigation/AppNavHost.kt \
        Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/FakeLockModePreferences.kt \
        Project/app/src/test/java/com/example/auto_voice_recorder/ui/settings/SettingsViewModelTest.kt \
        docs/modelo-de-datos.md docs/arquitectura.md docs/ui-movil.md
git commit -m "feat: add calculator lock preference and settings toggle"
```

---

### Task 3: Lock View Model Calculator Mode

**Files:**
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/LockViewModel.kt`
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/navigation/AppNavHost.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/LockViewModelTest.kt`

**Interfaces:**
- Consumes: `LockModePreferences`, `FakeLockModePreferences` from Task 2; existing `PinManager`.
- Produces:
  - `LockViewModel(pinManager: PinManager, lockModePreferences: LockModePreferences)`
  - `LockViewModel.isCalculatorLockEnabled: StateFlow<Boolean>`
  - `LockViewModel.submitCalculatorCode(code: String?)`

- [ ] **Step 1: Refactor `LockViewModelTest` to a factory and add the failing tests**

Add the imports for `PinManager` and `LockModePreferences`, and add this factory above the tests:

```kotlin
private fun newViewModel(
    pinManager: PinManager = FakePinManager(pin = "1234"),
    lockModePreferences: LockModePreferences = FakeLockModePreferences(),
) = LockViewModel(pinManager, lockModePreferences)
```

Replace every direct `LockViewModel(...)` construction in the file with `newViewModel(...)`, passing `pinManager = ...` where needed. Existing behavior must stay the same.

Add these tests:

```kotlin
@Test
fun `isCalculatorLockEnabled reflects the stored preference`() {
    val viewModel = newViewModel(
        lockModePreferences = FakeLockModePreferences(enabled = true),
    )

    assertTrue(viewModel.isCalculatorLockEnabled.value)
}

@Test
fun `submitCalculatorCode with the correct PIN unlocks`() {
    val viewModel = newViewModel(pinManager = FakePinManager(pin = "1234"))

    viewModel.submitCalculatorCode("1234")

    assertEquals(LockUiState.Unlocked, viewModel.uiState.value)
}

@Test
fun `submitCalculatorCode with an incorrect code leaves the state Idle without an error`() {
    val viewModel = newViewModel(pinManager = FakePinManager(pin = "1234"))

    viewModel.submitCalculatorCode("0000")

    assertEquals(LockUiState.Idle, viewModel.uiState.value)
}

@Test
fun `submitCalculatorCode with null does nothing`() {
    val viewModel = newViewModel()

    viewModel.submitCalculatorCode(null)

    assertEquals(LockUiState.Idle, viewModel.uiState.value)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew.bat testDebugUnitTest --tests "*LockViewModelTest"
```

Expected: compilation failure because the constructor still takes only `PinManager`, and `isCalculatorLockEnabled`/`submitCalculatorCode` do not exist.

- [ ] **Step 3: Implement the mode in `LockViewModel`**

Change the constructor to:

```kotlin
class LockViewModel(
    private val pinManager: PinManager,
    private val lockModePreferences: LockModePreferences,
) : ViewModel()
```

Add below `needsPinSetup`:

```kotlin
private val _isCalculatorLockEnabled =
    MutableStateFlow(lockModePreferences.isCalculatorLockEnabled())
val isCalculatorLockEnabled: StateFlow<Boolean> = _isCalculatorLockEnabled.asStateFlow()
```

Add:

```kotlin
/**
 * Verifies a code produced by the calculator lock. A wrong code must be indistinguishable
 * from a normal calculation, so it leaves [uiState] untouched instead of emitting Error.
 */
fun submitCalculatorCode(code: String?) {
    if (code != null && pinManager.verifyPin(code)) {
        _uiState.value = LockUiState.Unlocked
    }
}
```

Do not change `submitPin`, `createPin`, or `onBiometricSuccess`.

- [ ] **Step 4: Update `AppNavHost`**

Pass the preference to the lock view model:

```kotlin
initializer {
    LockViewModel(container.pinManager, container.lockModePreferences)
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew.bat testDebugUnitTest --tests "*LockViewModelTest"
./gradlew.bat assembleDebug
```

Expected: both commands `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/LockViewModel.kt \
        Project/app/src/main/java/com/example/auto_voice_recorder/ui/navigation/AppNavHost.kt \
        Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/LockViewModelTest.kt
git commit -m "feat: expose calculator lock mode in lock view model"
```

---

### Task 4: Calculator Lock Screen

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/CalculatorLockScreen.kt`
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/LockScreen.kt`
- Docs: `docs/seguridad.md`, `docs/ui-movil.md`, `docs/arquitectura.md`, `docs/testing.md`, `README.md`

**Interfaces:**
- Consumes: `CalculatorEngine`, `CalculatorState`, `CalculatorOperator` from Task 1; `LockViewModel.isCalculatorLockEnabled` and `LockViewModel.submitCalculatorCode` from Task 3.
- Produces: `@Composable fun CalculatorLockScreen(onEquals: (String?) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Create `CalculatorLockScreen.kt`**

Create a composable with this behavior:

- Signature:

  ```kotlin
  @Composable
  fun CalculatorLockScreen(
      onEquals: (String?) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- State: `var state by remember { mutableStateOf(CalculatorState()) }`. **Do not use `rememberSaveable`.**
- Lifecycle: observe `androidx.compose.ui.platform.LocalLifecycleOwner`; on `Lifecycle.Event.ON_STOP`, reset `state = CalculatorState()`. Follow the `DisposableEffect` pattern already used in `AppNavHost.kt`.
- Layout: `Column(modifier.fillMaxSize().padding(16.dp))`.
  - Display: a `Box` with `Modifier.fillMaxWidth().weight(1f)`, content aligned `Alignment.BottomEnd`; a `Text` with `state.display`, `MaterialTheme.typography.displaySmall`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.
  - Keypad: 5 rows × 4 columns using `Row`, `Modifier.weight(1f)` per key, 8 dp spacing, each key 64 dp high. Use Material 3 buttons; do not hardcode colors.
- Keypad rows, exactly:

  ```kotlin
  private val KEYPAD_ROWS = listOf(
      listOf("C", "±", "%", "÷"),
      listOf("7", "8", "9", "×"),
      listOf("4", "5", "6", "−"),
      listOf("1", "2", "3", "+"),
      listOf("0", ".", "⌫", "="),
  )
  ```

- Key dispatch:
  - `"C"` → `state = CalculatorEngine.pressClear()`
  - `"±"` → `state = CalculatorEngine.pressSign(state)`
  - `"%"` → `state = CalculatorEngine.pressPercent(state)`
  - `"⌫"` → `state = CalculatorEngine.pressBackspace(state)`
  - `"."` → `state = CalculatorEngine.pressDecimal(state)`
  - `"+"` → `pressOperator(state, CalculatorOperator.ADD)`
  - `"−"` → `pressOperator(state, CalculatorOperator.SUBTRACT)`
  - `"×"` → `pressOperator(state, CalculatorOperator.MULTIPLY)`
  - `"÷"` → `pressOperator(state, CalculatorOperator.DIVIDE)`
  - `"="` → `val result = CalculatorEngine.pressEquals(state); state = result.state; onEquals(result.typedCode)`
  - any digit → `pressDigit(state, key.single())`
- No app name, no "PIN", no "desbloquear", no biometric button, no hint text.

- [ ] **Step 2: Switch `LockScreen` between the two lock UIs**

In `LockScreen.kt`, collect the mode:

```kotlin
val calculatorMode by viewModel.isCalculatorLockEnabled.collectAsState()
```

Restructure the body so the calculator is not wrapped in the centered PIN column, without duplicating
the layout:

```kotlin
if (needsPinSetup || !calculatorMode) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (needsPinSetup) {
            CreatePinContent(viewModel = viewModel, uiState = uiState)
        } else {
            UnlockContent(viewModel = viewModel, uiState = uiState)
        }
    }
} else {
    CalculatorLockScreen(
        onEquals = viewModel::submitCalculatorCode,
        modifier = modifier,
    )
}
```

Keep the existing `LaunchedEffect(uiState)` that calls `onUnlocked`. `needsPinSetup` must stay first so a missing PIN can never render the calculator.

- [ ] **Step 3: Build and run the unit suite**

```bash
./gradlew.bat assembleDebug testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. The JVM suite has no Compose tests; the build verifies the new screen compiles.

- [ ] **Step 4: Update the security, UI and testing documentation**

- `docs/seguridad.md`:
  - In "Uso legal y responsable", stop saying the calculator mode is discarded. Say the main version discarded it and that this branch adds an **optional calculator lock**, explicitly approved, which does not hide the recording.
  - In "Protección de acceso a la app", add: optional calculator mode, no biometrics in that mode, silent wrong attempts, calculator state not saved in instance state and reset on `ON_STOP`, no PIN recovery.
  - In "Límites conocidos", add: silent failures plus no attempt limit make physical brute force possible; forgetting the PIN means clearing app data.
- `docs/ui-movil.md`:
  - Expand the "Bloqueo con calculadora" subsection with the calculator layout, the `=` unlock, the lack of error on wrong codes, the no-digit-cap rule, and the rotation/background reset.
- `docs/arquitectura.md`:
  - Add `CalculatorEngine.kt` and `CalculatorLockScreen.kt` to the `ui/lock/` structure.
  - Update "Bloqueo de la app" to mention the mode choice and `submitCalculatorCode`.
- `docs/testing.md`:
  - Add `CalculatorEngineTest` and `FakeLockModePreferences` to the test structure.
  - Add the calculator mode to the manual device checklist: enable it, verify the calculator appears, a wrong code does nothing, the correct PIN + `=` opens, rotation/background clears the entry, and disabling restores the normal lock.
- `README.md`:
  - Change the "Uso responsable" note so it no longer says the app never disguises itself; explain that this variant has an optional calculator **lock screen** and still shows the recording notification.
  - Add the calculator lock to "Qué hace" and remove "modo oculto tipo calculadora" from "Fuera de alcance"; replace it with "ocultación de la grabación".

- [ ] **Step 5: Run the full verification**

```bash
./gradlew.bat assembleDebug testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, tests pass.

- [ ] **Step 6: Manual checks on a device (or report which remain)**

If a device is connected, install the debug APK and verify:

- [ ] Enable "Desbloquear con calculadora" in Ajustes and confirm the warning text.
- [ ] Background the app and reopen: only the calculator appears.
- [ ] `2 + 2 =` → `4`; `7 ÷ 2 =` → `3.5`; `1 ÷ 0 =` → `Error`; `C`; `⌫`; `±`; `%`.
- [ ] Type a wrong code and `=`: no error, no hint, app stays locked.
- [ ] Type the PIN and `=`: the recordings list opens.
- [ ] Rotate while typing: the calculator resets to `0`.
- [ ] Background while typing and return: the calculator resets to `0`.
- [ ] Disable the mode: the next lock shows the PIN/biometric screen again.
- [ ] The recording notification is still visible and the volume gesture still starts/stops recording.

- [ ] **Step 7: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/CalculatorLockScreen.kt \
        Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/LockScreen.kt \
        docs/seguridad.md docs/ui-movil.md docs/arquitectura.md docs/testing.md README.md
git commit -m "feat: add calculator lock screen"
```

---

## Final Verification

After Task 4, run from `Project/`:

```bash
./gradlew.bat assembleDebug testDebugUnitTest
git status --short
```

Expected: `BUILD SUCCESSFUL`; `git status` clean except ignored files (`local.properties`, `build/`, `.gradle/`). The branch should contain four commits on top of `main`:

- `feat: add calculator engine`
- `feat: add calculator lock preference and settings toggle`
- `feat: expose calculator lock mode in lock view model`
- `feat: add calculator lock screen`

Report the APK path (`Project/app/build/outputs/apk/debug/app-debug.apk`) and any manual checks that could not be run without a device.
