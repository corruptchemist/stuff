#!/usr/bin/env bash
# Installs the video-editing toolkit into this container (idempotent).
#   Python libs  -> pip (user site)
#   Fonts        -> ~/.local/share/fonts (from @fontsource npm packages, woff2 -> ttf)
#   RNNoise      -> video-tools/models/*.rnnn (for ffmpeg arnndn speech denoising)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "== python packages (isolated venv: video-tools/.venv)"
[ -x "$HERE/.venv/bin/python" ] || python3 -m venv "$HERE/.venv"
"$HERE/.venv/bin/pip" install -q --upgrade pip
"$HERE/.venv/bin/pip" install -q \
  moviepy opencv-python-headless pyloudnorm noisereduce soundfile scipy matplotlib \
  pocketsphinx auto-editor fonttools brotli pillow numpy

echo "== rnnoise models"
mkdir -p "$HERE/models"
for m in std bd cb sh; do
  [ -s "$HERE/models/$m.rnnn" ] || curl -sSfL -o "$HERE/models/$m.rnnn" \
    "https://raw.githubusercontent.com/richardpl/arnndn-models/master/$m.rnnn"
done

echo "== fonts"
FONTS=(playfair-display oswald bebas-neue special-elite libre-baskerville montserrat cinzel old-standard-tt anton lora)
FD="$HOME/.local/share/fonts/fontsource"; mkdir -p "$FD"
TMP="$(mktemp -d)"
for f in "${FONTS[@]}"; do
  (cd "$TMP" && npm pack -s "@fontsource/$f" >/dev/null && tar xzf fontsource-$f-*.tgz && rm fontsource-$f-*.tgz)
  for w in "$TMP"/package/files/$f-latin-{400,700,900}-{normal,italic}.woff2; do
    [ -f "$w" ] || continue
    out="$FD/$(basename "${w%.woff2}").ttf"
    [ -s "$out" ] || "$HERE/.venv/bin/python" "$HERE/woff2ttf.py" "$w" "$out"
  done
  rm -rf "$TMP/package"
done
rm -rf "$TMP"
fc-cache -f >/dev/null
echo "== done"
