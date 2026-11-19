#!/usr/bin/env bash
# Preprocessing experiments on the prepared images (data/ocr-eval/img). Each variant gets its own folder
# data/ocr-eval/psm11-<variant>/ so that OcrEvalReport compares them side by side.
set -euo pipefail
cd "$(dirname "$0")/../.."
export OMP_THREAD_LIMIT=2
E=data/ocr-eval
prep() { # variant src dst
  case "$1" in
    div) convert "$2" \( +clone -blur 0x20 \) -compose Divide_Src -composite -level 40%,100% "$3" ;;
    divlines)
      convert "$2" \( +clone -blur 0x20 \) -compose Divide_Src -composite -level 40%,100% "$E/tmp_div.png"
      # Long dark horizontal / vertical runs = grid lines: paint them white.
      convert "$E/tmp_div.png" -threshold 60% -negate \
        \( +clone -morphology Open rectangle:120x1 \) \( -clone 0 -morphology Open rectangle:1x120 \) \
        -delete 0 -compose Lighten -composite -morphology Dilate square:3 "$E/tmp_mask.png"
      convert "$E/tmp_div.png" "$E/tmp_mask.png" -compose Lighten -composite "$3" ;;
  esac
}
for variant in ${VARIANTS:-div divlines}; do
  mkdir -p "$E/prep-$variant" "$E/psm11-$variant"
  for img in "$E"/img/*.png; do
    b=$(basename "$img" .png)
    prep "$variant" "$img" "$E/prep-$variant/$b.png"
    python3 tools/ocr-eval/tesseract_to_json.py "$E/prep-$variant/$b.png" 11 "$E/psm11-$variant/$b.json"
  done
done
rm -f "$E"/tmp_*.png
