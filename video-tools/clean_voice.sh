#!/usr/bin/env bash
# clean_voice.sh IN OUT.wav [rnnoise-model]
# Phone/laptop voice cleanup: rumble/hum cut -> RNNoise denoise -> gentle de-ess/compress -> two-pass loudnorm to -16 LUFS.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
IN="$1"; OUT="$2"; MODEL="${3:-$HERE/models/std.rnnn}"
PRE="aresample=48000,highpass=f=80,bandreject=f=60:width_type=h:w=4,bandreject=f=120:width_type=h:w=4,arnndn=m='$MODEL':mix=0.9,afftdn=nr=12:nf=-50:tn=1,agate=threshold=0.01:ratio=2:range=0.25:attack=10:release=250,acompressor=threshold=-20dB:ratio=3:attack=5:release=120:makeup=2"
# pass 1: measure
STATS=$(ffmpeg -hide_banner -nostats -i "$IN" -af "$PRE,loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json" -f null - 2>&1 | sed -n '/^{/,/^}/p')
get() { echo "$STATS" | python3 -c "import json,sys; print(json.load(sys.stdin)['$1'])"; }
# pass 2: apply measured values (linear normalization, no pumping)
ffmpeg -hide_banner -loglevel error -y -i "$IN" -af "$PRE,loudnorm=I=-16:TP=-1.5:LRA=11:measured_I=$(get input_i):measured_TP=$(get input_tp):measured_LRA=$(get input_lra):measured_thresh=$(get input_thresh):offset=$(get target_offset):linear=true,aresample=48000" -ac 1 "$OUT"
