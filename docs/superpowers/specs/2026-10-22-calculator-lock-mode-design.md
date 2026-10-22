# Modo calculadora para el bloqueo (variante)

- **Fecha:** 2026-10-22
- **Rama:** `feat/calculator-lock-mode`
- **Worktree:** `C:\Users\jguillen\.local\share\opencode\worktree\74d7d5\calculator-lock-mode`
- **Base:** `main` en `730d12e`
- **Estado:** pendiente de aprobación

## 1. Intención

Esta variante de la app añade un **modo de bloqueo opcional con calculadora**. Cuando está activo, al abrir la app
no aparece la pantalla actual de PIN + huella/Face, sino una calculadora funcional. El usuario escribe su PIN como
si fuera un número y pulsa `=`; si coincide, la app se abre. Si no coincide, la calculadora se comporta como una
calculadora normal: calcula, muestra el resultado y no da ninguna pista de que existe un bloqueo detrás.

El objetivo es que la app pueda abrirse sin que un observador casual vea una pantalla de PIN, huella o el nombre de
la app. **No** se oculta la grabación: la notificación obligatoria del servicio en primer plano sigue visible y el
servicio de accesibilidad no cambia.

Esta funcionalidad vive solo en esta rama/worktree. No se propone para `main` salvo petición expresa, porque
`docs/seguridad.md` descarta el patrón de "modo calculadora" en la versión principal.

## 2. Comportamiento

### 2.1 Activar y desactivar

- Hay un interruptor en **Ajustes**: "Desbloquear con calculadora".
- Solo se puede activar desde dentro de la app, es decir, con el bloqueo ya superado y un PIN existente.
- Por defecto está desactivado. Al activarlo, se aplica **en el siguiente bloqueo** (al volver del segundo plano o
  al abrir la app de nuevo).
- Al desactivarlo, el siguiente bloqueo vuelve a mostrar la pantalla actual de PIN + biometría.
- El valor se guarda en las preferencias cifradas, junto al PIN, y sobrevive a reinicios.

### 2.2 Pantalla de calculadora

- Ocupa toda la pantalla, sin título de la app, sin textos de "PIN", "desbloquear" ni pistas.
- Muestra una pantalla tipo calculadora: display grande y botonera de 4 columnas.
- Operaciones: `+`, `−`, `×`, `÷`, decimales, `C` (limpiar), `⌫` (borrar), `±` (cambiar signo), `%` y `=`.
- No hay botón de huella/Face ni teclado de PIN en este modo.
- La calculadora es **funcional de verdad**, no un teclado disfrazado: cualquier secuencia que no sea el PIN
  produce un resultado normal.

### 2.3 Desbloqueo

- El PIN se introduce como un número directo (solo dígitos) y se pulsa `=`.
- Se desbloquea **solo** si el número mostrado es exactamente el PIN y se ha tecleado directamente.
- Un resultado calculado nunca desbloquea, aunque coincida con el PIN. Ejemplo: si el PIN es `1234`, teclear
  `2 + 1232 =` muestra `1234` pero **no** abre la app.
- Si el código es incorrecto, no se muestra error, no se cambia el color, no hay vibración ni pista. La
  calculadora muestra su resultado y sigue como si nada.
- Los intentos incorrectos no se registran en logs.
- No hay límite de intentos ni bloqueo temporal (igual que el bloqueo actual; deuda ya documentada).

### 2.4 PIN olvidado

- **No hay mecanismo de recuperación.** Si se olvida el PIN, la única salida es borrar los datos de la app, con
  pérdida de las grabaciones locales.
- El texto del interruptor en Ajustes lo advierte antes de activarlo.

### 2.5 Primer arranque

- Si no existe PIN, se mantiene el flujo actual de creación de PIN. El modo calculadora no puede activarse antes
  de que exista un PIN; si por un estado corrupto estuviera activo sin PIN, la pantalla de creación de PIN tiene
  prioridad.

## 3. Objetivos

- Añadir el modo calculadora como alternativa opcional al desbloqueo actual.
- Mantener intacta la seguridad del PIN (hash con sal, comparación en tiempo constante).
- Mantener intacto todo lo relativo a la grabación: notificación visible, permisos, servicio y accesibilidad.
- Que la lógica de la calculadora y del desbloqueo sea testeable en JVM.
- Que cambiar de modo no deje rutas protegidas accesibles ni estados a medias.

## 4. No objetivos

- Cambiar el icono o el nombre de la app en el launcher.
- Ocultar la notificación de grabación o el hecho de que la app graba.
- Añadir recuperación de PIN, PIN maestro, correo de rescate ni copia de seguridad del PIN.
- Añadir límite de intentos, bloqueo temporal o biometría en modo calculadora.
- Cambiar el esquema de Room, los permisos, el manifiesto, `exported` o las reglas de backup.
- Implementar una calculadora científica o con precedencia algebraica.

## 5. Anti-objetivos

- Que un código incorrecto muestre "PIN incorrecto" o cualquier indicio de bloqueo.
- Que un resultado calculado sirva para desbloquear.
- Que el modo calculadora se muestre también en el launcher (icono/nombre).
- Que la app deje de mostrar la notificación de grabación o permita grabar en modo oculto.
- Que el PIN tecleado **en la calculadora** (visible en pantalla) quede persistido en el estado guardado de
  Android, en logs o en preferencias en claro. Los campos de PIN ya existentes siguen usando `rememberSaveable`
  con máscara y quedan fuera del alcance de esta variante.

## 6. Restricciones del proyecto

- `minSdk 29`, `target/compileSdk 36`, Kotlin 2.2, Compose Material 3, JDK 21.
- Sin DI framework: el contenedor manual `AppContainer` construye las dependencias.
- Interfaz + `Default…` para implementaciones reales; `Fake…` en tests JVM.
- Toda lógica nueva lleva test JVM. Los envoltorios finos de Android no se testean en unitario.
- Código, KDoc y comentarios en inglés; textos de UI y documentación en español.
- No modificar el esquema de Room sin migración.
- No quitar `safeDrawingPadding()` del `NavHost`, el permiso `INTERNET` ni las exclusiones de backup.
- Las rutas protegidas de `AppNavHost` siguen empezando por `if (isLocked) return@composable`.
- Un solo `AppDatabase.getInstance()` por proceso.
- Commits en inglés con Conventional Commits, un tema por commit.
- No hacer push sin petición.

## 7. Enfoques considerados

### A. Una sola pantalla de bloqueo que cambia de cara (elegida)

La ruta `lock` sigue siendo la única puerta de entrada. `LockScreen` consulta el modo guardado y decide si pinta
la pantalla actual o `CalculatorLockScreen`. El motor de la calculadora es una clase pura, sin Android, con tests
JVM. El interruptor escribe en una interfaz nueva `LockModePreferences`.

- **Ventaja:** un único punto de bloqueo; no se toca el manifiesto ni la navegación; menos superficie para un
  bypass.
- **Coste:** `LockScreen` y `LockViewModel` crecen un poco; la calculadora vive en la misma ruta.
- **Por qué se elige:** cumple el comportamiento con el menor riesgo de seguridad nuevo.

### B. Superficie de bloqueo separada (ruta o activity propia)

El modo calculadora tendría su propia pantalla y su propio `ViewModel`, elegidos al arrancar según la preferencia.

- **Ventaja:** separación visual y de código.
- **Coste:** duplicar o mover el ciclo de bloqueo; el destino inicial de la navegación no se reevalúa si el modo
  cambia en caliente; más código y más formas de romper el re-bloqueo.
- **Descartada:** no aporta nada que A no tenga y añade superficie de fallo.

### C. Calculadora solo estética sobre el teclado de PIN

Se mantendría el teclado actual con aspecto de calculadora, sin operaciones reales.

- **Descartada:** no cumple "como una calculadora normal"; un código incorrecto delataría el bloqueo.

## 8. Diseño técnico

### 8.1 Preferencia `LockModePreferences`

Nuevo fichero `security/LockModePreferences.kt`, siguiendo el patrón de `VibrationPreferences`:

```kotlin
interface LockModePreferences {
    fun isCalculatorLockEnabled(): Boolean
    fun setCalculatorLockEnabled(enabled: Boolean)
}

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

- Se guarda en `app_lock_prefs` (las mismas preferencias cifradas que el PIN).
- Clave nueva: `calculator_lock_enabled` (Boolean, por defecto `false`).
- `AppContainer` expone `val lockModePreferences: LockModePreferences by lazy { … }`.
- No lleva test propio: es un envoltorio fino de una sola línea, igual que `DefaultVibrationPreferences`.

### 8.2 `LockViewModel`

Constructor nuevo:

```kotlin
class LockViewModel(
    private val pinManager: PinManager,
    private val lockModePreferences: LockModePreferences,
) : ViewModel()
```

Estado nuevo:

```kotlin
private val _isCalculatorLockEnabled = MutableStateFlow(lockModePreferences.isCalculatorLockEnabled())
val isCalculatorLockEnabled: StateFlow<Boolean> = _isCalculatorLockEnabled.asStateFlow()
```

Método nuevo:

```kotlin
fun submitCalculatorCode(code: String?) {
    if (code != null && pinManager.verifyPin(code)) {
        _uiState.value = LockUiState.Unlocked
    }
}
```

- Si `code` es `null` o incorrecto, **no** se toca `uiState` (no se emite `Error`).
- `verifyPin` sigue siendo el único punto de comparación (hash + sal, tiempo constante).
- El PIN no se guarda en el `ViewModel` ni en ningún `StateFlow`.

`submitPin`, `createPin` y `onBiometricSuccess` no cambian.

### 8.3 `LockScreen`

Se añade la lectura del modo y la decisión de qué pintar:

```kotlin
val calculatorMode by viewModel.isCalculatorLockEnabled.collectAsState()

when {
    needsPinSetup -> CreatePinContent(viewModel, uiState)
    calculatorMode -> CalculatorLockScreen(onEquals = viewModel::submitCalculatorCode)
    else -> UnlockContent(viewModel, uiState)
}
```

- El `LaunchedEffect(uiState)` que llama a `onUnlocked` no cambia.
- En modo calculadora no se compone `UnlockContent`, así que no hay botón de biometría ni teclado de PIN.
- `needsPinSetup` tiene prioridad absoluta.

### 8.4 Motor `CalculatorEngine`

Nuevo fichero `ui/lock/CalculatorEngine.kt`, Kotlin puro (sin imports de Android), con tests JVM.

```kotlin
enum class CalculatorOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE }

data class CalculatorState(
    val display: String = "0",
    val currentValue: BigDecimal = BigDecimal.ZERO,
    // The initial "0" is a placeholder, not a typed entry: the first digit starts a new entry.
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
```

`CalculatorEngine` es un `object` con funciones puras que reciben y devuelven el estado:

| Función | Comportamiento |
|---|---|
| `pressDigit(state, digit)` | Si `waitingForOperand` o la entrada no es directa, empieza una entrada nueva. Si no, añade el dígito. Sin límite de dígitos, para que cualquier PIN existente se pueda teclear. Si el estado era `Error`, reinicia y empieza con el dígito. |
| `pressDecimal(state)` | Añade `.` solo si la entrada actual no tiene ya uno; si empieza entrada nueva, usa `0.`. |
| `pressOperator(state, op)` | Con un operador pendiente y una entrada nueva, calcula el parcial; si no hay entrada nueva, sustituye el operador. Guarda el acumulador y marca `waitingForOperand = true`. |
| `pressEquals(state)` | Con operador pendiente, calcula y muestra el resultado; guarda la última operación para repetir `=`; devuelve `typedCode = null`. Sin operador pendiente y con entrada directa de solo dígitos, devuelve `typedCode = display`. Sin nada pendiente, repite la última operación si existe. |
| `pressClear()` | Vuelve al estado inicial. |
| `pressBackspace(state)` | Borra el último carácter de una entrada directa; si la entrada es un resultado, un error o está vacía, vuelve a `0`. |
| `pressSign(state)` | Cambia el signo del valor actual; la entrada deja de ser directa (no puede desbloquear). |
| `pressPercent(state)` | Divide el valor actual entre 100; la entrada deja de ser directa. |
| `formatResult(value)` | `BigDecimal` con `stripTrailingZeros().toPlainString()`; división con `MathContext(16, HALF_UP)`. |

Reglas de cálculo:

- **Ejecución inmediata**, como una calculadora básica de teléfono: `2 + 3 × 4 =` da `20`, no `14`.
- División entre cero: display `Error`, estado de error; el siguiente dígito o `C` recupera la calculadora.
- `=` repetido repite la última operación con el último operando (comportamiento habitual).
- El `0` inicial es un placeholder: al teclear el primer dígito se sustituye. Si el usuario teclea explícitamente
  `0` y luego más dígitos, se conservan los ceros (`0123` se ve `0123`), para que un PIN que empiece por cero
  también se pueda introducir y desbloquear.
- No hay tope de dígitos: `PinRules` solo exige un mínimo de 4 y no limita la longitud, así que un PIN largo
  existente debe poder teclearse. Si no cabe en el display, se recorta visualmente, pero la entrada sigue activa.
- El separador decimal es `.` (no `,`) para no depender del locale; el PIN solo usa dígitos.
- No hay notación científica: resultados muy grandes se muestran en formato plano.

Regla de desbloqueo (`typedCode`):

```
typedCode = display
    si no hay error
    y la entrada se tecleó directamente (entryIsDirect)
    y no hay operador pendiente
    y no se está esperando un operando
    y display está formado solo por dígitos
```

Un resultado de una operación, un decimal, un signo o un porcentaje nunca producen `typedCode`.

### 8.5 `CalculatorLockScreen`

Nuevo fichero `ui/lock/CalculatorLockScreen.kt`.

- `@Composable fun CalculatorLockScreen(onEquals: (String?) -> Unit)`.
- Mantiene el `CalculatorState` con `remember { mutableStateOf(CalculatorState()) }` (**no** `rememberSaveable`):
  así el PIN tecleado no se guarda en el estado de instancia de Android.
- Se reinicia a `CalculatorState()` en `ON_STOP` (la app pasa a segundo plano), para que una entrada a medias no
  quede visible al volver. También se pierde al girar la pantalla; es una decisión de seguridad asumida.
- Layout:
  - `Column` a pantalla completa con `padding(16.dp)`.
  - Display arriba/centro, alineado a la derecha, `MaterialTheme.typography.displaySmall`, una línea; si el
    número no cabe, se recorta visualmente (`TextOverflow.Ellipsis`) sin limitar lo que se puede teclear.
  - Botonera de 5 filas × 4 columnas, con `Row`, `Modifier.weight(1f)` y separación de 8 dp:
    ```
    C   ±   %   ÷
    7   8   9   ×
    4   5   6   −
    1   2   3   +
    0   .   ⌫   =
    ```
  - Botones Material 3; `=` con `Button`, operadores con `FilledTonalButton`, resto con `OutlinedButton` o
    `FilledTonalButton`. Sin colores hardcodeados.
- Cada pulsación actualiza el estado; al pulsar `=`, llama a `onEquals(result.typedCode)`.
- Sin textos de la app, sin "PIN", sin "desbloquear", sin icono de la app.

### 8.6 Ajustes

`SettingsViewModel` recibe `lockModePreferences: LockModePreferences` y expone:

```kotlin
private val _isCalculatorLockEnabled = MutableStateFlow(lockModePreferences.isCalculatorLockEnabled())
val isCalculatorLockEnabled: StateFlow<Boolean> = _isCalculatorLockEnabled.asStateFlow()

fun setCalculatorLockEnabled(enabled: Boolean) {
    if (enabled && !pinManager.isPinSet()) return
    lockModePreferences.setCalculatorLockEnabled(enabled)
    _isCalculatorLockEnabled.value = enabled
}
```

`SettingsScreen` añade, después del interruptor de vibración y antes de "Cambiar PIN":

```
Desbloquear con calculadora            [Switch]
Al abrir la app aparecerá una calculadora. Escribe tu PIN y pulsa = para entrar.
Si olvidas el PIN no hay forma de recuperar el acceso.
```

- El aviso es texto normal, no un diálogo.
- El interruptor solo se ve dentro de la app, nunca en la pantalla de bloqueo.
- Cambiar el PIN sigue funcionando igual; el modo calculadora usa el PIN vigente.

### 8.7 Contenedor y navegación

- `AppContainer`: añadir `lockModePreferences` (lazy, sobre `EncryptedPrefsFactory`).
- `AppNavHost`:
  - `LockViewModel(container.pinManager, container.lockModePreferences)`.
  - `SettingsViewModel(..., lockModePreferences = container.lockModePreferences)`.
- No se añaden rutas ni destinos nuevos.
- `AppLockController` no cambia: la app sigue naciendo bloqueada y se re-bloquea en `ON_STOP`.

### 8.8 Errores y logging

- Un código incorrecto no genera log, no genera estado de error y no muestra mensaje.
- No se registra el PIN, el código tecleado ni el contenido del display.
- Los fallos de la calculadora (división entre cero) se muestran solo como `Error` en el display.
- No se añaden excepciones nuevas ni `catch` vacíos.

### 8.9 Seguridad y privacidad

- El PIN sigue almacenado como `SHA-256(sal ‖ PIN)` en `EncryptedSharedPreferences`; este cambio no toca
  `DefaultPinManager`.
- La comparación sigue siendo `MessageDigest.isEqual`.
- El estado de la calculadora vive solo en memoria y no se guarda en `rememberSaveable`.
- Los campos de PIN existentes (`UnlockContent`, `CreatePinContent`, Ajustes) siguen usando `rememberSaveable`
  con `PasswordVisualTransformation`; no se cambian en esta variante. El riesgo es menor porque nunca muestran
  los dígitos, pero conviene revisarlo en una tarea aparte.
- El modo calculadora **no** oculta la notificación de grabación, no cambia el servicio de accesibilidad y no
  afecta a `RecordingService`.
- Riesgo asumido: los intentos incorrectos son silenciosos y no hay límite de intentos; un atacante con acceso
  físico puede probar combinaciones. Es la misma deuda que ya existe con el PIN actual, agravada por la ausencia
  de feedback. Se documenta en `docs/seguridad.md`.
- Sin recuperación de PIN: perder el PIN implica perder el acceso a las grabaciones locales.

## 9. Tests

### 9.1 `CalculatorEngineTest` (nuevo, JVM)

- Los dígitos construyen la entrada y `=` devuelve el código tecleado.
- Los ceros iniciales se conservan (`0123`).
- Suma, resta, multiplicación y división con enteros y decimales.
- Ejecución inmediata encadenada: `2 + 3 × 4 =` → `20`.
- `=` repetido repite la última operación.
- División entre cero muestra `Error` y el siguiente dígito se recupera.
- `C` reinicia.
- `⌫` borra el último carácter y vuelve a `0` cuando corresponde.
- `±` y `%` cambian el valor y anulan el código de desbloqueo.
- Un resultado calculado que coincide con un PIN no devuelve `typedCode`.
- Un decimal no devuelve `typedCode`.
- Un PIN largo (por ejemplo, 20 dígitos) se puede teclear y `=` devuelve el código completo.

### 9.2 `LockViewModelTest` (ampliar)

- `isCalculatorLockEnabled` refleja la preferencia.
- `submitCalculatorCode` con el PIN correcto pasa a `Unlocked`.
- `submitCalculatorCode` con un código incorrecto deja `uiState` en `Idle` (sin `Error`).
- `submitCalculatorCode(null)` no hace nada.
- Los tests existentes se adaptan a una fábrica `newViewModel(...)` con `FakeLockModePreferences`.

### 9.3 `SettingsViewModelTest` (ampliar)

- `setCalculatorLockEnabled(true)` persiste y actualiza el `StateFlow`.
- `setCalculatorLockEnabled(false)` persiste y actualiza el `StateFlow`.
- No activa el modo si no hay PIN (si se mantiene la guarda).
- Los tests existentes se adaptan a la nueva dependencia con valor por defecto.

### 9.4 Fakes

- `FakeLockModePreferences` en `src/test/.../ui/lock/` (o `security/`), con estado en memoria.
- Se reutiliza en `LockViewModelTest` y `SettingsViewModelTest`.

### 9.5 No testeado en unitario

- `CalculatorLockScreen` (Compose), `LockScreen` y `SettingsScreen`: verificación manual.
- `DefaultLockModePreferences`: envoltorio fino.
- `AppNavHost`: integración manual.

## 10. Documentación a actualizar en la rama

- `docs/seguridad.md`:
  - Sustituir la frase que descarta el "modo calculadora" por la postura de esta variante: modo opcional,
    no oculta la grabación, sin recuperación, intentos silenciosos.
  - Añadir el modo a "Protección de acceso a la app" y a los límites conocidos.
- `docs/modelo-de-datos.md`: añadir `calculator_lock_enabled` a la tabla de preferencias.
- `docs/arquitectura.md`: añadir `CalculatorEngine`, `CalculatorLockScreen` y `LockModePreferences` a la
  estructura; actualizar el flujo de bloqueo.
- `docs/ui-movil.md`: describir la pantalla de calculadora y el interruptor de Ajustes.
- `docs/testing.md`: añadir `CalculatorEngineTest` y `FakeLockModePreferences`; añadir el modo al checklist manual.
- `README.md`: solo si la descripción general menciona el bloqueo; ajustar la frase si hace falta.

## 11. Decisiones del constructor

Estas son decisiones tomadas durante el diseño, no requisitos del usuario. Se pueden revisar:

1. **Ejecución inmediata**, no precedencia algebraica: es lo que hace una calculadora básica de teléfono.
2. **Separador decimal `.`**, para no depender del locale y porque el PIN solo usa dígitos.
3. **Ceros iniciales visibles**, para que un PIN como `0123` se pueda introducir y desbloquear.
4. **Estado de la calculadora con `remember`, no `rememberSaveable`**, y reinicio en `ON_STOP`: evita que el PIN
   tecleado quede en el estado guardado de Android o visible al volver del segundo plano.
5. **`%` = valor / 100**, no porcentaje contextual sobre el acumulador.
6. **Sin límite de dígitos** en la calculadora, para que cualquier PIN existente se pueda teclear aunque sea
   largo; el display recorta visualmente si no cabe.
7. **`=` repetido** repite la última operación.
8. **El modo se aplica en el siguiente bloqueo**, no cambia la pantalla actual mientras se está en Ajustes.
9. **Sin recuperación de PIN** y aviso explícito en Ajustes.
10. **Sin cambios en el manifiesto ni en el launcher**; la calculadora solo aparece tras abrir la app.

## 12. Criterios de aceptación

- [ ] El interruptor de Ajustes persiste y se lee al bloquear.
- [ ] Con el modo activo, al abrir o volver a la app aparece solo la calculadora, sin PIN ni biometría.
- [ ] La calculadora opera de verdad (`+`, `−`, `×`, `÷`, decimales, `C`, `⌫`, `±`, `%`, `=`).
- [ ] Un código incorrecto no muestra error ni pista; la calculadora sigue funcionando.
- [ ] El PIN correcto tecleado directamente y `=` desbloquea.
- [ ] Un resultado calculado que coincide con el PIN no desbloquea.
- [ ] Al desactivar el modo, el siguiente bloqueo vuelve a la pantalla actual.
- [ ] El primer arranque sigue pidiendo crear PIN.
- [ ] No cambian notificación, permisos, manifiesto, Room ni backups.
- [ ] `./gradlew assembleDebug testDebugUnitTest` en verde.
- [ ] Documentación actualizada en el mismo commit.

## 13. Checklist manual en dispositivo

- [ ] Activar el modo en Ajustes y comprobar el aviso de PIN olvidado.
- [ ] Pasar a segundo plano y volver: aparece la calculadora.
- [ ] Probar `2 + 2 =` → `4`; `7 ÷ 2 =` → `3.5`; `1 ÷ 0 =` → `Error`; `C`; `⌫`; `±`; `%`.
- [ ] Teclear un PIN incorrecto y `=`: no hay error, no se abre.
- [ ] Teclear el PIN correcto y `=`: se abre la lista de grabaciones.
- [ ] Girar la pantalla mientras se teclea: la calculadora se reinicia (decisión de seguridad).
- [ ] Pasar a segundo plano con dígitos tecleados y volver: la calculadora se reinicia.
- [ ] Desactivar el modo y comprobar que vuelve la pantalla de PIN/huella.
- [ ] Comprobar que la notificación de grabación sigue visible y que grabar con volumen funciona igual.
- [ ] Probar con un PIN que empiece por cero (p. ej. `0123`), si se puede configurar.
