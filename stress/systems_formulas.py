"""Pressure test: SYSTEM OF EQUATIONS (menu 3), FORMULAS (menu 4: rearrange, inverse)
and SOLVE FOR X with several letters (menu 1), on the simulated TI-84 Plus CE.

    python stress/systems_formulas.py            # everything (fixed seed), ~6 min
    python stress/systems_formulas.py -v         # also print every case
    python stress/systems_formulas.py --only sys # sys | rearr | inv | solve | deg | chain
    python stress/systems_formulas.py --pass ti  # only the calculator-arithmetic pass
    python stress/systems_formulas.py --exact-rref   # systems again with a noise-free rref(

Exit code 0 = every case passed. Each failure is printed with its exact answer
sequence from a fresh start (and the angle mode).

Two passes: "ti" redoes the simulator's + - * / and fPart( in 14-digit decimal, like
the calculator's BCD arithmetic (see set_mode below); "stock-sim:" runs the
unpatched simulator, and its failures are listed only when the "ti" pass got that
case right (they are simulator artifacts, not calculator bugs).

The oracle is independent of the program:
  * systems: the typed equations are parsed (TI precedence) into sympy and solved
    exactly with linsolve; a unique answer must match exactly (fractions), "NO
    SOLUTION" must be right, and an "infinitely many" answer must be an equivalent
    system with a correct set of free letters.
  * rearrange / solve: every displayed answer is substituted back into the original
    formula at random positive values (an answer that fails is extraneous), and every
    real solution of the original at those values must be among the answers (a
    missing branch). "Can't" messages are checked against the cleared polynomial.
  * inverse: f(finv(y)) = y and finv(f(x)) = x numerically; "no inverse" is checked
    against whether f really is one-to-one.
"""

from __future__ import annotations

import cmath
import math
import random
import re
import sys
import time
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import numpy as np  # noqa: E402
import sympy as sp  # noqa: E402

from brainiac.build import PROGRAMS, program_bytes  # noqa: E402
from brainiac.tibasic import COLS, ROWS, Machine, Screen, tokens_from_bytes  # noqa: E402

SEED = 20261014
VERBOSE = "-v" in sys.argv
ONLY = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None

TOK = {n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}
LETTERS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ") + ["θ"]
ORDER = {c: i for i, c in enumerate(LETTERS)}


# ============================================================ driving the program

class RecScreen(Screen):
    """The home screen, also logging every Output( and Disp with its page."""

    def __init__(self):
        self.page = 0
        self.log = []
        super().__init__()

    def clear(self):
        super().clear()
        self.page = getattr(self, "page", 0) + 1

    def output(self, row, col, text):
        self.log.append((self.page, "out", row, col, text))
        super().output(row, col, text)

    def disp(self, text, right=False):
        self.log.append((self.page, "disp", 0, 0, text))
        super().disp(text, right)


def calc_width(text: str) -> int:
    """Characters the text takes on the calculator (⁻¹ is one character there)."""
    return len(text) - text.count("⁻¹")


class Run:
    """One session from a fresh start: the answers, then every results page."""

    def __init__(self, answers, degree=False, machine=None):
        self.answers = list(answers)
        self.degree = degree
        self.problems = []
        if machine is None:
            m = Machine(TOK["BRAINIAC"])
            m.programs.update({k: v for k, v in TOK.items() if k != "BRAINIAC"})
            m.radians = not degree
            m.screen = RecScreen()
            m.run()
        else:
            m = machine
        self.m = m
        start = len(m.screen.log)
        for a in self.answers:
            if m.wait.kind not in ("menu", "input", "pause"):
                self.problems.append(f"stopped early ({m.wait}) before answer {a!r}")
                break
            self.check_menu()
            start = len(m.screen.log)
            m.answer(a)
        # turn the pages
        pages = 0
        while m.wait.kind == "pause" and "MORE" in m.screen.lines()[9]:
            self.check_layout()
            m.answer("")
            pages += 1
            if pages > 20:
                self.problems.append("more than 20 pages")
                break
        self.result_kind = m.wait.kind
        self.error = m.wait.message if m.wait.kind == "error" else None
        if self.error:
            self.problems.append(f"CRASH {self.error} at: {m.statement_text()}")
        self.check_layout()
        log = m.screen.log[start:]
        self.outs = [t for (_, k, _, _, t) in log if k == "out"
                     and not t.startswith("THINKING") and not t.startswith("ENTER:")]
        self.disps = [t for (_, k, _, _, t) in log if k == "disp"]
        self.lines = self.outs + self.disps
        # leave the results screen: ENTER must lead to a menu or back to a prompt
        self.after = None
        if m.wait.kind == "pause":
            m.answer("")
            self.after = m.wait
            if m.wait.kind == "error":
                self.problems.append(f"CRASH after results {m.wait.message} at: {m.statement_text()}")
            elif m.wait.kind == "menu":
                self.check_menu()
        if m.leaks:
            self.problems.append(f"leaks={m.leaks}")
        if m.blocks:
            self.problems.append(f"blocks open: {m.blocks}")

    def check_menu(self):
        w = self.m.wait
        if w.kind == "menu":
            for o in w.options:
                if len(o) > 24:
                    self.problems.append(f"menu option over 24 chars: {o!r}")
            if len(w.options) > 7:
                self.problems.append("menu with more than 7 options")

    def check_layout(self):
        """Outputs on the current page must not overlap or run off the screen."""
        scr = self.m.screen
        page = scr.page
        used = {}
        for i, (p, k, row, col, text) in enumerate(scr.log):
            if p != page:
                continue
            if k == "disp":
                if calc_width(text) > COLS:
                    self.problems.append(f"Disp text over {COLS} chars: {text!r}")
                continue
            if text.startswith("THINKING"):
                continue
            w = calc_width(text)
            cells = col - 1 + max(w, 1)
            end_row = row + (cells - 1) // COLS
            if end_row > ROWS:
                self.problems.append(f"Output runs off the screen: row {row} {text!r}")
            for c in range(w):
                pos = (row + (col - 1 + c) // COLS, (col - 1 + c) % COLS)
                if pos in used and used[pos] != i:
                    self.problems.append(f"Output overlaps another on the same page: {text!r}")
                    break
                used[pos] = i

    def seq(self):
        return ("DEGREE " if self.degree else "") + " | ".join(repr(a) for a in self.answers)


# ============================================================ TI expressions

class PErr(Exception):
    pass


def normalize_typed(text: str) -> str:
    """Keyboard spelling -> calculator glyphs (as keyboard_to_tokens does)."""
    t = text.replace("pi", "π").replace("sqrt(", "√(").replace("cbrt(", "³√(")
    out, prev = [], ""
    for ch in t:
        if ch == "-" and (not prev or prev in "(,=+-*/^~<>"):
            ch = "⁻"
        if ch != " ":
            prev = ch
        out.append(ch)
    return "".join(out)


DIGITS = "0123456789"


def tokenize(s: str):
    toks, i = [], 0
    while i < len(s):
        ch = s[i]
        if s.startswith("³√(", i):
            toks.append(("cbrt",))
            i += 3
        elif s.startswith("√(", i):
            toks.append(("sqrt",))
            i += 2
        elif s.startswith("𝑒^(", i) or s.startswith("e^(", i):
            toks.append(("exp",))
            i += 3
        elif s.startswith("⁻¹", i):
            toks.append(("post", -1))
            i += 2
        elif ch in DIGITS or ch == ".":
            j = i
            while j < len(s) and (s[j] in DIGITS or s[j] == "."):
                j += 1
            num = s[i:j]
            if j < len(s) and s[j] == "ᴇ":
                j += 1
                neg = False
                if j < len(s) and s[j] == "⁻":
                    neg, j = True, j + 1
                k = j
                while j < len(s) and s[j] in DIGITS:
                    j += 1
                e = int(s[k:j]) * (-1 if neg else 1)
                val = Fraction(num or "1") * Fraction(10) ** e
            else:
                if num.count(".") > 1 or num == ".":
                    raise PErr(f"bad number {num!r}")
                val = Fraction(num)
            toks.append(("num", val))
            i = j
        elif ch in LETTERS:
            toks.append(("var", ch))
            i += 1
        elif ch == "π":
            toks.append(("pi",))
            i += 1
        elif ch == "𝑖":
            toks.append(("imag",))
            i += 1
        elif ch == "²":
            toks.append(("post", 2))
            i += 1
        elif ch == "³":
            toks.append(("post", 3))
            i += 1
        elif ch in "+-*/^()⁻":
            toks.append((ch,))
            i += 1
        elif ch == " ":
            i += 1
        else:
            raise PErr(f"can't read {ch!r} in {s!r}")
    return toks


class TIParser:
    """TI-84 precedence: implied multiplication = * and /, left to right;
    negation below ^; ^ left-associative."""

    def __init__(self, s):
        self.t = tokenize(s)
        self.i = 0

    def peek(self):
        return self.t[self.i][0] if self.i < len(self.t) else None

    def nxt(self):
        tok = self.t[self.i]
        self.i += 1
        return tok

    def whole(self):
        node = self.add()
        if self.i != len(self.t):
            raise PErr(f"left over {self.t[self.i:]}")
        return node

    def add(self):
        node = self.mul()
        while self.peek() in ("+", "-"):
            op = self.nxt()[0]
            node = (op, node, self.mul())
        return node

    def starts_atom(self, k):
        return k in ("num", "var", "pi", "imag", "(", "sqrt", "cbrt", "exp")

    def mul(self):
        node = self.neg()
        while True:
            k = self.peek()
            if k in ("*", "/"):
                self.nxt()
                node = (k, node, self.neg())
            elif self.starts_atom(k) or k == "⁻":
                node = ("*", node, self.neg())
            else:
                return node

    def neg(self):
        if self.peek() == "⁻":
            self.nxt()
            return ("neg", self.neg())
        return self.power()

    def power(self):
        node = self.postfix()
        while self.peek() == "^":
            self.nxt()
            if self.peek() == "⁻":
                self.nxt()
                rhs = ("neg", self.postfix())
            else:
                rhs = self.postfix()
            node = ("^", node, rhs)
        return node

    def postfix(self):
        node = self.atom()
        while self.peek() == "post":
            node = ("^", node, ("num", Fraction(self.nxt()[1])))
        return node

    def atom(self):
        k = self.peek()
        if k is None:
            raise PErr("ends early")
        tok = self.nxt()
        if k == "num":
            return ("num", tok[1])
        if k == "var":
            return ("var", tok[1])
        if k == "pi":
            return ("pi",)
        if k == "imag":
            return ("imag",)
        if k in ("(", "sqrt", "cbrt", "exp"):
            inner = self.add()
            if self.peek() == ")":
                self.nxt()
            elif self.peek() is not None:
                raise PErr("missing )")
            if k == "sqrt":
                return ("sqrt", inner)
            if k == "cbrt":
                return ("cbrt", inner)
            if k == "exp":
                return ("exp", inner)
            return inner
        raise PErr(f"unexpected {tok}")


def parse(s: str, typed=False):
    return TIParser(normalize_typed(s) if typed else s).whole()


SYM = {c: sp.Symbol("theta" if c == "θ" else c) for c in LETTERS}


def to_sympy(node):
    k = node[0]
    if k == "num":
        return sp.Rational(node[1].numerator, node[1].denominator)
    if k == "var":
        return SYM[node[1]]
    if k == "pi":
        return sp.pi
    if k == "imag":
        return sp.I
    if k == "neg":
        return -to_sympy(node[1])
    if k == "sqrt":
        return sp.sqrt(to_sympy(node[1]))
    if k == "cbrt":
        return sp.cbrt(to_sympy(node[1]))
    if k == "exp":
        return sp.exp(to_sympy(node[1]))
    a, b = to_sympy(node[1]), to_sympy(node[2])
    if k == "+":
        return a + b
    if k == "-":
        return a - b
    if k == "*":
        return a * b
    if k == "/":
        return a / b
    if k == "^":
        return a ** b
    raise ValueError(node)


def ev(node, env):
    """Numeric value (complex), with the calculator's real odd roots of negatives."""
    k = node[0]
    if k == "num":
        return complex(float(node[1]))
    if k == "var":
        return env[node[1]]
    if k == "pi":
        return complex(math.pi)
    if k == "imag":
        return 1j
    if k == "neg":
        return -ev(node[1], env)
    if k == "sqrt":
        return cmath.sqrt(ev(node[1], env))
    if k == "cbrt":
        return odd_root(ev(node[1], env), 3)
    if k == "exp":
        return cmath.exp(ev(node[1], env))
    a = ev(node[1], env)
    if k == "^":
        e = node[2]
        if e[0] == "num" and e[1].denominator == 1:
            n = int(e[1])
            if n < 0:
                return 1 / (a ** (-n))
            return a ** n
        b = ev(e, env)
        if abs(b.imag) < 1e-15:
            inv = 1 / b.real if b.real else 0
            if abs(inv - round(inv)) < 1e-9 and round(inv) % 2 and abs(a.imag) < 1e-12 * max(1, abs(a)):
                return odd_root(a, int(round(inv)))
        if a == 0:
            return 0j
        return cmath.exp(b * cmath.log(a))
    b = ev(node[2], env)
    if k == "+":
        return a + b
    if k == "-":
        return a - b
    if k == "*":
        return a * b
    if k == "/":
        return a / b
    raise ValueError(node)


def odd_root(z, n):
    if abs(z.imag) <= 1e-12 * max(1.0, abs(z)):
        x = z.real
        return complex(math.copysign(abs(x) ** (1.0 / n), x))
    return cmath.exp(cmath.log(z) / n)


def letters_of(node, acc=None):
    acc = set() if acc is None else acc
    if node[0] == "var":
        acc.add(node[1])
    for sub in node[1:]:
        if isinstance(sub, tuple):
            letters_of(sub, acc)
    return acc


def has_func(node):
    """True if the node has trig/log/abs (we only ever type sqrt/cbrt) or a letter in a power."""
    if node[0] == "^":
        if letters_of(node[2]):
            return True
    if node[0] == "exp" and letters_of(node[1]):
        return True
    return any(has_func(s) for s in node[1:] if isinstance(s, tuple))


# ============================================================ result bookkeeping

class Results:
    def __init__(self):
        self.counts = {}
        self.fails = []
        self.prefix = ""

    def add(self, area, run, ok, why=""):
        area = self.prefix + area
        c = self.counts.setdefault(area, [0, 0])
        problems = list(run.problems) if run is not None else []
        if not ok:
            problems.append(why)
        if problems:
            c[1] += 1
            self.fails.append((area, run.seq() if run else "?", problems, run.lines if run else []))
        else:
            c[0] += 1
        if VERBOSE:
            print(("PASS " if not problems else "FAIL ") + area, run.seq() if run else "", run.lines if run else "")


R = Results()


def frac_text(v: Fraction) -> str:
    s = str(abs(v.numerator)) if v.denominator == 1 else f"{abs(v.numerator)}/{v.denominator}"
    return ("⁻" if v < 0 else "") + s


def num_value(text):
    """A number as the program shows it: ⁻7/3, 2.5, 1.2ᴇ⁻5."""
    return to_sympy(parse(text))


# ============================================================ systems

def coef_text(rng, c: Fraction, letter: str, first: bool) -> str:
    """c*letter typed, with its sign (a leading - becomes (-))."""
    sign = "-" if c < 0 else ("" if first else "+")
    a = abs(c)
    if a.denominator == 1:
        body = letter if a == 1 else f"{a.numerator}{letter}"
    elif 10 ** 6 % a.denominator == 0 and rng.random() < .7:
        dec = f"{float(a):.6f}".rstrip("0")
        if dec.startswith("0") and rng.random() < .6:
            dec = dec[1:]
        body = dec + letter
    else:
        style = rng.random()
        p, q = a.numerator, a.denominator
        if style < .5:
            body = f"{letter}/{q}" if p == 1 else f"{p}{letter}/{q}"
        elif style < .8:
            body = f"({p}/{q}){letter}"
        else:
            body = f"{p}/{q}{letter}"              # TI: (p/q)letter
    return sign + body


def const_text(rng, c: Fraction, first: bool) -> str:
    sign = "-" if c < 0 else ("" if first else "+")
    a = abs(c)
    if a.denominator == 1:
        body = str(a.numerator)
    elif 10 ** 6 % a.denominator == 0 and rng.random() < .7:
        body = f"{float(a):.6f}".rstrip("0")
        if body.startswith("0") and rng.random() < .5:
            body = body[1:]
    else:
        body = f"{a.numerator}/{a.denominator}"
        if not first or rng.random() < .3:
            body = f"({body})" if rng.random() < .3 else body
    return sign + body


def side_text(rng, terms, const, put_const_first=False):
    """terms: list of (coef, letter); const: Fraction or None."""
    items = [("t", c, l) for c, l in terms]
    if const is not None and const != 0:
        if put_const_first:
            items.insert(0, ("c", const, None))
        else:
            items.append(("c", const, None))
    if not items:
        return "0"
    out = []
    for i, (kind, c, l) in enumerate(items):
        out.append(coef_text(rng, c, l, i == 0) if kind == "t" else const_text(rng, c, i == 0))
    return "".join(out)


def rand_frac(rng, nonzero=True, kind=None):
    while True:
        k = rng.random() if kind is None else kind
        s = rng.choice([1, -1])
        if k < .55:
            v = Fraction(rng.choice([1, 1, 1, 2, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12])) * s
        elif k < .8:
            q = rng.choice([2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
            v = Fraction(rng.randint(1, 2 * q), q) * s
        else:
            v = Fraction(rng.choice([5, 25, 75, 125, 15, 2, 4, 6, 8, 35, 45, 12]),
                         rng.choice([10, 100, 10, 1000])) * s
        if v != 0 or not nonzero:
            return v


def rand_value(rng):
    k = rng.random()
    if k < .5:
        return Fraction(rng.randint(-12, 12))
    if k < .8:
        q = rng.choice([2, 3, 4, 5, 6, 7, 8, 9, 11, 13])
        return Fraction(rng.randint(-30, 30), q)
    return Fraction(rng.randint(-40, 40), rng.choice([10, 100, 4]))


def format_equation(rng, coefs: dict, rhs: Fraction) -> str:
    """sum(coef*letter) = rhs, typed in one of many ways a student might."""
    terms = [(c, l) for l, c in coefs.items() if c != 0]
    if rng.random() < .75:
        terms.sort(key=lambda t: ORDER[t[1]])
    else:
        rng.shuffle(terms)
    form = rng.random()
    if form < .30 or len(terms) == 0:
        return side_text(rng, terms, None) + "=" + const_text(rng, rhs, True)
    if form < .40:
        return const_text(rng, rhs, True) + "=" + side_text(rng, terms, None)
    if form < .58:
        # move some terms across, add a constant to both sides
        moved = [t for t in terms if rng.random() < .4]
        if len(moved) == len(terms):
            moved = moved[1:]
        left = [t for t in terms if t not in moved]
        right = [(-c, l) for c, l in moved]
        d = Fraction(rng.randint(-9, 9))
        dl = d if (d and rng.random() < .5) else Fraction(0)
        ltxt = side_text(rng, left, dl if dl else None)
        rtxt = side_text(rng, right, rhs + dl, put_const_first=rng.random() < .5)
        return ltxt + "=" + rtxt
    if form < .70:
        # factor out a number: f(inner)=rhs
        f = rng.choice([2, 3, 4, 5, -2, Fraction(1, 2)])
        inner = [(c / f, l) for c, l in terms]
        ftxt = "-" if f == -1 else (str(f) if f.__class__ is int else f"({f})")
        if f == -2:
            ftxt = "-2"
        return ftxt + "(" + side_text(rng, inner, None) + ")=" + const_text(rng, rhs, True)
    if form < .78:
        # divide the whole side: (inner)/f = rhs
        f = rng.choice([2, 3, 4, 5, 6, 10])
        inner = [(c * f, l) for c, l in terms]
        return "(" + side_text(rng, inner, None) + ")/" + str(f) + "=" + const_text(rng, rhs, True)
    if form < .90 and len(terms) >= 1:
        # one letter alone: L = (rhs - rest)/c
        c0, l0 = terms[0]
        rest = [(-c / c0, l) for c, l in terms[1:]]
        if c0 == 1:
            return l0 + "=" + side_text(rng, rest, rhs / c0, put_const_first=rng.random() < .3)
        return coef_text(rng, c0, l0, True) + "=" + side_text(rng, [(-c, l) for c, l in terms[1:]], rhs)
    # constant on the left with the terms: terms - rhs = 0  or  terms + k = rhs + k
    k = Fraction(rng.randint(1, 20)) * rng.choice([1, -1])
    return side_text(rng, terms, k) + "=" + const_text(rng, rhs + k, True)


def make_system(rng):
    """-> (list of typed equations, kind label)."""
    kind = rng.choices(["unique", "unique", "unique", "none", "inf", "over", "under", "random", "wide"],
                       k=1)[0]
    if kind == "wide":
        n = rng.randint(7, 9)
    else:
        n = rng.choices([1, 2, 3, 4, 5, 6], weights=[1, 4, 4, 3, 2, 2])[0]
    letters = rng.sample(LETTERS, n)
    letters.sort(key=lambda c: ORDER[c]) if rng.random() < .5 else None

    def rand_row(maxlet=6):
        while True:
            row = [rand_frac(rng) if rng.random() < .78 else Fraction(0) for _ in range(n)]
            nz = [i for i, v in enumerate(row) if v]
            if nz and len(nz) <= maxlet:
                return row

    def rank(rows):
        return sp.Matrix([[sp.Rational(x.numerator, x.denominator) for x in r] for r in rows]).rank() \
            if rows else 0

    if kind == "unique":
        m = n + (1 if rng.random() < .2 and n < 6 else 0)
        for _ in range(50):
            A = [rand_row() for _ in range(m)]
            if rank(A) == n:
                break
        else:
            return None
        x = [rand_value(rng) for _ in range(n)]
        b = [sum(a * xi for a, xi in zip(row, x)) for row in A]
    elif kind == "over":
        m = min(6, n + rng.randint(1, 3))
        A = [rand_row() for _ in range(n)]
        x = [rand_value(rng) for _ in range(n)]
        while len(A) < m:
            # a combination of earlier rows, or a fresh row through the same point
            if rng.random() < .5:
                i, j = rng.randrange(len(A)), rng.randrange(len(A))
                a, bb = rand_frac(rng), rand_frac(rng)
                row = [a * p + bb * q for p, q in zip(A[i], A[j])]
                if not any(row):
                    row = rand_row()
            else:
                row = rand_row()
            A.append(row)
        b = [sum(a * xi for a, xi in zip(row, x)) for row in A]
    elif kind == "none":
        m = rng.randint(2, 6)
        base = [rand_row() for _ in range(max(1, min(m - 1, n)))]
        A = list(base)
        while len(A) < m:
            i = rng.randrange(len(base))
            f = rand_frac(rng)
            A.append([f * p for p in base[i]])
        rng.shuffle(A)
        x = [rand_value(rng) for _ in range(n)]
        b = [sum(a * xi for a, xi in zip(row, x)) for row in A]
        b[rng.randrange(m)] += rand_frac(rng)
    elif kind == "inf":
        m = rng.randint(1, 6)
        r = rng.randint(1, max(1, min(m, n) - 1)) if min(m, n) > 1 else 1
        base = [rand_row() for _ in range(r)]
        A = list(base)
        while len(A) < m:
            coeffs = [rand_frac(rng) if rng.random() < .6 else Fraction(0) for _ in base]
            if not any(coeffs):
                coeffs[0] = Fraction(1)
            row = [sum(c * bb[j] for c, bb in zip(coeffs, base)) for j in range(n)]
            if any(row):
                A.append(row)
        rng.shuffle(A)
        x = [rand_value(rng) for _ in range(n)]
        b = [sum(a * xi for a, xi in zip(row, x)) for row in A]
    elif kind == "under":
        m = rng.randint(1, max(1, n - 1)) if n > 1 else 1
        A = [rand_row() for _ in range(m)]
        x = [rand_value(rng) for _ in range(n)]
        b = [sum(a * xi for a, xi in zip(row, x)) for row in A]
    elif kind == "wide":
        m = rng.randint(2, 6)
        A = [rand_row(maxlet=rng.randint(2, 6)) for _ in range(m)]
        b = [rand_value(rng) for _ in range(m)]
    else:
        m = rng.randint(1, 6)
        A = [rand_row() for _ in range(m)]
        b = [rand_value(rng) for _ in range(m)]
    eqs = []
    for row, bi in zip(A, b):
        coefs = {l: c for l, c in zip(letters, row)}
        eqs.append(format_equation(rng, coefs, bi))
    return eqs, kind


def system_oracle(eqs):
    """-> (letters in the system, A, b as sympy Matrix, solution set)."""
    exprs = []
    for e in eqs:
        l, r = e.split("=")
        exprs.append(sp.expand(to_sympy(parse(l, typed=True)) - to_sympy(parse(r, typed=True))))
    syms = sorted(set().union(*[x.free_symbols for x in exprs]),
                  key=lambda s: ORDER["θ" if s.name == "theta" else s.name])
    if not syms:
        return syms, None, None, all(x == 0 for x in exprs)
    A, b = sp.linear_eq_to_matrix(exprs, syms)
    sol = sp.linsolve((A, b), syms)
    return syms, A, b, sol


def lin_row(text, syms):
    """'X-2θ/3=5/3' -> coefficients over syms and the right side (sympy Rationals)."""
    l, r = text.split("=")
    e = sp.expand(to_sympy(parse(l)) - to_sympy(parse(r)))
    if not e.free_symbols <= set(syms):
        raise PErr(f"letter not in the system: {text}")
    row = [e.coeff(s) for s in syms]
    const = e.subs({s: 0 for s in syms})
    if sp.expand(e - sum(c * s for c, s in zip(row, syms)) - const) != 0:
        raise PErr(f"not linear: {text}")
    return row, -const


def name(s):
    return "θ" if s.name == "theta" else s.name


def num_rank(M, tol=1e-7):
    """Rank of a float matrix whose rows may be 10-digit approximations."""
    M = np.array(M, dtype=float)
    if M.size == 0:
        return 0
    norms = np.linalg.norm(M, axis=1)
    M = M[norms > 0] / norms[norms > 0][:, None]
    if M.size == 0:
        return 0
    s = np.linalg.svd(M, compute_uv=False)
    return int((s > tol * s[0]).sum())


def check_value_text(text, exact):
    """The program's number for an exact rational: a fraction when the bottom is
    under 1000, else decimals close enough."""
    exact = sp.Rational(exact)
    if exact.q <= 999:
        want = frac_text(Fraction(int(exact.p), int(exact.q)))
        if text != want:
            try:
                got = num_value(text)
            except PErr:
                return f"unreadable {text!r}"
            if got == exact:
                return None
            if abs(float(got) - float(exact)) <= 1e-9 * max(1, abs(float(exact))):
                return f"COSMETIC: shows {text} for {want}"
            return f"WRONG: shows {text}, should be {want}"
        return None
    try:
        got = num_value(text)
    except PErr:
        return f"unreadable {text!r}"
    if abs(float(got) - float(exact)) > 1e-8 * max(1, abs(float(exact))):
        return f"WRONG: shows {text}, should be {exact} = {float(exact)}"
    return None


def check_system(run, eqs):
    syms, A, b, sol = system_oracle(eqs)
    lines = run.lines
    if not syms:
        # every letter cancels out: true for any values (sol=True) or for none
        if any(x.startswith("ERR") for x in lines) or not lines:
            return False, "every letter cancels: should say so, not crash"
        text = " ".join(lines)
        if sol:
            return ("CANCELS" in text or "ANY" in text), "every letter cancels: any values work"
        return ("NO SOLUTION" in text or "NEVER" in text), "every letter cancels: no solution"
    if sol == sp.EmptySet:
        ok = lines == ["NO SOLUTION: THE EQUATIONS", "CONTRADICT EACH OTHER."]
        return ok, "should be NO SOLUTION"
    (point,) = list(sol)
    free = set().union(*[sp.sympify(v).free_symbols for v in point])
    if not free:
        want_letters = [name(s) for s in syms]
        if len(lines) != len(syms):
            return False, f"unique answer {dict(zip(want_letters, point))} expected"
        for s, v, ln in zip(syms, point, lines):
            if not ln.startswith(name(s) + "="):
                return False, f"line {ln!r} should be {name(s)}=..."
            msg = check_value_text(ln[len(name(s)) + 1:], v)
            if msg:
                return False, f"{name(s)}: {msg}"
        return True, ""
    # infinitely many
    if not lines or lines[0] != "INFINITELY MANY ANSWERS:":
        return False, f"should be INFINITELY MANY (solution {point})"
    eq_lines = [ln for ln in lines[1:] if "CAN BE ANY NUMBER" not in ln]
    free_lines = [ln for ln in lines[1:] if "CAN BE ANY NUMBER" in ln]
    shown_free = [ln.split(" ")[0] for ln in free_lines]
    rows = []
    for ln in eq_lines:
        try:
            rows.append(lin_row(ln, syms))
        except (PErr, ValueError) as e:
            return False, f"can't read {ln!r}: {e}"
    # the shown rows have 10-digit decimals in them: compare numerically
    aug = np.array(A.row_join(b).tolist(), dtype=float)
    r_sys = A.row_join(b).rank()
    if rows:
        S = np.array([[float(x) for x in r] + [float(c)] for r, c in rows])
        if num_rank(S) != r_sys or num_rank(np.vstack([aug, S])) != r_sys:
            return False, "the shown equations are not equivalent to the system"
    elif r_sys:
        return False, "no equations shown"
    nfree = len(syms) - A.rank()
    fset = [s for s in syms if name(s) in shown_free]
    if len(fset) != len(shown_free):
        return False, f"free letter not in the system: {shown_free}"
    if len(fset) != nfree:
        return False, f"{len(fset)} free letters shown, the system has {nfree}"
    bound = [i for i, s in enumerate(syms) if s not in fset]
    if A.extract(list(range(A.rows)), bound).rank() != A.rank():
        return False, f"free letters {shown_free} can't all be chosen freely"
    for (row, _), ln in zip(rows, eq_lines):
        lead = [s for s, c in zip(syms, row) if c != 0 and s not in fset]
        if len(lead) != 1:
            return False, f"{ln!r} isn't solved for one letter"
    return True, ""


def make_int_system(rng):
    """What a class hands out: small whole numbers, 2-4 letters."""
    n = rng.choice([2, 2, 3, 3, 4])
    letters = sorted(rng.sample(list("ABCXYZ") if rng.random() < .7 else LETTERS, n),
                     key=lambda c: ORDER[c])
    kind = rng.choice(["unique", "unique", "dependent", "parallel", "extra"])
    for _ in range(100):
        A = [[rng.randint(-9, 9) for _ in range(n)] for _ in range(n)]
        if all(any(r) for r in A) and sp.Matrix(A).rank() == n:
            break
    x = [rng.randint(-9, 9) for _ in range(n)]
    if kind == "dependent":              # one equation is a multiple of another
        A[-1] = [rng.choice([2, 3, -1, -2]) * v for v in A[0]]
    if kind == "parallel":
        A[-1] = [rng.choice([2, 3, -1, -2]) * v for v in A[0]]
    b = [sum(a * xi for a, xi in zip(row, x)) for row in A]
    if kind == "parallel":
        b[-1] += rng.choice([1, 2, -3])
    if kind == "extra":
        A.append([u + v for u, v in zip(A[0], A[1])])
        b.append(b[0] + b[1])
    eqs = [format_equation(rng, {l: Fraction(c) for l, c in zip(letters, row)}, Fraction(bi))
           for row, bi in zip(A, b)]
    return eqs, "int-" + kind


SYSTEM_LIST = []


def systems_cases(rng, count, tag=""):
    if not SYSTEM_LIST:
        for i in range(count):
            made = make_int_system(rng) if i % 4 == 0 else make_system(rng)
            if made:
                SYSTEM_LIST.append(made)
    for eqs, kind in SYSTEM_LIST:
        run = Run(["3", str(len(eqs))] + eqs)
        try:
            ok, why = check_system(run, eqs)
        except Exception as e:  # noqa: BLE001
            ok, why = False, f"oracle error {type(e).__name__}: {e}"
        R.add(f"system{tag}/" + kind, run, ok, why)


# A rref( that adds no rounding noise of its own (exact arithmetic on the 14-digit
# values it is given). The simulator's rref normalizes the pivot row first and then
# judges "zero" against the already-normalized matrix, so it can pivot on its own
# 2E-13 noise (6F+8Q=10 typed twice gives F=3, Q=-1). What the real TI-84 Plus CE's
# rref( does there is unknown, so system cases are run both ways: failures that remain
# with this exact rref( come from the program's own coefficients.
def exact_rref(m, mat):
    from brainiac import tibasic as tib
    if not isinstance(mat, tib.TIMatrix):
        raise tib.TIError("DATA TYPE")
    a = [[Fraction(f"{m.number(x):.14g}") for x in row] for row in mat.rows]
    rows, cols = mat.dims
    if cols < rows:
        raise tib.TIError("INVALID DIM")
    r = 0
    for c in range(cols):
        if r >= rows:
            break
        p = next((i for i in range(r, rows) if a[i][c] != 0), None)
        if p is None:
            continue
        a[r], a[p] = a[p], a[r]
        piv = a[r][c]
        a[r] = [x / piv for x in a[r]]
        for i in range(rows):
            if i != r and a[i][c] != 0:
                f = a[i][c]
                a[i] = [x - f * y for x, y in zip(a[i], a[r])]
        r += 1
    return tib.TIMatrix([[m.number(float(x)) for x in row] for row in a])


# hand-written ones from the brief and the tests, plus nonlinear and bad input
SYSTEM_FIXED = [
    ["X=2Y", "2(X+Y)=6"], ["X/2+Y/3=1", "X-Y=0"], ["X+Y=10", "X-Y=2"],
    ["2X+3Y-Z=1", "X-Y+2Z=3", "3X+Y+Z=6"], ["A+B=3", "A-B=1", "2A+B=5"],
    ["X+Y=1", "X+Y=2"], ["X+Y+Z=6", "X-Z=0"], ["3=X+Y", "X=Y+1"], ["5=X"], ["θ+A=2", "θ-A=0"],
    ["X+Y=Y+2", "X-3=0"], ["X+Y=Y+2", "X-2=0"], ["2X=X+X"], ["X=X+1"], ["X+Y=X+Y", "Y=Y"],
    ["X+Y=X+Y", "X=1"], ["6F=-8Q+10", "8Q+6F=10"], ["A=U/3+1", "3A=U+3"],
    [".1X+.2Y=.3", "X/7-Y/11=3"], ["X+Y+Z+W+U+V=6", "X=1", "Y=1", "Z=1", "W=1", "U=1"],
    ["A+B+C+D+E+F=21", "A-B=-1", "B-C=-1", "C-D=-1", "D-E=-1", "E-F=-1"],
    ["X/3+Y/3=1", "X+Y=3"], ["X/3+Y/7=1", "7X+3Y=21", "14X+6Y=42"],
    ["1000X+Y=1001", "X+Y=2"], ["X+1000000=1000002"], [".001X+.001Y=.002", "X-Y=0"],
    ["X/9+Y/11+Z/13=1", "X/11+Y/13+Z/9=1", "X/13+Y/9+Z/11=1"],
]
NONLINEAR = ["X^2+Y=3", "XY=2", "1/X+Y=1", "X/Y=2", "sqrt(X)+Y=4", "abs(X)+Y=1", "2^X+Y=3",
             "sin(X)+Y=1", "X²=4", "ln(X)=1", "X(X+1)=2", "e^(X)=2", "abs(X)=3", "Y=abs(X-1)",
             "X^3=8", "(X+Y)^2=4", "XYZ=1", "Y=1/(X+2)", "sqrt(X^2)=3", "X^-1=2", "log(X)+Y=2"]


def systems_fixed():
    for eqs in SYSTEM_FIXED:
        run = Run(["3", str(len(eqs))] + eqs)
        try:
            ok, why = check_system(run, eqs)
        except Exception as e:  # noqa: BLE001
            ok, why = False, f"oracle error {e}"
        R.add("system/fixed", run, ok, why)
    for degree in (False, True):
        for i, bad in enumerate(NONLINEAR):
            eqs = ["X+Y=1", bad] if i % 2 else [bad]
            run = Run(["3", "2"] + eqs, degree=degree)
            ok = any("LINEAR" in ln for ln in run.lines)
            R.add("system/nonlinear", run, ok, f"{bad!r} must be refused as not linear")
    for count in ["0", "7", "2.5", "-1", "10", "1.5", "6.5", "1/2", "-6"]:
        run = Run(["3", count])
        ok = run.lines[:2] == ["TYPE A WHOLE NUMBER FROM", "1 TO 6."] and \
            run.after is not None and run.after.kind == "input" and run.after.prompt == "HOW MANY:"
        R.add("system/badcount", run, ok, "count must be refused, then asked again")
    for eqs, msg in [(["2=3", "X=1"], "NO LETTERS"), (["X+Y=", "X=1"], "NEEDS ONE ="),
                     (["=X", "X=1"], "NEEDS ONE ="), (["X=Y=1", "X=1"], "NEEDS ONE ="),
                     (["A+B+C+D+E+F+G=1", "A=1"], "TOO MANY")]:
        run = Run(["3", "2"] + eqs[:1])
        ok = any(msg in ln for ln in run.lines)
        R.add("system/badeq", run, ok, f"expected a message with {msg!r}")


# ============================================================ rearranging

FORMULAS = [
    # geometry
    "A=BH/2", "A=LW", "P=2L+2W", "P=2(L+W)", "A=S^2", "P=4S", "C=2piR", "C=piD", "A=piR^2",
    "V=LWH", "V=S^3", "S=6E^2", "V=piR^2H", "V=piR²H/3", "V=4piR^3/3", "S=4piR^2", "A=(B+C)H/2",
    "S=2piR^2+2piRH", "A=2LW+2LH+2WH", "D=sqrt(L^2+W^2+H^2)", "C=sqrt(A^2+B^2)", "A^2+B^2=C^2",
    "M=(A+B)/2", "S=180(N-2)", "E=360/N", "L=piRT/180", "A=piR^2T/360", "V=BH/3", "A=PQ/2",
    "L=piRS", "V=piD^3/6", "S=piR^2+piRL", "A=DE/2", "S=piAB", "V=2piR^3/3", "P=2R+RT",
    # algebra
    "Y=MX+B", "Y-K=M(X-H)", "M=(Y-K)/(X-H)", "AX+BY=C", "Y=A(X-H)^2+K", "X/A+Y/B=1", "Y=KX",
    "Y=K/X", "A/B=C/D", "Y=(AX+B)/(CX+D)", "L=A+(N-1)D", "S=N(A+L)/2", "M=(A+B+C)/3",
    "Z=(X-M)/S", "X=M+ZS", "P=100A/B", "Y=AX^3", "X²=4Y²",
    # physics
    "D=RT", "F=MA", "E=MC^2", "K=MV^2/2", "U=MGH", "P=W/T", "W=FD", "V=U+AT", "S=UT+AT^2/2",
    "V^2=U^2+2AS", "S=(U+V)T/2", "P=MV", "F=KX", "F=GMN/R^2", "F=KQP/R^2", "R=V/I", "P=VI",
    "P=I^2R", "P=V^2/R", "1/R=1/A+1/B", "R=A+B+C", "T=2pisqrt(L/G)", "T=2pisqrt(M/K)", "F=1/T",
    "V=FL", "A=V^2/R", "F=MV^2/R", "P=F/A", "D=M/V", "W=MG", "Q=MCT", "1/F=1/A+1/B", "M=I/O",
    "N=C/V", "I=P/(4piR^2)", "E=HF", "V=sqrt(2GM/R)", "T^2=4pi^2R^3/(GM)", "H=V^2/(2G)",
    "E=MGH+MV^2/2", "W=MV^2/2-MU^2/2", "F=G(V+U)/(V-S)", "1/R=1/A+1/B+1/C", "E=KQ/R^2", "T=FR",
    "P=SAT^4", "Q=CV", "U=CV^2/2", "S=D/T", "V=U-GT", "Q=IT", "F=sqrt(G/L)/(2pi)", "U=-GMN/R",
    "E=PT", "C=Q/(MT)",
    # chemistry
    "PV=NRT", "P=NRT/V", "PV/T=QW/S", "M=N/V", "N=M/W", "MV=NW", "K=C+273", "F=9C/5+32",
    "C=5(F-32)/9", "P=A+B+C", "H=E+PV", "G=H-TS", "N=PV/(RT)", "D=PM/(RT)",
    "K=(F-32)5/9+273",
    # finance
    "I=PRT", "A=P+PRT", "A=P(1+RT)", "P=R-C", "T=P+PR", "S=P-PD", "C=F+VN", "A=P(1+R)^2",
    "M=PR/(1-(1+R)^-N)", "A=P(1+R)^T", "A=P(1+R/N)^(NT)",
    # the way students often type them: 1/2 in front, decimals
    "A=1/2BH", "K=1/2MV^2", "V=1/3piR^2H", "S=1/2AT^2+UT", "F=1.8C+32", "C=2.54I", "I=.05PT",
    "A=.5(B+C)H", "E=1/2CV²",
]


def cleared(eq_node):
    """The formula as the program sees it: squared if it has a square root,
    fractions cleared -> (numerator polynomial or None, symbols)."""
    lhs, rhs = eq_node
    L, Rr = to_sympy(lhs), to_sympy(rhs)
    expr = L - Rr
    num = sp.numer(sp.together(expr))
    syms = sorted(expr.free_symbols, key=lambda s: s.name)
    try:
        return sp.Poly(sp.expand(num), *syms), syms
    except sp.PolynomialError:
        pass
    sq = sp.numer(sp.together(L ** 2 - Rr ** 2))
    try:
        return sp.Poly(sp.expand(sq), *syms), syms
    except sp.PolynomialError:
        return None, syms


def powers_of(poly, sym):
    if poly is None:
        return None
    i = poly.gens.index(sym)
    return sorted({m[i] for m in poly.monoms() if m[i] > 0})


def residual(eq_node, env):
    lhs, rhs = ev(eq_node[0], env), ev(eq_node[1], env)
    return abs(lhs - rhs) / (1 + abs(lhs) + abs(rhs))


def real_solutions(eq_node, poly, sym, env):
    """Real values of sym that make the original equation true, other letters from env."""
    if poly is None:
        return None
    sub = {s: env[name(s)] for s in poly.gens if s != sym}
    i = poly.gens.index(sym)
    deg = max(m[i] for m in poly.monoms())
    coeffs = [0j] * (deg + 1)
    for mon, c in zip(poly.monoms(), poly.coeffs()):
        v = complex(c)
        for s, e in zip(poly.gens, mon):
            if s != sym:
                v *= sub[s] ** e
        coeffs[deg - mon[i]] += v
    while coeffs and abs(coeffs[0]) < 1e-14:
        coeffs.pop(0)
    if len(coeffs) <= 1:
        return []
    out = []
    for r in np.roots(coeffs):
        if abs(r.imag) > 1e-7 * max(1, abs(r)):
            continue
        e2 = dict(env)
        e2[name(sym)] = complex(r.real)
        try:
            if residual(eq_node, e2) < 1e-8:
                out.append(r.real)
        except ZeroDivisionError:
            pass
    return out


def check_answers(eq_node, letter, answer_nodes, poly, rng, positive=True):
    """Each answer must satisfy the formula; every real solution must be an answer."""
    sym = SYM[letter]
    others = sorted(letters_of(eq_node[0]) | letters_of(eq_node[1]) - {letter})
    others = [o for o in others if o != letter]
    good_samples = 0
    for trial in range(40):
        # positive values, half of the time spread over 0.02..50 (so that, say,
        # E=MGH+MV²/2 gets an E big enough for a real V)
        if trial % 2:
            env = {o: complex(math.exp(rng.uniform(math.log(.02), math.log(50)))) for o in others}
        else:
            env = {o: complex(rng.uniform(.6, 4.5)) for o in others}
        vals = []
        try:
            for a in answer_nodes:
                vals.append(ev(a, env))
        except (ZeroDivisionError, OverflowError, ValueError):
            continue
        sols = real_solutions(eq_node, poly, sym, env)
        for a_text, v in zip(answer_nodes, vals):
            if abs(v.imag) > 1e-9 * max(1, abs(v)):
                continue
            e2 = dict(env)
            e2[letter] = complex(v.real)
            try:
                res = residual(eq_node, e2)
            except ZeroDivisionError:
                continue
            if res > 1e-8:
                return False, (f"EXTRANEOUS: {letter}={v.real:.6g} at "
                               f"{ {k: round(x.real, 4) for k, x in env.items()} } fails the formula")
        if sols is None:
            good_samples += 1
            continue
        if sols:
            good_samples += 1
        for s in sols:
            if not any(abs(v - s) <= 1e-6 * max(1, abs(s)) for v in vals):
                return False, (f"MISSING: {letter}={s:.6g} solves it at "
                               f"{ {k: round(x.real, 4) for k, x in env.items()} } but isn't shown")
        if good_samples >= 4:
            break
    if not good_samples:
        return False, "could not verify (no sample had a real solution)"
    return True, ""


MSG_GONE = "THAT LETTER CANCELS OUT,"
MSG_MIXED = "THAT LETTER APPEARS WITH"
MSG_CANT = "CAN'T REARRANGE THIS ONE"
MSG_PICK = "PICK ONE LETTER THAT IS"
MSG_MANY = "TOO MANY LETTERS:"


def check_rearrange(run, formula, letter, rng, solve_mode=False):
    l, r = formula.split("=")
    eq_node = (parse(l, typed=True), parse(r, typed=True))
    letters = letters_of(eq_node[0]) | letters_of(eq_node[1])
    lines = run.lines
    if len(letters) > 6:
        return any(MSG_MANY in x for x in lines), "more than 6 letters: should say so"
    if letter not in letters:
        return any(MSG_PICK in x for x in lines), "letter not in formula: should say pick one"
    poly, syms = cleared(eq_node)
    pw = powers_of(poly, SYM[letter])
    if any(MSG_CANT in x for x in lines):
        if has_func(eq_node[0]) or has_func(eq_node[1]):
            return True, ""
        return False, ("MISLEADING: says it has trig/logs/letters in powers, but it has none "
                       f"(cleared powers of {letter}: {pw})")
    if any(MSG_GONE in x for x in lines):
        if pw == []:
            return True, ""
        return False, f"WRONG: says {letter} cancels out, but it appears with powers {pw}"
    if any(MSG_MIXED in x for x in lines):
        if pw is not None and len(pw) >= 2:
            return True, ""
        return False, f"WRONG: says two different powers, but the powers of {letter} are {pw}"
    if any(MSG_PICK in x for x in lines):
        return False, f"WRONG: says pick a letter in the formula, but {letter} is in it"
    # an answer: echo, SO, L=..., [OR L=...]
    if len(lines) < 3 or lines[1] != "SO":
        return False, f"unexpected screen {lines}"
    if lines[0] != normalize_typed(formula).replace("^2", "^2"):
        pass  # the echo is the typed text; not checked character for character
    answers = []
    for ln in lines[2:]:
        t = ln[3:] if ln.startswith("OR ") else ln
        if not t.startswith(letter + "="):
            return False, f"answer line {ln!r} should start {letter}="
        try:
            answers.append(parse(t[len(letter) + 1:]))
        except PErr as e:
            return False, f"can't read answer {ln!r}: {e}"
    if pw is not None and len(pw) >= 2:
        return False, f"answers shown though {letter} has powers {pw} ({lines[2:]})"
    ok, why = check_answers(eq_node, letter, answers, poly, rng)
    if ok:
        typed = normalize_typed(formula)
        for ln in lines[2:]:
            for numtxt in re.findall(r"[0-9]*\.[0-9]+", ln):
                if len(numtxt.strip(".0")) >= 6 and numtxt not in typed:
                    return False, f"INEXACT: a 10-digit decimal in the answer {ln!r}"
    return ok, why


def rearrange_cases(rng):
    seen = set()
    for f in FORMULAS:
        l, r = f.split("=")
        letters = sorted(letters_of(parse(l, typed=True)) | letters_of(parse(r, typed=True)),
                         key=lambda c: ORDER[c])
        for letter in letters:
            if (f, letter) in seen:
                continue
            seen.add((f, letter))
            run = Run(["4", "1", f, letter])
            try:
                ok, why = check_rearrange(run, f, letter, rng)
            except Exception as e:  # noqa: BLE001
                ok, why = False, f"oracle error {type(e).__name__}: {e}"
            R.add("rearrange", run, ok, why)
    # high single-letter powers, letters not in the formula, θ
    for f, letter in [("X^10=1024", "X"), ("X^9=512", "X"), ("X^12=4096", "X"), ("X^11=2048", "X"),
                      ("Y=X^7", "X"), ("Y=X^5", "X"), ("Y=X^6", "X"), ("A=BH/2", "Q"),
                      ("θ=2piR", "R"), ("θ=2piR", "θ"), ("X²=4", "X"), ("3X=12", "X")]:
        run = Run(["4", "1", f, letter])
        try:
            ok, why = check_rearrange(run, f, letter, rng)
        except Exception as e:  # noqa: BLE001
            ok, why = False, f"oracle error {type(e).__name__}: {e}"
        R.add("rearrange/edge", run, ok, why)
    for f, msg in [("A=BH/2", None), ("A+B", "NEEDS AN = SIGN"), ("A=", "NEEDS ONE ="),
                   ("A=B=C", "NEEDS ONE =")]:
        if msg is None:
            continue
        run = Run(["4", "1", f] + (["A"] if msg == "x" else []))
        ok = any(msg in ln for ln in run.lines)
        R.add("rearrange/bad", run, ok, f"expected a message with {msg!r}")
    for letter in ["AB", "1", "x2"]:
        run = Run(["4", "1", "A=BH/2", letter])
        ok = any(MSG_PICK in ln for ln in run.lines)
        R.add("rearrange/bad", run, ok, "a non-letter must be refused")


# ============================================================ inverse

def random_inverse_functions(rng, n):
    """(typed f, one_to_one?)"""
    out = []

    def ri(lo=1, hi=9):
        return rng.randint(lo, hi) * rng.choice([1, -1])

    def num(v):
        return str(v) if v >= 0 else f"-{-v}"

    for _ in range(n):
        k = rng.randrange(12)
        a, b, c, d = ri(), ri(0, 9), ri(), ri(0, 9)
        if k == 0:
            f = f"{num(a)}X+{b}" if b >= 0 else f"{num(a)}X{b}"
            out.append((f.replace("+-", "-"), True))
        elif k == 1:
            out.append((f"({num(a)}X+{b})/{abs(c)}".replace("+-", "-"), True))
        elif k == 2:
            if a * d - b * c == 0:
                d += 1
            out.append((f"({num(a)}X+{b})/({num(c)}X+{d})".replace("+-", "-"), a * d - b * c != 0))
        elif k == 3:
            out.append((f"{num(a)}/(X+{b})".replace("+-", "-"), True))
        elif k == 4:
            p = rng.choice([3, 5])
            out.append((f"{num(a)}X^{p}+{b}".replace("+-", "-"), True))
        elif k == 5:
            out.append((f"sqrt({abs(a)}X+{b})".replace("+-", "-"), True))
        elif k == 6:
            out.append((f"{abs(a)}sqrt(X-{abs(b)})", True))
        elif k == 7:
            out.append((f"(X+{b})^3".replace("+-", "-"), True))
        elif k == 8:
            out.append((f"X^3+{abs(a)}X", True))
        elif k == 9:
            out.append((f"X^2+{num(a)}X".replace("+-", "-"), False))
        elif k == 10:
            out.append((f"{abs(a)}/(X^2+{abs(b) + 1})", False))
        else:
            out.append((f"(X+{abs(b)})/{abs(c)}+{num(a)}/{abs(d) + 1}".replace("+-", "-"), True))
    return out


INVERSE_FIXED = [
    ("2X+3", True), ("(X+1)/(X-2)", True), ("X^3", True), ("sqrt(X-2)", True), ("1/X", True),
    ("X^2", "branches"), ("X^2+2X", False), ("X^4", "branches"), ("X^2+1", "branches"),
    ("1/X^2", "branches"), ("X+1/X", False), ("(X-1)^3+2", True), ("X^3+X", True),
    ("1/(X+2)+3", True), ("sqrt(X)+1", True), ("³√(X)", True), ("³√(X-1)+2", True),
    ("X^5", True), ("X^7", True), ("2X^5+X", True), ("7-X", True), ("X/4-2", True),
    (".5X+1.5", True), ("-X^3", True), ("X^3/8", True), ("2/X^3", True), ("(2X+1)^3", True),
    ("(X-3)^2", False), ("e^(2)X+1", True), ("10^(2)X", True), ("sqrt(3X-1)", True),
    ("(3X+2)/(4X+6)", True), ("X/(X^2+1)", False), ("piX+1", True), ("X^3-6X^2+12X-8", True),
]


def check_inverse(run, f, kind, rng):
    node = parse(f, typed=True)
    lines = run.lines
    if any("NO INVERSE" in x for x in lines):
        if kind is True:
            return False, "WRONG: says NO INVERSE / not one-to-one, but f is one-to-one"
        return True, ""
    if any("CAN'T FIND THIS INVERSE" in x for x in lines):
        if has_func(node):
            return True, ""
        # an honest "can't" is fine: X really does appear with two powers
        poly, _ = cleared((("var", "Y"), node))
        pw = powers_of(poly, SYM["X"])
        if "DIFFERENT POWERS" in " ".join(lines) and pw and len(pw) >= 2:
            return True, ""
        return False, "MISLEADING: says it has trig/logs/X in a power, but it has none"
    if len(lines) < 3 or lines[1] != "SO":
        return False, f"unexpected screen {lines}"
    answers = []
    for ln in lines[2:]:
        if not (ln.startswith("F⁻¹(X)=") or ln.startswith("OR F⁻¹(X)=")):
            continue
        t = ln[3:] if ln.startswith("OR ") else ln
        if not t.startswith("F⁻¹(X)="):
            return False, f"unexpected line {ln!r}"
        answers.append(parse(t[len("F⁻¹(X)="):]))
    if kind is False and len(answers) < 2:
        return False, f"f isn't one-to-one but one inverse is given: {lines[2:]}"
    if len(answers) >= 2 and not any("DOMAIN" in x for x in lines):
        return False, "two branches without the domain note"
    good = 0
    for trial in range(60):
        x0 = rng.uniform(-4, 4) if trial % 2 else rng.uniform(-2, 25)
        try:
            y0 = ev(node, {"X": complex(x0)})
        except ZeroDivisionError:
            continue
        if abs(y0.imag) > 1e-9 * max(1, abs(y0)) or abs(y0) > 1e6:
            continue
        y0 = complex(y0.real)
        vals = []
        for a in answers:
            try:
                vals.append(ev(a, {"X": y0}))
            except ZeroDivisionError:
                vals.append(None)
        # every real branch value must map back: f(g(y0)) = y0
        for v in vals:
            if v is None or abs(v.imag) > 1e-9 * max(1, abs(v)):
                continue
            try:
                back = ev(node, {"X": complex(v.real)})
            except ZeroDivisionError:
                continue
            if abs(back - y0) > 1e-7 * (1 + abs(y0)):
                return False, f"WRONG: f(finv({y0.real:.6g})) = {back:.6g}"
        # and some branch must give x0 back
        if not any(v is not None and abs(v - x0) <= 1e-6 * (1 + abs(x0)) for v in vals):
            if kind is True or kind == "branches":
                return False, f"WRONG: f({x0:.6g})={y0.real:.6g} but no branch gives {x0:.6g} back"
        good += 1
        if good >= 6:
            break
    if not good:
        return False, "could not verify"
    return True, ""


def inverse_cases(rng):
    items = list(INVERSE_FIXED) + random_inverse_functions(rng, 140)
    for f, kind in items:
        run = Run(["4", "2", f])
        try:
            ok, why = check_inverse(run, f, kind, rng)
        except Exception as e:  # noqa: BLE001
            ok, why = False, f"oracle error {type(e).__name__}: {e}"
        R.add("inverse", run, ok, why)
    for f, msg in [("2Y+3", "ONLY LETTER"), ("X+Y", "ONLY LETTER"), ("Y=2X", "NO = SIGN"),
                   ("5", "ONLY LETTER")]:
        run = Run(["4", "2", f])
        ok = any(msg in ln for ln in run.lines)
        R.add("inverse/bad", run, ok, f"expected a message with {msg!r}")


# ============================================================ solve with several letters

def solve_cases(rng):
    items = [("AX+B=C", None), ("AB=C", "B"), ("AB=C", "A"), ("AX+B=CX+D", None),
             ("A(X-B)=C", None), ("X/A+B=C", None), ("AX^2=B", None), ("X^2+AX=B", None),
             ("Y=MX+B", None), ("AX=BX+C", None), ("(X+A)/B=C", None), ("A/X=B", None),
             ("AX+BX=C", None), ("X-A=B", None), ("2X+A=5", None), ("PV=NRT", "T"),
             ("1/X+1/A=1/B", None), ("A=BH/2", "H"), ("AX=B", None), ("θX=1", None),
             ("D=RT", "R"), ("E=MC^2", "C"), ("AB=C", "Q")]
    # random linear-in-X equations with other letters
    for _ in range(60):
        lets = rng.sample([c for c in "ABCDKMNPQRT"], rng.randint(1, 4))
        terms = []
        for L in lets:
            form = rng.random()
            if form < .4:
                terms.append(f"{L}X")
            elif form < .7:
                terms.append(f"{rng.randint(2, 9)}{L}")
            else:
                terms.append(f"{L}")
        if not any("X" in t for t in terms):
            terms[0] = terms[0] + "X" if "X" not in terms[0] else terms[0]
        k = rng.randint(1, len(terms))
        left, right = terms[:k], terms[k:] or [str(rng.randint(1, 9))]
        items.append(("+".join(left) + "=" + "-".join(right), None))
    for eq, letter in items:
        if letter:
            ask = Run(["1", eq])
            ok = ask.m.wait.kind == "input" and ask.m.wait.prompt == "SOLVE FOR:"
            R.add("solve-letters/asks", ask, ok, "no X in it: should ask SOLVE FOR:")
        ans = ["1", eq] + ([letter] if letter else [])
        run = Run(ans)
        target = letter or "X"
        try:
            if letter is None and run.m.wait.kind == "input" and run.m.wait.prompt == "SOLVE FOR:":
                ok, why = False, "asked SOLVE FOR: though X is in it"
            else:
                ok, why = check_rearrange(run, eq, target, rng, solve_mode=True)
        except Exception as e:  # noqa: BLE001
            ok, why = False, f"oracle error {type(e).__name__}: {e}"
        R.add("solve-letters", run, ok, why)


# ============================================================ degree mode and AGAIN chains

def degree_cases(rng):
    """The same answers in Degree mode must give the same screens as in Radian."""
    picks = []
    srng = random.Random(SEED + 7)
    for _ in range(60):
        made = make_system(srng)
        if made:
            picks.append(["3", str(len(made[0]))] + made[0])
    for f in FORMULAS[::3]:
        l, r = f.split("=")
        lets = sorted(letters_of(parse(l, typed=True)) | letters_of(parse(r, typed=True)))
        picks.append(["4", "1", f, srng.choice(lets)])
    for f, _ in INVERSE_FIXED[::2]:
        picks.append(["4", "2", f])
    picks += [["1", "AX+B=C"], ["1", "AB=C", "B"], ["1", "AX^2=B"]]
    for i, ans in enumerate(picks):
        rad = Run(ans)
        deg = Run(ans, degree=True)
        ok = rad.lines == deg.lines
        # (a problem that Radian has too is reported by the other areas)
        deg.problems = [p for p in deg.problems if p not in rad.problems]
        why = f"Degree screens differ from Radian: {rad.lines} vs {deg.lines}"
        m = deg.m
        if ok and i % 7 == 0 and m.wait.kind == "menu" and m.wait.title == "WHAT NEXT?":
            # QUIT must put Degree back
            m.answer(str(len(m.wait.options)))
            if m.wait.kind != "done" or m.radians:
                ok, why = False, f"QUIT after a Degree run: {m.wait}, radians={m.radians}"
        R.add("degree", deg, ok, why)


def chain_cases(rng):
    """Many rounds in one machine through AGAIN: same screens as from a fresh start."""
    crng = random.Random(SEED + 11)
    for mode in ("sys", "rearr", "inv"):
        m = Machine(TOK["BRAINIAC"])
        m.programs.update({k: v for k, v in TOK.items() if k != "BRAINIAC"})
        m.screen = RecScreen()
        m.run()
        first = True
        for i in range(12):
            if mode == "sys":
                made = make_system(crng)
                if not made:
                    continue
                body = [str(len(made[0]))] + made[0]
                head = ["3"]
            elif mode == "rearr":
                f = crng.choice(FORMULAS)
                l, r = f.split("=")
                lets = sorted(letters_of(parse(l, typed=True)) | letters_of(parse(r, typed=True)))
                body = [f, crng.choice(lets)]
                head = ["4", "1"]
            else:
                body = [crng.choice(INVERSE_FIXED)[0]]
                head = ["4", "2"]
            if first:
                seq = head + body
                first = False
            else:
                w = m.wait
                if w.kind == "menu" and w.title == "WHAT NEXT?":
                    seq = ["1"] + body
                elif w.kind == "input":
                    seq = body
                else:
                    break
            chained = Run(seq, machine=m)
            freshrun = Run(head + body)
            ok = chained.lines == freshrun.lines
            chained.answers = ["(round %d of a chain)" % (i + 1)] + head + body
            R.add("chain", chained, ok, f"AGAIN round differs from a fresh start: "
                                        f"{chained.lines} vs {freshrun.lines}")


# ============================================================ main

# ============================================================ arithmetic like the calculator's

# The TI-84 Plus CE computes in decimal (14 digits, BCD). The simulator computes in
# binary floats rounded to 14 significant digits after each step, which is not the
# same: fPart(1011/10) is .099999999999994 there but exactly .1 on the calculator,
# and 127.2-128.2 is -.99999999999999 there but exactly -1. Brainiac reads letter
# powers as digits with iPart(10fPart(K/10^J)), so in the simulator V=LWH solved for
# H says "THAT LETTER CANCELS OUT". The "ti" pass below redoes + - * / and fPart( in
# 14-digit decimal, so its failures are what the calculator would do; the "stock"
# pass shows what the unpatched simulator does.

from decimal import ROUND_HALF_UP, Decimal, localcontext  # noqa: E402

from brainiac import tibasic as tib  # noqa: E402

STOCK = {"arith": tib.Evaluator.arith, "unary": tib.Evaluator.unary, "rref": tib.rref}


def _d(x):
    return Decimal(f"{x:.14g}")


def dec_arith(self, op, a, b):
    if op in ("+", "-", "*", "/") and type(a) in (float, int) and type(b) in (float, int):
        m = self.m
        m.ops += 1
        a, b = m.number(a), m.number(b)
        if op == "/" and b == 0:
            raise tib.TIError("DIVIDE BY 0")
        with localcontext() as ctx:
            ctx.prec = 14
            ctx.rounding = ROUND_HALF_UP
            da, db = _d(a), _d(b)
            r = da + db if op == "+" else da - db if op == "-" else da * db if op == "*" else da / db
        return m.number(float(r))
    return STOCK["arith"](self, op, a, b)


def dec_unary(self, name, v):
    if name == "fPart(" and type(v) in (float, int):
        self.m.ops += 1
        d = _d(self.m.real(v))
        return float(d - int(d))
    return STOCK["unary"](self, name, v)


def set_mode(mode):
    if mode == "ti":
        tib.Evaluator.arith = dec_arith
        tib.Evaluator.unary = dec_unary
    else:
        tib.Evaluator.arith = STOCK["arith"]
        tib.Evaluator.unary = STOCK["unary"]
    tib.rref = STOCK["rref"]


# ============================================================ main

def run_all(only, mode):
    rng = random.Random(SEED)
    if only in (None, "sys"):
        systems_fixed()
        systems_cases(rng, SYSTEM_COUNT)
        if "--exact-rref" in sys.argv:
            tib.rref = exact_rref
            try:
                systems_cases(rng, 0, tag="[exact rref]")
            finally:
                tib.rref = STOCK["rref"]
    if only in (None, "rearr"):
        rearrange_cases(random.Random(SEED + 1))
    if only in (None, "inv"):
        inverse_cases(random.Random(SEED + 2))
    if only in (None, "solve"):
        solve_cases(random.Random(SEED + 3))
    if mode == "stock" and only is None:
        return
    if only in (None, "deg"):
        degree_cases(random.Random(SEED + 4))
    if only in (None, "chain"):
        chain_cases(random.Random(SEED + 5))


SYSTEM_COUNT = 560


def main():
    t0 = time.time()
    passes = ["ti", "stock"]
    if "--pass" in sys.argv:
        passes = [sys.argv[sys.argv.index("--pass") + 1]]
    for mode in passes:
        set_mode(mode)
        R.prefix = "" if mode == "ti" else "stock-sim: "
        run_all(ONLY, mode)
    set_mode("stock")
    total_pass = sum(c[0] for c in R.counts.values())
    total_fail = sum(c[1] for c in R.counts.values())
    print(f"\n{'area':40} pass  fail")
    for area in sorted(R.counts):
        p, f = R.counts[area]
        print(f"{area:40} {p:4}  {f:4}")
    print(f"{'TOTAL':40} {total_pass:4}  {total_fail:4}   ({time.time() - t0:.0f} s)")
    ti_fail_keys = {(a, seq) for a, seq, _, _ in R.fails if not a.startswith("stock-sim: ")}
    if R.fails:
        print("\nFAILURES (answer sequence from a fresh start; '|' separates answers).")
        print("Unprefixed areas ran with calculator-like decimal arithmetic; 'stock-sim:' ones")
        print("ran on the unpatched simulator and are listed only when the decimal run passed.")
        for area, seq, problems, lines in R.fails:
            if area.startswith("stock-sim: ") and (area[len("stock-sim: "):], seq) in ti_fail_keys:
                continue
            print(f"\n[{area}] {seq}")
            for p in problems:
                print(f"    -> {p}")
            print(f"    screen: {lines}")
    return 1 if total_fail else 0


if __name__ == "__main__":
    sys.exit(main())
