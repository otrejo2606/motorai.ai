"""Cálculos de evaluate.py sin ultralytics: misma regla de umbrales que PhotoAnglePolicy del backend."""

import pytest

from training.evaluate import (
    MATCH,
    MISMATCH,
    UNCERTAIN,
    Outcome,
    accuracy,
    build_report,
    confusion_matrix,
    decide,
    honest_seller,
    per_class,
    percentile,
    wrong_shot,
)


@pytest.mark.parametrize(
    "expected,predicted,confidence,decision",
    [
        ("FRONT", "FRONT", 0.80, MATCH),
        ("FRONT", "FRONT", 0.79, UNCERTAIN),
        ("FRONT", "REAR", 0.90, MISMATCH),
        ("FRONT", "OTHER", 0.95, MISMATCH),
        ("FRONT", "REAR", 0.89, UNCERTAIN),
    ],
)
def test_decide_matches_the_backend_policy(expected, predicted, confidence, decision):
    assert decide(expected, predicted, confidence, 0.80, 0.90) == decision


def outcomes():
    return [
        Outcome("FRONT", "FRONT", 0.95, 40.0),
        Outcome("FRONT", "FRONT_LEFT", 0.92, 50.0),
        Outcome("REAR", "REAR", 0.70, 60.0),
        Outcome("ENGINE", "ENGINE", 0.99, 70.0),
        Outcome("OTHER", "OTHER", 0.97, 80.0),
    ]


def test_accuracy_by_group():
    data = outcomes()

    assert accuracy(data, ("FRONT", "REAR")) == pytest.approx(2 / 3)
    assert accuracy(data, ("ENGINE",)) == 1.0
    assert accuracy(data, ("VIN",)) is None


def test_confusion_and_per_class():
    matrix = confusion_matrix(outcomes())
    metrics = per_class(matrix)

    assert matrix["FRONT"]["FRONT_LEFT"] == 1
    assert metrics["FRONT"]["recall"] == 0.5
    assert metrics["FRONT"]["precision"] == 1.0
    assert metrics["FRONT_LEFT"]["precision"] == 0.0
    assert metrics["VIN"]["recall"] is None


def test_honest_seller_counts_without_other():
    result = honest_seller(outcomes(), 0.80, 0.90)

    assert result["total"] == 4
    assert result[MATCH] == 2
    assert result[MISMATCH] == 1
    assert result[UNCERTAIN] == 1
    assert result["MISMATCH_rate"] == 0.25


def test_wrong_shot_simulates_the_other_slots():
    result = wrong_shot([Outcome("FRONT", "FRONT", 0.95, 1.0)], 0.80, 0.90)

    assert result["total"] == 13
    assert result[MISMATCH] == 13


def test_percentile_nearest_rank():
    assert percentile([10.0, 20.0, 30.0, 40.0], 0.5) == 20.0
    assert percentile([10.0, 20.0, 30.0, 40.0], 0.95) == 40.0
    assert percentile([], 0.95) is None


def test_report_checks_the_minimums():
    report = build_report(outcomes(), 0.80, 0.90)

    assert report["images"] == 5
    assert report["checks"]["exteriorTop1"] is False
    assert report["checks"]["detailTop1"] is True
    assert report["checks"]["falseMismatchRate"] is False
    assert report["checks"]["latencyP95"] is True
    assert report["meetsMinimums"] is False
    assert len(report["thresholdSweep"]) == 30
