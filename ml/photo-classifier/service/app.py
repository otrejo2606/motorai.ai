"""API HTTP del servicio de inferencia (contrato de design D3 de add-captura-guiada-fotos).

POST /v1/classify
  solicitud: {"expectedSlot": "FRONT", "mimeType": "image/jpeg", "imageBase64": "..."}
  respuesta 200: {"modelVersion": "...", "predictions": [{"slot": "FRONT", "confidence": 0.93}],
                  "vin": null, "odometerKm": null}
  errores (lo que espera HttpPhotoAngleClassifierAdapter):
    400, 415, 422  imagen ilegible o solicitud mal formada  -> PHOTO_CLASSIFIER_IMAGE_UNREADABLE
    401            falta el token o no coincide             -> PHOTO_CLASSIFIER_NOT_CONFIGURED
    413, 500, 503  cuerpo excesivo, falla o sin modelo      -> PHOTO_CLASSIFIER_UNAVAILABLE
GET /health
  200 con el modelo cargado y su versión; 503 si no hay modelo.

Nunca se guarda ni se registra la imagen, el base64 ni la toma enviada por el cliente.
"""

from __future__ import annotations

import hmac
import json
import logging
import time
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator, Callable

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from service.classifier import Classifier, rank
from service.config import MAX_MODEL_VERSION, Settings
from service.imaging import ImageRejected, decode_base64, decode_image
from service.readings import NoReadings, ReadingExtractor

logger = logging.getLogger("photo_classifier")

MAX_SLOT_LENGTH = 40
MAX_MIME_LENGTH = 100

Loader = Callable[[Settings], Classifier]


class ApiError(Exception):
    def __init__(self, status: int, code: str) -> None:
        super().__init__(code)
        self.status = status
        self.code = code


def create_app(
    settings: Settings,
    classifier: Classifier | None = None,
    loader: Loader | None = None,
    readings: ReadingExtractor | None = None,
) -> FastAPI:
    """Arma la aplicación.

    classifier: un clasificador ya cargado (las pruebas pasan uno falso).
    loader: función que carga el modelo al arranque (service.classifier.load_yolo en producción).
    Si la carga falla, el servicio arranca igual: /health responde 503 y /v1/classify 503.
    """
    reading_extractor: ReadingExtractor = readings if readings is not None else NoReadings()

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        if application.state.classifier is None and loader is not None:
            try:
                application.state.classifier = loader(settings)
                logger.info("Model loaded, version %s", application.state.classifier.model_version)
            except Exception as error:  # noqa: BLE001 - el servicio debe arrancar y decirlo en /health
                logger.error("The model could not be loaded: %s", type(error).__name__)
        yield

    application = FastAPI(
        title="Motoria photo classifier",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
        lifespan=lifespan,
    )
    application.state.classifier = classifier

    @application.exception_handler(ApiError)
    async def api_error_handler(request: Request, error: ApiError) -> JSONResponse:
        headers = {"WWW-Authenticate": "Bearer"} if error.status == 401 else None
        return JSONResponse(status_code=error.status, content={"code": error.code}, headers=headers)

    @application.get("/health")
    async def health() -> JSONResponse:
        current: Classifier | None = application.state.classifier
        if current is None:
            return JSONResponse(
                status_code=503,
                content={"status": "DOWN", "modelLoaded": False, "modelVersion": None},
            )
        return JSONResponse(
            status_code=200,
            content={"status": "UP", "modelLoaded": True, "modelVersion": current.model_version[:MAX_MODEL_VERSION]},
        )

    @application.post("/v1/classify")
    async def classify(request: Request) -> JSONResponse:
        started = time.perf_counter()
        _check_token(request, settings.token)
        current: Classifier | None = application.state.classifier
        if current is None:
            raise ApiError(503, "MODEL_NOT_LOADED")
        body = await _read_body(request, settings.max_body_bytes)
        mime_type, image_base64 = _parse_request(body)
        try:
            content = await run_in_threadpool(
                _classify_sync, current, reading_extractor, settings, mime_type, image_base64
            )
        except ImageRejected as rejected:
            raise ApiError(rejected.status, rejected.code) from rejected
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        logger.info("Classified in %d ms", elapsed_ms)
        return JSONResponse(status_code=200, content=content)

    return application


def _check_token(request: Request, expected: str | None) -> None:
    if not expected:
        return
    header = request.headers.get("authorization", "")
    scheme, _, given = header.partition(" ")
    if scheme.lower() != "bearer":
        raise ApiError(401, "UNAUTHORIZED")
    if not hmac.compare_digest(given.strip().encode("utf-8"), expected.encode("utf-8")):
        raise ApiError(401, "UNAUTHORIZED")


async def _read_body(request: Request, limit: int) -> bytes:
    declared = request.headers.get("content-length")
    if declared is not None:
        try:
            declared_size = int(declared)
        except ValueError as error:
            raise ApiError(400, "BAD_REQUEST") from error
        if declared_size > limit:
            raise ApiError(413, "BODY_TOO_LARGE")
    chunks: list[bytes] = []
    size = 0
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            raise ApiError(413, "BODY_TOO_LARGE")
        chunks.append(chunk)
    return b"".join(chunks)


def _parse_request(body: bytes) -> tuple[str, str]:
    """Valida la forma del JSON. expectedSlot es solo contexto: se exige texto pero el modelo no lo usa."""
    try:
        payload: Any = json.loads(body)
    except (ValueError, UnicodeDecodeError, RecursionError) as error:
        raise ApiError(400, "BAD_REQUEST") from error
    if not isinstance(payload, dict):
        raise ApiError(400, "BAD_REQUEST")
    expected_slot = payload.get("expectedSlot")
    mime_type = payload.get("mimeType")
    image_base64 = payload.get("imageBase64")
    if not isinstance(expected_slot, str) or len(expected_slot) > MAX_SLOT_LENGTH:
        raise ApiError(400, "BAD_REQUEST")
    if not isinstance(mime_type, str) or not mime_type or len(mime_type) > MAX_MIME_LENGTH:
        raise ApiError(400, "BAD_REQUEST")
    if not isinstance(image_base64, str):
        raise ApiError(400, "BAD_REQUEST")
    return mime_type, image_base64


def _classify_sync(
    current: Classifier,
    reading_extractor: ReadingExtractor,
    settings: Settings,
    mime_type: str,
    image_base64: str,
) -> dict[str, Any]:
    raw = decode_base64(image_base64, settings.max_body_bytes)
    image = decode_image(raw, mime_type, settings.max_image_pixels)
    try:
        try:
            ranked = rank(current.predict(image), settings.top_k)
        except Exception as error:  # noqa: BLE001 - nunca se registra la imagen, solo el tipo de error
            logger.error("Inference failed: %s", type(error).__name__)
            raise ApiError(500, "INFERENCE_FAILED") from error
        if not ranked:
            logger.error("The model returned no known slot label")
            raise ApiError(500, "NO_KNOWN_LABEL")
        try:
            found = reading_extractor.extract(image, ranked[0].slot)
            vin = found.vin if isinstance(found.vin, str) else None
            odometer = found.odometer_km
            odometer_km = odometer if isinstance(odometer, int) and not isinstance(odometer, bool) else None
        except Exception as error:  # noqa: BLE001 - una falla del OCR no debe perder la clasificación
            logger.warning("Reading extraction failed: %s", type(error).__name__)
            vin = None
            odometer_km = None
    finally:
        image.close()
    return {
        "modelVersion": current.model_version[:MAX_MODEL_VERSION],
        "predictions": [{"slot": item.slot, "confidence": item.confidence} for item in ranked],
        "vin": vin,
        "odometerKm": odometer_km,
    }
