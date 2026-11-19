#!/usr/bin/env python3
"""JR Audio audio downloader core.

Downloads one publicly accessible media item with yt-dlp, extracts MP3 through
FFmpeg, and stores metadata, thumbnail and available subtitle tracks.
Progress and result events are emitted as newline-delimited JSON (NDJSON).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from yt_dlp import YoutubeDL


def emit(event: dict[str, Any]) -> None:
    print(json.dumps(event, ensure_ascii=False, separators=(",", ":")), flush=True)


def valid_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme in ("http", "https") and bool(parsed.netloc)


def safe_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).replace("\r", " ").replace("\n", " ").strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--quality", choices=("128", "192", "256", "320"), default="192")
    parser.add_argument("--languages", default="es,es-419,en")
    args = parser.parse_args()

    if not valid_url(args.url):
        emit({"event": "error", "code": "INVALID_URL", "message": "Solo se aceptan URL HTTP o HTTPS."})
        return 2

    output_dir = Path(args.output).expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    languages = [item.strip() for item in args.languages.split(",") if item.strip()]
    if not languages:
        languages = ["es", "en"]

    def progress_hook(data: dict[str, Any]) -> None:
        status = data.get("status")
        if status == "downloading":
            total = data.get("total_bytes") or data.get("total_bytes_estimate")
            downloaded = data.get("downloaded_bytes") or 0
            percent = round(downloaded * 100 / total, 1) if total else None
            emit({
                "event": "progress",
                "status": "downloading",
                "percent": percent,
                "downloaded_bytes": downloaded,
                "total_bytes": total,
                "speed": data.get("speed"),
                "eta": data.get("eta"),
            })
        elif status == "finished":
            emit({"event": "progress", "status": "processing", "message": "Procesando audio y metadatos."})

    def postprocessor_hook(data: dict[str, Any]) -> None:
        if data.get("status") == "started":
            emit({"event": "progress", "status": "processing", "message": safe_text(data.get("postprocessor"))})

    options: dict[str, Any] = {
        "format": "bestaudio/best",
        "noplaylist": True,
        "outtmpl": str(output_dir / "%(title).180B [%(id)s].%(ext)s"),
        "windowsfilenames": True,
        "continuedl": True,
        "overwrites": False,
        "writethumbnail": True,
        "writeinfojson": True,
        "writesubtitles": True,
        "writeautomaticsub": True,
        "subtitleslangs": languages,
        "subtitlesformat": "vtt/best",
        "ignoreerrors": False,
        "quiet": True,
        "no_warnings": True,
        "progress_hooks": [progress_hook],
        "postprocessor_hooks": [postprocessor_hook],
        "postprocessors": [
            {"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": args.quality},
            {"key": "FFmpegMetadata"},
            {"key": "EmbedThumbnail"},
        ],
    }

    emit({"event": "started", "message": "Analizando la fuente.", "output": str(output_dir)})
    try:
        with YoutubeDL(options) as ydl:
            info = ydl.extract_info(args.url, download=True)
            if not info:
                raise RuntimeError("La fuente no devolvió información del contenido.")

            prepared = Path(ydl.prepare_filename(info))
            mp3_path = prepared.with_suffix(".mp3")
            if not mp3_path.exists():
                # Some extractors adjust the final filename after metadata sanitization.
                candidates = sorted(output_dir.glob("*[[]*[]].mp3"), key=lambda p: p.stat().st_mtime, reverse=True)
                if candidates:
                    mp3_path = candidates[0]
            if not mp3_path.exists():
                raise RuntimeError("yt-dlp terminó, pero no se encontró el MP3. Comprueba que FFmpeg esté instalado.")

            subtitles = []
            stem = mp3_path.stem
            for candidate in output_dir.glob(stem + "*.vtt"):
                subtitles.append({"path": str(candidate), "language_file": candidate.name})

            thumbnail_candidates = [
                p for p in output_dir.glob(stem + ".*")
                if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp")
            ]
            metadata = {
                "id": safe_text(info.get("id")),
                "title": safe_text(info.get("title")),
                "artist": safe_text(info.get("artist") or info.get("creator") or info.get("uploader")),
                "album": safe_text(info.get("album")),
                "album_artist": safe_text(info.get("album_artist")),
                "track": info.get("track_number"),
                "duration_seconds": info.get("duration"),
                "source_url": safe_text(info.get("webpage_url") or args.url),
                "extractor": safe_text(info.get("extractor_key")),
                "upload_date": safe_text(info.get("upload_date")),
                "description": safe_text(info.get("description")),
                "audio_file": str(mp3_path),
                "thumbnail_file": str(thumbnail_candidates[0]) if thumbnail_candidates else None,
                "subtitles": subtitles,
                "subtitle_note": None if subtitles else "La fuente no proporcionó subtítulos en los idiomas solicitados.",
                "audio_quality_kbps": args.quality,
            }
            sidecar = mp3_path.with_suffix(".metadata.json")
            sidecar.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
            emit({"event": "completed", "result": metadata})
            return 0
    except Exception as exc:
        message = safe_text(exc) or "Error desconocido durante la descarga."
        code = "FFMPEG_MISSING" if re.search(r"ffmpeg|ffprobe", message, re.I) else "DOWNLOAD_FAILED"
        emit({"event": "error", "code": code, "message": message})
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
