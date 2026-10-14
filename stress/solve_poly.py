"""Pressure test: polynomial equations in BRAINIAC.

    python stress/solve_poly.py            # everything (a few minutes)
    python stress/solve_poly.py --quick    # a small sample
    python stress/solve_poly.py --jobs 2   # worker processes (default 3)

Covers SOLVE FOR X (main menu 1) with polynomial equations, POLYNOMIALS >
ROOTS (2, 1) and POLYNOMIALS > FIND ZEROS (2, 6). Every case is generated
from a fixed seed, typed through keyboard_to_tokens like a student would,
run on a fresh simulated TI-84 Plus CE, and judged against sympy/mpmath:

  * the typed text is parsed independently (TI precedence rules) into an
    exact sympy polynomial; its roots with multiplicity are the oracle;
  * every root line the program prints (exact forms like (⁻5+𝑖√(55))/2,
    fractions like 16/3, decimals, (DOUBLE)/(TRIPLE) tags) is converted
    back to a number, exact forms at 40 digits (so "exact" really means
    exact), decimals at 1e-6 relative;
  * the multiset shown must equal the oracle's: nothing missing, nothing
    extra; real roots must not be shown with an imaginary part;
  * an exact form and the decimal printed under it must agree;
  * FACTORED: must expand back to (left side)-(right side) exactly, and
    each factor should be irreducible over the rationals;
  * no ERR, leaks == 0, blocks == [], every printed line fits its page,
    and the program ends on the WHAT NEXT? menu;
  * a subset runs again in Degree mode (must print the same thing), and a
    subset runs back to back through AGAIN (must print what a fresh run
    prints).

Exit code 0 means every case passed. Each failure is printed with its
exact answer sequence from a fresh start.
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
from fractions import Fraction

# BRAINIAC_ROOT=/path/to/a/patched/checkout runs the cases against that copy
ROOT = os.environ.get("BRAINIAC_ROOT") or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

import mpmath  # noqa: E402
import sympy  # noqa: E402

mpmath.mp.dps = 50

SEED = 20261014

# ============================================================== the machine

_TOK = None


def tokens():
    global _TOK
    if _TOK is None:
        from brainiac.build import PROGRAMS, program_bytes
        from brainiac.tibasic import tokens_from_bytes
        _TOK = {n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}
    return _TOK


def fresh(degree=False):
    from brainiac.tibasic import Machine
    tok = tokens()
    m = Machine(tok["BRAINIAC"])
    m.programs.update({k: v for k, v in tok.items() if k != "BRAINIAC"})
    m.radians = not degree
    m.run()
    return m


MENU_PATH = {"solve": ["1"], "roots": ["2", "1"], "zeros": ["2", "6"]}


@dataclass
class Run:
    lines: list = field(default_factory=list)       # logical lines (one Output( each)
    problems: list = field(default_factory=list)    # (category, detail)
    error: str = ""
    final_menu: str = ""
    pages: list = field(default_factory=list)       # screen text at every pause
    final_input: str = ""                           # back at a prompt instead of a menu


def capture(m):
    log = []
    scr = m.screen
    orig = scr.output

    def out(row, col, text):
        log.append((row, col, text))
        return orig(row, col, text)
    scr.output = out
    return log


def drive(m, log, answers, run: Run):
    """Give the answers, then page through the results to the WHAT NEXT? menu."""
    for a in answers:
        if m.wait.kind not in ("menu", "input", "pause"):
            run.error = f"program stopped early ({m.wait}) before answer {a!r}"
            return
        m.answer(a)
        if m.wait.kind == "error":
            run.error = f"{m.wait.message} at: {m.statement_text()}"
            return
    from brainiac.screen import view
    pages = 0
    while m.wait.kind == "pause":
        pages += 1
        if pages > 80:
            run.error = "more than 80 pages"
            return
        run.pages.append([ln.rstrip() for ln in view(m)])
        m.answer("")
        if m.wait.kind == "error":
            run.error = f"{m.wait.message} at: {m.statement_text()}"
            return
    if m.wait.kind == "input":
        run.final_input = m.wait.prompt
        return
    if m.wait.kind != "menu":
        run.error = f"ended on {m.wait} instead of a menu"
        return
    run.final_menu = m.wait.title


def logical_lines(log, run: Run):
    """The text lines the program printed, and a check that each fits its page."""
    out = []
    for row, col, text in log:
        if text.startswith("THINKING"):
            continue
        if row == 10 and text.startswith("ENTER:"):
            continue
        rows_used = max(1, math.ceil(len(text) / 26))
        if col != 1 or row + rows_used - 1 > 9:
            run.problems.append(("layout", f"line {text!r} at row {row} runs past row 9"))
        out.append(text)
    return out


def run_answers(answers, degree=False):
    run = Run()
    m = fresh(degree)
    log = capture(m)
    drive(m, log, answers, run)
    run.lines = logical_lines(log, run)
    if not run.error:
        if m.leaks:
            run.problems.append(("leak", f"leaks={m.leaks}"))
        if m.blocks:
            run.problems.append(("leak", f"blocks left open: {m.blocks}"))
    return run


# ============================================================== reading TI text

TI_MULTI = ["³√(", "√(", "abs(", "sin(", "cos(", "tan(", "𝑒^("]
PI_SYM = sympy.Symbol("PI")      # π kept symbolic so polynomials stay polynomials


class TIParse:
    """TI-84 precedence: postfix ² ³ > ^ > negation > * / implicit (left to
    right, same level) > + -. Closing brackets may be left off at the end."""

    def __init__(self, text, degree=False):
        self.toks = self.lex(text)
        self.i = 0
        self.degree = degree

    @staticmethod
    def lex(text):
        out, i = [], 0
        while i < len(text):
            for g in TI_MULTI:
                if text.startswith(g, i):
                    out.append(g)
                    i += len(g)
                    break
            else:
                m = re.match(r"(\d+\.?\d*|\.\d+)(ᴇ⁻?\d+)?", text[i:])
                if m:
                    out.append(("num", m.group(0)))
                    i += len(m.group(0))
                    continue
                ch = text[i]
                if ch == " ":
                    i += 1
                    continue
                out.append(ch)
                i += 1
        return out

    def peek(self):
        return self.toks[self.i] if self.i < len(self.toks) else None

    def take(self):
        t = self.toks[self.i]
        self.i += 1
        return t

    def parse(self):
        e = self.expr()
        if self.peek() == ")":
            self.take()
        if self.peek() is not None:
            raise ValueError(f"oracle parser: unexpected {self.peek()!r}")
        return e

    def expr(self):
        e = self.term()
        while self.peek() in ("+", "-"):
            op = self.take()
            r = self.term()
            e = e + r if op == "+" else e - r
        return e

    def starts_atom(self, t):
        return (isinstance(t, tuple) or t in TI_MULTI or t in ("(", "π", "𝑒", "𝑖", "⁻")
                or (isinstance(t, str) and (t.isalpha() and t.isupper() or t == "θ")))

    def term(self):
        e = self.neg()
        while True:
            t = self.peek()
            if t in ("*", "/"):
                self.take()
                r = self.neg()
                e = e * r if t == "*" else e / r
            elif t is not None and self.starts_atom(t):
                e = e * self.neg()
            else:
                return e

    def neg(self):
        if self.peek() == "⁻":
            self.take()
            return -self.neg()
        return self.power()

    def power(self):
        e = self.postfix()
        while self.peek() == "^":
            self.take()
            if self.peek() == "⁻":
                self.take()
                r = -self.postfix()
            else:
                r = self.postfix()
            e = e ** r
        return e

    def postfix(self):
        e = self.atom()
        while self.peek() in ("²", "³"):
            e = e ** (2 if self.take() == "²" else 3)
        return e

    def close(self):
        if self.peek() == ")":
            self.take()

    def atom(self):
        t = self.take()
        if isinstance(t, tuple):
            s = t[1].replace("ᴇ⁻", "e-").replace("ᴇ", "e")
            return sympy.Rational(s) if "e" not in s else sympy.Rational(sympy.Float(s, 30))
        if t == "(":
            e = self.expr()
            self.close()
            return e
        if t == "π":
            return PI_SYM
        if t == "𝑒":
            return sympy.E
        if t == "𝑖":
            return sympy.I
        if t in TI_MULTI:
            e = self.expr()
            self.close()
            if t == "√(":
                return sympy.sqrt(e)
            if t == "³√(":
                return sympy.real_root(e, 3)
            if t == "abs(":
                return sympy.Abs(e)
            if t == "𝑒^(":
                return sympy.exp(e)
            k = sympy.pi / 180 if self.degree else 1
            e = e.subs(PI_SYM, sympy.pi)
            return {"sin(": sympy.sin, "cos(": sympy.cos, "tan(": sympy.tan}[t](e * k)
        if isinstance(t, str) and (t.isalpha() or t == "θ"):
            return sympy.Symbol(t)
        raise ValueError(f"oracle parser: can't read {t!r}")


def ti_sympy(text, degree=False):
    return TIParse(text, degree).parse()


# ---- what the program prints, as numbers

NUM_RE = re.compile(r"(\d+\.?\d*|\.\d+)(ᴇ⁻?\d+)?")


def shown_value(text):
    """A printed answer like (⁻5+𝑖√(55))/2 or ⁻.5+.866𝑖 or 2.4ᴇ⁻6 -> mpc."""
    py, i, prev_atom = [], 0, False
    while i < len(text):
        m = NUM_RE.match(text, i)
        if m:
            s = m.group(0).replace("ᴇ⁻", "e-").replace("ᴇ", "e")
            if prev_atom:
                py.append("*")
            py.append(f"mpf('{s}')")
            i = m.end()
            prev_atom = True
            continue
        if text.startswith("√(", i):
            if prev_atom:
                py.append("*")
            py.append("sqrt(")
            i += 2
            prev_atom = False
            continue
        ch = text[i]
        i += 1
        if ch == "𝑖":
            if prev_atom:
                py.append("*")
            py.append("I")
            prev_atom = True
        elif ch == "π":
            if prev_atom:
                py.append("*")
            py.append("pi")
            prev_atom = True
        elif ch == "(":
            if prev_atom:
                py.append("*")
            py.append("(")
            prev_atom = False
        elif ch == ")":
            py.append(")")
            prev_atom = True
        elif ch == "⁻":
            if prev_atom:
                py.append("*")
            py.append("(-1)*")
            prev_atom = False
        elif ch in "+-/":
            py.append(ch)
            prev_atom = False
        else:
            raise ValueError(f"can't read {ch!r} in {text!r}")
    env = {"mpf": mpmath.mpf, "sqrt": mpmath.sqrt, "I": mpmath.mpc(0, 1), "pi": mpmath.pi}
    return mpmath.mpc(eval("".join(py), {"__builtins__": {}}, env))


# ============================================================== the oracle

@dataclass
class Oracle:
    kind: str                     # "poly", "all" (identity), "none" (never 0)
    roots: list = field(default_factory=list)      # mpc, with repeats
    poly: object = None           # sympy expr in the letter (and PI)
    letter: str = "X"
    irreducible_quads: int = 0    # rational quadratic factors (exact surd roots expected)
    has_pi: bool = False
    rational: bool = True
    mults: dict = field(default_factory=dict)


def num_roots(coeffs):
    """mpmath roots of a square-free polynomial, highest power first."""
    cs = [mpmath.mpc(c) for c in coeffs]
    while abs(cs[0]) == 0:
        cs.pop(0)
    if len(cs) == 2:
        return [-cs[1] / cs[0]]
    return list(mpmath.polyroots(cs, maxsteps=400, extraprec=400))


def oracle_for(expr, letter):
    x = sympy.Symbol(letter)
    expr = sympy.expand(expr)
    o = Oracle("poly", poly=expr, letter=letter)
    if expr == 0:
        o.kind = "all"
        return o
    if not expr.has(x):
        o.kind = "none"
        return o
    o.has_pi = expr.has(PI_SYM)
    poly = sympy.Poly(expr, x)
    o.rational = all(c.is_rational for c in poly.all_coeffs())
    gens_ok = expr.free_symbols <= {x, PI_SYM} and all(
        (c.is_rational or (c.free_symbols <= {PI_SYM} and sympy.Poly(c, PI_SYM).domain in (sympy.ZZ, sympy.QQ)))
        for c in sympy.Poly(expr, x).all_coeffs())
    if gens_ok:
        _, facs = sympy.factor_list(expr)
        for f, m in facs:
            if not f.has(x):
                continue
            fp = sympy.Poly(f, x)
            if fp.degree() == 2 and not f.has(PI_SYM):
                o.irreducible_quads += m
            cs = [mpmath.mpf(sympy.N(c.subs(PI_SYM, sympy.pi), 60)) if c.is_real is not False
                  else mpmath.mpc(complex(sympy.N(c, 60))) for c in fp.all_coeffs()]
            if fp.degree() <= 2 and not f.has(PI_SYM):
                rs = [mpmath.mpc(sympy.N(r, 60)) for r in sympy.roots(fp, multiple=True)]
            else:
                rs = num_roots(cs)
            for r in rs:
                o.roots += [r] * m
                o.mults[len(o.mults)] = m
    else:
        # constants like √(2) or 𝑖: sympy.roots with multiplicity
        e2 = expr.subs(PI_SYM, sympy.pi)
        rd = sympy.roots(sympy.Poly(e2, x))
        if sum(rd.values()) != sympy.degree(e2, x):
            cs = [complex(sympy.N(c, 40)) for c in sympy.Poly(e2, x).all_coeffs()]
            rd = {}
            for r in num_roots(cs):
                rd[r] = 1
            o.roots = list(rd.keys())
        else:
            for r, m in rd.items():
                o.roots += [mpmath.mpc(complex(sympy.N(r, 50)))] * m
    return o


# ============================================================== judging

TAG_RE = re.compile(r" \((DOUBLE|TRIPLE|\d+ TIMES)\)$")
VERDICTS = [" NO: FAILS CHECK", " NO: BOTTOM=0", " NOT REAL"]


@dataclass
class Shown:
    text: str
    value: object
    exact: bool
    mult: int
    decimal: object = None      # the " =..." line or the =decimal after a fraction
    verdict: str = ""


def is_exact_text(t):
    """Fractions and square-root forms claim to be exact (a ᴇ inside a √(
    means a rounded number got into an exact form, which the check catches)."""
    return "√" in t or ("/" in t and "." not in t)


def parse_output(lines, header, letter, case, problems):
    """-> (factored text or None, [Shown], special) or None if unreadable."""
    if not lines:
        problems.append(("output", "nothing printed"))
        return None
    body = lines
    if lines[0] in header:
        body = lines[1:]
    else:
        problems.append(("no-header", f"first line {lines[0]!r}, expected {header[0]!r}"))
    factored = None
    shown = []
    special = []
    steps = False          # the radical / abs working (SQUARE BOTH SIDES, CASE 1: ...)
    after_power = False
    for ln in body:
        if after_power:
            after_power = False
            special.append("step:" + ln)
            continue
        if ln in ("SQUARE BOTH SIDES:", "CUBE BOTH SIDES:"):
            steps = after_power = True
            special.append("step:" + ln)
            continue
        if re.match(r"CASE \d: ", ln):
            steps = True
            special.append("step:" + ln)
            continue
        if ln.startswith("FACTORED: "):
            if (factored is not None or shown) and not steps:
                problems.append(("output", f"FACTORED line out of place: {ln!r}"))
            if factored is None:
                factored = ln[len("FACTORED: "):]
            continue
        if ln.startswith(" ="):
            if not shown or not shown[-1].exact or shown[-1].decimal is not None:
                problems.append(("output", f"stray decimal line {ln!r}"))
                continue
            shown[-1].decimal = ln[2:]
            continue
        if ln.startswith(letter + "="):
            t = ln[len(letter) + 1:]
            verdict = ""
            for v in VERDICTS:
                if t.endswith(v):
                    verdict = v.strip()
                    t = t[: -len(v)]
            mult = 1
            mt = TAG_RE.search(t)
            if mt:
                tag = mt.group(1)
                mult = {"DOUBLE": 2, "TRIPLE": 3}.get(tag) or int(tag.split()[0])
                t = t[: mt.start()]
            dec = None
            if "=" in t:
                t, dec = t.split("=", 1)
            try:
                val = shown_value(t)
            except Exception as e:  # noqa: BLE001
                problems.append(("output", f"can't read answer {ln!r}: {e}"))
                continue
            sh = Shown(t, val, is_exact_text(t), mult, dec, verdict)
            if dec is not None and not sh.exact:
                problems.append(("output", f"'=decimal' after a non-exact answer: {ln!r}"))
            shown.append(sh)
            continue
        special.append(ln)
    return factored, shown, special


def close(a, b, tol):
    return abs(a - b) <= tol


def judge(case, run: Run):
    """Fill run.problems with everything wrong; the oracle comes from the typed text."""
    from brainiac.tibasic import glyphs, keyboard_to_tokens
    problems = run.problems
    if run.error:
        problems.append(("ERR", run.error))
        return
    if run.final_input:
        shown_text = [ln for pg in run.pages for ln in pg if ln.strip()]
        problems.append(("rejected", f"refused and went back to {run.final_input!r}: {shown_text}"))
        return
    if run.final_menu != "WHAT NEXT?":
        problems.append(("flow", f"ended on menu {run.final_menu!r}"))
    typed = glyphs(keyboard_to_tokens(case.text))
    if case.mode == "solve":
        left, eq, right = typed.partition("=")
        header = ["SOLVING: " + typed + ("" if eq else "=0")]
    else:
        left, eq, right = typed, "", ""
        header = ["ROOTS OF " + typed, "ZEROS OF " + typed]
    lx = ti_sympy(left, case.degree)
    rx = ti_sympy(right, case.degree) if eq else sympy.Integer(0)
    expr = sympy.expand(lx - rx)
    letter = case.letter
    if case.expect_expr is not None:
        diff = sympy.simplify(expr - case.expect_expr)
        if diff != 0:
            problems.append(("HARNESS", f"generator/parser disagree: {expr} vs {case.expect_expr}"))
            return
    o = oracle_for(expr, letter)
    parsed = parse_output(run.lines, header, letter, case, problems)
    if parsed is None:
        return
    factored, shown, special = parsed
    if any(ln.startswith("(SEARCHED") for ln in special):
        problems.append(("not-poly", "searched numerically instead of solving the polynomial"))
        # still compare what it found, real roots only
    if o.kind == "all":
        want = [f"EVERY {letter} WORKS! BOTH SIDES", "ARE ALWAYS EQUAL."]
        if special != want or shown or factored:
            problems.append(("wrong", f"identity: expected {want}, got {run.lines[1:]}"))
        return
    if o.kind == "none":
        want = (["NO ANSWER: THE TWO SIDES", "ARE NEVER EQUAL."] if case.mode == "solve"
                else ["NO ZEROS: IT IS NEVER 0."])
        if special != want or shown or factored:
            problems.append(("wrong", f"contradiction: expected {want}, got {run.lines[1:]}"))
        return
    extra_special = [s for s in special if not s.startswith(("(SEARCHED", "step:"))]
    if extra_special:
        problems.append(("output", f"unexpected lines {extra_special}"))

    # ---- the roots, with multiplicity
    want = list(o.roots)
    scale = max([abs(r) for r in want] + [mpmath.mpf(1)])
    got = []
    for sh in shown:
        got += [sh] * sh.mult
        if sh.verdict:
            problems.append(("wrong", f"verdict {sh.verdict!r} on {sh.text!r} for a polynomial"))
    # exact forms: exact
    for sh in shown:
        if sh.exact:
            ok = any(close(sh.value, r, mpmath.mpf(10) ** -30 * max(1, abs(r))) for r in want)
            if not ok:
                near = min(want, key=lambda r: abs(r - sh.value)) if want else None
                cat = "wrong-exact"
                if near is not None and abs(near - sh.value) <= mpmath.mpf("1e-9") * max(1, abs(near)):
                    cat = "soft:exact-snapped-near-miss"     # beyond the 10 digits shown
                problems.append((cat, f"{letter}={sh.text} is not exactly a root"
                                 + (f" (nearest {mpmath.nstr(near, 15)}, off by "
                                    f"{mpmath.nstr(abs(near - sh.value), 3)})" if near is not None else "")))
        if sh.decimal is not None:
            try:
                dv = shown_value(sh.decimal)
            except Exception as e:  # noqa: BLE001
                problems.append(("output", f"can't read decimal {sh.decimal!r}: {e}"))
                continue
            if not close(dv, sh.value, mpmath.mpf("1e-9") * max(1, abs(sh.value))
                         + mpmath.mpf("1e-12") * scale):
                problems.append(("wrong-decimal", f"{sh.text} printed as {sh.decimal} "
                                 f"(it is {mpmath.nstr(sh.value, 12)})"))
    seen = Counter(sh.text for sh in shown if sh.exact)
    for t, n in seen.items():
        if n > 1:
            problems.append(("cosmetic:root-listed-twice",
                             f"{letter}={t} printed on {n} lines instead of one with (DOUBLE)/(TRIPLE)"))
    # match shown answers to roots, nearest first
    pairs = []
    for i, sh in enumerate(got):
        for j, r in enumerate(want):
            pairs.append((abs(sh.value - r), i, j))
    pairs.sort(key=lambda p: p[0])
    used_i, used_j = set(), set()
    matched = {}
    for d, i, j in pairs:
        if i in used_i or j in used_j:
            continue
        r = want[j]
        tol = mpmath.mpf("1e-6") * abs(r) + mpmath.mpf("1e-11") * scale
        if d <= tol:
            used_i.add(i)
            used_j.add(j)
            matched[i] = j
    missing = [want[j] for j in range(len(want)) if j not in used_j]
    extra = [got[i] for i in range(len(got)) if i not in used_i]
    if missing or extra:
        mult_root = len(want) != len({mpmath.nstr(r, 8) for r in want})
        cat = "wrong"
        if not extra and missing and all(abs(r) == 0 for r in missing):
            cat = "zero-multiplicity"
        elif mult_root and not o.rational and len(got) == len(want):
            cat = "decimal-multiple-root"
        problems.append((cat, "missing " + (", ".join(mpmath.nstr(r, 10) for r in missing) or "-")
                         + "; extra " + (", ".join(f"{letter}={s.text}" for s in extra) or "-")))
    # realness: a real root must not come out with an 𝑖, nor a complex one without
    for i, j in matched.items():
        sh, r = got[i], want[j]
        r_real = abs(r.imag) <= mpmath.mpf("1e-25") * max(1, abs(r))
        s_real = "𝑖" not in sh.text
        if r_real and not s_real:
            problems.append(("spurious-imag", f"real root {mpmath.nstr(r.real, 10)} shown as "
                             f"{letter}={sh.text}"))
        elif not r_real and s_real and abs(r.imag) > mpmath.mpf("1e-6") * abs(r):
            problems.append(("wrong", f"complex root {mpmath.nstr(r, 10)} shown as real {sh.text}"))
    # ---- exact surd roots expected when the polynomial is rational and has
    # irreducible quadratic factors (a soft check: decimals are still right)
    if o.rational and o.irreducible_quads and not any("√" in s.text or "𝑖" in s.text and s.exact
                                                       for s in shown):
        if not any(s.exact and "√" in s.text for s in shown) and not any(
                s.text in ("𝑖", "⁻𝑖") or re.fullmatch(r"⁻?\d*𝑖", s.text) for s in shown):
            problems.append(("soft:no-exact-quadratic",
                             f"{o.irreducible_quads} rational quadratic factor(s) but no exact form"))
    # ---- FACTORED
    if factored is not None:
        try:
            fx = ti_sympy(factored)
        except Exception as e:  # noqa: BLE001
            problems.append(("output", f"can't read FACTORED {factored!r}: {e}"))
            return
        bracketed = re.findall(r"\(([^()]*)\)", factored)
        dup = [b for b, n in Counter(bracketed).items() if n > 1]
        if dup:
            problems.append(("cosmetic:repeated-factor-not-merged",
                             f"FACTORED: {factored} lists ({dup[0]}) more than once"))
        if sympy.expand(fx - expr) != 0:
            ratio = sympy.simplify(fx / expr)
            if ratio.free_symbols:
                problems.append(("wrong-factored", f"FACTORED: {factored} is not {sympy.expand(expr)}"))
            else:
                problems.append(("factored-constant", f"FACTORED: {factored} is {ratio} times "
                                 f"{sympy.expand(expr)}"))
        else:
            # every factor irreducible over Q
            x = sympy.Symbol(letter)
            for f, _m in sympy.factor_list(fx)[1]:
                pass
            shown_factors = re.findall(r"\(([^()]*)\)", factored)
            for sf in shown_factors:
                try:
                    fe = ti_sympy(sf)
                except Exception:  # noqa: BLE001
                    continue
                if fe.has(x) and sympy.degree(fe, x) >= 2:
                    _, fl = sympy.factor_list(fe)
                    if sum(m for f, m in fl if f.has(x)) > 1:
                        problems.append(("soft:incomplete-factor",
                                         f"factor ({sf}) splits further: {sympy.factor(fe)}"))


# ============================================================== cases

@dataclass
class Case:
    cid: str
    group: str
    mode: str                 # solve | roots | zeros
    text: str                 # what the student types (keyboard form)
    letter: str = "X"
    degree: bool = False
    expect_expr: object = None    # left-right as the generator meant it (sanity)
    nasty: bool = False

    def __post_init__(self):
        from brainiac.tibasic import glyphs, keyboard_to_tokens
        letters = sorted(set(re.findall(r"[A-Zθ]", glyphs(keyboard_to_tokens(self.text)))))
        if len(letters) == 1:
            self.letter = letters[0]

    def answers(self):
        return MENU_PATH[self.mode] + [self.text]

    def describe(self):
        mode = "Degree" if self.degree else "Radian"
        return f"[{mode}] answers: " + " | ".join(repr(a) for a in self.answers())


X = sympy.Symbol("X")


def _rat_str(c: Fraction, rng, allow_decimal=True):
    """A positive rational as the student might type it (no sign)."""
    if c.denominator == 1:
        return str(c.numerator)
    d = c.denominator
    terminating = all(p in (2, 5) for p in sympy.factorint(d))
    if allow_decimal and terminating and rng.random() < 0.6:
        from decimal import Decimal
        s = format(Decimal(c.numerator) / Decimal(c.denominator), "f")   # exact: it terminates
        if "." in s:
            s = s.rstrip("0").rstrip(".")
        if s.startswith("0.") and rng.random() < 0.5:
            s = s[1:]
        return s
    return f"{c.numerator}/{c.denominator}"


def _mono(letter, n, rng):
    if n == 0:
        return ""
    if n == 1:
        return letter
    if n == 2:
        return letter + rng.choice(["^2", "²"])
    if n == 3:
        return letter + rng.choice(["^3", "³"])
    return f"{letter}^{n}"


def render_expanded(coeffs, letter, rng, first_sign=True):
    """coeffs: {power: Fraction}. -> text like 2X^2-3X/4+1 (or '0')."""
    terms = [(n, c) for n, c in sorted(coeffs.items(), reverse=True) if c != 0]
    if rng.random() < 0.15 and len(terms) > 1:
        rng.shuffle(terms)
    if not terms:
        return "0"
    out = ""
    for k, (n, c) in enumerate(terms):
        neg = c < 0
        a = abs(c)
        sign = "-" if neg else ("+" if k else "")
        mono = _mono(letter, n, rng)
        if a == 1 and n > 0:
            body = mono
        elif a.denominator == 1 or n == 0:
            body = _rat_str(a, rng) + mono
        else:
            style = rng.random()
            if style < 0.35:
                body = _rat_str(a, rng) + mono                       # 3/4X^2 or .75X^2
            elif style < 0.7 and a.numerator == 1:
                body = f"{mono}/{a.denominator}"                     # X^2/4
            elif style < 0.7:
                body = f"{a.numerator}{mono}/{a.denominator}"        # 3X^2/4
            else:
                body = f"({_rat_str(a, rng)}){mono}"                 # (3/4)X^2
        out += sign + body
    return out


def poly_coeffs(p, letter="X"):
    x = sympy.Symbol(letter)
    P = sympy.Poly(sympy.expand(p), x)
    out = {}
    for (n,), c in P.terms():
        out[n] = Fraction(int(sympy.fraction(c)[0]), int(sympy.fraction(c)[1]))
    return out


def render_factor(f, letter, rng):
    """An integer-coefficient factor as text, bracketed unless it is a lone letter."""
    x = sympy.Symbol(letter)
    cs = poly_coeffs(f, letter)
    if cs == {1: Fraction(1)}:
        return letter, False
    body = render_expanded(cs, letter, rng)
    return "(" + body + ")", True


def render_power(text, m, rng, bracketed):
    if m == 1:
        return text
    if m == 2:
        return text + rng.choice(["^2", "²"])
    if m == 3:
        return text + rng.choice(["^3", "³"])
    return text + f"^{m}"


def render_const(k: Fraction, rng):
    """Leading number in front of brackets: '', '-', '2', '(1/2)', '2.5'."""
    if k == 1:
        return ""
    if k == -1:
        return "-"
    neg = "-" if k < 0 else ""
    a = abs(k)
    if a.denominator == 1:
        return neg + str(a.numerator)
    s = _rat_str(a, rng)
    if "/" in s:
        return neg + rng.choice([f"({s})", s])
    return neg + s


def render_factored(k, factors, letter, rng):
    parts = []
    for f, m in factors:
        t, br = render_factor(f, letter, rng)
        parts.append(render_power(t, m, rng, br))
    if rng.random() < 0.3:
        rng.shuffle(parts)
    # a lone letter right after a number reads fine (2X(X-1)); keep it first
    lone = [p for p in parts if not p.startswith("(")]
    rest = [p for p in parts if p.startswith("(")]
    parts = lone + rest
    c = render_const(k, rng)
    body = "".join(parts)
    if c and "/" in c and not c.startswith("("):
        c = "(" + c + ")" if rng.random() < 0.5 else c
    return c + body


def render_horner(coeffs, letter, rng):
    n = max(coeffs)
    cs = [coeffs.get(i, Fraction(0)) for i in range(n, -1, -1)]

    def num(c, first):
        s = "-" if c < 0 else ("" if first else "+")
        a = abs(c)
        return s + _rat_str(a, rng, allow_decimal=True)
    # ((aX+b)X+c)X+d
    text = ("-" if cs[0] < 0 else "") + ("" if abs(cs[0]) == 1 else _rat_str(abs(cs[0]), rng)) + letter
    for i, c in enumerate(cs[1:], 1):
        if c != 0:
            text += num(c, False)
        if i < n:
            text = "(" + text + ")" + letter
    return text


def random_factor(rng, room):
    """A factor with integer coefficients, and its degree."""
    kinds = ["int"] * 6 + ["frac"] * 3 + ["zero"] + ["irr2"] * 2 + ["cplx2"] * 2
    if room >= 3:
        kinds += ["cubic"]
    if room >= 4:
        kinds += ["quartic"]
    k = rng.choice([t for t in kinds if {"irr2": 2, "cplx2": 2, "cubic": 3, "quartic": 4}.get(t, 1) <= room])
    if k == "int":
        r = rng.randint(-12, 12)
        return X - r, 1
    if k == "zero":
        return X, 1
    if k == "frac":
        while True:
            q = rng.randint(2, 9)
            p = rng.randint(-15, 15)
            if p and math.gcd(p, q) == 1:
                return q * X - p, 1
    if k == "irr2":
        c = rng.random()
        if c < 0.3:
            n = rng.choice([2, 3, 5, 6, 7, 8, 10, 11, 12, 13, 18, 20, 27, 50])
            return X**2 - n, 2
        if c < 0.45:
            return rng.choice([X**2 - X - 1, X**2 + X - 1, X**2 - 2*X - 1, X**2 - 4*X + 1]), 2
        while True:
            a, b, cc = rng.randint(1, 5), rng.randint(-9, 9), rng.randint(-9, 9)
            d = b*b - 4*a*cc
            if d > 0 and int(math.isqrt(d))**2 != d and math.gcd(math.gcd(a, abs(b)), abs(cc)) == 1:
                return a*X**2 + b*X + cc, 2
    if k == "cplx2":
        c = rng.random()
        if c < 0.4:
            return rng.choice([X**2 + X + 1, X**2 + 1, X**2 + 4, X**2 - X + 1, X**2 + 2*X + 5,
                               X**2 + 3, X**2 + 2]), 2
        while True:
            a, b, cc = rng.randint(1, 5), rng.randint(-9, 9), rng.randint(1, 15)
            d = b*b - 4*a*cc
            if d < 0 and math.gcd(math.gcd(a, abs(b)), cc) == 1:
                return a*X**2 + b*X + cc, 2
    if k == "cubic":
        return rng.choice([X**3 - 2, X**3 - X - 1, X**3 + X + 1, X**3 - 3*X + 1, X**3 - 5,
                           2*X**3 - 3, X**3 + 2*X**2 - 1 - X, X**3 - 7*X - 7]), 3
    return rng.choice([X**4 + 1, X**4 - 2, X**4 + X + 1, X**4 - 10*X**2 + 1, X**4 + 2]), 4


def random_poly(rng, deg_target):
    factors = []
    room = deg_target
    while room > 0:
        f, d = random_factor(rng, room)
        m = 1
        if room >= 2 * d and rng.random() < 0.22:
            m = 2
        if room >= 3 * d and rng.random() < 0.08:
            m = 3
        factors.append((f, m))
        room -= d * m
    # merge identical factors
    merged = {}
    for f, m in factors:
        key = sympy.expand(f)
        merged[key] = merged.get(key, 0) + m
    factors = list(merged.items())
    lead = rng.choice([Fraction(1)] * 8 + [Fraction(-1)] * 2 + [Fraction(2), Fraction(3), Fraction(-2),
                      Fraction(1, 2), Fraction(3, 4), Fraction(5, 2), Fraction(-1, 3), Fraction(1000),
                      Fraction(1, 1000), Fraction(6), Fraction(-5, 4)])
    return lead, factors


def poly_from(lead, factors, letter="X"):
    x = sympy.Symbol(letter)
    p = sympy.Rational(lead.numerator, lead.denominator)
    for f, m in factors:
        p *= f.subs(X, x) ** m
    return sympy.expand(p)


def to_fr(c):
    n, d = sympy.fraction(sympy.Rational(c))
    return Fraction(int(n), int(d))


def gen_solve_case(rng, i, mode="solve"):
    deg = rng.choice([1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8])
    letter = "X" if rng.random() < 0.75 else rng.choice(list("TAZYNKMBQPWE") + ["θ"])
    x = sympy.Symbol(letter)
    lead, factors = random_poly(rng, deg)
    factors = [(f.subs(X, x), m) for f, m in factors]
    p = poly_from(lead, [(f.subs(x, X), m) for f, m in factors], letter)
    cs = poly_coeffs(p, letter)
    forms = ["expanded", "expanded0", "factored", "bothsides", "zeroleft", "horner", "mixed",
             "divconst", "bothX", "constleft"]
    if mode != "solve":
        forms = ["expanded", "factored", "horner", "mixed", "divconst", "factored", "expanded"]
    form = rng.choice(forms)
    if lead.denominator != 1 and form == "factored" and rng.random() < 0.5:
        form = "expanded"
    expect = p
    if form in ("expanded", "expanded0"):
        text = render_expanded(cs, letter, rng)
        if mode == "solve" and form == "expanded0":
            text += "=0"
    elif form == "factored":
        text = render_factored(lead, [(f.subs(x, X).subs(X, x), m) for f, m in factors], letter, rng)
        if mode == "solve" and rng.random() < 0.6:
            text += "=0"
    elif form == "horner":
        text = render_horner(cs, letter, rng)
        if mode == "solve" and rng.random() < 0.5:
            text += "=0"
    elif form == "divconst":
        d = rng.choice([2, 3, 4, 5, 6, 10])
        if rng.random() < 0.5:
            num = poly_coeffs(p * d, letter)
            text = "(" + render_expanded(num, letter, rng) + f")/{d}"
        else:
            # every term over d: 3X^2/4-1/4 style
            pieces = []
            for n, c in sorted(poly_coeffs(p * d, letter).items(), reverse=True):
                if c == 0:
                    continue
                sign = "-" if c < 0 else ("+" if pieces else "")
                a = abs(c)
                mono = _mono(letter, n, rng)
                core = (mono if (a == 1 and n) else _rat_str(a, rng, allow_decimal=False) + mono)
                pieces.append(f"{sign}{core}/{d}")
            text = "".join(pieces)
        if mode == "solve":
            text += "=0"
    elif form == "mixed":
        q = rng.choice([x - rng.randint(-5, 5), 2 * x + rng.randint(-3, 3), x, x**2 + rng.randint(-3, 3),
                        rng.randint(2, 4) * x])
        r, s = sympy.div(sympy.Poly(p, x), sympy.Poly(q, x))
        r, s = r.as_expr(), s.as_expr()
        if r == 0:
            text = render_expanded(cs, letter, rng)
        else:
            qp = sympy.Poly(q, x)
            if qp.degree() == 1 and qp.TC() == 0:          # 2X(...) or X(...)
                qt = ("" if qp.LC() == 1 else str(qp.LC())) + letter
            else:
                qt = "(" + render_expanded(poly_coeffs(q, letter), letter, rng) + ")"
            rt = "(" + render_expanded(poly_coeffs(r, letter), letter, rng) + ")"
            st = render_expanded(poly_coeffs(s, letter), letter, rng) if s != 0 else ""
            if st and not st.startswith("-"):
                st = "+" + st
            text = (qt + rt if rng.random() < 0.7 else rt + qt) + st
        if mode == "solve" and rng.random() < 0.5:
            text += "=0"
    elif form == "bothsides":
        # move some terms to the right: X^2=5X-6
        left, right = {}, {}
        for n, c in cs.items():
            if rng.random() < 0.5 or n == max(cs):
                left[n] = c
            else:
                right[n] = -c
        if not right:
            n0 = min(cs)
            if n0 != max(cs):
                right[n0] = -left.pop(n0)
        text = render_expanded(left, letter, rng) + "=" + render_expanded(right, letter, rng)
    elif form == "zeroleft":
        text = "0=" + render_expanded(cs, letter, rng)
        expect = -p
    elif form == "constleft":
        c0 = cs.get(0, Fraction(0))
        rest = {n: c for n, c in cs.items() if n}
        text = render_expanded({0: -c0}, letter, rng) + "=" + render_expanded(rest, letter, rng)
        expect = -p
    else:  # bothX: add the same thing to both sides
        s = sum(rng.randint(-5, 5) * x**k for k in range(0, rng.randint(1, 3)))
        s = sympy.expand(s)
        if s == 0:
            s = x
        left = sympy.expand(p + s)
        text = render_expanded(poly_coeffs(left, letter), letter, rng) + "=" + \
            render_expanded(poly_coeffs(s, letter), letter, rng)
    if rng.random() < 0.05:
        text = "  " + text + " "        # stray spaces at the ends
    return Case(f"{mode}-{i}", f"{mode}:{form}:deg{deg}", mode, text, letter,
                expect_expr=expect)


def gen_family_case(rng, i, mode="solve"):
    """Families aimed at known weak spots: decimal roots, high multiplicity,
    big and small numbers."""
    fam = rng.choice(["decroots"] * 4 + ["powers"] * 4 + ["bigsmall"] * 2 + ["bigconst"] * 3)
    letter = "X"
    if fam == "bigconst":
        # decimal coefficients next to a big number: 2.13X^2=9000000
        a = Fraction(rng.randint(11, 999), 100)
        b = Fraction(rng.randint(-999, 999), 100) if rng.random() < 0.5 else Fraction(0)
        c = Fraction(rng.choice([1, 2, 3, 5, 7, 9]) * 10 ** rng.randint(4, 8) + rng.randint(0, 999))
        p = (sympy.Rational(a.numerator, a.denominator) * X**2 + sympy.Rational(b.numerator, b.denominator) * X
             - sympy.Rational(c.numerator, c.denominator))
        left = render_expanded({2: a, 1: b}, letter, rng)
        text = left + "=" + str(c) if mode == "solve" else render_expanded({2: a, 1: b, 0: -c}, letter, rng)
    elif fam == "decroots":
        deg = rng.choice([2, 3, 3, 4, 4, 5, 6, 7, 8])
        roots = []
        for _ in range(deg):
            places = rng.choice([1, 1, 2])
            d = Fraction(rng.randint(-999 if places == 2 else -99, 999 if places == 2 else 99),
                         10 ** places)
            roots.append(d)
        p = sympy.expand(sympy.prod([X - sympy.Rational(d.numerator, d.denominator) for d in roots]))
        if rng.random() < 0.5:
            parts = []
            for d in roots:
                ds = _rat_str(abs(d), rng)
                parts.append(f"({letter}{'-' if d > 0 else '+'}{ds})" if d else letter)
            text = "".join(parts)
        else:
            text = render_expanded(poly_coeffs(p), letter, rng)
    elif fam == "powers":
        c = rng.random()
        if c < 0.4:
            a = rng.choice([v for v in range(-12, 13) if v])
            m = rng.randint(2, 8)
            fs = [(X - a, m)]
        elif c < 0.6:
            q, pp = rng.randint(2, 5), rng.choice([v for v in range(-9, 10) if v])
            if math.gcd(q, abs(pp)) != 1:
                pp += 1 if pp > 0 else -1
            fs = [(q * X - pp, rng.randint(2, 4))]
        elif c < 0.8:
            quad = rng.choice([X**2 - 2, X**2 - 3, X**2 + 1, X**2 + X + 1, X**2 - X - 1, X**2 - 5,
                               X**2 + 4, 2*X**2 - 3, X**2 + 2*X - 2])
            fs = [(quad, rng.randint(2, 4))]
        else:
            a, b = rng.sample([v for v in range(-6, 7) if v], 2)
            m1, m2 = rng.randint(2, 4), rng.randint(1, 4)
            fs = [(X - a, m1), (X - b, m2)]
        deg = sum(sympy.degree(f, X) * m for f, m in fs)
        if deg > 8:
            fs = [(fs[0][0], max(1, 8 // sympy.degree(fs[0][0], X)))]
        p = poly_from(Fraction(1), fs)
        text = (render_factored(Fraction(1), fs, letter, rng) if rng.random() < 0.5
                else render_expanded(poly_coeffs(p), letter, rng))
    else:
        n = rng.choice([1, 2, 2, 2, 3, 4])
        k = rng.choice([-10, -9, -8, -7, -6, 5, 6, 7, 8, 9, 10, 11])
        c = Fraction(rng.choice([1, 2, 3, 4, 5, 9])) * (Fraction(10) ** k)
        lead = rng.choice([Fraction(1), Fraction(1), Fraction(1000), Fraction(1, 1000)])
        p = sympy.Rational(lead.numerator, lead.denominator) * X**n - sympy.Rational(c.numerator, c.denominator)
        if mode == "solve":
            text = render_expanded({n: lead}, letter, rng) + "=" + _rat_str(c, rng)
        else:
            text = render_expanded({n: lead, 0: -c}, letter, rng)
    if mode == "solve" and "=" not in text:
        text += "=0"
    return Case(f"{mode}-fam-{i}", f"{mode}:{fam}", mode, text, letter, expect_expr=sympy.expand(p))


def special_cases():
    """Hand-written ones: identities, contradictions, notation, repeated roots."""
    S = []

    def add(text, mode="solve", letter="X", group="special", nasty=False):
        S.append(Case(f"{group}-{len(S)}", group, mode, text, letter, nasty=nasty))
    for t in ["2X+1=2X+1", "X+1=X+2", "(X+1)^2=X^2+2X+1", "X^2=X^2+1", "X(X+1)=X^2+X",
              "2(X+3)=2X+6", "(X-1)(X+1)=X^2-1", "X^3-X=X(X-1)(X+1)", "(X+1)^2=X^2+2X+5",
              "3X-3X=0", "0=0X", "X-X=5", "(2X-1)^2=4X^2-4X+1", "X²=X^2", "X³+1=X^3"]:
        add(t, group="identity")
    for t in ["X-X", "X-X+3", "2X-2X-1", "(X+1)^2-X^2-2X-1"]:
        add(t, "roots", group="identity")
        add(t, "zeros", group="identity")
    for t in ["X^2=5X-6", "0=X^2-1", "X^2-1", "2X(X-1)=0", "(2X-1)(X+3)^2=0", "(2X-1)(X+3)²=0",
              "X^2/4-1=0", "X²/4=1", "-X^2+4=0", "-(X-2)^2=0", "X^3-4X=0", "X³=4X", "T^2=9",
              "Z^3=8", "A^2+A+1=0", "θ^2=2", "E^2=E+1", "X^2+X+1=0", "X^2-X-1=0",
              "(X-1)^3=0", "(X-1)³=0", "(X^2+1)^2=0", "(X^2-2)^2=0", "X^4=0", "X^5-X^4=0",
              "1000X^2=1", "1000X^2-1000=0", ".001X^2-.004=0", "0.001X^2=0.004", "2.5X=10",
              "2.5X^2-10=0", "X/3+X/6=1", "X^2/2+X/3-1/6=0", "((X-1)(X+1)+3)X=0",
              "(((X)))^2=4", "2(3(X-1)-2)=8", "X(X(X-6)+11)-6=0", "X^8=1", "X^8-256=0",
              "X^6-1=0", "X^4-5X^2+4=0", "X^4+4=0", "X^5=32", "X^7-X=0", "X^3+X^2+X+1=0",
              "(X-1)(X-2)(X-3)(X-4)(X-5)(X-6)(X-7)(X-8)=0", "X^2+0X-4=0", "X^2+1X-2=0",
              "-X=4", "X=0", "X=⁻3", "3=X", "X^2=0", "πX^2-π=0", "πX=π^2", "X^2=π^2",
              "X^2-2πX+π^2=0", "(X-π)(X+1)=0", "2πX^2=8π", "X^2-π=0", "(X-π)^2=0",
              "πX^3=X^2", "X^2(πX-1)=0", "(X-1)^2(X-π)=0", "(2X-1)^3=0", "(3X+2)^2(X-1)^2=0",
              "X^3(X+2)^2=0", "(X^2+X+1)^2(X-1)=0", "(X^2-3)^3=0", "(X^2-2)(X^2-3)=0",
              "(X^2+1)(X^2-5)=0", "(X^2+X+1)(X^2-X-1)=0", "(X^3-2)(X+1)=0", "X^3-2=0",
              "(X^3-2)^2=0", "4X^2-12X+9=0", "9X^2+6X+1=0", "X^2-6X+13=0", "2X^2+3X-5=0",
              "6X^2-X-2=0", "X(2X-1)(3X+1)(4X-3)=0", "X^2-1/4=0", "X^2=2/9", "X^2=0.25",
              "100X^2=1", "X^2=10000", "X^2=1000000", "X^3=1000", "X^2=12", "X^2=72",
              "X^2+X=0", " X^2-9=0 ", "X^2-9=0  ", "X^2*3=12", "3*X^2=12", "X*X=4",
              "X*X*X=8", "(X)(X)=4", "X(X)=9", "2X²+3X=⁻1"]:
        add(t)
    for t in ["X^4-1", "2X^2+3X-5", "X^5+X+1", "X^3-4X", "X^2+1", "X^8-1", "(X-2)^4",
              "X^3+X^2+X+1", "πX^2-1", "(X-1)(X-1.001)", "T^3-T", "X²", "X³-8", "X^6+1",
              "X^4+X^2+1", "(X^2+2)^2", "X^3-3X^2+3X-1", "-X^3+X", "1/2X^2-2"]:
        add(t, "roots")
        add(t, "zeros")
    return S


def nasty_cases():
    """Numerically hard ones (reported in their own group)."""
    S = []

    def add(text, mode="solve", letter="X"):
        S.append(Case(f"nasty-{len(S)}", "nasty", mode, text, letter, nasty=True))
    for t in [
        # clustered roots
        "(X-1)(X-1.001)=0", "(X-1)(X-1.0001)=0", "(X-1)(X-1.00001)=0", "(X-2)(X-2.01)(X-2.02)=0",
        "(X-1)^2(X-1.01)=0", "(X^2-2)(X^2-2.01)=0",
        # spread / Wilkinson-like
        "(X-1)(X-2)(X-3)(X-4)(X-5)(X-6)(X-7)=0",
        "(X-1)(X-2)(X-4)(X-8)(X-16)(X-32)(X-64)(X-128)=0",
        "(X-.5)(X-1.5)(X-2.5)(X-3.5)(X-4.5)(X-5.5)(X-6.5)(X-7.5)=0",
        "(X-1)(X-10)(X-100)(X-1000)=0", "(X-.1)(X-1)(X-10)(X-100)(X-1000)=0",
        "(X^3-2)(X^3-2000)=0", "(X^3-3)(X^3-300)(X+1)(X-1)=0",
        "(X-1.1)(X-2.2)(X-3.3)(X-4.4)(X-5.5)(X-6.6)(X-7.7)=0",
        # huge and tiny
        "(X-1000)(X-0.001)=0", "(X-10000)(X-.0001)=0", "(X-1000)^2=0", "(X-1000)^3=0",
        "(X-1001)^3=0", "(X-100)^4=0", "(X-50)^5=0", "(X-20)^8=0", "(X-10)^8=0",
        "X^2=10000000000", "X^2=1000000000", "X^2=4000000000", "X^3=1000000000",
        "X^2=.0000000001", "X^2=.000001", "X^2-.00000001=0", "10000X^2=1000000000000",
        "X^2+100000X+1=0", "X^2+100000X+3=0", "X^2+1000000X+7=0", "1000X^2+10000001X+3=0",
        "X^2-12345678X+1=0", "X^2+200001X+1=0",
        "1000000X^2-1=0", "1000000X^2-2=0", ".000001X^2-1=0", ".000001X^2-2=0",
        "X^2+.000001X-1=0", ".0001X^2=2000000", "0.001X^2=1000000", "X^4/1000-9000000=0",
        "2.13X^2=9000000", "1.43X^2=100000000", "0.37X^2+X=2500000", "X^2+2.13X=9000000",
        "(X^2+1)^3=0", "(X-5)^5=0", "(X+9)^8=0", "(X^2-2)^4=0",
        # near-miss coefficients
        "1000000X^2-1.00001=0", "1000000X^2+.5001X-3=0", "1000X^2-.3333=0",
        "X^2-2.0000000001=0", "X^2-1.9999999999X+.9999999999=0", "X^2-.3333333333=0",
        "1000X^2+X-.33333333=0", "10000X^3-X-.33333=0", "X^2-1.41421356=0",
        # π
        "πX^2-2π=0", "(X-π)(X-2π)(X-3π)=0", "X^2-π^2=0", "(X-π)^3=0", "πX^3-π=0",
        "X^2+πX+1=0", "X^2-3.14159265=0", "3.14159265X^2-1=0",
        # imaginary coefficients and constant sides
        "X^2+2𝑖X-1=0", "X^2+𝑖=0", "X^2=𝑖",
        "X^2=√(2)", "√(3)=X^2+X", "X^2=abs(-4)", "abs(-9)=X^2", "X^3=³√(2)",
        "√(2)X^2-1=0", "X^2-√(2)X=0",
    ]:
        add(t)
    return S


def degree_trig_cases():
    return [Case(f"degtrig-{i}", "degree-trig", "solve", t, degree=True, nasty=True)
            for i, t in enumerate(["sin(30)X^2=2", "cos(60)X^2-2=0", "2sin(30)X=4", "tan(45)X^2=9"])]


def build_cases(quick=False):
    rng = random.Random(SEED)
    cases = []
    n_solve, n_roots, n_zeros = (120, 40, 40) if quick else (1000, 250, 250)
    n_fam = 40 if quick else 250
    for i in range(n_solve):
        cases.append(gen_solve_case(rng, i, "solve"))
    for i in range(n_roots):
        cases.append(gen_solve_case(rng, i, "roots"))
    for i in range(n_zeros):
        cases.append(gen_solve_case(rng, i, "zeros"))
    for i in range(n_fam):
        cases.append(gen_family_case(rng, i, rng.choice(["solve", "solve", "roots", "zeros"])))
    cases += special_cases()
    cases += nasty_cases()
    cases += degree_trig_cases()
    return cases


# ============================================================== running

def work(item):
    kind, payload = item
    t0 = time.time()
    if kind == "case":
        case = payload
        run = run_answers(case.answers(), case.degree)
        try:
            judge(case, run)
        except Exception as e:  # noqa: BLE001
            run.problems.append(("HARNESS", f"judge crashed: {type(e).__name__}: {e}"))
        return ("case", case, run.lines, run.problems, time.time() - t0)
    if kind == "degree":
        case = payload
        b = run_answers(case.answers(), True)
        probs = []
        if b.error:
            probs.append(("ERR", "Degree mode: " + b.error))
        probs += [p for p in b.problems if p[0] in ("leak", "layout")]
        return ("degree", case, b.lines, probs, time.time() - t0)
    if kind == "again":
        group = payload
        m = fresh(False)
        log = capture(m)
        probs = []
        lines_all = []
        for k, case in enumerate(group):
            run = Run()
            start = len(log)
            answers = case.answers() if k == 0 else ["AGAIN", case.text]
            if k > 0 and group[k - 1].mode != case.mode:
                answers = ["MAIN MENU"] + case.answers()
            drive(m, log, answers, run)
            run.lines = logical_lines(log[start:], run)
            if run.error:
                probs.append(("ERR", f"after {k} earlier answers: {run.error}"))
                break
            if run.final_input:
                break                   # refused (reported by the case itself)
            lines_all.append(run.lines)
        if (m.leaks or m.blocks) and not any(p[0] == "ERR" for p in probs):
            probs.append(("leak", f"leaks={m.leaks} blocks={m.blocks}"))
        return ("again", group, lines_all, probs, time.time() - t0)
    raise ValueError(kind)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true")
    ap.add_argument("--jobs", type=int, default=3)
    ap.add_argument("--only", default="", help="regex on case text")
    ap.add_argument("--show", action="store_true", help="print every case's output")
    args = ap.parse_args()

    cases = build_cases(args.quick)
    if args.only:
        cases = [c for c in cases if re.search(args.only, c.text)]
    rng = random.Random(SEED + 1)
    poly_cases = [c for c in cases if not c.nasty and not c.degree]
    deg_subset = rng.sample(poly_cases, min(len(poly_cases), 40 if args.quick else 200))
    again_pool = rng.sample(poly_cases, min(len(poly_cases), 20 if args.quick else 120))
    again_groups = [again_pool[i:i + 6] for i in range(0, len(again_pool), 6)]
    items = ([("case", c) for c in cases] + [("degree", c) for c in deg_subset]
             + [("again", g) for g in again_groups])
    if args.only:
        items = [("case", c) for c in cases]

    t0 = time.time()
    results = []
    if args.jobs > 1:
        import multiprocessing as mp
        with mp.Pool(args.jobs, initializer=tokens) as pool:
            for r in pool.imap_unordered(work, items, chunksize=4):
                results.append(r)
    else:
        tokens()
        for it in items:
            results.append(work(it))
    elapsed = time.time() - t0

    # Degree re-runs and AGAIN sessions must print what the fresh Radian run printed
    fresh_lines = {r[1].cid: r[2] for r in results if r[0] == "case"}
    for kind, case, lines, probs, dt in results:
        if kind == "degree" and not probs and lines != fresh_lines.get(case.cid):
            probs.append(("degree-differs", f"Degree {lines} vs Radian {fresh_lines.get(case.cid)}"))
        if kind == "again":
            for k, got in enumerate(lines):
                want = fresh_lines.get(case[k].cid)
                if got != want:
                    probs.append(("again-differs", f"case {k} {case[k].text!r}: {got} vs fresh {want}"))

    # ---- report
    counts = Counter()
    fails = defaultdict(list)
    soft = defaultdict(list)
    n_by_group = Counter()
    fail_by_group = Counter()
    for kind, case, lines, probs, dt in results:
        label = kind if kind != "case" else case.group.split(":")[0]
        if kind == "case" and "-fam-" in case.cid:
            label = "family:" + case.group.split(":")[1]
        if kind == "case" and case.nasty:
            label = "nasty"
        n_by_group[label] += 1
        hard = [p for p in probs if not p[0].startswith("soft:")]
        for p in probs:
            if p[0].startswith("soft:"):
                soft[p[0]].append((kind, case, p[1]))
        if hard:
            fail_by_group[label] += 1
            for p in hard:
                fails[p[0]].append((kind, case, p[1], lines))
        if args.show:
            print(kind, case.describe() if kind != "again" else "again group", lines, probs)
    total = len(results)
    nfail = sum(fail_by_group.values())
    print(f"solve_poly: {total} runs in {elapsed:.0f}s "
          f"({sum(1 for r in results if r[0] == 'case')} cases, {len(deg_subset)} Degree re-runs, "
          f"{len(again_groups)} AGAIN sessions)")
    for g in sorted(n_by_group):
        print(f"  {g:14s} {n_by_group[g]:5d} runs, {fail_by_group[g]:4d} failing")
    print(f"PASS {total - nfail}  FAIL {nfail}")
    if soft:
        print("\nsoft findings (not counted as failures):")
        for cat, items_ in sorted(soft.items()):
            print(f"  {cat}: {len(items_)}")
            for kind, case, detail in items_[:6]:
                print(f"     {case.describe()}  -- {detail}")
    if fails:
        print("\nfailing runs per category (a run can be in several):")
        for cat, items_ in sorted(fails.items(), key=lambda kv: -len(kv[1])):
            nruns = len({id(it[1]) for it in items_})
            print(f"  {cat:40s} {nruns:4d}")
        print("\nFAILURES by category:")
        for cat, items_ in sorted(fails.items(), key=lambda kv: -len(kv[1])):
            print(f"\n== {cat}: {len({id(it[1]) for it in items_})} runs")
            for kind, case, detail, lines in items_:
                if kind == "again":
                    desc = "AGAIN session: " + " ; ".join(c.describe() for c in case)
                else:
                    desc = case.describe() + ("  [Degree re-run]" if kind == "degree" else "")
                print(f"  {desc}")
                print(f"      {detail}")
                if kind == "case":
                    print(f"      screen: {lines}")
    sys.exit(1 if nfail else 0)


if __name__ == "__main__":
    main()
