"""Clasificador del ángulo: interfaz, ordenamiento de predicciones y la implementación con Ultralytics YOLO.

Ultralytics (AGPL-3.0) se importa solo dentro de load_yolo, así las pruebas usan un clasificador
falso sin instalarlo. Ver LICENSE-NOTICE.md.
"""

from __future__ import annotations

import logging
import math
import threading
from dataclasses import dataclass
from typing import Protocol, Sequence

from PIL import Image

from service.config import MAX_MODEL_VERSION, Settings
from service.labels import normalize_label

logger = logging.getLogger("photo_classifier")


@dataclass(frozen=True)
class Prediction:
    slot: str
    confidence: float


class Classifier(Protocol):
    """Lo que el servicio necesita de un modelo de clasificación."""

    @property
    def model_version(self) -> str: ...

    def labels(self) -> Sequence[str]: ...

    def predict(self, image: Image.Image) -> Sequence[Prediction]: ...


class ModelNotUsable(Exception):
    """El modelo no se pudo cargar o no tiene ninguna etiqueta conocida."""


def rank(predictions: Sequence[Prediction], top_k: int) -> list[Prediction]:
    """Deja solo etiquetas conocidas con confianza finita entre 0 y 1, de mayor a menor, hasta top_k.

    Si el modelo repite una etiqueta (por ejemplo «front» y «FRONT»), queda la de mayor confianza.
    """
    best: dict[str, float] = {}
    for prediction in predictions:
        slot = normalize_label(prediction.slot)
        if slot is None:
            continue
        try:
            confidence = float(prediction.confidence)
        except (TypeError, ValueError):
            continue
        if math.isnan(confidence) or confidence < 0.0 or confidence > 1.0:
            continue
        if confidence > best.get(slot, -1.0):
            best[slot] = confidence
    ordered = sorted(best.items(), key=lambda item: (-item[1], item[0]))
    return [Prediction(slot=slot, confidence=round(confidence, 6)) for slot, confidence in ordered[:top_k]]


def model_version_for(settings: Settings) -> str:
    """MODEL_VERSION o, si falta, el nombre del archivo del modelo; hasta 60 caracteres como exige el backend."""
    if settings.model_version:
        version = settings.model_version
    elif settings.model_path is not None:
        version = settings.model_path.stem
    else:
        version = "unknown"
    return version[:MAX_MODEL_VERSION]


class YoloClassifier:
    """Clasificador de imagen completa de Ultralytics (por ejemplo yolo11n-cls), en .pt o exportado a .onnx."""

    def __init__(self, model: object, names: dict[int, str], version: str, image_size: int, device: str) -> None:
        self._model = model
        self._names = names
        self._version = version
        self._image_size = image_size
        self._device = device
        # El predictor de Ultralytics no es seguro entre hilos: una inferencia a la vez por proceso.
        self._lock = threading.Lock()

    @property
    def model_version(self) -> str:
        return self._version

    def labels(self) -> Sequence[str]:
        return tuple(self._names[index] for index in sorted(self._names))

    def predict(self, image: Image.Image) -> Sequence[Prediction]:
        with self._lock:
            results = self._model.predict(image, imgsz=self._image_size, device=self._device, verbose=False)
        probs = results[0].probs
        if probs is None:
            return []
        values = probs.data.tolist()
        return [
            Prediction(slot=self._names.get(index, ""), confidence=float(value))
            for index, value in enumerate(values)
        ]


def load_yolo(settings: Settings) -> YoloClassifier:
    """Carga el modelo una sola vez al arranque. Lanza ModelNotUsable si falta o no sirve."""
    if settings.model_path is None:
        raise ModelNotUsable("MODEL_PATH is not set")
    if not settings.model_path.is_file():
        raise ModelNotUsable("MODEL_PATH does not point to a file")
    from ultralytics import YOLO, settings as ultralytics_settings  # AGPL-3.0, ver LICENSE-NOTICE.md

    # Sin telemetría de Ultralytics: el servicio no habla con nadie fuera de la red privada.
    ultralytics_settings.update({"sync": False})
    model = YOLO(str(settings.model_path), task="classify")
    names = {int(index): str(name) for index, name in dict(model.names).items()}
    known = [name for name in names.values() if normalize_label(name) is not None]
    if not known:
        raise ModelNotUsable("The model has no known slot label")
    unknown = len(names) - len(known)
    if unknown:
        logger.warning("The model has %d labels outside the protocol; they are never returned", unknown)
    return YoloClassifier(model, names, model_version_for(settings), settings.image_size, settings.device)
