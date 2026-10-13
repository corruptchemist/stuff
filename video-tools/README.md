# video-tools

Toolkit for building narrated slideshow / documentary-style videos with ffmpeg.

Run `./setup.sh` once per fresh container (installs a Python venv, RNNoise models, and fonts).

| Tool | What it does |
|---|---|
| `clean_voice.sh IN OUT.wav` | Cuts rumble/hum, RNNoise + FFT denoise, gentle gate/compression, two-pass loudnorm to about -16 LUFS. |
| `.venv/bin/auto-editor IN --margin 0.25s -o OUT` | Trims long pauses / dead air. |
| `.venv/bin/python align_words.py AUDIO "transcript"` | Forced-aligns known text to speech and prints word timings as JSON. |
| `.venv/bin/python captions_from_words.py words.json [style.json]` | Word timings to ASS captions with the active word highlighted. |
| `node render_html.js slide.html out.png W H` | Renders an HTML/CSS slide to PNG with headless Chromium. |

Fonts installed: Playfair Display, Cinzel, Special Elite, Old Standard TT, Libre Baskerville, Lora, Oswald, Bebas Neue, Anton, Montserrat.

`test-output/` holds the verification run: noisy vs. cleaned audio, the font specimen, and an end-to-end slide + zoom + captions clip.
