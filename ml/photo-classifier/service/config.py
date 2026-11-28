"""Configuración del servicio, leída una sola vez de las variables de entorno."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

DEFAULT_MAX_BODY_BYTES = 8 * 1024 * 1024
DEFAULT_MAX_IMAGE_PIXELS = 4096 * 4096
DEFAULT_TOP_K = 5
DEFAULT_IMAGE_SIZE = 224
MAX_MODEL_VERSION = 60


@dataclass(frozen=True)
class Settings:
    """Parámetros del servicio.

    model_path: ruta del modelo (.pt u .onnx) montado por volumen (MODEL_PATH).
    model_version: versión que viaja en cada respuesta (MODEL_VERSION); si falta, se usa el nombre del archivo.
    token: token Bearer opcional (CLASSIFIER_TOKEN); vacío significa sin autenticación (red privada).
    max_body_bytes: tamaño máximo del cuerpo JSON (MAX_BODY_BYTES).
    max_image_pixels: ancho por alto máximo de la imagen decodificada (MAX_IMAGE_PIXELS).
    top_k: cuántas predicciones se devuelven (TOP_K).
    image_size: lado de entrada del modelo (IMAGE_SIZE).
    device: dispositivo de inferencia de Ultralytics (DEVICE), por defecto cpu.
    """

    model_path: Path | None = None
    model_version: str | None = None
    token: str | None = None
    max_body_bytes: int = DEFAULT_MAX_BODY_BYTES
    max_image_pixels: int = DEFAULT_MAX_IMAGE_PIXELS
    top_k: int = DEFAULT_TOP_K
    image_size: int = DEFAULT_IMAGE_SIZE
    device: str = "cpu"

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> "Settings":
        source = os.environ if env is None else env
        model_path = _text(source, "MODEL_PATH")
        return cls(
            model_path=Path(model_path) if model_path else None,
            model_version=_text(source, "MODEL_VERSION"),
            token=_text(source, "CLASSIFIER_TOKEN"),
            max_body_bytes=_int(source, "MAX_BODY_BYTES", DEFAULT_MAX_BODY_BYTES, 1024, 64 * 1024 * 1024),
            max_image_pixels=_int(source, "MAX_IMAGE_PIXELS", DEFAULT_MAX_IMAGE_PIXELS, 1, 100_000_000),
            top_k=_int(source, "TOP_K", DEFAULT_TOP_K, 1, 15),
            image_size=_int(source, "IMAGE_SIZE", DEFAULT_IMAGE_SIZE, 32, 1024),
            device=_text(source, "DEVICE") or "cpu",
        )


def _text(source: Mapping[str, str], name: str) -> str | None:
    value = source.get(name)
    if value is None:
        return None
    value = value.strip()
    return value or None


def _int(source: Mapping[str, str], name: str, default: int, minimum: int, maximum: int) -> int:
    raw = _text(source, name)
    if raw is None:
        return default
    try:
        value = int(raw)
    except ValueError as error:
        raise ValueError(f"{name} must be an integer") from error
    if value < minimum or value > maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")
    return value
