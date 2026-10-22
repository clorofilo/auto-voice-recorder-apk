# Basic Calc

Variante de **Auto Voice Recorder** que se instala con el nombre **Basic Calc** y un icono de
calculadora. La grabación de voz, su notificación y el servicio de accesibilidad funcionan igual.

App Android nativa (Kotlin + Jetpack Compose) de grabación de voz personal: notas de voz
rápidas o grabaciones con consentimiento (entrevistas, reuniones propias, accesibilidad).

> **Uso responsable.** La app **no** es una herramienta de vigilancia encubierta: muestra siempre una
> notificación persistente mientras graba. Esta variante se instala como **Basic Calc** con icono de
> calculadora y añade un **bloqueo opcional con calculadora**. El camuflaje afecta al launcher y a la
> *pantalla de bloqueo*, nunca a la grabación: la notificación sigue visible, con el texto "Recording" y un
> icono de micrófono, y el gesto de volumen funciona igual. Grabar a terceros sin su conocimiento puede ser
> ilegal (p. ej. art. 197 del Código Penal en España). Ver [docs/seguridad.md](docs/seguridad.md).

## Qué hace

- **Inicia** la grabación manteniendo pulsado **volumen abajo durante 2 s**, también con la pantalla bloqueada.
- **Detiene** la grabación pulsando **volumen arriba**.
- Notificación obligatoria "Recording" mientras graba.
- Lista, reproduce y elimina grabaciones (`.m4a`, AAC) guardadas en el almacenamiento privado de la app.
- **Copia opcional a Google Drive**, en una carpeta visible `voice-recorder` de *Mi unidad*.
- **Vibración opcional** al iniciar (1 pulso corto) y al detener (2 pulsos cortos).
- Acceso protegido con **biometría + PIN** (el PIN se crea en el primer arranque).
- **Bloqueo opcional con calculadora**: la pantalla de bloqueo se muestra como una calculadora funcional y el
  PIN se teclea como número, pulsando `=` para entrar (sin biometría en ese modo y sin recuperación de PIN).
- **Identidad de la variante**: el launcher muestra **Basic Calc** y un icono de calculadora; el paquete
  `com.example.auto_voice_recorder` no cambia.
- Avisos de configuración: permiso de micrófono y servicio de accesibilidad.

Fuera de alcance (decisiones tomadas, ver [spec de diseño](docs/superpowers/specs/2026-10-21-voice-recorder-design.md)):
interceptar el botón de power (imposible en Android), la ocultación de la grabación (la notificación siempre
queda visible) y `minSdk` < 29.

## Inicio rápido

Requisitos: Android Studio reciente, JDK 21 (Gradle lo descarga solo) y un teléfono con Android 10+ (API 29+).

```bash
cd Project
./gradlew assembleDebug            # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # tests unitarios JVM
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Después, en el teléfono: conceder micrófono y notificaciones, y activar **Ajustes → Accesibilidad →
Basic Calc**. Para la copia a Drive hay que configurar un cliente OAuth en Google Cloud
(una sola vez): [docs/sincronizacion-drive.md](docs/sincronizacion-drive.md).

Guía completa del entorno: [docs/entorno-desarrollo.md](docs/entorno-desarrollo.md).

## Documentación

Todo está pensado para no tener que releer el código. Empieza por la arquitectura.

| Documento | Contenido |
|---|---|
| [docs/stack-tecnologico.md](docs/stack-tecnologico.md) | Lenguajes, versiones, librerías y por qué |
| [docs/entorno-desarrollo.md](docs/entorno-desarrollo.md) | Setup, comandos, emulador/dispositivo, problemas habituales |
| [docs/arquitectura.md](docs/arquitectura.md) | Capas, estructura de ficheros, flujos y estado compartido |
| [docs/modelo-de-datos.md](docs/modelo-de-datos.md) | Entidades Room, estados de sincronización, preferencias y ficheros |
| [docs/convenciones.md](docs/convenciones.md) | Nombres, patrones, clean code/SOLID, git, cómo documentar el código |
| [docs/testing.md](docs/testing.md) | Estructura y estándares de los tests |
| [docs/ui-movil.md](docs/ui-movil.md) | Estándares de UI (Compose), validación de formularios, accesibilidad |
| [docs/errores-y-logging.md](docs/errores-y-logging.md) | Manejo de errores y logging |
| [docs/seguridad.md](docs/seguridad.md) | Ciberseguridad, privacidad, permisos y límites conocidos |
| [docs/sincronizacion-drive.md](docs/sincronizacion-drive.md) | Integración con Google Drive y configuración de Google Cloud |
| [docs/superpowers/](docs/superpowers) | Spec de diseño original y plan de implementación (históricos) |

Para agentes de IA: [CLAUDE.md](CLAUDE.md) y [AGENTS.md](AGENTS.md).

## Estructura del repositorio

```
.
├── Project/              # Proyecto Gradle (módulo :app)
├── docs/                 # Documentación técnica
├── CLAUDE.md / AGENTS.md # Contexto para agentes de IA
└── README.md
```

## Estado

Aplicación de uso personal en desarrollo. Solo hay compilación **debug** (sin firma de release ni
minificación). No está publicada en Google Play.
