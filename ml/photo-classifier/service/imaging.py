"""Decodificación segura de la imagen recibida.

Solo JPEG, PNG y WebP. Se lee primero el encabezado y se rechaza una imagen con más
píxeles que el límite antes de decodificarla (bomba de descompresión). La imagen se
mantiene en memoria y nunca se guarda ni se registra.
"""

from __future__ import annotations

import base64
import binascii
import io

from PIL import Image, UnidentifiedImageError

MIME_TO_FORMAT = {
    "image/jpeg": "JPEG",
    "image/png": "PNG",
    "image/webp": "WEBP",
}


class ImageRejected(Exception):
    """La imagen no se puede clasificar. status es el código HTTP que espera el adaptador Java.

    400: base64 inválido o imagen dañada; 415: tipo no admitido o bytes que no son del tipo declarado;
    422: imagen demasiado grande en píxeles. El backend trata los tres como PHOTO_CLASSIFIER_IMAGE_UNREADABLE.
    """

    def __init__(self, status: int, code: str) -> None:
        super().__init__(code)
        self.status = status
        self.code = code


def decode_base64(value: str, max_bytes: int) -> bytes:
    """Decodifica el base64 estricto (sin caracteres ajenos al alfabeto)."""
    if not value:
        raise ImageRejected(400, "IMAGE_EMPTY")
    # Cota previa: el base64 ocupa 4 caracteres por cada 3 bytes.
    if len(value) > (max_bytes // 3 + 1) * 4:
        raise ImageRejected(422, "IMAGE_TOO_LARGE")
    try:
        raw = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as error:
        raise ImageRejected(400, "IMAGE_BASE64_INVALID") from error
    if not raw:
        raise ImageRejected(400, "IMAGE_EMPTY")
    return raw


def decode_image(raw: bytes, mime_type: str, max_pixels: int) -> Image.Image:
    """Devuelve la imagen en RGB, completamente cargada en memoria."""
    image_format = MIME_TO_FORMAT.get(mime_type.strip().lower())
    if image_format is None:
        raise ImageRejected(415, "IMAGE_TYPE_NOT_SUPPORTED")
    # El límite propio de píxeles se comprueba antes de decodificar; no se usa warnings.catch_warnings
    # porque no es seguro entre hilos y la inferencia corre en el pool de hilos.
    try:
        with Image.open(io.BytesIO(raw), formats=[image_format]) as image:
            width, height = image.size
            if width < 1 or height < 1:
                raise ImageRejected(400, "IMAGE_UNREADABLE")
            if width * height > max_pixels:
                raise ImageRejected(422, "IMAGE_TOO_LARGE")
            image.load()
            return image.convert("RGB")
    except ImageRejected:
        raise
    except Image.DecompressionBombError as error:
        raise ImageRejected(422, "IMAGE_TOO_LARGE") from error
    except UnidentifiedImageError as error:
        raise ImageRejected(415, "IMAGE_TYPE_NOT_SUPPORTED") from error
    except (OSError, ValueError, SyntaxError, EOFError) as error:
        raise ImageRejected(400, "IMAGE_UNREADABLE") from error
