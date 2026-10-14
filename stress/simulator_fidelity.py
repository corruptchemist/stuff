"""Random walks through BRAINIAC, GEOMETRY and UNITS on the strict simulator.

    python stress/simulator_fidelity.py [--walks N] [--seed S] [--old PATH]

Each walk starts a program fresh (Radian or Degree, Real or a+bi), answers menus
with random choices, inputs from pools of typical student input, and pauses
with ENTER, until the program ends or a step budget runs out. Any ERR, a
leak, or a block left open is a failure, printed with its exact answer
sequence. With --old PATH (a copy of an older brainiac/tibasic.py), every
failing walk is replayed on that simulator too, to show which failures only
the newer, stricter rules catch.

Exit code 0 = no failures. Deterministic for a given seed.
"""

from __future__ import annotations

import argparse
import importlib.util
import random
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from brainiac import tibasic  # noqa: E402
from brainiac.build import PROGRAMS, program_bytes  # noqa: E402

EQUATIONS = [
    "X^2-5X+6=0", "2X+3=7", "5X+20-3=20", "X^2+5X+20=0", "X^2+6X+9=0", "X^2-2=0", "1/X=4",
    "X^3=6X^2-11X+6", "2X+1=2X+1", "X+1=X+2", "X^2-4", "2^X=X+3", "abs(X)+1=X^2",
    "sqrt(X)+1=X", "cos(X)=X", "e^(X^2)=X+5", "abs(2X-3)=7", "7=abs(2X-3)", "abs(X-1)=2X",
    "sqrt(X+5)=X-1", "sqrt(2X+3)=X", "sqrt(X)=3", "\\xBD(X+1)=2", "(2X+1)/(X-3)=5",
    "1/(X-1)+1/(X+1)=1", "3/(X-2)+1=X/(X-2)", "(X^2-4)/(X-2)=4", "2^X=16", "3^(X+1)=81",
    "(1/2)^X=8", "4^X=2^(X+3)", "2^X+5=3", "e^(X)=10", "4e^(2X)=20", "3e^(X)-2=10",
    "ln(X)=2", "log(X)=2", "log(X)+log(X-3)=1", "log(2X+4)=1", "ln(X)+ln(X+1)=ln(6)",
    "log(X)=log(2X-5)", "log(X^2)=2", "sin(X)=.5", "sin(X)=2", "2sin(30)X=4", "X+ln(X)=1",
    "X^2-4<0", "X^2-4>=0", "2X+3>7", "X^3-X>0", "abs(X-1)<3", "(X-1)/(X+2)<=0",
    "1/(X-1)>0", "(X-2)^2>0", "(X-2)^2<=0", "X^2+1<0", "X^2+1>0", "sqrt(X)<2", "log(X)<2",
    "ln(X)>=0", "5(2^X)<=40", "sin(X)>.5", "sqrt(4-X^2)>=1", "sqrt(X+5)<=X-1",
    "X^5+X+1=0", "X^4-1=0", "X=X", "2X=", "=5", "X^2=-4", "X^2=0", "0X=5", "X/0=1",
    "X^3-1000000=0", "1E5X=1", "X^2=1E-6", ".001X^2-1000=0", "X^10=1024", "10^(X)=1000",
    "\\xC1X)=1000", "(X-1)(X-2)(X-3)(X-4)=0", "X^2+X+1=0", "tan(X)=1", "X^2-2X+1>0",
    "2X+3=2X+3", "X^2=X^2+1", "abs(X)=-1", "sqrt(X)=-2", "e^(X)=0", "1/X=0",
    "2X+3=7)", "X^2-4=0)", "sqrt(X+5=X-1",          # typos: a stray ), a missing )
]
EXPRESSIONS = [
    "X^4-1", "X^3-2X+5", "X^5+X+1", "2X^2+3X-5", "(X-1)(X+3)", "3(X-2)^2+1", "X^3-8",
    "X^2+1", "(3X^3-X^2-6)(X-5)", "(X^2-1)/(X-1)", "X^3-4X", "X^2", "5", "X^4+5X^2+6",
    "(X+1)^3", "4X^2-12X+9", "6X^2+X-2", "X^5-X", "1/X+1/X^2", "(X^3-1)/(X-1)",
    "3X(X-2)+(X+1)^2", "X^2/2+X+3", "sin(X)", "cos(X)+1", "ln(X)", "sqrt(X)-2",
    "e^(X^3)-2", "tan(X)", "X^6-1", "(2X+5)(X-1)", "X^2-6X+9", "X^8-1", "1E-3X^2",
    "X^2+1E6", "0", "X-X", "2X", "X", "12X^2Y+18XY^2", "6AB+9A", "(A+B)^2-(A-B)^2",
    "X^2-1)", "(X+1)(X-1",
]
FORMULAS = [
    ("A=BH/2", "H"), ("A=BH/2", "B"), ("F=9C/5+32", "C"), ("A=P(1+R)^T", "P"),
    ("A=P+PRT", "P"), ("C=sqrt(A^2+B^2)", "A"), ("PV=NRT", "T"), ("Y=MX+B", "X"),
    ("T=2pisqrt(L/G)", "L"), ("1/F=1/A+1/B", "A"), ("V=piR^2H", "R"), ("P=2L+2W", "W"),
    ("Y=AX^2+BX+C", "X"), ("A=(B+C)H/2", "B"), ("I=V/R", "R"), ("AB=C", "B"),
    ("X^2=4Y^2", "X"), ("A=BH", "Q"), ("A=BH", "A"),
]
SYSTEM = ["X+Y=10", "X-Y=2", "2X+3Y=7", "X+Y+Z=6", "X-Y+2Z=3", "2X+3Y-Z=1", "X+Y=1",
          "X+Y=2", "X-Y=0", "X/2+Y/3=1", "A+B=3", "A-B=1", "2A+B=5", "X^2+Y=3", "X+Y=",
          "Y=4", "X=2", "3X+Y+Z=6", "Y+2Z=6", "X-Z=0", "2X+2Y=20", "X+Y=10)"]
NUMBERS = ["3", "4", "5", "0", "10", "2.5", "45", "30", "60", "90", "100", "1E-5", "-2",
           "1000", ".001", "12", "7", "1", "180", "1E6", "13", "8", "6", "0.5", "120",
           "2", "9", "25", "pi", "sqrt(2)", "1/3", "359", "1E50"]
INVERSES = ["2X+3", "X^3", "(X+1)/(X-2)", "sqrt(X)", "e^(X)", "X^2", "3X", "1/X",
            "(2X-1)/5", "X^3+1", "ln(X)", "5"]


def pool_for(prompt: str, rng: random.Random, state: dict) -> str:
    p = prompt.strip()
    if p in ("EQ:",):
        return rng.choice(EQUATIONS)
    if p.startswith("EQ") and p.endswith(":"):
        return rng.choice(SYSTEM)
    if p == "HOW MANY:":
        return rng.choice(["2", "2", "3", "1", "0", "7", "2.5"])
    if p == "FORMULA:":
        f, v = rng.choice(FORMULAS)
        state["var"] = v
        return f
    if p == "SOLVE FOR:":
        return state.pop("var", rng.choice("ABHXQ"))
    if p in ("F(X)=",):
        return rng.choice(EXPRESSIONS + INVERSES)
    if p in ("P(X)=", "EXPR:", "TOP:", "BOTTOM:"):
        return rng.choice(EXPRESSIONS)
    if p in ("FROM:", "TO:"):
        return rng.choice(["-10", "0", "10", "1", "100", "-1E3", "5"])
    if "(NUMBER)" in p:
        return rng.choice("12345678") if rng.random() < .9 else rng.choice(["9", "0"])
    return rng.choice(NUMBERS)


def make_tokens():
    return {n: tibasic.tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}


def fresh(mod, tokens, name, mode):
    """mode = (Degree?, a+bi?): the calculator's settings before the start."""
    m = mod.Machine(tokens[name])
    m.programs.update({k: v for k, v in tokens.items() if k != name})
    m.radians = not mode[0]
    m.complex_mode = mode[1]
    m.run()
    return m


def replay(mod, tokens, name, mode, answers):
    m = fresh(mod, tokens, name, mode)
    for a in answers:
        if m.wait.kind not in ("menu", "input", "pause"):
            break
        try:
            m.answer(a)
        except RuntimeError:
            break
    return m


def var_bytes(m) -> int:
    """Roughly how much RAM the program's variables take on the calculator:
    9 bytes a real number, 18 a complex one, a byte a token, plus a symbol
    table entry (about 15 bytes) per variable."""
    n = 0
    for lst in m.lists.values():
        n += 15 + 2 + (18 if any(isinstance(x, complex) for x in lst) else 9) * len(lst)
    for s in m.strings.values():
        n += 15 + 2 + len(s.tokens)
    for mat in m.mats.values():
        r, c = mat.dims
        n += 15 + 2 + 9 * r * c
    n += sum(15 + (18 if isinstance(v, complex) else 9) for v in m.reals.values())
    return n


def walk(tokens, rng, max_answers=14):
    name = rng.choice(["BRAINIAC"] * 6 + ["GEOMETRY"] * 3 + ["UNITS"])
    mode = (rng.random() < 0.4, rng.random() < 0.3)
    m = fresh(tibasic, tokens, name, mode)
    answers, state = [], {}
    for _ in range(max_answers):
        w = m.wait
        if w.kind == "menu":
            n = len(w.options)
            # rather not the last option (usually BACK/QUIT) so walks go deep
            a = str(rng.randint(1, n - 1 if n > 1 and rng.random() < .85 else n))
        elif w.kind == "input":
            a = pool_for(w.prompt, rng, state)
        elif w.kind == "pause":
            a = ""
        else:
            break
        answers.append(a)
        try:
            m.answer(a)
        except RuntimeError:
            break
        m.peak_bytes = max(getattr(m, "peak_bytes", 0), var_bytes(m))
        while m.wait.kind == "pause":            # read every page
            answers.append("")
            m.answer("")
            m.peak_bytes = max(m.peak_bytes, var_bytes(m))
    return name, mode, answers, m


def problems(m):
    out = []
    if m.wait.kind == "error":
        out.append(f"{m.wait.message} at: {m.statement_text()}")
    if m.leaks:
        out.append(f"leaks={m.leaks}")
    if m.wait.kind == "done" and m.blocks:
        out.append(f"blocks left open: {m.blocks}")
    for row, col, full, shown in getattr(m.graph, "clipped", []):
        out.append(f"Text({row},{col},{full!r}) runs off the right edge: shows {shown!r}")
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--walks", type=int, default=600)
    ap.add_argument("--seed", type=int, default=84)
    ap.add_argument("--old", help="an older tibasic.py to replay failures on")
    args = ap.parse_args()

    old = None
    if args.old:
        spec = importlib.util.spec_from_file_location("tibasic_old", args.old)
        old = importlib.util.module_from_spec(spec)
        sys.modules["tibasic_old"] = old
        spec.loader.exec_module(old)
        old_tokens = {n: old.tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}

    tokens = make_tokens()
    rng = random.Random(args.seed)
    t0 = time.time()
    failures, seen = [], set()
    kinds = {}
    peak = (0, None, None)
    for i in range(args.walks):
        name, mode, answers, m = walk(tokens, rng)
        kinds[name] = kinds.get(name, 0) + 1
        if getattr(m, "peak_bytes", 0) > peak[0]:
            peak = (m.peak_bytes, name, answers)
        found = problems(m)
        if not found:
            continue
        key = (name, tuple(found))
        if key in seen:
            continue
        seen.add(key)
        line = (f"[{name} {'Degree' if mode[0] else 'Radian'}"
                f"{' a+bi' if mode[1] else ''}] answers={answers!r}\n    "
                + "; ".join(found))
        if old is not None:
            mo = replay(old, old_tokens, name, mode, answers)
            line += ("\n    old simulator: " + ("; ".join(problems(mo)) or
                                                 f"no error (ends {mo.wait.kind})"))
        failures.append(line)
    print(f"{args.walks} walks ({', '.join(f'{k} {v}' for k, v in sorted(kinds.items()))}) "
          f"in {time.time() - t0:.0f} s: {len(failures)} distinct failures")
    print(f"most RAM the variables took (estimate): {peak[0]} bytes, in {peak[1]} "
          f"answers={peak[2]!r}")
    for f in failures:
        print(f)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
