"""Pressure test: SOLVE FOR X (main menu 1) with equations that aren't polynomials.

    python stress/solve_nonpoly.py            # every case (1464), 4-9 min with -j 2-3
    python stress/solve_nonpoly.py -k log     # only families whose name contains "log"
    python stress/solve_nonpoly.py -j 1       # one process (default 3)
    python stress/solve_nonpoly.py -v         # also print every case's screens

Families: absolute value, radicals, rational, exponential, logs, trig (Radian
and Degree), mixed equations that need the numeric search, and "SEARCH A
RANGE" after a numeric answer. Cases are generated from a fixed seed.

The oracle is independent of Brainiac: each case also carries its two sides
written in numpy with REAL-number meaning (√ and log of a negative are
undefined, 0/0 is undefined), and the real solution set is found by a dense
sweep (sign changes, touches, domain edges, all refined) over -1000..1000
(Radian) or the degree window. Holes (0/0) are found by snapping a root to a
simple fraction and evaluating there.

Checks, per case:
  CRASH        any ERR, or no WHAT NEXT? menu at the end
  LEAK         leaks != 0 or blocks left open
  WRONG        a value shown as an answer (no NO:/NOT REAL) that isn't a real
               solution; "EVERY X WORKS" / "NO ANSWER" claims that are false
  MISSED       a real solution (inside the range it says it searched, for the
               numeric search; anywhere, for the textbook methods) not shown
               as an answer, or shown with NO: FAILS CHECK / BOTTOM=0
  COMPLEX      a complex value shown as an answer to an abs or radical equation
  STEP         a working line that is mathematically wrong (GET .. ALONE value,
               CASE / SQUARE / LOG lines, the "...=0" polynomial not vanishing
               at the true answers, a log of a number that is 0 or less)
  RANGE        "(SEARCHED A TO B)" not what was really searched, or not the
               documented window (⁻99..99, or 0..360 for trig in Degree)
  SCREEN       text written over other text, or past the bottom of the screen,
               measured in real TI-84 Plus CE character cells (10^( is 3 cells,
               ⁻¹ is 1), menu options over 24 characters
Exit code 0 only if every case passes.
"""

from __future__ import annotations

import argparse
import cmath
import math
import random
import re
import sys
import time
from collections import Counter, defaultdict
from fractions import Fraction
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from brainiac.build import PROGRAMS, program_bytes          # noqa: E402
from brainiac.screen import view                            # noqa: E402
from brainiac.tibasic import (Machine, Screen, glyphs,      # noqa: E402
                              keyboard_to_tokens, tokens_from_bytes)

SEED = 20261014

# ============================================================== the machine

TOK = None


def tokens():
    global TOK
    if TOK is None:
        TOK = {n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}
    return TOK


class RecScreen(Screen):
    """A home screen that also remembers each Output( as one logical line."""

    def clear(self):
        super().clear()
        if not hasattr(self, "page"):
            self.pages = []
        self.page = []
        self.pages.append(self.page)

    def output(self, row, col, text):
        self.page.append((row, col, text))
        super().output(row, col, text)


def fresh(degree):
    t = tokens()
    m = Machine(t["BRAINIAC"])
    m.programs.update({k: v for k, v in t.items() if k != "BRAINIAC"})
    m.screen = RecScreen()
    m.radians = not degree
    m.run()
    return m


def cells(text):
    """How many character cells text takes on the real calculator."""
    return len(text) - text.count("₁₀") - text.count("⁻¹")


def keys(typed):
    """Fix up what we type: after e^( ³√( and 10^( a minus is the (-) key.
    (brainiac.tibasic.keyboard_to_tokens only spots a leading minus after a
    plain "(", so "e^(-X)" would otherwise become e^( then a subtraction.)"""
    return re.sub(r"(e\^\(|³√\(|\\xC1|\\xBD)-", r"\1~", typed)


def g(typed):
    """What the calculator shows for something typed."""
    return glyphs(keyboard_to_tokens(keys(typed)))


def drive(m, answers, seq, shown, problems):
    """Give answers in turn, then press ENTER through every result page.
    Appends every page that was on screen while waiting to `shown`."""
    for a in answers:
        if m.wait.kind not in ("menu", "input", "pause"):
            return
        seq.append(a)
        m.answer(a)
    while m.wait.kind == "pause":
        pg = m.screen.page
        if pg and (not shown or shown[-1] is not pg):
            shown.append(pg)
        seq.append("")
        m.answer("")
    if m.wait.kind == "menu":
        for o in m.wait.options:
            if len(o) > 24:
                problems.append(("SCREEN", f"menu option over 24 chars: {o!r}"))


def screen_check(pages, problems):
    """Overlapping text or text off the bottom, in real character cells."""
    for pg in pages:
        used = {}
        for row, col, text in pg:
            if text in ("THINKING...",):
                continue
            w = cells(text)
            start = (row - 1) * 26 + (col - 1)
            if start + w > 260:
                problems.append(("SCREEN", f"runs off the bottom of the screen: {text!r}"))
            for c in range(start, min(start + w, 260)):
                if c in used and used[c] != text:
                    problems.append(("SCREEN", f"{text!r} is written over {used[c]!r}"))
                    break
                used[c] = text


# ============================================================== reading the screens

ANS_SUFFIXES = [(" NO: FAILS CHECK", "fails"), (" NO: BOTTOM=0", "bottom"),
                (" NOT REAL", "notreal")]


def gval(s):
    """A number as the program shows it (⁻2, 7/3, (⁻5+𝑖√(55))/2, 2.5ᴇ⁻5) -> complex."""
    t = s.strip().replace("⁻", "-").replace("ᴇ", "e").replace("√(", "S(")
    t = t.replace("𝑖", "I").replace("π", "P")
    if not re.fullmatch(r"[0-9.eSIP()+\-*/]+", t):
        return None
    t = re.sub(r"(?<=[0-9.)IP])(?=[S(IP])", "*", t)
    t = t.replace("I", "1j")
    try:
        return complex(eval(t, {"__builtins__": {}}, {"S": cmath.sqrt, "P": math.pi}))
    except Exception:
        return None


def gpoly(s):
    """'X²-3X-10' -> (f(x), scale(x)) or None."""
    t = s.replace("⁻", "-").replace("ᴇ", "e").replace("π", "P")
    t = t.replace("X²", "X**2").replace("X³", "X**3").replace("X^", "X**")
    if not re.fullmatch(r"[0-9.eXP()+\-*/]+", t):
        return None
    t = re.sub(r"(?<=[0-9.)P])(?=[XP(])", "*", t)
    terms = [x for x in re.split(r"(?<=[^e*(/])(?=[+-])", t) if x]
    try:
        code = [compile(x, "poly", "eval") for x in terms]
    except SyntaxError:
        return None

    def f(x):
        return sum(eval(c, {"__builtins__": {}}, {"X": x, "P": math.pi}) for c in code)

    def scale(x):
        return sum(abs(eval(c, {"__builtins__": {}}, {"X": x, "P": math.pi})) for c in code)
    return f, scale


def parse(pages):
    """The logical lines of every page shown -> what the program said."""
    lines = [t for pg in pages for (_, _, t) in pg
             if t not in ("THINKING...",) and not t.startswith("ENTER:")]
    out = {"lines": lines, "answers": [], "searched": None, "every": [], "never": [],
           "nofound": False, "powernever": False, "alone": [], "polys": [], "logs": [],
           "cases": [], "square": [], "unparsed": [], "truewhen": [], "every_defined": False}
    case = 0
    expect = None          # what the next line is
    for i, ln in enumerate(lines):
        if expect == "logline":
            out["logs"].append(ln)
            expect = "poly"
            continue
        if expect == "poly":
            expect = None
            if ln.endswith("=0"):
                out["polys"].append((case, ln[:-2]))
                continue
        if expect == "alone":
            out["alone"].append(ln)
            expect = None
            continue
        if expect == "square":
            out["square"].append(ln)
            expect = None
            continue
        if ln.startswith("SOLVING: ") or ln.startswith("ZEROS OF ") or ln.startswith("FACTORED: "):
            continue
        m = re.fullmatch(r"\(SEARCHED (\S+) TO (\S+)\)", ln)
        if m:
            out["searched"] = (gval(m.group(1)).real, gval(m.group(2)).real)
            continue
        if ln.startswith("GET ") and ln.endswith(" ALONE:"):
            out["alone"].append(ln)
            expect = "alone"
            continue
        m = re.match(r"CASE (\d): ", ln)
        if m:
            case = int(m.group(1))
            out["cases"].append(ln)
            continue
        if ln in ("SQUARE BOTH SIDES:", "CUBE BOTH SIDES:"):
            out["square"].append(ln)
            expect = "square"
            continue
        if "OF EACH:" in ln or ln == "TAKE LN OF BOTH SIDES:":
            out["logs"].append(ln)
            expect = "logline"
            continue
        if ln.startswith("RAISE ") or ln.startswith("TIMES "):
            out["logs"].append(ln)
            expect = "poly"
            continue
        if expect == "truewhen":
            expect = None
            out["truewhen"].append(ln)
            continue
        if ln == "EVERY X WORKS! BOTH SIDES":
            out["every"].append(case)
            continue
        if ln == "TRUE FOR EVERY X WITH":
            expect = "truewhen"
            continue
        if ln == "EVERY X WHERE BOTH SIDES":
            out["every_defined"] = True
            continue
        if ln == "HAVE A VALUE WORKS.":
            continue
        if ln == "NO ANSWER: THE TWO SIDES":
            out["never"].append(case)
            continue
        if ln in ("ARE ALWAYS EQUAL.", "ARE NEVER EQUAL.", "RANGE SEARCHED.",
                  "THAT IS NEVER 0 OR LESS."):
            continue
        if ln == "NO ANSWERS FOUND IN THE":
            out["nofound"] = True
            continue
        if ln == "NO ANSWER: A POWER LIKE":
            out["powernever"] = True
            continue
        if ln.startswith(" ="):
            continue
        if ln.startswith("X="):
            body, flags = ln[2:], set()
            for suf, flag in ANS_SUFFIXES:
                if body.endswith(suf):
                    body, flags = body[:-len(suf)], flags | {flag}
            mm = re.search(r" \((DOUBLE|TRIPLE|\d+ TIMES)\)$", body)
            if mm:
                body = body[:mm.start()]
                flags.add("double")
            parts = body.split("=")
            v = gval(parts[0])
            if v is None and len(parts) > 1:
                v = gval(parts[-1])
            if v is None:
                out["unparsed"].append(ln)
                continue
            out["answers"].append({"v": v, "flags": flags, "line": ln, "case": case})
            continue
        out["unparsed"].append(ln)
    return out


# ============================================================== the oracle

LD = np.longdouble      # 80-bit: 3^(-3000) and e^(1000) don't under/overflow


def _where(cond, f, x):
    x = np.asarray(x, dtype=LD)
    ok = cond(x)
    return np.where(ok, f(np.where(ok, x, LD(1))), LD(np.nan))


def make_env(deg):
    k = LD(math.pi) / 180 if deg else LD(1)
    return {
        "np": np,
        "SQ": lambda x: _where(lambda t: t >= 0, np.sqrt, x),
        "CB": lambda x: np.cbrt(np.asarray(x, dtype=LD)),
        "LG": lambda x: _where(lambda t: t > 0, np.log10, x),
        "LN": lambda x: _where(lambda t: t > 0, np.log, x),
        "EXP": lambda x: np.exp(np.asarray(x, dtype=LD)),
        "ABS": np.abs,
        "SIN": lambda x: np.sin(np.asarray(x, dtype=LD) * k),
        "COS": lambda x: np.cos(np.asarray(x, dtype=LD) * k),
        "TAN": lambda x: _where(lambda t: np.abs(np.cos(t * k)) > 1e-18,
                                lambda t: np.tan(t * k), x),
        "DIV": lambda a, b: _div(a, b),
        "PW": lambda a, b: _pw(a, b),
    }


def _div(a, b):
    a, b = np.broadcast_arrays(np.asarray(a, dtype=LD), np.asarray(b, dtype=LD))
    zero = b == 0
    return np.where(zero, LD(np.nan), a / np.where(zero, LD(1), b))


def _pw(a, b):
    """a^b for real numbers: a>0, or a whole-number power."""
    a, b = np.broadcast_arrays(np.asarray(a, dtype=LD), np.asarray(b, dtype=LD))
    out = np.full(a.shape, np.nan, dtype=LD)
    pos = a > 0
    out[pos] = np.exp(b[pos] * np.log(a[pos]))
    whole = (~pos) & (b == np.round(b))
    out[whole] = np.power(a[whole], b[whole])
    zero = (a == 0) & (b > 0)
    out[zero] = 0
    return out


class Side:
    def __init__(self, py, deg):
        self.code = compile(py, py, "eval")
        self.env = make_env(deg)

    def __call__(self, x):
        x = np.asarray(x, dtype=LD)
        with np.errstate(all="ignore"):
            v = eval(self.code, self.env, {"x": x})
        return np.broadcast_to(np.asarray(v, dtype=LD), x.shape).astype(LD)


def nice(r):
    """r snapped to a simple fraction, if it is one."""
    if not math.isfinite(r):
        return None
    f = Fraction(r).limit_denominator(1000)
    if abs(float(f) - r) <= 1e-9 * max(1.0, abs(r)):
        return float(f)
    return None


class Oracle:
    """The real solutions of L(x)=R(x)."""

    def __init__(self, pyL, pyR, deg, lo=-1000.0, hi=1000.0):
        self.L, self.R = Side(pyL, deg), Side(pyR, deg)
        self.lo, self.hi = lo, hi
        self.roots, self.identity = self._solve()

    def F(self, x):
        return self.L(x) - self.R(x)

    def f1(self, x):
        x = np.array([x], dtype=float)
        l, r = self.L(x)[0], self.R(x)[0]
        return l - r, 1.0 + abs(l) + abs(r)

    def is_root(self, x, tol=1e-7):
        v, s = self.f1(x)
        return math.isfinite(v) and abs(v) <= tol * s

    def _grid(self):
        lo, hi = self.lo, self.hi
        parts = []
        a, b = max(lo, -110.0), min(hi, 110.0)
        if a < b:
            parts.append(np.linspace(a, b, 200001))
        near0 = np.logspace(-25, 0, 5000)
        parts.append(near0[(near0 >= lo) & (near0 <= hi)])
        parts.append(-near0[(-near0 >= lo) & (-near0 <= hi)])
        if lo < -110:
            parts.append(np.linspace(lo, -110.0, 40001))
        if hi > 110:
            parts.append(np.linspace(110.0, hi, 40001))
        return np.unique(np.concatenate(parts))

    def _solve(self):
        x = self._grid()
        l, r = self.L(x), self.R(x)
        y = l - r
        sc = 1.0 + np.abs(l) + np.abs(r)
        nan = np.isnan(y)
        fin = np.isfinite(y)
        found = []
        # identities: the two sides agree on a stretch while they change
        # (4e^(X)+4=4 for X near -1000 only looks like one: e^(X) vanishes)
        l0 = np.where(fin, l, LD(0))
        mag = np.abs(l0) + np.abs(np.where(fin, r, LD(0)))
        moving = np.abs(np.gradient(l0)) > 1e-9 * (1 + np.abs(l0))
        tiny = fin & (mag > 0) & (np.abs(y) <= 1e-13 * mag) & moving
        if tiny.sum() > 1000:
            return [], {"all": bool(tiny.sum() == len(x)), "frac": float(tiny.mean())}
        # samples that are exactly 0 (but not a flat stretch where one side
        # has become too small to matter, like 4e^(X)+4=4 near X=-1000)
        z = np.nonzero(fin & (y == 0))[0]
        if len(z):
            h = 1e-4 * np.maximum(1.0, np.abs(x[z]))
            lo_, hi_ = self.F(x[z] - h), self.F(x[z] + h)
            lo_ = np.where(np.isfinite(lo_), np.abs(lo_), LD(0))
            hi_ = np.where(np.isfinite(hi_), np.abs(hi_), LD(0))
            moves = np.maximum(lo_, hi_) > 1e-13 * (1 + np.abs(l[z]) + np.abs(r[z]))
            found += [float(v) for v in x[z][moves]]
        # sign changes, all halved at once
        s = np.sign(np.where(fin, y, 0.0))
        idx = np.nonzero(fin[:-1] & fin[1:] & (s[:-1] * s[1:] < 0))[0]
        if len(idx):
            a, b = x[idx].copy(), x[idx + 1].copy()
            fa = y[idx].copy()
            for _ in range(80):
                mid = (a + b) / 2
                fm = self.F(mid)
                left = np.isfinite(fm) & (np.sign(fm) == np.sign(fa))
                a = np.where(left, mid, a)
                fa = np.where(left, fm, fa)
                b = np.where(left, b, mid)
            va, vb = self.F(a), self.F(b)
            la, lb = self.L(a), self.L(b)
            ra, rb = self.R(a), self.R(b)
            qa = np.abs(va) / (1 + np.abs(la) + np.abs(ra))
            qb = np.abs(vb) / (1 + np.abs(lb) + np.abs(rb))
            qa = np.where(np.isfinite(qa), qa, np.inf)
            qb = np.where(np.isfinite(qb), qb, np.inf)
            best = np.where(qa <= qb, a, b)
            # a root: F got small; an asymptote: F got big. (A cube root's F
            # is still ~1e-5 this close to its root, so compare with the start.)
            start = np.minimum(np.abs(y[idx]), np.abs(y[idx + 1]))
            fin_a = np.where(np.isfinite(va), np.abs(va), LD(np.inf))
            fin_b = np.where(np.isfinite(vb), np.abs(vb), LD(np.inf))
            ok = (np.minimum(qa, qb) <= 1e-6) | (np.minimum(fin_a, fin_b) <= 1e-4 * start)
            found += [float(v) for v in best[ok]]
        # touches: a dip of |F| toward 0 with no sign change (golden section)
        ay = np.where(fin, np.abs(y), np.inf)
        cand = np.nonzero((ay[1:-1] <= ay[:-2]) & (ay[1:-1] < ay[2:]) &
                          (ay[1:-1] < 1e-2 * sc[1:-1]) &
                          (s[:-2] * s[1:-1] > 0) & (s[1:-1] * s[2:] > 0))[0] + 1
        if len(cand):
            a, b = x[cand - 1].copy(), x[cand + 1].copy()
            g1 = (math.sqrt(5) - 1) / 2
            for _ in range(100):
                c1, c2 = b - g1 * (b - a), a + g1 * (b - a)
                f1 = np.abs(self.F(c1))
                f2 = np.abs(self.F(c2))
                f1 = np.where(np.isfinite(f1), f1, np.inf)
                f2 = np.where(np.isfinite(f2), f2, np.inf)
                lower = f1 < f2
                b = np.where(lower, c2, b)
                a = np.where(lower, a, c1)
            mid = (a + b) / 2
            v = self.F(mid)
            scm = 1 + np.abs(self.L(mid)) + np.abs(self.R(mid))
            # a real touch goes right down to 0 between neighbours that aren't
            # (4e^(X)+4=4 near X=-43 only gets small: e^(X) is tiny there)
            nb = np.maximum(ay[cand - 1], ay[cand + 1])
            ok = np.isfinite(v) & (np.abs(v) <= 1e-9 * scm) & (np.abs(v) <= 1e-6 * nb)
            found += [float(t) for t in mid[ok]]
        # domain edges: where F starts or stops being defined
        idx = np.nonzero((fin[:-1] & nan[1:]) | (nan[:-1] & fin[1:]))[0]
        if len(idx):
            a, b = x[idx].copy(), x[idx + 1].copy()
            good_left = fin[idx]
            for _ in range(80):
                mid = (a + b) / 2
                ok = np.isfinite(self.F(mid))
                to_a = ok == good_left
                a = np.where(to_a, mid, a)
                b = np.where(to_a, b, mid)
            e = np.where(good_left, a, b)
            v = self.F(e)
            sce = 1 + np.abs(self.L(e)) + np.abs(self.R(e))
            ok = np.isfinite(v) & (np.abs(v) <= 1e-6 * sce)
            found += [float(t) for t in e[ok]]
        # snap to simple fractions; a fraction where F is undefined is a hole
        roots = []
        for r0 in sorted(found):
            # (floating point near a hole like (X²-4)/(X-2) at 2 is noisy, so
            # the halving can stop up to ~1e-7 away from it)
            f = Fraction(r0).limit_denominator(100)
            n = float(f)
            if abs(n - r0) > 1e-6 * max(1.0, abs(r0)) or (n == 0 and abs(r0) > 1e-30):
                n = nice(r0) if abs(r0) > 1e-9 else None
            if n is not None:
                v, sc1 = self.f1(n)
                if not math.isfinite(v):
                    # undefined right there: a hole like (X²-4)/(X-2) at 2 has
                    # values on both sides; the edge of √(5X-7) only on one
                    # (and 5(1.4)-7 isn't exactly 0 in binary)
                    d = 1e-12 * max(1.0, abs(n))
                    vl, vr = self.f1(n - d)[0], self.f1(n + d)[0]
                    if math.isfinite(vl) and math.isfinite(vr):
                        continue      # a hole: not an answer
                    side, ssc = self.f1(n - d) if math.isfinite(vl) else self.f1(n + d)
                    if not (math.isfinite(side) and abs(side) <= 1e-5 * ssc):
                        continue
                    if not roots or abs(n - roots[-1]) > 1e-7 * max(1.0, abs(n)):
                        roots.append(n)
                    continue
                if abs(v) <= 1e-9 * sc1:
                    r0 = n
            if roots and abs(r0 - roots[-1]) <= 1e-7 * max(1.0, abs(r0)):
                continue
            roots.append(r0)
        return roots, None


# ============================================================== the cases

def T(n):
    """A number typed (negative with the (-) key: the keyboard does that)."""
    if isinstance(n, Fraction):
        if n.denominator == 1:
            return str(n.numerator)
        return f"{n.numerator}/{n.denominator}"
    if isinstance(n, float) and n == int(n) and abs(n) < 1e9:
        n = int(n)
    s = repr(n) if isinstance(n, float) else str(n)
    if s.startswith("0."):
        s = s[1:]
    if s.startswith("-0."):
        s = "-" + s[2:]
    return s


def P(n):
    """The same number for numpy."""
    if isinstance(n, Fraction):
        return f"({n.numerator}/{n.denominator})"
    return f"({n!r})"


def lin(a, b, v="X"):
    """aX+b typed, and as numpy."""
    if a == 1:
        t = v
    elif a == -1:
        t = "-" + v
    else:
        t = f"{T(a)}{v}"
    if b:
        t += ("+" if b > 0 else "-") + T(abs(b))
    return t, f"({P(a)}*x+{P(b)})"


def case(fam, typed, L, R, deg=False, **kw):
    c = {"fam": fam, "typed": typed, "L": L, "R": R, "deg": deg}
    c.update(kw)
    return c


def nz(rng, lo, hi):
    while True:
        v = rng.randint(lo, hi)
        if v:
            return v


def gen_abs(rng):
    out = []
    for _ in range(45):            # abs(aX+b)=c, c any sign, sometimes on the left
        a, b = nz(rng, -6, 6), rng.randint(-9, 9)
        c = rng.choice([rng.randint(1, 15)] * 4 + [0, -rng.randint(1, 9)])
        lt, lp = lin(a, b)
        exp = [f"CASE 1: {g(lt)}={g(T(c))}", f"CASE 2: {g(lt)}=⁻({g(T(c))})"]
        if rng.random() < 0.3:
            out.append(case("abs/right", f"{T(c)}=abs({lt})", P(c), f"ABS({lp})", cases=exp))
        else:
            out.append(case("abs/basic", f"abs({lt})={T(c)}", f"ABS({lp})", P(c), cases=exp))
    for _ in range(45):            # k·abs(..)+d=e, the abs to get alone first
        a, b = nz(rng, -5, 5), rng.randint(-9, 9)
        k = rng.choice([2, 3, 4, 5, -2, -3, Fraction(1, 2)])
        d, e = rng.randint(-9, 9), rng.randint(-20, 20)
        lt, lp = lin(a, b)
        v = Fraction(e - d) / k
        ab = f"abs({lt})"
        form = rng.randrange(4)
        if k == Fraction(1, 2):
            typed, L = f"{ab}/2{'+' if d >= 0 else '-'}{abs(d)}={T(e)}", f"ABS({lp})/2+{P(d)}"
        elif form == 0:
            typed, L = f"{T(k)}{ab}{'+' if d >= 0 else '-'}{abs(d)}={T(e)}", f"{P(k)}*ABS({lp})+{P(d)}"
        elif form == 1:
            typed, L = f"{T(d)}+{T(k)}{ab}={T(e)}" if k > 0 else f"{T(d)}{T(k)}{ab}={T(e)}", \
                f"{P(d)}+{P(k)}*ABS({lp})"
        elif form == 2:
            typed, L = f"{T(e)}={T(k)}{ab}{'+' if d >= 0 else '-'}{abs(d)}", None
        else:
            typed, L = f"{T(k)}{ab}={T(e - d)}", f"{P(k)}*ABS({lp})"
            v = Fraction(e - d) / k
        if form == 2:
            out.append(case("abs/alone", typed, P(e), f"{P(k)}*ABS({lp})+{P(d)}",
                            alone=(g(ab), float(v))))
        else:
            out.append(case("abs/alone", typed, L, P(e) if form != 3 else P(e - d),
                            alone=(g(ab), float(v))))
    for _ in range(30):            # abs of a quadratic
        p, q = rng.randint(-6, 6), rng.randint(-12, 12)
        c = rng.choice([rng.randint(0, 12)] * 3 + [-rng.randint(1, 5)])
        inner = "X^2" + (f"{'+' if p > 0 else '-'}{abs(p) if abs(p) != 1 else ''}X" if p else "") + \
            (f"{'+' if q > 0 else '-'}{abs(q)}" if q else "")
        out.append(case("abs/quadratic", f"abs({inner})={T(c)}",
                        f"ABS(x**2+{p}*x+{q})", P(c)))
    for _ in range(25):            # two abs: the numeric search
        a, b = nz(rng, -4, 4), rng.randint(-9, 9)
        c, d = nz(rng, -4, 4), rng.randint(-9, 9)
        if (a, b) in ((c, d), (-c, -d)):
            d += 1
        l1, p1 = lin(a, b)
        l2, p2 = lin(c, d)
        out.append(case("abs/two", f"abs({l1})=abs({l2})", f"ABS({p1})", f"ABS({p2})"))
    for _ in range(40):            # abs(aX+b)=cX+d: extraneous roots
        a, b = nz(rng, -5, 5), rng.randint(-9, 9)
        c, d = nz(rng, -4, 4), rng.randint(-9, 9)
        if abs(a) == abs(c):
            c += 1 if c > 0 else -1
        l1, p1 = lin(a, b)
        l2, p2 = lin(c, d)
        out.append(case("abs/extraneous", f"abs({l1})={l2}", f"ABS({p1})", p2,
                        cases=[f"CASE 1: {g(l1)}={g(l2)}", f"CASE 2: {g(l1)}=⁻({g(l2)})"]))
    # abs(X)=X and friends: true for a whole half-line
    for t, L, R in [("abs(X)=X", "ABS(x)", "x"), ("abs(X)=-X", "ABS(x)", "-x"),
                    ("abs(X-2)=X-2", "ABS(x-2)", "x-2"), ("abs(2X+1)=-2X-1", "ABS(2*x+1)", "-2*x-1"),
                    ("abs(X)+1=X^2", "ABS(x)+1", "x**2"), ("abs(X^2)=X^2", "ABS(x**2)", "x**2"),
                    ("abs(X-3)=3-X", "ABS(x-3)", "3-x"), ("abs(X)=2X+3", "ABS(x)", "2*x+3")]:
        out.append(case("abs/special", t, L, R))
    return out


def gen_radical(rng):
    out = []
    for _ in range(50):            # √(aX+b)=cX+d
        a, b = nz(rng, -5, 6), rng.randint(-9, 12)
        c, d = rng.choice([1, 1, 1, 2, -1, 3]), rng.randint(-6, 6)
        l1, p1 = lin(a, b)
        l2, p2 = lin(c, d)
        out.append(case("rad/linear", f"√({l1})={l2}", f"SQ({p1})", p2,
                        square=[f"{g(l1)}=({g(l2)})²"]))
    for _ in range(30):            # √(aX+b)=c
        a, b = nz(rng, -5, 6), rng.randint(-9, 12)
        c = rng.choice([rng.randint(0, 9)] * 4 + [-rng.randint(1, 5)])
        l1, p1 = lin(a, b)
        if rng.random() < 0.25:
            out.append(case("rad/const", f"{T(c)}=√({l1})", P(c), f"SQ({p1})",
                            square=[f"{g(l1)}=({g(T(c))})²"]))
        else:
            out.append(case("rad/const", f"√({l1})={T(c)}", f"SQ({p1})", P(c),
                            square=[f"{g(l1)}=({g(T(c))})²"]))
    for _ in range(25):            # ³√(aX+b)=c
        a, b = nz(rng, -5, 6), rng.randint(-9, 12)
        c = rng.randint(-5, 5)
        l1, p1 = lin(a, b)
        out.append(case("rad/cube", f"³√({l1})={T(c)}", f"CB({p1})", P(c),
                        square=[f"{g(l1)}=({g(T(c))})³"]))
    for _ in range(40):            # k√(..)+d=e
        a, b = nz(rng, -4, 5), rng.randint(-9, 9)
        k = rng.choice([1, 2, 3, -2, 5])
        d, e = rng.randint(-9, 9), rng.randint(-15, 25)
        l1, p1 = lin(a, b)
        kt = {1: "", -1: "-"}.get(k, T(k))
        sq = f"√({l1})"
        if d == 0:
            d = 1
        typed = f"{kt}{sq}{'+' if d > 0 else '-'}{abs(d)}={T(e)}"
        out.append(case("rad/alone", typed, f"{P(k)}*SQ({p1})+{P(d)}", P(e),
                        alone=(g(sq), float(Fraction(e - d, k)))))
    for _ in range(35):            # √(..)=√(..): both sides can be undefined at once
        a, b = nz(rng, -5, 5), rng.randint(-12, 12)
        c, d = nz(rng, -5, 5), rng.randint(-12, 12)
        if a == c:
            c += 1
        l1, p1 = lin(a, b)
        l2, p2 = lin(c, d)
        out.append(case("rad/two", f"√({l1})=√({l2})", f"SQ({p1})", f"SQ({p2})"))
    for _ in range(20):            # √(X²+a)=b
        a, b = rng.randint(-16, 16), rng.randint(-2, 10)
        out.append(case("rad/quadratic", f"√(X^2{'+' if a >= 0 else '-'}{abs(a)})={T(b)}",
                        f"SQ(x**2+{a})", P(b)))
    for _ in range(20):            # two radicals or a radical and X: the numeric search
        a, b, c = rng.randint(-5, 9), rng.randint(-9, 5), rng.randint(1, 9)
        if rng.random() < 0.5:
            out.append(case("rad/numeric", f"√(X+{a})+√(X{'+' if b >= 0 else '-'}{abs(b)})={c}"
                            if a >= 0 else f"√(X-{-a})+√(X{'+' if b >= 0 else '-'}{abs(b)})={c}",
                            f"SQ(x+{a})+SQ(x+{b})", P(c)))
        else:
            out.append(case("rad/numeric", f"X-√(X)={c}", "x-SQ(x)", P(c)))
    return out


def gen_rational(rng):
    out = []
    for _ in range(30):            # a/(X-p)+c=(dX+e)/(X-p), sometimes the answer is the hole
        p = rng.randint(-6, 6)
        a, c, d = nz(rng, -9, 9), nz(rng, -4, 4), rng.randint(-4, 4)
        if c == d:
            d += 1
        e = rng.randint(-9, 9)
        if rng.random() < 0.35:     # make (c-d)p = e - a + cp, so X=p
            e = (c - d) * p + a - c * p
        bt = lin(1, -p)[0]
        dt, dp = lin(d, e) if d else (T(e), P(e))
        out.append(case("rat/same-bottom", f"{T(a)}/({bt})+{T(c)}=({dt})/({bt})",
                        f"DIV({a},x-{P(p)})+{c}", f"DIV({dp},x-{P(p)})"))
    for _ in range(30):            # 1/(X-a)+1/(X-b)=c
        a, b = rng.randint(-5, 5), rng.randint(-5, 5)
        if a == b:
            b += 2
        c = rng.choice([1, 2, -1, Fraction(1, 2), 3])
        out.append(case("rat/two-bottoms", f"1/({lin(1, -a)[0]})+1/({lin(1, -b)[0]})={T(c)}",
                        f"DIV(1,x-{P(a)})+DIV(1,x-{P(b)})", P(c)))
    for _ in range(20):            # (X²-a²)/(X-a)=c: a hole at X=a
        a = nz(rng, -6, 6)
        c = rng.choice([2 * a, 2 * a, rng.randint(-9, 9)])
        out.append(case("rat/hole", f"(X^2-{a * a})/({lin(1, -a)[0]})={T(c)}",
                        f"DIV(x**2-{a * a},x-{P(a)})", P(c)))
    for _ in range(20):            # (aX+b)/(X+c)=d
        a, b, c, d = nz(rng, -5, 5), rng.randint(-9, 9), rng.randint(-6, 6), nz(rng, -6, 6)
        l1, p1 = lin(a, b)
        out.append(case("rat/linear-over", f"({l1})/({lin(1, c)[0]})={T(d)}",
                        f"DIV({p1},x+{P(c)})", P(d)))
    for _ in range(30):            # the letter on the bottom of both sides
        a, b, c = nz(rng, -9, 9), nz(rng, -9, 9), nz(rng, -6, 6)
        k = rng.randrange(3)
        if k == 0:
            out.append(case("rat/both-sides", f"{T(a)}/X={T(b)}/({lin(1, c)[0]})",
                            f"DIV({a},x)", f"DIV({b},x+{P(c)})"))
        elif k == 1:
            out.append(case("rat/both-sides", f"X/({lin(1, -c)[0]})={T(c)}/({lin(1, -c)[0]})+{abs(a)}",
                            f"DIV(x,x-{P(c)})", f"DIV({c},x-{P(c)})+{abs(a)}"))
        else:
            out.append(case("rat/both-sides", f"{T(a)}/({lin(1, -c)[0]})=X-{abs(b)}",
                            f"DIV({a},x-{P(c)})", f"x-{abs(b)}"))
    for t, L, R in [("3/(X-2)+1=X/(X-2)", "DIV(3,x-2)+1", "DIV(x,x-2)"),
                    ("(X^2-4)/(X-2)=4", "DIV(x**2-4,x-2)", "4"),
                    ("1/(X-1)+1/(X+1)=1", "DIV(1,x-1)+DIV(1,x+1)", "1"),
                    ("(X^2-4)/(X-2)=X+2", "DIV(x**2-4,x-2)", "x+2"),
                    ("X/(X-1)=1/(X-1)", "DIV(x,x-1)", "DIV(1,x-1)"),
                    ("1/X=X", "DIV(1,x)", "x"), ("1/(X^2+1)=2", "DIV(1,x**2+1)", "2")]:
        out.append(case("rat/special", t, L, R))
    return out


BASES = [2, 3, 4, 5, 6, 10]


def gen_expo(rng):
    out = []
    for _ in range(30):            # b^X=v
        b = rng.choice(BASES)
        k = rng.randint(-3, 4)
        v = Fraction(b) ** k if rng.random() < 0.7 else Fraction(rng.randint(1, 60))
        out.append(case("exp/basic", f"{b}^X={T(v)}", f"PW({b},x)", P(v),
                        logbase=str(b)))
    for _ in range(25):            # b^(pX+q)=b^k
        b = rng.choice([2, 3, 5])
        p, q, k = nz(rng, -3, 3), rng.randint(-4, 4), rng.randint(-2, 4)
        l1, p1 = lin(p, q)
        out.append(case("exp/linear", f"{b}^({l1})={T(Fraction(b) ** k)}", f"PW({b},{p1})",
                        P(Fraction(b) ** k), logbase=str(b)))
    for _ in range(25):            # powers of the same base on both sides
        fam = rng.choice([(2, 4, 8, 16), (3, 9, 27)])
        b1, b2 = rng.sample(fam, 2)
        l1, p1 = lin(nz(rng, -3, 3), rng.randint(-4, 4))
        l2, p2 = lin(nz(rng, -3, 3), rng.randint(-4, 4))
        out.append(case("exp/both", f"{b1}^({l1})={b2}^({l2})", f"PW({b1},{p1})",
                        f"PW({b2},{p2})", logbase=str(b1)))
    for _ in range(15):            # (1/b)^X=v
        b = rng.choice([2, 3, 4, 5])
        k = rng.randint(-3, 3)
        out.append(case("exp/fraction-base", f"(1/{b})^X={T(Fraction(b) ** k)}",
                        f"PW(1/{b},x)", P(Fraction(b) ** k)))
    for _ in range(25):            # e^
        k, c = rng.choice([1, 1, 2, 3, -1, Fraction(1, 2)]), rng.randint(-3, 3)
        v = rng.choice([Fraction(rng.randint(1, 30)), Fraction(1, rng.randint(2, 9))])
        if k == Fraction(1, 2):
            ex, pe = "X/2", "x/2"
        else:
            ex, pe = lin(k, c)
        out.append(case("exp/e", f"e^({ex})={T(v)}", f"EXP({pe})", P(v), logbase="e"))
    for _ in range(30):            # 10^(..) typed two ways
        k = rng.randint(-2, 5)
        v = Fraction(10) ** k if rng.random() < 0.7 else Fraction(rng.randint(2, 500))
        a, c = rng.choice([1, 1, 2, -1]), rng.randint(-3, 3)
        ex, pe = lin(a, c)
        if rng.random() < 0.5:
            t = f"10^({ex})={T(v)}"
        else:
            t = "\\xC1" + ex + f")={T(v)}"
        if rng.random() < 0.3:
            t = T(v) + "=" + t.split("=")[0]
            out.append(case("exp/ten", t, P(v), f"PW(10,{pe})", logbase="10"))
        else:
            out.append(case("exp/ten", t, f"PW(10,{pe})", P(v), logbase="10"))
    for _ in range(50):            # numbers around the power: get it alone first
        b = rng.choice([2, 3, 5, "e"])
        k = rng.choice([1, 2, 3, 4, 5, -2, -3])
        d = rng.randint(-12, 12)
        val = rng.choice([Fraction(b) ** rng.randint(0, 4) if b != "e" else Fraction(rng.randint(1, 9)),
                          Fraction(rng.randint(1, 20))])
        e = k * val + d
        if b == "e":
            pw, pp, term = "e^(X)", "EXP(x)", "𝑒^(X)"
        else:
            pw, pp, term = f"{b}^X", f"PW({b},x)", f"{b}^X"
        form = rng.randrange(5)
        if form == 0:
            typed = f"{T(k)}({pw}){'+' if d >= 0 else '-'}{abs(d)}={T(e)}"
        elif form == 1:
            typed = f"{T(k)}{pw}{'+' if d >= 0 else '-'}{abs(d)}={T(e)}" if b == "e" else \
                f"{T(k)}*{pw}{'+' if d >= 0 else '-'}{abs(d)}={T(e)}"
        elif form == 2:
            typed = f"{T(e)}={T(k)}({pw}){'+' if d >= 0 else '-'}{abs(d)}"
        elif form == 3:
            typed = f"0={T(k)}({pw}){'+' if d >= 0 else '-'}{abs(d)}{'-' if e >= 0 else '+'}{abs(e)}"
        else:
            typed = f"{pw}{'+' if d >= 0 else '-'}{abs(d)}={T(val + d)}"
            k, e = 1, val + d
        L, R = f"{P(k)}*{pp}+{P(d)}", P(e)
        if form == 3:
            L, R = "0", f"{P(k)}*{pp}+{P(d)}-{P(e)}"
        out.append(case("exp/alone", typed, L, R, alone=(term, float(Fraction(e - d) / k)),
                        logbase=str(b)))
    for _ in range(25):            # no answer
        b = rng.choice([2, 3, 5, 10])
        kind = rng.randrange(5)
        if kind == 0:
            d, v = rng.randint(1, 9), rng.randint(-9, 0)
            if v > d:
                v = d
            out.append(case("exp/none", f"{b}^X+{d}={T(v)}", f"PW({b},x)+{d}", P(v)))
        elif kind == 1:
            v = -rng.randint(1, 20)
            out.append(case("exp/none", f"{b}^X={T(v)}", f"PW({b},x)", P(v)))
        elif kind == 2:
            v = -rng.randint(1, 20)
            out.append(case("exp/none", f"e^(X)={T(v)}", "EXP(x)", P(v)))
        elif kind == 3:
            k = rng.randint(2, 5)
            out.append(case("exp/none", f"{k}e^(X)+{k}={k}", f"{k}*EXP(x)+{k}", P(k)))
        else:
            out.append(case("exp/none", f"-{b}^X=5", f"-PW({b},x)", "5"))
    for _ in range(15):            # a decimal base
        b = rng.choice([1.05, 1.5, .5, .8, 2.5, 1.1, .25])
        v = rng.choice([2, 3, 10, .5, .1, 1.5])
        out.append(case("exp/decimal-base", f"{T(b)}^X={T(v)}", f"PW({b},x)", P(v)))
    for _ in range(10):            # power of a power
        b = rng.choice([2, 3])
        k = rng.randint(2, 3)
        n = rng.randint(1, 3)
        out.append(case("exp/power-of-power", f"({b}^X)^{k}={b ** (k * n)}",
                        f"PW(PW({b},x),{k})", P(b ** (k * n))))
    for t, L, R in [("2^X=16", "PW(2,x)", "16"), ("3^(2X-1)=27", "PW(3,2*x-1)", "27"),
                    ("2^(X+1)=8^(X-1)", "PW(2,x+1)", "PW(8,x-1)"), ("(1/2)^X=8", "PW(.5,x)", "8"),
                    ("e^(X)=10", "EXP(x)", "10"), ("e^(2X)=7", "EXP(2*x)", "7"),
                    ("10^(X)=100", "PW(10,x)", "100"), ("\\xC1X)=100", "PW(10,x)", "100"),
                    ("5(2^X)=40", "5*PW(2,x)", "40"), ("3e^(X)-2=10", "3*EXP(x)-2", "10"),
                    ("2^X+5=3", "PW(2,x)+5", "3"), ("0=2^X-8", "0", "PW(2,x)-8"),
                    ("1.05^X=2", "PW(1.05,x)", "2"), ("2^X=0", "PW(2,x)", "0"),
                    ("e^(X)=0", "EXP(x)", "0"), ("1^X=5", "PW(1,x)", "5"),
                    ("2^X=3^X", "PW(2,x)", "PW(3,x)"), ("2^(X^2)=16", "PW(2,x**2)", "16"),
                    ("4^(X^2-1)=64", "PW(4,x**2-1)", "64"), ("(2^X)^2=16", "PW(PW(2,x),2)", "16"),
                    ("2^X=1/8", "PW(2,x)", "1/8"), ("5\\xC1X)+3000=503000", "5*PW(10,x)+3000", "503000"),
                    ("2^(3X)=2^(X+4)", "PW(2,3*x)", "PW(2,x+4)"), ("e^(X)=-1", "EXP(x)", "-1")]:
        out.append(case("exp/special", t, L, R))
    return out


def gen_log(rng):
    out = []
    for _ in range(30):            # log(X)+log(X+a)=k
        k = rng.choice([1, 2, 2, 3])
        N = 10 ** k
        r = rng.choice([d for d in range(1, N + 1) if N % d == 0 and d * d != N])
        a = N // r - r
        typed = f"log(X)+log(X{'+' if a > 0 else '-'}{abs(a)})={k}"
        out.append(case("log/sum", typed, f"LG(x)+LG(x+{a})", P(k)))
    for _ in range(20):            # ln(X)+ln(X+a)=ln(c)
        r, a = rng.randint(1, 9), rng.randint(-8, 8)
        if a == 0 or r + a <= 0:
            a = 1
        c = r * (r + a)
        out.append(case("log/ln-sum", f"ln(X)+ln(X{'+' if a > 0 else '-'}{abs(a)})=ln({c})",
                        f"LN(x)+LN(x+{a})", f"np.log({c})"))
    for _ in range(25):            # log(aX+b)=k
        a, b, k = nz(rng, -5, 5), rng.randint(-9, 9), rng.randint(-1, 3)
        l1, p1 = lin(a, b)
        f = rng.choice(["log", "ln"])
        out.append(case("log/linear", f"{f}({l1})={k}", f"{'LG' if f == 'log' else 'LN'}({p1})", P(k)))
    for _ in range(35):            # k·log(X)+d=e: the log alone first
        f = rng.choice(["log", "ln"])
        k = rng.choice([2, 3, -2, 4, Fraction(1, 2)])
        d, e = rng.randint(-6, 6), rng.randint(-6, 8)
        if k == Fraction(1, 2):
            typed = f"{f}(X)/2{'+' if d >= 0 else '-'}{abs(d)}={T(e)}"
        elif rng.random() < 0.5:
            typed = f"{T(k)}{f}(X){'+' if d >= 0 else '-'}{abs(d)}={T(e)}"
        else:
            typed = f"{T(e)}={T(k)}{f}(X){'+' if d >= 0 else '-'}{abs(d)}"
        LL = f"{P(k)}*{'LG' if f == 'log' else 'LN'}(x)+{P(d)}"
        if typed.startswith(T(e) + "="):
            out.append(case("log/alone", typed, P(e), LL,
                            alone=(f"{f}(X)", float(Fraction(e - d) / k))))
        else:
            out.append(case("log/alone", typed, LL, P(e),
                            alone=(f"{f}(X)", float(Fraction(e - d) / k))))
    for _ in range(20):            # log(X^2)=k, log(X^3)=k
        n = rng.choice([2, 3, 4])
        f = rng.choice(["log", "ln"])
        k = rng.randint(0, 4) if f == "log" else rng.randint(-2, 4)
        out.append(case("log/power", f"{f}(X^{n})={k}", f"{'LG' if f == 'log' else 'LN'}(x**{n})", P(k)))
    for _ in range(40):            # log(aX+b)=log(cX+d)
        a, b, c, d = nz(rng, -5, 5), rng.randint(-12, 12), nz(rng, -5, 5), rng.randint(-12, 12)
        if a == c:
            c += 1
        f = rng.choice(["log", "ln"])
        l1, p1 = lin(a, b)
        l2, p2 = lin(c, d)
        F = "LG" if f == "log" else "LN"
        out.append(case("log/equal", f"{f}({l1})={f}({l2})", f"{F}({p1})", f"{F}({p2})"))
    for _ in range(35):            # log(A)-log(B)=log(c): both can be negative at the root
        a, b, c = rng.randint(-9, 9), rng.randint(-9, 9), rng.choice([2, 3, 4, 5, Fraction(1, 2)])
        if a == b:
            b += 1
        f = rng.choice(["log", "ln"])
        F = "LG" if f == "log" else "LN"
        out.append(case("log/difference", f"{f}({lin(1, a)[0]})-{f}({lin(1, b)[0]})={f}({T(c)})",
                        f"{F}(x+{a})-{F}(x+{b})", f"{F}({P(c)})"))
    for _ in range(15):            # ln(X)=v
        f = rng.choice(["log", "ln"])
        v = rng.choice([0, 1, -2, 2, 3, Fraction(1, 2), -1])
        out.append(case("log/basic", f"{f}(X)={T(v)}", f"{'LG' if f == 'log' else 'LN'}(x)", P(v)))
    for t, L, R in [("log(X)+log(X-3)=1", "LG(x)+LG(x-3)", "1"),
                    ("ln(X)+ln(X+1)=ln(6)", "LN(x)+LN(x+1)", "np.log(6)"),
                    ("log(2X+4)=1", "LG(2*x+4)", "1"), ("2log(X)+1=5", "2*LG(x)+1", "5"),
                    ("3ln(X)=6", "3*LN(x)", "6"), ("log(X^2)=2", "LG(x**2)", "2"),
                    ("log(X^3)=6", "LG(x**3)", "6"), ("log(X)=log(2X-5)", "LG(x)", "LG(2*x-5)"),
                    ("ln(X)=0", "LN(x)", "0"), ("log(X)=-2", "LG(x)", "-2"),
                    ("log(X)=log(X+1)", "LG(x)", "LG(x+1)"), ("ln(X)=ln(2X)", "LN(x)", "LN(2*x)"),
                    ("log(X^2)=log(X)", "LG(x**2)", "LG(x)"), ("log(X)=log(X)+1", "LG(x)", "LG(x)+1"),
                    ("log(2X)=log(X-3)", "LG(2*x)", "LG(x-3)"),
                    ("log(X^2-9)=log(8X)", "LG(x**2-9)", "LG(8*x)"),
                    ("log(X)-log(X+3)=log(2)", "LG(x)-LG(x+3)", "LG(2)"),
                    ("log(X-4)-log(X-1)=log(2)", "LG(x-4)-LG(x-1)", "LG(2)"),
                    ("log(X)-log(X+1)=1", "LG(x)-LG(x+1)", "1"),
                    ("log(X^2)=2log(X)", "LG(x**2)", "2*LG(x)"),
                    ("log(X+3)=log(X)+1", "LG(x+3)", "LG(x)+1"), ("log(-X)=2", "LG(-x)", "2"),
                    ("ln(X^2)=ln(X+6)", "LN(x**2)", "LN(x+6)"),
                    ("2log(X)=log(X+6)", "2*LG(x)", "LG(x+6)"),
                    ("log(X)+log(X-3)=-1", "LG(x)+LG(x-3)", "-1")]:
        out.append(case("log/special", t, L, R))
    return out


TRIG_V = ["0", ".5", "-.5", "1", "-1", "√(3)/2", "-√(2)/2", ".3", "-.8", "2", "-1.5", ".99"]


def tv(s):
    return {"√(3)/2": "np.sqrt(3)/2", "-√(2)/2": "-np.sqrt(2)/2"}.get(s, s)


def gen_trig(rng):
    out = []
    for deg in (False, True):
        mode = "deg" if deg else "rad"
        for _ in range(35):        # sin(X)=v and friends
            f = rng.choice(["sin", "cos", "tan"])
            v = rng.choice(TRIG_V + ["3", "-2.5"] if f == "tan" else TRIG_V)
            out.append(case(f"trig/{mode}/basic", f"{f}(X)={v}", f"{f.upper()}(x)", tv(v), deg=deg))
        for _ in range(15):        # a·sin(X)+b=0
            f = rng.choice(["sin", "cos", "tan"])
            a, b = rng.choice([2, 3, -2, 4]), rng.randint(-4, 4)
            out.append(case(f"trig/{mode}/linear", f"{a}{f}(X){'+' if b >= 0 else '-'}{abs(b)}=0",
                            f"{a}*{f.upper()}(x)+{b}", "0", deg=deg))
        for _ in range(15):        # sin(kX)=v
            f = rng.choice(["sin", "cos", "tan"])
            k = rng.choice([2, 3, 2, Fraction(1, 2)])
            v = rng.choice([".5", "0", "-.5", "1", ".2"])
            arg, pa = ("X/2", "x/2") if k == Fraction(1, 2) else (f"{k}X", f"{k}*x")
            out.append(case(f"trig/{mode}/multiple", f"{f}({arg})={v}", f"{f.upper()}({pa})", v,
                            deg=deg))
        for _ in range(10):        # a trig number as a coefficient
            f = rng.choice(["sin", "cos"])
            ang = rng.choice([30, 60, 45, 90, 2])
            c = rng.choice([4, -4, 3, 10])
            out.append(case(f"trig/{mode}/constant", f"{f}({ang})X={c}", f"{f.upper()}({ang})*x",
                            str(c), deg=deg))
        for t, L, R in [("sin(X)=.5", "SIN(x)", ".5"), ("cos(X)=-1", "COS(x)", "-1"),
                        ("tan(X)=1", "TAN(x)", "1"), ("2sin(X)-1=0", "2*SIN(x)-1", "0"),
                        ("sin(2X)=.5", "SIN(2*x)", ".5"), ("sin(X)=2", "SIN(x)", "2"),
                        ("sin(30)X=4", "SIN(30)*x", "4"), ("tan(X)=0", "TAN(x)", "0"),
                        ("sin(X)^2=1/4", "SIN(x)**2", ".25"), ("cos(X)=1", "COS(x)", "1"),
                        ("sin(X)+cos(X)=1", "SIN(x)+COS(x)", "1"),
                        ("2cos(X)^2-1=0", "2*COS(x)**2-1", "0"), ("tan(X)=-1", "TAN(x)", "-1"),
                        ("sin(X)=cos(X)", "SIN(x)", "COS(x)")]:
            out.append(case(f"trig/{mode}/special", t, L, R, deg=deg))
    return out


def gen_mixed(rng):
    out = []
    for _ in range(20):            # b^X = aX+c
        b = rng.choice([2, 3, "e"])
        a, c = rng.randint(-3, 4), rng.randint(-3, 6)
        pw, pp = ("e^(X)", "EXP(x)") if b == "e" else (f"{b}^X", f"PW({b},x)")
        l1, p1 = lin(a, c) if a else (T(c), P(c))
        out.append(case("mix/exp-line", f"{pw}={l1}", pp, p1))
    for _ in range(12):            # cos(X)=aX
        a = rng.choice([1, 2, -1, .5, Fraction(1, 3)])
        f = rng.choice(["cos", "sin"])
        out.append(case("mix/trig-line", f"{f}(X)={T(a)}X" if a != 1 else f"{f}(X)=X",
                        f"{f.upper()}(x)", f"{P(a)}*x"))
    for _ in range(8):            # e^(X²)=X+c
        c = rng.randint(1, 9)
        out.append(case("mix/fast", f"e^(X^2)=X+{c}", "EXP(x**2)", f"x+{c}"))
    for _ in range(12):            # X+ln(X)=c
        c = rng.randint(-3, 8)
        f = rng.choice(["ln", "log"])
        out.append(case("mix/x-log", f"X+{f}(X)={T(c)}", f"x+{'LN' if f == 'ln' else 'LG'}(x)", P(c)))
    for _ in range(12):            # abs(X)+c=X²
        c = rng.randint(-2, 6)
        out.append(case("mix/abs-square", f"abs(X){'+' if c >= 0 else '-'}{abs(c)}=X^2",
                        f"ABS(x)+{c}", "x**2"))
    for _ in range(8):            # e^(X³)=c (the cube: logs then a cubic)
        c = rng.choice([2, 5, 10, Fraction(1, 2)])
        out.append(case("mix/e-cube", f"e^(X^3)={T(c)}", "EXP(x**3)", P(c)))
    for _ in range(10):            # oscillation
        a = rng.choice([1, 2, 3, Fraction(1, 2)])
        out.append(case("mix/oscillate", f"Xsin(X)={T(a)}", "x*SIN(x)", P(a)))
    for t, L, R in [("2^X=X+3", "PW(2,x)", "x+3"), ("cos(X)=X", "COS(x)", "x"),
                    ("e^(X^2)=X+5", "EXP(x**2)", "x+5"), ("X+ln(X)=1", "x+LN(x)", "1"),
                    ("abs(X)+1=X^2", "ABS(x)+1", "x**2"), ("e^(X^3)=2", "EXP(x**3)", "2"),
                    ("tan(X)=X", "TAN(x)", "x"), ("1/X=X", "DIV(1,x)", "x"),
                    ("2^X+2^(X+1)=12", "PW(2,x)+PW(2,x+1)", "12"), ("1/2^X=4", "DIV(1,PW(2,x))", "4"),
                    ("X^3=2^X", "x**3", "PW(2,x)"), ("\\xC1X)=X^40", "PW(10,x)", "x**40"),
                    ("2^X=X^60", "PW(2,x)", "x**60"), ("X+ln(X)=-10", "x+LN(x)", "-10"),
                    ("X+log(X)=-6", "x+LG(x)", "-6"), ("(√(X))^2=X", "SQ(x)**2", "x"),
                    ("√(X)√(X-1)=-√(6)", "SQ(x)*SQ(x-1)", "-np.sqrt(6)"),
                    ("e^(X)=X^2", "EXP(x)", "x**2"), ("ln(X)=X-2", "LN(x)", "x-2"),
                    ("X^X=27", "PW(x,x)", "27")]:
        out.append(case("mix/special", t, L, R))
    return out


def gen_range(rng, numeric_cases):
    """SEARCH A RANGE after a numeric answer, on some of the numeric cases."""
    out = []
    pool = [c for c in numeric_cases]
    for _ in range(45):
        c = dict(rng.choice(pool))
        if c["deg"]:
            a = rng.choice([-360, -180, 0, 90, 360])
            b = a + rng.choice([90, 180, 360, 720])
        else:
            a = rng.choice([-50, -20, -10, -5, -2, 0, 1, 3, 100])
            b = a + rng.choice([2, 5, 10, 30, 60, 200])
        if rng.random() < 0.2:
            a, b = b, a
        c["fam"] = "range/" + c["fam"]
        c["range"] = (a, b)
        out.append(c)
    return out


def all_cases():
    rng = random.Random(SEED)
    cases = (gen_abs(rng) + gen_radical(rng) + gen_rational(rng) + gen_expo(rng) +
             gen_log(rng) + gen_trig(rng) + gen_mixed(rng))
    numeric_like = [c for c in cases if c["fam"].startswith(("trig", "mix", "abs/two", "rad/numeric"))]
    cases += gen_range(rng, numeric_like)
    # the calculator left in Degree mode must not change anything without trig
    plain = [c for c in cases if not c["deg"] and not c["fam"].startswith(("trig", "range"))
             and not re.search(r"sin\(|cos\(|tan\(", c["typed"])]
    for c in rng.sample(plain, 70):
        c = dict(c, deg=True, fam="degree-mode/" + c["fam"].split("/")[0])
        cases.append(c)
    for i, c in enumerate(cases):
        c["id"] = i
    return cases


# ============================================================== judging

def close(a, b, rel=1e-6):
    return abs(a - b) <= rel * max(1.0, abs(b))


def window(c):
    """Where the numeric search is documented to look: one turn in Degree
    when the letter is inside a sin( cos( tan( (sin(30)X=4 and sin⁻¹(X)=30
    are searched over ⁻99..99, like everything else)."""
    if c["deg"]:
        t = c["typed"]
        for m in re.finditer(r"(?<!⁻¹)(sin|cos|tan)\(", t):
            depth, j = 1, m.end()
            while depth and j < len(t):
                depth += (t[j] == "(") - (t[j] == ")")
                j += 1
            if re.search(r"[A-Zθ]", t[m.end():j]):
                return (0.0, 360.0)
    return (-99.0, 99.0)


def judge_result(c, res, orc, problems, where):
    """Check one results screen (`where` says which: the first, or a range)."""
    p = res["parsed"]
    ans = p["answers"]
    numeric = p["searched"] is not None
    roots = orc.roots
    valid = [a for a in ans if not (a["flags"] & {"fails", "bottom", "notreal"})]
    rejected = [a for a in ans if a["flags"] & {"fails", "bottom", "notreal"}]
    real_valid = [a for a in valid if abs(a["v"].imag) <= 1e-9 * (1 + abs(a["v"]))]
    cplx_valid = [a for a in valid if abs(a["v"].imag) > 1e-9 * (1 + abs(a["v"]))]
    lo, hi = (-math.inf, math.inf)
    if numeric:
        lo, hi = p["searched"]
        zl = res.get("zl")
        if zl and (zl[2] or zl[3]):
            alo, ahi = zl[0], zl[1]
            if alo > lo + 1e-3 * max(1, abs(lo)) or ahi < hi - 1e-3 * max(1, abs(hi)):
                problems.append(("RANGE", f"{where}: says SEARCHED {lo:g} TO {hi:g} but the search "
                                          f"stopped at {alo:.6g} .. {ahi:.6g} (values too big)"))
                lo, hi = max(lo, alo), min(hi, ahi)
        if where == "first":
            wlo, whi = window(c)
            # (narrower is fine: the first sweep, -10..10, may have had to stop
            # for a fast grower, and the second then stops there too)
            slo, shi = p["searched"]
            inner = (0.0, 360.0) if wlo == 0 else (-9.0, 9.0)
            if not (zl and (zl[2] or zl[3])) and (slo < wlo - 1e-9 or shi > whi + 1e-9 or
                                                 slo > inner[0] + 1e-9 or shi < inner[1] - 1e-9):
                problems.append(("RANGE", f"{where}: searched {p['searched']} instead of the "
                                          f"documented {wlo:g}..{whi:g}"))
    if orc.identity:
        # true on a whole stretch: "every X works" fits only if it is true
        # everywhere; "every X where both sides have a value" if it is true
        # wherever it is defined; "true for every X with C≥0" if it is
        # true exactly where C≥0
        xs = np.linspace(-50.0123, 50.0456, 2001)
        with np.errstate(all="ignore"):
            F = orc.F(xs)
            sc = 1 + np.abs(orc.L(xs)) + np.abs(orc.R(xs))
        holds = np.isfinite(F) & (np.abs(F) <= 1e-9 * sc)
        if p["truewhen"]:
            ok = True
            for cond in p["truewhen"]:
                pf = gpoly(cond[:-2]) if cond.endswith("≥0") else None
                if pf is None:
                    ok = False
                    continue
                cv = np.array([pf[0](float(x)) for x in xs]) >= 0
                ok = ok and np.mean(cv == holds) > 0.99
            if not ok:
                problems.append(("WRONG", f"{where}: says TRUE FOR EVERY X WITH {p['truewhen']}, "
                                          f"but that isn't where it holds"))
            return
        if p["every_defined"]:
            if np.mean(holds[np.isfinite(F)]) < 0.99:
                problems.append(("WRONG", f"{where}: says every X where both sides have a value "
                                          f"works, but it doesn't"))
            return
        if p["every"] and not orc.identity["all"]:
            problems.append(("WRONG", f"{where}: says EVERY X WORKS, but the equation only holds "
                                      f"for part of the real line ({orc.identity['frac']:.0%} of it)"))
        elif not p["every"]:
            problems.append(("WRONG", f"{where}: the equation holds for a whole stretch of X "
                                      f"({orc.identity['frac']:.0%} of -1000..1000) but it shows "
                                      f"{len(ans)} separate answers"))
        return
    if p["every"]:
        problems.append(("WRONG", f"{where}: says EVERY X WORKS (in case {p['every']}), but the real "
                                  f"answers are {fmt_roots(roots)}"))
    # every value shown as an answer must be a real solution
    for a in real_valid:
        r = a["v"].real
        if any(close(r, t, 1e-5 if "double" in a["flags"] else 1e-6) for t in roots):
            continue
        if abs(r) > 1000 and orc.is_root(r, 1e-6):
            continue
        v, s = orc.f1(r)
        why = "undefined there" if not math.isfinite(v) else f"sides differ by {v:.3g}"
        problems.append(("WRONG", f"{where}: shows {a['line']!r} as an answer, but it isn't one "
                                  f"({why}); real answers: {fmt_roots(roots)}"))
    if cplx_valid and c["fam"].startswith(("abs", "rad")):
        problems.append(("COMPLEX", f"{where}: shows complex {[a['line'] for a in cplx_valid]} as "
                                    f"answers to an abs/radical equation (real answers: "
                                    f"{fmt_roots(roots)})"))
    # every real solution (in range) must be shown as an answer
    missed, inrange = [], 0
    for t in roots:
        if numeric and not (lo - 1e-7 <= t <= hi + 1e-7):
            continue
        inrange += 1
        if any(close(a["v"].real, t, 1e-5) and abs(a["v"].imag) < 1e-6 for a in real_valid):
            continue
        rej = [a["line"] for a in rejected if close(a["v"].real, t, 1e-5)]
        if rej:
            problems.append(("WRONG", f"{where}: X={t:.10g} is a real answer but is shown as {rej[0]!r}"))
        else:
            missed.append(t)
    if missed:
        problems.append(("MISSED", f"{where}: {len(missed)} of the {inrange} real answers"
                                   f"{' in the range searched' if numeric else ''} not shown: "
                                   f"{fmt_roots(missed)} (shown: {len(ans)} answers"
                                   f"{', ' + ', '.join(a['line'] for a in ans[:4]) if ans else ''})"))
    if p["never"] and 0 in p["never"] and roots:
        problems.append(("WRONG", f"{where}: says NO ANSWER but X={fmt_roots(roots)}"))
    if p["powernever"] and roots:
        problems.append(("WRONG", f"{where}: says a power is never 0 or less, but X={fmt_roots(roots)}"))
    if p["nofound"] and numeric and any(lo <= t <= hi for t in roots):
        pass  # reported as MISSED above
    if p["unparsed"]:
        problems.append(("SCREEN", f"{where}: lines the harness can't read: {p['unparsed'][:3]}"))


def fmt_roots(roots):
    if not roots:
        return "none"
    s = ", ".join(f"{r:.8g}" for r in roots[:8])
    return s + (f", ... ({len(roots)} in all)" if len(roots) > 8 else "")


def judge_steps(c, p, orc, problems):
    roots = orc.roots if not orc.identity else []
    # GET ... ALONE:
    if "alone" in c:
        term, v = c["alone"]
        if not p["alone"]:
            problems.append(("STEP", f"no 'GET {term} ALONE:' step"))
        else:
            if p["alone"][0] != f"GET {term} ALONE:":
                problems.append(("STEP", f"alone step is {p['alone'][0]!r}, expected 'GET {term} ALONE:'"))
            elif len(p["alone"]) > 1:
                ln = p["alone"][1]
                got = gval(ln[len(term) + 1:]) if ln.startswith(term + "=") else None
                if got is None or not close(got.real, v, 1e-8):
                    problems.append(("STEP", f"'{ln}' should be {term}={v:.10g}"))
    for want in c.get("cases", []):
        if p["cases"] and want not in p["cases"]:
            problems.append(("STEP", f"case line {want!r} not shown (shown: {p['cases']})"))
    for want in c.get("square", []):
        if len(p["square"]) >= 2 and p["square"][1] != want and not p["alone"]:
            problems.append(("STEP", f"squared line is {p['square'][1]!r}, expected {want!r}"))
    # the log line: no log of a number that is 0 or less
    for ln in p["logs"]:
        m = re.fullmatch(r".*=(?:LOG(\S+?)|LN)\((.*)\)", ln)
        if m and "×" not in ln:
            v = gval(m.group(2))
            if v is not None and abs(v.imag) < 1e-12 and v.real <= 0:
                problems.append(("STEP", f"'{ln}': takes the log of {v.real:g}, which has no log "
                                         f"(should say there is no answer)"))
    # the polynomial line must be 0 at every real answer
    for cs, poly in p["polys"]:
        pf = gpoly(poly)
        if pf is None:
            problems.append(("SCREEN", f"can't read the polynomial line {poly!r}=0"))
            continue
        f, sc = pf
        for r in roots:
            if c["fam"].startswith("abs"):
                continue      # each abs case only has its own roots
            try:
                val, s = f(r), sc(r)
            except (OverflowError, ZeroDivisionError):
                continue
            if abs(val) > 1e-6 * max(1.0, s):
                problems.append(("STEP", f"'{poly}=0' isn't true at the real answer X={r:.10g} "
                                         f"(it gives {val:.4g})"))
        # the answers listed under it should be its roots
    return


def run_case(c):
    seq, shown, problems = [], [], []
    m = fresh(c["deg"])
    t0 = time.time()
    res = {}
    try:
        drive(m, ["1", keys(c["typed"])], seq, shown, problems)
    except RuntimeError as e:
        problems.append(("CRASH", f"harness: {e}"))
    if m.wait.kind == "error":
        problems.append(("CRASH", f"{m.wait.message} at: {m.statement_text()}"))
    elif m.wait.kind != "menu" or m.wait.title != "WHAT NEXT?":
        problems.append(("CRASH", f"ended waiting for {m.wait} instead of WHAT NEXT?"))
    first = {"parsed": parse(shown), "zl": list(m.lists.get("ZL", [])) or None}
    if first["parsed"]["searched"] is None:
        first["zl"] = None
    res["first"] = first
    if (m.leaks or m.blocks) and m.wait.kind != "error":
        problems.append(("LEAK", f"leaks={m.leaks} blocks={m.blocks}"))
    numeric = first["parsed"]["searched"] is not None
    every = first["parsed"]["every"] or first["parsed"]["every_defined"]
    if m.wait.kind == "menu" and not every:      # (EVERY X WORKS can come from either)
        if numeric != (len(m.wait.options) == 4):
            problems.append(("RANGE", f"WHAT NEXT? has {len(m.wait.options)} options after "
                                      f"{'a numeric' if numeric else 'an exact'} answer"))
    screen_check(shown, problems)
    lo, hi = -1000.0, 1000.0
    if not c["deg"] and re.search(r"sin\(|cos\(|tan\(", c["typed"]):
        lo, hi = -120.0, 120.0
    try:
        orc = Oracle(c["L"], c["R"], c["deg"], lo, hi)
    except Exception as e:      # a harness bug: say so loudly
        problems.append(("HARNESS", f"oracle failed: {e!r}"))
        return {"case": c, "seq": seq, "problems": problems, "shown": shown, "res": res}
    if m.wait.kind != "error":
        judge_result(c, first, orc, problems, "first")
        judge_steps(c, first["parsed"], orc, problems)
    if "range" in c and m.wait.kind == "menu" and len(m.wait.options) == 4:
        a, b = c["range"]
        shown2 = []
        drive(m, ["2", T(a), T(b)], seq, shown2, problems)
        if m.wait.kind == "error":
            problems.append(("CRASH", f"search a range: {m.wait.message} at: {m.statement_text()}"))
        else:
            second = {"parsed": parse(shown2), "zl": list(m.lists.get("ZL", [])) or None}
            want = (min(a, b), max(a, b))
            got = second["parsed"]["searched"]
            if got is None:
                if not second["parsed"]["every"]:
                    problems.append(("RANGE", f"search a range {a}..{b}: no SEARCHED line"))
            elif abs(got[0] - want[0]) > 1e-9 or abs(got[1] - want[1]) > 1e-9:
                # fine if it says honestly where it had to stop
                zl = second["zl"]
                if not (zl and (zl[2] or zl[3]) and abs(got[0] - max(want[0], round(zl[0], 4))) < 1e-3
                        and abs(got[1] - min(want[1], round(zl[1], 4))) < 1e-3):
                    problems.append(("RANGE", f"search a range {a}..{b}: says SEARCHED {got}"))
            orc2 = Oracle(c["L"], c["R"], c["deg"], min(a, b) - 1, max(a, b) + 1)
            judge_result(c, second, orc2, problems, f"range {a}..{b}")
            screen_check(shown2, problems)
            res["second"] = second
            shown = shown + shown2
            if m.leaks or m.blocks:
                problems.append(("LEAK", f"after search a range: leaks={m.leaks} blocks={m.blocks}"))
    res["time"] = time.time() - t0
    res["roots"] = orc.roots
    res["identity"] = orc.identity
    return {"case": c, "seq": seq, "problems": problems, "shown": shown, "res": res}


def show(r, verbose):
    c = r["case"]
    mode = "Degree" if c["deg"] else "Radian"
    out = [f"#{c['id']} [{c['fam']}] {mode}  answers: {r['seq']!r}"]
    for cat, msg in r["problems"]:
        out.append(f"    {cat}: {msg}")
    if verbose or r["problems"]:
        lines = [t for pg in r["shown"] for (_, _, t) in pg if t != "THINKING..."]
        out.append("    screen: " + " | ".join(lines[:40]) + (" ..." if len(lines) > 40 else ""))
        out.append(f"    oracle: {fmt_roots(r['res'].get('roots', []))}"
                   + (f"  identity={r['res']['identity']}" if r['res'].get('identity') else ""))
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-k", default="", help="only families containing this")
    ap.add_argument("-j", type=int, default=3)
    ap.add_argument("-v", action="store_true")
    ap.add_argument("--ids", default="", help="comma-separated case ids")
    args = ap.parse_args()
    cases = [c for c in all_cases() if args.k in c["fam"]]
    if args.ids:
        keep = {int(i) for i in args.ids.split(",")}
        cases = [c for c in cases if c["id"] in keep]
    t0 = time.time()
    if args.j > 1:
        import multiprocessing as mp
        with mp.Pool(args.j) as pool:
            results = pool.map(run_case, cases, chunksize=4)
    else:
        results = [run_case(c) for c in cases]
    fails = [r for r in results if r["problems"]]
    by_cat = Counter(cat for r in results for cat, _ in r["problems"])
    by_fam = defaultdict(lambda: [0, 0])
    for r in results:
        fam = r["case"]["fam"]
        by_fam[fam][0] += 1
        by_fam[fam][1] += bool(r["problems"])
    for r in results:
        if r["problems"] or args.v:
            print(show(r, args.v))
    print()
    print(f"{len(results)} cases ({sum(1 for c in cases if c['deg'])} in Degree), "
          f"{len(results) - len(fails)} pass, {len(fails)} fail, {time.time() - t0:.0f} s")
    print("problems by kind:", dict(by_cat))
    print("family                          cases  failing")
    for fam in sorted(by_fam):
        n, f = by_fam[fam]
        print(f"  {fam:30s} {n:5d}  {f:5d}")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
