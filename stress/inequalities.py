"""Pressure test for Brainiac's INEQUALITIES (main menu 1, an inequality with < > ≤ ≥).

    python stress/inequalities.py              # everything (about 1700 cases)
    python stress/inequalities.py --jobs 2     # worker processes (default 3)
    python stress/inequalities.py --only abs   # cases whose category or text contains "abs"
    python stress/inequalities.py --case "X^2<2" [--degree]   # one case, verbose
    python stress/inequalities.py --source my/BRAINIAC.txt  # try a fixed copy of the source

Exit code 0 when every case passes.

Each case is typed on a freshly started simulated TI-84 Plus CE (the exact
tokens of BRAINIAC.8xp). The oracle is independent of the program: the typed
text is parsed with TI precedence (implicit multiplication = explicit, ^ left
to right, negation below ^), evaluated over the REAL numbers (a √ or log of a
negative is undefined, as in algebra class: it is not "real because the
imaginary parts cancelled"), its critical points come from sympy's solveset
(exact) plus a dense numeric scan with mpmath bisection, and the truth of the
inequality is decided in each region and at each critical point. Where
sympy's solve_univariate_inequality also works it is compared with the oracle
as a sanity check of the oracle itself. Trig in Degree mode is judged over
0..360 (the README: "Trig inequalities in Degree mode are answered for one
turn, 0 to 360").

Checked for each case:
  * no ERR; leaks == 0 and blocks == []; AGAIN afterwards asks EQ: again;
    the window is put back
  * ANSWER: the interval string equals the oracle's set (ends, open/closed)
  * " SO ..." plain line exactly when the answer is one interval
  * BOUNDARIES line: every finite end of the true answer is listed; every
    listed value is a real critical point (root, pole/hole, domain edge)
  * TEST A POINT line: one point per part, inside its part, YES/NO true
  * the number line, captured before the first ENTER: Text( rows 0..150,
    whole-number columns 0..264; the inequality at row 2 and the answer at
    row 136 as typed/answered; one label per boundary under its dot, in
    order, with the right value; filled dot iff the oracle includes that
    point; thick line exactly over the parts that hold
  * Text( cut off at the right edge (the simulator measures the CE's small
    font and records it in graph.clipped) -- cosmetic, reported separately;
    since 0.5 an answer over 32 characters shows ANSWER: SEE THE WORKING
"""

from __future__ import annotations

import argparse
import math
import multiprocessing
import random
import re
import signal
import sys
import time
from dataclasses import dataclass, field
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import mpmath  # noqa: E402
import numpy as np  # noqa: E402
import sympy as sp  # noqa: E402

from brainiac.build import PROGRAMS, program_bytes  # noqa: E402
from brainiac.tibasic import (GH, GW, SMALL_FONT, Machine, glyphs, keyboard_to_tokens,  # noqa: E402
                              text_fit, tokens_from_bytes)

SEED = 20261014
TOK = None
mpmath.mp.dps = 40
X = sp.Symbol("x", real=True)
OPS = "<>≤≥"


SOURCE = None       # --source: run a patched copy of BRAINIAC.txt instead


def tokens():
    global TOK
    if TOK is None:
        if SOURCE:
            import brainiac.build as build
            orig = build.source_path
            build.source_path = lambda name: Path(SOURCE) if name == "BRAINIAC" else orig(name)
        TOK = {n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}
    return TOK


# =========================================================================== parsing
# The typed text, read the way the TI-84 Plus CE reads it.

FUNCS = [("abs(", "abs"), ("√(", "sqrt"), ("sqrt(", "sqrt"), ("³√(", "cbrt"), ("log(", "log"),
         ("ln(", "ln"), ("e^(", "exp"), ("𝑒^(", "exp"), ("sin(", "sin"), ("cos(", "cos"),
         ("tan(", "tan")]


class ParseError(ValueError):
    pass


def lex(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == " ":
            i += 1
            continue
        for spelling, name in FUNCS:
            if s.startswith(spelling, i):
                out.append(("fn", name))
                i += len(spelling)
                break
        else:
            if c.isdigit() or c == ".":
                j = i
                while j < len(s) and (s[j].isdigit() or s[j] == "."):
                    j += 1
                out.append(("num", Fraction(s[i:j])))
                i = j
            elif c == "π":
                out.append(("pi",))
                i += 1
            elif c.isupper() or c == "θ":
                out.append(("var", c))
                i += 1
            elif c in "+-*/^()":
                out.append((c,))
                i += 1
            elif c == "⁻":
                out.append(("-",))
                i += 1
            elif c in OPS:
                out.append(("rel", c))
                i += 1
            else:
                raise ParseError(f"can't read {c!r} in {s!r}")
    return out


class Parser:
    def __init__(self, s):
        self.t = lex(s)
        self.i = 0

    def peek(self):
        return self.t[self.i] if self.i < len(self.t) else ("end",)


    def take(self, kind=None):
        tok = self.peek()
        if kind and tok[0] != kind:
            raise ParseError(f"expected {kind}, got {tok}")
        self.i += 1
        return tok

    def relation(self):
        """[(left, sign, right)], more than one for a chain like ⁻3<2X+1<7."""
        rels = []
        left = self.expr()
        while self.peek()[0] == "rel":
            op = self.take()[1]
            right = self.expr()
            rels.append((left, op, right))
            left = right
        if self.peek()[0] != "end" or not rels:
            raise ParseError("trailing input")
        return rels

    def expr(self):
        node = self.term()
        while self.peek()[0] in "+-":
            op = self.take()[0]
            node = ("add" if op == "+" else "sub", node, self.term())
        return node

    def starts_primary(self):
        return self.peek()[0] in ("num", "var", "(", "fn", "pi")

    def term(self):
        node = self.unary()
        while True:
            k = self.peek()[0]
            if k in "*/":
                self.take()
                node = ("mul" if k == "*" else "div", node, self.unary())
            elif self.starts_primary():          # implicit: same level as *
                node = ("mul", node, self.power())
            else:
                return node

    def unary(self):
        if self.peek()[0] == "-":
            self.take()
            return ("neg", self.unary())
        return self.power()

    def power(self):
        node = self.primary()
        while self.peek()[0] == "^":                # left to right on the TI
            self.take()
            if self.peek()[0] == "-":
                self.take()
                node = ("pow", node, ("neg", self.primary()))
            else:
                node = ("pow", node, self.primary())
        return node

    def primary(self):
        tok = self.take()
        if tok[0] == "num":
            return ("num", tok[1])
        if tok[0] == "var":
            return ("var", tok[1])
        if tok[0] == "pi":
            return ("pi",)
        if tok[0] == "(":
            node = self.expr()
            self.take(")")
            return node
        if tok[0] == "fn":
            node = self.expr()
            self.take(")")
            return ("fn", tok[1], node)
        raise ParseError(f"unexpected {tok}")


def const_int(node):
    """The exponent as a whole number, if it is a constant one."""
    if node[0] == "num" and node[1].denominator == 1:
        return int(node[1])
    if node[0] == "neg" and node[1][0] == "num" and node[1][1].denominator == 1:
        return -int(node[1][1])
    return None


def has_var(node):
    if node[0] == "pi":
        return False
    if node[0] == "var":
        return True
    return any(isinstance(c, tuple) and has_var(c) for c in node[1:])


# ---- to sympy (for exact roots) --------------------------------------------------------

def to_sympy(node, degree):
    k = node[0]
    if k == "num":
        return sp.Rational(node[1].numerator, node[1].denominator)
    if k == "var":
        return X
    if k == "pi":
        return sp.pi
    if k == "neg":
        return -to_sympy(node[1], degree)
    a = to_sympy(node[1], degree) if k != "fn" else to_sympy(node[2], degree)
    if k == "fn":
        name = node[1]
        if name in ("sin", "cos", "tan"):
            arg = a * sp.pi / 180 if degree else a
            return {"sin": sp.sin, "cos": sp.cos, "tan": sp.tan}[name](arg)
        return {"abs": sp.Abs, "sqrt": sp.sqrt, "cbrt": lambda u: sp.real_root(u, 3),
                "log": lambda u: sp.log(u, 10), "ln": sp.log, "exp": sp.exp}[name](a)
    b = to_sympy(node[2], degree)
    return {"add": a + b, "sub": a - b, "mul": a * b}.get(k) if k != "div" and k != "pow" else (
        a / b if k == "div" else a ** b)


def limiting_parts(node, degree):
    """Expressions whose zeros can start or stop the domain: what is under a
    √ or log, every bottom, the cosine under a tan."""
    out = []

    def walk(n):
        k = n[0]
        if k in ("num", "var"):
            return
        if k == "fn":
            if n[1] in ("sqrt", "log", "ln") and has_var(n[2]):
                out.append(to_sympy(n[2], degree))
            if n[1] == "tan" and has_var(n[2]):
                arg = to_sympy(n[2], degree)
                out.append(sp.cos(arg * sp.pi / 180 if degree else arg))
            walk(n[2])
            return
        if k == "div" and has_var(n[2]):
            out.append(to_sympy(n[2], degree))
        if k == "pow" and has_var(n[1]) and (const_int(n[2]) is None or const_int(n[2]) < 0):
            out.append(to_sympy(n[1], degree))
        for c in n[1:]:
            if isinstance(c, tuple):
                walk(c)

    walk(node)
    return out


# ---- numeric evaluation over the reals ------------------------------------------------

def eval_np(node, xs, degree):
    """Vectorised, NaN where the expression is not a real number."""
    k = node[0]
    if k == "num":
        return np.full_like(xs, float(node[1]))
    if k == "var":
        return xs
    if k == "pi":
        return np.full_like(xs, np.pi)
    if k == "neg":
        return -eval_np(node[1], xs, degree)
    if k == "fn":
        u = eval_np(node[2], xs, degree)
        name = node[1]
        if name == "abs":
            return np.abs(u)
        if name == "sqrt":
            return np.where(u >= 0, np.sqrt(np.abs(u)), np.nan)
        if name == "cbrt":
            return np.cbrt(u)
        if name in ("log", "ln"):
            ok = u > 0
            v = np.where(ok, u, 1.0)
            return np.where(ok, np.log10(v) if name == "log" else np.log(v), np.nan)
        if name == "exp":
            return np.exp(u)
        r = np.deg2rad(u) if degree else u
        if name == "sin":
            return np.sin(r)
        if name == "cos":
            return np.cos(r)
        c = np.cos(r)
        return np.where(np.abs(c) > 1e-13, np.sin(r) / np.where(c == 0, 1, c), np.nan)
    a = eval_np(node[1], xs, degree)
    if k == "pow":
        n = const_int(node[2])
        if n is not None:
            if n < 0:
                return np.where(a != 0, np.power(np.where(a == 0, 1, a), float(n)), np.nan)
            return np.power(a, float(n))
        e = eval_np(node[2], xs, degree)
        pos = a > 0
        return np.where(pos, np.power(np.where(pos, a, 1.0), e),
                        np.where((a == 0) & (e > 0), 0.0, np.nan))
    b = eval_np(node[2], xs, degree)
    if k == "add":
        return a + b
    if k == "sub":
        return a - b
    if k == "mul":
        return a * b
    if k == "div":
        return np.where(b != 0, a / np.where(b == 0, 1, b), np.nan)
    raise ValueError(k)


class Undefined(Exception):
    pass


TINY = mpmath.mpf(10) ** -26


def eval_mp(node, x, degree):
    """One point, 60 digits; raises Undefined where it is not a real number.
    Values within 1e-35 of zero count as zero (the points come from
    bisection to ~1e-45)."""
    k = node[0]
    if k == "num":
        return mpmath.mpf(node[1].numerator) / node[1].denominator
    if k == "var":
        return x
    if k == "pi":
        return +mpmath.pi
    if k == "neg":
        return -eval_mp(node[1], x, degree)
    if k == "fn":
        u = eval_mp(node[2], x, degree)
        name = node[1]
        if name == "abs":
            return abs(u)
        if name == "sqrt":
            if u < -TINY:
                raise Undefined
            if u <= TINY:           # √ of a rounding error in 0 is 0, not 1e-20
                return mpmath.mpf(0)
            return mpmath.sqrt(u)
        if name == "cbrt":
            return mpmath.sign(u) * mpmath.cbrt(abs(u))
        if name in ("log", "ln"):
            if u <= TINY:
                raise Undefined
            return mpmath.log10(u) if name == "log" else mpmath.ln(u)
        if name == "exp":
            return mpmath.exp(u)
        r = u * mpmath.pi / 180 if degree else u
        if name == "sin":
            return mpmath.sin(r)
        if name == "cos":
            return mpmath.cos(r)
        c = mpmath.cos(r)
        if abs(c) <= TINY:
            raise Undefined
        return mpmath.sin(r) / c
    a = eval_mp(node[1], x, degree)
    if k == "pow":
        n = const_int(node[2])
        if n is not None:
            if n < 0 and abs(a) <= TINY:
                raise Undefined
            return a ** n
        e = eval_mp(node[2], x, degree)
        if a > TINY:
            return mpmath.power(a, e)
        if abs(a) <= TINY and e > 0:
            return mpmath.mpf(0)
        raise Undefined
    b = eval_mp(node[2], x, degree)
    if k == "add":
        return a + b
    if k == "sub":
        return a - b
    if k == "mul":
        return a * b
    if k == "div":
        if abs(b) <= TINY:
            raise Undefined
        return a / b
    raise ValueError(k)


def holds(op, d):
    return {"<": d < 0, ">": d > 0, "≤": d <= 0, "≥": d >= 0}[op]


def truth_mp(case, x, at_point=True):
    """Does the inequality (every link of a chain) hold at x, over the reals?
    at_point: x is a critical point, where the two sides may be equal: a
    difference within 1e-20 of the size of the two sides just around x counts
    as equal. Elsewhere only a difference tiny compared with the two sides
    does (so e^(X)>0 holds at X=-1000)."""
    x = mpmath.mpf(x)
    for left, op, right in case.rels:
        try:
            lv = eval_mp(left, x, case.degree)
            rv = eval_mp(right, x, case.degree)
        except (Undefined, ZeroDivisionError, ValueError):
            return False
        d = lv - rv
        scale = abs(lv) + abs(rv)
        if at_point:
            h = mpmath.mpf(10) ** -6 * max(1, abs(x))
            for t in (x - h, x + h):
                try:
                    scale = max(scale, abs(eval_mp(left, t, case.degree)) + abs(eval_mp(right, t, case.degree)))
                except (Undefined, ZeroDivisionError, ValueError):
                    pass
            if abs(d) <= mpmath.mpf(10) ** -20 * scale:
                d = 0
        elif abs(d) <= mpmath.mpf(10) ** -30 * scale:
            d = 0
        if not holds(op, d):
            return False
    return True


def defined_mp(case, x):
    try:
        for left, _, right in case.rels:
            eval_mp(left, mpmath.mpf(x), case.degree)
            eval_mp(right, mpmath.mpf(x), case.degree)
        return True
    except (Undefined, ZeroDivisionError, ValueError):
        return False


# =========================================================================== cases

@dataclass
class Case:
    text: str
    cat: str
    degree: bool = False
    rels: list = None       # None: not an inequality (it must be refused)
    letter: str = "X"

    def __post_init__(self):
        try:
            self.rels = Parser(self.text).relation()
        except ParseError:
            if self.cat != "two signs or odd":
                raise
            self.rels = None
        letters = set(re.findall(r"[A-Zθ]", re.sub(r"[a-z]+\(", "", self.text)))
        self.letter = letters.pop() if letters else "X"

    def answers(self):
        return f"[{'Degree' if self.degree else 'Radian'}] 1, {self.text!r}"


def num(v):
    """A number as typed (Fraction or int)."""
    v = Fraction(v)
    if v.denominator == 1:
        return str(v.numerator)
    return f"{v.numerator}/{v.denominator}"


def poly_text(coeffs, var="X"):
    """[a_n, ..., a_0] -> 2X^3-3X+1 (no * signs, like on the calculator)."""
    n = len(coeffs) - 1
    out = ""
    for i, c in enumerate(coeffs):
        c = Fraction(c)
        p = n - i
        if c == 0:
            continue
        sign = "-" if c < 0 else "+"
        a = abs(c)
        if p == 0:
            body = num(a) if a.denominator == 1 else f"({num(a)})"
        else:
            body = "" if a == 1 else (num(a) if a.denominator == 1 else f"({num(a)})")
            body += var + (f"^{p}" if p > 1 else "")
        if not out:
            out = ("-" if c < 0 else "") + body
        else:
            out += sign + body
    return out or "0"


def expand(roots, lead=1):
    coeffs = [Fraction(lead)]
    for r in roots:
        r = Fraction(r)
        new = coeffs + [Fraction(0)]
        for i in range(len(coeffs)):
            new[i + 1] -= r * coeffs[i]
        coeffs = new
    return coeffs


def lin(a, b, var="X"):
    return poly_text([a, b], var)


def gen_cases():
    rng = random.Random(SEED)
    cases: list[Case] = []
    seen = set()

    def add(text, cat, degree=False):
        key = (text, degree)
        if key in seen:
            return
        seen.add(key)
        cases.append(Case(text, cat, degree))

    op = lambda: rng.choice(OPS)  # noqa: E731
    nz = lambda lo, hi: rng.choice([v for v in range(lo, hi + 1) if v])  # noqa: E731

    def frac():
        q = rng.choice([2, 3, 4, 5])
        p = nz(-12, 12)
        return Fraction(p, q)

    # ---- linear (both sides, negative coefficients that flip) ----
    for _ in range(230):
        f = rng.randrange(6)
        a, b, c, d = nz(-9, 9), rng.randint(-12, 12), rng.randint(-9, 9), rng.randint(-12, 12)
        if f == 0:
            add(f"{lin(a, b)}{op()}{d}", "linear")
        elif f == 1:
            if c == a:
                c = a + 1
            add(f"{lin(a, b)}{op()}{lin(c, d) if c else d}", "linear both sides")
        elif f == 2:
            add(f"{a}(X{'+' if b >= 0 else '-'}{abs(b)}){op()}{lin(c, d) if c else d}", "linear brackets")
        elif f == 3:
            add(f"{d}-{abs(a)}X{op()}{b}", "linear flip")
        elif f == 4:
            q = rng.choice([2, 3, 4, 5, 10])
            add(f"X/{q}{'+' if b >= 0 else '-'}{abs(b)}{op()}{d}", "linear fraction")
        else:
            add(f".{rng.randint(1, 9)}X{'+' if b >= 0 else '-'}{abs(b)}{op()}{d}", "linear decimal")
    # 3X<2 -> a fraction boundary
    for _ in range(20):
        add(f"{nz(-9, 9)}X{op()}{nz(-9, 9)}", "linear fraction boundary")

    # ---- quadratics ----
    for _ in range(270):
        f = rng.randrange(7)
        lead = nz(-4, 4)
        if f == 0:
            r1, r2 = rng.randint(-9, 9), rng.randint(-9, 9)
            add(f"{poly_text(expand([r1, r2], lead))}{op()}0", "quadratic two roots")
        elif f == 1:
            r1, r2 = frac(), rng.randint(-6, 6)
            q = Fraction(r1).denominator
            add(f"{poly_text(expand([r1, r2], lead * q))}{op()}0", "quadratic fraction root")
        elif f == 2:
            r = rng.randint(-8, 8)
            add(f"{poly_text(expand([r, r], lead))}{op()}0", "quadratic double root")
        elif f == 3:
            h, kk = rng.randint(-5, 5), rng.randint(1, 9)
            co = expand([h, h], 1)
            co[-1] += kk
            co = [c * lead for c in co]
            add(f"{poly_text(co)}{op()}0", "quadratic no real roots")
        elif f == 4:
            b, c = rng.randint(-9, 9), rng.randint(-12, 12)
            add(f"{poly_text([lead, b, c])}{op()}0", "quadratic random")
        elif f == 5:
            r1, r2 = rng.randint(-7, 7), rng.randint(-7, 7)
            co = expand([r1, r2], lead)
            m1, m2 = rng.randint(-5, 5), rng.randint(-9, 9)
            left = [co[0], co[1] + m1, co[2] + m2]
            add(f"{poly_text(left)}{op()}{poly_text([m1, m2]) if m1 else m2}", "quadratic both sides")
        else:
            r1, r2 = rng.randint(-9, 9), rng.randint(-9, 9)
            add(f"{poly_text(expand([r1, r2], -abs(lead)))}{op()}0", "quadratic opening down")

    # ---- cubics / quartics ----
    for _ in range(170):
        f = rng.randrange(5)
        lead = nz(-3, 3)
        if f == 0:
            rs = rng.sample(range(-6, 7), 3)
            add(f"{poly_text(expand(rs, lead))}{op()}0", "cubic")
        elif f == 1:
            r, s = rng.sample(range(-5, 6), 2)
            add(f"{poly_text(expand([r, r, s], lead))}{op()}0", "cubic repeated")
        elif f == 2:
            rs = rng.sample(range(-4, 5), 4)
            add(f"{poly_text(expand(rs, lead))}{op()}0", "quartic")
        elif f == 3:
            r = rng.randint(-5, 5)
            kk = rng.randint(1, 6)
            co = expand([r], lead)
            co = [co[0], co[1]]
            full = [co[0], co[1], co[0] * kk, co[1] * kk]          # (aX-ar)(X^2+k)
            add(f"{poly_text(full)}{op()}0", "cubic complex pair")
        else:
            a, b = rng.sample([1, 4, 9, 16], 2)
            add(f"{poly_text(expand([], 1) and [1, 0, -(a + b), 0, a * b])}{op()}0", "quartic biquadratic")

    # ---- factored forms (typed as factors) ----
    def factor(r, var="X"):
        r = Fraction(r)
        if r == 0:
            return var
        if r.denominator == 1:
            return f"({var}{'-' if r > 0 else '+'}{abs(r.numerator)})"
        return f"({r.denominator}{var}{'-' if r > 0 else '+'}{abs(r.numerator)})"

    for _ in range(120):
        f = rng.randrange(5)
        if f == 0:
            rs = rng.sample(range(-6, 7), rng.choice([2, 3]))
            add("".join(factor(r) for r in rs) + f"{op()}0", "factored")
        elif f == 1:
            r, s = rng.sample(range(-5, 6), 2)
            add(f"{factor(r)}^2{factor(s)}{op()}0", "factored square")
        elif f == 2:
            r, s = frac(), rng.randint(-5, 5)
            add(f"{factor(r)}{factor(s)}{op()}0", "factored fraction")
        elif f == 3:
            r, s = rng.sample(range(-5, 6), 2)
            add(f"-{factor(r)}{factor(s)}{op()}0", "factored negative")
        else:
            r = rng.randint(-4, 4)
            add(f"{factor(r)}^3{op()}0", "factored cube")

    # ---- rational ----
    for _ in range(170):
        f = rng.randrange(8)
        a, b = rng.sample(range(-8, 9), 2)
        if f == 0:
            add(f"(X{'-' if a >= 0 else '+'}{abs(a)})/(X{'-' if b >= 0 else '+'}{abs(b)}){op()}0", "rational")
        elif f == 1:
            c = nz(-4, 4)
            add(f"(X{'-' if a >= 0 else '+'}{abs(a)})/(X{'-' if b >= 0 else '+'}{abs(b)}){op()}{c}", "rational vs number")
        elif f == 2:
            c = nz(-5, 5)
            add(f"1/X{op()}{c}", "rational 1/X")
        elif f == 3:
            add(f"(X^2-{a * a})/(X{'-' if a >= 0 else '+'}{abs(a)}){op()}0" if a else f"X^2/X{op()}0",
                "rational hole")
        elif f == 4:
            c = rng.randint(1, 5)
            add(f"X/(X^2-{c * c}){op()}0", "rational two poles")
        elif f == 5:
            add(f"(X{'+' if a >= 0 else '-'}{abs(a)})/(X{'-' if b >= 0 else '+'}{abs(b)})^2{op()}0",
                "rational square bottom")
        elif f == 6:
            c = rng.randint(1, 5)
            add(f"{c}/(X{'+' if a >= 0 else '-'}{abs(a)}){op()}1", "rational c/(x+a)")
        else:
            r, s, t = rng.sample(range(-6, 7), 3)
            add(f"{factor(r)}{factor(s)}/{factor(t)}{op()}0", "rational factored")
    for t in ["(X-1)/(X+2)≤0", "1/X>2", "1/X<X", "X-1/X>0", "(X^2-1)/(X-1)>0", "(X^2-1)/(X-1)≥0",
              "2/(X+1)≤1", "(X+3)/(X-1)≥2", "1/(X-2)<1/(X+2)", "X/(X-1)>X", "1/X^2>4",
              "1/(X^2+1)>0", "X/(X^2+1)≥0", "(X-3)/(X-3)>0", "1/X≥0", "1/X≤0"]:
        add(t, "rational fixed")

    # ---- absolute value ----
    for _ in range(150):
        f = rng.randrange(6)
        a, b, c = nz(-4, 4), rng.randint(-9, 9), rng.randint(-6, 12)
        inner = lin(a, b)
        if f == 0:
            add(f"abs({inner}){op()}{c}", "abs")
        elif f == 1:
            k, d = rng.choice(["2", "3", "1/2", ".5", "-2", "-1/2"]), rng.randint(-9, 9)
            add(f"{k}abs({inner}){'+' if d >= 0 else '-'}{abs(d)}{op()}{c}", "abs scaled")
        elif f == 2:
            add(f"abs({inner}){op()}{-rng.randint(1, 5)}", "abs vs negative")
        elif f == 3:
            add(f"abs({inner}){op()}0", "abs vs 0")
        elif f == 4:
            add(f"{c}{op()}abs({inner})", "abs on right")
        else:
            add(f"abs(X^2-{rng.randint(1, 9)}){op()}{rng.randint(1, 6)}", "abs quadratic")
    for t in ["abs(X-1)<3", "abs(2X+1)≥5", "1/2abs(X-3)-4≥6", "abs(X)<-2", "abs(X+1)≥-1", "abs(X-2)≤0",
              "abs(X-2)>0", "abs(X-1)>abs(X+2)", "abs(X)≥X", "abs(X)>X", "abs(X)-X≤0"]:
        add(t, "abs fixed")

    # ---- square roots ----
    for _ in range(130):
        f = rng.randrange(6)
        a, b, c = nz(-3, 3), rng.randint(-9, 9), rng.randint(-2, 6)
        if f == 0:
            add(f"√({lin(a, b)}){op()}{c}", "sqrt")
        elif f == 1:
            k = rng.choice(["2", "3", "-1", "1/2"])
            add(f"{k}√({lin(a, b)}){'+' if b >= 0 else '-'}{rng.randint(0, 5)}{op()}{c}", "sqrt scaled")
        elif f == 2:
            add(f"√(X{'+' if b >= 0 else '-'}{abs(b)}){op()}{lin(1, rng.randint(-5, 3))}", "sqrt vs line")
        elif f == 3:
            r = rng.randint(2, 6)
            add(f"√({r * r}-X^2){op()}{rng.randint(0, r)}", "sqrt circle")
        elif f == 4:
            r = rng.randint(1, 5)
            add(f"√(X^2-{r * r}){op()}{rng.randint(0, 5)}", "sqrt outside")
        else:
            add(f"√({lin(a, b)}){op()}{-rng.randint(1, 4)}", "sqrt vs negative")
    for t in ["√(X)<2", "√(X)>0", "√(X+5)≤X-1", "√(4-X^2)≥1", "√(X-3)>-1", "√(X)<-1", "2√(X+1)-3≥1",
              "√(X+2)>X", "√(25-X^2)>3", "√(X)≥0", "√(-X)≤2", "√(X^2)<3", "√(X)+√(X-1)<5",
              "³√(X)<2", "³√(X-1)≥-1"]:
        add(t, "sqrt fixed")

    # ---- logs ----
    for _ in range(120):
        f = rng.randrange(5)
        a, b = nz(-3, 3), rng.randint(-6, 6)
        if f == 0:
            add(f"log({lin(a, b)}){op()}{rng.randint(-1, 2)}", "log")
        elif f == 1:
            add(f"ln({lin(a, b)}){op()}{rng.randint(-1, 2)}", "ln")
        elif f == 2:
            k = rng.choice(["2", "3", "-1", "1/2"])
            add(f"{k}log({lin(1, b)}){'+' if b >= 0 else '-'}{abs(b)}{op()}{rng.randint(-2, 4)}", "log scaled")
        elif f == 3:
            s = rng.randint(1, 6)
            add(f"log(X)+log(X-{s}){op()}{rng.randint(0, 2)}", "log sum")
        else:
            add(f"ln(X{'+' if b >= 0 else '-'}{abs(b)}){op()}0", "ln vs 0")
    for t in ["log(X)<2", "ln(X)≥0", "log(X)+log(X-3)≤1", "log(X)>-1", "ln(X-1)>1", "2log(X)-1≤3",
              "log(X)<0", "ln(X)<-2", "log(2X-4)≥0", "log(X)≥log(5)", "ln(X)>ln(2X-3)",
              "log(X)-log(X-3)≤1", "ln(X+2)-ln(X)≥1", "log(X+9)-log(X)>1",
              "log(X^2)>0", "ln(X^2-1)<0", "log(abs(X))<1"]:
        add(t, "log fixed")

    # ---- exponentials ----
    for _ in range(120):
        f = rng.randrange(5)
        base = rng.choice([2, 3, 5, 10])
        e = rng.randint(-2, 4)
        if f == 0:
            add(f"{base}^X{op()}{num(Fraction(base) ** e)}", "exponential")
        elif f == 1:
            k = rng.randint(2, 6)
            add(f"{k}({base}^X){op()}{num(k * Fraction(base) ** e)}", "exponential scaled")
        elif f == 2:
            a, b = nz(-3, 3), rng.randint(-3, 3)
            add(f"{base}^({lin(a, b)}){op()}{num(Fraction(base) ** e)}", "exponential linear exponent")
        elif f == 3:
            add(f"e^({lin(nz(-3, 3), rng.randint(-3, 3))}){op()}{rng.choice(['1', '2', '5', '.5'])}",
                "exponential e")
        else:
            add(f"{base}^X{op()}{rng.choice(['0', '-1', '-5'])}", "exponential vs nonpositive")
    for t in ["2^X>8", "5(2^X)≤40", "e^(X)<1", "3^(2X-1)≥27", "(1/2)^X>4", "2^X>-1", "2^X<0", "10^(X)≥.01",
              "e^(2X)-5>0", "4^X<2^(X+3)", "3(2^X)+1≥25", "2^X>1000000", ".5^X≤8"]:
        add(t, "exponential fixed")

    # ---- always true / never true ----
    for t in ["X^2+1>0", "X^2+1<0", "X^2+2X+5≤0", "X^2+2X+5≥0", "2X+1>2X", "2X+1<2X", "abs(X)≥0", "abs(X)+1<0",
              "(X-1)^2≥0", "-(X-1)^2>0", "e^(X)>0", "e^(X)<0", "√(X^2+1)>0", "X^2≥0", "X^2<0", "X^2≤0",
              "X^4+X^2+1>0", "-X^2-1≥0", "3(X+1)≥3X+3", "3(X+1)>3X+3", "X-X+1>0", "X^2-2X+1≥0",
              "X^2-2X+1<0", "X^2-2X+1>0", "abs(X-3)≥0", "abs(X-3)<0"]:
        add(t, "always or never")
    for _ in range(40):
        h, kk = rng.randint(-6, 6), rng.randint(0, 6)
        co = expand([h, h], 1)
        co[-1] += kk
        add(f"{poly_text(co)}{op()}0", "always or never random")

    # ---- irrational boundaries ----
    for _ in range(80):
        n = rng.choice([2, 3, 5, 6, 7, 8, 10, 11, 12, 13])
        f = rng.randrange(3)
        if f == 0:
            add(f"X^2{op()}{n}", "irrational")
        elif f == 1:
            add(f"{poly_text([1, rng.choice([-3, -1, 1, 3, 5]), rng.choice([-1, 1, -3])])}{op()}0", "irrational")
        else:
            add(f"{poly_text([rng.choice([2, 3, -2]), rng.randint(-5, 5), rng.choice([-1, -2, 1])])}{op()}0",
                "irrational")
    for t in ["X^2<2", "X^2-3X+1>0", "X^2+X-1≤0", "3X^2-2X-2≥0", "X^2>5", "X^3<2", "X^3-3X+1≥0"]:
        add(t, "irrational fixed")

    # ---- large and tiny boundaries ----
    for t in ["X>1000", "X<0.001", "2X-5000≥0", "X^2<1000000", "1000X>1", "X^2<0.0001", "(X-500)(X+250)≤0",
              "1/X>1000", "X/1000<3", "log(X)<5", "ln(X)<10", "2^X>1000000", "X>123456", "X<-98765",
              "X^2>1E6".replace("1E6", "1000000"), "X-0.00001>0", "100000X<3", "X^2-1001X+1000<0",
              "(X-150)(X+200)>0", "X^3>8000000", "X^2<0.000002",
              "√(X-150)<2", "√(X+120)>1", "1/(X-150)>0", "(X-200)/(X+300)<0", "1/(X+500)≤0",
              "log(X-200)<3", "ln(X+150)>0", "√(200-X)≥1"]:
        add(t, "large or tiny")
    for _ in range(60):
        big = rng.choice([150, 250, 999, 1000, 1500, 12345, 100000])
        sgn = rng.choice([1, -1])
        f = rng.randrange(4)
        if f == 0:
            add(f"X{'-' if sgn > 0 else '+'}{big}{op()}0", "large linear")
        elif f == 1:
            add(f"X^2{op()}{big * big}", "large quadratic")
        elif f == 2:
            q = rng.choice([1000, 2000, 5000, 10000])
            add(f"{q}X{op()}{rng.randint(1, 9)}", "tiny linear")
        else:
            add(f"(X{'-' if sgn > 0 else '+'}{big})(X{'+' if sgn > 0 else '-'}{rng.randint(1, 9)}){op()}0",
                "large factored")

    # ---- many boundaries ----
    for _ in range(90):
        f = rng.randrange(4)
        if f == 0:
            rs = rng.sample(range(-7, 8), 5)
            add(f"{poly_text(expand(rs, nz(-2, 2)))}{op()}0", "degree 5")
        elif f == 1:
            rs = rng.sample(range(-5, 6), 4) + [frac()]
            q = Fraction(rs[-1]).denominator
            add(f"{poly_text(expand(rs, q))}{op()}0", "degree 5 fraction")
        elif f == 2:
            rs = rng.sample(range(-6, 7), 6)
            add(f"{poly_text(expand(rs, 1))}{op()}0", "degree 6")
        else:
            a, b = rng.sample([2, 3, 5, 6, 7], 2)
            r = rng.randint(-4, 4)
            co = [Fraction(c) for c in [1, 0, -(a + b), 0, a * b]]
            co = expand([r], 1)
            full = sp.Poly(sp.expand((X**2 - a) * (X**2 - b) * (X - r)), X).all_coeffs()
            add(f"{poly_text([Fraction(int(c)) for c in full])}{op()}0", "degree 5 irrational")

    # ---- trig in Degree mode (0 to 360) ----
    for t in ["sin(X)>.5", "sin(X)<.5", "sin(X)≤0", "sin(X)≥0", "cos(X)≥.5", "cos(X)<0", "2sin(X)-1≥0",
              "sin(X)>-.5", "cos(X)>-1", "sin(X)≥1", "sin(X)>1", "cos(X)≤1", "tan(X)>1", "tan(X)≥0",
              "tan(X)<0", "sin(X)<cos(X)", "2cos(X)+1>0", "sin(2X)>0", "sin(X)≤-1", "abs(sin(X))<.5"]:
        add(t, "trig degree fixed", degree=True)
    for _ in range(80):
        fn = rng.choice(["sin", "cos", "sin", "cos", "tan"])
        k = rng.choice(["", "2", "3", "-2", "1/2"])
        c = rng.choice(["0", ".5", "-.5", "1", "-1", "1/2", "√(3)/2", "√(2)/2", ".3", "2"])
        lhs = f"{k}{fn}(X)"
        add(f"{lhs}{op()}{c}", f"trig degree {fn}", degree=True)

    # ---- other letters ----
    for _ in range(20):
        v = rng.choice("ATYBK")
        f = rng.randrange(3)
        if f == 0:
            add(f"{lin(nz(-5, 5), rng.randint(-9, 9), v)}{op()}{rng.randint(-9, 9)}", "other letter")
        elif f == 1:
            add(f"{poly_text(expand(rng.sample(range(-5, 6), 2), 1), v)}{op()}0", "other letter")
        else:
            add(f"abs({v}-{rng.randint(1, 5)})<{rng.randint(1, 5)}", "other letter")

    # ---- found while building this harness (each one stands for a class) ----
    for t in ["√(X^2-2)≥0", "√(X^2-2)≤0", "√(X^2-2)≤1", "√(3-X^2)≥0", "√(X^2-2)+1>0", "√(X^2-X-1)≥0",
              "√(2-X^2)>0", "√(X^2-5)<2", "√(X)√(X-4)<5", "√(X)/√(X-1)>0", "abs(X-1)+abs(X+1)≤2",
              "abs(X-1)+abs(X+1)>2", "X-X≤0", "3(X+1)≤3X+3", "2X+1≥2X+1", "log(X^2)≤2", "log(X)≤-4",
              "-log(X)≥4", "ln(X)<-10", "log(X)<-5", "log(X)+log(X-1)>2", "ln(X+150)>0", "log(X+200)≥0",
              "X^2≥10000000000", "X^2<1000000000000", "1/2log(X-2)-2<-1", "3log(X-5)-5≥4",
              "2log(X-4)-4<4", "-1/2abs(-3X+6)+5<5", "abs(2X-4)>0", "sin(√(X))>0", "1/X>1000",
              "2^X>0", "e^(X)≥0", "e^(-X)>0", "2^X≤0", "(X+2)^2(X+1)≥0", "X(X-4)^2≥0", "(X+1)^2X≥0",
              "X^3-8X^2+16X≥0", "abs(X^2-4)≤X+2", "X/abs(X)>0", "1/X+1/(X-1)>0",
              "(X-1)/(X+1)≥(X+1)/(X-1)", "√(X^2)≥X", "2^X>3^X", "2^X<5^X", "πX^2>X", "√(2)X^2≤X"]:
        add(t, "found")
    for t in ["cos(X)≤1", "cos(X)≥-1", "sin(X)≤1", "log(X)>sin(X)", "sin(X)+√(X)>1", "tan(X)=1".replace("=", "<")]:
        add(t, "found degree", degree=True)
    # ---- radian trig: many boundaries over -99..99; only checked for crashes and the screen ----
    for t in ["sin(X)>.5", "cos(X)≤0", "sin(X)<X/10", "tan(X)>0"]:
        add(t, "explore radian trig")

    # ---- shapes students also type ----
    for t in ["-3<2X+1<7", "0≤X-1≤5", "X<2=Y", "2X+3>7", "X>X", "X≥X", "1<X"]:
        add(t, "two signs or odd")
    return cases


# =========================================================================== oracle

class Timeout(Exception):
    pass


def _alarm(signum, frame):
    raise Timeout


def with_timeout(seconds, fn, *a):
    old = signal.signal(signal.SIGALRM, _alarm)
    signal.setitimer(signal.ITIMER_REAL, seconds)
    try:
        return fn(*a)
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, old)


def exact_zeros(expr, domain):
    """Real zeros of expr in domain, as 60-digit mpf; None if sympy can't."""
    try:
        s = with_timeout(4, sp.solveset, expr, X, domain)
    except Exception:
        return None
    if not isinstance(s, sp.FiniteSet):
        return None
    out = []
    for e in s:
        try:
            v = sp.N(e, 60)
            if abs(sp.im(v)) > 1e-40:
                continue
            out.append(mpmath.mpf(str(sp.re(v))))
        except Exception:
            return None
    return out


def grid(degree):
    if degree:
        return np.linspace(0, 360, 72001)
    parts = [np.linspace(-130, 130, 52001), np.logspace(-9, 7.3, 6000), -np.logspace(-9, 7.3, 6000)]
    return np.unique(np.concatenate(parts))


GRIDS = {}


def bisect(pred, a, b, iters=120):
    """pred(a) != pred(b): the point where it changes."""
    a, b = mpmath.mpf(a), mpmath.mpf(b)
    pa = pred(a)
    for _ in range(iters):
        m = (a + b) / 2
        if m == a or m == b:
            break
        if pred(m) == pa:
            a = m
        else:
            b = m
        if b - a < mpmath.mpf(10) ** -30 * (1 + abs(a)):
            break
    return (a + b) / 2


def snap(c):
    """A numerically found point that is a small fraction to 18 digits is that fraction."""
    if abs(c) < mpmath.mpf(10) ** -18:
        return mpmath.mpf(0)
    if abs(c) > 1e12:
        return c
    f = Fraction(float(c)).limit_denominator(720)
    v = mpmath.mpf(f.numerator) / f.denominator
    if abs(c - v) < mpmath.mpf(10) ** -18 * (1 + abs(c)):
        return v
    return c


def golden_min(fn, a, b, iters=90):
    a, b = mpmath.mpf(a), mpmath.mpf(b)
    g = (mpmath.sqrt(5) - 1) / 2
    for _ in range(iters):
        c, d = b - g * (b - a), a + g * (b - a)
        if fn(c) < fn(d):
            b = d
        else:
            a = c
    return (a + b) / 2


@dataclass
class Truth:
    pieces: list            # [(lo, lo_in, hi, hi_in)] with ±inf for no end; points as (a,True,a,True)
    crit: list              # critical points, sorted (mpf)
    notes: list = field(default_factory=list)

    def contains(self, v):
        v = mpmath.mpf(v)
        for lo, li, hi, hi_in in self.pieces:
            if (lo < v or (li and lo == v)) and (v < hi or (hi_in and v == hi)):
                return True
        return False


def oracle(case: Case) -> Truth:
    deg = case.degree
    domain = sp.Interval(0, 360) if deg else sp.S.Reals
    notes = []
    crit = []
    exact_crit = []
    crit_truth, crit_edge = [], []
    for left, op, right in case.rels:
        f = to_sympy(left, deg) - to_sympy(right, deg)
        for g in [f] + limiting_parts(left, deg) + limiting_parts(right, deg):
            z = exact_zeros(g, domain)
            if z is None:
                notes.append(f"no exact zeros for {g}")
            else:
                exact_crit += z
    # dense numeric scan: where the truth or the definedness changes, and dips
    if deg not in GRIDS:
        GRIDS[deg] = grid(deg)
    xs = GRIDS[deg]
    ok = np.ones_like(xs, dtype=bool)
    tv = np.ones_like(xs, dtype=bool)
    diffs = []
    for left, op, right in case.rels:
        with np.errstate(all="ignore"):
            lv, rv = eval_np(left, xs, deg), eval_np(right, xs, deg)
            okr = ~np.isnan(lv) & ~np.isnan(rv)
            d = lv - rv
            scale = 1 + np.abs(lv) + np.abs(rv)
            dz = np.where(np.abs(d) <= 1e-12 * scale, 0.0, d)
            tv &= okr & {"<": dz < 0, ">": dz > 0, "≤": dz <= 0, "≥": dz >= 0}[op]
        ok &= okr
        diffs.append((left, right, okr, d, scale))
    for i in np.nonzero(tv[1:] != tv[:-1])[0]:
        a, b = xs[i], xs[i + 1]
        if truth_mp(case, a, False) != truth_mp(case, b, False):
            crit_truth.append(bisect(lambda t: truth_mp(case, t, at_point=False), a, b))
    for i in np.nonzero(ok[1:] != ok[:-1])[0]:
        a, b = xs[i], xs[i + 1]
        if defined_mp(case, a) != defined_mp(case, b):
            crit_edge.append(bisect(lambda t: defined_mp(case, t), a, b))
    for left, right, okr, d, scale in diffs:
        def diff(t, left=left, right=right):
            return eval_mp(left, t, deg) - eval_mp(right, t, deg)

        # sign changes of the difference (roots and poles)
        with np.errstate(all="ignore"):
            sg = np.sign(np.where(okr, d, np.nan))
        idx = np.nonzero((sg[1:] * sg[:-1]) < 0)[0]
        for i in idx[:400]:
            a, b = xs[i], xs[i + 1]

            def sign_at(t):
                try:
                    return diff(t) > 0
                except (Undefined, ZeroDivisionError, ValueError):
                    return None
            if sign_at(a) is not None and sign_at(b) is not None and sign_at(a) != sign_at(b):
                crit.append(bisect(sign_at, a, b))
        # dips that may touch zero without crossing
        with np.errstate(all="ignore"):
            ad = np.where(okr, np.abs(d) / scale, np.inf)
        # (strictly lower on one side, so a flat run of zeros is not 10000 dips)
        dips = np.nonzero((ad[1:-1] <= ad[:-2]) & (ad[1:-1] <= ad[2:]) & (ad[1:-1] < 1e-3)
                          & ((ad[1:-1] < ad[:-2]) | (ad[1:-1] < ad[2:])))[0] + 1
        dips = [i for i in dips if not any(abs(xs[i] - float(c)) < 0.05 for c in exact_crit)]
        for i in dips[:16]:
            a, b = xs[i - 1], xs[i + 1]

            def mag(t):
                try:
                    return abs(diff(t))
                except (Undefined, ZeroDivisionError, ValueError):
                    return mpmath.inf
            crit.append(golden_min(mag, a, b))
    # exact ones first; a numeric one only where no other point is within 1e-10
    kept = []
    for c in exact_crit + [snap(c) for c in crit + crit_edge + crit_truth]:
        if deg and not 0 <= c <= 360:
            continue
        if not any(abs(c - k) <= mpmath.mpf(10) ** -10 * (1 + abs(c)) for k in kept):
            kept.append(c)
    if deg:
        kept = [c for c in kept if c not in (0, 360)] + [mpmath.mpf(0), mpmath.mpf(360)]
    crit = sorted(kept)

    # truth in each region and at each point
    seq = []        # ("region", lo, hi, truth) / ("point", c, truth)
    bounds = ([mpmath.mpf(0)] if deg else [-mpmath.inf]) + [c for c in crit if not (deg and c in (0, 360))] + \
             ([mpmath.mpf(360)] if deg else [mpmath.inf])
    for i in range(len(bounds) - 1):
        lo, hi = bounds[i], bounds[i + 1]
        if lo == -mpmath.inf and hi == mpmath.inf:
            samples = [mpmath.mpf(v) for v in (-1e6, -17.3, -0.31, 0.27, 13.1, 1e6)]
        elif lo == -mpmath.inf:
            samples = [hi - mpmath.mpf(v) * max(1, abs(hi)) for v in (1e-7, 0.5, 3, 1e3)]
        elif hi == mpmath.inf:
            samples = [lo + mpmath.mpf(v) * max(1, abs(lo)) for v in (1e-7, 0.5, 3, 1e3)]
        else:
            w = hi - lo
            samples = [lo + w * t for t in (mpmath.mpf("0.0001"), mpmath.mpf("0.31"), mpmath.mpf("0.5"),
                                            mpmath.mpf("0.77"), mpmath.mpf("0.9999"))]
        vals = [truth_mp(case, s, at_point=False) for s in samples]
        if len(set(vals)) > 1:
            notes.append(f"ORACLE UNSURE in ({mpmath.nstr(lo, 8)},{mpmath.nstr(hi, 8)}): {vals}")
        t = sum(vals) * 2 > len(vals)
        if i > 0 or deg:
            seq.append(("point", lo, truth_mp(case, lo)))
        seq.append(("region", lo, hi, t))
    if deg:
        seq.append(("point", mpmath.mpf(360), truth_mp(case, 360)))
    # into pieces: a run of true items is one piece
    pieces = []
    cur = None          # (lo, lo_in) of the run being built
    for item in seq:
        if item[0] == "point":
            _, c, t = item
            if t and cur is None:
                cur = (c, True)
            elif not t and cur is not None:
                pieces.append((cur[0], cur[1], c, False))
                cur = None
        else:
            _, lo, hi, t = item
            if t and cur is None:
                cur = (lo, False)
            elif not t and cur is not None:     # the run ended on the (true) point lo
                pieces.append((cur[0], cur[1], lo, True))
                cur = None
    if cur is not None:
        pieces.append((cur[0], cur[1], mpmath.mpf(360) if deg else mpmath.inf, bool(deg)))
    return Truth(pieces, crit, notes)


def sympy_check(case):
    """solve_univariate_inequality, where sympy manages it (to check the oracle)."""
    deg = case.degree
    if len(case.rels) != 1:
        return None
    left, op, right = case.rels[0]
    L, R = to_sympy(left, deg), to_sympy(right, deg)
    rel = {"<": sp.Lt, ">": sp.Gt, "≤": sp.Le, "≥": sp.Ge}[op](L, R)
    domain = sp.Interval(0, 360) if deg else sp.S.Reals
    try:
        s = with_timeout(4, lambda: sp.solve_univariate_inequality(rel, X, relational=False, domain=domain))
    except Exception:
        return None
    return set_to_pieces(s)


def set_to_pieces(s):
    out = []
    parts = s.args if isinstance(s, sp.Union) else (s,)
    for p in parts:
        if p is sp.S.EmptySet:
            continue
        if isinstance(p, sp.Interval):
            lo = -mpmath.inf if p.start == -sp.oo else mpmath.mpf(str(sp.N(p.start, 60)))
            hi = mpmath.inf if p.end == sp.oo else mpmath.mpf(str(sp.N(p.end, 60)))
            out.append((lo, not p.left_open, hi, not p.right_open))
        elif isinstance(p, sp.FiniteSet):
            for e in p:
                v = mpmath.mpf(str(sp.N(e, 60)))
                out.append((v, True, v, True))
        else:
            return None
    return sorted(out, key=lambda t: (t[0], t[2]))


# =========================================================================== the program

UNI = {"⁻": "-", "ᴇ": "e"}


def parse_number(s):
    s = s.strip()
    if s == "INF":
        return mpmath.inf
    if s == "-INF":
        return -mpmath.inf
    t = "".join(UNI.get(c, c) for c in s)
    if "/" in t:
        p, q = t.split("/")
        return mpmath.mpf(p) / mpmath.mpf(q)
    return mpmath.mpf(t)


class AnswerError(ValueError):
    pass


def parse_answer(s):
    if s == "NO SOLUTION":
        return []
    out = []
    for part in s.split("U"):
        if part.startswith("{") and part.endswith("}"):
            v = parse_number(part[1:-1])
            out.append((v, True, v, True))
            continue
        m = re.fullmatch(r"([(\[])([^,]+),([^,]+)([)\]])", part)
        if not m:
            raise AnswerError(f"can't read {part!r}")
        lo, hi = parse_number(m.group(2)), parse_number(m.group(3))
        out.append((lo, m.group(1) == "[", hi, m.group(4) == "]"))
    return out


def close(a, b, tol=1e-7):
    if mpmath.isinf(a) or mpmath.isinf(b):
        return a == b
    return abs(a - b) <= tol * max(1, abs(a), abs(b))


def same_pieces(p, q, tol=1e-7):
    if len(p) != len(q):
        return False
    for (a, ai, b, bi), (c, ci, d, di) in zip(p, q):
        if not (close(a, c, tol) and close(b, d, tol)):
            return False
        if not mpmath.isinf(a) and ai != ci:
            return False
        if not mpmath.isinf(b) and bi != di:
            return False
    return True


def clip360(pieces):
    """The program's answer, restricted to the one turn it promises."""
    out = []
    for lo, li, hi, hi_in in pieces:
        if hi < 0 or lo > 360 or (hi == 0 and not hi_in) or (lo == 360 and not li):
            continue
        if lo < 0:
            lo, li = mpmath.mpf(0), True
        if hi > 360:
            hi, hi_in = mpmath.mpf(360), True
        out.append((lo, li, hi, hi_in))
    return out


def show_pieces(p, digits=10):
    if not p:
        return "NO SOLUTION"
    out = []
    for a, ai, b, bi in p:
        if a == b:
            out.append("{" + mpmath.nstr(a, digits) + "}")
        else:
            out.append(("[" if ai else "(") + ("-INF" if a == -mpmath.inf else mpmath.nstr(a, digits)) + "," +
                       ("INF" if b == mpmath.inf else mpmath.nstr(b, digits)) + ("]" if bi else ")"))
    return "U".join(out)


@dataclass
class Run:
    error: str = ""
    error_at: str = ""
    picture: list = field(default_factory=list)       # (row, col, text)
    pixels: set = field(default_factory=set)
    offscreen: int = 0
    clipped: list = field(default_factory=list)    # Text( cut at the right edge
    lines: list = field(default_factory=list)          # every Output( text, in order
    outputs: list = field(default_factory=list)        # (row, col, text)
    disp: list = field(default_factory=list)
    leaks: int = 0
    blocks: list = field(default_factory=list)
    again_ok: bool = False
    window_ok: bool = True
    showed_graph: bool = False
    steps: int = 0


def typed_text(text):
    """keyboard_to_tokens turns - into (-) after < > ( but not after ≤ ≥ or
    e^( (a simulator-helper gap): there a student presses (-), so type ~."""
    return re.sub(r"(?<=[≤≥])-|(?<=e\^\()-", "~", text)


def run_program(case: Case) -> Run:
    tok = tokens()
    m = Machine(tok["BRAINIAC"])
    m.programs.update({k: v for k, v in tok.items() if k != "BRAINIAC"})
    m.radians = not case.degree
    r = Run()
    window0 = dict(m.window)
    orig_output, orig_disp, orig_point = m.screen.output, m.screen.disp, m.graph.point

    def output(row, col, text):
        r.outputs.append((row, col, text))
        orig_output(row, col, text)

    def disp(text, right=False):
        r.disp.append(text)
        orig_disp(text, right)

    def point(px, py):
        if not (0 <= px < GW and 0 <= py < GH):
            r.offscreen += 1
        orig_point(px, py)

    m.screen.output, m.screen.disp, m.graph.point = output, disp, point

    def stop():
        r.error = m.wait.message
        r.error_at = m.statement_text()
        r.leaks, r.blocks, r.steps = m.leaks, list(m.blocks), m.steps
        return r

    m.run()
    m.answer("1")
    if m.wait.kind != "input":
        return stop() if m.wait.kind == "error" else r
    m.answer(typed_text(case.text))
    if m.wait.kind == "error":
        return stop()
    if m.wait.kind == "pause" and m.showing_graph:
        r.showed_graph = True
        r.picture = [(row, col, text) for row, col, text, _ in m.graph.texts]
        r.clipped = [(row, col, text, shown) for row, col, text, shown in m.graph.clipped]
        r.pixels = set(m.graph.pixels)
        m.answer("")
        if m.wait.kind == "error":
            return stop()
        r.window_ok = dict(m.window) == window0
    # the working, page by page
    guard = 0
    while m.wait.kind == "pause" and guard < 60:
        guard += 1
        row10 = m.screen.lines()[9]
        if "MORE" in row10:
            m.answer("")
            if m.wait.kind == "error":
                return stop()
            continue
        break
    r.leaks, r.blocks = m.leaks, list(m.blocks)
    r.lines = [t for _, _, t in r.outputs if not t.startswith("ENTER:") and not t.startswith("THINKING")]
    if m.wait.kind == "pause":
        m.answer("")
    if m.wait.kind == "error":
        return stop()
    if m.wait.kind == "menu" and m.wait.title == "WHAT NEXT?" and m.wait.options and m.wait.options[0] == "AGAIN":
        m.answer("1")
        r.again_ok = m.wait.kind == "input" and m.wait.prompt == "EQ:"
        if m.wait.kind == "error":
            return stop()
    r.steps = m.steps
    r.leaks = max(r.leaks, m.leaks)
    return r


# =========================================================================== checks

def label_value_ok(label, b):
    try:
        v = parse_number(label)
    except Exception:
        return False
    return abs(v - b) <= mpmath.mpf("0.0005") + mpmath.mpf(10) ** -9 * abs(b)


def text_width(s, px=6):
    return px * len(s)


def check(case: Case, truth: Truth | None, r: Run):
    """-> list of (kind, message)."""
    bad = []
    if r.error:
        bad.append(("crash", f"{r.error} at: {r.error_at}"))
        return bad
    if r.leaks or r.blocks:
        bad.append(("leak", f"leaks={r.leaks} blocks={r.blocks}"))
    if case.cat.startswith("explore"):
        truth = None
    if case.rels is None:
        if r.showed_graph:
            bad.append(("flow", "drew a number line for something that is not an inequality"))
        return bad
    if not r.showed_graph and len(case.rels) > 1:
        return bad          # refusing ⁻3<2X+1<7 (one sign at a time) is fine; a wrong answer is not
    if not r.showed_graph:
        bad.append(("flow", f"no number line; screens: {r.disp + r.lines}"))
        return bad
    if not r.again_ok:
        bad.append(("again", "AGAIN did not ask EQ: again"))
    if not r.window_ok:
        bad.append(("window", "window not put back"))
    if r.offscreen:
        bad.append(("pixels", f"{r.offscreen} pixels drawn off the screen"))
    for row, col, text in r.picture:
        if not (0 <= row <= 150 and 0 <= col <= 264) or row != int(row) or col != int(col):
            bad.append(("text-bounds", f"Text({row},{col},{text!r})"))
    lines = r.lines
    typed = glyphs(keyboard_to_tokens(typed_text(case.text)))
    if not lines or lines[0] != typed:
        bad.append(("echo", f"first line {lines[:1]} != {typed!r}"))
    ans_lines = [ln for ln in lines if ln.startswith("ANSWER: ")]
    if len(ans_lines) != 1:
        bad.append(("format", f"ANSWER lines: {ans_lines}"))
        return bad
    answer = ans_lines[0][8:]
    try:
        prog = parse_answer(answer)
    except (AnswerError, ValueError) as e:
        bad.append(("WRONG ANSWER", f"garbled answer {answer!r} ({e}); the answer is "
                                    f"{show_pieces(truth.pieces) if truth else '?'}"))
        return bad
    # --- the answer itself
    if truth is not None:
        cmp = clip360(prog) if case.degree else prog
        if not same_pieces(cmp, truth.pieces, 1e-5):
            bad.append(("WRONG ANSWER", f"shows {answer}  but the answer is {show_pieces(truth.pieces)}"))
        elif not same_pieces(cmp, truth.pieces, 1e-8):
            bad.append(("digits", f"shows {answer}  but the answer is {show_pieces(truth.pieces, 12)}"))
        if case.degree and any(mpmath.isinf(a) or mpmath.isinf(b) or a < 0 or b > 360 for a, _, b, _ in prog):
            bad.append(("degree-range", f"{answer} goes outside 0..360 though it says FOR X FROM 0 TO 360"))
    # --- plain line
    plain = [ln for ln in lines if ln.startswith(" SO ")]
    want = None
    if len(prog) == 1 and prog[0][0] != prog[0][2]:
        lo, li, hi, hi_in = prog[0]
        parts = re.fullmatch(r"([(\[])([^,]+),([^,]+)([)\]])", answer)
        a, b = parts.group(2), parts.group(3)
        v = case.letter
        if a == "-INF" and b == "INF":
            want = f" SO EVERY {v} WORKS"
        elif a == "-INF":
            want = f" SO {v}{'≤' if hi_in else '<'}{b}"
        elif b == "INF":
            want = f" SO {v}{'≥' if li else '>'}{a}"
        else:
            want = f" SO {a}{'≤' if li else '<'}{v}{'≤' if hi_in else '<'}{b}"
    if want is None and plain:
        bad.append(("plain", f"unexpected {plain}"))
    if want is not None and plain != [want]:
        bad.append(("plain", f"{plain} != [{want!r}]"))
    # --- the degree note
    if case.degree:
        if f"(FOR {case.letter} FROM 0 TO 360)" not in lines:
            bad.append(("format", "no (FOR X FROM 0 TO 360) note"))
    # --- boundaries and test points
    if "TEST A POINT IN EACH PART:" in lines:
        i = lines.index("TEST A POINT IN EACH PART:")
        bline, tline = lines[i - 1], lines[i + 1]
        try:
            bvals = [parse_number(w) for w in bline.split(" ")]
        except Exception:
            bad.append(("format", f"boundaries {bline!r}"))
            return bad
    elif "SIGN NEVER CHANGES." in lines:
        bvals, tline, bline = [], None, ""
    else:
        bad.append(("format", "no boundaries / test point lines"))
        return bad
    if bvals != sorted(bvals):
        bad.append(("boundaries", f"not in order: {bline}"))
    if truth is not None:
        ends = sorted({e for a, _, b, _ in truth.pieces for e in (a, b) if not mpmath.isinf(e)})
        if case.degree:
            ends = [e for e in ends if 0 < e < 360]
        for e in ends:
            if not any(close(e, b, 1e-7) for b in bvals):
                bad.append(("boundaries", f"{mpmath.nstr(e, 10)} (an end of the answer) is not a boundary"))
        for b, word in zip(bvals, bline.split(" ")):
            near = [c for c in truth.crit if close(b, c, 1e-7)]
            if not near:
                bad.append(("boundaries", f"{mpmath.nstr(b, 10)} is not a root, pole or domain edge"))
            elif near[0] == 0 and b != 0:
                bad.append(("boundaries", f"{word} is shown for the boundary 0"))
            elif abs(b - near[0]) > mpmath.mpf("6e-10") * abs(near[0]):
                bad.append(("digits", f"{word} is shown for {mpmath.nstr(near[0], 12)}"))
    if tline is not None:
        items = tline.split(", ")
        if len(items) != len(bvals) + 1:
            bad.append(("test points", f"{len(items)} test points for {len(bvals)} boundaries: {tline}"))
        else:
            edges = [-mpmath.inf] + bvals + [mpmath.inf]
            for k, it in enumerate(items):
                mm = re.fullmatch(r"(\S+) (YES|NO)", it)
                if not mm:
                    bad.append(("format", f"test point {it!r}"))
                    continue
                p = parse_number(mm.group(1))
                if not (edges[k] < p < edges[k + 1]):
                    bad.append(("test points", f"{mm.group(1)} is not inside part {k + 1} "
                                f"({mpmath.nstr(edges[k], 8)},{mpmath.nstr(edges[k + 1], 8)})"))
                claim = mm.group(2) == "YES"
                if claim != truth_mp(case, p):
                    bad.append(("test points", f"'{it}' is false: at {mm.group(1)} it "
                                f"{'holds' if not claim else 'does not hold'}"))
    # --- the number line
    rows = {}
    for row, col, text in r.picture:
        rows.setdefault(row, []).append((col, text))
    if rows.get(2) != [(2, text_fit(2, typed))]:
        bad.append(("number line", f"row 2 {rows.get(2)} != {typed!r}"))
    for row, col, text, shown in r.clipped:
        bad.append(("clipped", f"Text({row},{col}) shows only {shown!r} of {text!r}"))
    # 0.5: an answer over 32 characters is not drawn on the picture
    want136 = answer if len(answer) <= 32 else "ANSWER: SEE THE WORKING"
    if rows.get(136) != [(2, want136)]:
        bad.append(("number line", f"row 136 {rows.get(136)} != {want136!r}"))
    if rows.get(150) != [(2, "ENTER FOR THE WORKING")]:
        bad.append(("number line", f"row 150 {rows.get(150)}"))
    labels = rows.get(90, [])
    n = len(bvals)
    if len(labels) != n:
        bad.append(("number line", f"{len(labels)} labels for {n} boundaries"))
    else:
        for k, ((col, text), b) in enumerate(zip(labels, bvals), 1):
            w = 20 + 224 * k / (n + 1)
            if not label_value_ok(text, b):
                bad.append(("number line", f"label {text!r} under the dot for {mpmath.nstr(b, 10)}"))
            want_col = int(max(0, min(264 - 8 * len(text), w - 4 * len(text))))
            if col != want_col:
                bad.append(("number line", f"label {text!r} at column {col}, dot at {w:.1f}"))
        texts = [t for _, t in labels]
        if len(set(texts)) != len(texts):
            bad.append(("label-same", f"two dots get the same label: {texts} for {bline}"))
        cols = [c for c, _ in labels]
        if cols != sorted(cols):
            bad.append(("label-order", f"labels out of order: {labels}"))
        for (c1, t1), (c2, t2) in zip(labels, labels[1:]):
            if c1 + sum(SMALL_FONT.get(ch, 4) for ch in t1) - 2 > c2:
                bad.append(("label-overlap", f"{t1!r} at {c1} runs into {t2!r} at {c2}"))
                break
        for k, b in enumerate(bvals, 1):
            w = 20 + 224 * k / (n + 1)
            filled = (round(w), 80) in r.pixels
            if truth is not None:
                if case.degree and not (0 <= b <= 360):
                    continue
                want = truth.contains(b)
                # a boundary the program got slightly off: judge by the nearest true end
                for a, ai, bb, bi in truth.pieces:
                    if close(a, b, 1e-7) and not mpmath.isinf(a):
                        want = ai
                    if close(bb, b, 1e-7) and not mpmath.isinf(bb):
                        want = bi
                if filled != want:
                    bad.append(("number line", f"dot at {mpmath.nstr(b, 10)} is {'filled' if filled else 'open'}"
                                f" but {mpmath.nstr(b, 10)} is {'in' if want else 'not in'} the answer"))
        # thick line over each part
        for k in range(n + 1):
            p = 22 if k == 0 else 25 + 224 * k / (n + 1)
            q = 242 if k == n else 15 + 224 * (k + 1) / (n + 1)
            if q - p < 2:
                continue
            mid = round((p + q) / 2)
            thick = (mid, 81) in r.pixels and (mid, 83) in r.pixels
            if truth is None:
                continue
            lo = bvals[k - 1] if k else None
            hi = bvals[k] if k < n else None
            if lo is None and hi is None:
                sample = mpmath.mpf("0.123")
            elif lo is None:
                sample = hi - 1
            elif hi is None:
                sample = lo + 1
            else:
                sample = (lo + hi) / 2
            if case.degree and not (0 <= sample <= 360):
                continue
            want = truth.contains(sample)
            if thick != want:
                bad.append(("number line", f"part {k + 1} (around {mpmath.nstr(sample, 6)}) is "
                            f"{'drawn' if thick else 'not drawn'} thick but "
                            f"{'does not hold' if not want else 'holds'}"))
    # --- the home screen: every Output( fits (only ENTER: lines sit on row 10)
    for row, col, text in r.outputs:
        rows_needed = (col - 1 + len(text) - 1) // 26 + 1
        if row + rows_needed - 1 > 10 or (row == 10 and not text.startswith("ENTER:")):
            bad.append(("screen", f"Output({row},{col},…{len(text)} chars) runs off the screen: {text[:40]!r}…"))
            break
    return bad


# =========================================================================== driver

def work(i):
    case = CASES[i]
    t0 = time.time()
    try:
        if case.rels is None:
            raise ValueError("not an inequality: should be refused")
        if case.cat.startswith("explore"):
            raise ValueError("explored only for crashes")
        truth = oracle(case)
    except Exception as e:      # the oracle itself failed: say so, still run the program
        truth = None
        onote = [f"oracle failed: {e!r}"]
    else:
        onote = truth.notes
    sym = None
    try:
        sym = sympy_check(case) if truth is not None and (i % 3 == 0 or SYMPY_ALL) else None
    except Exception:
        pass
    sym_disagree = ""
    if truth is not None and sym is not None and not same_pieces(sym, truth.pieces, 1e-9):
        sym_disagree = f"sympy says {show_pieces(sym)}, oracle {show_pieces(truth.pieces)}"
    t1 = time.time()
    r = run_program(case)
    t2 = time.time()
    try:
        bad = check(case, truth, r)
    except Exception as e:       # a bug in the checks must not stop the run
        import traceback
        bad = [("HARNESS ERROR", f"{e!r} {traceback.format_exc(limit=-1)}")]
    unsure = [n for n in onote if n.startswith("ORACLE UNSURE")]
    widths = {row: 1 for row, _, _, _ in r.clipped}
    return dict(i=i, bad=bad, unsure=unsure, sym=sym_disagree, widths=widths, t_oracle=t1 - t0,
                t_prog=t2 - t1, lines=r.lines, picture=r.picture, answer=next(
                    (ln[8:] for ln in r.lines if ln.startswith("ANSWER: ")), None),
                truth=show_pieces(truth.pieces) if truth else None)


CASES: list[Case] = []
SYMPY_ALL = False

# kinds that are judged as failures; the rest are reported as cosmetic notes
COSMETIC = {"label-overlap", "label-same", "degree-range", "screen", "digits", "clipped"}


def main():
    global CASES
    ap = argparse.ArgumentParser()
    ap.add_argument("--jobs", type=int, default=3)
    ap.add_argument("--only", default="")
    ap.add_argument("--case", default="")
    ap.add_argument("--degree", action="store_true")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("-v", action="store_true")
    ap.add_argument("--sympy-all", action="store_true", help="cross-check the oracle on every case")
    ap.add_argument("--source", default="", help="a patched BRAINIAC.txt to test instead of brainiac/src")
    args = ap.parse_args()
    global SYMPY_ALL, SOURCE
    SYMPY_ALL = args.sympy_all
    SOURCE = args.source
    tokens()
    if args.case:
        CASES = [Case(args.case, "one", args.degree)]
    else:
        CASES = [c for c in gen_cases() if args.only in c.cat or args.only in c.text]
    if args.limit:
        CASES = CASES[:args.limit]
    t0 = time.time()
    if args.jobs > 1 and len(CASES) > 1:
        ctx = multiprocessing.get_context("fork")
        with ctx.Pool(args.jobs) as pool:
            results = pool.map(work, range(len(CASES)), chunksize=4)
    else:
        results = [work(i) for i in range(len(CASES))]
    elapsed = time.time() - t0

    by_cat = {}
    fails = cosmetic = 0
    kinds = {}
    for res in results:
        c = CASES[res["i"]]
        real = [b for b in res["bad"] if b[0] not in COSMETIC]
        cos = [b for b in res["bad"] if b[0] in COSMETIC]
        st = by_cat.setdefault(c.cat.split(" fixed")[0], [0, 0])
        st[0] += 1
        if real:
            st[1] += 1
            fails += 1
        if cos:
            cosmetic += 1
        for k, _ in res["bad"]:
            kinds[k] = kinds.get(k, 0) + 1
    print(f"\n{len(CASES)} inequalities, {fails} failing, {cosmetic} with cosmetic notes, "
          f"{elapsed:.0f} s ({args.jobs} jobs)\n")
    print("by category (cases / failing):")
    for cat, (n, f) in sorted(by_cat.items()):
        print(f"  {cat:32s} {n:5d} {f:5d}")
    print("\nby kind of problem (cases):")
    for k, n in sorted(kinds.items(), key=lambda kv: -kv[1]):
        print(f"  {k:20s} {n}{'   (cosmetic, not counted as failing)' if k in COSMETIC else ''}")
    # Text( cut off at the right edge (the simulator measures the CE's small font)
    for row, name in ((136, "answer (row 136)"), (2, "inequality (row 2)"), (90, "labels (row 90)")):
        over = sum(1 for res in results if res["widths"].get(row))
        print(f"  Text( {name} cut off at the right edge in {over} cases")
    unsure = [res for res in results if res["unsure"]]
    if unsure:
        print(f"\noracle unsure on {len(unsure)} cases (not counted):")
        for res in unsure[:20]:
            print("  ", CASES[res["i"]].answers(), res["unsure"][:2])
    sym = [res for res in results if res["sym"]]
    if sym:
        print(f"\nsympy's solve_univariate_inequality disagrees with the oracle on {len(sym)} cases"
              " (checked by hand; see the report):")
        for res in sym[:30]:
            print("  ", CASES[res["i"]].answers(), res["sym"])
    print("\nFAILURES (answer sequence from a fresh start, then what is wrong):")
    for res in results:
        c = CASES[res["i"]]
        real = [b for b in res["bad"] if b[0] not in COSMETIC]
        if not real and not (args.v and res["bad"]):
            continue
        print(f"\n  {c.answers()}   <{c.cat}>")
        for k, msg in res["bad"]:
            print(f"      {k}: {msg}")
        if args.v or args.case:
            print("      working:", res["lines"])
    if args.case:
        res = results[0]
        print("\n  picture:", res["picture"])
        print("  lines:", res["lines"])
        print("  oracle:", res["truth"])
    tp = sum(r["t_prog"] for r in results)
    to = sum(r["t_oracle"] for r in results)
    print(f"\ntime in the simulator {tp:.0f} s, in the oracle {to:.0f} s")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
