# Sincronización con Google Drive

Copia **opcional** de las grabaciones a una carpeta `voice-recorder` en *Mi unidad*. Desactivada por defecto.

## Comportamiento

- Alcance OAuth: **`drive.file`**. Con él la app solo ve lo que ella misma crea.
- Carpeta destino: **`voice-recorder`** en la raíz de *Mi unidad*. La app la busca por nombre
  (`mimeType = carpeta and name = 'voice-recorder' and trashed = false`) y la crea si no existe.
  Como solo ve lo que creó, **no puede usar una carpeta `voice-recorder` creada a mano** (p. ej. dentro de
  `Proyectos/`): crea la suya. Si el usuario **mueve** la carpeta creada por la app a otro sitio, la app la
  sigue encontrando por nombre.
- Cada grabación se sube con el nombre del fichero local (`recording_<epochMs>.m4a`), MIME `audio/mp4`.
- **Solo sube**: borrar una grabación en la app no la borra de Drive, y no se evitan duplicados si una subida
  termina pero falla justo antes de marcar `SYNCED`.
- Las grabaciones subidas con versiones antiguas de la app (que usaban la carpeta oculta `appDataFolder`)
  permanecen allí; no se migran.

## Componentes

| Clase | Función |
|---|---|
| `GoogleSignInHelper` | Opciones de login (`requestEmail()` + scope `drive.file`), `lastAccount()`, `accountFromResult()`, `describeError()` |
| `GoogleDriveUploader` | Implementa `DriveUploader` con la API v3: busca/crea la carpeta y sube el fichero |
| `DriveSyncUseCase` | Sube todas las filas `PENDING` y las marca `SYNCED` (no hace nada si la sync está desactivada) |
| `DriveSyncWorker` | `CoroutineWorker` que une cuenta + repositorio + preferencias |
| `DriveSyncScheduler` | Encola trabajo único `drive_sync` con red y *backoff* exponencial |
| `SyncPreferences` | Interruptor de sincronización (`drive_sync_enabled`) |
| `SyncStatus` | Último resultado, visible en Ajustes |

### Cuándo se sincroniza

| Disparador | Política de cola |
|---|---|
| Se guarda una grabación `PENDING` (`RecordingService`) | `APPEND_OR_REPLACE`: encadena tras la ejecución en curso |
| Activar el interruptor, **Conectar con Google** con sync activa, **Sincronizar ahora** (Ajustes) | `REPLACE`: cancela lo pendiente/en *backoff* y ejecuta ya |

La política `REPLACE` para acciones del usuario es **deliberada**: con `APPEND`, una ejecución fallida en
*backoff* exponencial (hasta horas) retenía todas las nuevas, y las grabaciones se quedaban en `PENDING`.

### Reglas de `lastAccount()`

Devuelve la cuenta solo si (a) existe, (b) tiene `account != null` y (c) tiene concedido `drive.file`. Una sesión
creada **sin correo** no sirve: `GoogleAccountCredential.selectedAccount = null` lanza
`IllegalArgumentException: the name must be not be empty: null`. Por eso se trata como "no conectado" y
hay que volver a conectar.

## Configuración de Google Cloud (una sola vez)

Necesitas una cuenta con acceso a [console.cloud.google.com](https://console.cloud.google.com).

1. **Proyecto**: crear o elegir uno (p. ej. `auto-voice-recorder`).
2. **API**: *APIs y servicios → Biblioteca → Google Drive API → Habilitar*.
3. **Pantalla de consentimiento OAuth** (*Google Auth Platform*):
   - Información de la app: nombre `Auto Voice Recorder` y tu correo de asistencia.
   - Público: **Externo**.
   - *Acceso a los datos*: añadir el alcance `.../auth/drive.file`.
   - *Público → Usuarios de prueba*: añadir **el correo de Google con el que inicias sesión en el teléfono**.
     Mientras la app esté en modo "Prueba", Google rechaza cualquier otra cuenta.
4. **Credenciales → Crear credenciales → ID de cliente de OAuth → Android**:
   - Nombre del paquete: `com.example.auto_voice_recorder`
   - Huella SHA-1: la de tu `debug.keystore` → `cd Project && ./gradlew signingReport` (variante `debug`).
5. Esperar unos minutos a que se propague y reinstalar/relanzar la app.

> El nombre visible en la pantalla de consentimiento (`Auto Voice Recorder`) es independiente del nombre
> del launcher de esta variante (`Basic Calc`). El cliente OAuth se identifica por el paquete y la huella
> SHA-1, no por el nombre visible; no hace falta cambiar la configuración de Google Cloud.

Notas:

- Cada máquina de desarrollo tiene su propia SHA-1 de depuración; para varias hay que crear un cliente
  Android por huella (o compartir el `debug.keystore`). Una compilación firmada con otra clave necesita su huella.
- En modo "Prueba" con usuarios externos, Google puede caducar la autorización a los 7 días y obligar a
  reconectar; comprobarlo en la documentación vigente de Google. Pasar la app a "En producción" evita
  esa restricción (el alcance `drive.file` no es sensible).
- Al reconectar tras cambiar de alcance, Google muestra de nuevo la pantalla de permisos.

## Errores frecuentes

| Mensaje / síntoma | Causa | Solución |
|---|---|---|
| "Error de configuración (código 10)…" tras elegir cuenta | Falta el cliente OAuth Android, o paquete/SHA-1 incorrectos, o el usuario no está en *usuarios de prueba* | Revisar los pasos 3–4 |
| Elegir cuenta y "no pasa nada" (APK antiguo) | El resultado cancelado se descartaba en silencio | Usar la versión actual: muestra el error |
| "No conectado a Google" pese a haber iniciado sesión | Sesión sin correo/`account` nulo, o falta el permiso `drive.file` | **Conectar con Google** de nuevo |
| `Error al subir: SecurityException: Permission denied (missing INTERNET permission)` | Falta `INTERNET` en el manifiesto | Mantener el permiso declarado |
| `Error al subir: IllegalArgumentException: the name must be not be empty: null` | Cuenta sin `account` (ver arriba) | Reconectar |
| `Error al subir: GoogleJsonResponseException … 403` | Drive API no habilitada en el proyecto, o alcance no concedido | Habilitar la API; reconectar |
| Grabaciones siguen `PENDING` | Worker en *backoff* tras fallos previos, o sync desactivada, o sin cuenta | **Sincronizar ahora**; mirar el mensaje de `SyncStatus`; `adb logcat -s DriveSync` |
| No veo los ficheros en Drive | Versión antigua (carpeta oculta `appDataFolder`) o buscando en otra carpeta | Buscar `voice-recorder` en *Mi unidad* |

## Probar manualmente

1. Ajustes → **Conectar con Google** → elegir cuenta → debe poner "Conectado como …".
2. Activar **Sincronizar grabaciones con Google Drive**.
3. Grabar algo (o usar una existente) → **Sincronizar ahora** → "Última sincronización: N subidas."
4. En la lista, la grabación pasa de `PENDING` a `SYNCED`; en Drive aparece en `voice-recorder`.

## Ampliar o cambiar

- **Otro destino** (otro proveedor): implementar `DriveUploader` y cambiar `DriveSyncWorker`; el caso de uso y
  los tests (`FakeDriveUploader`) no cambian.
- **Subir a una carpeta existente del usuario** requeriría el alcance `drive` (restringido, con verificación de
  Google) o el selector de archivos de Drive; hoy se descarta por privacidad.
- **Borrado remoto / deduplicación**: no implementados; requerirían guardar el `id` de Drive en la entidad
  `Recording` (cambio de esquema con migración, ver [modelo-de-datos.md](modelo-de-datos.md#cambiar-el-esquema)).
