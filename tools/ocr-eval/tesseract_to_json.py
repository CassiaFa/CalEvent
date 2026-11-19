#!/usr/bin/env python3
"""Runs Tesseract on a prepared image and writes CalEvent's RecognizedText JSON.

Mimics the foss OcrEngine: words grouped into lines, confidence 0..1, boxes in image pixels, no angle.
Usage: tesseract_to_json.py IMAGE PSM OUT.json
"""
import csv, io, json, subprocess, sys

image, psm, out = sys.argv[1], sys.argv[2], sys.argv[3]
tsv = subprocess.run(["tesseract", image, "stdout", "-l", "fra", "--psm", psm, "tsv"],
                     check=True, capture_output=True, text=True).stdout
rows = list(csv.DictReader(io.StringIO(tsv), delimiter="\t", quoting=csv.QUOTE_NONE))
page = next(r for r in rows if r["level"] == "1")
width, height = int(page["width"]), int(page["height"])
lines = {}
for r in rows:
    if r["level"] != "5" or not r["text"].strip():
        continue
    l, t, w, h = (int(r[k]) for k in ("left", "top", "width", "height"))
    key = (int(r["block_num"]), int(r["par_num"]), int(r["line_num"]))
    conf = max(0.0, min(1.0, float(r["conf"]) / 100.0))
    lines.setdefault(key, []).append({"text": r["text"].strip(), "box": [l, t, l + w, t + h], "confidence": conf})
out_lines = []
for words in lines.values():
    box = [min(w["box"][0] for w in words), min(w["box"][1] for w in words),
           max(w["box"][2] for w in words), max(w["box"][3] for w in words)]
    out_lines.append({"text": " ".join(w["text"] for w in words), "box": box,
                      "confidence": sum(w["confidence"] for w in words) / len(words), "elements": words})
with open(out, "w") as f:
    json.dump({"width": width, "height": height, "lines": out_lines}, f, ensure_ascii=False)
