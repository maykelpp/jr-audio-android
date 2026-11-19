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

## Pendiente de integración

Esta versión todavía no es una APK y no incluye todavía el proyecto Android Gradle/Kotlin. Tampoco están conectados yt-dlp, reproducción real, descargas, letras sincronizadas, visualizador de audio 3D, almacenamiento local ni el panel de control de Render. Las canciones mostradas son datos ficticios de demostración.

## Arquitectura objetivo

- Kotlin + Android WebView para la aplicación.
- HTML, CSS y JavaScript para la interfaz.
- AndroidX Media3 para reproducción y servicio en segundo plano.
- Almacenamiento local para biblioteca y descargas.
- Integración controlada de yt-dlp y servicios online compatibles.
- API de JR Audio Control para estado y avisos de actualización.

Las funciones online dependerán de la disponibilidad y las condiciones de cada fuente. No se eluden DRM, autenticación ni restricciones de acceso.
