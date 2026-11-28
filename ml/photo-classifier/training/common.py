"""Utilidades compartidas por los scripts de entrenamiento."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

IMAGE_SUFFIXES = frozenset({".jpg", ".jpeg", ".png", ".webp"})

# Semilla fija por defecto para que la división y el entrenamiento sean reproducibles.
DEFAULT_SEED = 20261127


def disable_ultralytics_telemetry() -> None:
    """Apaga el envío de eventos de Ultralytics: ningún dato del entrenamiento sale de la máquina."""
    from ultralytics import settings  # AGPL-3.0, ver LICENSE-NOTICE.md

    settings.update({"sync": False})


def ultralytics_version() -> str:
    import ultralytics

    return str(getattr(ultralytics, "__version__", "unknown"))


def write_json(path: Path, content: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(content, handle, indent=2, ensure_ascii=False, default=str)


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()
