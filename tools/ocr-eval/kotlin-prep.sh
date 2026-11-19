#!/usr/bin/env bash
# Validates the Kotlin preprocessing (core/.../image/OcrPreprocess.kt) against the ImageMagick `divlines` variant:
# runs it on data/ocr-eval/img -> prep-kotlin, OCRs the result (psm 11) into psm11-kotlin, rebuilds report.txt.
# Compare the `psm11-kotlin` and `psm11-divlines` sections of data/ocr-eval/report.txt.
set -euo pipefail
cd "$(dirname "$0")/../.."
export OMP_THREAD_LIMIT=2
CAP=(systemd-run --user --scope -q -p MemoryMax=5G -p CPUQuota=400% nice -n 10 ionice -c3)
E=data/ocr-eval
"${CAP[@]}" ./gradlew :core:test --tests '*OcrPreprocessEval*' --rerun
mkdir -p "$E/psm11-kotlin"
for img in "$E"/prep-kotlin/*.png; do
  b=$(basename "$img" .png)
  "${CAP[@]}" python3 tools/ocr-eval/tesseract_to_json.py "$img" 11 "$E/psm11-kotlin/$b.json"
done
"${CAP[@]}" ./gradlew :core:test --tests '*OcrEvalReport*' --rerun
./gradlew --stop
echo "timings: $E/prep-kotlin/timings.txt ; report: $E/report.txt"
