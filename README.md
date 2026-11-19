# JR Audio — Android

Proyecto de la nueva aplicación JR Audio. La primera entrega contiene el prototipo navegable de interfaz en HTML, preparado para integrarse en una aplicación Android con Kotlin y WebView.

## Estado actual

- Interfaz adaptable con tema oscuro y acento verde.
- Pantallas de Inicio, Buscar, Explorar, Biblioteca, Descargas y Ajustes.
- Reproductor compacto y vista ampliada.
- Búsqueda y catálogo de demostración en JavaScript.
- Estructura visual preparada para conectar posteriormente el motor de audio y los servicios Android.

## Ejecutar el prototipo

Abre `index.html` en un navegador moderno. No requiere dependencias ni servidor.

## Descargador yt-dlp (núcleo inicial disponible)

El módulo `downloader/` contiene un worker Python que analiza una URL, descarga el mejor flujo de audio disponible, convierte a MP3 con FFmpeg, intenta incrustar portada/metadatos y guarda subtítulos VTT manuales o automáticos cuando existen. También genera un JSON normalizado para la app. Consulta `downloader/README.md` para instalarlo y ejecutarlo.

Este motor todavía no está conectado a una APK: requiere un entorno Python y binarios FFmpeg/FFprobe compatibles con Android, además de la integración Kotlin para cola, progreso, cancelación y almacenamiento. Tampoco están conectados la reproducción real, letras sincronizadas, visualizador 3D ni el panel Render. Las canciones de la interfaz siguen siendo datos ficticios.

## Arquitectura objetivo

- Kotlin + Android WebView para la aplicación.
- HTML, CSS y JavaScript para la interfaz.
- AndroidX Media3 para reproducción y servicio en segundo plano.
- Almacenamiento local para biblioteca y descargas.
- Integración controlada de yt-dlp y servicios online compatibles.
- API de JR Audio Control para estado y avisos de actualización.

Las funciones online dependerán de la disponibilidad y las condiciones de cada fuente. No se eluden DRM, autenticación ni restricciones de acceso.
