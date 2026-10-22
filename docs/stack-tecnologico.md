# Stack tecnológico

Aplicación **móvil Android nativa**, de un único módulo (`:app`). No hay backend propio ni API
propia: lo único que sale del teléfono son las subidas opcionales a la API de Google Drive.

> Las versiones salen de `Project/gradle/libs.versions.toml` y de los `build.gradle.kts`. Si cambian,
> ese fichero manda sobre este documento.

## Plataforma y herramientas de build

| Elemento | Valor |
|---|---|
| Lenguaje | Kotlin 2.2.10 (estilo `official`) |
| UI | Jetpack Compose + Material 3 (Compose BOM 2026.02.01) |
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.4.1 (wrapper) |
| JDK | 21 (toolchain; `gradle-daemon-jvm.properties`, descarga automática con foojay) |
| Compatibilidad Java del código | 11 (`sourceCompatibility`/`targetCompatibility`) |
| `minSdk` / `targetSdk` / `compileSdk` | 29 (Android 10) / 36 / 36.1 |
| `applicationId` / namespace | `com.example.auto_voice_recorder` |
| Procesador de anotaciones | KSP 2.3.12 (Room) |
| Catálogo de dependencias | `libs.versions.toml` (sin versiones sueltas en los `build.gradle.kts`) |

`minSdk = 29` es una decisión deliberada: las reglas de *foreground service* de micrófono son más
uniformes desde Android 10.

## Librerías

| Área | Librería | Versión | Uso |
|---|---|---|---|
| UI | `androidx.compose.*` (BOM), `material3` | 2026.02.01 | Pantallas |
| Navegación | `navigation-compose` | 2.8.4 | Grafo `lock → recordings ⇄ settings` |
| Arquitectura | `lifecycle-viewmodel-compose`, `lifecycle-runtime-ktx` | 2.8.7 / 2.6.1 | `ViewModel` + `StateFlow` |
| Asincronía | `kotlinx-coroutines-android` | 1.8.1 | Corrutinas y `Flow` |
| Persistencia | `room-runtime`, `room-ktx` (+ `room-compiler` vía KSP) | 2.7.2 | Base de datos `recordings.db` |
| Segundo plano | `work-runtime-ktx` (WorkManager) | 2.9.1 | `DriveSyncWorker` |
| Seguridad | `security-crypto` | 1.1.0 | `EncryptedSharedPreferences` |
| Seguridad | `biometric` | 1.1.0 | Desbloqueo biométrico |
| Google | `play-services-auth` | 21.3.0 | Google Sign-In (API clásica) |
| Google | `google-api-client-android` | 2.7.2 | `GoogleAccountCredential` |
| Google | `google-api-services-drive` | v3-rev20261011-2.0.0 | Cliente de la API de Drive |
| Núcleo | `core-ktx`, `activity-compose` | 1.10.1 / 1.8.0 | Utilidades de plataforma |

APIs de plataforma usadas directamente: `MediaRecorder` (grabación AAC en contenedor MPEG-4),
`MediaPlayer` (reproducción), `AccessibilityService` (teclas de volumen), `Service` en primer plano
de tipo `microphone`, `Vibrator`/`VibrationEffect`, `AccessibilityManager`.

## Testing

JUnit 4.13.2 + `kotlinx-coroutines-test`. Para tests instrumentados: `androidx.test.ext:junit`,
Espresso y `ui-test-junit4` (declarados; hoy solo existe el test de ejemplo). **No** se usan
librerías de mocks: se usan *fakes* escritos a mano. Detalle en [testing.md](testing.md).

## Lo que NO hay (a propósito)

- **Sin framework de inyección de dependencias** (Hilt/Koin): un contenedor manual, `AppContainer`.
  Ver [arquitectura.md](arquitectura.md).
- **Sin backend ni API REST propia**, sin Retrofit/OkHttp: Drive se usa a través del cliente oficial.
- **Sin librería de logging** (Timber…): se usa `android.util.Log`. Ver [errores-y-logging.md](errores-y-logging.md).
- **Sin analítica ni telemetría** de ningún tipo.
- **Sin linters configurados** (ktlint/detekt) ni cobertura mínima exigida. Si se añaden, documentarlo
  aquí y en [convenciones.md](convenciones.md).
- **Sin build de release configurado**: `isMinifyEnabled = false`, sin `signingConfig` de release.

## Backend / frontend / móvil

Este proyecto es solo **móvil**. Las guías de "frontend" del equipo se aplican como estándares de UI
Compose ([ui-movil.md](ui-movil.md)) y no existe capa de backend que documentar.
