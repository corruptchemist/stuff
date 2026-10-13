"""Draw the simulated home screen: as text for the terminal, or as a PNG."""

from __future__ import annotations

from pathlib import Path

from brainiac.tibasic import COLS, ROWS, Machine, Wait

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

# TI-84 Plus CE look: white screen, dark text, grey status bar, dark bezel.
BEZEL = (38, 41, 48)
SCREEN = (255, 255, 255)
TEXT = (16, 16, 16)
STATUS_BG = (228, 228, 228)
STATUS_TEXT = (40, 90, 170)
HIGHLIGHT = (16, 16, 16)
HIGHLIGHT_TEXT = (255, 255, 255)

# glyphs the font lacks, drawn as the nearest thing it has
FONT_FALLBACK = {"𝑖": "i", "𝑒": "e"}


def menu_rows(wait: Wait) -> list[str]:
    rows = [wait.title[:COLS]]
    for i, text in enumerate(wait.options, 1):
        rows.append(f"{i}:{text}"[:COLS])
    return rows + [""] * (ROWS - len(rows))


def view(m: Machine) -> list[str]:
    """What the calculator is showing right now, one string per row."""
    if m.wait and m.wait.kind == "menu":
        return menu_rows(m.wait)
    return m.screen.lines()


def to_text(m: Machine) -> str:
    rows = view(m)
    top = "┌" + "─" * COLS + "┐"
    bottom = "└" + "─" * COLS + "┘"
    body = [f"│{r:<{COLS}}│" for r in rows]
    if m.wait and m.wait.kind == "menu":
        body[0] = f"│\033[7m{rows[0]:<{COLS}}\033[0m│"
    return "\n".join([top, *body, bottom])


def status_text(m: Machine) -> str:
    mode = "a+bi" if m.complex_mode else "REAL"
    return f"NORMAL FLOAT AUTO {mode} RADIAN MP"


def to_png(m: Machine, path, *, caption: str | None = None, scale: int = 2) -> Path:
    from PIL import Image, ImageDraw, ImageFont

    cw, ch = 12 * scale, 20 * scale          # one character cell
    status_h = 18 * scale
    pad = 4 * scale
    bezel = 14 * scale
    cap_h = 22 * scale if caption else 0

    sw, sh = COLS * cw + 2 * pad, status_h + ROWS * ch + 2 * pad
    img = Image.new("RGB", (sw + 2 * bezel, sh + 2 * bezel + cap_h), BEZEL)
    d = ImageDraw.Draw(img)
    x0, y0 = bezel, bezel
    d.rounded_rectangle([x0 - 3 * scale, y0 - 3 * scale, x0 + sw + 3 * scale, y0 + sh + 3 * scale],
                        radius=6 * scale, fill=(20, 22, 26))
    d.rectangle([x0, y0, x0 + sw, y0 + sh], fill=SCREEN)

    font = ImageFont.truetype(FONT, int(16.5 * scale))
    small = ImageFont.truetype(FONT, int(9.5 * scale))

    # status bar, with the busy dots TI shows while a program is paused
    d.rectangle([x0, y0, x0 + sw, y0 + status_h], fill=STATUS_BG)
    d.text((x0 + pad, y0 + 4 * scale), status_text(m), font=small, fill=STATUS_TEXT)
    if m.wait and m.wait.kind == "pause":
        for i in range(4):
            bx = x0 + sw - (10 + 4 * i) * scale
            d.rectangle([bx, y0 + 7 * scale, bx + 2 * scale, y0 + 9 * scale], fill=TEXT)

    rows = view(m)
    top = y0 + status_h + pad
    for r, line in enumerate(rows):
        y = top + r * ch
        inverted = m.wait and m.wait.kind == "menu" and r == 0
        if inverted:
            d.rectangle([x0 + pad, y, x0 + pad + len(line) * cw, y + ch - scale], fill=HIGHLIGHT)
        for c, glyph in enumerate(line):
            if glyph == " ":
                continue
            glyph = FONT_FALLBACK.get(glyph, glyph)
            x = x0 + pad + c * cw
            if m.wait and m.wait.kind == "menu" and r == 1 and c == 0:
                d.rectangle([x, y, x + cw, y + ch - scale], fill=HIGHLIGHT)
                d.text((x, y), glyph, font=font, fill=HIGHLIGHT_TEXT)
                continue
            d.text((x, y), glyph, font=font, fill=HIGHLIGHT_TEXT if inverted else TEXT)

    # a blinking-cursor block where the program is waiting for typing
    if m.wait and m.wait.kind == "input":
        r = m.screen.row - 1
        c = len(m.wait.prompt)
        if 0 <= r < ROWS and c < COLS:
            x, y = x0 + pad + c * cw, top + r * ch
            d.rectangle([x + scale, y + 2 * scale, x + cw - scale, y + ch - 2 * scale], fill=TEXT)

    if m.wait and m.wait.kind == "error":
        msg = m.wait.message
        bw = (len(msg) + 2) * cw
        bx, by = x0 + (sw - bw) // 2, y0 + sh // 2 - ch
        d.rectangle([bx, by, bx + bw, by + 2 * ch], fill=(255, 255, 255), outline=(200, 30, 30),
                    width=2 * scale)
        d.text((bx + cw, by + ch // 2), msg, font=font, fill=(200, 30, 30))

    if caption:
        cf = ImageFont.truetype(FONT, int(11 * scale))
        d.text((bezel, sh + 2 * bezel - 4 * scale), caption, font=cf, fill=(220, 224, 232))

    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return path
