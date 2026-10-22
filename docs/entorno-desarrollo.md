# Entorno de desarrollo

## Requisitos

- **Android Studio** reciente (el proyecto usa AGP 9.2.1 y Gradle 9.4.1).
- **JDK 21**. No hace falta instalarlo a mano: el toolchain (`foojay-resolver-convention`) lo descarga.
- **Android SDK** con plataforma 36 y `platform-tools` (`adb`). Android Studio lo instala.
- Un **teléfono físico con Android 10+** (API 29+). Ver "Probar" más abajo: el flujo principal no se
  puede validar de forma fiable en emulador.
- Opcional: una cuenta de Google con acceso a Google Cloud, solo para probar la copia a Drive.

## Primer arranque

1. Clonar el repositorio y abrir la carpeta **`Project/`** en Android Studio (no la raíz).
2. Android Studio genera `Project/local.properties` con `sdk.dir=...` (está en `.gitignore`).
   Fuera del IDE, créalo a mano o define `ANDROID_HOME`.
3. Sincronizar Gradle. La primera vez descarga dependencias y el JDK 21.

No hay variables de entorno, ficheros `.env`, claves de API ni secretos que configurar en el repo.

## Comandos habituales

Desde `Project/` (en Windows `gradlew.bat`; en Git Bash vale `./gradlew.bat`):

| Acción | Comando |
|---|---|
| Compilar APK debug | `./gradlew assembleDebug` |
| Tests unitarios (JVM) | `./gradlew testDebugUnitTest` |
| Compilar y testear | `./gradlew assembleDebug testDebugUnitTest` |
| Tests instrumentados (dispositivo conectado) | `./gradlew connectedDebugAndroidTest` |
| Huella SHA-1 de depuración | `./gradlew signingReport` |
| Limpiar | `./gradlew clean` |

APK resultante: `Project/app/build/outputs/apk/debug/app-debug.apk` (~43 MB).

Instalar en el teléfono (depuración USB activada):

```bash
adb devices
adb install -r Project/app/build/outputs/apk/debug/app-debug.apk
adb logcat -s DriveSync RecordingService     # ver errores de sincronización y de grabación
```

`adb` vive en `<sdk.dir>/platform-tools/`; puede que no esté en el `PATH`.

## Configuración en el dispositivo

La app no funciona del todo sin estos pasos manuales:

1. **Permisos**: micrófono y (Android 13+) notificaciones. La pantalla de grabaciones los solicita.
2. **Servicio de accesibilidad**: *Ajustes → Accesibilidad → Basic Calc* → activar. Sin él,
   las teclas de volumen no hacen nada. La app muestra un aviso con un acceso directo.
3. **PIN**: se crea en el primer arranque (mínimo 4 dígitos).
4. **Google Drive** (opcional): configurar Google Cloud ([sincronizacion-drive.md](sincronizacion-drive.md))
   y luego *Ajustes → Conectar con Google*.

## Probar

- **Teléfono físico** para el flujo completo (bloqueado → mantener volumen abajo 2 s → grabar →
  volumen arriba): `MediaRecorder` y las teclas físicas no se simulan de forma fiable en emulador.
- **Emulador**: sirve para UI y navegación. Necesita aceleración por hardware (Intel VT-x / AMD-V en
  BIOS + Windows Hypervisor Platform o Hyper-V; comprobar con `emulator -accel-check`) y una imagen de
  sistema instalada. Google Sign-In requiere imagen con Google Play.
- **Tests JVM**: no necesitan dispositivo; son la red de seguridad principal ([testing.md](testing.md)).

## Problemas habituales

| Síntoma | Causa y solución |
|---|---|
| "Conectar con Google" no hace nada | Falta el cliente OAuth Android (paquete + SHA-1) en Google Cloud → error `DEVELOPER_ERROR (10)`. La pantalla de Ajustes muestra el motivo. Ver [sincronizacion-drive.md](sincronizacion-drive.md). |
| `SecurityException: missing INTERNET permission` | Falta `<uses-permission INTERNET>` en el manifiesto. Ya está declarado; no quitarlo. |
| `IllegalArgumentException: the name must be not be empty: null` al subir | Sesión de Google creada sin correo (`GoogleSignInAccount.account == null`). Reconectar desde Ajustes (ya se pide `requestEmail()`). |
| Grabaciones en `PENDING` para siempre | Una ejecución previa de `DriveSyncWorker` falló y está en *backoff* exponencial (hasta horas). Usar **Sincronizar ahora** en Ajustes (reemplaza la cola). Ver [errores-y-logging.md](errores-y-logging.md). |
| El botón de Ajustes queda bajo la barra de estado | La actividad es *edge-to-edge*; el `NavHost` aplica `safeDrawingPadding()`. No quitarlo. Ver [ui-movil.md](ui-movil.md). |
| `Filename too long` al borrar `build/` (Windows) | Rutas largas bajo `.claude/worktrees/...`. Borrar con prefijo `\\?\` o activar rutas largas en Windows. |
| La app se cierra al leer preferencias tras restaurar un backup | El fichero cifrado no se puede descifrar en otro dispositivo; por eso se excluye del Auto Backup. No quitar esas reglas. |

## Flujo de trabajo con git

Ver [convenciones.md](convenciones.md#git-workflow). Resumen: rama `main`, *conventional commits*
en inglés, commits pequeños por tema.
