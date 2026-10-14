#!/usr/bin/env python3
"""Robustness of the whole user interface of BRAINIAC, GEOMETRY and UNITS.

    python stress/fuzz.py              # everything (a few minutes)
    python stress/fuzz.py --quick      # a smaller fuzz corpus
    python stress/fuzz.py --parts 1,4  # only some parts
    python stress/fuzz.py --jobs 2     # worker processes for the fuzz (default 2)
    python stress/fuzz.py --parts 2 --only BRAINIAC     # fuzz one program's prompts
    FUZZ_SRC=some/dir python stress/fuzz.py             # programs built from some/dir/*.txt

In the fuzz inputs, ‒ (U+2012) stands for the [−] key pressed where (-) was
meant; everything else is typed through brainiac.tibasic.keyboard_to_tokens.

Four parts, all on the simulated TI-84 Plus CE (brainiac/tibasic.py):

1. Menu crawl. Every menu reachable from each program's start (BRAINIAC,
   including HELP ME CHOOSE and GEOMETRY & UNITS -> both programs, and
   GEOMETRY and UNITS run on their own) is visited, every option is taken,
   down to the first Input. Every Input is then completed with sensible
   answers to reach the WHAT NEXT? menu, whose options are taken too. Each
   BACK / MAIN MENU / GEOMETRY MENU / UNITS MENU / AGAIN / QUIT / BACK TO
   BRAINIAC option is checked against where its label says it goes.
2. Input fuzzing. Every Input prompt found in part 1 gets the adversarial
   corpus below (empty, =, brackets, stray operators, huge/tiny numbers,
   complex numbers, long and nested inputs, lists, quotes, odd tokens, plus
   deterministic random token soup). The program must show something and get
   back to a menu or a prompt. Every ERR is classified (at the Input itself,
   which the calculator lets the user re-type, vs. in the program's own code
   afterwards = a crash the program could prevent), every simulator Python
   exception and every hang (step limit) is reported.
3. Long sessions: 100 rounds mixing all modes through AGAIN and MAIN MENU in
   one Machine, in both angle modes: leaks stay 0, the #call stacks (BS GS UQ)
   are empty at every menu, no list grows without bound.
4. QUIT from every program restores the calculator: angle mode, Real,
   graph window (GEOMETRY), and every list the program made is deleted.

Exit code 0 when everything passes. Failures are printed with the exact
answer sequence from a fresh start.
"""

from __future__ import annotations

import argparse
import multiprocessing as mp
import os
import random
import re
import signal
import sys
import time
import traceback
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import brainiac.tibasic as tibasic  # noqa: E402
from brainiac.build import PROGRAMS, expand, program_bytes, raw_lines, source_path, statements  # noqa: E402
from brainiac.tibasic import Machine, Tok, split_statements, tokens_from_bytes  # noqa: E402

# The keyboard mapper turns a typed - into (-) wherever a negative number makes
# sense, so it can't show what happens when a student presses the [−] key where
# (-) was meant (the README's most repeated warning). Here ‒ (U+2012) is that
# key: the subtraction token, wherever it is typed.
MINUS_KEY = "‒"
_keyboard = tibasic.keyboard_to_tokens


def _keyboard_with_minus_key(text):
    out = []
    for i, part in enumerate(text.split(MINUS_KEY)):
        if i:
            out.append(Tok("-", "-"))
        if part.strip():
            out += _keyboard(part)
    return out


tibasic.keyboard_to_tokens = _keyboard_with_minus_key

# FUZZ_SRC=dir runs the programs built from dir/*.txt instead of brainiac/src
# (to try candidate fixes without touching the sources)
if os.environ.get("FUZZ_SRC"):
    import brainiac.build as _build
    _alt = Path(os.environ["FUZZ_SRC"]).resolve()
    _build.source_path = source_path = lambda name=_build.NAME: _alt / f"{name}.txt"

TOK = {n: tokens_from_bytes(program_bytes(n)) for n in PROGRAMS}
STACKS = dict(PROGRAMS)                       # program -> its #call stack list
PROG_BY_LEN = {len(split_statements(TOK[n])): n for n in TOK}
assert len(PROG_BY_LEN) == len(TOK), "programs need distinct statement counts"

STEP_BUDGET = 1_500_000        # steps per fuzz case before we call it a hang
WALL_BUDGET = 60               # seconds per fuzz case before we call the simulator stuck


# -- where a statement comes from in the source ------------------------------------

def _source_map(name):
    """Statement index -> (source line, enclosing @label or #sub)."""
    raw = source_path(name).read_text(encoding="utf-8").splitlines()
    label_at, cur = {}, "start"
    for n, line in enumerate(raw, 1):
        s = line.strip()
        if s.startswith("Lbl @"):
            cur = s[5:]
        elif s.startswith("#sub "):
            cur = "sub " + s.split()[1]
        label_at[n] = cur
    out = []
    for n, line in expand(raw_lines(source_path(name)), PROGRAMS[name]):
        out += [(n, label_at.get(n, "?"))] * len(statements(line))
    assert len(out) == len(split_statements(TOK[name])), name
    return out


SRC = {n: _source_map(n) for n in TOK}


def prog(m):
    return PROG_BY_LEN[len(m.stmts)]


def where(m):
    """'BRAINIAC.txt:1622 [m_solve]' for the statement the machine is on."""
    p = prog(m)
    if 0 <= m.pc < len(SRC[p]):
        line, label = SRC[p][m.pc]
        return f"{p}.txt:{line} [{label}]"
    return f"{p}.txt:? (pc {m.pc})"


# -- driving --------------------------------------------------------------------------

def fresh(name="BRAINIAC", degree=False, max_steps=10**9):
    m = Machine(TOK[name], max_steps=max_steps)
    m.programs.update({k: v for k, v in TOK.items() if k != name})
    m.radians = not degree
    m.run()
    return m


def replay(root, path, degree=False, max_steps=10**9):
    m = fresh(root, degree, max_steps)
    for a in path:
        if m.wait.kind not in ("menu", "input", "pause"):
            raise RuntimeError(f"replay of {path} stopped early: {m.wait}")
        m.answer(a)
    return m


def node(m):
    """Identity of what the machine waits on: (program, statement, kind)."""
    w = m.wait
    if w.kind in ("menu", "input"):
        return (prog(m), m.pc, w.kind)
    if w.kind == "pause":
        return (prog(m), m.pc, "pause")
    return (w.kind,)


def ident(m):
    """A readable key for an Input: (program, label, prompt)."""
    p = prog(m)
    return (p, SRC[p][m.pc][1], m.wait.prompt if m.wait.kind == "input" else m.wait.title)


def settle(m, path, limit=400):
    """Answer ENTER to every Pause (pages, figures, help) until something else."""
    path = list(path)
    n = 0
    while m.wait.kind == "pause" and n < limit:
        m.answer("")
        path.append("")
        n += 1
    return tuple(path)


def stack_sizes(m):
    return {s: len(m.lists.get(s, [])) for s in STACKS.values()}


def fmt_path(root, path, degree=False):
    mode = "Degree" if degree else "Radian"
    return f"{root} ({mode}): " + " | ".join(repr(a) for a in path)


# -- sensible answers for every Input, to get through a mode -------------------------
# keyed by (program, enclosing label, prompt); UNITS' amount prompt has the unit name
# in it, so it is matched by prefix.

BENIGN = {
    ("BRAINIAC", "m_solve", "EQ:"): "2X+3=7",
    ("BRAINIAC", "m_solve", "SOLVE FOR:"): "H",
    ("BRAINIAC", "m_zeros", "F(X)="): "X^3-4X",
    ("BRAINIAC", "m_roots", "P(X)="): "X^4-1",
    ("BRAINIAC", "range", "FROM:"): "-5",
    ("BRAINIAC", "range", "TO:"): "5",
    ("BRAINIAC", "m_rearr", "FORMULA:"): "A=BH/2",
    ("BRAINIAC", "m_rearr", "SOLVE FOR:"): "H",
    ("BRAINIAC", "m_inv", "F(X)="): "(X+1)/(X-2)",
    ("BRAINIAC", "m_quad", "EQ:"): "2X^2+3X-5",
    ("BRAINIAC", "m_simp", "EXPR:"): "3X(X-2)+(X+1)^2",
    ("BRAINIAC", "m_fact", "EXPR:"): "X^2-9",
    ("BRAINIAC", "m_div", "TOP:"): "X^3-1",
    ("BRAINIAC", "m_div", "BOTTOM:"): "X-1",
    ("BRAINIAC", "m_sys", "HOW MANY:"): "2",
    ("BRAINIAC", "sy_in", "EQ1:"): "X+Y=3",
    ("BRAINIAC", "sy_in", "EQ2:"): "X-Y=1",
    ("BRAINIAC", "sy_in", "EQ3:"): "Z=3",
    ("BRAINIAC", "sy_in", "EQ4:"): "W=4",
    ("BRAINIAC", "sy_in", "EQ5:"): "V=5",
    ("BRAINIAC", "sy_in", "EQ6:"): "U=6",
    ("GEOMETRY", "ta", "ANGLE A="): "50", ("GEOMETRY", "ta", "ANGLE B="): "60",
    ("GEOMETRY", "py", "A="): "3", ("GEOMETRY", "py", "B="): "4", ("GEOMETRY", "py", "C="): "0",
    ("GEOMETRY", "sp45", "LEG="): "1", ("GEOMETRY", "sp45", "HYP="): "0",
    ("GEOMETRY", "sp30", "SHORT="): "1", ("GEOMETRY", "sp30", "LONG="): "0",
    ("GEOMETRY", "sp30", "HYP="): "0",
    ("GEOMETRY", "tg", "ANGLE A="): "30", ("GEOMETRY", "tg", "OPP="): "5",
    ("GEOMETRY", "tg", "ADJ="): "0", ("GEOMETRY", "tg", "HYP="): "0",
    ("GEOMETRY", "sm", "A="): "3", ("GEOMETRY", "sm", "B="): "4", ("GEOMETRY", "sm", "C="): "5",
    ("GEOMETRY", "sm", "A'="): "6",
    ("GEOMETRY", "il", "ANGLE="): "40",
    ("GEOMETRY", "pl", "ANGLE 1="): "50",
    ("GEOMETRY", "at3", "A="): "3", ("GEOMETRY", "at3", "B="): "4", ("GEOMETRY", "at3", "C="): "5",
    ("GEOMETRY", "atb", "B="): "4", ("GEOMETRY", "atb", "H="): "3",
    ("GEOMETRY", "a_rec", "L="): "3", ("GEOMETRY", "a_rec", "W="): "4",
    ("GEOMETRY", "a_trap", "B1="): "4", ("GEOMETRY", "a_trap", "B2="): "6",
    ("GEOMETRY", "a_trap", "H="): "3", ("GEOMETRY", "a_trap", "S3="): "3.5",
    ("GEOMETRY", "a_trap", "S4="): "3.2",
    ("GEOMETRY", "cb_r1", "LENGTH="): "3", ("GEOMETRY", "cb_r1", "WIDTH="): "4",
    ("GEOMETRY", "cb_t1", "BASE="): "4", ("GEOMETRY", "cb_t1", "HEIGHT="): "3",
    ("GEOMETRY", "cb_c1", "RADIUS="): "1", ("GEOMETRY", "cb_h1", "RADIUS="): "1",
    ("GEOMETRY", "v_box", "L="): "2", ("GEOMETRY", "v_box", "W="): "3",
    ("GEOMETRY", "v_box", "H="): "4",
    ("GEOMETRY", "v_cyl", "R="): "2", ("GEOMETRY", "v_cyl", "H="): "5",
    ("GEOMETRY", "v_cone", "R="): "3", ("GEOMETRY", "v_cone", "H="): "4",
    ("GEOMETRY", "v_sph", "R="): "2",
    ("GEOMETRY", "v_pyr", "S="): "4", ("GEOMETRY", "v_pyr", "H="): "3",
    ("GEOMETRY", "c_solve", "R="): "2", ("GEOMETRY", "c_solve", "D="): "0",
    ("GEOMETRY", "c_solve", "C="): "0", ("GEOMETRY", "c_solve", "AREA="): "0",
    ("GEOMETRY", "c_arc", "R="): "5", ("GEOMETRY", "c_arc", "T="): "60",
    ("GEOMETRY", "ce_build", "H="): "1", ("GEOMETRY", "ce_build", "K="): "2",
    ("GEOMETRY", "ce_build", "R="): "3",
    ("GEOMETRY", "ce_read", "D="): "-2", ("GEOMETRY", "ce_read", "E="): "-4",
    ("GEOMETRY", "ce_read", "F="): "-4",
    ("GEOMETRY", "sc", "K="): "2",
    ("UNITS", "pk_f", "FROM (NUMBER):"): "2", ("UNITS", "pk_t", "TO (NUMBER):"): "3",
    ("UNITS", "single", "HOW MANY"): "5", ("UNITS", "rbot", "HOW MANY"): "60",
    ("UNITS", "amt1", "HOW MANY"): "5", ("UNITS", "amt2", "HOW MANY"): "60",
    ("UNITS", "amt3", "HOW MANY"): "212",
    ("UNITS", "tp_f", "FROM (NUMBER):"): "1", ("UNITS", "tp_t", "TO (NUMBER):"): "2",
    ("UNITS", "tp_t", "HOW MANY"): "212",
}

# menus inside a mode that need a choice to finish it (not navigation)
MENU_CHOICE = {
    ("GEOMETRY", "COMBINED SHAPE"): "6",       # DONE
    ("UNITS", "THE BOTTOM UNIT IS A…"): "1",    # TIME (MI/HR)
}


def benign(m):
    p, label, prompt = ident(m)
    if (p, label, prompt) in BENIGN:
        return BENIGN[(p, label, prompt)]
    for (bp, bl, bprompt), v in BENIGN.items():
        if bp == p and bl == label and prompt.startswith(bprompt):
            return v
    raise KeyError(f"no sensible answer for {(p, label, prompt)}")


def is_next_menu(m):
    return m.wait.kind == "menu" and m.wait.title == "WHAT NEXT?"


def finish_mode(m, path, limit=40):
    """Answer sensibly until the WHAT NEXT? menu (or anything unexpected)."""
    path = list(path)
    for _ in range(limit):
        path = list(settle(m, path))
        w = m.wait
        if w.kind == "input":
            a = benign(m)
        elif w.kind == "menu" and (prog(m), w.title) in MENU_CHOICE:
            a = MENU_CHOICE[(prog(m), w.title)]
        else:
            break
        m.answer(a)
        path.append(a)
    return tuple(path)


# -- part 1: the menu crawl ---------------------------------------------------------

MAIN_TITLES = {n: fresh(n).wait.title for n in ("BRAINIAC", "GEOMETRY", "UNITS")}


class Report:
    def __init__(self):
        self.failures = []          # (part, text)
        self.notes = []             # (part, text)  not failures, but worth reading
        self.counts = Counter()

    def fail(self, part, text):
        self.failures.append((part, text))

    def note(self, part, text):
        self.notes.append((part, text))


def describe(m):
    w = m.wait
    if w.kind == "menu":
        return f"menu {w.title!r} ({prog(m)})"
    if w.kind == "input":
        return f"input {w.prompt!r} ({prog(m)})"
    if w.kind == "error":
        return f"{w.message} at {m.statement_text()!r}"
    return w.kind


def crawl(root, rep: Report):
    """BFS over menus; returns (menus, inputs, edges)."""
    m = fresh(root)
    start = node(m)
    menus, inputs = {}, {}
    edges = []                    # (from_node, option, to_node, path_taken)
    queue, seen = [(start, ())], {start}
    while queue:
        nd, path = queue.pop(0)
        m = replay(root, path)
        w = m.wait
        if w.kind == "input":
            inputs[nd] = dict(prompt=w.prompt, path=path, ident=ident(m))
            continue
        if w.kind != "menu":
            continue
        menus[nd] = dict(title=w.title, options=list(w.options), path=path)
        rep.counts["menus visited"] += 1
        if len(w.title) > 26:
            rep.fail(1, f"menu title wider than the screen: {w.title!r}")
        for i, opt in enumerate(w.options, 1):
            if len(opt) > 24:
                rep.fail(1, f"menu option wider than 24 columns: {opt!r} in {w.title!r}")
            p2 = path + (str(i),)
            m2 = replay(root, p2)
            p2 = settle(m2, p2)
            d = node(m2)
            rep.counts["menu options taken"] += 1
            edges.append((nd, opt, d, p2, describe(m2)))
            if m2.wait.kind == "error":
                rep.fail(1, f"ERR choosing {opt!r}: {describe(m2)} at {where(m2)}\n    "
                         + fmt_path(root, p2))
            if m2.leaks or m2.blocks:
                rep.fail(1, f"leak/blocks after {opt!r}: leaks={m2.leaks} blocks={m2.blocks}\n"
                         f"    " + fmt_path(root, p2))
            if m2.wait.kind == "menu" and any(stack_sizes(m2).values()):
                rep.fail(1, f"#call stack not empty at menu {m2.wait.title!r}: "
                         f"{stack_sizes(m2)}\n    " + fmt_path(root, p2))
            if d not in seen and m2.wait.kind in ("menu", "input"):
                seen.add(d)
                queue.append((d, p2))
    return menus, inputs, edges


def part1(rep: Report, verbose=False):
    """Crawl, then check what each option's label promises."""
    all_inputs = {}
    graph_lines = []
    for root in ("BRAINIAC", "GEOMETRY", "UNITS"):
        menus, inputs, edges = crawl(root, rep)
        # the WHAT NEXT? menus: finish every mode sensibly from its first Input
        entry_of = {}
        extra_edges = []
        for nd, info in list(inputs.items()):
            m = replay(root, info["path"])
            p = finish_mode(m, info["path"])
            if m.wait.kind == "error":
                rep.fail(1, f"ERR finishing {info['ident']} with sensible answers: "
                         f"{describe(m)} at {where(m)}\n    " + fmt_path(root, p))
                continue
            if not is_next_menu(m):
                rep.fail(1, f"{info['ident']}: sensible answers did not reach WHAT NEXT? "
                         f"but {describe(m)}\n    " + fmt_path(root, p))
                continue
            nn = node(m)
            entry_of[nn] = entry_of.get(nn, nd)
            menus.setdefault(nn, dict(title=m.wait.title, options=list(m.wait.options), path=p))
            for i, opt in enumerate(m.wait.options, 1):
                p2 = p + (str(i),)
                m2 = replay(root, p2)
                p2 = settle(m2, p2)
                rep.counts["menu options taken"] += 1
                extra_edges.append((nn, opt, node(m2), p2, describe(m2), nd, info["prompt"]))
                if m2.wait.kind == "error":
                    rep.fail(1, f"ERR choosing {opt!r} after {info['ident']}: {describe(m2)} "
                             f"at {where(m2)}\n    " + fmt_path(root, p2))
                if m2.wait.kind == "menu" and any(stack_sizes(m2).values()):
                    rep.fail(1, f"#call stack not empty at {m2.wait.title!r}: {stack_sizes(m2)}"
                             f"\n    " + fmt_path(root, p2))
                if m2.leaks or m2.blocks:
                    rep.fail(1, f"leak after {opt!r}: leaks={m2.leaks}\n    " + fmt_path(root, p2))
        # parents of each menu, for BACK
        parents = defaultdict(set)       # reached going forward from (not by going back)
        for frm, opt, to, p2, desc in edges:
            if to != frm and "BACK" not in opt and opt not in (
                    "MAIN MENU", "GEOMETRY MENU", "UNITS MENU", "QUIT", "AGAIN", "HOW IT WORKS"):
                parents[to].add(frm)
        title = {nd: info["title"] for nd, info in menus.items()}
        main = [nd for nd, t in title.items() if t == MAIN_TITLES[root] and nd[0] == root]
        main_of = {p: nd for nd, t in title.items() for p in MAIN_TITLES
                   if t == MAIN_TITLES[p] and nd[0] == p}

        def check(frm, opt, to, p2, desc, entry=None, prompt=None):
            here = f"{title.get(frm, frm)!r} -> {opt!r} -> {desc}"
            program = frm[0]
            nested = root == "BRAINIAC" and program != "BRAINIAC"
            label = opt.upper()
            ok, why = True, ""
            if label == "BACK":
                if to not in parents[frm]:
                    ok, why = False, "BACK does not go to the menu this one was reached from"
                elif len(parents[frm]) > 1:
                    rep.note(1, f"{root}: {here}: this menu is reached from "
                             f"{sorted(title.get(p, p) for p in parents[frm])}; BACK always goes "
                             f"to {title.get(to)!r}")
            elif label == "MAIN MENU":
                ok = to == main_of.get("BRAINIAC")
                why = "MAIN MENU is not Brainiac's main menu"
            elif label == "GEOMETRY MENU":
                ok = to == main_of.get("GEOMETRY")
            elif label == "UNITS MENU":
                ok = to == main_of.get("UNITS")
            elif label == "AGAIN":
                # back to the mode's first prompt, or to the menu just above it
                # (45-45-90 or 30-60-90, 3 sides or base & height, ...)
                if entry is None:       # WHAT NEXT? reached from a menu (COMBINED SHAPE)
                    ok = frm in [e[2] for e in edges if e[0] == to]
                else:
                    ok = to == entry or entry in [e[2] for e in edges if e[0] == to]
                why = f"AGAIN should go back to {prompt!r}"
            elif label in ("QUIT", "BACK TO BRAINIAC"):
                if nested:
                    ok = to == main_of.get("BRAINIAC")
                    why = "should land on Brainiac's main menu"
                    if label == "QUIT":
                        rep.note(1, f"{root}: {here}: QUIT inside {program} run from Brainiac "
                                    f"goes back to Brainiac's main menu, it does not quit")
                else:
                    ok = to == ("done",)
                    why = "should end the program"
                    if label == "BACK TO BRAINIAC" and root != "BRAINIAC":
                        rep.note(1, f"{root} run on its own: {here}: 'BACK TO BRAINIAC' just "
                                    f"ends the program (there is no Brainiac to go back to)")
            elif "BACK" in label:          # HOW TO USE / BACK
                ok = to in main or to in parents[frm]
                why = "should end on the menu above"
            if not ok:
                rep.fail(1, f"{root}: {here}: {why}\n    " + fmt_path(root, p2))

        for frm, opt, to, p2, desc in edges:
            check(frm, opt, to, p2, desc)
        for frm, opt, to, p2, desc, entry, prompt in extra_edges:
            check(frm, opt, to, p2, desc, entry, prompt)

        # every menu must be able to reach the end of the program (no trap)
        succ = defaultdict(set)
        for frm, opt, to, *_ in edges + [e[:5] for e in extra_edges]:
            succ[frm].add(to)
        for nd, info in inputs.items():
            m = replay(root, info["path"])
            finish_mode(m, info["path"])
            succ[nd].add(node(m))
        for nd in menus:
            todo, seen = [nd], {nd}
            while todo:
                x = todo.pop()
                for y in succ[x]:
                    if y not in seen:
                        seen.add(y)
                        todo.append(y)
            if ("done",) not in seen:
                rep.fail(1, f"{root}: no way out of menu {title[nd]!r}: QUIT is unreachable")
        for nd, info in inputs.items():
            all_inputs.setdefault((root, nd), info)
        graph_lines.append(f"  {root}: {len(menus)} menus, {len(inputs)} first inputs, "
                           f"{len(edges) + len(extra_edges)} options taken")

        def short(desc):
            return re.sub(r"^(menu|input) '(.*)' \((\w+)\)$",
                          lambda mm: (mm.group(2) if mm.group(1) == "menu" else f"[{mm.group(2)}]")
                          + ("" if mm.group(3) == root else f"@{mm.group(3)}"), desc)
        if True:
            done_titles = set()
            for frm in menus:
                if frm[0] != root and not verbose:
                    continue           # GEOMETRY and UNITS are listed on their own
                key = (title.get(frm), frm)
                if key in done_titles:
                    continue
                done_titles.add(key)
                outs = [f"{opt}->{short(desc)}" for f_, opt, to, p2, desc in edges if f_ == frm]
                if outs:
                    graph_lines.append(f"    {title.get(frm)}: " + ", ".join(outs))
        if verbose:
            for frm, opt, to, p2, desc in edges:
                graph_lines.append(f"    {title.get(frm)!r} --{opt}--> {desc}")
            for frm, opt, to, p2, desc, entry, prompt in extra_edges:
                graph_lines.append(f"    WHAT NEXT? after {prompt!r} --{opt}--> {desc}")
    return graph_lines


def escape_check(rep: Report, targets):
    """README: "If you change your mind, type anything (like 1) and pick MAIN MENU
    afterwards." Does typing 1 at each first prompt get to a menu?"""
    for t in targets:
        if not t["first"]:
            continue
        m = replay(t["root"], t["path"], t["degree"])
        path = list(t["path"])
        stuck = None
        for k in range(4):
            m.answer("1")
            path.append("1")
            path = list(settle(m, path))
            if m.wait.kind != "input":
                break
            if node(m) == t["node"] and k == 1:
                stuck = True
                break
        if stuck:
            rep.note(1, f"typing 1 never leaves {t['ident']}: the error message is shown and the "
                        f"same prompt comes back (README says 'type anything (like 1)')\n    "
                        + fmt_path(t["root"], tuple(path)))
        elif m.wait.kind == "error":
            rep.fail(1, f"typing 1 at {t['ident']}: {describe(m)} at {where(m)}\n    "
                     + fmt_path(t["root"], tuple(path)))


# -- part 2: input fuzzing ----------------------------------------------------------

def find_targets():
    """Every Input prompt, with the answers that lead to it.

    Starts from each mode's first Input (found by the crawl) and walks through
    the mode with sensible answers, noting every Input on the way. A few modes
    need special first answers to show their later prompts."""
    targets = {}

    def walk(root, path, degree=False, first=True):
        m = replay(root, path, degree)
        path = list(path)
        for _ in range(30):
            path = list(settle(m, path))
            if m.wait.kind == "input":
                key = (root, degree, node(m))
                if key not in targets:
                    targets[key] = dict(root=root, degree=degree, path=tuple(path),
                                        node=node(m), ident=ident(m), prompt=m.wait.prompt,
                                        first=first)
                first = False
                a = benign(m)
            elif m.wait.kind == "menu" and (prog(m), m.wait.title) in MENU_CHOICE:
                a = MENU_CHOICE[(prog(m), m.wait.title)]
            else:
                break
            m.answer(a)
            path.append(a)

    rep = Report()
    for root in ("BRAINIAC", "GEOMETRY", "UNITS"):
        _, inputs, _ = crawl(root, rep)
        for nd, info in inputs.items():
            walk(root, info["path"])
    # prompts that only show up for some answers
    S, POLY, SYS = "1", "2", "3"
    walk("BRAINIAC", (S, "A=BH/2"), first=False)                       # SOLVE FOR: in solve
    m = replay("BRAINIAC", (S, "2^X=X+3"))
    p = settle(m, (S, "2^X=X+3"))
    walk("BRAINIAC", p + ("2",), first=True)                            # SEARCH A RANGE
    walk("BRAINIAC", (SYS, "6"), first=False)                           # EQ3..EQ6
    for top in "12345":                                                 # every rate
        walk("UNITS", ("6", top), first=False)
    walk("UNITS", ("6", "1", "2", "3", "2"), first=False)
    return list(targets.values())


def _clean_list(xs):
    out, seen = [], set()
    for x in xs:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


# typed like on a computer (see keyboard_to_tokens): x->X, sqrt( -> √(, pi -> π,
# e^( -> 𝑒^(, a leading - is (-); ᴇ is the EE key, 𝑖 the imaginary unit.
LONG1 = "X+" * 50 + "1"                                       # 101 characters
LONG2 = "(" * 30 + "X" + ")" * 30 + "=1"
LONG3 = "1+" * 120 + "X=0"                                    # 243 characters
LONG4 = "SIN(X)" * 30
EXPRESSIONS = [
    "", " ", "=", "==", "X=", "=5", "X=Y=Z", "X==2", "X=2=", "2=X=3",
    "((X+1)", "X+1)", "(X+1))=2", ")(", "()", "(", ")", "X(", ")X", "((((X",
    "X++2", "*X", "X^", "X^=2", "2^^3", "X^^-1=2", "X*", "X/", "/X", "X//2", "X+-2=1", "X-+2=1",
    "X=+2", "X=*2", "-=X", "X=-", "--X=2", "X--2=0", "X..2", "1.2.3X=1", ".=X", "X.=1",
    "abc", "solve x", "x+y=2", "sin x", "hello", "ABC", "XYZ", "ABCDEFG", "ABCDEFG=1",
    "A=BCDEFGH", "ABCDEFGHIJKLMNOPQRSTUVWXYZθ",
    "0", "5", "-5", "3.14", "1ᴇ99", "9ᴇ99*10", "1ᴇ-99", "1ᴇ100", "ᴇ5", "1ᴇ", "X=1ᴇ99",
    "X=1ᴇ-99", "1ᴇ50X=1", "X^2=1ᴇ99", "X=9ᴇ99*10", "1ᴇ99X^2+X=0",
    "0/0", "1/0", "X/0=1", "X=1/0", "0/0=X", "0^0", "0^0X=1", "0X=0", "0X=5", "X-X=0",
    "X=X", "X=X+1", "1=1", "1=2", "X/X=1", "1/X=0", "1/X^2=0",
    "𝑖", "3+4𝑖", "X=𝑖", "𝑖X=1", "X^2=-1", "X^2+1", "√(-1)X=1", "(1+𝑖)X=2", "X=𝑖^𝑖",
    "θ", "θ=2", "Xθ=1", "θ^2-4=0", "θ+X=1",
    LONG1, LONG2, LONG3, LONG4[:150], "X" * 30 + "=1", "X^2-5X+6" * 12 + "=0",
    "XXXXXXX=1", "X*X*X*X*X*X*X*X=2", "XX+XX=2",
    "sin(cos(tan(X)))=0", "abs(abs(X))=1", "√(√(X))=2", "ln(ln(X))=1", "log(log(log(X)))=0",
    "sin(sin(sin(sin(X))))", "abs(X-abs(X+abs(X)))=1", "e^(e^(X))=2",
    "X!=6", "X!", "3!X=12",
    "X<2=Y", "X<Y<Z", "X<2<3", "X≠2", "≠", "X≠", "X<", "<X", "X<=2", "X>=2", "X=<2", "X=>2",
    "X<2X", "1<2", "2<1", "X<X", "X^2<-1", "X^2≥0", "abs(X)<0", "√(X)<-1", "1/X>0",
    "{1,2}", "{1,2}=X", "X={1,2}", "{X}=1", "{}", "ʟA", "ʟA=X",
    "\"X\"", "\"X=1\"", "X=\"", "\"", "\"X\"=1", "\"=\"",
    "é", "∞", "÷", "√", "😀",
    "X→A", "X:Y", "X:=1", "?", "X?=1", "??", "Ans", "rand", "rand=X", "Str1", ",", "X,Y",
    "X=1,2", "[A]", "e", "𝑒", "𝑒X=1", "π", "πX=1", "X°=1", "X^^T", "int(X)=2",
    "round(X,2)=1", "max(X,2)=3", "X and 1", "not(X)=1", "X or 1=1", "10^(X)=100",
    "\\xC1X)=100", "e^(X)=0", "2^X=0", "10^(X)=0", "2^X=-8", "e^(X)=-1", "5(2^X)=0",
    "ln(0)", "ln(X)=0", "log(-1)=X", "log(X)=-1ᴇ99", "√(-1)=X", "X^.5=-1", "X^(1/3)=-2",
    "tan(X)=1ᴇ99", "sin(X)=2", "sin(X)=1", "cos(X)=X", "sin(X)<2", "sin(X)>-2",
    "sin^-1(X)=1", "X^X=0", "X^X=-1", "(-8)^(1/3)=X", "abs(X)=-1", "abs(X)=0", "√(X)=0",
    "√(X)=-2", "1/(X-1)=1/(X-1)", "(X-1)/(X-1)=1", "X/(X-2)=2/(X-2)", "2^X=X^2",
    "X^99=1", "X^100-1", "X^1000=2", "X^-1=2", "X^0.5=2", "X^1.5=8", "X^(-2)=4",
    "(X+1)^20=0", "1ᴇ99/X=1", "X/1ᴇ99=1", "1ᴇ-99X=1",
    "A=B", "A=BH/2", "PV=NRT", "Y=MX+B", "A=πR^2", "F=MA", "V=LWH", "T=2π√(L/G)",
    "A+B+C+D+E+F=G", "AB=AB", "A=A", "A=1", "1=A", "X+Y=1", "X=Y", "XY=1", "X^2+Y^2=1",
    "A/B=C/D", "1/A+1/B=1/C", "A=B^C", "A=log(B)", "A=sin(B)", "A=√(B)", "A=√(B)+√(C)",
    "A=abs(B)", "A=B/0", "A=0/B", "A=B-B", "A=(B+1)/(B+1)",
    "ln(X^2)<1", "log(X^2)≥0", "log(abs(X-1))<1", "2^X>0", "e^(X)≥0", "2^X<0", "sin(X)",
    "√(X)", "X^2-4", "5", "π", "0", "X-X",
    # the [−] key where (-) was meant
    "2X+3=‒7", "‒X=5", "X=‒5", "X^2=‒4X‒4", "‒X^2+1", "X<‒2", "‒5", "X^2‒‒1", "A=‒B",
    "‒(X+1)=2", "sin(‒X)=.5", "X+‒1", "X^2‒4=0", "2^X=‒8", "X+Y=‒1", "‒2X+Y=3",
]
NUMBERS = [
    "", " ", "0", "-0", "1", "-1", ".5", "1.", "..", "1.2.3", "2", "3", "6", "7", "2.5",
    "1ᴇ99", "-1ᴇ99", "9.99ᴇ99", "1ᴇ50", "1ᴇ-99", "1ᴇ-50", "1ᴇ100", "9ᴇ99*10", "1ᴇ12",
    "0/0", "1/0", "𝑖", "3+4𝑖", "-𝑖", "√(-1)", "π", "𝑒", "e", "A", "X", "θ", "ABC", "abc",
    "=", "X=1", "1+", "*2", "2^^3", "((1", "1)", "{1,2}", "\"1\"", "1,2", "1:2", "?",
    "360", "180", "90", "89.9999999", "179.9999999", "1ᴇ-12", "45", "1000000", "-360", "720",
    "1+" * 60 + "1", "(" * 25 + "1", "1" * 30, "rand", "Ans", "sin(90)", "cos(90)",
    "123456789012345", "99999999999999", "1/3", "2/3", "-π", "ln(0)", "√(2)", "1ᴇ-6",
    "-1ᴇ-99", "5ᴇ49", "1ᴇ33", "1ᴇ34", "-5", "-1ᴇ12", "1ᴇ-13", "X→A", "Str1", "é", "😀",
    "{}", "[A]", "ʟA", "1<2", "1=1", "not(0)", "0.0000001", "‒5", "‒1", "1‒", "3‒‒2",
    ".0004", "-.0004", ".001", "1ᴇ-4",
]
SOLVE_FOR = ["X", "H", "A", "B", "XY", "AB", "", " ", "1", "θ", "x", "h", "?", "Str1", "=",
             "(", "HH", "Z", "sin(", "𝑖", "π", "e", "ʟA", "\"", "{", "ABCDEFGHIJ"]

KEYS = ["X", "Y", "A", "B", "θ", "1", "2", "0", "9", ".", "+", "-", "*", "/", "^", "(", ")",
        "=", "<", ">", "≤", "≥", "≠", "sin(", "cos(", "tan(", "√(", "abs(", "log(", "ln(",
        "e^(", "π", "𝑖", "²", "³", "⁻¹", ",", " ", "ᴇ", "{", "}", "\"", "!", "?", ":"]
NKEYS = ["1", "2", "0", "9", ".", "-", "+", "*", "/", "^", "(", ")", "ᴇ", "π", "𝑖", "X", "A"]


def random_inputs(seed, n, keys, long_share=0.2):
    rnd = random.Random(seed)
    out = []
    for i in range(n):
        if rnd.random() < long_share:
            k = rnd.randint(100, 250)
        else:
            k = rnd.randint(1, 30)
        s = ""
        while len(s) < k:
            s += rnd.choice(keys)
        out.append(s[:max(k, 1)] if len(s) > 250 else s)
    return out


def random_equations(seed, n):
    """Random but well-formed equations: these should be answered, never crash."""
    rnd = random.Random(seed)
    atoms = ["X", "2", "3", "1", "0", ".5", "π", "X^2", "X^3", "(X-1)", "(X+2)"]
    funcs = ["sin(", "cos(", "√(", "abs(", "ln(", "log(", "e^("]
    ops = ["+", "-", "*", "/", "^"]

    def term(depth):
        r = rnd.random()
        if depth > 2 or r < 0.5:
            return rnd.choice(atoms)
        if r < 0.75:
            return rnd.choice(funcs) + expr(depth + 1) + ")"
        return "(" + expr(depth + 1) + ")"

    def expr(depth=0):
        s = term(depth)
        for _ in range(rnd.randint(0, 2)):
            op = rnd.choice(ops)
            rhs = term(depth)
            if op == "^":
                rhs = rnd.choice(["2", "3", "(1/2)", "-1"])
            s += op + rhs
        return s

    out = []
    for _ in range(n):
        rel = rnd.choice(["=", "=", "=", "<", ">", "≤", "≥"])
        out.append(expr() + rel + expr())
    return out


COMPLEXISH = re.compile(r"𝑖|√\(-|ln|log|\^")


def corpus_for(target, quick):
    """The fuzz inputs for one prompt."""
    root = target["root"]
    p, label, prompt = target["ident"]
    seed = sum(map(ord, f"{root}{p}{label}{prompt}{target['degree']}"))
    if p == "BRAINIAC" and prompt == "SOLVE FOR:":
        base = SOLVE_FOR + EXPRESSIONS[:40]
    elif p == "BRAINIAC" and prompt in ("HOW MANY:", "FROM:", "TO:"):
        base = NUMBERS + EXPRESSIONS[::4]
    elif p == "BRAINIAC" and prompt == "EQ2:":
        base = EXPRESSIONS[::3] + random_inputs(seed, 10, KEYS)
    elif p == "BRAINIAC" and prompt in ("EQ3:", "EQ4:", "EQ5:", "EQ6:"):
        base = EXPRESSIONS[::7] + random_inputs(seed, 5, KEYS)
    elif p == "BRAINIAC" and (label, prompt) in (("m_solve", "EQ:"), ("sy_in", "EQ1:")):
        base = EXPRESSIONS + random_inputs(seed, 40, KEYS) + \
            random_equations(seed, 50 if label == "m_solve" else 15)
    elif p == "BRAINIAC":           # the other expression prompts: every other one,
        # plus the realistic ones and the [−] key at the end of the list
        base = EXPRESSIONS[::2] + EXPRESSIONS[-45:] + random_inputs(seed, 20, KEYS)
    elif root == "BRAINIAC":
        # GEOMETRY and UNITS run from Brainiac: the same as on their own (fuzzed
        # in full there) except that Brainiac left the calculator in a+bi
        base = [x for x in NUMBERS if COMPLEXISH.search(x)] + ["3", "0", "-1", "1ᴇ99"]
    elif target["first"]:
        base = NUMBERS + EXPRESSIONS[::15] + random_inputs(seed, 8, NKEYS, 0.1)
    else:                           # later prompts of a GEOMETRY/UNITS solver
        base = NUMBERS[::2] + ["", "0", "-1", "1ᴇ99", "1ᴇ50", "𝑖", ".0004", "-.0004", "‒5"] + \
            random_inputs(seed, 3, NKEYS, 0.1)
    if quick:
        rnd = random.Random(seed)
        base = [x for x in base if rnd.random() < 0.3]
    return _clean_list(base)


class Stuck(Exception):
    pass


def _alarm(signum, frame):
    raise Stuck()


def run_case(target, text):
    """Type `text` at the target prompt; follow the program until it is back at a
    menu or a prompt. Returns a dict describing the outcome."""
    root, degree = target["root"], target["degree"]
    m = replay(root, target["path"], degree)
    assert node(m) == target["node"], (target["ident"], node(m))
    start_steps = m.steps
    m.max_steps = start_steps + STEP_BUDGET
    in_prog, in_pc = prog(m), m.pc
    numeric = not m.stmts[m.pc][-1].key.startswith("Str")
    path = list(target["path"]) + [text]
    res = dict(target=target, text=text, path=None, outcome=None, numeric=numeric)
    try:
        m.answer(text)
    except ValueError as e:                         # the keyboard mapper can't type it
        if "could not tokenize" not in str(e):
            raise
        res.update(outcome="untypeable", detail=f"{type(e).__name__}: {e}", path=tuple(path),
                   at="while typing")
        return res
    shown = False
    inputs_given = 0
    try:
        for _ in range(60):
            w = m.wait
            if w.kind == "pause":
                shown = True
                m.answer("")
                path.append("")
                continue
            if w.kind == "input":
                if node(m) == target["node"] or inputs_given >= 12:
                    break
                a = benign(m)
                inputs_given += 1
                m.answer(a)
                path.append(a)
                continue
            if w.kind == "menu" and (prog(m), w.title) in MENU_CHOICE and inputs_given < 12:
                a = MENU_CHOICE[(prog(m), w.title)]
                inputs_given += 1
                m.answer(a)
                path.append(a)
                continue
            break
    except Exception as e:
        res.update(outcome="pyexc", detail=f"{type(e).__name__}: {e}\n" +
                   "".join(traceback.format_exc().splitlines(True)[-4:]), path=tuple(path),
                   at=where(m))
        return res
    res["path"] = tuple(path)
    res["steps"] = m.steps - start_steps
    w = m.wait
    if w.kind == "error":
        if "STEP LIMIT" in w.message:
            res.update(outcome="hang", at=where(m), stmt=m.statement_text())
        else:
            at_input = (prog(m) == in_prog and m.pc == in_pc and len(path) == len(target["path"]) + 1)
            res.update(outcome="err_input" if at_input else "err_prog", msg=w.message.split(" (")[0],
                       full=w.message, at=where(m), stmt=m.statement_text())
        return res
    problems = []
    if m.leaks:
        problems.append(f"leaks={m.leaks}")
    if w.kind == "menu":
        if m.blocks:
            problems.append(f"blocks open at a menu: {m.blocks}")
        if any(stack_sizes(m).values()):
            problems.append(f"#call stack at menu: {stack_sizes(m)}")
    if w.kind == "done":
        problems.append("the program ended")
    if w.kind == "input" and not shown and node(m) == target["node"]:
        res["silent"] = True                # asked again without a message
    if w.kind == "input" and node(m) != target["node"]:
        problems.append(f"still asking {w.prompt!r} after {inputs_given} answers")
    if problems:
        res.update(outcome="bad", detail="; ".join(problems), at=where(m))
    else:
        res.update(outcome="ok", end=describe(m))
    return res


def _worker(args):
    target, texts = args
    signal.signal(signal.SIGALRM, _alarm)
    out = []
    for text in texts:
        signal.alarm(WALL_BUDGET)
        t0 = time.time()
        try:
            r = run_case(target, text)
        except Stuck:
            r = dict(target=target, text=text, outcome="stuck",
                     path=tuple(target["path"]) + (text,), detail=f">{WALL_BUDGET}s wall clock")
        except Exception as e:
            r = dict(target=target, text=text, outcome="pyexc",
                     path=tuple(target["path"]) + (text,),
                     detail=f"{type(e).__name__}: {e}", at="harness")
        finally:
            signal.alarm(0)
        r["secs"] = time.time() - t0
        out.append(r)
    return out


def part2(rep: Report, targets, quick, jobs):
    work = []
    for t in targets:
        texts = corpus_for(t, quick)
        # the main prompts get a Degree-mode run too (trig and complex inputs)
        for i in range(0, len(texts), 25):
            work.append((t, texts[i:i + 25]))
        if t["root"] == "BRAINIAC" and not t["degree"] and t["ident"][:2] in (
                ("BRAINIAC", "m_solve"), ("BRAINIAC", "m_zeros"), ("BRAINIAC", "m_simp")):
            td = dict(t, degree=True)
            deg = [x for x in texts if re.search(r"sin|cos|tan|𝑖|e\^|ln|log|√|\^", x)]
            for i in range(0, len(deg), 25):
                work.append((td, deg[i:i + 25]))
    t0 = time.time()
    results = []
    if jobs > 1:
        with mp.get_context("fork").Pool(jobs) as pool:
            for chunk in pool.imap_unordered(_worker, work):
                results += chunk
    else:
        for w in work:
            results += _worker(w)
    rep.counts["fuzz cases"] = len(results)
    rep.counts["fuzz seconds"] = round(time.time() - t0)
    return results


def err_class(r):
    """A rough name for why the program stopped (for the summary table)."""
    p = r["at"].split(".txt")[0]
    msg, stmt, text, label = r["msg"], r["stmt"], r["text"], r["at"].split("[")[-1].rstrip("]")
    if MINUS_KEY in text and msg == "ERR:SYNTAX":
        return "[−] key typed where (-) was meant"
    if "ʟBM" in stmt:
        return "ʟBM read before it exists (or stale)"
    if label in ("range", "m_sys"):
        return "number prompt read as a string, then expr("
    if p == "BRAINIAC" and msg == "ERR:SYNTAX":
        return "typo reaches expr( (X^, *X, (), stray , \" : ...)"
    if p == "BRAINIAC" and msg in ("ERR:DATA TYPE", "ERR:DIM MISMATCH") and re.search(r"[\"{]", text):
        return "quote or list typed, reaches expr("
    if p == "BRAINIAC" and msg == "ERR:DOMAIN" and label == "ex_vars" and \
            re.fullmatch(r"(\d*\(?)?(\d|e)\^\(?[^=<>≤≥]*\)?[=<>≤≥]‒?0", text.replace("(X)", "X")):
        return "power = 0: ln(0) when taking logs"
    if p == "BRAINIAC" and label == "fc_common":
        return "Factor of 0 (gcd 0)"
    if p == "BRAINIAC" and label == "sub sweep" and r["target"]["ident"][2] in ("FROM:", "TO:"):
        return "SEARCH A RANGE far from 0 overflows (2^X at 720)"
    if p == "BRAINIAC" and label == "iq_noedge":
        return "inequality test point lands where F is undefined"
    if p in ("GEOMETRY", "UNITS") and msg == "ERR:DATA TYPE":
        return "complex number typed (Brainiac left a+bi on)"
    if msg == "ERR:OVERFLOW":
        return f"{p}: huge numbers overflow"
    if p == "GEOMETRY" and "sub(" in stmt:
        return "tiny value shows as 0, then sub(\"0\",2,0)"
    if p == "GEOMETRY" and "rmark" in label:
        return "degenerate figure: right-angle mark divides by 0"
    return f"{p}: {msg} other"


def summarize_fuzz(rep: Report, results):
    by = defaultdict(list)
    for r in results:
        by[r["outcome"]].append(r)
    for k, v in by.items():
        rep.counts[f"fuzz outcome {k}"] = len(v)
    lines = []
    classes = defaultdict(list)
    for r in by["err_prog"]:
        classes[err_class(r)].append(r)
    lines.append("\nERR classes (program's own code after an Input):")
    for cls, rs in sorted(classes.items(), key=lambda kv: -len(kv[1])):
        prompts = {f"{r['target']['ident'][2]}" for r in rs}
        ex = sorted({r["text"] for r in rs}, key=len)[:4]
        lines.append(f"  {len(rs):5}  {cls:52} {len(prompts):3} prompts  e.g. {ex}")
    # ERR classes: (message, where) -> inputs
    groups = defaultdict(list)
    for r in by["err_prog"]:
        groups[(r["msg"], r["at"], r["stmt"])].append(r)
    lines.append(f"\nERR in the program's own code after an Input ({len(by['err_prog'])} cases, "
                 f"{len(groups)} distinct places):")
    for (msg, at, stmt), rs in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        prompts = Counter(f"{r['target']['ident'][1]}:{r['target']['ident'][2]}" for r in rs)
        ex = sorted({r["text"] for r in rs}, key=len)[:6]
        lines.append(f"  {msg:16} {at}  {stmt[:60]!r}  x{len(rs)}")
        lines.append(f"      prompts: {dict(prompts.most_common(6))}")
        lines.append(f"      inputs:  {ex}")
        r0 = min(rs, key=lambda r: (len(r["path"]), len(r["text"])))
        lines.append(f"      repro:   {fmt_path(r0['target']['root'], r0['path'], r0['target']['degree'])}")
        rep.fail(2, f"{msg} at {at} {stmt[:50]!r}: {len(rs)} inputs, e.g. "
                    f"{fmt_path(r0['target']['root'], r0['path'], r0['target']['degree'])}")
    ig = defaultdict(list)
    for r in by["err_input"]:
        ig[(r["msg"], r["numeric"])].append(r)
    lines.append(f"\nERR at the Input itself ({len(by['err_input'])} cases; on the calculator the "
                 f"user re-types, except where noted):")
    for (msg, numeric), rs in sorted(ig.items(), key=lambda kv: -len(kv[1])):
        ex = sorted({r["text"] for r in rs}, key=len)[:10]
        kind = "numeric Input" if numeric else "string Input (blank: simulator refuses it)"
        lines.append(f"  {msg:16} {kind:42} x{len(rs)}  e.g. {ex}")
    untypeable = sorted({r["text"] for r in by["untypeable"]})
    if untypeable:
        lines.append(f"\nNot typeable through keyboard_to_tokens ({len(by['untypeable'])} cases, "
                     f"not a program bug): {untypeable}")
    for kind in ("hang", "stuck", "pyexc", "bad"):
        if by[kind]:
            lines.append(f"\n{kind.upper()} ({len(by[kind])}):")
            seen = set()
            for r in by[kind]:
                key = (r.get("detail", r.get("stmt", ""))[:80], r.get("at"))
                if key in seen:
                    continue
                seen.add(key)
                lines.append(f"  {r.get('detail', r.get('stmt', ''))[:300]} at {r.get('at')}")
                lines.append(f"      repro: {fmt_path(r['target']['root'], r['path'], r['target']['degree'])}")
                rep.fail(2, f"{kind}: {r.get('detail', r.get('stmt', ''))[:120]} at {r.get('at')}: "
                            f"{fmt_path(r['target']['root'], r['path'], r['target']['degree'])}")
    silent = [r for r in by["ok"] if r.get("silent")]
    if silent:
        sp = Counter(f"{r['target']['ident']}" for r in silent)
        lines.append(f"\nAsked again with no message ({len(silent)} cases): {dict(sp)}")
    slow = sorted((r for r in results if r.get("steps", 0) > 400_000), key=lambda r: -r["steps"])
    if slow:
        lines.append(f"\nSlowest cases (steps):")
        for r in slow[:8]:
            lines.append(f"  {r['steps']:>9}  {fmt_path(r['target']['root'], r['path'], r['target']['degree'])}")
    return lines


# -- part 3: long sessions ------------------------------------------------------------

def find_option(m, text):
    for i, o in enumerate(m.wait.options, 1):
        if o == text:
            return str(i)
    raise KeyError(f"no option {text!r} in {m.wait.title!r}: {m.wait.options}")


SESSION_ROUNDS = [
    # (menu path from Brainiac's main menu, the answers for the mode)
    (["SOLVE FOR X (OR < >)"], ["X^2-5X+6=0"]),
    (["SOLVE FOR X (OR < >)"], ["X^2-4<0"]),
    (["SOLVE FOR X (OR < >)"], ["sin(X)=.5"]),
    (["SOLVE FOR X (OR < >)"], ["2^(X+1)=8^(X-1)"]),
    (["SOLVE FOR X (OR < >)"], ["log(X)+log(X-3)=1"]),
    (["SOLVE FOR X (OR < >)"], ["abs(X-1)<3"]),
    (["POLYNOMIALS", "ROOTS (WITH COMPLEX)"], ["X^4-1"]),
    (["POLYNOMIALS", "QUADRATIC STEPS+FORMS"], ["2X^2+3X-5"]),
    (["POLYNOMIALS", "SIMPLIFY / EXPAND"], ["3X(X-2)+(X+1)^2"]),
    (["POLYNOMIALS", "FACTOR"], ["6X^2+9X"]),
    (["POLYNOMIALS", "DIVIDE"], ["X^3-1", "X-1"]),
    (["POLYNOMIALS", "FIND ZEROS OF F(X)"], ["X^3-4X"]),
    (["SYSTEM OF EQUATIONS"], ["2", "2X+3Y=7", "X-Y=1"]),
    (["FORMULAS", "REARRANGE A FORMULA"], ["T=2π√(L/G)", "L"]),
    (["FORMULAS", "INVERSE FUNCTION"], ["(X+1)/(X-2)"]),
    (["GEOMETRY & UNITS", "GEOMETRY", "TRIANGLES", "PYTHAGOREAN THEOREM"], ["3", "4", "0"]),
    (["GEOMETRY & UNITS", "GEOMETRY", "CIRCLES", "ARC & SECTOR"], ["5", "60"]),
    (["GEOMETRY & UNITS", "GEOMETRY", "3D SOLIDS: VOLUME", "CONE"], ["3", "4"]),
    (["GEOMETRY & UNITS", "UNIT CONVERSION", "LENGTH"], ["8", "4", "5"]),
    (["GEOMETRY & UNITS", "UNIT CONVERSION", "RATES, LIKE MI/HR", "LENGTH (MI/HR)"],
     ["8", "6", "TIME (MI/HR)", "3", "1", "60"]),
    (["GEOMETRY & UNITS", "UNIT CONVERSION", "MORE…", "TEMPERATURE"], ["1", "2", "212"]),
    (["HELP ME CHOOSE", "AN EQUATION (HAS =)", "ONE LETTER (LIKE X)",
      "A QUADRATIC, ALL STEPS"], ["X^2=5X-6"]),
    (["SOLVE FOR X (OR < >)"], ["2^X=X+3", "SEARCH A RANGE", "-3", "6"]),
    (["GEOMETRY & UNITS", "GEOMETRY", "2D SHAPES: AREA", "COMBINED SHAPE"],
     ["ADD A RECTANGLE", "3", "4", "TAKE AWAY A SHAPE", "A CIRCLE", "1", "DONE"]),
]
# what AGAIN asks first, when that's a menu: the rates start at the top unit
AGAIN_FIRST = {"RATES, LIKE MI/HR": ["LENGTH (MI/HR)"]}


def drive_round(m, menu_path, answers, log, check_stacks=None):
    """From a menu: walk the menus, answer, reach WHAT NEXT? (the one that isn't
    followed by SEARCH A RANGE answers)."""
    for opt in menu_path:
        a = find_option(m, opt)
        m.answer(a)
        log.append(a)
        log[:] = list(settle(m, log))
    queue = list(answers)
    for _ in range(80):
        log[:] = list(settle(m, log))
        w = m.wait
        if w.kind == "menu" and check_stacks is not None and any(stack_sizes(m).values()):
            check_stacks.append((w.title, stack_sizes(m), tuple(log)))
        if w.kind == "menu" and w.title == "WHAT NEXT?" and not (queue and queue[0] in w.options):
            return
        if w.kind == "error":
            raise AssertionError(describe(m) + " at " + where(m))
        if not queue:
            raise AssertionError(f"ran out of answers at {describe(m)}")
        a = queue.pop(0)
        if w.kind == "menu":
            a = find_option(m, a) if not a.isdigit() else a
        m.answer(a)
        log.append(a)
    raise AssertionError("never reached WHAT NEXT?")


def part3(rep: Report, rounds=100):
    lines = []
    for degree in (False, True):
        m = fresh("BRAINIAC", degree)
        log = []
        rnd = random.Random(1234 + degree)
        list_max = Counter()
        stack_max = Counter()
        history = []                 # list sizes after each round
        bad_stacks = []
        try:
            for r in range(rounds):
                if m.wait.title != MAIN_TITLES["BRAINIAC"]:
                    raise AssertionError(f"round {r}: not at the main menu: {describe(m)}")
                menu_path, answers = SESSION_ROUNDS[r] if r < len(SESSION_ROUNDS) \
                    else rnd.choice(SESSION_ROUNDS)
                drive_round(m, menu_path, answers, log, bad_stacks)
                # AGAIN (same mode, same answers) a few times, then back to the main menu
                for _ in range(rnd.choice([0, 0, 1, 1, 2, 3])):
                    a = find_option(m, "AGAIN")
                    m.answer(a)
                    log.append(a)
                    drive_round(m, AGAIN_FIRST.get(menu_path[-2] if len(menu_path) > 1 else "", []),
                                answers, log, bad_stacks)
                nested = prog(m) != "BRAINIAC"
                opt = {"GEOMETRY": "BACK TO BRAINIAC", "UNITS": "QUIT"}.get(prog(m), "MAIN MENU")
                if nested and rnd.random() < 0.5:
                    # back to that program's own menu first, then out
                    own = {"GEOMETRY": "GEOMETRY MENU", "UNITS": "UNITS MENU"}[prog(m)]
                    a = find_option(m, own)
                    m.answer(a)
                    log.append(a)
                    if prog(m) == "GEOMETRY":
                        a = find_option(m, "BACK TO BRAINIAC")
                    else:
                        a = find_option(m, "MORE…")
                        m.answer(a)
                        log.append(a)
                        a = find_option(m, "QUIT")
                    m.answer(a)
                    log.append(a)
                else:
                    a = find_option(m, opt)
                    m.answer(a)
                    log.append(a)
                log[:] = list(settle(m, log))
                for k, v in m.lists.items():
                    list_max[k] = max(list_max[k], len(v))
                for k, v in stack_sizes(m).items():
                    stack_max[k] = max(stack_max[k], v)
                if m.leaks or m.blocks:
                    raise AssertionError(f"round {r}: leaks={m.leaks} blocks={m.blocks}")
                if any(stack_sizes(m).values()) or m.frames:
                    raise AssertionError(f"round {r}: #call stack not empty at the main menu: "
                                         f"{stack_sizes(m)}, programs open: {len(m.frames)}")
                history.append({k: len(v) for k, v in m.lists.items()})
            for title, sizes, lg in bad_stacks[:3]:
                rep.fail(3, f"#call stack not empty at menu {title!r}: {sizes}\n    "
                            + fmt_path("BRAINIAC", lg, degree))
            # a list that only ever grows: its size late in the session is above anything
            # it reached early on, by more than one mode's worth
            half = len(history) // 2
            names = set().union(*history)
            grew = {}
            for k in names:
                early = max(h.get(k, 0) for h in history[:half])
                late = max(h.get(k, 0) for h in history[half:])
                if late > early + 20:
                    grew[k] = (early, late)
            if grew:
                rep.fail(3, f"lists keep growing in a long session ({'Degree' if degree else 'Radian'})"
                            f": {grew}")
            big = {k: v for k, v in list_max.items() if v > 300}
            lines.append(f"  {'Degree' if degree else 'Radian'}: {rounds} rounds, {len(log)} answers, "
                         f"{m.steps} steps, leaks={m.leaks}, frames={len(m.frames)}, "
                         f"stack max {dict(stack_max)}, {len(m.lists)} lists alive, biggest "
                         f"{list_max.most_common(3)}" + (f", over 300: {big}" if big else ""))
            # now QUIT: the calculator must be as it was
            a = find_option(m, "QUIT")
            m.answer(a)
            log.append(a)
            check_restored(rep, 3, m, degree, f"after {rounds} rounds", "BRAINIAC", log)
        except (AssertionError, RuntimeError, KeyError) as e:
            rep.fail(3, f"long session ({'Degree' if degree else 'Radian'}): {e}\n    "
                        + fmt_path("BRAINIAC", tuple(log), degree))
    return lines


# -- part 4: QUIT restores the calculator --------------------------------------------

def check_restored(rep, part, m, degree, how, root, path, before_lists=(), before_window=None,
                   before_complex=False):
    where_ = f"{root} {how}"
    if m.wait.kind != "done":
        rep.fail(part, f"{where_}: QUIT did not end the program: {describe(m)}\n    "
                       + fmt_path(root, tuple(path), degree))
        return
    if m.radians == degree:
        rep.fail(part, f"{where_}: angle mode not restored (was {'Degree' if degree else 'Radian'})"
                       f"\n    " + fmt_path(root, tuple(path), degree))
    if root == "BRAINIAC" and m.complex_mode:
        rep.fail(part, f"{where_}: left in a+bi\n    " + fmt_path(root, tuple(path), degree))
    if root != "BRAINIAC" and m.complex_mode != before_complex:
        rep.fail(part, f"{where_}: Real/a+bi changed\n    " + fmt_path(root, tuple(path), degree))
    left = sorted(set(m.lists) - set(before_lists))
    if left:
        rep.fail(part, f"{where_}: lists left behind: {left}\n    "
                       + fmt_path(root, tuple(path), degree))
    if before_window is not None and m.window != before_window:
        rep.fail(part, f"{where_}: graph window not restored: {m.window} (was {before_window})"
                       f"\n    " + fmt_path(root, tuple(path), degree))
    if root == "GEOMETRY" and not m.graph.axes:
        rep.fail(part, f"{where_}: axes left off\n    " + fmt_path(root, tuple(path), degree))


def part4(rep: Report, targets):
    lines = []
    window = {"Xmin": -7.0, "Xmax": 13.0, "Xscl": 2.0, "Ymin": -3.0, "Ymax": 9.0, "Yscl": 0.5}
    count = 0
    for root in ("BRAINIAC", "GEOMETRY", "UNITS"):
        for degree in (False, True):
            for complex_before in (False, True):
                # every mode of this program (by its first input), finished, then QUIT
                firsts = [t for t in targets if t["root"] == root and t["first"] and not t["degree"]]
                for t in [None] + firsts:
                    m = Machine(TOK[root])
                    m.programs.update({k: v for k, v in TOK.items() if k != root})
                    m.radians = not degree
                    m.complex_mode = complex_before
                    m.window = dict(window)
                    m.lists["MINE"] = [1.0, 2.0]          # the user's own list stays
                    m.run()
                    path = []
                    if t is not None:
                        for a in t["path"]:
                            m.answer(a)
                        path = list(finish_mode(m, t["path"]))
                        if not is_next_menu(m):
                            rep.fail(4, f"{t['ident']}: could not finish: {describe(m)}")
                            continue
                    # find the way out
                    for _ in range(6):
                        w = m.wait
                        if w.kind != "menu":
                            break
                        opts = w.options
                        for want in ("QUIT", "BACK TO BRAINIAC", "MORE…", "MAIN MENU",
                                     "GEOMETRY MENU", "UNITS MENU"):
                            if want in opts:
                                break
                        a = str(opts.index(want) + 1)
                        m.answer(a)
                        path.append(a)
                        path = list(settle(m, path))
                        if want in ("QUIT", "BACK TO BRAINIAC") and m.wait.kind == "done":
                            break
                    count += 1
                    check_restored(rep, 4, m, degree, f"QUIT after {t['ident'] if t else 'nothing'}"
                                   f" ({'a+bi' if complex_before else 'Real'} before)", root, path,
                                   before_lists=["MINE"], before_window=window if root == "GEOMETRY" else None,
                                   before_complex=complex_before)
                    if root == "BRAINIAC" and complex_before and not m.complex_mode:
                        rep.note(4, "BRAINIAC's QUIT always sets Real, even when the calculator was in "
                                    "a+bi before it started")
                    if m.lists.get("MINE") != [1.0, 2.0]:
                        rep.fail(4, f"{root}: the user's own list was changed")
    # Geometry and Units run from Brainiac, then Brainiac's QUIT
    for degree in (False, True):
        for menu_path, answers in SESSION_ROUNDS[15:21]:
            m = fresh("BRAINIAC", degree)
            log = []
            try:
                drive_round(m, menu_path, answers, log)
                opt = {"GEOMETRY": "BACK TO BRAINIAC", "UNITS": "QUIT"}[prog(m)]
                a = find_option(m, opt)
                m.answer(a)
                log.append(a)
                a = find_option(m, "QUIT")
                m.answer(a)
                log.append(a)
                count += 1
                check_restored(rep, 4, m, degree, f"QUIT after {menu_path[-1]}", "BRAINIAC", log)
                if m.window != {"Xmin": -10.0, "Xmax": 10.0, "Xscl": 1.0, "Ymin": -10.0,
                                "Ymax": 10.0, "Yscl": 1.0}:
                    rep.fail(4, f"window changed after Geometry via Brainiac: {m.window}")
            except AssertionError as e:
                rep.fail(4, f"{menu_path}: {e}\n    " + fmt_path("BRAINIAC", tuple(log), degree))
    # README: Geometry "puts your window, axes and angle mode back". Axes that
    # were off before?
    m = Machine(TOK["GEOMETRY"])
    m.graph.axes = False
    m.run()
    m.answer(str(m.wait.options.index("BACK TO BRAINIAC") + 1))
    if m.wait.kind == "done" and m.graph.axes:
        rep.note(4, "GEOMETRY's QUIT always turns the axes on (AxesOn), also when they were "
                    "off before; README says it puts the axes back. FnOff, PlotsOff and "
                    "GridOff at the start are never undone (the simulator doesn't model them).")
    lines.append(f"  {count} QUIT checks")
    rep.counts["quit checks"] = count
    return lines


# -- main -----------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true")
    ap.add_argument("--parts", default="1,2,3,4")
    ap.add_argument("--jobs", type=int, default=2)
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument("--rounds", type=int, default=100)
    ap.add_argument("--only", default="", help="fuzz only prompts of this program, "
                    "e.g. BRAINIAC (run from itself), GEOMETRY, UNITS")
    args = ap.parse_args()
    parts = {int(p) for p in args.parts.split(",")}
    rep = Report()
    t0 = time.time()
    out = []
    if os.environ.get("FUZZ_SRC"):
        out.append(f"(programs built from {os.environ['FUZZ_SRC']})")

    targets = find_targets()
    rep.counts["input prompts found"] = len(targets)

    if 1 in parts:
        out.append("PART 1: menu crawl")
        out += part1(rep, args.verbose)
        escape_check(rep, targets)
    if 2 in parts:
        fuzzed = [t for t in targets if not args.only or
                  (t["ident"][0] == args.only and t["root"] in (args.only, "GEOMETRY", "UNITS"))]
        out.append(f"PART 2: input fuzzing at {len(fuzzed)} prompts")
        results = part2(rep, fuzzed, args.quick, args.jobs)
        out += summarize_fuzz(rep, results)
    if 3 in parts:
        out.append("PART 3: long sessions")
        out += part3(rep, args.rounds)
    if 4 in parts:
        out.append("PART 4: QUIT restores the calculator")
        out += part4(rep, targets)

    print("\n".join(out))
    print("\nCOUNTS:", dict(rep.counts))
    if rep.notes:
        rep.notes = _clean_list(rep.notes)
        print(f"\nNOTES ({len(rep.notes)}):")
        for part, text in _clean_list(rep.notes):
            print(f"  [part {part}] {text}")
    rep.failures = _clean_list(rep.failures)
    print(f"\nFAILURES ({len(rep.failures)}):")
    for part, text in rep.failures:
        print(f"  [part {part}] {text}")
    print(f"\n{'FAIL' if rep.failures else 'PASS'} in {time.time() - t0:.0f}s")
    return 1 if rep.failures else 0


if __name__ == "__main__":
    sys.exit(main())
