"""Punto de entrada de uvicorn: uvicorn service.main:app --host 0.0.0.0 --port 8080 --workers 1."""

import logging
import os

from service.app import create_app
from service.classifier import load_yolo
from service.config import Settings

logging.basicConfig(
    level=os.environ.get("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)

app = create_app(Settings.from_env(), loader=load_yolo)
