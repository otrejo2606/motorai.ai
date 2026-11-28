"""Arma el dataset de clasificación que espera Ultralytics: <salida>/{train,val,test}/<TOMA>/*.jpg.

Entradas (una de dos):

  --labels-csv etiquetas.csv --images-root /datos/fotos
      CSV con encabezado. Columnas: path (relativa a --images-root), slot (una de las 15 etiquetas),
      vehicle_id (recomendada: agrupa las fotos de un mismo vehículo para que no queden en dos
      divisiones), split (opcional: train, val o test; fija la división, por ejemplo del conjunto dorado)
      y source (opcional: listing, own o partner, solo para trazabilidad).

  --from-folders /datos/por-toma
      Una carpeta por etiqueta (FRONT, REAR, ..., OTHER). Si el nombre del archivo empieza con
      «<vehículo>__», ese prefijo agrupa las fotos del mismo vehículo.

La división es estratificada por toma y agrupada por vehículo, con semilla fija. Cada imagen se
normaliza como lo hace el backend antes de llamar al servicio: se rota según su EXIF, se reduce al
lado mayor --max-side (1024) y se guarda como JPEG sin metadatos (sin EXIF ni GPS).

Ejemplo:
  python -m training.prepare_dataset --labels-csv etiquetas.csv --images-root /datos/fotos --out /datos/angle-v1
"""

from __future__ import annotations

import argparse
import csv
import random
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence

from PIL import Image, ImageOps, UnidentifiedImageError

from service.labels import KNOWN_LABELS, normalize_label
from training.common import DEFAULT_SEED, IMAGE_SUFFIXES, write_json

SPLITS = ("train", "val", "test")
GROUP_SEPARATOR = "__"
UNSAFE_CHARS = re.compile("[^A-Za-z0-9_.-]")


@dataclass(frozen=True)
class Sample:
    path: Path
    slot: str
    group: str
    split: str | None = None
    source: str = ""


class DatasetError(Exception):
    pass


def safe_name(text: str, limit: int = 60) -> str:
    cleaned = UNSAFE_CHARS.sub("_", text.strip())[:limit]
    return cleaned or "x"


def resolve_inside(root: Path, relative: str) -> Path | None:
    """Ruta absoluta de relative dentro de root, o None si sale de root (por ejemplo con «..»)."""
    base = root.resolve()
    candidate = (base / relative).resolve()
    return candidate if candidate.is_relative_to(base) else None


def read_csv_samples(csv_path: Path, images_root: Path) -> tuple[list[Sample], Counter]:
    samples: list[Sample] = []
    skipped: Counter = Counter()
    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None or "path" not in reader.fieldnames or "slot" not in reader.fieldnames:
            raise DatasetError("The CSV needs the columns path and slot")
        for line, row in enumerate(reader, start=2):
            slot = normalize_label(row.get("slot"))
            if slot is None:
                skipped["unknown_slot"] += 1
                continue
            relative = (row.get("path") or "").strip()
            path = resolve_inside(images_root, relative) if relative else None
            if path is None:
                skipped["path_outside_root"] += 1
                continue
            if path.suffix.lower() not in IMAGE_SUFFIXES:
                skipped["not_an_image"] += 1
                continue
            if not path.is_file():
                skipped["missing_file"] += 1
                continue
            split = (row.get("split") or "").strip().lower() or None
            if split is not None and split not in SPLITS:
                raise DatasetError(f"Line {line}: split must be train, val or test")
            group = (row.get("vehicle_id") or "").strip() or f"image-{line}"
            source = (row.get("source") or "").strip()
            samples.append(Sample(path=path, slot=slot, group=group, split=split, source=source))
    return samples, skipped


def read_folder_samples(root: Path) -> tuple[list[Sample], Counter]:
    samples: list[Sample] = []
    skipped: Counter = Counter()
    for folder in sorted(item for item in root.iterdir() if item.is_dir()):
        slot = normalize_label(folder.name)
        if slot is None:
            skipped["unknown_folder"] += 1
            continue
        for path in sorted(item for item in folder.iterdir() if item.is_file()):
            if path.suffix.lower() not in IMAGE_SUFFIXES:
                skipped["not_an_image"] += 1
                continue
            prefix, separator, _ = path.stem.partition(GROUP_SEPARATOR)
            group = prefix if separator and prefix else f"{slot}/{path.name}"
            samples.append(Sample(path=path, slot=slot, group=group))
    return samples, skipped


def assign_splits(
    samples: Sequence[Sample], val_fraction: float, test_fraction: float, seed: int
) -> dict[str, str]:
    """Asigna cada vehículo (grupo) a una división.

    Los grupos con split fijado en el CSV van a esa división. El resto se reparte en orden
    pseudoaleatorio (semilla fija), primero los grupos más grandes, a la división cuyo déficit
    relativo por toma es mayor. Así cada toma queda cerca de las fracciones pedidas y ningún
    vehículo aparece en dos divisiones.
    """
    fractions = {"train": 1.0 - val_fraction - test_fraction, "val": val_fraction, "test": test_fraction}
    groups: dict[str, Counter] = {}
    forced: dict[str, str] = {}
    for sample in samples:
        groups.setdefault(sample.group, Counter())[sample.slot] += 1
        if sample.split is not None:
            previous = forced.setdefault(sample.group, sample.split)
            if previous != sample.split:
                raise DatasetError(f"Vehicle {sample.group} has photos forced into two splits")
    totals: Counter = Counter(sample.slot for sample in samples)
    assigned: dict[str, Counter] = {split: Counter() for split in SPLITS}
    assignment: dict[str, str] = {}
    for group in sorted(forced):
        assignment[group] = forced[group]
        assigned[forced[group]].update(groups[group])
    free = sorted(group for group in groups if group not in forced)
    random.Random(seed).shuffle(free)
    free.sort(key=lambda group: -sum(groups[group].values()))
    active = [split for split in SPLITS if fractions[split] > 0]
    for group in free:
        counts = groups[group]
        scores = {split: _deficit(counts, fractions[split], totals, assigned[split]) for split in active}
        best = max(active, key=scores.__getitem__)
        assignment[group] = best
        assigned[best].update(counts)
    return assignment


def _deficit(counts: Counter, fraction: float, totals: Counter, assigned: Counter) -> float:
    """Cuánto le falta a una división, por toma y relativo al total de la toma, para llegar a su fracción."""
    return sum(
        count * (fraction * totals[slot] - assigned[slot]) / totals[slot]
        for slot, count in counts.items()
    )


def normalize_image(source: Path, destination: Path, max_side: int, quality: int) -> None:
    """Rota según el EXIF, reduce el lado mayor y guarda JPEG sin metadatos, como el backend."""
    with Image.open(source) as opened:
        rotated = ImageOps.exif_transpose(opened)
        rgb = rotated.convert("RGB")
        if rotated is not opened:
            rotated.close()
    rgb.thumbnail((max_side, max_side))
    destination.parent.mkdir(parents=True, exist_ok=True)
    rgb.save(destination, format="JPEG", quality=quality)
    rgb.close()


def write_dataset(
    samples: Sequence[Sample], assignment: dict[str, str], out: Path, max_side: int, quality: int
) -> tuple[list[dict[str, str]], Counter]:
    rows: list[dict[str, str]] = []
    failed: Counter = Counter()
    for index, sample in enumerate(samples):
        split = assignment[sample.group]
        name = f"{safe_name(sample.group)}{GROUP_SEPARATOR}{index:07d}.jpg"
        destination = out / split / sample.slot / name
        try:
            normalize_image(sample.path, destination, max_side, quality)
        except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
            failed["unreadable_image"] += 1
            continue
        rows.append({
            "split": split,
            "slot": sample.slot,
            "file": f"{split}/{sample.slot}/{name}",
            "vehicle_id": sample.group,
            "source": sample.source,
        })
    return rows, failed


def summarize(rows: Iterable[dict[str, str]], min_per_class: int) -> dict:
    counts: dict[str, Counter] = {split: Counter() for split in SPLITS}
    for row in rows:
        counts[row["split"]][row["slot"]] += 1
    missing = {
        split: [label for label in KNOWN_LABELS if counts[split][label] == 0]
        for split in SPLITS
        if sum(counts[split].values()) > 0
    }
    below_minimum = [label for label in KNOWN_LABELS if counts["train"][label] < min_per_class]
    return {
        "counts": {split: {label: counts[split][label] for label in KNOWN_LABELS} for split in SPLITS},
        "missingClasses": {split: labels for split, labels in missing.items() if labels},
        "trainBelowMinimum": below_minimum,
    }


def write_manifest(rows: Sequence[dict[str, str]], path: Path) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["split", "slot", "file", "vehicle_id", "source"])
        writer.writeheader()
        writer.writerows(rows)


def parse_args(argv: Sequence[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Arma el dataset por toma para Ultralytics (clasificación).")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--labels-csv", type=Path, help="CSV con path, slot, vehicle_id, split y source")
    source.add_argument("--from-folders", type=Path, help="Carpeta con una subcarpeta por etiqueta")
    parser.add_argument("--images-root", type=Path, help="Raíz de las rutas del CSV")
    parser.add_argument("--out", type=Path, required=True, help="Carpeta de salida (no debe existir o estar vacía)")
    parser.add_argument("--val", type=float, default=0.15, help="Fracción de validación")
    parser.add_argument("--test", type=float, default=0.15, help="Fracción de prueba (0 para no separar prueba)")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--max-side", type=int, default=1024, help="Igual a motoria.media.angle-check.max-side")
    parser.add_argument("--quality", type=int, default=90)
    parser.add_argument("--min-per-class", type=int, default=300, help="Mínimo de fotos de entrenamiento por toma")
    parser.add_argument("--allow-missing-classes", action="store_true",
                        help="No falla si una división queda sin alguna toma (solo para pruebas de humo)")
    args = parser.parse_args(argv)
    if args.labels_csv is not None and args.images_root is None:
        parser.error("--labels-csv needs --images-root")
    if not (0.0 < args.val < 0.5) or not (0.0 <= args.test < 0.5) or args.val + args.test >= 0.6:
        parser.error("--val and --test must leave at least 40 % for training")
    if not 64 <= args.max_side <= 4096 or not 50 <= args.quality <= 100:
        parser.error("--max-side must be 64 to 4096 and --quality 50 to 100")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    if args.out.exists() and any(args.out.iterdir()):
        print(f"The output folder {args.out} is not empty", file=sys.stderr)
        return 2
    try:
        if args.labels_csv is not None:
            samples, skipped = read_csv_samples(args.labels_csv, args.images_root)
        else:
            samples, skipped = read_folder_samples(args.from_folders)
        if not samples:
            raise DatasetError("No usable image was found")
        assignment = assign_splits(samples, args.val, args.test, args.seed)
    except DatasetError as error:
        print(str(error), file=sys.stderr)
        return 2
    args.out.mkdir(parents=True, exist_ok=True)
    rows, failed = write_dataset(samples, assignment, args.out, args.max_side, args.quality)
    skipped.update(failed)
    write_manifest(rows, args.out / "manifest.csv")
    summary = summarize(rows, args.min_per_class)
    summary.update({
        "seed": args.seed,
        "fractions": {"val": args.val, "test": args.test},
        "maxSide": args.max_side,
        "images": len(rows),
        "vehicles": len(set(assignment)),
        "skipped": dict(skipped),
    })
    write_json(args.out / "summary.json", summary)
    print(f"Dataset written to {args.out}: {len(rows)} images, skipped {dict(skipped)}")
    if summary["trainBelowMinimum"]:
        print(f"Warning: fewer than {args.min_per_class} training photos for {summary['trainBelowMinimum']}")
    if summary["missingClasses"] and not args.allow_missing_classes:
        print(f"Missing classes per split: {summary['missingClasses']}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
