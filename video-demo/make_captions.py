"""Build word-by-word "pop" captions (ASS) from (word, start, end) timings."""
import sys

HEADER = """[Script Info]
ScriptType: v4.00+
PlayResX: 1280
PlayResY: 720

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Pop,Inter Display Black,72,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,1,0,0,0,100,100,1,0,1,6,3,2,40,40,90,1
Style: Title,Inter Display Black,110,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,1,0,0,0,100,100,4,0,1,0,8,5,0,0,0,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
HIGHLIGHT = r"{\c&H00E5FF&\fscx115\fscy115}"  # yellow (BGR) + bigger
RESET = r"{\c&HFFFFFF&\fscx100\fscy100}"


def ts(t):
    return f"{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}"


def phrase_events(words):
    """words: list of (text, start, end). One event per word; active word highlighted."""
    out = []
    for i, (_, start, end) in enumerate(words):
        parts = [(HIGHLIGHT + w + RESET) if j == i else w for j, (w, _, _) in enumerate(words)]
        out.append(f"Dialogue: 0,{ts(start)},{ts(end)},Pop,,0,0,0,,{' '.join(parts)}")
    return out


if __name__ == "__main__":
    lines = [
        r"Dialogue: 1,0:00:00.20,0:00:01.80,Title,,0,0,0,,{\fad(200,300)\fscx60\fscy60\t(0,250,\fscx100\fscy100)\blur2}INTO THE {\c&H00E5FF&}FRACTAL",
    ]
    for phrase in [
        [("WAIT", 2.0, 2.3), ("FOR", 2.3, 2.6), ("IT...", 2.6, 4.0)],
        [("IT", 4.0, 4.3), ("NEVER", 4.3, 4.7), ("ENDS", 4.7, 6.0)],
        [("FOLLOW", 6.0, 6.5), ("FOR", 6.5, 6.9), ("MORE", 6.9, 7.8)],
    ]:
        lines += phrase_events(phrase)
    sys.stdout.write(HEADER + "\n".join(lines) + "\n")
