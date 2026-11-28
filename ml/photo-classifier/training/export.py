"""Exporta el clasificador a ONNX para servirlo en CPU con ONNX Runtime.

Ejemplo (desde ml/photo-classifier):
  python -m training.export --model runs/angle/yolo11n-cls/weights/best.pt --version angle-yolo11n-cls-2027.01

Deja en --out-dir:
  <versión>.onnx  el modelo que se monta en el servicio (MODEL_PATH)
  <versión>.json  versión, etiquetas en orden, tamaño de entrada, sha256 y versión de Ultralytics

El servicio carga el .onnx con Ultralytics (que usa ONNX Runtime) y lee las etiquetas de los metadatos
del propio ONNX. La versión debe ir en MODEL_VERSION del servicio (hasta 60 caracteres).

Futuro (fuera de este cambio): el mismo ONNX podría correr en el navegador con onnxruntime-web como otra
implementación de OnDeviceDetector del portal (design D8 de add-captura-guiada-fotos). Eso exige medir el
tamaño y la latencia en gama media y revisar la licencia del modelo distribuido al navegador (puerta 0.4).
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Sequence

from training.common import disable_ultralytics_telemetry, sha256_of, ultralytics_version, write_json

VERSION_PATTERN = re.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,59}")


def parse_args(argv: Sequence[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Exporta el clasificador del ángulo a ONNX.")
    parser.add_argument("--model", type=Path, required=True, help="Pesos .pt entrenados (best.pt)")
    parser.add_argument("--version", required=True, help="Versión del modelo, por ejemplo angle-yolo11n-cls-2027.01")
    parser.add_argument("--imgsz", type=int, default=224, help="Igual que en el entrenamiento y en IMAGE_SIZE")
    parser.add_argument("--opset", type=int, default=17)
    parser.add_argument("--simplify", action="store_true", help="Simplifica el grafo (requiere onnxslim)")
    parser.add_argument("--out-dir", type=Path, default=Path("models"))
    args = parser.parse_args(argv)
    if not VERSION_PATTERN.fullmatch(args.version):
        parser.error("--version must be 1 to 60 letters, digits, dot, dash or underscore")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    if not args.model.is_file():
        print(f"{args.model} does not exist", file=sys.stderr)
        return 2
    disable_ultralytics_telemetry()
    from ultralytics import YOLO  # AGPL-3.0, ver LICENSE-NOTICE.md

    model = YOLO(str(args.model), task="classify")
    exported = Path(
        model.export(format="onnx", imgsz=args.imgsz, opset=args.opset, simplify=args.simplify, dynamic=False)
    )
    args.out_dir.mkdir(parents=True, exist_ok=True)
    target = args.out_dir / f"{args.version}.onnx"
    shutil.copy2(exported, target)
    metadata = {
        "modelVersion": args.version,
        "labels": [str(name) for _, name in sorted(dict(model.names).items())],
        "imgsz": args.imgsz,
        "opset": args.opset,
        "sha256": sha256_of(target),
        "sourceWeights": str(args.model),
        "sourceSha256": sha256_of(args.model),
        "ultralyticsVersion": ultralytics_version(),
        "exportedAt": datetime.now(timezone.utc).isoformat(),
        "license": "AGPL-3.0 (Ultralytics) until the Enterprise license is signed; see LICENSE-NOTICE.md",
    }
    write_json(args.out_dir / f"{args.version}.json", metadata)
    print(f"Exported {target}. Evaluate it again with: python -m training.evaluate --model {target} ...")
    return 0


if __name__ == "__main__":
    sys.exit(main())
