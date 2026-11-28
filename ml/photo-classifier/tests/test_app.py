"""Contrato HTTP del servicio con HttpPhotoAngleClassifierAdapter del backend (design D3 de add-captura-guiada-fotos)."""

from __future__ import annotations

import json
import math

from service.classifier import ModelNotUsable, Prediction
from service.config import Settings
from service.readings import Readings
from tests.helpers import FakeClassifier, as_base64, client_for, image_bytes, request_body

# Valor de prueba de baja entropía, no es un secreto.
TEST_TOKEN = "unit-test"


def test_classify_returns_the_exact_contract(fake):
    response = client_for(fake).post("/v1/classify", json=request_body())

    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"modelVersion", "predictions", "vin", "odometerKm"}
    assert body["modelVersion"] == "angle-test-1"
    assert body["predictions"] == [
        {"slot": "FRONT", "confidence": 0.93},
        {"slot": "FRONT_LEFT", "confidence": 0.05},
        {"slot": "OTHER", "confidence": 0.02},
    ]
    assert body["vin"] is None
    assert body["odometerKm"] is None


def test_predictions_are_sorted_and_limited_to_top_k():
    classifier = FakeClassifier([
        Prediction("REAR", 0.10),
        Prediction("FRONT", 0.60),
        Prediction("LEFT", 0.20),
        Prediction("RIGHT", 0.10),
    ])

    response = client_for(classifier, Settings(top_k=2)).post("/v1/classify", json=request_body())

    assert response.status_code == 200
    assert [item["slot"] for item in response.json()["predictions"]] == ["FRONT", "LEFT"]


def test_unknown_labels_and_invalid_confidences_are_filtered():
    classifier = FakeClassifier([
        Prediction("CAT", 0.70),
        Prediction("FRONT_LEFTX", 0.20),
        Prediction("front", 0.05),
        Prediction("REAR", 1.5),
        Prediction("LEFT", -0.1),
        Prediction("RIGHT", math.nan),
        Prediction("VIN", 0.01),
    ])

    response = client_for(classifier).post("/v1/classify", json=request_body())

    assert response.status_code == 200
    assert response.json()["predictions"] == [
        {"slot": "FRONT", "confidence": 0.05},
        {"slot": "VIN", "confidence": 0.01},
    ]


def test_a_model_without_any_known_label_answers_500():
    classifier = FakeClassifier([Prediction("CAT", 0.99)])

    response = client_for(classifier).post("/v1/classify", json=request_body())

    assert response.status_code == 500
    assert response.json() == {"code": "NO_KNOWN_LABEL"}


def test_model_version_is_cut_to_60_characters():
    classifier = FakeClassifier([Prediction("FRONT", 0.9)], version="v" * 80)

    response = client_for(classifier).post("/v1/classify", json=request_body())

    assert response.json()["modelVersion"] == "v" * 60


def test_png_with_alpha_and_webp_are_accepted_and_sent_as_rgb(fake):
    client = client_for(fake)

    png = client.post("/v1/classify", json=request_body(image_bytes("PNG", mode="RGBA"), "image/png"))
    webp = client.post("/v1/classify", json=request_body(image_bytes("WEBP"), "image/webp"))

    assert png.status_code == 200
    assert webp.status_code == 200
    assert fake.seen_modes == ["RGB", "RGB"]


def test_unsupported_mime_type_answers_415(fake):
    response = client_for(fake).post("/v1/classify", json=request_body(image_bytes("GIF"), "image/gif"))

    assert response.status_code == 415
    assert fake.seen_modes == []


def test_bytes_that_are_not_an_image_answer_415(fake):
    response = client_for(fake).post("/v1/classify", json=request_body(b"this is not a picture at all"))

    assert response.status_code == 415
    assert fake.seen_modes == []


def test_a_png_declared_as_jpeg_is_rejected(fake):
    response = client_for(fake).post("/v1/classify", json=request_body(image_bytes("PNG"), "image/jpeg"))

    assert response.status_code == 415


def test_a_truncated_jpeg_is_rejected(fake):
    raw = image_bytes("JPEG", size=(256, 256))

    response = client_for(fake).post("/v1/classify", json=request_body(raw[: len(raw) // 2]))

    assert response.status_code == 400
    assert fake.seen_modes == []


def test_invalid_base64_answers_400(fake):
    body = {"expectedSlot": "FRONT", "mimeType": "image/jpeg", "imageBase64": "@@not-base64@@"}

    response = client_for(fake).post("/v1/classify", json=body)

    assert response.status_code == 400


def test_empty_image_answers_400(fake):
    body = {"expectedSlot": "FRONT", "mimeType": "image/jpeg", "imageBase64": ""}

    response = client_for(fake).post("/v1/classify", json=body)

    assert response.status_code == 400


def test_image_over_the_pixel_limit_answers_422(fake):
    settings = Settings(max_image_pixels=100)

    response = client_for(fake, settings).post("/v1/classify", json=request_body(image_bytes(size=(20, 20))))

    assert response.status_code == 422
    assert fake.seen_modes == []


def test_malformed_requests_answer_400(fake):
    client = client_for(fake)
    image = as_base64(image_bytes())

    not_json = client.post("/v1/classify", content=b"{not json", headers={"Content-Type": "application/json"})
    not_object = client.post("/v1/classify", json=[1, 2, 3])
    no_mime = client.post("/v1/classify", json={"expectedSlot": "FRONT", "imageBase64": image})
    no_image = client.post("/v1/classify", json={"expectedSlot": "FRONT", "mimeType": "image/jpeg"})
    no_slot = client.post("/v1/classify", json={"mimeType": "image/jpeg", "imageBase64": image})

    for response in (not_json, not_object, no_mime, no_image, no_slot):
        assert response.status_code == 400


def test_body_over_the_limit_answers_413(fake):
    settings = Settings(max_body_bytes=2048)
    body = request_body(image_bytes(size=(64, 48)))
    body["imageBase64"] = body["imageBase64"] + "A" * 4096

    response = client_for(fake, settings).post("/v1/classify", json=body)

    assert response.status_code == 413
    assert response.json() == {"code": "BODY_TOO_LARGE"}
    assert fake.seen_modes == []


def test_body_over_the_limit_without_content_length_answers_413(fake):
    settings = Settings(max_body_bytes=2048)
    payload = json.dumps({"expectedSlot": "FRONT", "mimeType": "image/jpeg", "imageBase64": "A" * 4096})

    def chunks():
        yield payload.encode("utf-8")

    response = client_for(fake, settings).post(
        "/v1/classify", content=chunks(), headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 413


def test_token_is_required_when_configured(fake):
    client = client_for(fake, Settings(token=TEST_TOKEN))

    missing = client.post("/v1/classify", json=request_body())
    wrong = client.post("/v1/classify", json=request_body(), headers={"Authorization": "Bearer other"})
    basic = client.post("/v1/classify", json=request_body(), headers={"Authorization": "Basic " + TEST_TOKEN})
    right = client.post("/v1/classify", json=request_body(), headers={"Authorization": "Bearer " + TEST_TOKEN})

    assert missing.status_code == 401
    assert missing.headers.get("www-authenticate") == "Bearer"
    assert wrong.status_code == 401
    assert basic.status_code == 401
    assert right.status_code == 200


def test_token_is_checked_before_the_model_and_the_body():
    client = client_for(None, Settings(token=TEST_TOKEN, max_body_bytes=1024))

    response = client.post("/v1/classify", json={"imageBase64": "A" * 4096})

    assert response.status_code == 401


def test_without_token_configured_no_header_is_needed(fake):
    response = client_for(fake).post("/v1/classify", json=request_body())

    assert response.status_code == 200


def test_without_model_classify_answers_503_and_health_says_so():
    client = client_for(None)

    classify = client.post("/v1/classify", json=request_body())
    health = client.get("/health")

    assert classify.status_code == 503
    assert classify.json() == {"code": "MODEL_NOT_LOADED"}
    assert health.status_code == 503
    assert health.json() == {"status": "DOWN", "modelLoaded": False, "modelVersion": None}


def test_health_reports_the_loaded_model(fake):
    response = client_for(fake).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "modelLoaded": True, "modelVersion": "angle-test-1"}


def test_the_loader_runs_once_at_startup(fake):
    calls = []

    def loader(settings):
        calls.append(settings)
        return fake

    with client_for(None, loader=loader) as client:
        health = client.get("/health")
        classify = client.post("/v1/classify", json=request_body())

    assert len(calls) == 1
    assert health.status_code == 200
    assert classify.status_code == 200


def test_a_failing_loader_leaves_the_service_up_without_model():
    def loader(settings):
        raise ModelNotUsable("MODEL_PATH is not set")

    with client_for(None, loader=loader) as client:
        health = client.get("/health")
        classify = client.post("/v1/classify", json=request_body())

    assert health.status_code == 503
    assert classify.status_code == 503


def test_an_inference_failure_answers_500_without_details():
    classifier = FakeClassifier([], error=RuntimeError("secret detail"))

    response = client_for(classifier).post("/v1/classify", json=request_body())

    assert response.status_code == 500
    assert response.json() == {"code": "INFERENCE_FAILED"}


def test_readings_extension_point_fills_vin_and_odometer(fake):
    class FakeReader:
        def extract(self, image, predicted_slot):
            return Readings(vin="1HGCM82633A004352", odometer_km=45210)

    response = client_for(fake, readings=FakeReader()).post("/v1/classify", json=request_body())

    assert response.status_code == 200
    assert response.json()["vin"] == "1HGCM82633A004352"
    assert response.json()["odometerKm"] == 45210


def test_a_failing_reader_keeps_the_classification(fake):
    class BrokenReader:
        def extract(self, image, predicted_slot):
            raise RuntimeError("ocr down")

    response = client_for(fake, readings=BrokenReader()).post("/v1/classify", json=request_body())

    assert response.status_code == 200
    assert response.json()["vin"] is None
    assert response.json()["predictions"][0]["slot"] == "FRONT"


def test_docs_are_not_exposed(fake):
    client = client_for(fake)

    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404
