from pathlib import Path

import pytest

from service.classifier import Prediction, model_version_for, rank
from service.config import DEFAULT_MAX_BODY_BYTES, Settings
from service.labels import KNOWN_LABELS, SLOTS, normalize_label


def test_labels_match_the_backend_contract():
    assert len(SLOTS) == 14
    assert set(KNOWN_LABELS) == {
        "FRONT", "REAR", "LEFT", "RIGHT", "FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT",
        "INTERIOR_FRONT", "INTERIOR_REAR", "DASHBOARD_ODOMETER", "ENGINE", "TIRES", "VIN", "OTHER",
    }


def test_normalize_label():
    assert normalize_label(" front_left ") == "FRONT_LEFT"
    assert normalize_label("CAT") is None
    assert normalize_label(3) is None


def test_settings_from_env_reads_every_value():
    settings = Settings.from_env({
        "MODEL_PATH": "/models/model.onnx",
        "MODEL_VERSION": "angle-yolo11n-cls-2027.01",
        "CLASSIFIER_TOKEN": " unit-test ",
        "MAX_BODY_BYTES": "4096",
        "MAX_IMAGE_PIXELS": "1000000",
        "TOP_K": "3",
        "IMAGE_SIZE": "256",
        "DEVICE": "cpu",
    })

    assert settings.model_path == Path("/models/model.onnx")
    assert settings.model_version == "angle-yolo11n-cls-2027.01"
    assert settings.token == "unit-test"
    assert settings.max_body_bytes == 4096
    assert settings.max_image_pixels == 1_000_000
    assert settings.top_k == 3
    assert settings.image_size == 256


def test_settings_defaults_and_blank_values():
    settings = Settings.from_env({"CLASSIFIER_TOKEN": "  ", "MODEL_PATH": ""})

    assert settings.token is None
    assert settings.model_path is None
    assert settings.max_body_bytes == DEFAULT_MAX_BODY_BYTES
    assert settings.device == "cpu"


@pytest.mark.parametrize("name,value", [("TOP_K", "zero"), ("TOP_K", "0"), ("MAX_BODY_BYTES", "10")])
def test_settings_reject_invalid_numbers(name, value):
    with pytest.raises(ValueError):
        Settings.from_env({name: value})


def test_model_version_falls_back_to_the_file_name():
    assert model_version_for(Settings(model_path=Path("/models/angle-v3.onnx"))) == "angle-v3"
    assert model_version_for(Settings(model_version="x" * 70)) == "x" * 60
    assert model_version_for(Settings()) == "unknown"


def test_rank_keeps_the_best_confidence_of_a_repeated_label():
    ranked = rank([Prediction("front", 0.2), Prediction("FRONT", 0.7), Prediction("REAR", 0.1)], 5)

    assert ranked == [Prediction("FRONT", 0.7), Prediction("REAR", 0.1)]
