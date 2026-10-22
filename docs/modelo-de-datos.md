# Modelo de datos

Una sola entidad persistida en Room, más preferencias cifradas y ficheros de audio. No hay backend:
Drive es solo un destino de copia.

## Entidad `Recording`

Tabla `recordings` (Room, base de datos `recordings.db`, versión 1).
Fuente: `data/Recording.kt`.

| Campo | Tipo Kotlin | Descripción |
|---|---|---|
| `id` | `Long` | Clave primaria autogenerada (`@PrimaryKey(autoGenerate = true)`, por defecto `0`) |
| `filePath` | `String` | Ruta absoluta del `.m4a` en `filesDir/recordings/` |
| `createdAtEpochMs` | `Long` | Instante de finalización de la grabación (epoch ms; lo da el `clock` del orquestador) |
| `durationMs` | `Long` | Duración medida por `MediaRecorderController` (tiempo transcurrido, no leída del fichero) |
| `syncState` | `SyncState` | Estado de la copia a Drive |

### `SyncState`

| Valor | Significado |
|---|---|
| `PENDING` | Lista para subirse; el worker la recoge |
| `SYNCED` | Subida correctamente a Drive |
| `DISABLED` | Grabada con la sincronización desactivada |

Transiciones (las únicas que existen):

```
grabación terminada ──(sync activada)──▶ PENDING ──(subida OK)──▶ SYNCED
        │
        └──(sync desactivada)──▶ DISABLED ──(usuario activa sync: promoteDisabledToPending)──▶ PENDING
```

No hay vuelta atrás de `SYNCED`. Una grabación `PENDING` que no se puede subir permanece así
indefinidamente (el worker reintenta) hasta que se suba o el usuario la borre.

### Acceso: `RecordingDao` y `RecordingsRepository`

| Operación | Dónde | Notas |
|---|---|---|
| `add(filePath, createdAtEpochMs, durationMs, initialState)` | `RecordingsRepository` | inserta y devuelve el `id` |
| `delete(id)` | `RecordingsRepository` | borra la fila **y** el fichero de audio (en `Dispatchers.IO`) |
| `observeAll()` | `Flow<List<Recording>>` | orden `createdAtEpochMs DESC` |
| `pendingSyncRecordings()` | `suspend` | solo `PENDING`, orden `createdAtEpochMs ASC` |
| `markSynced(id)` | `suspend` | `PENDING → SYNCED` |
| `promoteDisabledToPending()` | `suspend`, devuelve filas cambiadas | `DISABLED → PENDING` |

Regla: **nadie fuera de `data/` usa `RecordingDao`**; el borrado del fichero va dentro del repositorio
(inyectable como `deleteAudioFile` para que los tests no toquen el disco).

### Modelo de UI

`RecordingUiModel(id, createdAtEpochMs, durationMs, syncState)` lo construye `RecordingsViewModel`
(`Recording.toUiModel()`). La UI no recibe `filePath`: lo pide con `viewModel.filePathFor(id)` para reproducir.

## Preferencias (`EncryptedSharedPreferences`)

Fichero `app_lock_prefs` (AES-256-GCM para valores, AES-256-SIV para claves; clave maestra en el
Android Keystore). Lo crea `EncryptedPrefsFactory`.

| Clave | Tipo | Quién | Descripción |
|---|---|---|---|
| `pin_hash` | String (Base64) | `DefaultPinManager` | SHA-256(sal + PIN) |
| `pin_salt` | String (Base64) | `DefaultPinManager` | 16 bytes aleatorios, nueva en cada `setPin` |
| `drive_sync_enabled` | Boolean (def. `false`) | `DefaultSyncPreferences` | Copia a Drive activada |
| `vibrate_on_recording_enabled` | Boolean (def. `false`) | `DefaultVibrationPreferences` | Vibrar al iniciar/parar |
| `calculator_lock_enabled` | Boolean (def. `false`) | `DefaultLockModePreferences` | Bloqueo con calculadora activado |

## Ficheros

| Ruta | Contenido |
|---|---|
| `filesDir/recordings/recording_<epochMs>.m4a` | Audio AAC en contenedor MPEG-4 |
| `databases/recordings.db` (+ `-shm`, `-wal`, `-journal`) | Base de datos Room |
| `shared_prefs/app_lock_prefs.xml` | Preferencias cifradas |

Todos están **excluidos del Auto Backup** y de la transferencia entre dispositivos
(`backup_rules.xml`, `data_extraction_rules.xml`; mantenerlos sincronizados). Motivo: la clave
maestra del Keystore no se restaura, y el audio es privado y solo viaja por la sincronización propia.

## Estado en memoria (no persistido)

| Objeto | Campo | Nota |
|---|---|---|
| `RecordingStatus` | `isRecording: StateFlow<Boolean>` | Se pierde con el proceso |
| `AppLockController.process` | `isLocked: StateFlow<Boolean>` | Siempre `true` al arrancar |
| `SyncStatus` | `message: StateFlow<String?>` | Último resultado de sincronización |

## En Drive

Carpeta `voice-recorder` en *Mi unidad*, creada por la app (scope `drive.file`). Cada grabación se sube con
el nombre de su fichero local (`recording_<epochMs>.m4a`, MIME `audio/mp4`). Ver
[sincronizacion-drive.md](sincronizacion-drive.md).

## Cambiar el esquema

- `AppDatabase` está en `version = 1` **sin migraciones**. Cualquier cambio de columnas/tablas exige subir
  la versión **y** escribir una `Migration` (o decidir explícitamente borrar datos). No cambiar el
  esquema sin migración: las grabaciones del usuario viven en esa tabla.
- Si se añade un estado a `SyncState`, revisar la consulta `pendingSync()` (usa el literal `'PENDING'`),
  `DriveSyncUseCase` y los tests de `RecordingsRepository`.
- Si se renombran claves de preferencias, migrar los valores: no hay copia de seguridad que los restaure.
