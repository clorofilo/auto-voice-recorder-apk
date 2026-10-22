# Contexto del proyecto

App Android nativa de grabación de voz personal (no vigilancia): mantener **volumen abajo 2 s** inicia la
grabación (también con pantalla bloqueada), **volumen arriba** la detiene. Copia opcional a Google Drive.
Solo móvil: **no hay backend ni API propia**.

Stack: Kotlin 2.2 + Jetpack Compose (Material 3) · Room 2.7 · WorkManager · AGP 9.2 / Gradle 9.4.1 · JDK 21
`minSdk 29`, `target/compileSdk 36`, paquete `com.example.auto_voice_recorder`, un módulo (`Project/app`).
Sin DI framework (contenedor manual `AppContainer`), sin librería de mocks, sin linters configurados.

Tests: JUnit 4 + kotlinx-coroutines-test, **fakes a mano**, lógica en JVM. Sin umbral de cobertura: toda
lógica nueva lleva test JVM.

# Comandos (desde `Project/`)

- Compilar: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Tests: `./gradlew testDebugUnitTest`  (antes de commitear: `./gradlew assembleDebug testDebugUnitTest` en verde)
- SHA-1 de depuración: `./gradlew signingReport`
- En Windows desde Git Bash: `./gradlew.bat …`

# Convenciones

- Commits: **Conventional Commits en inglés** (`feat:`, `fix:`, `docs:`, `build:`), un tema por commit.
- Código, KDoc y comentarios en **inglés**; textos de UI y documentación en **español**.
- Interfaz + `Default…` para la implementación real; `Fake…` en tests; `…Screen`, `…ViewModel`, `…UiModel`.
- Dependencias por constructor (interfaces o lambdas); clases finas sin lógica para Android/Google.
- Tests: nombre en frase entre acentos graves, `newXxx()` como fábrica, resetear singletons en `@Before`.
- Comentarios: explican el **porqué**; KDoc en lo no obvio; "Not unit tested: <motivo>" en envoltorios finos.
- Errores: `Result` en lugar de excepciones para fallos externos; nada de `catch` vacío; logs con `Log.e(TAG, msg, e)`,
  **sin PIN, tokens, correos, audio ni rutas completas**.
- Validación: reglas en funciones puras (`PinRules`), el `ViewModel` valida antes de guardar, la UI muestra el mensaje.
- Si cambias comportamiento descrito en `docs/`, actualiza el documento en el mismo commit.

# Restricciones (no negociables)

- **No modificar el esquema de Room sin migración** (`AppDatabase` está en `version = 1`, sin migraciones).
- **Siempre notificación visible al grabar**; nada de modo oculto ni disfraz de la app (riesgo legal/stalkerware).
- El servicio de accesibilidad solo lee teclas de volumen; no interceptar el botón de power (imposible).
- Alcance de Drive: solo `drive.file`. No añadir red ni SDK de analítica. No tocar `exported`.
- **No quitar** `safeDrawingPadding()` del `NavHost`, el permiso `INTERNET`, ni las exclusiones de backup
  (`backup_rules.xml` y `data_extraction_rules.xml`, mantenerlos sincronizados).
- Rutas protegidas de `AppNavHost` empiezan con `if (isLocked) return@composable`.
- No commitear `local.properties`, keystores, `build/`, `graft/` ni secretos. No hacer push ni force-push sin que se pida.
- Un solo `AppDatabase.getInstance()` por proceso (si no, la UI no se refresca).

# Dónde mirar

- Arquitectura y estructura de ficheros: `docs/arquitectura.md`
- Modelo de datos (Room, preferencias, ficheros): `docs/modelo-de-datos.md`
- Stack y versiones: `docs/stack-tecnologico.md` · Entorno y problemas habituales: `docs/entorno-desarrollo.md`
- Convenciones, patrones, git: `docs/convenciones.md` · Tests: `docs/testing.md`
- UI y validación de formularios: `docs/ui-movil.md` · Errores y logging: `docs/errores-y-logging.md`
- Seguridad y privacidad: `docs/seguridad.md` · Google Drive y Google Cloud: `docs/sincronizacion-drive.md`
- Diseño original (parcialmente superado) y plan: `docs/superpowers/`
- Visión general para humanos: `README.md`
