# Testing

## Filosofía

1. **La lógica va en clases testeables en JVM** (sin dispositivo): reglas de gestos, orquestación,
   repositorio, caso de uso de sincronización, PIN, bloqueo y `ViewModel`s.
2. **Lo que depende de hardware o de servicios de Google** (`MediaRecorder`, Play Services, Drive, Compose,
   `AccessibilityService`) se mantiene en envoltorios finos, sin lógica, **no testeados en unitario**
   y verificados a mano en un teléfono.
3. **Fakes escritos a mano, no mocks.** No hay Mockito/MockK en el proyecto.

## Herramientas

JUnit 4.13.2 · `kotlinx-coroutines-test` 1.8.1 (`runTest`, `UnconfinedTestDispatcher`,
`Dispatchers.setMain/resetMain`). Tests instrumentados: AndroidX Test + Espresso + `ui-test-junit4`
(declarados; hoy solo hay `ExampleInstrumentedTest`).

## Estructura

```
Project/app/src/test/java/com/example/auto_voice_recorder/    # JVM, espeja los paquetes de main
├── data/       RecordingsRepositoryTest   FakeRecordingDao
├── input/      VolumeGestureDetectorTest  VolumeKeyCommandHandlerTest
├── security/   PinRulesTest  DefaultPinManagerTest  FakeSharedPreferences
├── service/    RecordingOrchestratorTest  FakeAudioRecorderController
├── sync/       DriveSyncUseCaseTest       FakeDriveUploader
└── ui/
    ├── lock/       AppLockControllerTest  LockViewModelTest  FakePinManager
    ├── recordings/ RecordingsViewModelTest  FakeSystemStatusChecker
    └── settings/   SettingsViewModelTest  FakeSyncPreferences  FakeVibrationPreferences
```

- Un fichero `<Clase>Test` por clase bajo test, en el **mismo paquete**.
- Los `Fake*` viven junto a los tests que los usan y se reutilizan entre paquetes cuando hace falta.
- Un `Fake` implementa la **interfaz** real y guarda estado en memoria (p. ej. `FakeSyncPreferences(enabled = false)`).

## Convenciones de los tests

- Nombre: frase en inglés entre acentos graves que describe comportamiento y resultado:
  `` `setDriveSyncEnabled(false) neither schedules a sync nor touches recordings` ``.
- Estructura **Arrange / Act / Assert** con una línea en blanco entre bloques; una idea por test.
- Una función `newXxx(...)` por clase de test que construye el sujeto con parámetros por defecto
  sobrescribibles (`newViewModel(syncPreferences = FakeSyncPreferences(enabled = true))`).
- `@Before` reinicia el estado y **resetea los singletons de proceso** (`RecordingStatus.setRecording(false)`).
- Corrutinas: `Dispatchers.setMain(UnconfinedTestDispatcher())` en `@Before`, `resetMain()` en `@After`, y
  `runTest(testDispatcher)` en los tests suspendidos.
- Sin red, sin disco real (el borrado de ficheros es inyectable) y sin reloj real (`clock` inyectable).
- Aserciones de JUnit (`assertEquals`, `assertTrue`, `assertFalse`).

Ejemplo (extracto real, `SettingsViewModelTest`):

```kotlin
@Test
fun `setDriveSyncEnabled(true) persists through syncPreferences and updates isDriveSyncEnabled`() {
    val syncPreferences = FakeSyncPreferences(enabled = false)
    val viewModel = newViewModel(syncPreferences = syncPreferences)

    viewModel.setDriveSyncEnabled(true)

    assertTrue(syncPreferences.isEnabled())
    assertTrue(viewModel.isDriveSyncEnabled.value)
}
```

## Qué se cubre y qué no

| Cubierto en JVM | No cubierto en unitario (verificar a mano) |
|---|---|
| Repositorio y transiciones de `SyncState` | `MediaRecorderController`, `AudioPlayerController` |
| Detector de gesto y gating de teclas | `VolumeKeyAccessibilityService`, `RecordingService` |
| Orquestador (permiso, fallos, persistencia) | `GoogleSignInHelper`, `GoogleDriveUploader`, `DriveSyncWorker` |
| Caso de uso de subida | `DriveSyncScheduler`, `AppNavHost` y todas las pantallas Compose |
| PIN, reglas del PIN, bloqueo de la app | `BiometricAuthHelper`, vibración, insets |
| `ViewModel`s de bloqueo, grabaciones y ajustes | Flujo completo con pantalla bloqueada |

No hay umbral de cobertura configurado; el criterio es **toda lógica nueva con test en JVM**, y un
envoltorio fino solo si no tiene lógica.

## Comandos

```bash
cd Project
./gradlew testDebugUnitTest                          # todos los tests JVM
./gradlew testDebugUnitTest --tests "*SettingsViewModelTest"   # una clase
./gradlew connectedDebugAndroidTest                  # instrumentados (dispositivo conectado)
```

Informe HTML: `Project/app/build/reports/tests/testDebugUnitTest/index.html`.

## Añadir un test nuevo (checklist)

1. ¿La lógica está separada de Android? Si no, extraerla (interfaz + lambdas) antes de testear.
2. Crear `<Clase>Test` en el mismo paquete bajo `src/test`.
3. Reutilizar o crear un `Fake*` de la interfaz; nada de mocks.
4. Resetear el estado compartido en `@Before`.
5. Nombrar el test con una frase de comportamiento.
6. Ejecutar `./gradlew testDebugUnitTest` completo.

Si se añade un parámetro al constructor de una clase con tests, actualizar su `newXxx()` con un valor por
defecto (así hay que tocar un solo sitio).

## Checklist manual en dispositivo

Antes de dar por buena una versión, con el APK debug en un teléfono físico:

- [ ] Permisos concedidos y servicio de accesibilidad activo; sin ellos aparece el aviso.
- [ ] Con la pantalla **bloqueada**: mantener volumen abajo 2 s → notificación "Recording"; volumen arriba → se detiene.
- [ ] La grabación aparece en la lista, se reproduce y se elimina (el fichero desaparece).
- [ ] Primer arranque: se pide crear PIN; tras salir de la app vuelve a pedir desbloqueo.
- [ ] Ajustes: los botones son pulsables (no quedan bajo la barra de estado).
- [ ] Vibración activada: 1 pulso corto al iniciar, 2 al detener; desactivada: ninguna.
- [ ] Drive: *Conectar con Google* muestra "Conectado como…"; *Sincronizar ahora* pasa las grabaciones a `SYNCED`
      y aparecen en la carpeta `voice-recorder` de Drive.
