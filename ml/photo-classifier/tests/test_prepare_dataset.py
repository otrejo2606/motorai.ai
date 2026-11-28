"""División y normalización del dataset (sin ultralytics)."""

from __future__ import annotations

import csv
from pathlib import Path

from PIL import Image

from service.labels import KNOWN_LABELS
from training.prepare_dataset import (
    Sample,
    assign_splits,
    main,
    normalize_image,
    resolve_inside,
    safe_name,
)


def samples_for(vehicles: int, slots=("FRONT", "REAR", "OTHER")) -> list[Sample]:
    return [
        Sample(path=Path(f"{vehicle}-{slot}.jpg"), slot=slot, group=f"vehicle-{vehicle}")
        for vehicle in range(vehicles)
        for slot in slots
    ]


def test_split_is_deterministic_with_the_same_seed():
    samples = samples_for(40)

    assert assign_splits(samples, 0.15, 0.15, 7) == assign_splits(samples, 0.15, 0.15, 7)


def test_a_vehicle_never_lands_in_two_splits_and_every_split_gets_every_class():
    samples = samples_for(40)

    assignment = assign_splits(samples, 0.15, 0.15, 7)

    assert set(assignment) == {f"vehicle-{index}" for index in range(40)}
    for split in ("train", "val", "test"):
        slots = {sample.slot for sample in samples if assignment[sample.group] == split}
        assert slots == {"FRONT", "REAR", "OTHER"}
    train = sum(1 for value in assignment.values() if value == "train")
    assert 24 <= train <= 32


def test_forced_split_is_respected():
    samples = samples_for(10) + [Sample(Path("gold.jpg"), "FRONT", "golden", split="test")]

    assignment = assign_splits(samples, 0.2, 0.2, 1)

    assert assignment["golden"] == "test"


def test_safe_name_and_path_guard(tmp_path):
    assert safe_name("../evil name/ü") == ".._evil_name__"
    assert resolve_inside(tmp_path, "a/b.jpg") == (tmp_path / "a" / "b.jpg").resolve()
    assert resolve_inside(tmp_path, "../outside.jpg") is None


def test_normalize_image_reduces_and_drops_metadata(tmp_path):
    source = tmp_path / "big.png"
    Image.new("RGBA", (2000, 1000), (10, 20, 30, 255)).save(source)
    destination = tmp_path / "out" / "small.jpg"

    normalize_image(source, destination, 1024, 90)

    with Image.open(destination) as result:
        assert result.format == "JPEG"
        assert result.size == (1024, 512)
        assert "exif" not in result.info


def test_main_builds_the_ultralytics_layout(tmp_path):
    images = tmp_path / "raw"
    images.mkdir()
    rows = []
    for vehicle in range(12):
        for slot in KNOWN_LABELS:
            name = f"{vehicle}-{slot}.jpg"
            Image.new("RGB", (40, 30), (vehicle * 10, 50, 90)).save(images / name)
            rows.append({"path": name, "slot": slot, "vehicle_id": f"v{vehicle}", "split": "", "source": "own"})
    rows.append({"path": "../escape.jpg", "slot": "FRONT", "vehicle_id": "x", "split": "", "source": ""})
    rows.append({"path": "0-FRONT.jpg", "slot": "SKY", "vehicle_id": "x", "split": "", "source": ""})
    labels = tmp_path / "labels.csv"
    with labels.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["path", "slot", "vehicle_id", "split", "source"])
        writer.writeheader()
        writer.writerows(rows)
    out = tmp_path / "dataset"

    code = main([
        "--labels-csv", str(labels), "--images-root", str(images), "--out", str(out),
        "--val", "0.25", "--test", "0.25", "--min-per-class", "1",
    ])

    assert code == 0
    for split in ("train", "val", "test"):
        assert sorted(item.name for item in (out / split).iterdir()) == sorted(KNOWN_LABELS)
    assert (out / "manifest.csv").is_file()
    assert (out / "summary.json").is_file()


def test_main_refuses_a_non_empty_output(tmp_path):
    out = tmp_path / "dataset"
    out.mkdir()
    (out / "old.txt").write_text("x", encoding="utf-8")
    folders = tmp_path / "folders"
    (folders / "FRONT").mkdir(parents=True)

    assert main(["--from-folders", str(folders), "--out", str(out)]) == 2
