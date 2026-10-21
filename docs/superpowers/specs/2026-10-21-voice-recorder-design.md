# Auto Voice Recorder — Diseño

Fecha: 2026-10-21

## Objetivo y alcance

App Android nativa (Kotlin + Jetpack Compose) de grabación de voz personal, pensada
para notas de voz rápidas o grabaciones con consentimiento (p. ej. entrevistas,
reuniones propias, accesibilidad). No es una herramienta de vigilancia encubierta:
siempre muestra una notificación persistente mientras graba y no se disfraza de
otra aplicación.

Funcionalidad cubierta:

- Iniciar grabación manteniendo pulsado 3s el botón de bajar volumen, incluso con
  la pantalla bloqueada.
- Detener grabación pulsando el botón de subir volumen.
- Notificación obligatoria y mínima ("Recording") mientras graba.
- Listar, reproducir y eliminar grabaciones dentro de la app.
- Copia de seguridad automática opcional en la carpeta privada de la app en
  Google Drive (`appDataFolder`).
- Indicador de "grabando ahora" al abrir la app.
- Acceso a la app protegido con biometría del sistema + PIN de respaldo.

Explícitamente fuera de alcance (ver conversación de brainstorming):

- Interceptar el botón de power/bloqueo (no es posible en Android; es un evento
  reservado del sistema, ninguna app ni Accessibility Service puede capturarlo).
- Cualquier forma de "modo oculto" que disfrace la app como otra aplicación
  (p. ej. calculadora). Se descartó por ser un patrón característico de
  stalkerware y por infringir las políticas de Google Play y, en España,
  la normativa de protección de la intimidad (art. 197 CP) si se usa para
  grabar a terceros sin su conocimiento.
- minSdk por debajo de API 29 (Android 10).

## Arquitectura

```
VolumeKeyAccessibilityService (AccessibilityService)
  └─ detecta KEYCODE_VOLUME_DOWN mantenido 3s
  └─ envía Intent(ACTION_START_RECORDING) a RecordingService
  └─ mientras graba, detecta KEYCODE_VOLUME_UP
  └─ envía Intent(ACTION_STOP_RECORDING) a RecordingService
       ↓
RecordingService (foreground service, type="microphone")
  └─ MediaRecorder → archivo .m4a en almacenamiento privado de la app
  └─ notificación persistente "Recording" (icono de micrófono, sin botones)
  └─ al detener: cierra el archivo, guarda metadatos en Room,
     encola DriveSyncWorker si la sincronización está activada
       ↓
RecordingsRepository (Room DB)
  └─ tabla `recordings`: id, ruta, fechaCreacion, duracionMs, syncState
       ↓
DriveSyncWorker (WorkManager, constraint de red)
  └─ sube archivos con syncState = PENDING a appDataFolder de Drive
  └─ reintentos con backoff exponencial en caso de fallo
       ↓
UI (Jetpack Compose)
  └─ Pantalla de desbloqueo (biometría + PIN de respaldo)
  └─ Pantalla principal: banner "grabando ahora" (si RecordingService activo)
     + lista de grabaciones (reproducir / eliminar)
  └─ Pantalla de ajustes: activar/desactivar sync con Drive, cambiar PIN,
     acceso directo a Ajustes > Accesibilidad para activar el servicio
```

### Componentes

- **VolumeKeyAccessibilityService**: única responsabilidad es detectar los
  gestos de volumen y traducirlos en intents de inicio/parada. No conoce
  `MediaRecorder` ni el sistema de archivos.
- **RecordingService**: único responsable del ciclo de vida de la grabación
  (`MediaRecorder`, archivo, notificación). Expone únicamente los intents
  `ACTION_START_RECORDING` / `ACTION_STOP_RECORDING` y un `StateFlow` de
  "¿hay una grabación en curso?" que la UI puede observar.
- **RecordingsRepository**: capa de acceso a Room; la UI y `RecordingService`
  nunca tocan la base de datos directamente, pasan siempre por aquí.
- **DriveSyncWorker**: aislado de la lógica de grabación; solo sabe leer
  registros `PENDING` del repositorio y subirlos.
- **UI (Compose)**: no conoce detalles de `MediaRecorder` ni de Drive;
  observa `StateFlow`s expuestos por el repositorio y por `RecordingService`.

## Flujo de datos y permisos

- **Permisos**: `RECORD_AUDIO`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (Android 13+),
  servicio de Accesibilidad (activación manual del usuario en Ajustes del
  sistema; la app enlaza directamente a esa pantalla con
  `Settings.ACTION_ACCESSIBILITY_SETTINGS`), y scope de Drive
  `drive.appdata` vía OAuth de Google al activar la sincronización.
- **Arranque tras reinicio**: no se re-arma nada manualmente; si el usuario
  activó el Accessibility Service, Android lo vuelve a lanzar solo al
  reiniciar (comportamiento estándar de la plataforma). No se usa
  `RECEIVE_BOOT_COMPLETED`.
- **Durante la grabación**: `MediaRecorder` escribe de forma incremental al
  archivo, así que un cierre inesperado (proceso matado, sin batería) deja
  como mucho el archivo truncado hasta el último segundo grabado, nunca se
  pierde la grabación completa.
- **Sincronización**: al terminar una grabación se marca `syncState = PENDING`
  y se encola `DriveSyncWorker` si la sincronización está activada; si no lo
  está, el registro queda en `PENDING` hasta que el usuario la active.

## Manejo de errores

- Sin permiso `RECORD_AUDIO` concedido → el Accessibility Service ignora el
  gesto de 3s, no intenta iniciar `RecordingService` (evita crash). Al abrir
  la app se muestra un aviso pidiendo conceder el permiso.
- Accessibility Service desactivado por el usuario desde Ajustes → la app
  comprueba `AccessibilityManager.isEnabled` al abrirse y muestra un banner
  para reactivarlo si corresponde.
- Fallo de `MediaRecorder` (micrófono ocupado por otra app, error de IO) →
  se detiene el intento limpiamente, no se persiste un archivo corrupto en
  Room, se registra el error solo en log interno (sin notificación extra).
- Fallo de subida a Drive (sin red, token caducado, cuota) → WorkManager
  reintenta con backoff exponencial; el registro permanece `PENDING`
  indefinidamente hasta éxito o hasta que el usuario borre la grabación.

## Testing

- Unit tests (JVM) para `RecordingsRepository` y las transiciones de
  `syncState`.
- Test instrumentado para el temporizador de 3s de
  `VolumeKeyAccessibilityService`, simulando `KeyEvent`s.
- Test manual en dispositivo físico para el flujo completo
  bloqueado → grabar → desbloquear → parar: `MediaRecorder` y los eventos
  de teclas físicas de hardware no se simulan de forma fiable en emulador.

## Decisiones descartadas y por qué

- **Interceptar el botón de power**: técnicamente imposible en Android para
  cualquier app de terceros; se sustituye por "subir volumen detiene la
  grabación".
- **Modo calculadora / app disfrazada**: descartado explícitamente por ser
  un patrón de stalkerware (vigilancia encubierta sin conocimiento de la
  persona grabada) y por las implicaciones legales y de políticas de Play
  Store que conlleva. El acceso a la app se protege con biometría + PIN,
  sin ocultar su identidad real.
- **minSdk 26**: descartado a favor de minSdk 29 para tener una base de
  código única y coherente frente a las reglas de foreground service de
  micrófono, que son más estrictas y uniformes a partir de esa versión.
