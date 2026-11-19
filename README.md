# JR Audio — Android

Proyecto de la nueva aplicación JR Audio. La primera entrega contiene el prototipo navegable de interfaz en HTML, preparado para integrarse en una aplicación Android con Kotlin y WebView.

## Estado actual

- Proyecto Android nativo Kotlin creado (`app/`, Gradle y Manifest), `minSdk 29` (Android 10).
- WebView integrada que carga la interfaz desde `app/src/main/assets/index.html`.
- Puente `JRAndroid` Kotlin → JavaScript para reproducir, pausar, reanudar y detener.
- Reproductor nativo ExoPlayer/Media3 para URLs HTTPS de audio compatibles.
- Interfaz adaptable con tema oscuro y acento verde; pantallas Inicio, Buscar, Explorar, Biblioteca, Descargas y Ajustes.
- Diagnóstico de puente y pruebas de variables simuladas de mantenimiento, actualización y preparación.
- GitHub Actions configura la compilación de APK debug; el resultado de la primera compilación sigue pendiente de verificación.

La reproducción real de esta fase requiere una URL HTTPS directa de audio compatible. El catálogo de canciones de ejemplo continúa siendo ficticio y no se presenta como una biblioteca conectada a servicios musicales.

## Ejecutar el prototipo

Abre `index.html` en un navegador moderno. No requiere dependencias ni servidor.

## Descargador yt-dlp (núcleo inicial disponible)

El módulo `downloader/` contiene un worker Python que analiza una URL, descarga el mejor flujo de audio disponible, convierte a MP3 con FFmpeg, intenta incrustar portada/metadatos y guarda subtítulos VTT manuales o automáticos cuando existen. También genera un JSON normalizado para la app. Consulta `downloader/README.md` para instalarlo y ejecutarlo.

El worker Python todavía no está integrado en Android: requiere un entorno Python y binarios FFmpeg/FFprobe compatibles con Android, además de la integración Kotlin para cola, progreso, cancelación y almacenamiento. La reproducción por URL directa ya tiene una primera integración nativa, pero aún faltan servicio multimedia en segundo plano, selección de archivos locales, letras sincronizadas, visualizador 3D y conexión de búsquedas online. El panel Render mantiene una integración separada en la interfaz.

## Arquitectura objetivo

- Kotlin + Android WebView para la aplicación.
- HTML, CSS y JavaScript para la interfaz.
- AndroidX Media3 para reproducción y servicio en segundo plano.
- Almacenamiento local para biblioteca y descargas.
- Integración controlada de yt-dlp y servicios online compatibles.
- API de JR Audio Control para estado y avisos de actualización.

Las funciones online dependerán de la disponibilidad y las condiciones de cada fuente. No se eluden DRM, autenticación ni restricciones de acceso.
