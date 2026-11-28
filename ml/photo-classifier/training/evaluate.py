"""Evalúa el clasificador como lo usará el backend.

Ejemplo (desde ml/photo-classifier):
  python -m training.evaluate --model runs/angle/yolo11n-cls/weights/best.pt --data /datos/angle-v1 --split test

Calcula, con el mismo filtrado de etiquetas que el servicio (service.classifier.rank) y la misma regla
de umbrales que PhotoAnglePolicy del backend (MATCH, MISMATCH, UNCERTAIN):

- exactitud top-1 global, en exteriores, en detalle (interior, tablero, motor, llantas, VIN) y en OTHER;
- matriz de confusión y precisión y exhaustividad por clase;
- vendedor honesto (la foto es de la toma pedida): cuántas quedarían MATCH, MISMATCH (falso rechazo)
  y UNCERTAIN con los umbrales --accept y --reject (0.80 y 0.90 en el backend);
- foto equivocada (simulada: la misma foto enviada para cada una de las otras 14 tomas): cuántas se
  rechazarían bien (MISMATCH), cuántas se aceptarían por error (MATCH) y cuántas irían al analista;
- un barrido de umbrales para ajustarlos en la puerta 0.2;
- latencia por imagen (p50 y p95) en la máquina donde corre la evaluación;
- si cumple las métricas mínimas del design (D7 de add-clasificador-fotos-yolo).

Escribe report.json y confusion_matrix.csv en --out.
"""

from __future__ import annotations

import argparse
import csv
import math
import sys
import time
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence

from service.classifier import Prediction, rank
from service.labels import DETAIL_SLOTS, EXTERIOR_SLOTS, KNOWN_LABELS, OTHER, SLOTS, normalize_label
from training.common import IMAGE_SUFFIXES, disable_ultralytics_telemetry, ultralytics_version, write_json

MATCH = "MATCH"
MISMATCH = "MISMATCH"
UNCERTAIN = "UNCERTAIN"

MIN_EXTERIOR_TOP1 = 0.95
MIN_DETAIL_TOP1 = 0.90
MAX_FALSE_MISMATCH_RATE = 0.01
MAX_P95_LATENCY_MS = 300.0

ACCEPT_GRID = (0.60, 0.70, 0.75, 0.80, 0.85, 0.90)
REJECT_GRID = (0.80, 0.85, 0.90, 0.95, 0.98)


@dataclass(frozen=True)
class Outcome:
    truth: str
    predicted: str
    confidence: float
    latency_ms: float


def decide(expected: str, predicted: str, confidence: float, accept: float, reject: float) -> str:
    """Copia de PhotoAnglePolicy.decide del backend (design D4 de add-captura-guiada-fotos)."""
    same = expected == predicted
    if same and confidence >= accept:
        return MATCH
    if not same and confidence >= reject:
        return MISMATCH
    return UNCERTAIN


def accuracy(outcomes: Sequence[Outcome], labels: Iterable[str]) -> float | None:
    subset = set(labels)
    selected = [outcome for outcome in outcomes if outcome.truth in subset]
    if not selected:
        return None
    return sum(1 for outcome in selected if outcome.predicted == outcome.truth) / len(selected)


def confusion_matrix(outcomes: Sequence[Outcome]) -> dict[str, dict[str, int]]:
    matrix = {truth: {predicted: 0 for predicted in KNOWN_LABELS} for truth in KNOWN_LABELS}
    for outcome in outcomes:
        matrix[outcome.truth][outcome.predicted] += 1
    return matrix


def per_class(matrix: dict[str, dict[str, int]]) -> dict[str, dict[str, float | int | None]]:
    metrics: dict[str, dict[str, float | int | None]] = {}
    for label in KNOWN_LABELS:
        support = sum(matrix[label].values())
        predicted_as = sum(matrix[truth][label] for truth in KNOWN_LABELS)
        hits = matrix[label][label]
        metrics[label] = {
            "support": support,
            "precision": hits / predicted_as if predicted_as else None,
            "recall": hits / support if support else None,
        }
    return metrics


def honest_seller(outcomes: Sequence[Outcome], accept: float, reject: float) -> dict[str, float | int]:
    """La foto es de la toma pedida (solo fotos de una de las 14 tomas, no OTHER)."""
    decisions = Counter(
        decide(outcome.truth, outcome.predicted, outcome.confidence, accept, reject)
        for outcome in outcomes
        if outcome.truth != OTHER
    )
    return _rates(decisions)


def wrong_shot(outcomes: Sequence[Outcome], accept: float, reject: float) -> dict[str, float | int]:
    """Simula enviar cada foto para cada una de las otras tomas: lo correcto es MISMATCH."""
    decisions: Counter = Counter()
    for outcome in outcomes:
        for expected in SLOTS:
            if expected != outcome.truth:
                decisions[decide(expected, outcome.predicted, outcome.confidence, accept, reject)] += 1
    return _rates(decisions)


def _rates(decisions: Counter) -> dict[str, float | int]:
    total = sum(decisions.values())
    result: dict[str, float | int] = {"total": total}
    for decision in (MATCH, MISMATCH, UNCERTAIN):
        result[decision] = decisions[decision]
        result[decision + "_rate"] = decisions[decision] / total if total else 0.0
    return result


def percentile(values: Sequence[float], fraction: float) -> float | None:
    """Percentil por rango más cercano."""
    if not values:
        return None
    ordered = sorted(values)
    position = max(1, math.ceil(fraction * len(ordered)))
    return ordered[position - 1]


def threshold_sweep(outcomes: Sequence[Outcome]) -> list[dict[str, float]]:
    rows = []
    for accept in ACCEPT_GRID:
        for reject in REJECT_GRID:
            honest = honest_seller(outcomes, accept, reject)
            wrong = wrong_shot(outcomes, accept, reject)
            rows.append({
                "accept": accept,
                "reject": reject,
                "honestMatchRate": honest["MATCH_rate"],
                "falseMismatchRate": honest["MISMATCH_rate"],
                "honestUncertainRate": honest["UNCERTAIN_rate"],
                "wrongShotRejectedRate": wrong["MISMATCH_rate"],
                "wrongShotAcceptedRate": wrong["MATCH_rate"],
            })
    return rows


def build_report(outcomes: Sequence[Outcome], accept: float, reject: float) -> dict:
    matrix = confusion_matrix(outcomes)
    honest = honest_seller(outcomes, accept, reject)
    latencies = [outcome.latency_ms for outcome in outcomes]
    exterior = accuracy(outcomes, EXTERIOR_SLOTS)
    detail = accuracy(outcomes, DETAIL_SLOTS)
    p95 = percentile(latencies, 0.95)
    checks = {
        "exteriorTop1": exterior is not None and exterior >= MIN_EXTERIOR_TOP1,
        "detailTop1": detail is not None and detail >= MIN_DETAIL_TOP1,
        "falseMismatchRate": honest["total"] > 0 and honest["MISMATCH_rate"] < MAX_FALSE_MISMATCH_RATE,
        "latencyP95": p95 is not None and p95 < MAX_P95_LATENCY_MS,
    }
    return {
        "images": len(outcomes),
        "thresholds": {"accept": accept, "reject": reject},
        "top1": {
            "all": accuracy(outcomes, KNOWN_LABELS),
            "exterior": exterior,
            "detail": detail,
            "other": accuracy(outcomes, (OTHER,)),
        },
        "perClass": per_class(matrix),
        "honestSeller": honest,
        "wrongShot": wrong_shot(outcomes, accept, reject),
        "latencyMs": {"p50": percentile(latencies, 0.50), "p95": p95},
        "minimums": {
            "exteriorTop1": MIN_EXTERIOR_TOP1,
            "detailTop1": MIN_DETAIL_TOP1,
            "maxFalseMismatchRate": MAX_FALSE_MISMATCH_RATE,
            "maxP95LatencyMs": MAX_P95_LATENCY_MS,
        },
        "checks": checks,
        "meetsMinimums": all(checks.values()),
        "thresholdSweep": threshold_sweep(outcomes),
        "confusionMatrix": matrix,
    }


def write_confusion_csv(matrix: dict[str, dict[str, int]], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["truth/predicted", *KNOWN_LABELS])
        for truth in KNOWN_LABELS:
            writer.writerow([truth, *(matrix[truth][predicted] for predicted in KNOWN_LABELS)])


def labelled_images(split_dir: Path) -> list[tuple[Path, str]]:
    images: list[tuple[Path, str]] = []
    for folder in sorted(item for item in split_dir.iterdir() if item.is_dir()):
        label = normalize_label(folder.name)
        if label is None:
            continue
        for path in sorted(folder.iterdir()):
            if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
                images.append((path, label))
    return images


def parse_args(argv: Sequence[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evalúa el clasificador del ángulo con los umbrales del backend.")
    parser.add_argument("--model", type=Path, required=True, help="Pesos .pt o modelo .onnx")
    parser.add_argument("--data", type=Path, required=True, help="Salida de prepare_dataset")
    parser.add_argument("--split", default="test", choices=("val", "test"))
    parser.add_argument("--imgsz", type=int, default=224)
    parser.add_argument("--device", default="cpu", help="cpu mide la latencia como en el servicio")
    parser.add_argument("--accept", type=float, default=0.80, help="motoria.media.angle-check.accept-threshold")
    parser.add_argument("--reject", type=float, default=0.90, help="motoria.media.angle-check.reject-threshold")
    parser.add_argument("--warmup", type=int, default=5, help="Inferencias de calentamiento sin medir")
    parser.add_argument("--out", type=Path, default=Path("reports") / "angle-eval")
    args = parser.parse_args(argv)
    if not (0.0 < args.accept <= 1.0 and 0.0 < args.reject <= 1.0):
        parser.error("--accept and --reject must be between 0 and 1")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    split_dir = args.data / args.split
    if not split_dir.is_dir():
        print(f"{split_dir} does not exist", file=sys.stderr)
        return 2
    images = labelled_images(split_dir)
    if not images:
        print(f"No labelled image in {split_dir}", file=sys.stderr)
        return 2
    disable_ultralytics_telemetry()
    from ultralytics import YOLO  # AGPL-3.0, ver LICENSE-NOTICE.md

    model = YOLO(str(args.model), task="classify")
    names = {int(index): str(name) for index, name in dict(model.names).items()}
    for path, _ in images[: max(0, args.warmup)]:
        model.predict(str(path), imgsz=args.imgsz, device=args.device, verbose=False)
    outcomes: list[Outcome] = []
    for path, truth in images:
        started = time.perf_counter()
        results = model.predict(str(path), imgsz=args.imgsz, device=args.device, verbose=False)
        latency_ms = (time.perf_counter() - started) * 1000.0
        probs = results[0].probs
        values = probs.data.tolist() if probs is not None else []
        ranked = rank([Prediction(names.get(index, ""), float(value)) for index, value in enumerate(values)], 1)
        predicted, confidence = (ranked[0].slot, ranked[0].confidence) if ranked else (OTHER, 0.0)
        outcomes.append(Outcome(truth=truth, predicted=predicted, confidence=confidence, latency_ms=latency_ms))
    report = build_report(outcomes, args.accept, args.reject)
    report.update({
        "model": str(args.model),
        "split": args.split,
        "device": args.device,
        "ultralyticsVersion": ultralytics_version(),
    })
    write_json(args.out / "report.json", report)
    write_confusion_csv(report["confusionMatrix"], args.out / "confusion_matrix.csv")
    top1 = report["top1"]
    honest = report["honestSeller"]
    print(f"Top-1 all {top1['all']}, exterior {top1['exterior']}, detail {top1['detail']}")
    print(
        f"Honest seller at {args.accept}/{args.reject}: MATCH {honest['MATCH']}, "
        f"MISMATCH {honest['MISMATCH']}, UNCERTAIN {honest['UNCERTAIN']}"
    )
    print(f"Latency p95 {report['latencyMs']['p95']} ms. Meets the minimums: {report['meetsMinimums']}")
    print(f"Report written to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
