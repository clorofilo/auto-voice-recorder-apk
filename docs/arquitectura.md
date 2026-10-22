# Arquitectura y estructura de ficheros

Un único módulo Android (`Project/app`), paquete raíz `com.example.auto_voice_recorder`.
Arquitectura por capas con dependencias hacia abajo, lógica pura aislada de Android para poder
testearla en JVM, e inyección por constructor con un contenedor manual.

> El diseño original está en [superpowers/specs/2026-10-21-voice-recorder-design.md](superpowers/specs/2026-10-21-voice-recorder-design.md).
> Este documento refleja el estado **actual** del código; donde difieren, manda este.

## Visión general

```
VolumeKeyAccessibilityService ──(Intent START/STOP)──▶ RecordingService (foreground, type=microphone)
   │ usa                                                   │ delega en
   ▼                                                       ▼
VolumeKeyCommandHandler ─▶ VolumeGestureDetector      RecordingOrchestrator ─▶ AudioRecorderController
   (puro, testeable)         (puro, 2 s)                 │                       (MediaRecorderController)
                                                         ▼
                                                  RecordingsRepository ─▶ RecordingDao ─▶ Room (recordings.db)
                                                         ▲                    │
              ┌──────────────────────────────────────────┘                    │ Flow
              │                                                               ▼
   DriveSyncWorker (WorkManager) ─▶ DriveSyncUseCase ─▶ DriveUploader      ViewModels ─▶ Compose UI
                                                         (GoogleDriveUploader)
```

## Capas y responsabilidades

| Paquete | Responsabilidad | Depende de |
|---|---|---|
| `input/` | Convertir pulsaciones de volumen en órdenes de iniciar/parar. Sin tipos de Android salvo `HandlerGestureScheduler`. | — |
| `service/` | Ciclo de vida de la grabación: servicio en primer plano, notificación, orquestación, estado global, vibración. | `audio`, `data`, `sync`, `security` |
| `audio/` | Abstracción del grabador (`AudioRecorderController`) y su implementación real con `MediaRecorder`. | — |
| `data/` | Entidad Room, DAO, base de datos y `RecordingsRepository`. Única vía de acceso a la BD. | — |
| `sync/` | Subida a Drive: caso de uso, worker, planificador, login de Google, preferencias de sincronización. | `data`, `security` |
| `security/` | PIN (hash con sal), reglas del PIN, biometría, fábrica de preferencias cifradas. | — |
| `ui/` | Pantallas Compose, `ViewModel`s, navegación, tema. No conoce `MediaRecorder` ni Drive (salvo el flujo de login). | `data`, `service` (solo `RecordingStatus`), `sync`, `security` |
| raíz | `MainActivity` (edge-to-edge) y `AppContainer` (dependencias compartidas). | todo |

Reglas de dependencia que no hay que romper:

- La UI y el servicio **nunca tocan Room directamente**: siempre `RecordingsRepository`.
- `RecordingOrchestrator`, `DriveSyncUseCase`, `VolumeKeyCommandHandler`, `VolumeGestureDetector`,
  `PinRules` y los `ViewModel` **no importan Android** (o casi): reciben lo que necesitan por
  constructor (interfaces o lambdas) para poder testearse en JVM con *fakes*.
- Las clases que sí envuelven Android o red (`MediaRecorderController`, `GoogleDriveUploader`,
  `GoogleSignInHelper`, `DriveSyncScheduler`, las pantallas) son **finas** y no llevan lógica de negocio.

## Estructura de ficheros

```
Project/app/src/main/java/com/example/auto_voice_recorder/
├── MainActivity.kt              # FragmentActivity (necesario para BiometricPrompt), enableEdgeToEdge()
├── AppContainer.kt              # Contenedor manual: repositorio, PinManager, preferencias, checker
├── input/
│   ├── VolumeGestureDetector.kt     # "mantener X ms" → callback; holdDurationMs = 2000
│   ├── GestureScheduler.kt          # interfaz + Cancellable (abstrae el temporizador)
│   ├── HandlerGestureScheduler.kt   # implementación con Handler del hilo principal
│   └── VolumeKeyCommandHandler.kt   # reglas: permiso, no re-entrada, ignora auto-repeat
├── service/
│   ├── VolumeKeyAccessibilityService.kt  # AccessibilityService; onKeyEvent siempre devuelve false
│   ├── RecordingService.kt               # foreground service + notificación + vibración
│   ├── RecordingOrchestrator.kt          # start()/stop(): grabar, persistir, avisar
│   ├── RecordingStatus.kt                # object: StateFlow<Boolean> isRecording
│   └── VibrationPreferences.kt           # interfaz + implementación sobre SharedPreferences
├── audio/
│   ├── AudioRecorderController.kt   # interfaz: start(File), stop(): Long, release()
│   └── MediaRecorderController.kt   # MediaRecorder MIC → MPEG_4/AAC
├── data/
│   ├── Recording.kt  SyncState.kt  RecordingDao.kt  AppDatabase.kt  RecordingsRepository.kt
├── sync/
│   ├── DriveUploader.kt             # interfaz: upload(File, Recording): Result<Unit>
│   ├── GoogleDriveUploader.kt       # Drive v3, carpeta "voice-recorder", scope drive.file
│   ├── GoogleSignInHelper.kt        # Google Sign-In + traducción de errores
│   ├── DriveSyncUseCase.kt          # sube los PENDING y los marca SYNCED
│   ├── DriveSyncWorker.kt           # CoroutineWorker
│   ├── DriveSyncScheduler.kt        # encola trabajo único con red + backoff exponencial
│   ├── SyncPreferences.kt  DefaultSyncPreferences.kt
│   └── SyncStatus.kt                # object: último resultado, visible en Ajustes
├── security/
│   ├── PinManager.kt  DefaultPinManager.kt  PinRules.kt
│   ├── LockModePreferences.kt      # interfaz + DefaultLockModePreferences (modo calculadora)
│   ├── EncryptedPrefsFactory.kt     # EncryptedSharedPreferences "app_lock_prefs"
│   └── BiometricAuthHelper.kt
└── ui/
    ├── navigation/AppNavHost.kt     # grafo, bloqueo y safeDrawingPadding
    ├── lock/       LockScreen  LockViewModel  AppLockController
    │               CalculatorEngine  CalculatorLockScreen
    ├── recordings/ RecordingsListScreen  RecordingsViewModel  RecordingUiModel
    │               AudioPlayerController  SystemStatusChecker
    ├── settings/   SettingsScreen  SettingsViewModel
    └── theme/      Color  Theme  Type        # Material 3, color dinámico si está disponible

Project/app/src/test/...          # tests JVM, misma estructura de paquetes + Fake*
Project/app/src/androidTest/...   # solo el test de ejemplo
Project/app/src/main/res/xml/     # accessibility_service_config, backup_rules, data_extraction_rules
```

## Flujos principales

### Grabar
1. `VolumeKeyAccessibilityService` recibe `KEYCODE_VOLUME_DOWN` y llama a `VolumeKeyCommandHandler`.
2. El handler arma `VolumeGestureDetector` solo con la primera pulsación (ignora el auto-repeat). Si la
   tecla se mantiene 2 s, y hay permiso de micrófono y no se está grabando, envía
   `ACTION_START_RECORDING` a `RecordingService`.
3. `RecordingService` entra en primer plano **de forma síncrona** (obligatorio) y lanza el trabajo en
   un `CoroutineScope` de paralelismo 1. Si hay vibración activada, vibra y espera 200 ms **antes** de
   abrir el micrófono.
4. `RecordingOrchestrator.start()` crea `filesDir/recordings/recording_<epochMs>.m4a`, arranca el
   `AudioRecorderController` y pone `RecordingStatus.isRecording = true`.
5. `KEYCODE_VOLUME_UP` (solo si se está grabando) envía `ACTION_STOP_RECORDING`.
6. `RecordingOrchestrator.stop()` cierra el grabador, calcula la duración, guarda la fila con estado
   `PENDING` (sync activada) o `DISABLED` y, si es `PENDING`, avisa por el callback `onRecordingSaved`
   (el servicio encola la sincronización). Después `RecordingService` vibra (si procede) y se detiene.

### Sincronizar con Drive
1. Tras guardar una grabación `PENDING`, o al pulsar **Sincronizar ahora** / activar el interruptor en Ajustes,
   `DriveSyncScheduler` encola un trabajo único (`drive_sync`) con restricción de red.
2. `DriveSyncWorker` obtiene la cuenta (`GoogleSignInHelper.lastAccount`), crea `GoogleDriveUploader` y
   ejecuta `DriveSyncUseCase.syncPending()`.
3. Cada subida correcta pasa la fila a `SYNCED`; cualquier fallo → `Result.retry()` (backoff exponencial).
   Sin cuenta o con la sincronización desactivada el worker termina en éxito sin hacer nada.
4. El resultado se publica en `SyncStatus` y se muestra en Ajustes.

Detalle en [sincronizacion-drive.md](sincronizacion-drive.md).

### Bloqueo de la app
`AppLockController.process` **nace bloqueado** en cada proceso. `AppNavHost` observa el ciclo de vida:
en `ON_STOP` (salvo cambio de configuración) vuelve a bloquear y navega a `lock` limpiando la pila. Las
rutas protegidas (`recordings`, `settings`) no pintan nada mientras `isLocked` sea `true`. Los flujos que
cubren brevemente la actividad (login de Google, diálogo de permisos) llaman antes a
`skipNextBackgroundRelock()` para no perder su resultado.

`LockScreen` elige la cara del bloqueo: si no hay PIN, la creación de PIN (prioridad absoluta); si el
modo calculadora está activo (`LockModePreferences`), `CalculatorLockScreen`; si no, el PIN/biometría de
siempre. La calculadora mantiene su estado en memoria (nunca en `rememberSaveable`) y lo reinicia en
`ON_STOP`; al pulsar `=`, `CalculatorEngine` devuelve `typedCode` solo si el número se tecleó directamente
y `LockViewModel.submitCalculatorCode` lo verifica con `PinManager`. Un código incorrecto no emite error.

## Estado compartido (singletons de proceso)

| Objeto | Qué guarda | Quién escribe | Quién lee |
|---|---|---|---|
| `RecordingStatus` | `isRecording` | `RecordingOrchestrator` | UI, `VolumeKeyCommandHandler` |
| `AppLockController.process` | `isLocked` | `AppNavHost` (bloquea en `ON_STOP`; desbloquea tras `LockScreen`) | `AppNavHost` |
| `SyncStatus` | último mensaje de sincronización | `DriveSyncWorker`, `GoogleDriveUploader` | `SettingsScreen` |
| `AppDatabase.getInstance()` | una sola instancia de Room | — | servicio, worker, UI |

`AppDatabase` **debe** ser una única instancia por proceso: Room solo notifica a los `Flow` las
escrituras hechas con la misma instancia; con varias, la lista de grabaciones se queda obsoleta.

## Hilos y concurrencia

- `RecordingService` serializa las llamadas al orquestador en `Dispatchers.IO.limitedParallelism(1)`:
  `RecordingOrchestrator` no tiene sincronización interna y `MediaRecorder` no es *thread-safe*.
- `startForeground` se llama en el hilo principal, justo tras `onStartCommand`, incluso al parar
  (Android exige `startForeground` tras `startForegroundService`).
- `HandlerGestureScheduler` usa el `Looper` principal.
- `RecordingsRepository.delete` borra el fichero en `Dispatchers.IO`.
- Las pantallas observan `StateFlow` con `collectAsState()`.

## Inyección de dependencias

`AppContainer` (creado perezosamente en `MainActivity`) construye y comparte `recordingsRepository`,
`pinManager`, `syncPreferences`, `vibrationPreferences`, `lockModePreferences`, `systemStatusChecker` y
expone `scheduleDriveSync()`. `AppNavHost` fabrica los `ViewModel` con
`viewModelFactory { initializer { … } }`.
`RecordingService` y `DriveSyncWorker` no tienen acceso al contenedor (los crea el sistema), así que
construyen sus dependencias a partir del `applicationContext`, **reutilizando los mismos ficheros de
preferencias cifradas y la misma instancia de Room**.

## Navegación

`lock` (inicio) → `recordings` ⇄ `settings`. La cabecera de "Ajustes"/"Volver" vive en `AppNavHost`; las
pantallas reciben el resto del espacio con `Modifier.weight(1f)`.

## Cómo añadir cosas

- **Nueva preferencia booleana**: interfaz + `Default…Preferences(prefs)` sobre `EncryptedPrefsFactory`,
  propiedad `lazy` en `AppContainer`, parámetro en el `ViewModel`, `Fake…` en tests. Plantilla: `VibrationPreferences`.
- **Nueva columna/entidad**: ver [modelo-de-datos.md](modelo-de-datos.md#cambiar-el-esquema).
- **Nueva pantalla**: `XScreen` + `XViewModel` + ruta en `AppNavHost`, protegida con `if (isLocked) return@composable`.
- **Nuevo permiso**: manifiesto + [seguridad.md](seguridad.md); si es de ejecución, solicitarlo en la UI.
