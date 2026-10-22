# Estándares de UI (móvil / Compose)

Aplica a todo lo que ve el usuario. Este proyecto no tiene frontend web: estas son las normas
equivalentes para la capa de presentación Android.

## Principios

- **Pantalla sin lógica**: cada `XScreen` pinta estado y emite eventos; la lógica está en `XViewModel`
  o en clases puras.
- **Estado observable**: el `ViewModel` expone `StateFlow` de solo lectura (`asStateFlow()`); la pantalla lo
  consume con `collectAsState()`.
- **Una sola dirección**: estado hacia abajo, eventos hacia arriba (`onUnlocked`, `viewModel::setVibrationEnabled`).
- **Estado de entrada de usuario** (texto de campos) en la propia pantalla con `rememberSaveable`; no
  se guarda en el `ViewModel` salvo que otra lógica lo necesite. La calculadora del bloqueo es la excepción
  deliberada: usa `remember` para no guardar el PIN tecleado (ver «Bloqueo con calculadora»).
- **Material 3** y `MaterialTheme` (colores y tipografía del tema; color dinámico si el sistema lo permite,
  con esquemas claro/oscuro en `ui/theme/`). No hardcodear colores en pantallas.
- Pantallas y `ViewModel`s por funcionalidad: `ui/lock`, `ui/recordings`, `ui/settings`.

## Composición y navegación

- Un `NavHost` único en `AppNavHost` (`lock` → `recordings` ⇄ `settings`).
- Las `ViewModel` se crean ahí con `viewModelFactory { initializer { … } }` usando `AppContainer`.
- **Las rutas protegidas deben empezar con `if (isLocked) return@composable`** para no pintar datos privados
  si el estado se restaura tras morir el proceso.
- Cabecera común (botón "Ajustes"/"Volver", 56 dp) en `AppNavHost`; la pantalla ocupa el resto con
  `Modifier.weight(1f)`.

## Insets y pantalla completa (edge-to-edge)

`MainActivity` llama a `enableEdgeToEdge()`, así que **la app se dibuja bajo las barras del sistema**.
El `NavHost` aplica `Modifier.safeDrawingPadding()` una sola vez: cubre barra de estado, barra de
navegación, *notch* y teclado para todas las pantallas.

> Historial: sin ese padding, el botón "Ajustes" quedaba bajo la barra de estado y no se podía pulsar.
> No quitarlo ni duplicarlo en cada pantalla.

## Permisos en la UI

- Se piden con `ActivityResultContracts.RequestMultiplePermissions`: `RECORD_AUDIO` y, en Android 13+,
  `POST_NOTIFICATIONS`.
- Si el usuario deniega el micrófono de forma permanente, se le lleva a los ajustes de la app
  (`ACTION_APPLICATION_DETAILS_SETTINGS`). El servicio de accesibilidad se activa en
  `ACTION_ACCESSIBILITY_SETTINGS`.
- Antes de lanzar un flujo que cubre la actividad (permisos, login de Google) hay que llamar a
  `AppLockController.process.skipNextBackgroundRelock()`.
- Los avisos de configuración (`SetupWarning`) se recalculan con `refreshStatus()` al volver a la pantalla.

## Textos

- Los textos de interfaz están en **español** y, hoy, **escritos directamente en los `@Composable`**.
  *(pendiente: moverlos a `res/values/strings.xml` para poder traducirlos.)*
- `strings.xml` solo contiene: nombre de la app, texto y canal de la notificación y la descripción del
  servicio de accesibilidad (en inglés, por ser texto del sistema).
- Mensajes claros y accionables: qué falló y qué hacer ("No conectado a Google", "Error de configuración
  (código 10): falta registrar el paquete y la huella SHA-1 en Google Cloud.").

## Validación de formularios

Único formulario real: **PIN** (creación en el primer arranque, cambio en Ajustes).

Patrón (seguirlo para cualquier formulario nuevo):

1. **Reglas en una función pura**, sin Android: `PinRules.validate(pin, confirmation): PinValidation`
   (`OK`, `TOO_SHORT`, `NOT_DIGITS`, `MISMATCH`). Se testea en JVM (`PinRulesTest`).
2. **El `ViewModel` aplica la regla antes de actuar** y devuelve/expone el resultado
   (`SettingsViewModel.changePin` solo guarda si es `OK`; `LockViewModel.createPin` emite
   `PinSetupError(result)`). Nunca se guarda un valor no validado.
3. **La pantalla traduce el resultado a un mensaje** con `PinRules.errorMessage(result)` y lo muestra junto al campo.
4. **Filtrado en la entrada**: `onValueChange = { pin = it.filter(Char::isDigit) }`,
   `KeyboardType.NumberPassword`, `PasswordVisualTransformation`.
5. **Doble confirmación** para valores que no se pueden recuperar (PIN): evita bloquearse por un error de tecleo.
6. **El mensaje se limpia al editar** el campo.
7. **Operaciones sensibles solo cuando procede**: crear PIN desde la pantalla de bloqueo solo está permitido
   mientras no exista uno (no se puede sobrescribir desde ahí).

## Accesibilidad

- Estados comunicados con texto, no solo con color (p. ej. "Grabando ahora", "Conectado como …").
- Áreas táctiles de botones con el tamaño por defecto de Material 3; no reducirlas.
- Respetar el tamaño de fuente del sistema (usar `sp` de la tipografía del tema).
- *(pendiente: revisar `contentDescription` de iconos y el orden de foco en las pantallas.)*

## Estado visible de la sincronización

Ajustes muestra: estado de conexión ("Conectado como … / No conectado a Google"), error de inicio de sesión
si lo hay (en color de error), el último resultado de `SyncStatus` y, si hay cuenta y sync activa, el botón
**Sincronizar ahora**. Cada grabación en la lista muestra su `SyncState`.

## Bloqueo con calculadora

Ajustes incluye el interruptor **Desbloquear con calculadora**, situado después del interruptor de vibración
y antes de "Cambiar PIN". Solo se puede activar si ya existe un PIN y el valor se guarda en las preferencias
cifradas (`calculator_lock_enabled`), así que sobrevive a reinicios.

Debajo del interruptor se advierte de que, si se olvida el PIN, no hay forma de recuperar el acceso: la única
salida es borrar los datos de la app, con pérdida de las grabaciones locales. El modo se aplica en el siguiente
bloqueo, no en la pantalla que se está viendo.

Con el modo activo, `LockScreen` pinta `CalculatorLockScreen` en lugar del PIN/biometría: pantalla completa
con `padding(16.dp)` (objetivo en vertical), display alineado a la derecha (`displaySmall`, una línea, con
elipsis si el número no cabe) y botonera de 5 filas × 4 columnas con separación de 8 dp y teclas de 64 dp
de alto:

```
C   ±   %   ÷
7   8   9   ×
4   5   6   −
1   2   3   +
0   .   ⌫   =
```

- La calculadora es funcional: `+`, `−`, `×`, `÷` con ejecución inmediata (sin precedencia), decimales con
  `.`, `C`, `⌫`, `±`, `%` y `=`. Si no existe PIN, la creación de PIN tiene prioridad y la calculadora no
  se muestra.
- Para desbloquear se teclea el PIN como número directo (solo dígitos) y se pulsa `=`. Un resultado
  calculado nunca desbloquea, aunque coincida con el PIN.
- Un código incorrecto no produce ningún cambio visible: ni mensaje, ni color de error, ni vibración, ni
  log. La calculadora muestra su resultado y sigue como si nada.
- No hay tope de dígitos: un PIN largo se puede teclear completo; si no cabe en el display se recorta
  visualmente, pero la entrada sigue activa. El estado se guarda con `remember`, **no** con
  `rememberSaveable`, para no escribir el PIN tecleado en el estado de instancia de Android.
- Al girar la pantalla o al pasar la app a segundo plano (`ON_STOP`) la calculadora vuelve a `0`: una
  entrada a medias no queda visible al volver.
- En horizontal o ventanas bajas la botonera se compacta (menos padding, separación y tipografía del
  display, y teclas que bajan hasta un mínimo de 48 dp) para que el display y `=` sigan visibles; si aun
  así no cabe, el contenido se puede desplazar.
- En este modo no se compone el teclado de PIN ni el botón de huella/Face, y no aparece ningún texto con
  el nombre de la app, «PIN» ni «desbloquear».

## Identidad del launcher (variante)

En esta variante la app se instala como **Basic Calc** y usa un icono adaptativo de calculadora (fondo
oscuro, cuerpo claro, teclas de colores y versión monocroma para iconos temáticos). El nombre del paquete
no cambia: `com.example.auto_voice_recorder`. La notificación de grabación conserva el texto "Recording" y
usa un icono de micrófono dedicado, para que la grabación siga siendo inequívoca aunque el launcher parezca
una calculadora.

## Lista de comprobación para una pantalla nueva

- [ ] `XScreen` sin lógica + `XViewModel` con `StateFlow` y test JVM.
- [ ] Ruta en `AppNavHost` protegida con `isLocked`.
- [ ] No añade su propio padding de insets (ya lo hace el `NavHost`).
- [ ] Textos en español, claros y accionables.
- [ ] Probada en teléfono con barras de sistema visibles y con el teclado abierto.
