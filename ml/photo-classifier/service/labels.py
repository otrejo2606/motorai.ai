"""Etiquetas del clasificador.

Son las 14 tomas del protocolo de fotos más OTHER, idénticas a
HttpPhotoAngleClassifierAdapter.KNOWN_SLOTS del backend. Cualquier otra etiqueta
del modelo se descarta antes de responder.
"""

EXTERIOR_SLOTS = (
    "FRONT",
    "REAR",
    "LEFT",
    "RIGHT",
    "FRONT_LEFT",
    "FRONT_RIGHT",
    "REAR_LEFT",
    "REAR_RIGHT",
)

DETAIL_SLOTS = (
    "INTERIOR_FRONT",
    "INTERIOR_REAR",
    "DASHBOARD_ODOMETER",
    "ENGINE",
    "TIRES",
    "VIN",
)

SLOTS = EXTERIOR_SLOTS + DETAIL_SLOTS

OTHER = "OTHER"

KNOWN_LABELS = SLOTS + (OTHER,)

KNOWN_LABEL_SET = frozenset(KNOWN_LABELS)


def normalize_label(label: object) -> str | None:
    """Devuelve la etiqueta en mayúsculas si es una de las 15 conocidas; si no, None."""
    if not isinstance(label, str):
        return None
    value = label.strip().upper()
    return value if value in KNOWN_LABEL_SET else None
