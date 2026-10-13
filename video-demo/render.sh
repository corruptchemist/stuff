#!/usr/bin/env bash
# Reproduce the demo: raw.mp4 -> edited.mp4 (grade, grain, vignette, letterbox, glitch, zoom punch, pop captions).
set -euo pipefail
cd "$(dirname "$0")"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "mandelbrot=s=1280x720:rate=30:start_scale=3:end_scale=0.02" \
  -f lavfi -i "sine=frequency=220:sample_rate=44100,volume=0.15" -t 8 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest raw.mp4
python3 make_captions.py > captions.ass
ffmpeg -hide_banner -loglevel error -y -i raw.mp4 -filter_complex "\
[0:v]eq=contrast=1.15:saturation=1.4:gamma=0.95,curves=preset=increase_contrast,\
zoompan=z='if(between(on,120,128),1.0+0.08*sin((on-120)/8*PI),1)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=30,\
rgbashift=rh=-8:bh=8:enable='between(t,3.9,4.3)',\
noise=alls=10:allf=t,vignette=PI/4.5,\
drawbox=y=0:w=iw:h=60:color=black:t=fill,drawbox=y=ih-60:w=iw:h=60:color=black:t=fill,\
fade=t=in:st=0:d=0.4:color=white,fade=t=out:st=7.4:d=0.6,\
ass=captions.ass[v]" -map "[v]" -map 0:a -c:v libx264 -crf 20 -preset fast -pix_fmt yuv420p -c:a copy edited.mp4
echo "wrote edited.mp4"
