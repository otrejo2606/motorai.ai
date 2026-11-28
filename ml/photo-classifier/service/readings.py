"""Punto de extensión para leer el VIN y el odómetro de la foto (puerta 0.5 de add-clasificador-fotos-yolo).

YOLO clasifica la imagen completa; no lee texto. Mientras no exista un detector de la región más un OCR,
el servicio responde "vin": null y "odometerKm": null, y el backend no compara lecturas (design D6 de
add-captura-guiada-fotos).

Para agregar el OCR: implementar ReadingExtractor (por ejemplo, solo para las tomas VIN y
DASHBOARD_ODOMETER) y pasarlo a create_app(readings=...) en service/main.py. El backend ya normaliza el VIN
(letras y dígitos, 5 a 30) y descarta un odómetro fuera de 0 a 2 000 000 km.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from PIL import Image


@dataclass(frozen=True)
class Readings:
    vin: str | None = None
    odometer_km: int | None = None


class ReadingExtractor(Protocol):
    def extract(self, image: Image.Image, predicted_slot: str) -> Readings: ...


class NoReadings:
    """Sin OCR: nunca devuelve lecturas."""

    def extract(self, image: Image.Image, predicted_slot: str) -> Readings:
        return Readings()
