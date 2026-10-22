# Manejo de errores y logging

## Principios

1. **Un fallo externo no tumba la app.** Micrófono ocupado, red caída, token caducado o permiso
   revocado son situaciones esperadas: se capturan donde se pueden resolver.
2. **No se persiste un estado a medias.** Si una grabación falla, no se guarda una fila ni queda un fichero corrupto.
3. **El usuario ve el motivo cuando puede actuar** (login de Google, sincronización); los errores internos
   del grabador **solo van al log**, sin notificaciones extra.
4. **Nada de `catch` vacíos**: o se registra, o se devuelve como valor, o se convierte en estado visible.

## Cómo se trata cada tipo de error

| Situación | Dónde se captura | Qué pasa |
|---|---|---|
| Falla `MediaRecorder` al iniciar (micro ocupado, IO) | `RecordingOrchestrator.start()` | Libera el grabador, borra el fichero parcial, `isRecording` sigue en `false`, no hay fila; avisa por `onError` → `Log.e`. El servicio deja el primer plano. |
| Falla `stop()` (sin audio válido, IO) | `RecordingOrchestrator.stop()` | Descarta el fichero, libera el grabador, no persiste nada (`finally` pone `isRecording=false`). |
| Excepción inesperada en el servicio | `RecordingService` | `Log.e`, fuerza `RecordingStatus=false`, libera el grabador y detiene el servicio. |
| Falta permiso `RECORD_AUDIO` | `VolumeKeyCommandHandler` / orquestador | El gesto se ignora (no se arranca un servicio de micrófono sin permiso). La UI muestra un aviso. |
| El sistema rechaza el primer plano | `tryStartForegroundWithNotification()` | `Log.e` y `stopSelf`; en la parada se continúa guardando igualmente. |
| Fallo de vibración | `RecordingService.vibrate()` | `Log.w`; nunca interrumpe la grabación. |
| Fallo al enviar el intent desde accesibilidad | `VolumeKeyAccessibilityService` | `Log.e` con la acción. |
| Fallo de subida a Drive | `GoogleDriveUploader.upload()` | Devuelve `Result.failure`; `Log.e` y `SyncStatus.report("Error al subir: …")`. |
| Hay subidas fallidas en una pasada | `DriveSyncWorker` | `Result.retry()` → WorkManager reintenta con **backoff exponencial**; la fila sigue `PENDING`. |
| Sin cuenta de Google con permiso / sync desactivada | `DriveSyncWorker` / `DriveSyncUseCase` | Termina en **éxito sin hacer nada** (no es un fallo transitorio; reintentar no lo arregla). Se informa en `SyncStatus`. |
| Fallo de inicio de sesión de Google | `GoogleSignInHelper.accountFromResult` + `describeError` | Mensaje en rojo en Ajustes (ver tabla siguiente). |
| Fallo al reproducir (`MediaPlayer`) | `AudioPlayerController` | Llama al callback `onError`; la UI deja de mostrar "reproduciendo" y muestra un `Toast` "No se pudo reproducir la grabación". |
| Error biométrico | `BiometricAuthHelper` | Callback `onError(String)`; la pantalla de bloqueo muestra el mensaje y permite usar el PIN. |
| PIN incorrecto / inválido | `LockViewModel`, `PinRules` | Estado `Error`/`PinSetupError` con mensaje en español. |

Reglas de diseño:

- Las **clases de dominio no propagan excepciones del grabador**: reciben un callback `onError(message, throwable)`
  para que el llamador decida cómo registrarlo (y siguen siendo testeables en JVM).
- Las operaciones que dependen de la red o de Google **devuelven `Result`** en lugar de lanzar.
- Se distingue **fallo transitorio** (reintentar) de **precondición no cumplida** (no reintentar y avisar).

### Mensajes de inicio de sesión (`GoogleSignInHelper.describeError`)

| Código | Mensaje mostrado | Causa típica |
|---|---|---|
| `DEVELOPER_ERROR` (10) | "Error de configuración (código 10): falta registrar el paquete y la huella SHA-1 en Google Cloud." | Falta el cliente OAuth Android o la SHA-1 no coincide |
| `SIGN_IN_CANCELLED` | "Inicio de sesión cancelado." | El usuario cerró el selector |
| `NETWORK_ERROR` | "Sin conexión de red." | Sin red |
| otro | "No se pudo conectar (código N)." | Ver el código en la documentación de Play Services |

### Estado de sincronización visible (`SyncStatus`)

Mensaje único, en español, mostrado en Ajustes. Ejemplos: "Sincronizando…", "Última sincronización: 3 subidas.",
"Sincronización omitida: no hay cuenta de Google con permiso de Drive.",
"Error al subir: `<Excepción>`: `<mensaje>`". Se reinicia con el proceso. Es la forma de diagnosticar en
un teléfono sin `adb`.

## Logging

- **API**: `android.util.Log`. No hay librería de logging ni envío remoto de errores.
- **Etiquetas** (`TAG`) como constante del fichero o `companion object`: `RecordingService`, `DriveSync`,
  `VolumeKeyA11yService`. Usar una etiqueta estable y corta por componente.
- **Niveles**:
  - `Log.e(TAG, mensaje, excepción)` — algo falló y se pierde funcionalidad (grabación, subida).
  - `Log.w(TAG, mensaje[, excepción])` — degradación no crítica (vibración, parada sin notificación).
  - `Log.d/i/v`: no se usan en producción; si se añaden para depurar, **eliminarlos antes del commit**.
- **Siempre con la excepción** como tercer argumento (la traza es lo que permite diagnosticar).
- **Qué NO se registra jamás**: el PIN o su *hash*, tokens de Google, contenido de audio, ni datos
  personales (el correo del usuario no se escribe en el log). Las rutas de fichero se limitan al nombre
  (`file.name`), no a la ruta completa.
- Los logs son locales: se leen con `adb logcat -s DriveSync RecordingService VolumeKeyA11yService`.

## Qué hacer al añadir código nuevo

1. ¿Puede fallar por causa externa? Devolver `Result` o capturar donde se pueda decidir.
2. ¿Hay que reintentar? Si es transitorio → `retry`; si es una precondición → éxito sin acción + mensaje.
3. ¿El usuario puede arreglarlo? Mostrar un mensaje claro en español. Si no, solo `Log.e`.
4. Añadir un test del camino de fallo con un `Fake` (ver [testing.md](testing.md)).
5. Revisar que el log no incluye datos sensibles.
