"""Ayudantes de prueba: clasificador falso (sin ultralytics) e imágenes generadas en memoria."""

from __future__ import annotations

import base64
import io
from typing import Sequence

from fastapi.testclient import TestClient
from PIL import Image

from service.app import create_app
from service.classifier import Prediction
from service.config import Settings


class FakeClassifier:
    def __init__(self, predictions: Sequence[Prediction], version: str = "angle-test-1", error: Exception | None = None):
        self._predictions = list(predictions)
        self._version = version
        self._error = error
        self.seen_modes: list[str] = []

    @property
    def model_version(self) -> str:
        return self._version

    def labels(self) -> Sequence[str]:
        return tuple(prediction.slot for prediction in self._predictions)

    def predict(self, image: Image.Image) -> Sequence[Prediction]:
        self.seen_modes.append(image.mode)
        if self._error is not None:
            raise self._error
        return list(self._predictions)


def image_bytes(image_format: str = "JPEG", size: tuple[int, int] = (64, 48), mode: str = "RGB") -> bytes:
    color = (200, 30, 30, 128) if mode == "RGBA" else (200, 30, 30)
    buffer = io.BytesIO()
    Image.new(mode, size, color).save(buffer, format=image_format)
    return buffer.getvalue()


def as_base64(raw: bytes) -> str:
    return base64.b64encode(raw).decode("ascii")


def request_body(raw: bytes | None = None, mime_type: str = "image/jpeg", expected_slot: str = "FRONT") -> dict:
    data = image_bytes() if raw is None else raw
    return {"expectedSlot": expected_slot, "mimeType": mime_type, "imageBase64": as_base64(data)}


DEFAULT_PREDICTIONS = [
    Prediction("FRONT", 0.93),
    Prediction("FRONT_LEFT", 0.05),
    Prediction("OTHER", 0.02),
]


def client_for(classifier=None, settings: Settings | None = None, **kwargs) -> TestClient:
    return TestClient(create_app(settings or Settings(), classifier=classifier, **kwargs))
