#!/usr/bin/env python3
"""Pressure test for GEOMETRY (brainiac/src/GEOMETRY.txt) on the simulated TI-84 Plus CE.

    python stress/geometry.py              # full run; exit code 0 = everything passed
    python stress/geometry.py --quick      # a fifth of the random cases
    python stress/geometry.py --show       # also print the figures of the extreme showcase cases
    GEOMETRY_SRC=my/GEOMETRY.txt python stress/geometry.py   # test a modified copy of the source

Every solver gets random inputs from a fixed seed: lengths from 0.001 to 100000 typed as
integers, decimals, fractions (3/4) and roots (2sqrt(3)), plus invalid inputs (0, negatives,
impossible triangles, out-of-range angles, too many / too few knowns, not-a-circle).
Each case runs with the calculator starting in Degree and in Radian (and a quarter of them
also in a+bi, the way Brainiac leaves the calculator when it runs GEOMETRY).

Every printed number (report lines and figure labels) is checked against formulas written
here with mpmath, allowing for the program's rounding (3 places, angles 2) and the
10-digit display. Also checked: no ERR, leaks == 0, blocks == [], the #call stack is empty
at every menu, Text( rows/columns are whole numbers in 0..150 / 0..264, figure lines stay
on the screen, labels don't run off the right edge (estimated at 4-6 px a character),
report lines don't overwrite each other, Disp/menu text fits, AGAIN / GEOMETRY MENU / BACK
go where they should, leaving restores the window, angle mode, axes and deletes the lists,
called from BRAINIAC it comes back to Brainiac's menu, and 30 rounds in one session leak
nothing.

Failures are printed with the exact answer sequence from a fresh start (and the angle mode).
"""

from __future__ import annotations

import argparse
import math
import os
import random
import re
import sys
import time
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path

import mpmath as mp

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import brainiac.build as build  # noqa: E402
from brainiac.build import PROGRAMS, program_bytes, source_lines  # noqa: E402
from brainiac.screen import view  # noqa: E402
from brainiac.tibasic import GH, GW, Graph, Machine, Screen, tokens_from_bytes  # noqa: E402

# GEOMETRY_SRC=path/to/GEOMETRY.txt tests a modified copy of the source (to try a fix
# without touching brainiac/src)
if os.environ.get("GEOMETRY_SRC"):
    _source_path = build.source_path
    build.source_path = lambda name=build.NAME: (Path(os.environ["GEOMETRY_SRC"]).resolve()
                                                if name == "GEOMETRY" else _source_path(name))

mp.mp.dps = 40
SEED = 20261014
COUNT = Counter()          # how much got checked
ONCE = set()               # problems about fixed text: report each one once
TOK = {n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}

# ----------------------------------------------------------------- instrumentation
# Log every Output( (to rebuild the report lines and catch lines that overwrite each
# other), every Disp (to catch text longer than the screen) and every Line( (to see the
# figure's real end points, which the simulator clips silently).

_screen_clear, _screen_output, _screen_disp = Screen.clear, Screen.output, Screen.disp
_graph_clear, _graph_line = Graph.clear, Graph.line


def screen_clear(self):
    _screen_clear(self)
    self.owner = {}                         # cell -> number of the Output that wrote it
    if not hasattr(self, "olog"):
        self.olog, self.long_disp = [], []


def screen_output(self, row, col, text):
    n = len(self.olog)
    cells, r, c, past_bottom = [], row - 1, col - 1, False
    for _ in text:
        if r >= 10:
            past_bottom = True
            break
        cells.append((r, c))
        c += 1
        if c >= 26:
            r, c = r + 1, 0
    hit = sorted({self.owner[x] for x in cells if x in self.owner})
    self.olog.append({"row": row, "col": col, "text": text, "overwrites": hit,
                      "past_bottom": past_bottom})
    for x in cells:
        self.owner[x] = n
    _screen_output(self, row, col, text)


def screen_disp(self, text, right=False):
    if len(text) > 26:
        self.long_disp.append(text)
    _screen_disp(self, text, right)


def graph_clear(self):
    _graph_clear(self)
    self.raw = []


def graph_line(self, x1, y1, x2, y2):
    self.raw.append((x1, y1, x2, y2))
    if max(abs(x1), abs(y1), abs(x2), abs(y2)) > 1e5 or not all(
            math.isfinite(v) for v in (x1, y1, x2, y2)):
        return                              # would take forever to plot; flagged later
    _graph_line(self, x1, y1, x2, y2)


Screen.clear, Screen.output, Screen.disp = screen_clear, screen_output, screen_disp
Graph.clear, Graph.line = graph_clear, graph_line

# ----------------------------------------------------------------- numbers


@dataclass(frozen=True)
class Num:
    """A number the program should show: x rounded to `places` (TI: 10 digits at most)."""
    x: object
    places: int = 3


def N(x):
    return Num(x, 3)


def A(x):
    return Num(x, 2)


NUM_RE = r"(⁻?(?:\d+\.?\d*|\.\d+)(?:ᴇ⁻?\d+)?)"


def parse_ti(s: str) -> float:
    return float(s.replace("⁻", "-").replace("ᴇ", "e"))


def r_half_up(x, places):
    q = mp.mpf(10) ** places
    v = mp.floor(abs(x) * q + mp.mpf("0.5")) / q
    return v if x >= 0 else -v


def match(pattern, text):
    """None if `text` reads as `pattern` (strings literally, Num within rounding), else why not."""
    rx = "".join(re.escape(p) if isinstance(p, str) else NUM_RE for p in pattern)
    mo = re.fullmatch(rx, text)
    if not mo:
        return "text"
    nums = [p for p in pattern if isinstance(p, Num)]
    for got, want in zip(mo.groups(), nums):
        x = float(want.x)
        # rounded to `places`, then shown with at most 10 significant digits; the
        # calculator's own 14-digit arithmetic adds a hair more
        e = math.floor(math.log10(abs(x))) if x else 0
        tol = (0.5 * 10 ** -want.places + 0.5 * 10 ** (e - 9)) * (1 + 1e-9) + 1e-12 * abs(x) + 1e-13
        COUNT["numbers"] += 1
        if abs(parse_ti(got) - x) > tol:
            return f"number {got} should be {mp.nstr(want.x, 12)}"
    return None


def show(pattern):
    out = ""
    for p in pattern:
        out += p if isinstance(p, str) else "{" + mp.nstr(p.x, 10) + "}"
    return out

# ----------------------------------------------------------------- typed values


@dataclass(frozen=True)
class V:
    text: str          # what is typed
    x: object          # its exact value (mpf)


def v_int(n):
    return V(str(n) if n >= 0 else "-" + str(-n), mp.mpf(n))


def v_dec(s):
    return V(s, mp.mpf(s))


def neg(v: V) -> V:
    if v.x == 0:
        return v
    return V("-" + v.text if not v.text.startswith("-") else v.text[1:], -v.x)


def fmt_dec(x: float, digits: int) -> str:
    s = f"{x:.{digits}f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return s


def gen_len(rng, lo=1e-3, hi=1e5, style=None) -> V:
    """A positive length, 0.001..100000, typed one of several ways."""
    for _ in range(50):
        x = 10 ** rng.uniform(math.log10(lo), math.log10(hi))
        st = style or rng.choice(["int", "dec", "dec", "dec", "frac", "sqrt", "small"])
        if st == "int" and x >= 1:
            v = v_int(max(1, round(x)))
        elif st == "dec" or (st == "int" and x < 1):
            d = rng.choice([1, 2, 3, 4]) if x >= 1 else rng.choice([3, 4, 5])
            s = fmt_dec(x, d)
            v = v_dec(s)
        elif st == "frac":
            q = rng.randint(2, 16)
            p = max(1, round(x * q))
            if x < 0.01:
                q = rng.choice([1000, 3000, 7000])
                p = max(1, round(x * q))
            v = V(f"{p}/{q}", mp.mpf(p) / q)
        elif st == "sqrt":
            n = rng.choice([2, 3, 5, 6, 7, 10, 11, 13, 17, 19, 23, 50, 99])
            k = x / math.sqrt(n)
            if k >= 2:
                k = round(k)
                v = V(f"{k}sqrt({n})", k * mp.sqrt(n))
            elif k >= 0.5:
                v = V(f"sqrt({n})", mp.sqrt(n))
            else:
                q = max(2, round(1 / k))
                v = V(f"sqrt({n})/{q}", mp.sqrt(n) / q)
        else:  # small: plain decimal with significant digits
            s = f"{x:.4g}"
            if "e" in s:
                s = fmt_dec(x, 8)
            v = v_dec(s)
        if v.x > 0 and lo * 0.5 <= v.x <= hi * 2:
            return v
    return v_int(1)


def gen_angle(rng, lo, hi) -> V:
    """An angle strictly between lo and hi."""
    for _ in range(100):
        st = rng.choice(["int", "dec", "dec", "frac", "tiny"])
        if st == "tiny":
            x = rng.choice([lo + 0.001, lo + 0.01, hi - 0.01, hi - 0.001, lo + 0.5, hi - 0.5])
            v = v_dec(fmt_dec(x, 3))
        else:
            x = rng.uniform(lo, hi)
            if st == "int":
                v = v_int(round(x))
            elif st == "dec":
                v = v_dec(fmt_dec(x, rng.choice([1, 2, 3])))
            else:
                q = rng.choice([2, 3, 4, 7, 8])
                p = round(x * q)
                v = V(f"{p}/{q}", mp.mpf(p) / q)
        if lo < v.x < hi:
            return v
    return v_dec(fmt_dec((lo + hi) / 2, 2))


def gen_real(rng, lo=1e-3, hi=1e4) -> V:
    """Any real: 0, small or big, either sign (circle centres, equation coefficients)."""
    r = rng.random()
    if r < 0.12:
        return v_int(0)
    v = gen_len(rng, lo, hi, style=rng.choice(["int", "int", "dec", "frac"]))
    return neg(v) if rng.random() < 0.5 else v

# ----------------------------------------------------------------- expectations


@dataclass
class Expect:
    ok: bool
    lines: list = field(default_factory=list)       # report lines (patterns)
    labels: list = field(default_factory=list)      # figure labels incl. titles (patterns)
    msgs: tuple = ()                                # good error messages (for invalid input)
    flag: str = ""                                  # input the program should refuse
    order: list = field(default_factory=list)       # labels in reading order (il, pl)


ERRS = {"tri": "THOSE DON'T MAKE A", "one0": "TYPE 0 FOR EXACTLY ONE OF",
        "two0": "TYPE 0 FOR EXACTLY TWO OF", "hyp": "THE HYPOTENUSE MUST BE",
        "ang": "THAT IS OUT OF RANGE.", "pos": "LENGTHS MUST BE POSITIVE.",
        "circ": "THAT ISN'T A CIRCLE:", "leg": "A SLANTED SIDE CAN'T BE"}

PI = mp.pi


def sind(x):
    return mp.sin(x * PI / 180)


def cosd(x):
    return mp.cos(x * PI / 180)


def bad(*msgs, flag=""):
    return Expect(False, msgs=msgs, flag=flag)


def heron(a, b, c):
    s = (a + b + c) / 2
    return s, mp.sqrt(s * (s - a) * (s - b) * (s - c))


def is_tri(a, b, c):
    return a > 0 and b > 0 and c > 0 and a + b > c and a + c > b and b + c > a


def o_ta(U, V_):
    if U <= 0 or V_ <= 0 or U + V_ >= 180:
        return bad("tri", "ang")
    W = 180 - U - V_
    return Expect(True, [["ANGLES IN A TRIANGLE ADD"], ["UP TO 180, SO"],
                         ["C=180-", A(U), "-", A(V_)], [" =", A(W)]],
                  [["A=", A(U)], ["B=", A(V_)], ["C=", A(W)], ["A+B+C=180"]])


def o_py(a, b, c):
    zeros = (a == 0) + (b == 0) + (c == 0)
    if zeros != 1:
        return bad("one0")
    if a < 0 or b < 0 or c < 0:
        return bad("pos")
    if c != 0 and c <= max(a, b):
        return bad("hyp")
    if c == 0:
        c = mp.sqrt(a * a + b * b)
        step = ["C=√(", N(a * a), "+", N(b * b), ")"]
    elif a == 0:
        a = mp.sqrt(c * c - b * b)
        step = ["A=√(", N(c * c), "-", N(b * b), ")"]
    else:
        b = mp.sqrt(c * c - a * a)
        step = ["B=√(", N(c * c), "-", N(a * a), ")"]
    return Expect(True, [["A²+B²=C²"], [N(a * a), "+", N(b * b), "=", N(c * c)], step,
                         ["A=", N(a)], ["B=", N(b)], ["C=", N(c)]],
                  [["90"], ["A=", N(a)], ["C=", N(c)], ["B=", N(b)], ["A²+B²=C²"]])


def o_sp45(leg, hyp):
    if (leg == 0) + (hyp == 0) != 1:
        return bad("one0")
    if leg < 0 or hyp < 0:
        return bad("pos")
    if leg == 0:
        leg = hyp / mp.sqrt(2)
    hyp = leg * mp.sqrt(2)
    return Expect(True, [["LEG=", N(leg)], ["HYP=LEG×√(2)=", N(leg), "×√(2)"], [" =", N(hyp)]],
                  [["90"], ["45"], ["45"], ["LEG=", N(leg)], ["HYP=", N(hyp)], ["LEG=", N(leg)],
                   ["HYP=LEG×√(2)"]])


def o_sp30(s, l, h):
    if (s == 0) + (l == 0) + (h == 0) != 2:
        return bad("two0")
    if s < 0 or l < 0 or h < 0:
        return bad("pos")
    if l:
        s = l / mp.sqrt(3)
    if h:
        s = h / 2
    l, h = s * mp.sqrt(3), 2 * s
    return Expect(True, [["SHORT LEG S=", N(s)], ["LONG LEG=S×√(3)=", N(s), "×√(3)"], [" =", N(l)],
                         ["HYPOTENUSE=2S=", N(h)]],
                  [["90"], ["30"], ["60"], ["LONG=", N(l)], ["HYP=", N(h)], ["SHORT=", N(s)],
                   ["SHORT S, LONG S√(3), HYP 2S"]])


def o_tg(a, o, d, h):
    if (a == 0) + (o == 0) + (d == 0) + (h == 0) != 2:
        return bad("two0")
    if a >= 90 or min(a, o, d, h) < 0:
        return bad("ang", "pos")
    if a:
        title = ("OPP" if o else "ADJ" if d else "HYP") + " AND ANGLE KNOWN"
        if o:
            h = o / sind(a)
        elif d:
            h = d / cosd(a)
        o, d = h * sind(a), h * cosd(a)
    else:
        if h and (o >= h or d >= h):
            return bad("hyp")
        if o and h:
            title, a = "SIN(A)=OPP/HYP", mp.asin(o / h) * 180 / PI
            d = mp.sqrt(h * h - o * o)
        elif d and h:
            title, a = "COS(A)=ADJ/HYP", mp.acos(d / h) * 180 / PI
            o = mp.sqrt(h * h - d * d)
        else:
            title, a = "TAN(A)=OPP/ADJ", mp.atan(o / d) * 180 / PI
            h = mp.sqrt(o * o + d * d)
    return Expect(True, [[title], ["SIN(", A(a), ")=OPP/HYP"], [" OPP=", N(o)],
                         ["COS(", A(a), ")=ADJ/HYP"], [" ADJ=", N(d)],
                         ["TAN(", A(a), ")=OPP/ADJ"], [" HYP=", N(h)], ["ANGLE A=", A(a)],
                         ["OTHER ANGLE=90-A=", A(90 - a)]],
                  [["A=", A(a)], ["90"], ["ADJ=", N(d)], ["OPP=", N(o)], ["HYP=", N(h)], [title]])


def o_sm(a, b, c, a2=None):
    if not is_tri(a, b, c):
        return bad("tri", "pos")
    if a2 <= 0:
        return bad("pos")
    k = a2 / a
    return Expect(True, [["K=A'/A=", N(k)], ["B'=B×K=", N(k * b)], ["C'=C×K=", N(k * c)],
                         ["AREA OF 2=AREA OF 1×K²"], [" (×", N(k * k), ")"]],
                  [[N(c)], [N(a)], [N(b)], [N(k * c)], [N(a2)], [N(k * b)],
                   ["SCALE FACTOR K=", N(k)]])


def o_il(t):
    if t <= 0 or t >= 180:
        return bad("ang")
    u = 180 - t
    e = Expect(True, [["ACROSS FROM EACH OTHER:"], ["EQUAL, SO ", A(t), " AND ", A(t), "."],
                      ["NEXT TO EACH OTHER: ADD"], ["TO 180, SO THE OTHER TWO"],
                      ["ARE 180-", A(t), "=", A(u), "."]],
               [[A(t)], [A(t)], [A(u)], [A(u)], ["VERTICAL ANGLES ARE EQUAL"]])
    e.order = [[A(u)], [A(t)], [A(t)], [A(u)]]      # top, left, right, bottom
    return e


def o_pl(t):
    if t <= 0 or t >= 180:
        return bad("ang")
    u = 180 - t
    e = Expect(True, [["ONLY TWO SIZES APPEAR:"], [A(t), " (ANGLES 1,4,5,8) AND"],
                      ["180-", A(t), "=", A(u), " (2,3,6,7)."], ["CORRESPONDING, ALTERNATE"],
                      ["AND VERTICAL ANGLES ARE"], ["EQUAL; ANGLES ON A LINE"], ["ADD TO 180."]],
               [[A(x)] for x in (t, u, u, t, t, u, u, t)] + [["ONLY TWO SIZES APPEAR"]])
    e.order = [[A(x)] for x in (t, u, u, t, t, u, u, t)]
    return e


def o_at3(a, b, c):
    if not is_tri(a, b, c):
        return bad("tri", "pos")
    s, t = heron(a, b, c)
    return Expect(True, [["PERIMETER=A+B+C=", N(2 * s)], ["HERON: S=HALF PERIMETER"],
                         [" S=", N(s)], ["AREA=√(S(S-A)(S-B)(S-C))"], [" =", N(t)]],
                  [[N(c)], [N(a)], [N(b)], ["AREA=", N(t)]])


def o_atb(u, h):
    if u <= 0 or h <= 0:
        return bad("pos")
    return Expect(True, [["AREA=BASE×HEIGHT/2"], [" =", N(u * h / 2)]],
                  [["B=", N(u)], ["H=", N(h)], ["AREA=BH/2=", N(u * h / 2)]])


def o_rec(l, w):
    if l <= 0 or w <= 0:
        return bad("pos")
    d = mp.sqrt(l * l + w * w)
    return Expect(True, [["AREA=L×W=", N(l * w)], ["PERIMETER=2(L+W)=", N(2 * (l + w))],
                         ["DIAGONAL=√(L²+W²)=", N(d)]],
                  [["L=", N(l)], ["W=", N(w)], ["D=", N(d)], ["AREA=", N(l * w)]])


def o_trap(u, v, h, s3=None, s4=None):
    if u <= 0 or v <= 0 or h <= 0:
        return bad("pos")
    area = (u + v) * h / 2
    lines = [["AREA=(B1+B2)/2×H"], [" =", N(area)]]
    labels = [["B1=", N(u)], ["B2=", N(v)], ["H=", N(h)], ["AREA=", N(area)]]
    if s3 < 0 or s4 < 0:
        return bad("pos")
    if (0 < s3 < h) or (0 < s4 < h):
        return bad("leg")
    if s3 > 0:
        labels.append(["S3=", N(s3)])
    if s4 > 0:
        labels.append(["S4=", N(s4)])
    if s3 > 0 and s4 > 0:
        lines += [["PERIMETER=B1+B2+S3+S4"], [" =", N(u + v + s3 + s4)]]
    return Expect(True, lines, labels)


def o_box(l, w, h):
    if min(l, w, h) <= 0:
        return bad("pos")
    return Expect(True, [["VOLUME=L×W×H=", N(l * w * h)], ["SURFACE=2(LW+LH+WH)"],
                         [" =", N(2 * (l * w + l * h + w * h))],
                         ["SPACE DIAGONAL=", N(mp.sqrt(l * l + w * w + h * h))]],
                  [["L=", N(l)], ["W=", N(w)], ["H=", N(h)], ["VOLUME=", N(l * w * h)]])


def o_cyl(r, h):
    if min(r, h) <= 0:
        return bad("pos")
    vol = PI * r * r * h
    return Expect(True, [["VOLUME=πR²H=", N(vol)], ["SURFACE=2πR²+2πRH"],
                         [" =", N(2 * PI * r * r + 2 * PI * r * h)],
                         ["CURVED PART ONLY=", N(2 * PI * r * h)]],
                  [["R=", N(r)], ["H=", N(h)], ["VOLUME=", N(vol)]])


def o_cone(r, h):
    if min(r, h) <= 0:
        return bad("pos")
    sl = mp.sqrt(r * r + h * h)
    vol = PI * r * r * h / 3
    return Expect(True, [["VOLUME=πR²H/3=", N(vol)], ["SLANT L=√(R²+H²)=", N(sl)],
                         ["SURFACE=πR²+πRL=", N(PI * r * r + PI * r * sl)]],
                  [["R=", N(r)], ["H=", N(h)], ["SLANT=", N(sl)], ["VOLUME=", N(vol)]])


def o_sph(r):
    if r <= 0:
        return bad("pos")
    vol = 4 * PI * r ** 3 / 3
    return Expect(True, [["VOLUME=4πR³/3=", N(vol)], ["SURFACE=4πR²=", N(4 * PI * r * r)]],
                  [["R=", N(r)], ["VOLUME=", N(vol)]])


def o_pyr(s, h):
    if min(s, h) <= 0:
        return bad("pos")
    sl = mp.sqrt(h * h + (s / 2) ** 2)
    vol = s * s * h / 3
    return Expect(True, [["VOLUME=S²H/3=", N(vol)], ["SLANT=√(H²+(S/2)²)=", N(sl)],
                         ["SURFACE=S²+2S×SLANT"], [" =", N(s * s + 2 * s * sl)]],
                  [["S=", N(s)], ["H=", N(h)], ["VOLUME=", N(vol)]])


def o_cs(r, d, c, a):
    known = [x for x in (r, d, c, a) if x != 0]
    if any(x < 0 for x in known):
        e = bad("pos")
        if sum(x > 0 for x in known) == 1:
            e.flag = "a negative value was typed, yet it solved from the other one"
        return e
    if len(known) != 1:
        return bad("one0")
    if d:
        r = d / 2
    if c:
        r = c / (2 * PI)
    if a:
        r = mp.sqrt(a / PI)
    return Expect(True, [["RADIUS R=", N(r)], ["DIAMETER=2R=", N(2 * r)],
                         ["CIRCUMFERENCE=2πR=", N(2 * PI * r)], ["AREA=πR²=", N(PI * r * r)]],
                  [["R=", N(r)], ["D=", N(2 * r)], ["AREA=", N(PI * r * r)], ["C=", N(2 * PI * r)]])


def o_arc(r, t):
    if r <= 0 or t <= 0 or t > 360:
        return bad("ang", "pos")
    arc, sec = 2 * PI * r * t / 360, PI * r * r * t / 360
    return Expect(True, [["FRACTION=T/360=", N(t / 360)], ["ARC=FRACTION×2πR=", N(arc)],
                         ["SECTOR=FRACTION×πR²=", N(sec)],
                         ["CHORD=2R×SIN(T/2)=", N(2 * r * sind(t / 2))]],
                  [["T=", A(t)], ["R=", N(r)], ["ARC=", N(arc)], ["SECTOR=", N(sec)]])


def _circle_eq(h, k, c):
    def centre_part(x):
        r = r_half_up(x, 3)
        if r > 0:
            return ["-", N(x)]
        if r < 0:
            return ["+", N(-x)]
        return []

    def term(x, suffix):
        r = r_half_up(x, 3)
        if r > 0:
            return ["+", N(x)] + ([suffix] if suffix else [])
        if r < 0:
            return ["-", N(-x)] + ([suffix] if suffix else [])
        return []
    std = ["(X"] + centre_part(h) + [")²+(Y"] + centre_part(k) + [")²=", N(c * c)]
    gen = ["X²+Y²"] + term(-2 * h, "X") + term(-2 * k, "Y") + term(h * h + k * k - c * c, "") + ["=0"]
    return Expect(True, [["CENTER (", N(h), ",", N(k), ")"], ["RADIUS ", N(c)], ["STANDARD FORM:"],
                         std, ["GENERAL FORM:"], gen],
                  [["(", N(h), ",", N(k), ")"], ["R=", N(c)], ["(X-H)²+(Y-K)²=R²"]])


def o_ceb(h, k, c):
    if c <= 0:
        return bad("pos")
    return _circle_eq(h, k, c)


def o_cer(d, e, f):
    s = d * d / 4 + e * e / 4 - f
    if s <= 0:
        return bad("circ")
    return _circle_eq(-d / 2, -e / 2, mp.sqrt(s))


def o_sc(k):
    if k <= 0:
        return bad("pos")
    return Expect(True, [["SCALE FACTOR K=", N(k)], ["LENGTHS ×K=×", N(k)], ["AREA ×K²=×", N(k * k)],
                         ["VOLUME ×K³=×", N(k ** 3)]],
                  [["1"], [N(k)], ["LENGTHS ×", N(k)]])

# ----------------------------------------------------------------- the solvers


@dataclass
class Solver:
    name: str
    paths: list            # menu answers from the GEOMETRY menu (one is picked per case)
    prompts: list
    oracle: object
    title: str             # the reference figure's title
    again: tuple           # what AGAIN shows: ("ref", title) or ("menu", menu title)
    sub: str = ""          # answer to the menu after the reference figure (circle equation)


S = {}


def solver(name, paths, prompts, oracle, title, again=None, sub=""):
    S[name] = Solver(name, paths, prompts, oracle, title, again or ("ref", title), sub)


solver("ta", [["1", "1"], ["2", "1"]], ["ANGLE A=", "ANGLE B="], o_ta, "A, B, C: THE THREE ANGLES")
solver("py", [["1", "2"]], ["A=", "B=", "C="], o_py, "A, B: LEGS.  C: HYPOTENUSE")
solver("sp45", [["1", "3", "1"]], ["LEG=", "HYP="], o_sp45, "45-45-90: LEG, LEG, LEG×√(2)",
       ("menu", "SPECIAL RIGHT TRIANGLE"))
solver("sp30", [["1", "3", "2"]], ["SHORT=", "LONG=", "HYP="], o_sp30, "30-60-90: SHORT, LONG, HYP",
       ("menu", "SPECIAL RIGHT TRIANGLE"))
solver("tg", [["1", "4"]], ["ANGLE A=", "OPP=", "ADJ=", "HYP="], o_tg,
       "SIN=OPP/HYP COS=ADJ/HYP TAN=OPP/ADJ")
solver("sm", [["1", "5"]], ["A=", "B=", "C=", "A'="], o_sm, "TRIANGLE 2 MATCHES: A'=K×A")
solver("il", [["2", "2"]], ["ANGLE="], o_il, "TYPE ANY ONE OF THE FOUR")
solver("pl", [["2", "3"]], ["ANGLE 1="], o_pl, "THE 8 ANGLES: TYPE ANGLE 1")
solver("at3", [["3", "1", "1"]], ["A=", "B=", "C="], o_at3, "THE THREE SIDES A, B, C",
       ("menu", "TRIANGLE AREA"))
solver("atb", [["3", "1", "2"]], ["B=", "H="], o_atb, "BASE B, HEIGHT H (AT 90)",
       ("menu", "TRIANGLE AREA"))
solver("rec", [["3", "2"]], ["L=", "W="], o_rec, "LENGTH L, WIDTH W, DIAGONAL D")
solver("trap", [["3", "4"]], ["B1=", "B2=", "H=", "S3=", "S4="], o_trap, "B1, B2 PARALLEL; H BETWEEN")
solver("box", [["4", "1"]], ["L=", "W=", "H="], o_box, "LENGTH L, WIDTH W, HEIGHT H")
solver("cyl", [["4", "2"]], ["R=", "H="], o_cyl, "RADIUS R, HEIGHT H")
solver("cone", [["4", "3"]], ["R=", "H="], o_cone, "RADIUS R, HEIGHT H")
solver("sph", [["4", "4"]], ["R="], o_sph, "RADIUS R")
solver("pyr", [["4", "5"]], ["S=", "H="], o_pyr, "BASE SIDE S, HEIGHT H")
solver("cs", [["5", "1"], ["3", "3"]], ["R=", "D=", "C=", "AREA="], o_cs, "R RADIUS, D DIAMETER, C AROUND")
solver("arc", [["5", "2"]], ["R=", "T="], o_arc, "RADIUS R, CENTRAL ANGLE T")
solver("ceb", [["5", "3"]], ["H=", "K=", "R="], o_ceb, "CENTER (H,K), RADIUS R", sub="1")
solver("cer", [["5", "3"]], ["D=", "E=", "F="], o_cer, "CENTER (H,K), RADIUS R", sub="2")
solver("sc", [["6"]], ["K="], o_sc, "EVERY LENGTH TIMES K")


@dataclass
class Case:
    solver: str
    vals: list            # V per prompt (fewer if the program stops asking early)
    path: list = None
    tag: str = ""

# ----------------------------------------------------------------- generators


def zero():
    return v_int(0)


def maybe_neg(rng, v):
    return neg(v)


def gen_cases(rng, scale):
    cases = []

    def add(name, vals, tag=""):
        cases.append(Case(name, vals, tag=tag))

    L = lambda: gen_len(rng)              # noqa: E731
    n = lambda k: max(4, int(k * scale))  # noqa: E731

    # -- missing angle
    for _ in range(n(90)):
        u = gen_angle(rng, 0, 179.99)
        w = gen_angle(rng, 0, float(180 - u.x)) if 180 - u.x > 0.002 else v_dec("0.001")
        add("ta", [u, w])
    for _ in range(n(25)):
        r = rng.random()
        if r < 0.3:
            u = gen_angle(rng, 0, 180)
            if "/" in u.text:
                add("ta", [u, gen_angle(rng, float(180 - u.x), 400)], "sum>180")
            else:   # exactly 180 together
                rest = Decimal(180) - Decimal(u.text)
                add("ta", [u, v_dec(str(rest))], "sum=180")
        elif r < 0.5:
            add("ta", [v_int(0), gen_angle(rng, 0, 179)], "zero")
        elif r < 0.7:
            add("ta", [neg(gen_angle(rng, 0, 90)), gen_angle(rng, 0, 90)], "negative")
        else:
            add("ta", [gen_angle(rng, 90, 179), gen_angle(rng, 90, 179)], "sum>=180")
    for u, w in [("90", "90"), ("100", "80"), ("179", "1"), ("179", "0.5"), ("0.01", "0.01"),
                 ("0.001", "0.001"), ("0.0001", "0.0001"), ("1", "0.001"), ("179.9", "0.01"),
                 ("60", "60"), ("45", "45"), ("100", "79.99")]:
        add("ta", [v_dec(u), v_dec(w)], "edge")

    # -- pythagorean
    for _ in range(n(90)):
        x, y = L(), L()
        f = rng.choice([1, 2, 3])
        if f == 3:
            add("py", [x, y, zero()])
        else:
            if x.x == y.x:
                continue
            lo, hi = (x, y) if x.x < y.x else (y, x)
            add("py", [zero(), lo, hi] if f == 1 else [lo, zero(), hi])
    for _ in range(n(25)):
        x, y, z = L(), L(), L()
        r = rng.random()
        if r < 0.25:
            add("py", [x, y, z], "three known")
        elif r < 0.4:
            add("py", [zero(), zero(), z], "two unknown")
        elif r < 0.5:
            add("py", [zero(), zero(), zero()], "all zero")
        elif r < 0.75:
            lo, hi = sorted([x, y], key=lambda v: v.x)
            add("py", [zero(), hi, lo], "short hypotenuse")
        elif r < 0.85:
            add("py", [x, zero(), x], "hypotenuse = leg")
        else:
            add("py", [neg(x), y, zero()], "negative")
    for a, b, c in [("3", "4", "0"), ("0", "12", "13"), ("1", "1", "0"), ("0.001", "100000", "0"),
                    ("100000", "100000", "0"), ("0", "99999.999", "100000"), ("0.001", "0", "0.0011")]:
        add("py", [v_dec(a), v_dec(b), v_dec(c)], "edge")

    # -- special right triangles
    for _ in range(n(45)):
        x = L()
        add("sp45", [x, zero()] if rng.random() < 0.5 else [zero(), x])
    for _ in range(n(12)):
        r = rng.random()
        add("sp45", [L(), L()] if r < 0.4 else [zero(), zero()] if r < 0.6 else
            [neg(L()), zero()] if r < 0.8 else [zero(), neg(L())], "invalid")
    for _ in range(n(45)):
        x = L()
        i = rng.randrange(3)
        vals = [zero(), zero(), zero()]
        vals[i] = x
        add("sp30", vals)
    for _ in range(n(12)):
        r = rng.random()
        if r < 0.4:
            vals = [L(), L(), zero()]
            rng.shuffle(vals)
        elif r < 0.55:
            vals = [L(), L(), L()]
        elif r < 0.7:
            vals = [zero(), zero(), zero()]
        else:
            vals = [zero(), zero(), zero()]
            vals[rng.randrange(3)] = neg(L())
        add("sp30", vals, "invalid")

    # -- trig
    for _ in range(n(100)):
        mode = rng.choice(["AO", "AD", "AH", "OH", "DH", "OD"])
        a = gen_angle(rng, 0, 90)
        x, y = L(), L()
        lo, hi = sorted([x, y], key=lambda v: v.x)
        if lo.x == hi.x:
            continue
        vals = {"AO": [a, x, zero(), zero()], "AD": [a, zero(), x, zero()],
                "AH": [a, zero(), zero(), x], "OH": [zero(), lo, zero(), hi],
                "DH": [zero(), zero(), lo, hi], "OD": [zero(), x, y, zero()]}[mode]
        add("tg", vals)
    for _ in range(n(35)):
        r = rng.random()
        x, y = L(), L()
        lo, hi = sorted([x, y], key=lambda v: v.x)
        if r < 0.2:
            add("tg", [zero(), hi, zero(), lo], "opp > hyp")
        elif r < 0.35:
            add("tg", [zero(), zero(), hi, lo], "adj > hyp")
        elif r < 0.5:
            add("tg", [zero(), x, zero(), x], "opp = hyp")
        elif r < 0.6:
            add("tg", [zero(), zero(), x, x], "adj = hyp")
        elif r < 0.7:
            add("tg", [v_dec(rng.choice(["90", "120", "180", "360"])), x, zero(), zero()], "angle >= 90")
        elif r < 0.8:
            add("tg", [gen_angle(rng, 0, 89), x, y, zero()], "three known")
        elif r < 0.9:
            add("tg", [zero(), zero(), zero(), x], "one known")
        else:
            add("tg", [gen_angle(rng, 0, 89), neg(x), zero(), zero()], "negative")
    for vals in [("35", "0", "0", "10"), ("0", "3", "4", "0"), ("89.999", "1", "0", "0"),
                 ("0.001", "100000", "0", "0"), ("0", "100000", "0.001", "0"),
                 ("0", "0.001", "100000", "0"), ("0", "99999.999", "0", "100000"),
                 ("0", "0", "99999.999", "100000"), ("0", "5", "0", "5"), ("0", "0", "5", "5"),
                 ("0", "5", "0", "3"), ("0", "0", "5", "3")]:
        add("tg", [v_dec(x) for x in vals], "edge")

    # -- triangles from sides (similar, Heron)
    def tri_sides():
        a, b = L(), L()
        lo_, hi_ = abs(a.x - b.x), a.x + b.x
        for _ in range(30):
            c = gen_len(rng, float(max(lo_, mp.mpf("1e-3"))), float(hi_))
            if lo_ < c.x < hi_:
                return [a, b, c]
        return [v_int(3), v_int(4), v_int(5)]

    def bad_sides():
        r = rng.random()
        a, b = L(), L()
        if r < 0.4:
            return [a, b, V(f"{a.text}+{b.text}", a.x + b.x)], "c = a+b"
        if r < 0.7:
            return [a, b, gen_len(rng, float(a.x + b.x) * 1.01, float(a.x + b.x) * 10)], "c > a+b"
        if r < 0.85:
            return [zero(), a, b], "zero side"
        return [neg(a), b, b], "negative side"

    for _ in range(n(80)):
        vals = tri_sides()
        rng.shuffle(vals)
        add("sm", vals + [L()])
        vals = tri_sides()
        rng.shuffle(vals)
        add("at3", vals)
    for _ in range(n(20)):
        vals, tag = bad_sides()
        add("sm", vals + [L()], tag)
        vals, tag = bad_sides()
        add("at3", vals, tag)
    for _ in range(n(6)):
        add("sm", tri_sides() + [rng.choice([zero(), neg(L())])], "A' <= 0")
    for vals in [("3", "4", "5"), ("1", "1", "1.9999"), ("1", "1", "1.99999999"), ("100000", "100000", "0.001"),
                 ("0.001", "0.001", "0.001"), ("1/3", "2/3", "0.99999"), ("0.1", "0.2", "0.3"),
                 ("5", "6", "7"), ("100000", "0.001", "100000")]:
        add("at3", [v_dec(x) if "/" not in x else V(x, mp.mpf(x.split("/")[0]) / int(x.split("/")[1]))
                    for x in vals], "edge")
        add("sm", [v_dec(x) if "/" not in x else V(x, mp.mpf(x.split("/")[0]) / int(x.split("/")[1]))
                   for x in vals] + [v_dec("2")], "edge")

    # -- intersecting / parallel lines
    for name in ("il", "pl"):
        for _ in range(n(60)):
            add(name, [gen_angle(rng, 0, 180)])
        for _ in range(n(15)):
            add(name, [rng.choice([zero(), v_int(180), neg(gen_angle(rng, 0, 180)),
                                   gen_angle(rng, 180, 1000)])], "out of range")
        for t in ["0.001", "1", "19", "20", "90", "160", "161", "179", "179.999"]:
            add(name, [v_dec(t)], "edge")

    # -- base & height, rectangle, solids, scale (all just positive lengths)
    simple = {"atb": 2, "rec": 2, "box": 3, "cyl": 2, "cone": 2, "sph": 1, "pyr": 2, "sc": 1}
    for name, k in simple.items():
        for _ in range(n(70)):
            add(name, [L() for _ in range(k)])
        for _ in range(n(15)):
            vals = [L() for _ in range(k)]
            i = rng.randrange(k)
            vals[i] = zero() if rng.random() < 0.4 else neg(vals[i])
            add(name, vals, "zero/negative")
        for x in ["0.001", "100000"]:
            add(name, [v_dec(x)] * k, "edge")
        if k >= 2:
            add(name, [v_dec("1"), v_dec("10000")] + [v_dec("1")] * (k - 2), "1x10000")
            add(name, [v_dec("10000"), v_dec("1")] + [v_dec("1")] * (k - 2), "10000x1")
            add(name, [v_dec("100000"), v_dec("0.001")] + [v_dec("1")] * (k - 2), "extreme")
            add(name, [v_dec("0.001"), v_dec("100000")] + [v_dec("1")] * (k - 2), "extreme")

    # -- trapezoid
    for _ in range(n(70)):
        u, v, h = L(), L(), L()
        r = rng.random()
        if r < 0.3:
            s3, s4 = zero(), zero()
        elif r < 0.8:
            s3 = gen_len(rng, float(h.x), float(h.x) * 3 + 1)
            s4 = gen_len(rng, float(h.x), float(h.x) * 3 + 1)
        elif r < 0.9:
            s3, s4 = neg(L()), L()
        else:
            s3, s4 = gen_len(rng, 1e-3, float(h.x)), L()
        add("trap", [u, v, h, s3, s4])
    for _ in range(n(15)):
        vals = [L(), L(), L()]
        i = rng.randrange(3)
        vals[i] = zero() if rng.random() < 0.4 else neg(vals[i])
        add("trap", vals + [L(), L()], "zero/negative")
    add("trap", [v_dec("10"), v_dec("6"), v_dec("4"), v_dec("5"), v_dec("5")], "edge")
    add("trap", [v_dec("1"), v_dec("10000"), v_dec("1"), v_dec("0"), v_dec("0")], "extreme")
    add("trap", [v_dec("10"), v_dec("6"), v_dec("4"), v_dec("1"), v_dec("1")], "legs < height")

    # -- circle solver
    for _ in range(n(80)):
        vals = [zero()] * 4
        vals[rng.randrange(4)] = L()
        add("cs", vals)
    for _ in range(n(25)):
        r = rng.random()
        vals = [zero()] * 4
        if r < 0.3:
            i, j = rng.sample(range(4), 2)
            vals[i], vals[j] = L(), L()
            tag = "two known"
        elif r < 0.45:
            tag = "all zero"
        elif r < 0.7:
            vals[rng.randrange(4)] = neg(L())
            tag = "negative"
        else:
            i, j = rng.sample(range(4), 2)
            vals[i], vals[j] = neg(L()), L()
            tag = "negative + positive"
        add("cs", vals, tag)

    # -- arc & sector
    for _ in range(n(80)):
        add("arc", [L(), gen_angle(rng, 0, 360) if rng.random() < 0.9 else v_int(360)])
    for _ in range(n(20)):
        r = rng.random()
        add("arc", [zero(), gen_angle(rng, 0, 360)] if r < 0.25 else
            [neg(L()), gen_angle(rng, 0, 360)] if r < 0.5 else
            [L(), rng.choice([zero(), neg(gen_angle(rng, 0, 90)), gen_angle(rng, 360, 1000)])],
            "invalid")
    for t in ["0.001", "1", "4.99", "5", "180", "359.999", "360"]:
        add("arc", [v_dec("6"), v_dec(t)], "edge")

    # -- circle equation, both ways
    for _ in range(n(80)):
        add("ceb", [gen_real(rng), gen_real(rng), L()])
    for _ in range(n(15)):
        add("ceb", [gen_real(rng), gen_real(rng), rng.choice([zero(), neg(L())])], "R <= 0")
    for vals in [("2", "-3", "5"), ("0", "0", "1"), ("-0.0001", "2", "3"), ("0.0001", "2", "3"),
                 ("3", "4", "5"), ("3", "4", "5.00001"), ("3", "4", "4.99999"), ("0.1", "0.2", "0.22361"),
                 ("1", "1", "sqrt(2)"), ("100000", "-100000", "0.001"), ("0.0002", "-0.0003", "1")]:
        add("ceb", [V(x, mp.sqrt(2)) if x == "sqrt(2)" else v_dec(x) for x in vals], "edge")
    for _ in range(n(80)):
        h, k, c = gen_real(rng), gen_real(rng), L()
        d, e = -2 * h.x, -2 * k.x
        f = h.x ** 2 + k.x ** 2 - c.x ** 2
        # type D, E, F as decimals with a few places
        dv = v_dec(fmt_dec(float(d), 3))
        ev = v_dec(fmt_dec(float(e), 3))
        fv = v_dec(fmt_dec(float(f), 3))
        if dv.x * dv.x / 4 + ev.x * ev.x / 4 - fv.x > 0:
            add("cer", [dv, ev, fv])
    for _ in range(n(20)):
        d, e = gen_real(rng, 1e-3, 100), gen_real(rng, 1e-3, 100)
        s = d.x ** 2 / 4 + e.x ** 2 / 4
        f = s + abs(gen_real(rng, 1e-3, 100).x)
        add("cer", [d, e, v_dec(fmt_dec(float(f) + 0.001, 3))], "not a circle")
    for vals in [("2", "0", "1"), ("0", "0", "0"), ("0", "0", "4"), ("-4", "6", "-12"),
                 ("0.0002", "0", "-1"), ("4", "0", "3.9999"), ("0", "0", "-0.000001")]:
        add("cer", [v_dec(x) for x in vals], "edge")
    return cases

# ----------------------------------------------------------------- running a case


class Abort(Exception):
    pass


@dataclass
class Problem:
    sev: str          # crash | wrong | misleading | cosmetic | leak
    what: str
    seq: list
    mode: str
    solver: str
    tag: str = ""


DEFAULT_WINDOW = {"Xmin": -7.5, "Xmax": 12.25, "Xscl": 2.0, "Ymin": -3.0, "Ymax": 4.5, "Yscl": 0.5}


def mode_name(degree, cplx):
    return ("DEGREE" if degree else "RADIAN") + (" a+bi" if cplx else "")


def new_geometry(degree, cplx=False):
    m = Machine(TOK["GEOMETRY"], max_steps=50_000_000)
    m.programs.update({k: v for k, v in TOK.items() if k != "GEOMETRY"})
    m.radians = not degree
    m.complex_mode = cplx
    m.window.update(DEFAULT_WINDOW)
    m.lists["MINE"] = [1.0, 2.0, 3.0]
    m.run()
    return m


class Runner:
    def __init__(self, m, mode, solver="", tag=""):
        self.m, self.mode, self.solver, self.tag = m, mode, solver, tag
        self.seq = []
        self.problems = []
        self.figures = []           # (kind, texts, raw lines, pixels)

    def fail(self, sev, what):
        self.problems.append(Problem(sev, what, list(self.seq), self.mode, self.solver, self.tag))

    def ans(self, a):
        self.seq.append(a)
        try:
            self.m.answer(a)
        except RuntimeError as e:
            self.fail("crash", f"answer {a!r} not accepted: {e}")
            raise Abort
        self.check_state()

    def check_state(self):
        m = self.m
        if m.wait.kind == "error":
            self.fail("crash", f"{m.wait.message} at: {m.statement_text()}")
            raise Abort
        if m.leaks:
            self.fail("leak", f"leaks={m.leaks}")
            raise Abort
        if m.wait.kind == "menu":
            COUNT["menus"] += 1
            for opt in m.wait.options:
                if len(opt) > 24 and opt not in ONCE:
                    ONCE.add(opt)
                    self.fail("cosmetic", f"menu option longer than 24 characters: {opt!r} "
                                          f"(shows as {('1:' + opt)[:26]!r})")
            if len(m.wait.title) > 26:
                self.fail("cosmetic", f"menu title too long: {m.wait.title!r}")
            if m.blocks:
                self.fail("leak", f"blocks open at a menu: {m.blocks}")
            gs = m.lists.get("GS")
            if gs:
                self.fail("leak", f"#call stack GS not empty at menu {m.wait.title!r}: {gs}")
        if m.screen.long_disp:
            for t in m.screen.long_disp:
                key = re.sub(r"[\d.ᴇ⁻]+", "#", t)
                if key not in ONCE:
                    ONCE.add(key)
                    self.fail("cosmetic", f"Disp text longer than 26 characters is cut off: {t!r} "
                                          f"(shows {t[:26]!r})")
            m.screen.long_disp.clear()

    def menu(self, title=None):
        m = self.m
        if m.wait.kind != "menu" or (title and m.wait.title != title):
            self.fail("wrong", f"expected menu {title!r}, got {m.wait} {view(m)}")
            raise Abort

    def graph_pause(self):
        m = self.m
        return m.wait.kind == "pause" and m.showing_graph

    def texts(self):
        return [(r, c, t) for r, c, t, _ in self.m.graph.texts]

    def check_figure(self, kind):
        """Pixel / Text( sanity for the graph screen showing now."""
        g = self.m.graph
        texts = self.texts()
        COUNT["figures"] += 1
        COUNT["labels"] += len(texts)
        self.figures.append((kind, texts, list(g.raw), len(g.pixels)))
        if not g.pixels:
            self.fail("cosmetic", f"{kind} figure: nothing drawn")
        for x, y in g.pixels:
            if not (0 <= x < GW and 0 <= y < GH):
                self.fail("crash", f"{kind} figure: pixel off the screen {(x, y)}")
                break
        far = 0.0
        for x1, y1, x2, y2 in g.raw:
            if not all(math.isfinite(v) for v in (x1, y1, x2, y2)) or max(map(abs, (x1, y1, x2, y2))) > 1e5:
                self.fail("crash", f"{kind} figure: Line( to an absurd pixel {(x1, y1, x2, y2)}")
                return
            for x, y in ((x1, y1), (x2, y2)):
                far = max(far, -x, x - (GW - 1), -y, y - (GH - 1))
        if far > 3:
            self.fail("layout", f"{kind} figure: a line runs {far:.0f} px off the screen")
        for r, c, t in texts:
            if r != int(r) or c != int(c) or not (0 <= r <= 150 and 0 <= c <= 264):
                self.fail("crash", f"{kind} figure: Text({r},{c}) out of range")
            if t.strip() and c + 4 * len(t) > GW + 2:
                self.fail("cosmetic", f"{kind} figure: label {t!r} at column {c} runs off the right "
                                      f"edge even at 4 px a character")
            elif t.strip() and c + 6 * len(t) > GW + 6:
                self.fail("cosmetic", f"{kind} figure: label {t!r} at column {c} likely runs off the "
                                      f"right edge (6 px a character: ends at {c + 6 * len(t)})")
        # the ENTER line at row 150 is drawn last; Text( paints its background, so a label
        # less than a font height (~14 px on the CE) above it, under its letters, is cut
        foot = [(c, c + 4 * len(t)) for r, c, t in texts if r == 150]
        for r, c, t in texts:
            if t.strip() and 150 - 14 < r < 150 and any(c < b and a < c + 4 * len(t) for a, b in foot):
                self.fail("layout", f"{kind} figure: label {t!r} at row {r} is partly covered by "
                                    f"the ENTER line at row 150")
        # labels written on top of each other (estimated 4 px a character, 10 px tall)
        boxes = [(c, r, c + 4 * len(t.rstrip()), r + 10, t) for r, c, t in texts
                 if t.strip() and r not in (2, 150)]
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                a, b = boxes[i], boxes[j]
                if a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]:
                    self.fail("layout", f"{kind} figure: labels {a[4]!r} and {b[4]!r} overlap")
                    return

    def report_lines(self):
        """Press ENTER through the report; return its lines (as Output( wrote them)."""
        m = self.m
        start = len(m.screen.olog)
        self.ans("")
        pages = 0
        while True:
            if m.wait.kind != "pause" or m.showing_graph:
                self.fail("wrong", f"report: expected a paused home screen, got {m.wait}")
                raise Abort
            last = m.screen.lines()[9]
            if "ENTER: MORE" in last:
                pages += 1
                self.ans("")
                continue
            if "ENTER: DONE" in last:
                break
            self.fail("wrong", f"report page doesn't end in ENTER: {view(m)}")
            raise Abort
        log = m.screen.olog[start:]
        for k, e in enumerate(log):
            if e["overwrites"]:
                prev = [log[i - start]["text"] if i >= start else "?" for i in e["overwrites"]]
                if not e["text"].startswith("ENTER:") or prev != [e["text"]]:
                    self.fail("cosmetic", f"report line {e['text']!r} (row {e['row']}) overwrites "
                                          f"the end of {prev}")
            if e["past_bottom"]:
                self.fail("cosmetic", f"report line {e['text']!r} runs past the bottom of the screen")
        return [e["text"] for e in log if not e["text"].startswith("ENTER:")]

    def expect_start(self, sv: Solver):
        """After AGAIN: the solver's own start (reference figure or its menu)."""
        kind, what = sv.again
        m = self.m
        if kind == "menu":
            self.menu(what)
        else:
            if not (self.graph_pause() and (2, 2, what) in self.texts()):
                self.fail("wrong", f"AGAIN should show the {sv.name} reference figure "
                                   f"{what!r}, got {m.wait} {self.texts() if m.showing_graph else view(m)}")
                raise Abort


def match_labels(r: Runner, expected, actual, where):
    """Every expected label pattern must match a different label on the figure."""
    acts = [t for t in actual if t.strip()]
    cand = [[j for j, t in enumerate(acts) if match(p, t) is None] for p in expected]
    owner = {}

    def augment(i, seen):
        for j in cand[i]:
            if j in seen:
                continue
            seen.add(j)
            if j not in owner or augment(owner[j], seen):
                owner[j] = i
                return True
        return False
    for i in range(len(expected)):
        if not augment(i, set()):
            # find the nearest miss to explain
            why = [f"{t!r}: {match(expected[i], t)}" for t in acts if match(expected[i], t) != "text"]
            r.fail("wrong" if why else "wrong",
                   f"{where} figure: no label reads {show(expected[i])!r}; labels {acts}"
                   + (f" ({'; '.join(why)})" if why else ""))
            return


def run_case(case: Case, degree: bool, cplx: bool, end: str, rng_path: int = 0):
    sv = S[case.solver]
    path = sv.paths[rng_path % len(sv.paths)]
    m = new_geometry(degree, cplx)
    r = Runner(m, mode_name(degree, cplx), case.solver, case.tag)
    exp = sv.oracle(*[v.x for v in case.vals])
    try:
        r.check_state()
        for a in path:
            r.menu()
            r.ans(a)
        if not (r.graph_pause() and "ENTER, THEN TYPE THE VALUES" in [t for _, _, t in r.texts()]):
            r.fail("wrong", f"no reference figure first: {m.wait}")
            raise Abort
        if (2, 2, sv.title) not in r.texts():
            r.fail("wrong", f"reference figure title isn't {sv.title!r}: {r.texts()}")
        r.check_figure("reference")
        r.ans("")
        if sv.sub:
            r.menu("CIRCLE EQUATION")
            r.ans(sv.sub)
        used = 0
        for prompt, v in zip(sv.prompts, case.vals):
            if m.wait.kind != "input":
                break
            if m.wait.prompt != prompt:
                r.fail("wrong", f"asked {m.wait.prompt!r}, expected {prompt!r}")
                raise Abort
            r.ans(v.text)
            used += 1
        if m.wait.kind == "input":
            r.fail("wrong", f"still asking {m.wait.prompt!r} after {used} values")
            raise Abort
        if r.graph_pause():
            texts = r.texts()
            if (150, 2, "ENTER FOR THE WORKING") not in texts:
                r.fail("wrong", f"unexpected graph screen {texts}")
                raise Abort
            if not exp.ok:
                r.fail("misleading" if exp.flag else "wrong",
                       exp.flag or f"accepted invalid input (should say one of "
                                   f"{[ERRS[k] for k in exp.msgs]})")
                raise Abort
            r.check_figure("solved")
            match_labels(r, exp.labels, [t for rr, c, t in texts if (rr, c) != (150, 2)], "solved")
            if exp.order:
                lab = [t for rr, c, t in sorted(texts) if t.strip() and rr not in (2, 150)]
                if len(lab) != len(exp.order) or any(match(p, t) for p, t in zip(exp.order, lab)):
                    r.fail("wrong", f"angle labels in the wrong places: {lab}, expected "
                                    f"{[show(p) for p in exp.order]}")
            lines = r.report_lines()
            if len(lines) != len(exp.lines):
                r.fail("wrong", f"report has {len(lines)} lines, expected {len(exp.lines)}: {lines} vs "
                                f"{[show(p) for p in exp.lines]}")
            else:
                COUNT["report lines"] += len(lines)
                for got, want in zip(lines, exp.lines):
                    why = match(want, got)
                    if why:
                        r.fail("wrong" if why != "text" else "cosmetic",
                               f"report line {got!r} should read {show(want)!r} ({why})")
            if exp.flag:
                r.fail("misleading", exp.flag)
            r.ans("")
            r.menu("WHAT NEXT?")
            r.ans(end)
            if end == "1":
                r.expect_start(sv)
            elif end == "2":
                r.menu("GEOMETRY")
            else:
                check_restored(r)
        else:
            screen = " ".join(ln.strip() for ln in m.screen.lines())
            keys = [k for k, msg in ERRS.items() if msg in screen]
            if m.wait.kind != "pause" or not keys:
                r.fail("wrong", f"neither a figure nor a message: {m.wait} {view(m)}")
                raise Abort
            if exp.ok:
                r.fail("wrong", f"refused valid input with {ERRS[keys[0]]!r}")
            elif exp.flag:
                pass
            elif keys[0] not in exp.msgs:
                r.fail("note", f"message {ERRS[keys[0]]!r} for {case.tag or 'this'} input "
                               f"(better: {ERRS[exp.msgs[0]]!r})")
            r.ans("")
            r.expect_start(sv)
    except Abort:
        pass
    return r


def check_restored(r: Runner):
    m = r.m
    if m.wait.kind != "done":
        r.fail("wrong", f"BACK TO BRAINIAC didn't end the program: {m.wait}")
        return
    if m.window != DEFAULT_WINDOW:
        r.fail("wrong", f"window not restored: {m.window}")
    want_rad = r.mode.startswith("RADIAN")
    if m.radians != want_rad:
        r.fail("wrong", f"angle mode not restored: radians={m.radians}")
    if not m.graph.axes:
        r.fail("cosmetic", "axes left off")
    if set(m.lists) != {"MINE"} or m.lists["MINE"] != [1.0, 2.0, 3.0]:
        r.fail("cosmetic", f"lists left behind or user list changed: {sorted(m.lists)}")
    if m.graph.pixels or m.graph.texts:
        r.fail("cosmetic", "drawing left on the graph screen")

# ----------------------------------------------------------------- combined shape


SHAPES = {"1": ("RECTANGLE", ["LENGTH=", "WIDTH="], lambda a, b: a * b),
          "2": ("TRIANGLE", ["BASE=", "HEIGHT="], lambda a, b: a * b / 2),
          "3": ("CIRCLE", ["RADIUS="], lambda a: PI * a * a),
          "4": ("HALF CIRCLE", ["RADIUS="], lambda a: PI * a * a / 2)}


def gen_comb(rng, scale):
    out = []
    for _ in range(max(4, int(60 * scale))):
        ops = []
        for _ in range(rng.randint(0, 5)):
            shape = rng.choice("1234")
            sign = 1 if rng.random() < 0.75 else -1
            vals = [gen_len(rng, 1e-3, 1e4) for _ in SHAPES[shape][1]]
            if rng.random() < 0.08:
                vals[0] = neg(vals[0])
            ops.append((shape, sign, vals))
        out.append(ops)
    big = [("1", 1, [v_dec("123456"), v_dec("98765")]), ("4", 1, [v_dec("100000")])]
    out.append(big)
    return out


def run_comb(ops, degree, cplx, end):
    m = new_geometry(degree, cplx)
    r = Runner(m, mode_name(degree, cplx), "comb")
    total = mp.mpf(0)
    parts = 0
    try:
        r.ans("3")
        r.ans("5")
        if m.wait.kind != "pause" or "COMBINED SHAPE" not in m.screen.lines()[0]:
            r.fail("wrong", f"combined shape intro missing: {view(m)}")
            raise Abort
        r.ans("")
        flagged = False
        for shape, sign, vals in ops:
            r.menu("COMBINED SHAPE")
            if sign > 0:
                r.ans(shape)
            else:
                r.ans("5")
                r.menu("TAKE AWAY")
                r.ans(shape)
            name, prompts, f = SHAPES[shape]
            for p, v in zip(prompts, vals):
                if m.wait.kind != "input" or m.wait.prompt != p:
                    r.fail("wrong", f"expected prompt {p!r}, got {m.wait}")
                    raise Abort
                r.ans(v.text)
            area = f(*[v.x for v in vals])
            lines = [ln.strip() for ln in m.screen.lines() if ln.strip()]
            if any(v.x < 0 for v in vals):
                # 0.5: a negative length is refused and the part isn't added
                if lines[:2] != ["LENGTHS CAN'T BE NEGATIVE.", "THAT PART WASN'T ADDED."]:
                    r.fail("misleading", f"a negative length ({[v.text for v in vals]}) is accepted "
                                         f"for a {name.lower()}: {lines[:2]}")
                r.ans("")
                continue
            parts += 1
            total += sign * area
            # 0.5: the name, then the signed area, then the total, on their own lines
            want = [[name + ":"], [("+" if sign > 0 else "-"), N(area)], ["TOTAL SO FAR:"], [N(total)]]
            if len(lines) < 4 or any(match(w, g) for w, g in zip(want, lines)):
                bad_text = len(lines) >= 4 and all("text" in (match(w, g) or "text")
                                                   for w, g in zip(want, lines))
                r.fail("cosmetic" if bad_text else "wrong",
                       f"after a {name.lower()}: {lines[:4]} should read {[show(w) for w in want]}")
            if total < 0 and "(MORE TAKEN AWAY THAN" not in lines:
                r.fail("misleading", f"total area is negative ({mp.nstr(total, 8)}) with no warning")
            r.ans("")
        r.menu("COMBINED SHAPE")
        start = len(m.screen.olog)
        r.ans("6")
        # the result screen comes straight up (there is no figure)
        log = [e["text"] for e in m.screen.olog[start:] if not e["text"].startswith("ENTER:")]
        want = [["COMBINED SHAPE: ", N(parts), " PARTS"], ["TOTAL AREA=", N(total)]]
        if total < 0:
            want += [["THAT'S LESS THAN 0: MORE"], ["WAS TAKEN AWAY THAN ADDED."]]
        if len(log) < len(want) or any(match(w, g) for w, g in zip(want, log[-len(want):])):
            r.fail("wrong", f"result {log} should read {[show(w) for w in want]}")
        r.ans("")
        r.menu("WHAT NEXT?")
        r.ans(end)
        if end == "1":
            if m.wait.kind != "pause" or "COMBINED SHAPE" not in m.screen.lines()[0]:
                r.fail("wrong", "AGAIN should start the combined shape again")
        elif end == "2":
            r.menu("GEOMETRY")
        else:
            check_restored(r)
    except Abort:
        pass
    return r

# ----------------------------------------------------------------- program-level tests


def valid_case(rng, cases, name=None):
    pool = [c for c in cases if (name is None or c.solver == name) and S[c.solver].oracle(
        *[v.x for v in c.vals]).ok and not c.tag]
    return rng.choice(pool)


def drive_valid(r: Runner, case: Case, start_shown=False):
    """From the GEOMETRY menu (or the solver's start screen if start_shown), solve a case."""
    sv = S[case.solver]
    m = r.m
    if not start_shown:
        for a in sv.paths[0]:
            r.menu()
            r.ans(a)
    if sv.again[0] == "menu" and m.wait.kind == "menu" and m.wait.title == sv.again[1]:
        r.ans(sv.paths[0][-1])
    if not r.graph_pause():
        r.fail("wrong", f"{sv.name}: no reference figure: {m.wait}")
        raise Abort
    r.ans("")
    if sv.sub:
        r.menu("CIRCLE EQUATION")
        r.ans(sv.sub)
    for v in case.vals:
        if m.wait.kind != "input":
            break
        r.ans(v.text)
    if not r.graph_pause():
        r.fail("wrong", f"{sv.name}: no solved figure: {m.wait} {view(m)}")
        raise Abort
    r.report_lines()
    r.ans("")
    r.menu("WHAT NEXT?")


def session_tests(rng, cases, quick):
    problems = []
    rounds = 8 if quick else 30
    for degree in (True, False):
        # -- 30 rounds in one GEOMETRY session, by AGAIN and by the menu
        m = new_geometry(degree)
        r = Runner(m, mode_name(degree, False), "session")
        try:
            prev = None
            for i in range(rounds):
                if prev is not None and i % 2:
                    case = valid_case(rng, cases, prev.solver)
                    r.ans("1")                               # AGAIN
                    r.expect_start(S[case.solver])
                    drive_valid(r, case, start_shown=True)
                else:
                    if prev is not None:
                        r.ans("2")                           # GEOMETRY MENU
                    case = valid_case(rng, cases)
                    drive_valid(r, case)
                prev = case
                if m.leaks or m.blocks or m.lists.get("GS"):
                    r.fail("leak", f"after round {i + 1}: leaks={m.leaks} blocks={m.blocks} "
                                   f"GS={m.lists.get('GS')}")
                    raise Abort
            r.ans("3")
            check_restored(r)
        except Abort:
            pass
        problems += r.problems

        # -- every BACK goes back up
        for path, sub, back_to in [(["1"], "TRIANGLES", "GEOMETRY"), (["2"], "ANGLES & LINES", "GEOMETRY"),
                                   (["3"], "2D SHAPES: AREA", "GEOMETRY"), (["4"], "3D SOLIDS", "GEOMETRY"),
                                   (["5"], "CIRCLES", "GEOMETRY"),
                                   (["1", "3"], "SPECIAL RIGHT TRIANGLE", "TRIANGLES"),
                                   (["3", "1"], "TRIANGLE AREA", "2D SHAPES: AREA"),
                                   (["5", "3", ""], "CIRCLE EQUATION", "CIRCLES"),
                                   (["3", "5", "", "5"], "TAKE AWAY", "COMBINED SHAPE")]:
            m = new_geometry(degree)
            r = Runner(m, mode_name(degree, False), "menus")
            try:
                for a in path:
                    r.ans(a)
                r.menu(sub)
                r.ans(str(m.wait.options.index("BACK") + 1))
                r.menu(back_to)
            except Abort:
                pass
            problems += r.problems
        m = new_geometry(degree)
        r = Runner(m, mode_name(degree, False), "menus")
        try:
            r.ans("7")
            check_restored(r)
        except Abort:
            pass
        problems += r.problems

        # -- axes that were off before GEOMETRY ran should be off again afterwards
        m = Machine(TOK["GEOMETRY"], max_steps=50_000_000)
        m.radians = not degree
        m.window.update(DEFAULT_WINDOW)
        m.graph.axes = False
        m.lists["MINE"] = [1.0, 2.0, 3.0]
        m.run()
        r = Runner(m, mode_name(degree, False) + ", axes off", "restore")
        try:
            r.ans("7")
            if m.graph.axes:
                r.fail("cosmetic", "the axes were off before GEOMETRY ran and are on after it "
                                   "(README: it puts your axes back)")
        except Abort:
            pass
        problems += r.problems

        # -- run from BRAINIAC (5 GEOMETRY & UNITS, 1 GEOMETRY), rounds, back, Brainiac still works
        b = Machine(TOK["BRAINIAC"], max_steps=50_000_000)
        b.programs.update({k: v for k, v in TOK.items() if k != "BRAINIAC"})
        b.radians = not degree
        b.window.update(DEFAULT_WINDOW)
        b.run()
        r = Runner(b, mode_name(degree, False) + " via BRAINIAC", "brainiac")
        try:
            r.menu("BRAINIAC 0.5")
            r.ans("5")
            r.menu("GEOMETRY & UNITS")
            r.ans("1")
            r.menu("GEOMETRY")
            if b.radians:
                r.fail("wrong", "GEOMETRY isn't in Degree mode")
            prev = None
            for i in range(rounds):
                if prev is not None and i % 3 == 1:
                    case = valid_case(rng, cases, prev.solver)
                    r.ans("1")
                    r.expect_start(S[case.solver])
                    drive_valid(r, case, start_shown=True)
                else:
                    if prev is not None:
                        r.ans("2")
                    case = valid_case(rng, cases)
                    drive_valid(r, case)
                prev = case
            r.ans("3")                                       # BACK TO BRAINIAC
            r.menu("BRAINIAC 0.5")
            if not b.radians or b.leaks or b.blocks:
                r.fail("wrong", f"back in BRAINIAC: radians={b.radians} leaks={b.leaks} blocks={b.blocks}")
            for k in ("GS", "GW", "PX", "PY", "Z1", "Z2"):
                if k in b.lists:
                    r.fail("cosmetic", f"list {k} left behind")
            r.ans("5")
            r.ans("1")
            r.menu("GEOMETRY")
            r.ans("7")                                       # GEOMETRY menu's own BACK TO BRAINIAC
            r.menu("BRAINIAC 0.5")
            r.ans("1")
            r.ans("5X=30")
            if "X=6" not in [ln.strip() for ln in b.screen.lines()]:
                r.fail("wrong", f"Brainiac doesn't solve 5X=30 after GEOMETRY: {view(b)}")
            while b.wait.kind == "pause":
                r.ans("")
            r.menu("WHAT NEXT?")
            r.ans(str(b.wait.options.index("MAIN MENU") + 1))
            r.menu("BRAINIAC 0.5")
            r.ans("7")                                       # QUIT
            while b.wait.kind == "pause":
                r.ans("")
            if b.wait.kind != "done":
                r.fail("wrong", f"BRAINIAC didn't quit: {b.wait}")
            elif b.radians != (not degree) or b.window != DEFAULT_WINDOW:
                r.fail("wrong", f"after BRAINIAC quits: radians={b.radians} window={b.window}")
        except Abort:
            pass
        problems += r.problems
    return problems

# ----------------------------------------------------------------- showcase


SHOWCASE = [("rectangle 1x10000", ["3", "2", "", "1", "10000", ""]),
            ("rectangle 10000x1", ["3", "2", "", "10000", "1", ""]),
            ("missing angle 179 + 0.5", ["1", "1", "", "179", "0.5", ""]),
            ("missing angle 1 + 0.001", ["1", "1", "", "1", "0.001", ""]),
            ("intersecting 179", ["2", "2", "", "179", ""]),
            ("intersecting 0.001", ["2", "2", "", "0.001", ""]),
            ("parallel 179", ["2", "3", "", "179", ""]),
            ("arc 359.999", ["5", "2", "", "100000", "359.999", ""]),
            ("trig A=0.001", ["1", "4", "", "0.001", "100000", "0", "0", ""]),
            ("similar 100000/0.001", ["1", "5", "", "3", "4", "5", "100000", ""]),
            ("box 1x10000x1", ["4", "1", "", "1", "10000", "1", ""]),
            ("cylinder r=100000 h=0.001", ["4", "2", "", "100000", "0.001", ""]),
            ("cone r=0.001 h=100000", ["4", "3", "", "0.001", "100000", ""]),
            ("pyramid s=100000 h=0.001", ["4", "5", "", "100000", "0.001", ""]),
            ("trapezoid 1, 10000, 1", ["3", "4", "", "1", "10000", "1", "0", "0", ""]),
            ("circle eq centre far away", ["5", "3", "", "1", "100000", "-100000", "0.001", ""]),
            ("scale K=100000", ["6", "", "100000", ""]),
            ("heron 100000,100000,0.001", ["3", "1", "1", "", "100000", "100000", "0.001", ""])]


def render(m):
    cells = [[" "] * 66 for _ in range(28)]
    for x, y in m.graph.pixels:
        cells[min(27, y * 28 // GH)][min(65, x * 66 // GW)] = "#"
    for row, col, text, _ in m.graph.texts:
        rr, cc = min(27, row * 28 // GH), min(65, col * 66 // GW)
        for i, ch in enumerate(text):
            if cc + i < 66:
                cells[rr][cc + i] = ch
    return ["|" + "".join(r) + "|" for r in cells]


def showcase():
    for name, seq in SHOWCASE:
        m = new_geometry(True)
        try:
            for a in seq[:-1]:
                m.answer(a)
        except Exception as e:  # noqa: BLE001
            print(name, "->", e)
        print(f"== {name}: {seq}  {m.wait}")
        if m.showing_graph:
            print("\n".join(render(m)))
            print("   texts:", sorted((r, c, t) for r, c, t, _ in m.graph.texts))

# ----------------------------------------------------------------- static checks


def static_checks():
    problems = []
    for line in source_lines("GEOMETRY"):
        if line.startswith("Disp "):
            for part in re.findall(r'"([^"]*)"', line):
                if len(part) > 26:
                    problems.append(Problem("cosmetic", f"Disp string longer than 26 characters: "
                                                        f"{part!r}", [], "-", "source"))
        if '""→' in line:
            problems.append(Problem("crash", f"empty string stored: {line}", [], "-", "source"))
    return problems

# ----------------------------------------------------------------- main


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true")
    ap.add_argument("--show", action="store_true")
    args = ap.parse_args()
    scale = 0.2 if args.quick else 1.0
    t0 = time.time()
    rng = random.Random(SEED)
    cases = gen_cases(rng, scale)
    combos = gen_comb(rng, scale)
    problems = static_checks()
    stats = Counter()
    runs = 0
    for i, case in enumerate(cases):
        exp_ok = S[case.solver].oracle(*[v.x for v in case.vals]).ok
        stats[(case.solver, "valid" if exp_ok else "invalid")] += 1
        end = "123"[i % 3]
        modes = [(True, False), (False, False)] + ([(True, True)] if i % 4 == 0 else [])
        for degree, cplx in modes:
            r = run_case(case, degree, cplx, end, i)
            runs += 1
            problems += r.problems
    for i, ops in enumerate(combos):
        stats[("comb", "sequence")] += 1
        for degree in (True, False):
            r = run_comb(ops, degree, False, "123"[i % 3])
            runs += 1
            problems += r.problems
    problems += session_tests(rng, cases, args.quick)

    if args.show:
        showcase()

    # ---- summary
    n_inputs = sum(len(c.vals) for c in cases) + sum(len(v) for ops in combos for _, _, v in ops)
    print(f"GEOMETRY stress: {len(cases)} solver cases + {len(combos)} combined-shape sequences "
          f"({n_inputs} typed values), {runs} runs (Degree and Radian, a quarter also a+bi), "
          f"plus session/menu/BRAINIAC tests; {time.time() - t0:.0f} s")
    per = defaultdict(lambda: [0, 0])
    for (name, kind), k in stats.items():
        per[name][0 if kind != "invalid" else 1] += k
    print("cases per solver (valid/invalid):",
          ", ".join(f"{k} {v[0]}/{v[1]}" for k, v in sorted(per.items())))
    print("checked: " + ", ".join(f"{v} {k}" for k, v in COUNT.items()))
    real = [p for p in problems if p.sev not in ("note", "layout")]
    notes = [p for p in problems if p.sev == "note"]
    layout = [p for p in problems if p.sev == "layout"]
    groups = defaultdict(list)
    for p in real:
        key = (p.sev, p.solver, re.sub(r"[-⁻]?\d[\d.ᴇ⁻]*", "#", re.sub(r"'[^']*'", "'…'", p.what))[:90])
        groups[key].append(p)
    order = {"crash": 0, "leak": 1, "wrong": 2, "misleading": 3, "cosmetic": 4}
    print(f"\n{len(real)} failures in {len(groups)} groups ({len(layout)} layout, {len(notes)} notes)")
    for key in sorted(groups, key=lambda k: (order.get(k[0], 9), k[1], k[2])):
        ps = groups[key]
        modes = sorted({p.mode for p in ps})
        print(f"\n[{key[0].upper()}] {key[1]}: {ps[0].what}")
        print(f"   {len(ps)} time(s), modes {modes}")
        for p in ps:
            print(f"   - {p.mode}: {p.seq}  {p.what if p.what != ps[0].what else ''}".rstrip())
    if layout:
        lg = defaultdict(list)
        for p in layout:
            kind = ("labels overlap" if "overlap" in p.what else "label under the ENTER line"
                    if "ENTER line" in p.what else "line off screen")
            lg[(p.solver, kind)].append(p)
        print("\nlayout (not failures; mostly extreme proportions; 3 examples each):")
        for (sv, kind), ps in sorted(lg.items()):
            print(f"   {sv}: {kind} x{len(ps)}")
            for p in ps[:3]:
                print(f"      {p.mode}: {p.seq}  {p.what}")
    if notes:
        ng = Counter((p.solver, re.sub(r"[-⁻]?\d[\d.]*", "#", p.what)) for p in notes)
        print("\nnotes (not failures):")
        for (sv, w), k in sorted(ng.items()):
            print(f"   {sv}: {w}  x{k}")
    print(f"\n{'PASS' if not real else 'FAIL'}: {len(real)} failures")
    return 0 if not real else 1


if __name__ == "__main__":
    sys.exit(main())
