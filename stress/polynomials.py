#!/usr/bin/env python3
"""Stress test for Brainiac's POLYNOMIALS menu (main menu 2).

    python stress/polynomials.py            # full run (fixed seed, 2 worker processes)
    python stress/polynomials.py -n 200     # fewer cases per mode, for a quick look
    python stress/polynomials.py --seed 7   # another fixed set
    python stress/polynomials.py --only quad,fact -j 1
    python stress/polynomials.py --source my/BRAINIAC.txt   # try a fix without editing src
    python stress/polynomials.py --raw-sim  # don't make the simulator's fPart( decimal

Covers 2 QUADRATIC STEPS+FORMS, 3 SIMPLIFY/EXPAND, 4 FACTOR, 5 DIVIDE in
depth, and 1 ROOTS / 6 FIND ZEROS briefly. Each case runs on a fresh
simulated TI-84 Plus CE (a share of them in Degree mode), every results line
is checked against sympy, and every run must end with no ERR, no leaked
blocks and the angle mode put back on QUIT. A few long sessions also chain
cases on one machine (AGAIN / MAIN MENU) to catch state left over between
runs.

Exit code 0 = everything passed. Every failure is printed with the exact
answers to type from a fresh start, and whether the calculator was in Degree.
"""

from __future__ import annotations

import argparse
import math
import random
import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

import sympy as sp

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import brainiac.build as build                              # noqa: E402
from brainiac.build import PROGRAMS, program_bytes        # noqa: E402
from brainiac.tibasic import COLS, ROWS, Machine, fmt, tokens_from_bytes  # noqa: E402
from brainiac.screen import view                           # noqa: E402

TOK = {}


def load_tokens(source=None):
    """Build every program from source (BRAINIAC from `source` if given, to
    try a fix without touching brainiac/src)."""
    if source:
        orig = build.source_path
        build.source_path = lambda name=build.NAME: Path(source) if name == "BRAINIAC" else orig(name)
    TOK.update({n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS})


def patch_bcd_parts():
    """The TI-84 Plus CE does decimal (BCD) arithmetic: 2112/10 is exactly
    211.2 there and fPart( gives exactly .2. The simulator uses binary floats,
    where fPart(211.2) is .19999999999999 and iPart(10*that) is 1, which
    garbles Brainiac's letter-power digits (PK) with 4+ letters. Until the
    simulator does this itself, work fPart( iPart( int( out on the 14-digit
    decimal value, like the calculator. (--raw-sim turns this off.)"""
    import brainiac.tibasic as tb
    from decimal import Decimal
    orig = tb.Evaluator.unary
    if getattr(orig, "bcd", False):
        return

    def unary(self, name, v):
        if name in ("fPart(", "iPart(", "int("):
            m = self.m
            m.ops += 1
            x = m.real(v)
            d = Decimal(f"{x:.14g}")
            if name == "fPart(":
                return float(d - int(d))
            if name == "iPart(":
                return float(int(d))
            return float(math.floor(d))
        return orig(self, name, v)
    unary.bcd = True
    tb.Evaluator.unary = unary


MODE_KEY ={"roots": "1", "quad": "2", "simp": "3", "fact": "4", "div": "5", "zeros": "6"}
PROMPTS = {"roots": ["P(X)="], "quad": ["EQ:"], "simp": ["EXPR:"], "fact": ["EXPR:"],
           "div": ["TOP:", "BOTTOM:"], "zeros": ["F(X)="]}
LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZθ"
DIGITS = "0123456789"
SYM = {c: sp.Symbol("theta" if c == "θ" else c) for c in LETTERS}
X = SYM["X"]


# ============================================================ running the program

def fresh(degree=False) -> Machine:
    m = Machine(TOK["BRAINIAC"])
    m.programs.update({k: v for k, v in TOK.items() if k != "BRAINIAC"})
    m.radians = not degree
    m.run()
    return m


class Recorder:
    """Logs every Output( so results can be read as logical lines (before the
    screen wraps them), and notices text written over other text on the same
    page or running off the bottom of the screen."""

    def __init__(self, m: Machine):
        self.m = m
        self.out = []            # texts written by Output(, in order
        self.problems = []
        self.used = {}
        scr = m.screen
        orig_out, orig_clear = scr.output, scr.clear

        def output(row, col, text):
            self.out.append(text)
            r, c = row - 1, col - 1
            for i, ch in enumerate(text):
                rr, cc = r + (c + i) // COLS, (c + i) % COLS
                if rr >= ROWS:
                    self.problems.append(f"text runs off the screen: {text!r}")
                    break
                if (rr, cc) in self.used and self.used[(rr, cc)] != text:
                    self.problems.append(
                        f"text written over other text: {text!r} over {self.used[(rr, cc)]!r}")
                    break
                self.used[(rr, cc)] = text
            orig_out(row, col, text)

        def clear():
            self.used = {}
            orig_clear()

        scr.output = output
        scr.clear = clear

    def mark(self):
        return len(self.out)

    def lines_since(self, mark):
        return [t for t in self.out[mark:]
                if not t.startswith("THINKING") and t not in ("ENTER: MORE", "ENTER: DONE")]


class Result:
    def __init__(self):
        self.kind = None         # result | message | error | stuck
        self.lines = []          # logical results lines (Output( texts)
        self.message = []        # the screen of an e_* message
        self.err = ""
        self.problems = []       # screen / leak / mode problems
        self.steps = 0           # statements run (a feel for the calculator's time)


def drive(m: Machine, rec: Recorder, mode: str, inputs: list[str]) -> Result:
    """The machine waits at this mode's first prompt: type the inputs and read
    every page up to the WHAT NEXT? menu (or a message screen)."""
    res = Result()
    mark = rec.mark()
    nprob = len(rec.problems)
    for text, prompt in zip(inputs, PROMPTS[mode]):
        if m.wait.kind != "input" or m.wait.prompt != prompt:
            res.kind = "stuck"
            res.err = f"expected prompt {prompt!r}, got {m.wait}"
            return res
        m.answer(text)
    for _ in range(60):
        w = m.wait
        if w.kind == "error":
            res.kind = "error"
            res.err = f"{w.message} at: {m.statement_text()}"
            break
        if w.kind == "pause":
            bottom = m.screen.lines()[ROWS - 1]
            if "ENTER: MORE" in bottom or "ENTER: DONE" in bottom:
                m.answer("")
                continue
            res.kind = "message"
            res.message = [ln.strip() for ln in view(m) if ln.strip()]
            m.answer("")
            # 0.5: a message leads to WHAT NEXT?, whose AGAIN asks again
            if m.wait.kind == "menu" and m.wait.title == "WHAT NEXT?":
                m.answer("1")
            if m.wait.kind != "input" or m.wait.prompt != PROMPTS[mode][0]:
                res.problems.append(f"after the message it did not ask again: {m.wait}")
            break
        if w.kind == "menu":
            if w.title != "WHAT NEXT?":
                res.problems.append(f"ended on menu {w.title!r}")
            res.kind = "result"
            break
        res.kind = "stuck"
        res.err = f"stopped at {w}"
        break
    else:
        res.kind = "stuck"
        res.err = "too many pages"
    res.lines = rec.lines_since(mark)
    res.problems += rec.problems[nprob:]
    if m.leaks:
        res.problems.append(f"leaks={m.leaks}")
    if m.blocks and res.kind != "error":          # (an ERR stops inside blocks)
        res.problems.append(f"blocks open: {m.blocks}")
    return res


def run_case(case) -> Result:
    m = fresh(case.degree)
    rec = Recorder(m)
    m.answer("2")
    m.answer(MODE_KEY[case.mode])
    res = drive(m, rec, case.mode, case.inputs)
    res.steps = m.steps
    if res.kind == "result":
        m.answer("QUIT")
        if m.wait.kind != "done":
            res.problems.append(f"QUIT did not end the program: {m.wait}")
        elif m.radians != (not case.degree):
            res.problems.append("QUIT did not put the angle mode back")
        if m.leaks:
            res.problems.append(f"leaks={m.leaks} after QUIT")
    return res


# ============================================================ reading the screen

def to_py(s: str) -> str:
    """Calculator text (what it shows, or what is typed on a computer) ->
    Python source for sympy, with exact numbers and implied multiplication."""
    s = s.replace("sqrt(", "√(").replace("pi", "π").replace("−", "-")
    out = []                        # (kind, text)
    i = 0
    while i < len(s):
        c = s[i]
        if c in DIGITS or c == "." or c == "ᴇ":
            j = i
            while j < len(s) and (s[j] in DIGITS or s[j] == "."):
                j += 1
            num = s[i:j] or "1"
            if j < len(s) and s[j] == "ᴇ":
                j += 1
                sign = ""
                if j < len(s) and s[j] in "⁻-~":
                    sign = "-"
                    j += 1
                k = j
                while k < len(s) and s[k] in DIGITS:
                    k += 1
                num += "e" + sign + s[j:k]
                j = k
            tok = ("val", f"R('{num}')")
            i = j
        elif c in LETTERS:
            tok = ("val", f"S['{c}']")
            i += 1
        elif c == "π":
            tok = ("val", "pi")
            i += 1
        elif c == "𝑖":
            tok = ("val", "I")
            i += 1
        elif c == "√":
            if s[i + 1:i + 2] != "(":
                raise ValueError(f"bad √ in {s!r}")
            tok = ("open", "sqrt(")
            i += 2
        elif c == "(":
            tok = ("open", "(")
            i += 1
        elif c == ")":
            tok = ("close", ")")
            i += 1
        elif c in "⁻~":
            tok = ("neg", "-")
            i += 1
        elif c == "-":
            tok = ("neg", "-") if not out or out[-1][0] in ("open", "op", "neg") else ("op", "-")
            i += 1
        elif c in "+*/":
            tok = ("op", c)
            i += 1
        elif c == "^":
            tok = ("op", "**")
            i += 1
        elif c in "²³":
            tok = ("post", "**2" if c == "²" else "**3")
            i += 1
        elif c == " ":
            i += 1
            continue
        else:
            raise ValueError(f"can't read {c!r} in {s!r}")
        if out and out[-1][0] in ("val", "close", "post") and tok[0] in ("val", "open", "neg"):
            out.append(("op", "*"))
        out.append(tok)
    return "".join(t for _, t in out)


NS = {"R": sp.Rational, "S": SYM, "pi": sp.pi, "I": sp.I, "sqrt": sp.sqrt}


def calc(s: str):
    """The value of calculator text, as sympy (exact: decimals become fractions)."""
    return eval(to_py(s), {"__builtins__": {}}, dict(NS))


def has_decimal(s: str) -> bool:
    return bool(re.search(r"\.\d|ᴇ", s))


def same(a, b, letters=(), approx=False, tol=1e-8) -> bool:
    """a == b as functions of the letters (exactly, or to about 9 digits)."""
    d = sp.simplify(sp.expand(a - b)) if not approx else None
    if not approx:
        if d == 0:
            return True
        d = sp.simplify(sp.cancel(sp.together(a - b)))
        return d == 0
    rng = random.Random(12345)
    syms = [SYM[c] if isinstance(c, str) else c for c in letters]
    for _ in range(4):
        sub = {v: complex(rng.uniform(-1.5, 1.5), rng.uniform(-1.5, 1.5)) for v in syms}
        try:
            va = complex(sp.N(a.subs(sub), 20))
            vb = complex(sp.N(b.subs(sub), 20))
        except (TypeError, ZeroDivisionError):
            return False
        if abs(va - vb) > tol * (1 + abs(va) + abs(vb)):
            return False
    return True


def top_level_terms(s: str) -> list[str]:
    """Split text at + and - that are outside brackets (⁻ is part of a term)."""
    out, depth, start = [], 0, 0
    for i, c in enumerate(s):
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        elif c in "+-" and depth == 0 and i > 0 and s[i - 1] != "ᴇ":
            out.append(s[start:i])
            start = i
    out.append(s[start:])
    return out


# ============================================================ writing inputs

def frac_text(q: sp.Rational, rng, allow_dec=True) -> str:
    """|q| as typed: 3, 3/4, (3/4) or .75. Returns (text, kind) with kind
    'int', 'frac' (needs brackets before a letter) or 'dec'."""
    q = abs(sp.Rational(q))
    if q.q == 1:
        return str(q.p), "int"
    den = q.q
    while den % 2 == 0:
        den //= 2
    while den % 5 == 0:
        den //= 5
    if allow_dec and den == 1 and rng.random() < 0.4:
        d = format(float(q), ".12g")
        if d.startswith("0.") and rng.random() < 0.5:
            d = d[1:]
        return d, "dec"
    return f"{q.p}/{q.q}", "frac"


def pow_text(name: str, e: int, rng) -> str:
    if e == 1:
        return name
    if e == 2 and rng.random() < 0.4:
        return name + "²"
    if e == 3 and rng.random() < 0.3:
        return name + "³"
    return f"{name}^{e}"


def mono_text(monom, names, rng) -> str:
    return "".join(pow_text(n, e, rng) for n, e in zip(names, monom) if e)


def term_text(mag: sp.Rational, mono: str, rng, allow_dec=True) -> str:
    t, kind = frac_text(mag, rng, allow_dec)
    if not mono:
        return t
    if kind == "int":
        return mono if mag == 1 else t + mono
    if kind == "dec":
        return t + mono
    p, q = t.split("/")
    r = rng.random()
    if r < 0.45:
        return f"({t}){mono}"
    return (mono if p == "1" else p + mono) + "/" + q


def poly_text(expr, names: str, rng, shuffle=0.15, allow_dec=True) -> str:
    """A polynomial (rational coefficients) typed out term by term."""
    gens = [SYM[c] for c in names]
    P = sp.Poly(sp.expand(expr), *gens)
    terms = P.terms()
    if not terms:
        return "0"
    if rng.random() < shuffle:
        rng.shuffle(terms)
    out = ""
    for i, (monom, c) in enumerate(terms):
        body = term_text(abs(c), mono_text(monom, names, rng), rng, allow_dec)
        if i == 0:
            out = ("-" if c < 0 else "") + body
        else:
            out += ("-" if c < 0 else "+") + body
    return out


def bracket(t: str) -> str:
    return "(" + t + ")"


class Case:
    def __init__(self, mode, inputs, degree, **info):
        self.mode = mode
        self.inputs = inputs
        self.degree = degree
        self.info = info

    def answers(self):
        return ["2", MODE_KEY[self.mode]] + self.inputs

    def describe(self):
        mode = "Degree" if self.degree else "Radian"
        return f"[{mode}] answers {self.answers()!r}"


def rand_rat(rng, maxn=9, dens=(1, 1, 1, 2, 3, 4, 5)) -> sp.Rational:
    return sp.Rational(rng.randint(-maxn, maxn), rng.choice(dens))


def nz(f):
    def g(*a, **k):
        while True:
            v = f(*a, **k)
            if v != 0:
                return v
    return g


rand_rat_nz = nz(rand_rat)


def rand_linear(rng, var=X, lead=5, const=9):
    p = rng.randint(1, lead) * rng.choice([1, 1, -1])
    q = rng.randint(-const, const)
    return p * var + q


def rand_poly(rng, deg, names="X", coef=9, dens=(1,), dense=0.8):
    gens = [SYM[c] for c in names]
    e = 0
    if len(gens) == 1:
        for k in range(deg + 1):
            if k == deg or rng.random() < dense:
                c = sp.Rational(rng.randint(-coef, coef), rng.choice(dens))
                if k == deg and c == 0:
                    c = sp.Rational(rng.choice([-1, 1]) * rng.randint(1, coef), rng.choice(dens))
                e += c * gens[0] ** k
        return e
    for _ in range(rng.randint(2, 4)):
        c = sp.Rational(rng.choice([-1, 1]) * rng.randint(1, coef), rng.choice(dens))
        mon = 1
        for g in gens:
            mon *= g ** rng.randint(0, deg)
        e += c * mon
    return e


# ============================================================ checking: QUADRATIC

def numstr_ok(text: str, value, what: str, fails: list, exact_needed=True):
    """A number shown by numstr: a fraction (bottom under 1000) when it is one,
    else decimals."""
    value = sp.nsimplify(value) if not isinstance(value, sp.Basic) else value
    try:
        got = calc(text)
    except Exception as e:  # noqa: BLE001
        fails.append(("unreadable", f"{what}: can't read {text!r} ({e})"))
        return
    if has_decimal(text):
        if abs(complex(sp.N(got - value))) > 1e-9 * (1 + abs(complex(sp.N(value)))):
            fails.append(("wrong-number", f"{what}: shows {text}, should be {value}"))
        elif value.is_Rational and value.q <= 999 and exact_needed:
            fails.append(("decimal-not-fraction", f"{what}: shows {text}, exact value {value}"))
    elif sp.simplify(got - value) != 0:
        fails.append(("wrong-number", f"{what}: shows {text}, should be {value}"))


def check_quad(case, res):
    fails = []
    info = case.info
    if info.get("expect") != "quad":
        if res.kind != "message":
            fails.append(("nonquad-not-refused", f"expected a message, got {res.kind}: {res.lines[:3]}"))
        elif info.get("msg") and not any(info["msg"] in ln for ln in res.message):
            fails.append(("wrong-message", f"expected {info['msg']!r}, got {res.message}"))
        return fails
    if res.kind != "result":
        return [("quad-refused", f"{res.kind}: {res.message or res.err}")]
    p = sp.expand(info["poly"])
    v = SYM[info["letter"]]
    vs = info["letter"]
    a0, b0, c0 = [sp.Rational(sp.Poly(p, v).coeff_monomial(v ** k)) for k in (2, 1, 0)]
    L = sp.ilcm(a0.q, b0.q, c0.q)
    A, B, C = int(a0 * L), int(b0 * L), int(c0 * L)
    D = B * B - 4 * A * C
    lines = list(res.lines)
    pos = [0]

    def take(desc):
        if pos[0] >= len(lines):
            fails.append(("missing-line", f"ran out of lines looking for {desc}"))
            raise StopIteration
        pos[0] += 1
        return lines[pos[0] - 1]

    def expect_eq(desc, want):
        got = take(desc)
        if got != want:
            fails.append(("line-mismatch", f"{desc}: got {got!r}, want {want!r}"))
        return got

    try:
        ln = take("header")
        if not ln.startswith("QUADRATIC: "):
            fails.append(("line-mismatch", f"header {ln!r}"))
        ln = take("standard")
        m = re.fullmatch(r"STANDARD: (.*)=0", ln)
        if not m:
            fails.append(("line-mismatch", f"standard line {ln!r}"))
        else:
            st = m.group(1)
            if "(" in st or not same(calc(st), p):
                fails.append(("wrong-standard", f"STANDARD shows {st!r}, input is {p}"))
            if len(top_level_terms(st)) != len(sp.Poly(p, v).terms()):
                fails.append(("standard-not-collected", f"STANDARD {st!r}"))
        if L != 1:
            expect_eq("times L", f"TIMES {L} FOR WHOLE NUMBERS:")
            ln = take("scaled")
            m = re.fullmatch(r" (.*)=0", ln)
            if not m or not same(calc(m.group(1)), L * p):
                fails.append(("wrong-scaled", f"scaled line {ln!r}, want {sp.expand(L * p)}=0"))
        expect_eq("ABC", f"A={fmt(A)}, B={fmt(B)}, C={fmt(C)}")
        expect_eq("step1", "STEP 1: DISCRIMINANT")
        expect_eq("D formula", "D=B²-4AC")
        expect_eq("D subst", f" =({fmt(B)})²-4({fmt(A)})({fmt(C)})")
        want = " =" + fmt(B * B) + (("-" + fmt(4 * A * C)) if A * C >= 0 else ("+" + fmt(-4 * A * C)))
        expect_eq("D arith", want)
        expect_eq("D value", " =" + fmt(D))
        expect_eq("D sentence", "D>0: TWO REAL ROOTS." if D > 0 else
                  "D=0: ONE REPEATED ROOT." if D == 0 else "D<0: TWO COMPLEX ROOTS.")
        expect_eq("step2", "STEP 2: QUADRATIC FORMULA")
        expect_eq("formula1", f"{vs}=(⁻B+√(D))/(2A)")
        expect_eq("formula2", " OR (⁻B-√(D))/(2A)")
        expect_eq("formula3", f" =({fmt(-B)}+√({fmt(D)}))/{fmt(2 * A)}")
        expect_eq("formula4", f" OR ({fmt(-B)}-√({fmt(D)}))/{fmt(2 * A)}")
        r1 = (-B + sp.sqrt(D)) / (2 * A)
        r2 = (-B - sp.sqrt(D)) / (2 * A)
        rational = D >= 0 and sp.sqrt(D).is_Rational
        if D == 0:
            ln = take("double root")
            m = re.fullmatch(re.escape(vs) + r"=(.*) \(DOUBLE\)", ln)
            if not m:
                fails.append(("line-mismatch", f"double root line {ln!r}"))
            else:
                numstr_ok(m.group(1), r1, "double root", fails)
        else:
            shown = []
            for k in (1, 2):
                ln = take(f"root {k}")
                if not ln.startswith(vs + "="):
                    fails.append(("line-mismatch", f"root line {ln!r}"))
                    continue
                shown.append(ln[len(vs) + 1:])
            got = []
            for k, txt in enumerate(shown, 1):
                try:
                    got.append(calc(txt))
                except Exception as e:  # noqa: BLE001
                    fails.append(("unreadable", f"root {txt!r}: {e}"))
            if rational:
                # numstr: fractions with bottoms under 1000, else decimals
                for k, txt in enumerate(shown, 1):
                    want_r = r1 if k == 1 else r2
                    numstr_ok(txt, want_r, f"root {k}", fails)
            elif len(got) == 2:
                exact = [sp.simplify(sp.expand(g - w)) == 0 for g, w in zip(got, (r1, r2))]
                swapped = [sp.simplify(sp.expand(g - w)) == 0 for g, w in zip(got, (r2, r1))]
                if any(has_decimal(t) for t in shown) or not (all(exact) or all(swapped)):
                    fails.append(("wrong-root", f"roots shown {shown}, should be {r1} and {r2}"))
                elif not all(exact):
                    fails.append(("root-order", f"exact roots {shown} are in the other order from "
                                  f"the formula line (⁻B+√(D))/(2A) = {r1}"))
            if not rational:
                for k, suffix in ((1, " AND"), (2, "")):
                    ln = take(f"decimal {k}")
                    m = re.fullmatch(r" =(.*)" + suffix, ln)
                    if not m:
                        fails.append(("line-mismatch", f"decimal line {ln!r}"))
                        continue
                    dec = complex(sp.N(calc(m.group(1))))
                    if len(got) == 2:
                        w = complex(sp.N(got[k - 1]))
                        other = complex(sp.N(got[2 - k]))
                        if abs(dec - w) > 2e-9 * (1 + abs(w)):
                            if abs(dec - other) <= 2e-9 * (1 + abs(other)):
                                fails.append(("root-decimal-mismatch",
                                              f"{vs}={shown[k - 1]} is followed by the decimal of "
                                              f"the other root: ={m.group(1)}"))
                            else:
                                fails.append(("wrong-decimal", f"decimal root {m.group(1)} vs {w}"))
        expect_eq("step3", "STEP 3: FACTORED FORM")
        if D < 0:
            expect_eq("none", "NONE WITH REAL NUMBERS")
            expect_eq("none2", "(THE ROOTS ARE COMPLEX).")
        else:
            ln = take("factored")
            try:
                F = calc(ln)
                if not same(F, p):
                    fails.append(("wrong-factored", f"factored form {ln!r} != {p}"))
            except Exception as e:  # noqa: BLE001
                fails.append(("unreadable", f"factored {ln!r}: {e}"))
            if len(top_level_terms(ln.lstrip("⁻"))) > 1:
                fails.append(("factored-not-factored", f"factored form {ln!r}"))
            if re.match(r"⁻?[0-9]+/[0-9]+\(", ln):
                fails.append(("factored-fraction-unbracketed",
                              f"factored form {ln!r}: the vertex form brackets a fraction in "
                              "front, this doesn't"))
        expect_eq("step4", "STEP 4: VERTEX")
        h = -b0 / (2 * a0)
        k = p.subs(v, h)
        ln = take("H")
        m = re.fullmatch(r"H=⁻B/\(2A\)=(.*)", ln)
        htxt = ktxt = None
        if not m:
            fails.append(("line-mismatch", f"H line {ln!r}"))
        else:
            htxt = m.group(1)
            numstr_ok(htxt, h, "H", fails)
        ln = take("K")
        m = re.fullmatch(r"K=F\(H\)=(.*)", ln)
        if not m:
            fails.append(("line-mismatch", f"K line {ln!r}"))
        else:
            ktxt = m.group(1)
            numstr_ok(ktxt, k, "K", fails)
        expect_eq("vertex", f"VERTEX: ({htxt},{ktxt})")
        expect_eq("axis", f"AXIS: {vs}={htxt}")
        expect_eq("opens", "OPENS UP." if a0 > 0 else "OPENS DOWN.")
        expect_eq("vf head", "VERTEX FORM:")
        ln = take("vertex form")
        vf = ln[1:]
        try:
            V = calc(vf)
            if not same(V, p, [v], approx=has_decimal(vf)):
                fails.append(("wrong-vertex-form", f"vertex form {vf!r} != {p}"))
        except Exception as e:  # noqa: BLE001
            fails.append(("unreadable", f"vertex form {vf!r}: {e}"))
        if not re.fullmatch(r"(⁻|[0-9.ᴇ⁻]+|\(⁻?[0-9]+/[0-9]+\)|[0-9]+/[0-9]+)?"
                            + "(" + re.escape(vs) + r"²|\(" + re.escape(vs) + r"[+-][^()]+\)²)([+-].+)?", vf):
            fails.append(("vertex-form-shape", f"vertex form {vf!r}"))
        if re.match(r"[0-9]+/[0-9]+\(", vf):
            fails.append(("vertex-form-shape", f"fraction in front without brackets {vf!r}"))
        expect_eq("step5", "STEP 5: SUM AND PRODUCT")
        ln = take("sum")
        m = re.fullmatch(r"ROOT SUM=⁻B/A=(.*)", ln)
        if not m:
            fails.append(("line-mismatch", f"sum line {ln!r}"))
        else:
            numstr_ok(m.group(1), -b0 / a0, "root sum", fails)
        ln = take("product")
        m = re.fullmatch(r"ROOT PRODUCT=C/A=(.*)", ln)
        if not m:
            fails.append(("line-mismatch", f"product line {ln!r}"))
        else:
            numstr_ok(m.group(1), c0 / a0, "root product", fails)
        if pos[0] != len(lines):
            fails.append(("extra-lines", f"extra lines {lines[pos[0]:]}"))
    except StopIteration:
        pass
    return fails


def gen_quad(rng, n, degree_share):
    cases = []
    letters = "XXXXXXXTAY"

    def add(text, poly=None, letter="X", expect="quad", msg=None, kind=""):
        cases.append(Case("quad", [text], rng.random() < degree_share, poly=poly, letter=letter,
                          expect=expect, msg=msg, kind=kind))

    def eq_text(p, v, name):
        """p=0 typed in one of several ways (standard, moved terms, =0)."""
        r = rng.random()
        if r < 0.45:
            return poly_text(p, name, rng)
        if r < 0.6:
            return poly_text(p, name, rng) + "=0"
        # split the terms across the = sign
        P = sp.Poly(p, v)
        left, right = 0, 0
        for (e,), c in P.terms():
            if rng.random() < 0.5:
                left += c * v ** e
            else:
                right -= c * v ** e
        if left == 0 or right == 0:
            return poly_text(p, name, rng) + "=0"
        return poly_text(left, name, rng) + "=" + poly_text(right, name, rng)

    fixed = ["X^2-5X+6", "2X^2+3X-5", "X^2-6X+9", "X^2+1", "-X^2+4", "X^2/2+X+3", "X^2-2",
             "3X^2-12X+13", "(X-1)(X+3)", "3(X-2)^2+1", "4X^2+4X+1", "-2X^2+X+1", "X^2",
             "X^2-X", "0.5X^2-1.25X+2", "(2/3)X^2-X/4+1", "X²-7X+12=0", "2X²=5-3X", "X^2+X+1",
             "-X^2-1", "9X^2-12X+4", "X^2-10X+1", "25X^2-30X+9", "-(X+1/2)^2+3/4",
             "(1/2)(X-3)^2-2", "X(X+4)", "T^2-4T+1", "A^2=2A+8"]
    for t in fixed:
        name = "T" if "T" in t else "A" if "A" in t else "X"
        v = SYM[name]
        if "=" in t:
            l, r = t.split("=")
            p = calc(l) - calc(r)
        else:
            p = calc(t)
        add(t, sp.expand(p), name, kind="fixed")
    # graceful refusals
    for t, msg in [("X^3-1", "ISN'T A QUADRATIC"), ("2X+3", "ISN'T A QUADRATIC"),
                   ("5", "EXACTLY ONE LETTER"), ("X^2+Y", "EXACTLY ONE LETTER"),
                   ("1/X^2+1", "ISN'T A QUADRATIC"), ("piX^2+1", "ISN'T A QUADRATIC"),
                   ("sqrt(2)X^2+1", "ISN'T A QUADRATIC"), ("X(X+1)-X^2", "ISN'T A QUADRATIC"),
                   ("X^4-X^2", "ISN'T A QUADRATIC"), ("=X^2", "NEEDS ONE ="),
                   ("X^2=", "NEEDS ONE ="), ("X^2=1=2", "NEEDS ONE ="), ("sin(X)^2", "ISN'T A QUADRATIC"),
                   ("sqrt(X)+X^2", "ISN'T A QUADRATIC"), ("X^2-X^2", "ISN'T A QUADRATIC"),
                   ("abs(X)^2", "ISN'T A QUADRATIC"), ("2^X", "ISN'T A QUADRATIC"),
                   ("0.1234567X^2+X+1", "ISN'T A QUADRATIC"), ("(X+1)^3-X^3", None)]:
        if t == "(X+1)^3-X^3":
            add(t, sp.expand(calc(t)), "X", kind="fixed")
        else:
            add(t, expect="msg", msg=msg, kind="refuse")
    while len(cases) < n:
        name = rng.choice(letters)
        v = SYM[name]
        kind = rng.choice(["std", "std", "frac", "dec", "factored", "vertex", "double", "complex",
                           "irrational", "neg", "big", "cubic", "linear"])
        if kind == "std":
            a = rng.choice([1, 1, 2, 3, -1, -2, 4, 5, 6, -3])
            p = a * v ** 2 + rng.randint(-15, 15) * v + rng.randint(-20, 20)
            add(eq_text(p, v, name), p, name, kind=kind)
        elif kind == "frac":
            p = rand_rat_nz(rng, 6, (1, 2, 3, 4, 6)) * v ** 2 + rand_rat(rng, 9, (1, 2, 3, 5)) * v \
                + rand_rat(rng, 9, (1, 2, 3, 4, 7))
            add(eq_text(p, v, name), sp.expand(p), name, kind=kind)
        elif kind == "dec":
            p = (sp.Rational(rng.choice([-1, 1]) * rng.randint(1, 40), rng.choice([2, 4, 5, 10, 20, 8]))
                 * v ** 2 + sp.Rational(rng.randint(-99, 99), rng.choice([4, 10, 100, 5]))
                 * v + sp.Rational(rng.randint(-99, 99), rng.choice([1, 10, 100, 8])))
            add(eq_text(p, v, name), sp.expand(p), name, kind=kind)
        elif kind == "factored":
            k = rng.choice([1, 1, 1, 2, -1, 3, -2, sp.Rational(1, 2)])
            f1 = rand_linear(rng, v, 4, 9)
            f2 = rand_linear(rng, v, 4, 9)
            if rng.random() < 0.3:
                f1 = v - sp.Rational(rng.randint(-9, 9), rng.randint(1, 5))
            kt = "" if k == 1 else "-" if k == -1 else bracket(frac_text(k, rng, False)[0]) \
                if not sp.Rational(k).q == 1 else str(k)
            t = kt + bracket(poly_text(f1, name, rng)) + bracket(poly_text(f2, name, rng))
            if rng.random() < 0.3:
                t += "=0"
            add(t, sp.expand(k * f1 * f2), name, kind=kind)
        elif kind == "vertex":
            a = rng.choice([1, 1, 2, -1, 3, -2, sp.Rational(1, 2), sp.Rational(-3, 4), sp.Rational(2, 3)])
            h = rand_rat(rng, 9, (1, 1, 2, 3))
            kk = rand_rat(rng, 20, (1, 1, 2, 4))
            at = "" if a == 1 else "-" if a == -1 else (
                str(a) if sp.Rational(a).q == 1 else
                ("-" if a < 0 else "") + bracket(frac_text(a, rng, False)[0]))
            if a not in (1, -1) and sp.Rational(a).q == 1 and a < 0:
                at = "-" + str(-a)
            inner = poly_text(v - h, name, rng)
            sq = "^2" if rng.random() < 0.6 else "²"
            t = at + bracket(inner) + sq
            if kk:
                t += ("+" if kk > 0 else "-") + frac_text(kk, rng, False)[0]
            add(t, sp.expand(a * (v - h) ** 2 + kk), name, kind=kind)
        elif kind == "double":
            r = rand_rat(rng, 9, (1, 1, 2, 3))
            a = rng.choice([1, 2, 3, -1, 4, 9])
            p = sp.expand(a * (v - r) ** 2)
            add(eq_text(p, v, name), p, name, kind=kind)
        elif kind == "complex":
            a = rng.choice([1, 2, 3, -1, 5])
            b = rng.randint(-8, 8)
            c = (b * b) // (4 * abs(a)) + rng.randint(1, 30)
            p = a * v ** 2 + b * v + c * (1 if a > 0 else -1)
            add(eq_text(p, v, name), p, name, kind=kind)
        elif kind == "irrational":
            while True:
                a = rng.choice([1, 2, 3, -1, 5, 4])
                b = rng.randint(-12, 12)
                c = rng.randint(-20, 20)
                D = b * b - 4 * a * c
                if D > 0 and not sp.sqrt(D).is_Rational:
                    break
            p = a * v ** 2 + b * v + c
            add(eq_text(p, v, name), p, name, kind=kind)
        elif kind == "neg":
            p = -rng.randint(1, 9) * v ** 2 + rng.randint(-9, 9) * v + rng.randint(-9, 9)
            add(eq_text(p, v, name), p, name, kind=kind)
        elif kind == "big":
            p = rng.randint(-60, 60) * v ** 2 + rng.randint(-300, 300) * v + rng.randint(-999, 999)
            if p.coeff(v, 2) == 0:
                continue
            add(eq_text(p, v, name), p, name, kind=kind)
        elif kind == "cubic":
            p = rand_poly(rng, 3, name)
            add(poly_text(p, name, rng), expect="msg", msg="ISN'T A QUADRATIC", kind=kind)
        elif kind == "linear":
            p = rng.randint(1, 9) * v + rng.randint(-9, 9)
            add(poly_text(p, name, rng), expect="msg", msg="ISN'T A QUADRATIC", kind=kind)
    return cases


# ============================================================ checking: SIMPLIFY

def within_limits(expr, letters) -> bool:
    """Would Brainiac's extract take this? A polynomial, maybe over one term
    with letter powers up to 3, of degree up to 14 in one letter, or up to 6
    per letter and a grid of at most 99 points with several letters."""
    gens = [SYM[c] for c in letters]
    if not gens:
        return True
    num, den = sp.fraction(sp.cancel(sp.together(expr)))
    if not den.is_number:
        dp = sp.Poly(den, *gens)
        if not dp.is_monomial or max(dp.monoms()[0]) > 3:
            return False
    degs = [sp.Poly(num, g).degree() if g in num.free_symbols else 0 for g in gens]
    if len(gens) == 1:
        return degs[0] <= 14
    return max(degs) <= 6 and math.prod(d + 1 for d in degs) <= 99


def check_simp(case, res):
    fails = []
    info = case.info
    expr = info["expr"]
    letters = info["letters"]
    if info.get("expect") == "many":
        if res.kind != "message" or not any("TOO MANY LETTERS" in m for m in res.message):
            fails.append(("wrong-message", f"expected TOO MANY LETTERS, got {res.kind} {res.message}"))
        return fails
    if expr is None:
        if res.kind == "result":
            fails.append(("simplify-wrong-answer", f"{info['text']} is not a polynomial, "
                          f"but it shows {res.lines[1:]}"))
        elif res.kind == "message" and not any("SIMPLIFY" in m for m in res.message):
            fails.append(("wrong-message", f"{res.message}"))
        return fails
    canon = sp.cancel(sp.together(expr))
    num, den = sp.fraction(canon)
    gens = [SYM[c] for c in letters]
    if gens and not (num.is_polynomial(*gens) and den.is_polynomial(*gens)):
        den_is_monomial = False
    else:
        den_is_monomial = (den.is_number or (sp.Poly(den, *gens).is_monomial if gens else True))
    expect_ok = den_is_monomial and info.get("expect") != "refuse"
    if res.kind == "message":
        if not any("SIMPLIFY" in m for m in res.message):
            fails.append(("wrong-message", f"{res.message}"))
        if expect_ok:
            if within_limits(expr, letters):
                fails.append(("simplify-refused", f"refused, but it is {canon}"))
            else:
                fails.append(("limit-refused", f"too big for Brainiac: {canon}"))
        return fails
    if res.kind != "result":
        return [("simplify-" + res.kind, res.err)]
    if len(res.lines) != 2 or not res.lines[1].startswith("="):
        return [("line-mismatch", f"lines {res.lines}")]
    out = res.lines[1][1:]
    if not den_is_monomial:
        fails.append(("simplify-wrong-answer",
                      f"shows ={out} but {info['text']} = {canon} is not a polynomial "
                      "(nor one over a single term)"))
        return fails
    # the human reading of P/XY is P/(XY): note it, and read it that way
    m = re.fullmatch(r"(.*)/([A-Zθ](?:[A-Zθ²³]|\^\d)*)", out)
    human = out
    if m and len(re.findall(r"[A-Zθ]", m.group(2))) > 1 and ("+" in m.group(1) or "-" in m.group(1)
                                                             or m.group(1).startswith("(")
                                                             or not re.search(r"/", m.group(1))):
        fails.append(("ambiguous-denominator",
                      f"={out}: on the calculator P/{m.group(2)} means (P/{m.group(2)[0]})·..., "
                      f"needs brackets: {m.group(1)}/({m.group(2)})"))
        human = m.group(1) + "/(" + m.group(2) + ")"
    try:
        got = calc(human)
    except Exception as e:  # noqa: BLE001
        return [("unreadable", f"={out}: {e}")]
    approx = has_decimal(out) or info.get("approx", False)
    if not same(got, expr, letters, approx=approx):
        fails.append(("simplify-wrong-answer", f"shows ={out}, but it is {canon}"))
        return fails
    # shape: N, (N)/D or N/D with D the letters, N expanded and collected
    n_terms = len(sp.Poly(num, *gens).terms()) if gens else 1
    body = human
    if den != 1 and not den.is_number:
        mm = re.fullmatch(r"\((.*)\)/(\(?[A-Zθ²³^0-9]+\)?)", human) or \
            re.fullmatch(r"(.*)/(\(?[A-Zθ²³^0-9]+\)?)", human)
        if not mm:
            fails.append(("simplify-shape", f"={out}: expected (top)/{den}"))
            return fails
        body = mm.group(1)
    if re.search(r"\((?![0-9]+π\))", body):
        fails.append(("simplify-shape", f"={out}: not expanded"))
    elif len(top_level_terms(body.lstrip("⁻"))) != n_terms and not approx:
        fails.append(("simplify-not-collected", f"={out}: expected {n_terms} terms ({num})"))
    return fails


def gen_simp(rng, n, degree_share):
    cases = []

    def add(text, expr, letters, **kw):
        cases.append(Case("simp", [text], rng.random() < degree_share, text=text, expr=expr,
                          letters=letters, **kw))

    fixed = ["3X(X-2)+(X+1)^2", "2A+3B-A", "(X^3-1)/(X-1)", "(X+1)^3", "1/X+1/X^2",
             "(A+B)^2-(A-B)^2", "(3X^3-X^2-6)(X-5)", "5", "2+3*4", "1/2+1/3", "pi+pi", "2pi(3)",
             "pi/2", "piX^2+2piX", "(X+pi)^2", "X/pi", "(X^2-4)/(X+2)", "(6X^2+X-2)/(2X-1)",
             "(X^2+3X)/X^3", "1/X+1/Y", "A/B+B/A", "1/(XY)", "(X+Y)/(XY)", "X^2Y/(XY)",
             "(X-1)(X+1)(X^2+1)", "((X+1)^2-1)(X-2)", "2(X-(3X+1)(X-2))+X", "0.5X(X+0.2)",
             "(X+1)^10", "(X-2)^7", "X^14", "(A+B+C)^2", "(A-B)(A^2+AB+B^2)",
             "(X+Y+Z+W)^2", "(2X-3Y)^3", "X(Y+Z)-Y(X+Z)", "(X^2-1)/(X-1)+(X^2-4)/(X-2)",
             "(X+1)/X-1/X", "X/X", "(X^4-16)/(X^2+4)", "(A^2-B^2)/(A-B)", "sqrt(2)X+sqrt(2)X",
             "(X^2-Y^2)/(X+Y)", "1/X^3+X", "1/(X^3Y)", "1/(XY^2)+1", "(A^3-B^3)/(A-B)",
             "(X^2+2XY+Y^2)/(X+Y)", "(A^2-B^2)/(A+B)", "(X^3+Y^3)/(X+Y)", "(P^2-Q^2)/(P-Q)+R"]
    for t in fixed:
        e = calc(t)
        letters = "".join(sorted({c for c in t if c in LETTERS}, key=LETTERS.index))
        add(t, e, letters, kind="fixed")
    # rational expressions that don't simplify, and things that aren't polynomials
    for t in ["1/(X+1)", "(X+2)/(X-1)", "X/(X^2+1)", "1/(X-100)", "1/(X+50)", "1/(X-30)",
              "(X^2+1)/(X+100)", "1/(X^2+400)", "1/(X+20)", "1/(A+B)", "(A+1)/(B+1)",
              "1/(X-1)+1/(X+1)", "X^20", "X^15"]:
        letters = "".join(sorted({c for c in t if c in LETTERS}, key=LETTERS.index))
        add(t, calc(t), letters, expect="refuse", may_refuse=True, kind="rational")
    for t in ["sqrt(X)", "abs(X)", "2^X", "e^(X)", "sin(X)", "ln(X)", "X^(1/2)", "X^0.5",
              "e^(X/30)", "sqrt(X^2)", "1/sqrt(X)", "e^(X/50)", "ln(X+100)", "2^(X/20)",
              "10^(X/100)", "X^200", "(X+1)^150", "X^99"]:
        add(t, None, "X", expect="refuse", kind="notpoly")
    for a in (25, 30, 40, 50, 60, 75, 100, 150, 200, 500, 1000):
        for t in (f"1/(X-{a})", f"1/(X+{a})", f"X/(X-{a})", f"(X^2+1)/(X+{a})", f"sqrt(X+{a})",
                  f"1/(X^2+{a * a})"):
            add(t, calc(t), "X", expect="refuse", kind="farpole")
    add("A+B+C+D+E+F+G", calc("A+B+C+D+E+F+G"), "ABCDEFG", expect="many", kind="many")
    pools = ["X", "XY", "AB", "XYZ", "ABC", "ABCD", "PQRT", "MN"]
    while len(cases) < n:
        kind = rng.choice(["prod", "prod", "sumprod", "power", "multi", "multi", "nested",
                           "cancel", "monoden", "frac", "pi", "const", "rational", "dec"])
        if kind == "prod":
            fs = [rand_poly(rng, rng.randint(1, 2), "X", 6) for _ in range(rng.randint(2, 3))]
            fs = [f for f in fs if sp.Poly(f, X).degree() > 0] or [X + 1, X - 2]
            t = "".join(bracket(poly_text(f, "X", rng)) for f in fs)
            e = sp.Mul(*fs)
            if rng.random() < 0.3:
                k = rng.randint(2, 9) * rng.choice([1, -1])
                t = str(k).replace("-", "-") + t
                e = k * e
            add(t, e, "X", kind=kind)
        elif kind == "sumprod":
            parts, e = [], 0
            for _ in range(rng.randint(2, 3)):
                f = rand_poly(rng, 1, "X", 5)
                g = rand_poly(rng, rng.randint(1, 2), "X", 5)
                c = rng.randint(-5, 5) or 1
                s = (str(abs(c)) if abs(c) != 1 else "") + "X" * rng.randint(0, 1)
                if s.endswith("X"):
                    f = X
                    piece = s + bracket(poly_text(g, "X", rng))
                    val = abs(c) * X * g
                else:
                    piece = s + bracket(poly_text(f, "X", rng)) + bracket(poly_text(g, "X", rng))
                    val = abs(c) * f * g
                sign = -1 if c < 0 else 1
                parts.append(("-" if sign < 0 else "+") + piece)
                e += sign * val
            t = "".join(parts).lstrip("+")
            add(t, e, "X", kind=kind)
        elif kind == "power":
            f = rand_poly(rng, 1, "X", 5)
            if sp.Poly(f, X).degree() < 1:
                continue
            k = rng.randint(2, 6)
            t = bracket(poly_text(f, "X", rng)) + (f"^{k}" if k > 3 or rng.random() < 0.5 else "²³"[k - 2])
            if rng.random() < 0.5:
                g = rand_poly(rng, 2, "X", 9)
                t += rng.choice("+-") + bracket(poly_text(g, "X", rng))
            add(t, calc(t), "X", kind=kind)
        elif kind == "multi":
            names = rng.choice(pools[1:])
            fs = [rand_poly(rng, 1, names, 5) for _ in range(rng.randint(2, 3))]
            t = "".join(bracket(poly_text(f, names, rng)) for f in fs)
            if rng.random() < 0.4:
                g = rand_poly(rng, 2, names, 5)
                t += "+" + bracket(poly_text(g, names, rng))
            e = calc(t)
            letters = "".join(sorted({c for c in t if c in LETTERS}, key=LETTERS.index))
            add(t, e, letters, kind=kind)
        elif kind == "nested":
            a, b, c = (rand_poly(rng, 1, "X", 5) for _ in range(3))
            k = rng.randint(2, 5)
            t = f"{k}(X-{bracket(poly_text(a, 'X', rng))}{bracket(poly_text(b, 'X', rng))})" \
                f"+{bracket(poly_text(c, 'X', rng))}^2"
            add(t, calc(t), "X", kind=kind)
        elif kind == "cancel":
            names = rng.choice(["X", "X", "X", "AB"])
            q = rand_poly(rng, 1, names, 4)
            if q.is_number:
                continue
            pq = rand_poly(rng, rng.randint(1, 2), names, 5)
            top = sp.expand(pq * q)
            if top == 0:
                continue
            t = bracket(poly_text(top, names, rng)) + "/" + bracket(poly_text(q, names, rng))
            letters = "".join(sorted({c for c in t if c in LETTERS}, key=LETTERS.index))
            add(t, calc(t), letters, kind=kind)
        elif kind == "monoden":
            names = rng.choice(["X", "X", "XY", "AB"])
            e = 0
            parts = []
            for _ in range(rng.randint(2, 3)):
                c = rng.randint(1, 6)
                v = SYM[rng.choice(names)]
                k = rng.randint(0, 3)
                parts.append(f"{c}/{pow_text(str(v), k, rng)}" if k else str(c) + str(v))
            t = "+".join(parts)
            add(t, calc(t), "".join(sorted({c for c in t if c in LETTERS}, key=LETTERS.index)), kind=kind)
        elif kind == "frac":
            f = rand_poly(rng, rng.randint(1, 3), "X", 9, dens=(1, 2, 3, 4, 5, 6))
            g = rand_poly(rng, 1, "X", 5, dens=(1, 2, 3))
            t = bracket(poly_text(f, "X", rng)) + bracket(poly_text(g, "X", rng))
            add(t, calc(t), "X", kind=kind)
        elif kind == "pi":
            k = rng.randint(1, 4)
            t = rng.choice([f"{k}pi(X+{k})^2", f"pi(X^2-{k})+{k}piX", f"(X+{k}pi)(X-pi)",
                            f"piR^2+{k}piRH", f"{k}X/pi+X", f"(pi/{k+1})X^2"])
            add(t, calc(t), "".join(sorted({c for c in t if c in "XRH"}, key=LETTERS.index)), kind=kind)
        elif kind == "const":
            a, b, c = rng.randint(1, 9), rng.randint(1, 9), rng.randint(2, 9)
            t = rng.choice([f"{a}/{c}+{b}/{c+1}", f"({a}+{b})^2", f"{a}*{b}-{c}", f"{a}-{b}{c}",
                            f"({a}/{c})^2", f"{a}pi-{b}pi"])
            add(t, calc(t), "", kind=kind)
        elif kind == "rational":
            r = rng.randint(1, 9) * rng.choice([1, -1])
            t = rng.choice([f"1/(X+{r})", f"(X^2+1)/(X-{abs(r)})", f"X/(X^2+{abs(r)})",
                            f"(X+{abs(r)+1})/(X+{abs(r)})"])
            add(t, calc(t), "X", expect="refuse", kind=kind)
        elif kind == "dec":
            a = rng.choice(["0.1", ".25", "1.5", "2.75", ".2"])
            t = f"{a}X" + bracket(poly_text(X + rng.randint(1, 9), "X", rng))
            add(t, calc(t), "X", kind=kind)
    return cases


# ============================================================ checking: FACTOR

def split_product(s: str):
    """'⁻2X²(X+3)²(2X-1)/5' -> (constant text, [(factor text, power)], bottom)."""
    if len(top_level_terms(s.lstrip("⁻"))) > 1:
        return "", [(s, 1)], 1
    m = re.fullmatch(r"(.*?)/(\d+)", s)
    bottom = 1
    if m and not m.group(1).endswith(")") and "(" not in s[s.rfind("/"):]:
        pass
    if m and ")" not in s[s.rfind("/"):]:
        s, bottom = m.group(1), int(m.group(2))
    i = 0
    const = ""
    while i < len(s) and (s[i] in DIGITS or s[i] == "⁻"):
        const += s[i]
        i += 1
    factors = []
    while i < len(s):
        if s[i] == "(":
            depth, j = 0, i
            while True:
                if s[j] == "(":
                    depth += 1
                elif s[j] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            ftxt = s[i + 1:j]
            i = j + 1
        elif s[i] in LETTERS:
            ftxt = s[i]
            i += 1
        else:
            raise ValueError(f"can't split {s!r} at {i}")
        power = 1
        if i < len(s) and s[i] in "²³":
            power = 2 if s[i] == "²" else 3
            i += 1
        elif i < len(s) and s[i] == "^":
            j = i + 1
            while j < len(s) and s[j] in DIGITS:
                j += 1
            power = int(s[i + 1:j])
            i = j
        factors.append((ftxt, power))
    return const, factors, bottom


def check_fact(case, res):
    fails = []
    info = case.info
    expr = sp.expand(info["expr"])
    letters = info["letters"]
    gens = [SYM[c] for c in letters]
    if info.get("expect") == "notpoly":
        if res.kind != "message":
            fails.append(("fact-not-refused", f"{res.kind}: {res.lines}"))
        return fails
    if res.kind == "message":
        if not within_limits(expr, letters):
            return [("limit-refused-misleading",
                     f"too big for Brainiac, but it says {res.message[0]!r}: {expr}")]
        return [("fact-refused", f"{res.message}")]
    if res.kind != "result":
        return [("fact-" + res.kind, res.err)]
    lines = res.lines
    if len(lines) < 2:
        return [("line-mismatch", f"{lines}")]
    rest = lines[1:]
    single = (len(gens) == 1 and sp.Poly(expr, *gens).degree() >= 1
              and all(c.is_Rational for c in sp.Poly(expr, *gens).coeffs()))
    if single:
        if not rest[0].startswith("="):
            return [("line-mismatch", f"{lines}")]
        out = rest[0][1:]
        msg = rest[1:]
        try:
            F = calc(out)
        except Exception as e:  # noqa: BLE001
            return [("unreadable", f"={out}: {e}")]
        if not same(F, expr):
            fails.append(("factor-wrong-answer", f"shows ={out}, but it is {expr}"))
            return fails
        try:
            const, factors, bottom = split_product(out)
        except Exception as e:  # noqa: BLE001
            return [("unreadable", f"={out}: {e}")]
        v = gens[0]
        polys = []
        for ftxt, pw in factors:
            fp = sp.Poly(calc(ftxt), v)
            polys.append((fp, pw))
            if fp.degree() >= 1 and not fp.is_irreducible:
                fails.append(("factor-incomplete", f"={out}: factor {ftxt} still factors: "
                              f"{sp.factor(fp.as_expr())}"))
            if fp.degree() >= 1 and sp.gcd_list(fp.coeffs()) != 1:
                fails.append(("factor-incomplete", f"={out}: factor {ftxt} has a common number"))
        seen = [fp.monic() for fp, _ in polys]
        if len(set(seen)) != len(seen):
            fails.append(("factor-repeated", f"={out}: a factor appears twice instead of a power"))
        content, flist = sp.factor_list(expr, v)
        want = sum(m for f, m in flist if sp.Poly(f, v).degree() >= 1)
        got = sum(pw for fp, pw in polys if fp.degree() >= 1)
        if got != want and not any(t == "factor-incomplete" for t, _ in fails):
            fails.append(("factor-incomplete", f"={out}: {got} factors, sympy has {want}: "
                          f"{sp.factor(expr)}"))
        says_no = any("DOES NOT FACTOR" in m for m in msg)
        irreducible = len(flist) == 1 and flist[0][1] == 1 and abs(content) == 1
        if says_no and not irreducible:
            fails.append(("factor-says-no-but-factors",
                          f"says IT DOES NOT FACTOR, but {expr} = {sp.factor(expr)}"))
        if len(msg) not in (0, 4):
            fails.append(("line-mismatch", f"extra lines {msg}"))
        return fails
    # several letters, π, decimals or a constant: the common factor taken out
    if any("NOTHING TO TAKE OUT" in ln for ln in rest):
        P = sp.Poly(expr, *gens) if gens else None
        if gens:
            mono_gcd = P.terms_gcd()[0]
            cont = P.content() if P.domain.is_QQ or P.domain.is_ZZ else 1
            if any(mono_gcd) or (P.domain.is_QQ or P.domain.is_ZZ) and \
                    sp.Rational(sp.gcd_list([sp.Rational(c) for c in P.coeffs()])) != 1:
                fails.append(("factor-gcf-missed", f"says nothing to take out of {expr}"))
        return fails
    if not rest[0].startswith("="):
        return [("line-mismatch", f"{lines}")]
    out = rest[0][1:]
    try:
        F = calc(out)
    except Exception as e:  # noqa: BLE001
        return [("unreadable", f"={out}: {e}")]
    if not same(F, expr, letters, approx=has_decimal(out)):
        fails.append(("factor-wrong-answer", f"shows ={out}, but it is {expr}"))
        return fails
    if len(rest) != 1:
        fails.append(("line-mismatch", f"extra lines {rest[1:]}"))
    # what's left in the brackets shares nothing
    if out.endswith(")") and gens and not has_decimal(out):
        depth, j = 0, len(out) - 1
        while True:
            if out[j] == ")":
                depth += 1
            elif out[j] == "(":
                depth -= 1
                if depth == 0:
                    break
            j -= 1
        inner = calc(out[j + 1:-1])
        Pi = sp.Poly(inner, *gens)
        if any(Pi.terms_gcd()[0]):
            fails.append(("factor-gcf-incomplete", f"={out}: the bracket still shares letters"))
        coeffs = Pi.coeffs()
        if all(c.is_Rational for c in coeffs) and sp.gcd_list(coeffs) != 1:
            fails.append(("factor-gcf-incomplete", f"={out}: the bracket still shares a number"))
    return fails


def gen_fact(rng, n, degree_share):
    cases = []

    def add(text, expr, letters, **kw):
        cases.append(Case("fact", [text], rng.random() < degree_share, text=text, expr=expr,
                          letters=letters, **kw))

    fixed = ["X^2-9", "6X^2+X-2", "4X^2-12X+9", "X^5-X", "X^3-8", "X^4+5X^2+6", "X^2/2-2",
             "6AB+9A", "12X^2Y+18XY^2", "2piR^2+2piRH", "X^2+1", "X^3+27", "8X^3-125", "X^4-16",
             "X^6-1", "X^6+1", "X^4+1", "X^4+4", "X^2-2", "2X^2+3X+5", "X^3+X+1", "(X^3+2)(X^3+3)",
             "X^6+5X^3+6", "(X^3+X+1)(X^4+X+1)", "(X^2+X+1)(X^3+X+1)", "X^8-1", "(X-1)^5",
             "(2X-1)^4", "(3X+2)^3(X-1)^2", "X^2", "X", "X-1", "-X^2-1", "-2X^2+8", "(2/3)X^2-6",
             "X^2/4-X+1", "0.5X^2-2", "X^2-Y^2", "piX^2-piX", "6", "12", "2X+4Y+6Z",
             "X^2/2+XY/4", "-6AB-9A", "sqrt(2)X^2+sqrt(2)X", "(X^2+1)(X^2+2)(X^2+3)",
             "X^4-10X^2+1", "4X^4+1", "X^4+X^2+1", "(1000X-1)(X+1)", "(X+100)^3"]
    for t in fixed:
        e = calc(t)
        letters = "".join(sorted({c for c in t if c in LETTERS}, key=LETTERS.index))
        add(t, e, letters, kind="fixed")
    for t in ["1/X+1", "sqrt(X)", "1/(X+1)", "1/(X-100)", "sqrt(X+100)", "(X^2+1)/(X+60)",
              "X^150"]:
        add(t, 0, "X", expect="notpoly", kind="notpoly")
    for t in ["X^10+X+1", "(X-1)(X^10+X+1)", "X^12-1", "2X^11-2X", "(X+1)^10", "X^14+X"]:
        add(t, calc(t), "X", kind="highdeg")
    while len(cases) < n:
        kind = rng.choice(["lin", "lin", "linquad", "gcf", "squares", "cubes", "irred", "fracs",
                           "repeated", "multi", "multi", "big", "cubics", "quartic", "huge",
                           "quads"])
        v, name = X, "X"
        if rng.random() < 0.1:
            name = rng.choice("TAYZ")
            v = SYM[name]
        if kind in ("lin", "repeated", "big"):
            k = rng.randint(2, 4)
            lead, const = (12, 15) if kind == "big" else (5, 9)
            fs = [rand_linear(rng, v, lead, const) for _ in range(k)]
            if kind == "repeated":
                fs.append(fs[0])
                fs.append(rng.choice(fs))
            e = sp.expand(sp.Mul(*fs) * rng.choice([1, 1, 1, 2, 3, -1, 6]))
            if e == 0:
                continue
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "linquad":
            q = v ** 2 + rng.randint(-6, 6) * v + rng.randint(1, 15)
            fs = [rand_linear(rng, v, 4, 6) for _ in range(rng.randint(1, 2))] + [q]
            if rng.random() < 0.4:
                fs.append(rng.randint(1, 3) * v ** 2 + rng.randint(1, 9))
            e = sp.expand(sp.Mul(*fs))
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "gcf":
            k = rng.randint(2, 12)
            inner = rand_poly(rng, rng.randint(1, 4), name, 9)
            e = sp.expand(k * v ** rng.randint(0, 3) * inner)
            if e == 0 or sp.Poly(e, v).degree() < 1:
                continue
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "squares":
            a, b = rng.randint(1, 12), rng.randint(1, 15)
            e = (a * v) ** 2 - b * b
            if rng.random() < 0.3:
                e = a * a * v ** 4 - b * b
            add(poly_text(e, name, rng), sp.expand(e), name, kind=kind)
        elif kind == "cubes":
            a, b = rng.randint(1, 5), rng.randint(1, 6)
            e = a ** 3 * v ** 3 + rng.choice([1, -1]) * b ** 3
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "irred":
            e = rng.choice([v ** 2 + rng.randint(1, 20), v ** 2 - rng.choice([2, 3, 5, 6, 7, 10]),
                            v ** 2 + v + rng.randint(1, 9), 2 * v ** 2 + 3 * v + rng.randint(2, 9),
                            v ** 3 + v + rng.randint(1, 5), v ** 4 + rng.randint(1, 3) * v + 1])
            if not sp.Poly(e, v).is_irreducible:
                continue
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "fracs":
            fs = [rand_linear(rng, v, 4, 7) for _ in range(rng.randint(1, 3))]
            c = sp.Rational(rng.randint(1, 5) * rng.choice([1, -1]), rng.choice([2, 3, 4, 6, 10]))
            e = sp.expand(c * sp.Mul(*fs))
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "multi":
            names = rng.choice(["XY", "AB", "ABC", "XYZ", "MN", "RH"])
            gens = [SYM[c] for c in names]
            mono = rng.randint(1, 12)
            for g in gens:
                mono *= g ** rng.randint(0, 2)
            inner = rand_poly(rng, 2, names, 7)
            if rng.random() < 0.2:
                inner = sp.Rational(1, rng.choice([2, 3, 4])) * inner
            e = sp.expand(mono * inner)
            if e == 0 or len({c for c in str(e) if c in LETTERS}) < 2:
                continue
            letters = "".join(c for c in names if SYM[c] in e.free_symbols)
            add(poly_text(e, letters, rng), e, letters, kind=kind)
        elif kind == "cubics":
            c1 = v ** 3 + rng.randint(-3, 3) * v + rng.choice([1, 2, 3, 5, -2, 7])
            c2 = v ** 3 + rng.randint(-3, 3) * v ** 2 + rng.choice([1, 2, 3, -5, 7])
            if not (sp.Poly(c1, v).is_irreducible and sp.Poly(c2, v).is_irreducible):
                continue
            e = sp.expand(c1 * c2)
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "huge":
            fs = [rng.randint(1, 30) * v + rng.choice([-1, 1]) * rng.randint(1, 60)
                  for _ in range(rng.randint(3, 5))]
            e = sp.expand(sp.Mul(*fs))
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "quads":
            q1 = rng.randint(1, 5) * v ** 2 + rng.randint(-5, 5) * v + rng.randint(1, 12)
            q2 = rng.randint(1, 5) * v ** 2 + rng.randint(-5, 5) * v + rng.randint(1, 12)
            if not (sp.Poly(q1, v).is_irreducible and sp.Poly(q2, v).is_irreducible):
                continue
            e = sp.expand(q1 * q2 * (rand_linear(rng, v, 3, 5) if rng.random() < 0.4 else 1))
            add(poly_text(e, name, rng), e, name, kind=kind)
        elif kind == "quartic":
            q = v ** 4 + rng.randint(-3, 3) * v + rng.choice([1, 2, 3, -1])
            if not sp.Poly(q, v).is_irreducible:
                continue
            e = sp.expand(q * rand_linear(rng, v, 3, 5))
            add(poly_text(e, name, rng), e, name, kind=kind)
    return cases


# ============================================================ checking: DIVIDE

def check_div(case, res):
    fails = []
    info = case.info
    if info.get("expect") == "div0":
        if res.kind != "message" or not any("DIVIDE BY ZERO" in m for m in res.message):
            fails.append(("div0-not-caught", f"{res.kind}: {res.lines or res.message}"))
        return fails
    if info.get("expect") == "msg":
        if res.kind != "message":
            fails.append(("div-not-refused", f"{res.kind}: {res.lines}"))
        return fails
    if res.kind == "message":
        return [("div-refused", f"{res.message}")]
    if res.kind != "result":
        return [("div-" + res.kind, res.err)]
    top, bot = sp.expand(info["top"]), sp.expand(info["bottom"])
    v = SYM[info["letter"]]
    q, r = sp.div(sp.Poly(top, v), sp.Poly(bot, v))
    lines = res.lines
    if len(lines) < 3:
        return [("line-mismatch", f"{lines}")]
    mq = re.fullmatch(r"QUOTIENT: (.*)", lines[1])
    mr = re.fullmatch(r"REMAINDER: (.*)", lines[2])
    if not mq or not mr:
        return [("line-mismatch", f"{lines}")]
    try:
        gq, gr = calc(mq.group(1)), calc(mr.group(1))
    except Exception as e:  # noqa: BLE001
        return [("unreadable", f"{lines}: {e}")]
    approx = has_decimal(mq.group(1) + mr.group(1))
    if not same(gq, q.as_expr(), [v], approx) or not same(gr, r.as_expr(), [v], approx):
        fails.append(("div-wrong-answer", f"shows Q={mq.group(1)} R={mr.group(1)}, "
                      f"should be Q={q.as_expr()} R={r.as_expr()}"))
    if not approx and not same(sp.expand(gq * bot + gr), top):
        fails.append(("div-wrong-answer", "quotient*bottom+remainder != top"))
    if r.is_zero:
        if len(lines) != 3:
            fails.append(("line-mismatch", f"extra lines {lines[3:]}"))
    else:
        if len(lines) != 4 or not lines[3].startswith("SO IT IS "):
            fails.append(("line-mismatch", f"{lines[3:]}"))
        else:
            try:
                so = calc(lines[3][len("SO IT IS "):])
                if not same(so, top / bot, [v], approx=True):
                    fails.append(("div-wrong-answer", f"{lines[3]} != top/bottom"))
            except Exception as e:  # noqa: BLE001
                fails.append(("unreadable", f"{lines[3]}: {e}"))
            if lines[3].startswith("SO IT IS 0+"):
                fails.append(("div-cosmetic", f"{lines[3]!r}: '0+' in front"))
    return fails


def gen_div(rng, n, degree_share):
    cases = []

    def add(top_t, bot_t, top, bot, letter="X", **kw):
        cases.append(Case("div", [top_t, bot_t], rng.random() < degree_share, top=top, bottom=bot,
                          letter=letter, **kw))

    fixed = [("X^3-1", "X-1"), ("X^3+2X+5", "X^2+1"), ("X^2+1", "X^3"), ("6X^2+3X", "3"),
             ("X^4", "X^2+X+1"), ("2X^3-3X^2+X-5", "2X-1"), ("X^2/2+1", "X/3+1"),
             ("X^5-1", "X^2-1"), ("5", "X+1"), ("6", "3"), ("X^2-4", "X-2"), ("X", "X"),
             ("0", "X+1"), ("T^3+1", "T+1"), ("X^6", "X-1"), ("3X^2+2X+1", "0.5X+0.25")]
    for t, b in fixed:
        letter = "T" if "T" in t + b else "X"
        add(t, b, calc(t), calc(b), letter, kind="fixed")
    add("X^2+1", "0", 0, 0, expect="div0", kind="zero")
    add("X^2+1", "X-X", 0, 0, expect="div0", kind="zero")
    add("X^2+1", "Y+1", 0, 0, expect="msg", kind="letters")
    add("1/(X-100)", "X+1", 0, 0, expect="msg", kind="notpoly")
    add("sqrt(X+100)", "X+1", 0, 0, expect="msg", kind="notpoly")
    for t, b in [("X^12+1", "X+1"), ("X^11", "X^2+1"), ("X^14-1", "X-1"), ("(X+1)^10", "X"),
                 ("X^13+X", "2")]:
        add(t, b, calc(t), calc(b), "X", kind="highdeg")
    add("1/X+1", "X+1", 0, 0, expect="msg", kind="notpoly")
    add("X^2+1", "sqrt(X)", 0, 0, expect="msg", kind="notpoly")
    while len(cases) < n:
        kind = rng.choice(["rand", "rand", "exact", "const", "higher", "frac", "monic", "big"])
        name = "X" if rng.random() < 0.85 else rng.choice("TAZ")
        v = SYM[name]
        dens = (1,) if kind != "frac" else (1, 2, 3, 4, 5)
        if kind == "rand":
            top = rand_poly(rng, rng.randint(1, 6), name, 9)
            bot = rand_poly(rng, rng.randint(1, 3), name, 5)
        elif kind == "exact":
            bot = rand_poly(rng, rng.randint(1, 3), name, 4)
            top = sp.expand(bot * rand_poly(rng, rng.randint(0, 3), name, 5))
        elif kind == "const":
            top = rand_poly(rng, rng.randint(1, 5), name, 9)
            bot = sp.Integer(rng.choice([2, 3, -4, 5, 7, -1, 10]))
        elif kind == "higher":
            top = rand_poly(rng, rng.randint(0, 2), name, 9)
            bot = rand_poly(rng, rng.randint(3, 4), name, 5)
        elif kind == "frac":
            top = rand_poly(rng, rng.randint(1, 5), name, 9, dens)
            bot = rand_poly(rng, rng.randint(1, 2), name, 5, dens)
        elif kind == "monic":
            top = rand_poly(rng, rng.randint(2, 7), name, 9)
            bot = v + rng.randint(-9, 9)
        else:
            top = rand_poly(rng, rng.randint(3, 6), name, 99)
            bot = rand_poly(rng, rng.randint(1, 3), name, 12)
        if sp.expand(bot) == 0 or sp.expand(top) == 0:
            continue
        tt, bt = poly_text(top, name, rng), poly_text(bot, name, rng)
        if v not in (sp.expand(top) + sp.expand(bot)).free_symbols:
            continue
        add(tt, bt, sp.expand(top), sp.expand(bot), name, kind=kind)
    return cases


# ============================================================ checking: ROOTS / ZEROS

def check_roots(case, res):
    fails = []
    if case.info.get("kind") == "farpole":
        # not polynomials: ROOTS must refuse (FIND ZEROS would search)
        if res.kind == "result":
            fails.append(("roots-of-non-polynomial",
                          f"{case.inputs[0]} isn't a polynomial, but ROOTS shows {res.lines[1:]}"))
        return fails
    if res.kind != "result":
        return [("roots-" + res.kind, res.err or str(res.message))]
    p = sp.expand(case.info["expr"])
    v = X
    want = [complex(sp.N(z, 20)) for z in sp.Poly(p, v).all_roots()]
    got, mult = [], []
    lines = res.lines[1:]
    for ln in lines:
        if ln.startswith("FACTORED: "):
            try:
                if not same(calc(ln[10:]), p):
                    fails.append(("roots-wrong-factored", ln))
            except Exception as e:  # noqa: BLE001
                fails.append(("unreadable", f"{ln}: {e}"))
            continue
        if ln.startswith("X="):
            txt = ln[2:]
            k = 1
            for word, kk in ((" (DOUBLE)", 2), (" (TRIPLE)", 3)):
                if txt.endswith(word):
                    txt, k = txt[:-len(word)], kk
            mt = re.search(r" \((\d+) TIMES\)$", txt)
            if mt:
                txt, k = txt[:mt.start()], int(mt.group(1))
            txt = txt.split("=")[0]
            try:
                got += [complex(sp.N(calc(txt)))] * k
            except Exception as e:  # noqa: BLE001
                fails.append(("unreadable", f"{ln}: {e}"))
    if len(got) != len(want):
        return fails + [("roots-count", f"got {len(got)} roots, want {len(want)}: {lines}")]
    left = list(want)
    for z in got:
        best = min(range(len(left)), key=lambda i: abs(left[i] - z))
        tol = 1e-6 * (1 + abs(z)) if case.info.get("multiple") else 1e-8 * (1 + abs(z))
        if abs(left[best] - z) > tol:
            fails.append(("roots-wrong", f"root {z} not a root of {p} (nearest {left[best]})"))
        left.pop(best)
    return fails


def gen_roots(rng, n, degree_share):
    cases = []
    for t in ["(X-1)(X^10+X+1)", "(X+2)(X^11-X-1)", "X^12-1"]:
        cases.append(Case("roots", [t], False, expr=calc(t), multiple=False, kind="highdeg"))
    for t in ["sqrt(X+100)-10", "1/(X-60)-1", "1/(X+50)", "X^200-1"]:
        cases.append(Case(rng.choice(["roots"]), [t], False, expr=None, kind="farpole"))
    while len(cases) < n:
        kind = rng.choice(["lin", "quad", "mixed", "cubic"])
        fs = []
        if kind in ("lin", "mixed"):
            fs += [rand_linear(rng, X, 4, 7) for _ in range(rng.randint(1, 3))]
        if kind in ("quad", "mixed"):
            fs += [X ** 2 + rng.randint(-5, 5) * X + rng.randint(-6, 12)]
        if kind == "cubic":
            fs += [X ** 3 + rng.randint(-4, 4) * X + rng.randint(1, 9)]
        e = sp.expand(sp.Mul(*fs))
        if sp.Poly(e, X).degree() < 1:
            continue
        multiple = any(m > 1 for _, m in sp.factor_list(e)[1])
        mode = rng.choice(["roots", "zeros"])
        cases.append(Case(mode, [poly_text(e, "X", rng)], rng.random() < degree_share, expr=e,
                          multiple=multiple, kind=kind))
    return cases


# limitations (graceful, documented or by design): shown, but not failures
INFO = {"limit-refused", "limit-refused-misleading"}

CHECK = {"quad": check_quad, "simp": check_simp, "fact": check_fact, "div": check_div,
         "roots": check_roots, "zeros": check_roots}


def check(case, res):
    fails = []
    if res.kind == "error":
        fails.append(("ERR", res.err))
    elif res.kind == "stuck":
        fails.append(("stuck", res.err))
    else:
        try:
            fails += CHECK[case.mode](case, res)
        except Exception as e:  # noqa: BLE001
            fails.append(("checker-crash", f"{type(e).__name__}: {e}"))
    for prob in res.problems:
        fails.append(("screen/state", prob))
    return fails


# ============================================================ sessions

THROWAWAY = {"roots": ["X"], "quad": ["X^2"], "simp": ["X"], "fact": ["X^2"], "div": ["X", "1"],
             "zeros": ["X"]}


def run_session(cases, degree):
    """Run cases one after another on one machine, the way a student would."""
    m = fresh(degree)
    rec = Recorder(m)
    m.answer("2")
    m.answer(MODE_KEY[cases[0].mode])
    at = cases[0].mode
    out = []
    for i, case in enumerate(cases):
        if at != case.mode:
            if m.wait.kind == "input":           # still at the old mode's prompt
                r = drive(m, rec, at, THROWAWAY[at])
                if r.kind != "result":
                    out.append((case, [("session", f"throwaway run gave {r.kind}")]))
                    return out
            m.answer("MAIN MENU")
            m.answer("2")
            m.answer(MODE_KEY[case.mode])
            at = case.mode
        elif m.wait.kind == "menu":
            m.answer("AGAIN")
        res = drive(m, rec, case.mode, case.inputs)
        out.append((case, check(case, res)))
        if res.kind in ("error", "stuck"):
            return out
    if m.wait.kind == "input":
        drive(m, rec, at, THROWAWAY[at])
    m.answer("QUIT")
    if m.radians != (not degree) or m.leaks:
        out.append((cases[-1], [("session", "QUIT did not restore the mode / leaked")]))
    return out


# ============================================================ main

CASES = []


def work(i):
    """Run and check case i (in a worker process when -j > 1)."""
    t1 = time.time()
    res = run_case(CASES[i])
    return i, check(CASES[i], res), res.steps, time.time() - t1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=int, default=20261014)
    ap.add_argument("-n", type=int, default=0, help="cases per deep mode (default: full run)")
    ap.add_argument("--degree-share", type=float, default=0.35)
    ap.add_argument("--only", default="", help="comma list of modes: quad,simp,fact,div,roots")
    ap.add_argument("--raw-sim", action="store_true",
                    help="don't fix the simulator's binary fPart( (see patch_bcd_parts)")
    ap.add_argument("--source", help="build BRAINIAC from this file instead of brainiac/src")
    ap.add_argument("-j", "--jobs", type=int, default=2, help="worker processes (default 2)")
    args = ap.parse_args()
    load_tokens(args.source)
    if not args.raw_sim:
        patch_bcd_parts()
    rng = random.Random(args.seed)
    n = args.n
    plan = {"quad": n or 420, "simp": n or 430, "fact": n or 400, "div": n or 300,
            "roots": (n // 4) if n else 90}
    gens = {"quad": gen_quad, "simp": gen_simp, "fact": gen_fact, "div": gen_div, "roots": gen_roots}
    only = set(args.only.split(",")) if args.only else set(plan)
    cases = []
    for mode, count in plan.items():
        sub = random.Random(f"{args.seed}-{mode}")
        made = gens[mode](sub, count, args.degree_share)
        if mode in only:
            cases += made
    t0 = time.time()
    failures = []          # (case, tag, detail)
    by_mode = Counter()
    slow = []
    CASES[:] = cases
    results = [None] * len(cases)
    if args.jobs > 1:
        import multiprocessing as mp
        pool = mp.get_context("fork").Pool(args.jobs)
        it = pool.imap_unordered(work, range(len(cases)), chunksize=4)
    else:
        pool = None
        it = map(work, range(len(cases)))
    for done, (i, fs, steps, secs) in enumerate(it, 1):
        results[i] = (fs, steps, secs)
        if done % 250 == 0:
            print(f"  ... {done}/{len(cases)} cases, {time.time() - t0:.0f}s", flush=True)
    if pool:
        pool.close()
    for case, (fs, steps, secs) in zip(cases, results):
        slow.append((steps, secs, case))
        by_mode[case.mode] += 1
        for tag, detail in fs:
            failures.append((case, tag, detail))
    # sessions: a handful of long runs on one machine each. Only what differs
    # from the same case on a fresh machine is reported (state left over).
    fresh_tags = defaultdict(set)
    for case, tag, _ in failures:
        fresh_tags[id(case)].add(tag)
    srng = random.Random(f"{args.seed}-sessions")
    pool = [c for c in cases if c.mode in ("simp", "fact", "div", "quad")]
    chains = []
    for s in range(12):
        chains.append((srng.sample(pool, min(14, len(pool))), s % 2 == 1))

    def mk(mode, inputs, **info):
        return Case(mode, inputs, False, **info)
    frac = mk("simp", ["1/X+1"], text="1/X+1", expr=calc("1/X+1"), letters="X")
    chains += [
        ([frac, mk("fact", ["6"], text="6", expr=sp.Integer(6), letters="")], False),
        ([frac, mk("fact", ["12"], text="12", expr=sp.Integer(12), letters="")], True),
        ([frac, mk("div", ["5", "X+1"], top=sp.Integer(5), bottom=X + 1, letter="X")], False),
        ([frac, mk("div", ["6", "3"], top=sp.Integer(6), bottom=sp.Integer(3), letter="X")], False),
    ]
    nsess = 0
    for s, (chain, deg) in enumerate(chains):
        for case, fs in run_session(chain, deg):
            nsess += 1
            for tag, detail in fs:
                if tag not in fresh_tags.get(id(case), ()):
                    failures.append((case, "session:" + tag,
                                     detail + f" (on one machine after: "
                                     f"{[c.answers() for c in chain[:chain.index(case)]][-2:]})"))
    elapsed = time.time() - t0
    # report
    real = [f for f in failures if f[1].split(":")[-1] not in INFO]
    tags = Counter(tag for _, tag, _ in failures)
    bad = {id(c) for c, _, _ in real}
    print()
    print(f"POLYNOMIALS stress: {len(cases)} cases + {nsess} session steps, seed {args.seed}, "
          f"{elapsed:.0f}s" + ("" if args.raw_sim else " (simulator fPart( made decimal, like the TI)"))
    deg = sum(c.degree for c in cases)
    fail_mode = Counter(c.mode for c in cases if id(c) in bad)
    print(f"  Degree mode: {deg} cases;  per mode: " +
          ", ".join(f"{m} {by_mode[m]} ({fail_mode[m]} failing)" for m in by_mode))
    nbad = sum(1 for c in cases if id(c) in bad)
    print(f"  cases passing: {len(cases) - nbad}, failing: {nbad}")
    for tag, k in tags.most_common():
        note = "   (limitation, not counted)" if tag.split(":")[-1] in INFO else ""
        print(f"    {k:5d}  {tag}{note}")
    slow.sort(key=lambda t: -t[0])
    print("  most statements run (the real calculator does very roughly 1-3 thousand a second):")
    for steps, secs, case in slow[:6]:
        print(f"    {steps:8d} steps ({secs:.1f}s here)  {case.describe()}")
    print()
    grouped = defaultdict(list)
    for case, tag, detail in failures:
        grouped[tag].append((case, detail))
    for tag, items in sorted(grouped.items(), key=lambda kv: -len(kv[1])):
        print(f"== {tag} ({len(items)})")
        for case, detail in items:
            print(f"  {case.describe()}")
            print(f"      {detail}")
    return 1 if real else 0


if __name__ == "__main__":
    sys.exit(main())
