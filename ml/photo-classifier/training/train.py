"""Entrena el clasificador del ángulo con Ultralytics YOLO (tarea classify).

Ejemplo (desde ml/photo-classifier):
  python -m training.train --data /datos/angle-v1 --model yolo11n-cls.pt --epochs 60 --imgsz 224 --device 0

Importante: el volteo horizontal queda apagado (fliplr=0). Voltear una foto cambia la toma
(LEFT por RIGHT, FRONT_LEFT por FRONT_RIGHT, REAR_LEFT por REAR_RIGHT) y enseñaría al modelo a confundirlas.

Guarda en la carpeta de la corrida (runs/angle/<nombre>) los pesos de Ultralytics y train_summary.json
con los argumentos, las métricas finales, las etiquetas y la versión de Ultralytics.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any, Sequence

from service.labels import KNOWN_LABEL_SET
from training.common import DEFAULT_SEED, disable_ultralytics_telemetry, ultralytics_version, write_json


def parse_args(argv: Sequence[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Entrena el clasificador del ángulo (Ultralytics, classify).")
    parser.add_argument("--data", type=Path, required=True, help="Salida de prepare_dataset (train, val y test)")
    parser.add_argument("--model", default="yolo11n-cls.pt", help="Pesos de partida o YAML de la arquitectura")
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--imgsz", type=int, default=224, help="Debe coincidir con IMAGE_SIZE del servicio")
    parser.add_argument("--batch", type=int, default=64)
    parser.add_argument("--patience", type=int, default=15, help="Épocas sin mejora antes de parar")
    parser.add_argument("--lr0", type=float, default=0.001)
    parser.add_argument("--optimizer", default="AdamW")
    parser.add_argument("--dropout", type=float, default=0.0)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--device", default=None, help="cpu, 0, 0,1 ... (por defecto, el que elija Ultralytics)")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--project", type=Path, default=Path("runs") / "angle")
    parser.add_argument("--name", default="yolo11n-cls")
    args = parser.parse_args(argv)
    if args.epochs < 1 or args.batch < 1 or args.imgsz < 32:
        parser.error("--epochs, --batch and --imgsz must be positive")
    return args


def check_dataset(data: Path) -> list[str]:
    """Las clases de train deben ser etiquetas conocidas y estar también en val."""
    problems: list[str] = []
    train_dir = data / "train"
    val_dir = data / "val"
    if not train_dir.is_dir() or not val_dir.is_dir():
        return ["The dataset needs the folders train and val"]
    train_classes = {item.name for item in train_dir.iterdir() if item.is_dir()}
    val_classes = {item.name for item in val_dir.iterdir() if item.is_dir()}
    unknown = sorted(train_classes - KNOWN_LABEL_SET)
    if unknown:
        problems.append(f"Unknown class folders: {unknown}")
    missing = sorted(train_classes - val_classes)
    if missing:
        problems.append(f"Classes without validation photos: {missing}")
    return problems


def train_arguments(args: argparse.Namespace) -> dict[str, Any]:
    arguments: dict[str, Any] = {
        "data": str(args.data),
        "epochs": args.epochs,
        "imgsz": args.imgsz,
        "batch": args.batch,
        "patience": args.patience,
        "lr0": args.lr0,
        "optimizer": args.optimizer,
        "dropout": args.dropout,
        "workers": args.workers,
        "seed": args.seed,
        "deterministic": True,
        "project": str(args.project),
        "name": args.name,
        "exist_ok": False,
        "plots": True,
        # Nunca voltear: cambiaría la toma (izquierda por derecha).
        "fliplr": 0.0,
    }
    if args.device:
        arguments["device"] = args.device
    return arguments


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    problems = check_dataset(args.data)
    if problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        return 2
    disable_ultralytics_telemetry()
    from ultralytics import YOLO  # AGPL-3.0, ver LICENSE-NOTICE.md

    model = YOLO(args.model, task="classify")
    arguments = train_arguments(args)
    metrics = model.train(**arguments)
    save_dir = Path(model.trainer.save_dir)
    best = save_dir / "weights" / "best.pt"
    results = getattr(metrics, "results_dict", None) or {}
    summary = {
        "arguments": arguments,
        "ultralyticsVersion": ultralytics_version(),
        "metrics": {key: float(value) for key, value in results.items()},
        "labels": [str(name) for _, name in sorted(dict(model.names).items())],
        "bestWeights": str(best),
    }
    write_json(save_dir / "train_summary.json", summary)
    print(f"Training finished. Best weights: {best}")
    print("Next: python -m training.evaluate --model <best> --data <dataset> --split test")
    return 0


if __name__ == "__main__":
    sys.exit(main())
