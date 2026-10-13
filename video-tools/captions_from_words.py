"""captions_from_words.py words.json [style.json] > captions.ass
Groups aligned words into short phrases (max N words, breaks on pauses) and highlights the active word."""
import json, sys

words = json.load(open(sys.argv[1]))
st = {"font": "Montserrat", "size": 64, "weight": -1, "max_words": 4, "gap": 0.35,
      "fill": "FFFFFF", "hi": "6AC9E8", "outline": "000000", "w": 1920, "h": 1080, "margin_v": 90,
      **(json.load(open(sys.argv[2])) if len(sys.argv) > 2 else {})}


def ts(t):
    return f"{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}"


def bgr(hex6):
    return hex6[4:6] + hex6[2:4] + hex6[0:2]


phrases, cur = [], []
for w in words:
    if cur and (len(cur) >= st["max_words"] or w[1] - cur[-1][2] > st["gap"]):
        phrases.append(cur); cur = []
    cur.append(w)
if cur:
    phrases.append(cur)

print(f"""[Script Info]
ScriptType: v4.00+
PlayResX: {st['w']}
PlayResY: {st['h']}
WrapStyle: 2

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Cap,{st['font']},{st['size']},&H00{bgr(st['fill'])},&H00{bgr(st['fill'])},&H00{bgr(st['outline'])},&H96000000,{st['weight']},0,0,0,100,100,0,0,1,5,2,2,60,60,{st['margin_v']},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text""")
for p in phrases:
    for i, (_, start, end) in enumerate(p):
        end = p[i + 1][1] if i + 1 < len(p) else end + 0.25  # hold last word briefly; no flicker between words
        text = " ".join(
            (rf"{{\c&H{bgr(st['hi'])}&}}{w.upper()}{{\c&H{bgr(st['fill'])}&}}" if j == i else w.upper())
            for j, (w, _, _) in enumerate(p))
        print(f"Dialogue: 0,{ts(start)},{ts(end)},Cap,,0,0,0,,{text}")
