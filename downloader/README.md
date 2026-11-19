# JR Audio Downloader Core

Motor de descarga de audio para contenido al que el usuario tenga derecho de acceso y descarga. Usa yt-dlp para extraer información y FFmpeg para convertir el audio a MP3.

## Requisitos

- Python 3.10 o posterior.
- FFmpeg y FFprobe instalados y disponibles en PATH.
- Dependencias: `python -m pip install -r requirements.txt`.

## Uso

```bash
python yt_dlp_worker.py \
  --url "https://example.com/media" \
  --output "./JR-Audio" \
  --quality 192 \
  --languages "es,es-419,en"
```

El proceso imprime eventos NDJSON por stdout. Cada línea es un objeto JSON independiente.

### Eventos

- `started`: comienza el análisis.
- `progress`: descarga o conversión, con porcentaje cuando la fuente informa tamaño.
- `completed`: resultado con la ruta MP3, metadatos, portada y subtítulos encontrados.
- `error`: error con código y mensaje.

## Archivos generados

- `*.mp3`: audio convertido; se intenta incrustar metadatos y portada.
- `*.info.json`: información de yt-dlp.
- `*.metadata.json`: metadatos normalizados para JR Audio.
- `*.vtt`: subtítulos manuales o automáticos si la fuente los ofrece.
- Imagen de portada descargada para incrustarla en el MP3 y conservar una copia.

## Integración Android

Este archivo es el núcleo Python y todavía no está conectado a una APK. Android deberá:
1. Ejecutar el worker en un entorno Python compatible o en un proceso nativo empaquetado.
2. Leer stdout línea a línea y convertir cada evento NDJSON en actualizaciones de la cola.
3. Guardar el resultado en almacenamiento de la app y publicar el MP3 mediante MediaStore/SAF.
4. Gestionar cancelación, reintentos y permisos desde Kotlin.

No se debe ejecutar el proceso en el hilo principal de Android. Para Android ARM64 hace falta empaquetar también un FFmpeg/FFprobe compatible con Android; un FFmpeg de Linux/Termux no sirve como binario dentro de la APK.

## Limitaciones y uso responsable

No elude DRM, autenticación, paywalls ni controles de acceso. La descarga depende de la fuente, del extractor de yt-dlp y de sus condiciones. Los subtítulos no están garantizados: se guardan únicamente cuando existen en alguno de los idiomas solicitados. El usuario debe contar con permiso o derechos para guardar el contenido.
