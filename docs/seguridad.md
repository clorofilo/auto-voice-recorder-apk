# Seguridad y privacidad

## Postura

La app graba audio, que es un dato especialmente sensible. Principios:

1. **Transparencia**: siempre hay una notificación visible mientras se graba; no hay modo oculto de
   grabación. En esta variante la *pantalla de bloqueo* puede mostrarse como una calculadora, pero la
   grabación nunca se oculta. Esto no es negociable (ver más abajo).
2. **Mínimo privilegio**: solo los permisos y alcances imprescindibles.
3. **Local primero**: el audio vive en el almacenamiento privado de la app; sale del teléfono solo si el
   usuario activa la copia a Drive.
4. **Sin telemetría**: no hay analítica, publicidad ni envío de datos a terceros (salvo Drive, opcional).

### Uso legal y responsable

Grabar a otras personas sin su conocimiento puede ser delito (p. ej. art. 197 del Código Penal en
España) y vulnera las políticas de Google Play. Se descartaron explícitamente: interceptar el botón de
power y cualquier ocultación de la grabación (patrón de *stalkerware*). **No se aceptan
contribuciones que reduzcan la visibilidad de la grabación.**

La versión principal (`main`) descartó el «modo calculadora». Esta variante lo incorpora como **bloqueo
opcional con calculadora**, aprobado explícitamente para uso personal: solo cambia el aspecto de la
pantalla de bloqueo, no oculta la notificación de grabación y no toca el servicio de accesibilidad ni
`RecordingService`.

## Permisos

| Permiso | Para qué | Notas |
|---|---|---|
| `RECORD_AUDIO` | Grabar | Se solicita en tiempo de ejecución; sin él el gesto se ignora |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Servicio en primer plano de tipo micrófono | Declarado en el manifiesto con `foregroundServiceType="microphone"` |
| `POST_NOTIFICATIONS` | Mostrar la notificación obligatoria (Android 13+) | Runtime |
| `INTERNET` | Subir a Drive | Permiso normal; sin él la subida falla con `SecurityException` |
| `VIBRATE` | Vibración opcional | Permiso normal |
| Servicio de accesibilidad | Leer teclas de volumen | Lo activa el usuario a mano en Ajustes del sistema |

Antes de añadir un permiso: justificarlo aquí y en el manifiesto, y valorar si hay alternativa.

## Servicio de accesibilidad

- `canRetrieveWindowContent="false"`: no lee el contenido de la pantalla.
- Solo procesa `KEYCODE_VOLUME_DOWN` y `KEYCODE_VOLUME_UP`; `onKeyEvent` devuelve siempre `false`
  (el sistema aplica el cambio de volumen normal).
- Nunca intercepta el botón de power. `RecordingService` no está exportado (`exported="false"`); el
  servicio de accesibilidad exige `BIND_ACCESSIBILITY_SERVICE`.

## Protección de acceso a la app

- **Bloqueo** con biometría (`BIOMETRIC_STRONG`) y **PIN** de respaldo (mínimo 4 dígitos, solo números).
- El PIN **no se guarda**: se almacena `SHA-256(sal ‖ PIN)` con **sal aleatoria de 16 bytes**
  (`SecureRandom`), comparado con `MessageDigest.isEqual` (tiempo constante). Ambos valores van en
  `EncryptedSharedPreferences` (AES-256-GCM/SIV, clave maestra en Android Keystore).
- La app **nace bloqueada** en cada proceso y se **vuelve a bloquear** al pasar a segundo plano.
- El PIN inicial solo se puede crear si no existe ninguno (no se puede sobrescribir desde la pantalla de bloqueo).
- **Modo calculadora opcional** (interruptor en Ajustes, desactivado por defecto): la pantalla de bloqueo
  es una calculadora funcional; el PIN se teclea como número directo y se pulsa `=` para entrar. En este
  modo **no hay biometría** y la creación de PIN sigue teniendo prioridad si aún no existe ninguno.
- Un código incorrecto es **silencioso**: no muestra error, no cambia el color, no vibra y no se registra
  en logs. Un resultado calculado nunca desbloquea, aunque coincida con el PIN.
- Lo tecleado en la calculadora vive solo en memoria: no se guarda en el estado de instancia de Android
  (`rememberSaveable`) y se reinicia al pasar la app a segundo plano (`ON_STOP`) y al girar la pantalla.
- **No hay recuperación de PIN**: si se olvida, la única salida es borrar los datos de la app, con pérdida
  de las grabaciones locales.

### Límites conocidos (deuda de seguridad)

- SHA-256 de una sola pasada con un PIN de 4 dígitos es barato de forzar **si alguien consiguiera el
  fichero de preferencias** (que ya está cifrado con una clave del Keystore y aislado por el sandbox de
  Android). Mejora prevista: derivación lenta (PBKDF2/scrypt/Argon2).
- **No hay límite de intentos** ni bloqueo temporal tras PIN erróneo. En el modo calculadora los fallos son
  además silenciosos (sin error, color ni vibración), así que alguien con acceso físico puede probar
  combinaciones sin ninguna señal. La única salida si se olvida el PIN es borrar los datos de la app.
- Los **ficheros de audio no están cifrados** por la app (dependen del aislamiento de Android y del
  cifrado de disco del dispositivo).
- No se usa `FLAG_SECURE`: las capturas de pantalla y la vista de apps recientes pueden mostrar la lista de grabaciones.
- Compilación sin ofuscación (`isMinifyEnabled = false`) y sin firma de release.

## Datos en reposo y copias de seguridad

| Dato | Dónde | Protección |
|---|---|---|
| Audio `.m4a` | `filesDir/recordings/` (privado de la app) | Sandbox de Android |
| Metadatos | Room `recordings.db` (privado) | Sandbox de Android |
| PIN (hash+sal) y ajustes | `app_lock_prefs` | `EncryptedSharedPreferences` + Keystore |

`allowBackup="true"` pero con **exclusiones** en `backup_rules.xml` (Android ≤ 11) y
`data_extraction_rules.xml` (Android ≥ 12; `cloud-backup` **y** `device-transfer`): se excluyen las
preferencias cifradas, la base de datos y los audios. Motivos: la clave del Keystore no se restaura (un
fichero cifrado restaurado haría fallar la app al leerlo) y los audios solo deben salir por la copia a Drive.
**Mantener ambos ficheros sincronizados y no quitar las exclusiones.**

## Google Drive

- Alcance mínimo: **`drive.file`** (solo ve los ficheros y carpetas que la propia app crea). Nunca `drive` completo.
- Autenticación con Google Sign-In/Play Services; la app **no maneja contraseñas ni tokens**
  directamente y no almacena credenciales.
- El cliente OAuth de Android se identifica por **paquete + huella SHA-1**: no hay *client secret* en el repo.
- Las grabaciones salen del dispositivo **solo** si el usuario activa la sincronización. Borrar una grabación
  en la app **no** la borra de Drive.

Detalle en [sincronizacion-drive.md](sincronizacion-drive.md).

## Superficie de componentes

| Componente | Exportado | Protección |
|---|---|---|
| `MainActivity` | Sí (launcher) | Pantalla de bloqueo antes de cualquier dato |
| `RecordingService` | **No** | Solo lo inicia la propia app |
| `VolumeKeyAccessibilityService` | Sí (requisito del sistema) | `BIND_ACCESSIBILITY_SERVICE` |

## Secretos y repositorio

- No hay secretos en el repo. `local.properties` (ruta del SDK), keystores y `build/` están ignorados.
- **Nunca** commitear keystores, `google-services.json` con claves privadas, tokens ni capturas con datos reales.
- La huella SHA-1 de depuración es específica de cada máquina (`~/.android/debug.keystore`): cada
  desarrollador debe registrar la suya en Google Cloud.

## Reglas para quien programe

1. No registrar en logs PIN, *hash*, tokens, correos, audio ni rutas completas ([errores-y-logging.md](errores-y-logging.md)).
2. Nada de red salvo Drive; no añadir SDK de analítica o publicidad.
3. No ampliar permisos ni el alcance de Drive sin documentarlo aquí.
4. No cambiar `exported` ni abrir el servicio de grabación a otras apps.
5. No tocar las exclusiones de backup.
6. No debilitar el bloqueo (`isLocked` inicial `true`, re-bloqueo en `ON_STOP`).
7. Dependencias: preferir versiones estables y mantenerlas en `libs.versions.toml`; revisar avisos de seguridad al actualizar.
8. Antes de una versión de release: activar minificación, configurar firma, revisar los *límites conocidos* de arriba.
