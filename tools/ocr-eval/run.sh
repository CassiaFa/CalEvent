#!/usr/bin/env bash
# Prepares each photo of data/ like the foss app (upright, gray, long side <= 2000 px), then runs Tesseract
# at 0/90/180/270 degrees with several page segmentation modes. Output: data/ocr-eval/psm<N>/<photo>_<deg>.json
set -euo pipefail
cd "$(dirname "$0")/../.."
export OMP_THREAD_LIMIT=2
OUT=data/ocr-eval; mkdir -p "$OUT/img"
for src in data/*.JPG; do
  name=$(basename "$src" .JPG)
  for deg in 0 90 180 270; do
    img="$OUT/img/${name}_${deg}.png"
    [ -f "$img" ] || convert "$src" -auto-orient -resize 2000x2000\> -colorspace Gray -rotate "$deg" "$img"
    for psm in ${PSMS:-11 3}; do
      mkdir -p "$OUT/psm$psm"
      start=$(date +%s.%N)
      python3 tools/ocr-eval/tesseract_to_json.py "$img" "$psm" "$OUT/psm$psm/${name}_${deg}.json"
      echo "$name $deg psm$psm $(echo "$(date +%s.%N) - $start" | bc)s" >> "$OUT/timings.txt"
    done
  done
done
