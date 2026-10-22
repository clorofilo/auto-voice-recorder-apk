# Convenciones y patrones

Lo que sigue describe cómo **está** escrito el proyecto, para que el código nuevo sea indistinguible
del existente. Donde se marca *(pendiente)* hay una deuda conocida, no una norma.

## Idioma

| Qué | Idioma |
|---|---|
| Código, identificadores, KDoc y comentarios | Inglés |
| Mensajes de commit | Inglés |
| Textos visibles para el usuario (UI) | Español |
| Documentación (`README.md`, `docs/`) | Español |

## Reglas de nombres

| Elemento | Regla | Ejemplos reales |
|---|---|---|
| Paquetes | minúsculas, un paquete por capa | `input`, `service`, `data`, `sync`, `security`, `ui.lock` |
| Clases / interfaces | `PascalCase`, un fichero por clase principal con su mismo nombre | `RecordingsRepository`, `PinManager` |
| Implementación real de una interfaz | prefijo `Default` | `PinManager` → `DefaultPinManager`; `SyncPreferences` → `DefaultSyncPreferences`; `SystemStatusChecker` → `DefaultSystemStatusChecker` |
| Implementación con tecnología concreta | nombre de la tecnología | `AudioRecorderController` → `MediaRecorderController`; `DriveUploader` → `GoogleDriveUploader`; `GestureScheduler` → `HandlerGestureScheduler` |
| Doble de test | prefijo `Fake` (en `src/test`) | `FakeRecordingDao`, `FakeDriveUploader` |
| Pantallas Compose | `<Feature>Screen` | `LockScreen`, `SettingsScreen` |
| `ViewModel` | `<Feature>ViewModel` | `RecordingsViewModel` |
| Modelos de UI | `<Algo>UiModel` | `RecordingUiModel` |
| Estados de UI / avisos | `sealed interface`/`enum` con valores `PascalCase` | `LockUiState.Unlocked`, `SetupWarning.MissingMicPermission`, `PinValidation.TOO_SHORT` (enum: `SCREAMING_SNAKE`) |
| Funciones y propiedades | `camelCase`; booleanos con `is`/`has` | `isRecording`, `hasRecordPermission` |
| Constantes | `SCREAMING_SNAKE_CASE` dentro de `private companion object` o `private const val` | `KEY_HASH`, `SALT_BYTES`, `TAG` |
| Claves de preferencias | `snake_case` | `drive_sync_enabled` |
| Acciones de `Intent` | `com.example.auto_voice_recorder.action.<NOMBRE>` | `ACTION_START_RECORDING` |
| Recursos Android | `snake_case` | `notification_channel_name` |
| Tests | frase entre acentos graves, en inglés, describe el comportamiento | `` `stop notifies onRecordingSaved with the stored sync state…` `` |

Estilo de formato: el de Kotlin (`kotlin.code.style=official`), 4 espacios, comas finales en llamadas
multilínea como hace el código existente.

## Patrones de diseño usados

| Patrón | Dónde | Para qué |
|---|---|---|
| **Interfaz + implementación por defecto** (puertos y adaptadores) | `PinManager`, `SyncPreferences`, `DriveUploader`, `AudioRecorderController`, `SystemStatusChecker`, `GestureScheduler` | Aislar Android/red de la lógica y poder testear con *fakes* |
| **Inyección por constructor, incluso de funciones** | `syncEnabled: () -> Boolean`, `scheduleDriveSync: () -> Unit`, `outputFileFor: () -> File`, `clock: () -> Long` | Evitar dependencias pesadas para una sola operación |
| **Contenedor de dependencias manual** | `AppContainer` | Compartir singletons sin framework de DI |
| **Repositorio** | `RecordingsRepository` | Única puerta a Room |
| **Caso de uso** | `DriveSyncUseCase` | Una operación de negocio, testeable sin WorkManager |
| **Orquestador** | `RecordingOrchestrator` | Coordinar grabador + repositorio + estado |
| **Observador con `StateFlow`/`Flow`** | `RecordingStatus`, `isLocked`, `observeAll()` | UI reactiva |
| **Singleton de proceso (`object`)** | `RecordingStatus`, `SyncStatus`, `AppLockController.process` | Estado compartido entre servicio, worker y UI. Mutador `internal` o con método explícito |
| **Máquina de estados con tipos** | `SyncState`, `LockUiState`, `SetupWarning`, `PinValidation` | Estados explícitos en lugar de banderas sueltas |
| **Envoltorio fino** | `MediaRecorderController`, `GoogleSignInHelper`, `DriveSyncScheduler` | Sin lógica; se documentan como "no testeado por…" |

**Patrones de diseño de API.** La app no expone ni consume una API HTTP propia. Los contratos internos
siguen estas reglas: las operaciones que pueden fallar por causas externas devuelven `Result<…>`
(`DriveUploader.upload(): Result<Unit>`) en vez de lanzar; las interfaces son pequeñas y de una sola
responsabilidad; `RecordingService` solo se controla con dos `Intent` con acción
(`ACTION_START_RECORDING`/`ACTION_STOP_RECORDING`) y no está exportado. El acceso a Drive se hace
exclusivamente a través de `DriveUploader`.

## Clean code y SOLID (cómo se aplica aquí)

- **Responsabilidad única**: `VolumeKeyAccessibilityService` solo traduce teclas; `RecordingService` solo
  gestiona el servicio y la notificación; `RecordingOrchestrator` solo coordina; `DriveSyncWorker` solo
  sabe leer `PENDING` y subir.
- **Abierto/cerrado e inversión de dependencias**: lo de alto nivel (`DriveSyncUseCase`) depende de
  `DriveUploader`, no de Google; cambiar de destino es añadir una implementación.
- **Sustitución de Liskov**: los `Fake*` cumplen el mismo contrato que las implementaciones reales.
- **Segregación de interfaces**: `SyncPreferences` y `VibrationPreferences` son dos interfaces mínimas, no un
  "Settings" gigante.
- **Funciones cortas y con nombre que explica la intención**; sin números mágicos (constantes con nombre).
- **Sin estado global mutable accesible**: los singletons exponen `StateFlow` de solo lectura.
- **Los fallos son parte del diseño**: se tratan en el sitio donde se pueden resolver
  ([errores-y-logging.md](errores-y-logging.md)).
- Evitar abstracciones especulativas: se añade una interfaz cuando hay un segundo uso real (tests incluidos).

## Documentar el código

- **KDoc en clases públicas y en cualquier cosa no obvia**, y explica el **porqué** (restricción de
  plataforma, decisión, riesgo), no el qué. Ejemplo: `AppDatabase.getInstance` explica por qué debe ser una
  única instancia.
- **Comentarios en línea** solo para invariantes o trampas: orden de llamadas obligatorio
  (`startForeground` síncrono), motivo de un `skipNextBackgroundRelock()`, por qué se vibra antes de abrir el micro.
- Las clases finas sin test llevan la nota **"Not unit tested: <motivo>"** en su KDoc.
- Referencias a código con `[NombreDeClase]`.
- No comentar lo que el código ya dice, ni dejar código comentado, ni `TODO` sin contexto.
- Si cambia un comportamiento descrito en `docs/`, **actualizar el documento en el mismo commit**.
- Las decisiones de diseño grandes van a `docs/superpowers/specs/` (con fecha); los planes, a `plans/`.

## Validación de formularios y errores

Reglas de validación como funciones puras, mensajes en la UI y errores de usuario: ver
[ui-movil.md](ui-movil.md#validación-de-formularios). Errores técnicos y logs:
[errores-y-logging.md](errores-y-logging.md). Seguridad: [seguridad.md](seguridad.md).

## Git workflow

- Rama de trabajo habitual: **`main`**. Se han usado *worktrees* temporales (`.claude/worktrees/…`) para
  trabajo aislado; al terminar se integran con *fast-forward* y se eliminan.
- **Conventional Commits en inglés**: `<tipo>: <descripción en imperativo, minúsculas, sin punto>`.
  Tipos en uso: `feat`, `fix`, `docs`, `build`; también válidos `refactor`, `test`, `chore`.
- **Un tema por commit**; cada commit debería compilar y pasar los tests. Cuando un cambio toca varios
  temas, separar por tema (se puede preparar con `git add -p` o preparando versiones intermedias).
- Cuerpo del commit: explica el **porqué** y los efectos visibles, en frases cortas o viñetas.
- Ejemplos reales:
  - `feat: start recording after holding volume-down for 2 seconds`
  - `fix: keep every screen out of the system bars`
  - `fix: exclude encrypted prefs and recordings from Auto Backup`
- Si un agente de IA participa, se añade al final del mensaje la línea `Co-Authored-By:` indicada por la herramienta.
- **No se versionan**: `local.properties`, `build/`, `.gradle/`, claves/keystores, `graft/` (caché), ni
  secretos. Los ficheros de IDE compartidos (`.idea/`) sí están en el repo.
- **No** reescribir historial publicado ni forzar *push*. No hacer *push* ni commits sin que el responsable
  lo pida expresamente cuando se trabaja con agentes.
- Antes de commitear: `./gradlew assembleDebug testDebugUnitTest` en verde.

## Restricciones de producto (no negociables)

- Siempre notificación visible mientras se graba; **no** hay modo oculto de grabación. La variante
  `feat/calculator-lock-mode` puede mostrar el **bloqueo** como una calculadora y el **launcher** como
  Basic Calc con icono de calculadora; la grabación y su notificación siguen visibles.
- El servicio de accesibilidad solo lee teclas de volumen (`canRetrieveWindowContent=false`).
- No interceptar el botón de power (imposible) ni bajar de `minSdk 29`.
- No cambiar el esquema de Room sin migración ([modelo-de-datos.md](modelo-de-datos.md#cambiar-el-esquema)).
- No ampliar el alcance de Drive más allá de `drive.file`.
