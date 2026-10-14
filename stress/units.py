#!/usr/bin/env python3
"""Pressure test for UNITS (brainiac/src/UNITS.txt) on the simulated TI-84 Plus CE.

    python stress/units.py            # full run (~70 s of CPU)
    python stress/units.py --quick    # 600 rate samples instead of 4000

Exit code 0 = everything passed. Every failure is printed with the exact answer
sequence from a fresh start (UNITS run on its own unless it says BRAINIAC).

The oracle is independent of the program: every unit is defined here from the
exact definitions (NIST: 1 in = 2.54 cm, 1 lb = 453.59237 g, 1 gal = 231 in^3,
1 acre = 43560 ft^2, ...) as an exact Fraction, and the expected screen numbers
are made by rounding exact values to 10 significant digits (TI Normal/Float).
The harness reads the unit numbers off the program's own list screen, so a
mislabelled list is caught too.

Checked for every conversion:
  * no ERR, leaks == 0, blocks == [] and, after QUIT, no lists left
  * the header (CONVERT A TO B), the amount line, every fraction in the chain,
    and the answer line
  * every fraction "×a U1/b U2" is a true identity (a U1 = b U2), is written
    with a 1 on one side, and has the unit being cancelled in the right place
    (each fraction's bottom unit is the unit of the line before; on the bottom
    of a rate, the old unit is on top)
  * the chain multiplied out (with the numbers as shown) gives the answer
  * the answer agrees with the oracle to 9 significant digits ("WRONG" if not)
  * no ugly floats: an exact value with <= 9 significant digits must be shown
    exactly (768, not 767.9999999)
  * the "units cancel" note appears exactly when there is a fraction
  * screen: no Output( runs past row 9 (row 10 is for ENTER), no two lines on a
    page overlap, Disp/Input text <= 26 columns, menu options <= 24
Plus temperature (all 9 pairs, formulas and substituted numbers), bad unit
numbers, AGAIN, MORE, HOW IT WORKS, BACK, QUIT from everywhere, 30 rounds on
one machine, and UNITS run from BRAINIAC (5, 2) in Radian and Degree.

"Notes" are printed but are not failures: TIE = the exact answer is a tie at
the 11th digit (like 28349.523125 G) and the simulator's binary rounding went
down where the calculator's decimal rounding goes up; plus pedagogy flags
(rounded factors, ᴇ notation in a fraction, below absolute zero, wrapping).
Type 1ᴇ50 with the ᴇ glyph: "1E50" is 1*E*50 (E the variable), and "\x3B" is
mangled by keyboard_to_tokens (its x->X rule).
"""

from __future__ import annotations

import random
import re
import sys
import time
from collections import Counter, defaultdict
from decimal import ROUND_HALF_UP, Decimal, localcontext
from fractions import Fraction as Fr
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from brainiac.build import PROGRAMS, program_bytes, source_lines, split_args  # noqa: E402
from brainiac.screen import view  # noqa: E402
from brainiac.tibasic import COLS, ROWS, Machine, Screen, tokens_from_bytes  # noqa: E402

QUICK = "--quick" in sys.argv
SEED = 20261014

# ------------------------------------------------------------------ the oracle

IN = Fr(254, 10000)                      # metres
FT = 12 * IN
YD = 3 * FT
MI = 5280 * FT
LB = Fr(45359237, 100000)                # grams
GAL = 231 * (IN * 10) ** 3               # litres: 231 cubic inches, 1 L = 1 dm^3
QT, PT, CUP = GAL / 4, GAL / 8, GAL / 16
FLOZ = CUP / 8
TBSP = FLOZ / 2
TSP = TBSP / 3
DAY = 86400

UNIT = {   # name -> (category, size in m, g, L, m^2 or s)
    "MM": ("LENGTH", Fr(1, 1000)), "CM": ("LENGTH", Fr(1, 100)), "M": ("LENGTH", Fr(1)),
    "KM": ("LENGTH", Fr(1000)), "IN": ("LENGTH", IN), "FT": ("LENGTH", FT),
    "YD": ("LENGTH", YD), "MI": ("LENGTH", MI),
    "MG": ("MASS", Fr(1, 1000)), "G": ("MASS", Fr(1)), "KG": ("MASS", Fr(1000)),
    "OZ": ("MASS", LB / 16), "LB": ("MASS", LB), "TON": ("MASS", 2000 * LB),
    "ML": ("VOLUME", Fr(1, 1000)), "L": ("VOLUME", Fr(1)), "CM³": ("VOLUME", Fr(1, 1000)),
    "M³": ("VOLUME", Fr(1000)), "TSP": ("VOLUME", TSP), "TBSP": ("VOLUME", TBSP),
    "FL OZ": ("VOLUME", FLOZ), "CUP": ("VOLUME", CUP), "PT": ("VOLUME", PT),
    "QT": ("VOLUME", QT), "GAL": ("VOLUME", GAL),
    "MM²": ("AREA", Fr(1, 10 ** 6)), "CM²": ("AREA", Fr(1, 10 ** 4)), "M²": ("AREA", Fr(1)),
    "HA": ("AREA", Fr(10 ** 4)), "KM²": ("AREA", Fr(10 ** 6)), "IN²": ("AREA", IN ** 2),
    "FT²": ("AREA", FT ** 2), "YD²": ("AREA", YD ** 2), "ACRE": ("AREA", 43560 * FT ** 2),
    "MI²": ("AREA", MI ** 2),
    "SEC": ("TIME", Fr(1)), "MIN": ("TIME", Fr(60)), "HR": ("TIME", Fr(3600)),
    "DAY": ("TIME", Fr(DAY)), "WEEK": ("TIME", Fr(7 * DAY)),
    "YEAR": ("TIME", Fr(365 * DAY)),       # README: "a year is 365 days"
}
CATS = ["LENGTH", "MASS", "VOLUME", "AREA", "TIME"]            # main menu 1..5
CAT_COUNT = {c: sum(1 for k, v in UNIT.items() if v[0] == c) for c in CATS}
TOP_MENU = {c: str(i + 1) for i, c in enumerate(CATS)}          # rate top menu
BOTTOM_MENU = {"TIME": "1", "LENGTH": "2", "MASS": "3", "VOLUME": "4", "AREA": "5"}

# what the student types, and its exact value
AMOUNTS = [("1", Fr(1)), ("0", Fr(0)), ("-2", Fr(-2)), ("-12.5", Fr(-25, 2)),
           ("0.001", Fr(1, 1000)), ("123456789", Fr(123456789)),
           ("1ᴇ50", Fr(10) ** 50), ("3/4", Fr(3, 4)), ("2+3", Fr(5)),
           ("-1.234567891ᴇ⁻50", Fr(-1234567891, 10 ** 59))]     # long lines that wrap


def dec(x: Fr) -> Decimal:
    with localcontext() as c:
        c.prec = 80
        return Decimal(x.numerator) / Decimal(x.denominator)


def ti_fmt(x: Fr) -> str:
    """x as TI Normal/Float mode shows it: 10 significant digits, ᴇ outside
    [.001, 1E10), no leading 0, ⁻ for negative."""
    if x == 0:
        return "0"
    neg, x = x < 0, abs(x)
    d = dec(x)
    e = d.adjusted()
    with localcontext() as c:
        c.prec = 80
        mant = (d.scaleb(-e)).quantize(Decimal("1.000000000"), rounding=ROUND_HALF_UP)
        if mant >= 10:
            mant, e = mant / 10, e + 1
        val = mant.scaleb(e)
    if Decimal("0.001") <= val < Decimal("1e10"):
        s = format(val, "f")
        if "." in s:
            s = s.rstrip("0").rstrip(".")
        if s.startswith("0."):
            s = s[1:]
    else:
        m = format(mant, "f").rstrip("0").rstrip(".")
        s = f"{m}ᴇ{'⁻' if e < 0 else ''}{abs(e)}"
    return ("⁻" if neg else "") + s


NUM = r"⁻?(?:\d+\.?\d*|\.\d+)(?:ᴇ⁻?\d+)?"


def parse_num(s: str) -> Fr:
    if not re.fullmatch(NUM, s):
        raise ValueError(f"not a TI number: {s!r}")
    return Fr(Decimal(s.replace("⁻", "-").replace("ᴇ", "e")))


def sig_digits(s: str) -> int:
    m = s.replace("⁻", "").split("ᴇ")[0].replace(".", "").lstrip("0")
    return len(m.rstrip("0")) if "." not in s.split("ᴇ")[0] else len(m)


def judge(shown: str, exact: Fr) -> str:
    """'' if shown is exactly right; else WRONG / UGLY / ROUND (1 unit in the
    10th digit off, on a value that isn't short: arithmetic noise)."""
    want = ti_fmt(exact)
    if shown == want:
        return ""
    try:
        got = parse_num(shown)
    except ValueError:
        return "WRONG"
    if exact == 0:
        return "WRONG"
    ulp = Fr(10) ** (dec(abs(exact)).adjusted() - 9)
    diff = abs(got - exact)
    if diff > 10 * ulp:                       # worse than 9 significant digits
        return "WRONG"
    nice = parse_num(want) == exact             # exact value fits in 10 digits
    if nice and sig_digits(want) <= 9:
        return "UGLY"
    if diff > ulp * Fr(11, 10):
        return "DIGIT10"                       # 10th digit off by more than 1
    if exact * Fr(10) ** (9 - dec(abs(exact)).adjusted()) * 2 % 2 == 1:
        return "TIE"                           # x.xxxxxxxxx5 exactly
    return "ROUND"


# ------------------------------------------------------------- the simulator

TOK = {n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}
LOG: list = []

_out, _disp, _clr, _inp = Screen.output, Screen.disp, Screen.clear, Screen.input_line


def _log_output(self, row, col, text):
    LOG.append(("out", row, col, text))
    return _out(self, row, col, text)


def _log_disp(self, text, right=False):
    LOG.append(("disp", text))
    return _disp(self, text, right)


def _log_clear(self):
    LOG.append(("clr",))
    return _clr(self)


def _log_input(self, prompt):
    LOG.append(("input", prompt))
    return _inp(self, prompt)


Screen.output, Screen.disp, Screen.clear, Screen.input_line = (
    _log_output, _log_disp, _log_clear, _log_input)


def fresh(name="UNITS", degree=False) -> Machine:
    LOG.clear()
    m = Machine(TOK[name])
    m.programs.update({k: v for k, v in TOK.items() if k != name})
    m.radians = not degree
    m.run()
    return m


class Fail(Exception):
    pass


FAILS: list[tuple[str, str, str]] = []      # (kind, answers, message)
INFO: Counter = Counter()
EXAMPLES: dict[str, list] = defaultdict(list)
COUNTS: Counter = Counter()


def fail(kind, answers, msg):
    FAILS.append((kind, ",".join(repr(a) for a in answers), msg))


def note(kind, example):
    INFO[kind] += 1
    if len(EXAMPLES[kind]) < 4:
        EXAMPLES[kind].append(example)


def ask(m: Machine, a: str, answers: list):
    # a scripted sequence doesn't know how many pages a chain takes: turn
    # "ENTER: MORE" pages until it's asked something else
    while a != "" and m.wait.kind == "pause":
        answers.append("")
        m.answer("")
    answers.append(a)
    if m.wait.kind not in ("menu", "input", "pause"):
        raise Fail(f"expected to be asked something, got {m.wait} at {m.statement_text()}")
    m.answer(a)
    if m.wait.kind == "error":
        raise Fail(f"{m.wait.message} at: {m.statement_text()}")
    if m.leaks:
        raise Fail(f"leaks={m.leaks}")
    if m.wait.kind == "menu":
        if m.blocks:
            raise Fail(f"blocks open at a menu: {m.blocks}")
        if len(m.wait.title) > COLS:
            raise Fail(f"menu title too long: {m.wait.title!r}")
        for o in m.wait.options:
            if len(o) > 24:
                raise Fail(f"menu option over 24 chars: {o!r}")


def since_clear(kinds=("disp", "input", "out")):
    i = max((k for k, e in enumerate(LOG) if e[0] == "clr"), default=-1)
    return [e for e in LOG[i + 1:] if e[0] in kinds]


def check_text_widths(entries):
    for e in entries:
        if e[0] in ("disp", "input") and len(e[1]) > COLS:
            raise Fail(f"{e[0]} text over {COLS} columns: {e[1]!r}")


def read_unit_list(m: Machine, title: str) -> dict[int, str]:
    """The numbered unit list the program shows -> {number: name}."""
    shown = since_clear()
    check_text_widths(shown)
    disp = [e[1] for e in shown if e[0] == "disp"]
    if not disp or disp[0] != title:
        raise Fail(f"unit list title {disp[:1]!r}, expected {title!r}")
    names = {}
    for row in disp[1:]:
        for num, name in re.findall(r"(\d+):(\S+(?: OZ)?)", row):
            if int(num) in names:
                raise Fail(f"unit number {num} listed twice")
            names[int(num)] = name.strip()
    if sorted(names) != list(range(1, len(names) + 1)):
        raise Fail(f"unit list not numbered 1..N: {sorted(names)}")
    # everything on one screen: title + rows + the FROM and TO prompts
    if len(disp) + 2 > ROWS:
        raise Fail(f"unit list ({len(disp)} lines) + 2 prompts don't fit on 10 rows")
    if m.wait.prompt != "FROM (NUMBER):":
        raise Fail(f"prompt {m.wait.prompt!r}, expected FROM (NUMBER):")
    return names


def collect_pages(m: Machine, answers: list) -> list[str]:
    """After the amount: page through MORE until DONE. Returns the logical
    lines printed with Output( (not the ENTER: lines), checking each page."""
    lines: list[str] = []
    page_rows: set[int] = set()
    start = max(k for k, e in enumerate(LOG) if e[0] == "input")
    pos = start + 1
    while True:
        if m.wait.kind != "pause":
            raise Fail(f"expected a Pause, got {m.wait}")
        for e in LOG[pos:]:
            if e[0] == "clr":
                page_rows = set()
            elif e[0] == "disp":
                raise Fail(f"unexpected Disp on the result screen: {e[1]!r}")
            elif e[0] == "out":
                _, row, col, text = e
                if text.startswith("ENTER: "):
                    if row != 10:
                        raise Fail(f"{text!r} at row {row}")
                    continue
                if col != 1:
                    raise Fail(f"line at column {col}: {text!r}")
                nrows = (len(text) - 1) // COLS + 1
                rows = set(range(row, row + nrows))
                if max(rows) > ROWS - 1:
                    raise Fail(f"line runs into row {max(rows)}: {text!r}")
                if rows & page_rows:
                    raise Fail(f"line overwrites an earlier one: {text!r}")
                page_rows |= rows
                lines.append(text)
                COUNTS["longest line"] = max(COUNTS["longest line"], len(text))
                if len(text) > COLS:
                    note("line wraps onto a second row", text)
                    a, b = text[COLS - 1], text[COLS]
                    if a != " " and b != " ":
                        note("wrap splits a word/number", (",".join(answers), text))
        pos = len(LOG)
        bottom = m.screen.lines()[ROWS - 1].strip()
        if bottom == "ENTER: DONE":
            return lines
        if bottom != "ENTER: MORE":
            raise Fail(f"paused without ENTER: MORE/DONE (bottom row {bottom!r})")
        ask(m, "", answers)


CANCEL = ["EACH FRACTION EQUALS 1;", "MATCHING UNITS ON TOP AND", "BOTTOM CANCEL."]
STEP = re.compile(rf"^×({NUM}) (.+)/({NUM}) (.+)$")
AMOUNT = re.compile(rf"^({NUM}) (.+)$")
ANSWER = re.compile(rf"^=({NUM}) (.+)$")


def check_number(shown, exact, what, answers, extra=""):
    v = judge(shown, exact)
    if v in ("WRONG", "UGLY"):
        raise Fail(f"{v}: {what} shows {shown}, should be {ti_fmt(exact)}{extra}")
    if v:
        note(f"{v} ({what})", (",".join(answers), shown, ti_fmt(exact)))
    return v


def check_chain(lines, amount: Fr, top_from, top_to, bot_from=None, bot_to=None,
                answers=()):
    """lines = everything printed for one conversion. Raises Fail."""
    rate = bot_from is not None
    unit_in = top_from + ("/" + bot_from if rate else "")
    unit_out = top_to + ("/" + bot_to if rate else "")
    m = AMOUNT.match(lines[0])
    if not m or m.group(2) != unit_in:
        raise Fail(f"first line {lines[0]!r}, expected '<amount> {unit_in}'")
    check_number(m.group(1), amount, "the amount", answers)
    shown_amount = parse_num(m.group(1))
    k = 1
    steps = []
    while k < len(lines) and lines[k].startswith("×"):
        s = STEP.match(lines[k])
        if not s:
            raise Fail(f"can't read the fraction {lines[k]!r}")
        a, u1, b, u2 = s.groups()
        steps.append((a, u1, b, u2, lines[k]))
        k += 1
    if k >= len(lines):
        raise Fail("no answer line")
    ans = ANSWER.match(lines[k])
    if not ans or ans.group(2) != unit_out:
        raise Fail(f"answer line {lines[k]!r}, expected '=<number> {unit_out}'")
    rest = lines[k + 1:]
    want_rest = ([" "] + CANCEL) if steps else []
    if [r.rstrip() or " " for r in rest] != want_rest:
        raise Fail(f"after the answer: {rest!r}, expected {want_rest!r}")

    # each fraction is a true identity, with a 1 on one side
    product = Fr(1)
    for a, u1, b, u2, text in steps:
        if u1 not in UNIT or u2 not in UNIT:
            raise Fail(f"unknown unit in {text!r}")
        if UNIT[u1][0] != UNIT[u2][0]:
            raise Fail(f"{text!r} mixes {UNIT[u1][0]} and {UNIT[u2][0]}")
        if u1 == u2:
            raise Fail(f"{text!r} converts a unit to itself")
        fa, fb = parse_num(a), parse_num(b)
        lhs, rhs = fa * UNIT[u1][1], fb * UNIT[u2][1]
        if abs(lhs - rhs) > abs(rhs) * Fr(1, 10 ** 9):
            raise Fail(f"{text!r} is not true: {a} {u1} = {float(lhs):.12g}, "
                       f"{b} {u2} = {float(rhs):.12g}")
        if a != "1" and b != "1":
            raise Fail(f"{text!r} has no 1 on either side")
        if (a == "1" and fb < 1) or (b == "1" and fa < 1):
            raise Fail(f"{text!r}: the other side is less than 1")
        # the factor shown must be the exact factor, nicely rounded
        if b == "1":
            check_number(a, UNIT[u2][1] / UNIT[u1][1], f"factor in {text!r}", answers)
        else:
            check_number(b, UNIT[u1][1] / UNIT[u2][1], f"factor in {text!r}", answers)
        if "ᴇ" in a or "ᴇ" in b:
            note("a fraction in ᴇ notation", text)
        if lhs != rhs:
            note("a fraction that is only approximately 1 (rounded factor)", text)
        product *= fa / fb

    # the units cancel: find an order (top steps, then bottom steps) that works
    def walk(i, top, bot, phase):
        if i == len(steps):
            return top == top_to and (not rate or bot == bot_to)
        a, u1, b, u2, _ = steps[i]
        if phase == 0 and u2 == top and walk(i + 1, u1, bot, 0):
            return True
        return rate and u1 == bot and walk(i + 1, top, u2, 1)
    if not walk(0, top_from, bot_from, 0):
        raise Fail("the units in the chain don't cancel in order: "
                   + " | ".join(s[4] for s in steps))
    if not steps and (top_from != top_to or (rate and bot_from != bot_to)):
        raise Fail("no fractions shown but the units differ")

    # exact answer
    exact = amount * UNIT[top_from][1] / UNIT[top_to][1]
    if rate:
        exact = exact * UNIT[bot_to][1] / UNIT[bot_from][1]
    shown = ans.group(1)
    check_number(shown, exact, "the answer", answers)
    # the chain, multiplied out with the numbers as shown, gives the answer
    chain = shown_amount * product
    got = parse_num(shown)
    if abs(chain - got) > abs(got) * Fr(1, 10 ** 8) + (0 if got else Fr(0)):
        raise Fail(f"the chain multiplies out to {float(chain):.10g}, not {shown}")
    return len(steps)


# ------------------------------------------------------------- single units

def run_single(cat_no: int, f: int, t: int, amount_text: str, amount: Fr,
               quit_after=True):
    answers: list[str] = []
    m = fresh()
    cat = CATS[cat_no - 1]
    try:
        if m.wait.title != "UNIT CONVERSION":
            raise Fail(f"first menu is {m.wait.title!r}")
        ask(m, str(cat_no), answers)
        names = read_unit_list(m, f"{cat} UNITS:")
        if set(names.values()) != {k for k, v in UNIT.items() if v[0] == cat}:
            raise Fail(f"{cat} list is {sorted(names.values())}")
        ask(m, str(f), answers)
        if m.wait.prompt != "TO (NUMBER):":
            raise Fail(f"after FROM: prompt {m.wait.prompt!r}")
        ask(m, str(t), answers)
        a, b = names[f], names[t]
        shown = since_clear()
        check_text_widths(shown)
        disp = [e[1] for e in shown if e[0] == "disp"]
        if disp[:1] != [f"CONVERT {a} TO {b}"]:
            raise Fail(f"header {disp!r}, expected CONVERT {a} TO {b}")
        if m.wait.prompt != "HOW MANY? ":   # (a literal prompt: the unit is on the CONVERT line)
            raise Fail(f"prompt {m.wait.prompt!r}")
        ask(m, amount_text, answers)
        lines = collect_pages(m, answers)
        n = check_chain(lines, amount, a, b, answers=answers)
        COUNTS[f"single steps={n}"] += 1
        ask(m, "", answers)
        if m.wait.title != "WHAT NEXT?":
            raise Fail(f"after DONE: {m.wait}")
        if quit_after:
            ask(m, "3", answers)
            if m.wait.kind != "done" or m.lists:
                raise Fail(f"after QUIT: {m.wait}, lists left: {sorted(m.lists)}")
        return True
    except Fail as e:
        fail(f"single {cat}", answers, str(e))
        return False


# ------------------------------------------------------------- rates

def run_rate(tc: int, tf: int, tt: int, bc: int, bf: int, bt: int,
             amount_text: str, amount: Fr):
    answers: list[str] = []
    m = fresh()
    top, bot = CATS[tc - 1], CATS[bc - 1]
    try:
        ask(m, "6", answers)
        if m.wait.title != "THE TOP UNIT IS A…":
            raise Fail(f"rate menu is {m.wait.title!r}")
        ask(m, TOP_MENU[top], answers)
        tn = read_unit_list(m, f"TOP: {top} UNITS")
        ask(m, str(tf), answers)
        ask(m, str(tt), answers)
        if m.wait.title != "THE BOTTOM UNIT IS A…":
            raise Fail(f"after the top units: {m.wait}")
        ask(m, BOTTOM_MENU[bot], answers)
        bn = read_unit_list(m, f"BOTTOM: {bot} UNITS")
        ask(m, str(bf), answers)
        ask(m, str(bt), answers)
        uin, uout = f"{tn[tf]}/{bn[bf]}", f"{tn[tt]}/{bn[bt]}"
        shown = since_clear()
        check_text_widths(shown)
        disp = [e[1] for e in shown if e[0] == "disp"]
        if disp[:2] != [f"CONVERT {uin}", f"TO {uout}"]:
            raise Fail(f"header {disp!r}")
        if m.wait.prompt != "HOW MANY? ":
            raise Fail(f"prompt {m.wait.prompt!r}")
        ask(m, amount_text, answers)
        lines = collect_pages(m, answers)
        n = check_chain(lines, amount, tn[tf], tn[tt], bn[bf], bn[bt], answers=answers)
        COUNTS[f"rate steps={n}"] += 1
        ask(m, "", answers)
        ask(m, "3", answers)
        if m.wait.kind != "done" or m.lists:
            raise Fail(f"after QUIT: {m.wait}, lists left: {sorted(m.lists)}")
        return True
    except Fail as e:
        fail(f"rate {top}/{bot}", answers, str(e))
        return False


# ------------------------------------------------------------- temperature

TEMP_UNITS = {1: "°F", 2: "°C", 3: "K"}
TEMPS = [("-40", Fr(-40)), ("0", Fr(0)), ("100", Fr(100)), ("98.6", Fr(493, 5)),
         ("-459.67", Fr(-45967, 100)), ("1ᴇ6", Fr(10 ** 6)), ("-273.15", Fr(-27315, 100)),
         ("37", Fr(37)), ("3/4", Fr(3, 4)), ("1ᴇ50", Fr(10) ** 50), ("0.001", Fr(1, 1000))]


def to_c(v: Fr, u: str) -> Fr:
    return {"°F": (v - 32) * Fr(5, 9), "°C": v, "K": v - Fr(27315, 100)}[u]


def from_c(c: Fr, u: str) -> Fr:
    return {"°F": c * Fr(9, 5) + 32, "°C": c, "K": c + Fr(27315, 100)}[u]


def run_temp(f: int, t: int, text: str, v: Fr):
    answers: list[str] = []
    m = fresh()
    try:
        ask(m, "7", answers)
        if m.wait.title != "MORE":
            raise Fail(f"MORE menu is {m.wait.title!r}")
        ask(m, "1", answers)
        shown = since_clear()
        check_text_widths(shown)
        if [e[1] for e in shown if e[0] == "disp"][:2] != ["TEMPERATURE", "1:°F  2:°C  3:K"]:
            raise Fail(f"temperature screen {shown!r}")
        ask(m, str(f), answers)
        ask(m, str(t), answers)
        uf, ut = TEMP_UNITS[f], TEMP_UNITS[t]
        if m.wait.prompt != "HOW MANY? ":
            raise Fail(f"prompt {m.wait.prompt!r}")
        ask(m, text, answers)
        lines = collect_pages(m, answers)
        am = AMOUNT.match(lines[0])
        if not am or am.group(2) != uf:
            raise Fail(f"first line {lines[0]!r}")
        vs = am.group(1)
        check_number(vs, v, "the amount", answers)
        want_lines = [lines[0]]
        if f == t:
            want_lines.append(f" ={vs} {ut} (THE SAME UNIT)")
        else:
            k = 1
            c_shown = vs
            if uf != "°C":
                c = to_c(v, uf)
                form = "C=(F-32)×5/9" if uf == "°F" else "C=K-273.15"
                sub = f"C=({vs}-32)×5/9" if uf == "°F" else f"C={vs}-273.15"
                if lines[k:k + 2] != [form, sub]:
                    raise Fail(f"formula lines {lines[k:k + 2]!r}, expected {[form, sub]!r}")
                r = re.fullmatch(rf" =({NUM}) °C", lines[k + 2] if k + 2 < len(lines) else "")
                if not r:
                    raise Fail(f"°C result line {lines[k + 2:k + 3]!r}")
                check_number(r.group(1), c, "°C", answers)
                c_shown = r.group(1)
                want_lines += [form, sub, lines[k + 2]]
                k += 3
            if ut != "°C":
                y = from_c(to_c(v, uf), ut)
                form = "F=C×9/5+32" if ut == "°F" else "K=C+273.15"
                sub = f"F={c_shown}×9/5+32" if ut == "°F" else f"K={c_shown}+273.15"
                if lines[k:k + 2] != [form, sub]:
                    raise Fail(f"formula lines {lines[k:k + 2]!r}, expected {[form, sub]!r}")
                r = re.fullmatch(rf" =({NUM}) {ut}", lines[k + 2] if k + 2 < len(lines) else "")
                if not r:
                    raise Fail(f"{ut} result line {lines[k + 2:k + 3]!r}")
                check_number(r.group(1), y, ut, answers)
                # what a student gets from the numbers on the screen
                cs = parse_num(c_shown)
                from_screen = from_c(cs, ut)
                if judge(r.group(1), from_screen) == "WRONG":
                    raise Fail(f"{lines[k + 1]} doesn't give {r.group(1)}")
                want_lines += [form, sub, lines[k + 2]]
                y_final = y
            else:
                y_final = to_c(v, uf)
        # colder than 0 K: the program must say so (and only then)
        if to_c(v, uf) < Fr(-27315, 100):
            want_lines += ["BELOW ABSOLUTE ZERO (0 K):", "NOTHING IS THAT COLD."]
        if lines != want_lines:
            raise Fail(f"lines {lines!r}, expected {want_lines!r}")
        ask(m, "", answers)
        if m.wait.title != "WHAT NEXT?":
            raise Fail(f"after DONE: {m.wait}")
        ask(m, "1", answers)                                  # AGAIN
        if "TEMPERATURE" not in [ln.strip() for ln in m.screen.lines()]:
            raise Fail("AGAIN after temperature didn't go back to TEMPERATURE")
        return True
    except Fail as e:
        fail("temperature", answers, str(e))
        return False


# ------------------------------------------------------------- robustness

def bad_numbers(n):
    return ["0", str(n + 1), "2.5", "-1", "1ᴇ99", "-1ᴇ99", "0.0001", str(n) + ".5", "999"]


def robustness():
    ok = 0
    # bad unit numbers are asked again, at FROM and TO, in every category
    for c in range(1, 6):
        n = CAT_COUNT[CATS[c - 1]]
        for where in ("FROM", "TO"):
            answers: list[str] = []
            m = fresh()
            try:
                ask(m, str(c), answers)
                if where == "TO":
                    ask(m, "1", answers)
                for bad in bad_numbers(n):
                    ask(m, bad, answers)
                    if m.wait.kind != "input" or m.wait.prompt != f"{where} (NUMBER):":
                        raise Fail(f"after a bad unit number {bad!r}: {m.wait}")
                ask(m, str(n), answers)
                if where == "FROM":
                    ask(m, "1", answers)
                if not m.wait.prompt.startswith("HOW MANY"):
                    raise Fail(f"didn't get to HOW MANY: {m.wait}")
                ok += 1
            except Fail as e:
                fail("bad unit number", answers, str(e))
    # the same in the rate pickers and temperature
    for seq, where in [(["6", "1"], "FROM"), (["6", "1", "1"], "TO"),
                       (["6", "1", "1", "2", "1"], "FROM"), (["6", "1", "1", "2", "1", "1"], "TO")]:
        answers = []
        m = fresh()
        try:
            for a in seq:
                ask(m, a, answers)
            for bad in bad_numbers(8):                      # LENGTH on top and bottom
                ask(m, bad, answers)
                if m.wait.kind != "input" or m.wait.prompt != f"{where} (NUMBER):":
                    raise Fail(f"after a bad unit number {bad!r}: {m.wait}")
            ok += 1
        except Fail as e:
            fail("bad unit number (rate)", answers, str(e))
    for where in ("FROM", "TO"):
        answers = []
        m = fresh()
        try:
            ask(m, "7", answers)
            ask(m, "1", answers)
            if where == "TO":
                ask(m, "2", answers)
            for bad in bad_numbers(3):
                ask(m, bad, answers)
                if m.wait.kind != "input" or m.wait.prompt != f"{where} (NUMBER):":
                    raise Fail(f"after a bad unit number {bad!r}: {m.wait}")
            ok += 1
        except Fail as e:
            fail("bad unit number (temperature)", answers, str(e))

    # AGAIN repeats the category (single), the rate menu, temperature
    for c in range(1, 6):
        answers = []
        m = fresh()
        try:
            ask(m, str(c), answers)
            for a in ("1", "2", "5", ""):
                ask(m, a, answers)
            for _ in range(3):
                ask(m, "1", answers)                         # AGAIN
                d = [e[1] for e in since_clear(("disp",))]
                if d[:1] != [f"{CATS[c - 1]} UNITS:"]:
                    raise Fail(f"AGAIN showed {d[:1]!r}")
                for a in ("2", "1", "7", ""):
                    ask(m, a, answers)
            ok += 1
        except Fail as e:
            fail("AGAIN", answers, str(e))
    answers = []
    m = fresh()
    try:
        for a in ("6", "1", "8", "6", "1", "3", "1", "60", "", "1"):
            ask(m, a, answers)
        if m.wait.title != "THE TOP UNIT IS A…":
            raise Fail(f"AGAIN after a rate: {m.wait}")
        ok += 1
    except Fail as e:
        fail("AGAIN (rate)", answers, str(e))

    # MORE menu, HOW IT WORKS, BACK, QUIT from everywhere
    answers = []
    m = fresh()
    try:
        ask(m, "7", answers)
        if m.wait.options != ["TEMPERATURE", "HOW IT WORKS", "BACK", "QUIT"]:
            raise Fail(f"MORE menu {m.wait.options}")
        ask(m, "3", answers)
        if m.wait.title != "UNIT CONVERSION":
            raise Fail(f"BACK went to {m.wait}")
        ask(m, "7", answers)
        ask(m, "2", answers)
        shown = since_clear()
        check_text_widths(shown)
        d = [e[1] for e in shown if e[0] == "disp"]
        if len(d) > ROWS - 1 or not d or m.wait.kind != "pause":
            raise Fail(f"HOW IT WORKS: {d}, {m.wait}")
        ask(m, "", answers)
        if m.wait.title != "UNIT CONVERSION":
            raise Fail(f"after HOW IT WORKS: {m.wait}")
        ask(m, "6", answers)
        ask(m, "6", answers)                                  # BACK from the rate menu
        if m.wait.title != "UNIT CONVERSION":
            raise Fail(f"rate BACK went to {m.wait}")
        ask(m, "7", answers)
        ask(m, "4", answers)
        if m.wait.kind != "done" or m.lists or m.leaks:
            raise Fail(f"QUIT from MORE: {m.wait}, lists {sorted(m.lists)}")
        ok += 1
    except Fail as e:
        fail("menus", answers, str(e))
    for seq in [("7", "4"), ("1", "8", "6", "5", "", "3"),
                ("6", "2", "2", "3", "4", "1", "2", "1", "", "3"),
                ("7", "1", "1", "3", "0", "", "3"),
                ("1", "1", "2", "3", "", "2", "7", "4"),             # UNITS MENU then quit
                ("6", "6", "7", "4")]:
        answers = []
        m = fresh()
        try:
            for a in seq:
                ask(m, a, answers)
            if m.wait.kind != "done" or m.lists or m.leaks or m.blocks:
                raise Fail(f"after QUIT: {m.wait}, lists {sorted(m.lists)}, leaks {m.leaks}")
            if any(ln.strip() for ln in m.screen.lines()):
                raise Fail("QUIT left text on the screen")
            ok += 1
        except Fail as e:
            fail("QUIT", answers, str(e))

    # 30 rounds on one machine, mixing AGAIN and UNITS MENU, then QUIT
    rnd = random.Random(SEED + 1)
    answers = []
    m = fresh()
    try:
        mode = None
        for i in range(30):
            kind = rnd.choice(["single", "rate", "temp"])
            if mode != kind:
                if mode is not None:
                    ask(m, "2", answers)                      # UNITS MENU
                if kind == "single":
                    ask(m, str(rnd.randint(1, 5)), answers)
                elif kind == "rate":
                    ask(m, "6", answers)
                else:
                    ask(m, "7", answers)
                    ask(m, "1", answers)
                mode = kind
            else:
                ask(m, "1", answers)                          # AGAIN
            if kind == "rate":
                ask(m, str(rnd.randint(1, 5)), answers)
                for a in ("1", "2"):
                    ask(m, a, answers)
                ask(m, str(rnd.randint(1, 5)), answers)
            for a in ("2", "1", "7"):
                ask(m, a, answers)
            while m.wait.kind == "pause":
                ask(m, "", answers)
            if m.wait.title != "WHAT NEXT?":
                raise Fail(f"round {i + 1}: {m.wait}")
            if m.lists.get("UQ"):
                raise Fail(f"round {i + 1}: call stack UQ not empty: {m.lists['UQ']}")
        ask(m, "3", answers)
        if m.wait.kind != "done" or m.lists or m.leaks or m.blocks:
            raise Fail(f"after 30 rounds and QUIT: {m.wait}, lists {sorted(m.lists)}, "
                       f"leaks {m.leaks}, blocks {m.blocks}")
        COUNTS["30-round max block depth"] = m.max_depth
        ok += 1
    except Fail as e:
        fail("30 rounds", answers, str(e))

    # from BRAINIAC (both angle modes): 5, 2, use it, quit back, solve
    for degree in (False, True):
        answers = []
        m = fresh("BRAINIAC", degree=degree)
        try:
            ask(m, "5", answers)
            if m.wait.title != "GEOMETRY & UNITS":
                raise Fail(f"BRAINIAC 5 is {m.wait.title!r}")
            ask(m, "2", answers)
            if m.wait.title != "UNIT CONVERSION":
                raise Fail(f"BRAINIAC 5,2 is {m.wait.title!r}")
            for a in ("1", "8", "4", "5", "", "1", "3", "6", "10", "", "2",
                      "6", "1", "8", "6", "1", "3", "1", "60", "", "1",
                      "2", "2", "3", "4", "1", "2", "1", "", "2",
                      "7", "1", "1", "3", "98.6", "", "1", "3", "2", "0", "", "2",
                      "7", "2", "", "7", "4"):
                ask(m, a, answers)
            if m.wait.title != "BRAINIAC 0.5":
                raise Fail(f"UNITS QUIT went to {m.wait}")
            left = sorted(k for k in m.lists if k.startswith("U"))
            if left:
                raise Fail(f"UNITS lists left inside BRAINIAC: {left}")
            ask(m, "1", answers)
            ask(m, "5X=30", answers)
            if "X=6" not in [ln.strip() for ln in m.screen.lines()]:
                raise Fail(f"BRAINIAC after UNITS: {m.screen.lines()}")
            # the units again, and quit BRAINIAC from its own menu afterwards
            while m.wait.kind == "pause":
                ask(m, "", answers)
            ask(m, "2", answers)                             # MAIN MENU
            for a in ("5", "2", "7", "4", "7"):
                ask(m, a, answers)
            if m.wait.kind != "done":
                raise Fail(f"BRAINIAC QUIT: {m.wait}")
            left = sorted(m.lists)
            if left:
                note("lists left after BRAINIAC QUIT", left)
            if m.radians == degree:
                raise Fail("BRAINIAC didn't put the angle mode back")
            ok += 1
        except Fail as e:
            fail(f"from BRAINIAC ({'Degree' if degree else 'Radian'})",
                 ["BRAINIAC"] + answers, str(e))
    return ok


def source_checks():
    n = 0
    for line in source_lines("UNITS"):
        if line.startswith("Menu("):
            args = split_args(line[5:].rstrip(")"))
            title, opts = args[0].strip('"'), [a.strip('"') for a in args[1::2]]
            if len(opts) > 7:
                fail("source", [], f"menu with {len(opts)} options: {line}")
            for o in opts:
                if len(o) > 24:
                    fail("source", [], f"menu option over 24 chars: {o!r}")
            if len(title) > COLS:
                fail("source", [], f"menu title over 26: {title!r}")
            n += 1
        for s in re.findall(r'Disp ((?:"[^"]*",?)+)', line):
            for lit in re.findall(r'"([^"]*)"', s):
                if len(lit) > COLS:
                    fail("source", [], f"Disp text over 26: {lit!r}")
    return n


# ------------------------------------------------------------- main

def main():
    t0 = time.time()
    rnd = random.Random(SEED)
    stats = Counter()
    nmenus = source_checks()
    stats["source menus"] = nmenus

    for c, cat in enumerate(CATS, 1):
        n = CAT_COUNT[cat]
        for f in range(1, n + 1):
            for t in range(1, n + 1):
                for text, v in AMOUNTS:
                    stats["single"] += 1
                    stats["single ok"] += run_single(c, f, t, text, v)

    n_rates = 600 if QUICK else 4000
    cases = []
    for tc in range(1, 6):
        for bc in range(1, 6):
            nt, nb = CAT_COUNT[CATS[tc - 1]], CAT_COUNT[CATS[bc - 1]]
            for same in ("top", "bottom", "both"):
                tf = rnd.randint(1, nt)
                bf = rnd.randint(1, nb)
                tt = tf if same in ("top", "both") else rnd.randint(1, nt)
                bt = bf if same in ("bottom", "both") else rnd.randint(1, nb)
                cases.append((tc, tf, tt, bc, bf, bt))
    while len(cases) < n_rates:
        tc, bc = rnd.randint(1, 5), rnd.randint(1, 5)
        nt, nb = CAT_COUNT[CATS[tc - 1]], CAT_COUNT[CATS[bc - 1]]
        cases.append((tc, rnd.randint(1, nt), rnd.randint(1, nt),
                      bc, rnd.randint(1, nb), rnd.randint(1, nb)))
    for case in cases:
        text, v = AMOUNTS[rnd.randrange(len(AMOUNTS))]
        stats["rate"] += 1
        stats["rate ok"] += run_rate(*case, text, v)

    for f in (1, 2, 3):
        for t in (1, 2, 3):
            for text, v in TEMPS + [("-500", Fr(-500))]:
                stats["temperature"] += 1
                stats["temperature ok"] += run_temp(f, t, text, v)

    stats["robustness ok"] = robustness()

    # ---- report
    print(f"UNITS stress test ({time.time() - t0:.0f} s)")
    for k in ("single", "rate", "temperature"):
        print(f"  {k:12s} {stats[k + ' ok']}/{stats[k]} passed")
    print(f"  robustness   {stats['robustness ok']} scenarios passed")
    print(f"  source       {nmenus} Menu( lines checked")
    print("  chains:", dict(sorted(COUNTS.items())))
    if INFO:
        print("\nNotes (not failures):")
        for k, n in sorted(INFO.items()):
            print(f"  {n:6d}  {k}")
            for ex in EXAMPLES[k]:
                print(f"            e.g. {ex}")
    if FAILS:
        groups = defaultdict(list)
        for kind, answers, msg in FAILS:
            groups[(kind, re.sub(r"[⁻\d.ᴇ]+", "#", msg)[:70])].append((answers, msg))
        print(f"\n{len(FAILS)} FAILURES in {len(groups)} groups:")
        for (kind, _), items in sorted(groups.items(), key=lambda kv: -len(kv[1])):
            print(f"\n  [{kind}] x{len(items)}")
            for answers, msg in items[:5]:
                print(f"    UNITS answers {answers}\n      {msg}")
        return 1
    print("\nALL PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
