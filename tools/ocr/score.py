"""Offline vyhodnocení ML Kit benchmarku; nemění obraz ani produkční OCR."""
import argparse
import hashlib
import json
import math
import unicodedata
from collections import defaultdict
from pathlib import Path


def text(value):
    return " ".join(unicodedata.normalize("NFC", value).split())


def distance(reference, actual):
    previous = list(range(len(actual) + 1))
    for i, left in enumerate(reference, 1):
        current = [i]
        for j, right in enumerate(actual, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1,
                               previous[j - 1] + (left != right)))
        previous = current
    return previous[-1]


def percentile(values, fraction):
    return sorted(values)[math.ceil(len(values) * fraction) - 1]


def score(references, rows):
    samples = references["samples"]
    expected = {sample["file"]: sample for sample in samples}
    if not expected or len(expected) != len(samples):
        raise ValueError("Prázdná nebo duplicitní referenční sada")
    if any(not isinstance(s["text"], str) or not text(s["text"]) or
           not isinstance(s["lines"], int) or s["lines"] < 1 for s in samples):
        raise ValueError("Reference musí mít text a počet řádků")
    groups = defaultdict(dict)
    for row in rows:
        name, scale, mode = row["file"], row["scale"], row["mode"]
        if name not in expected:
            raise ValueError("Neznámý snímek: " + str(name))
        if (not isinstance(scale, (int, float)) or not math.isfinite(scale) or scale <= 0 or
                not isinstance(mode, str) or not mode or not isinstance(row["text"], str) or
                not isinstance(row["ms"], (int, float)) or not math.isfinite(row["ms"]) or row["ms"] < 0 or
                not isinstance(row["heights"], list) or
                any(not isinstance(h, int) or h < 0 for h in row["heights"])):
            raise ValueError("Neplatné OCR měření")
        if "sha256" in row and row["sha256"] != expected[name]["sha256"]:
            raise ValueError("Jiný vstupní obraz: " + name)
        group = groups[(mode, scale)]
        if name in group:
            raise ValueError("Duplicitní měření: " + name)
        group[name] = row
    if not groups:
        raise ValueError("Chybí OCR měření")
    report = []
    for (mode, scale), group in sorted(groups.items()):
        if set(group) != set(expected):
            raise ValueError("Varianta nemá totožnou referenční sadu: " + str((mode, scale)))
        chars = words = char_errors = word_errors = exact = line_matches = 0
        for name, row in group.items():
            reference, actual = text(expected[name]["text"]), text(row["text"])
            chars += len(reference)
            words += len(reference.split())
            char_errors += distance(reference, actual)
            word_errors += distance(reference.split(), actual.split())
            exact += reference == actual
            line_matches += len(row["heights"]) == expected[name]["lines"]
        durations = [r["ms"] for r in group.values()]
        report.append(dict(mode=mode, scale=scale, samples=len(group),
                           char_errors=char_errors, reference_chars=chars, cer=char_errors / chars,
                           word_errors=word_errors, reference_words=words, wer=word_errors / words,
                           exact_samples=exact, line_count_matches=line_matches,
                           ocr_ms_p50=percentile(durations, .50), ocr_ms_p95=percentile(durations, .95)))
    return dict(dataset=references["dataset"], normalization="NFC + collapsed whitespace; case/punctuation preserved",
                input_sha256_verified=all("sha256" in r for r in rows), variants=report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("measurements", type=Path)
    parser.add_argument("--images", type=Path, help="Zkontroluje SHA-256 lokálních vstupních PNG")
    args = parser.parse_args()
    try:
        references = json.loads(args.reference.read_text(encoding="utf-8"))
        rows = json.loads(args.measurements.read_text(encoding="utf-8"))
        if args.images:
            for sample in references["samples"]:
                name = sample["file"]
                if Path(name).name != name:
                    raise ValueError("Neplatné jméno snímku")
                digest = hashlib.sha256((args.images / name).read_bytes()).hexdigest()
                if digest != sample["sha256"]:
                    raise ValueError("Jiný lokální vstup: " + name)
        result = score(references, rows)
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, str(error) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
