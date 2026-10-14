"""A small TI-BASIC interpreter, for testing Brainiac off the calculator.

It runs the exact tokens that go into BRAINIAC.8xp (decoded with the same
TI-Toolkit tables used to build it), on a 26x10 home screen like the
TI-84 Plus CE's. It covers the commands Brainiac uses, not all of TI-BASIC.

Driving it:

    m = Machine.from_file("brainiac/dist/BRAINIAC.8xp")
    wait = m.run()          # runs until the program needs you
    wait.kind               # "menu", "input", "pause", "done" or "error"
    m.answer("2")           # pick menu item 2 / type text / "" for ENTER
    print(m.screen.text())

Things the real calculator does that this copies on purpose, because they
are the usual ways TI-BASIC programs break:
  - a Goto out of an If-Then/For/While block leaves that block open on the
    calculator's stack (a slow memory leak); `leaks` counts these,
  - √ of a negative stops with ERR:NONREAL ANS in Real mode, 1/0 stops with
    ERR:DIVIDE BY 0, results past 1E100 stop with ERR:OVERFLOW,
  - the If line without Then guards only the single next statement,
  - there are no empty strings: ""→Str1, ""+"A", length(""), a blank Input
    into a string are all errors (Disp "" for a blank line is fine).
"""

from __future__ import annotations

import cmath
import math
import re
from dataclasses import dataclass, field
from pathlib import Path

from tivars.tokenizer import encode
from tivars.tokenizer.decoder import decode
from tivars.types import TIProgram

ROWS, COLS = 10, 26


class TIError(Exception):
    """A calculator error, e.g. TIError("DOMAIN") shows as ERR:DOMAIN."""


# -- tokens -------------------------------------------------------------------

@dataclass(frozen=True)
class Tok:
    key: str      # the token's accessible (ASCII) name, e.g. "Disp ", "sqrt("
    glyph: str    # what the calculator shows, e.g. "Disp ", "√("


def tokens_from_bytes(data: bytes) -> list[Tok]:
    tokens, _ = decode(data)
    # the token tables spell the apostrophe's glyph with an escaping backslash
    return [Tok(t.accessible, t.display.replace("\\'", "'")) for t in tokens]


def tokens_from_text(text: str) -> list[Tok]:
    data, _ = encode(text)
    return tokens_from_bytes(data)


def keyboard_to_tokens(text: str) -> list[Tok]:
    """What you'd get typing `text` on the calculator's keypad.

    Accepts the way people type on a computer: x for X, x^2, sqrt(x), pi,
    e^(x), and a plain - for negative numbers (the calculator has a separate
    (-) key; here a minus at the start or after ( , = + - * / ^ means that).
    """
    text = text.strip().replace("**", "^").replace("−", "-")
    text = re.sub(r"(?<![A-Za-z])x(?![A-Za-z])", "X", text)
    text = text.replace("π", "pi").replace("³√(", r"\xBD").replace("√(", "sqrt(")
    text = re.sub(r"(?<![A-Za-z])e\^\(", "￿", text)
    out, prev = [], ""
    for ch in text:
        if ch == "-" and (not prev or prev in "(,=+-*/^~<>"):
            ch = "~"
        if ch != " ":
            prev = ch
        out.append(ch)
    text = "".join(out).replace("￿", "𝑒^(")
    # Capital letters are keys of their own on the calculator: PV is P then V,
    # and R² is R then ², never the finance variable PV or the statistic R²
    # that a longest-match tokenizer would pick.
    data = b""
    for part in re.split(r"(\\x[0-9A-Fa-f]{2}|[A-Zθ])", text):
        if part.startswith("\\x"):
            data += bytes.fromhex(part[2:])
        elif part:
            data += encode(part, mode="max")[0]
    return tokens_from_bytes(data)


def glyphs(tokens) -> str:
    return "".join(t.glyph for t in tokens)


# -- values -------------------------------------------------------------------

@dataclass(frozen=True)
class TIString:
    tokens: tuple

    def __str__(self):
        return glyphs(self.tokens)


class TIMatrix:
    """A matrix variable like [J]: a list of rows."""

    def __init__(self, rows):
        self.rows = [list(r) for r in rows]

    @property
    def dims(self):
        return len(self.rows), len(self.rows[0]) if self.rows else 0


# glyphs toString( can produce, mapped back to the tokens they are
GLYPH_TOKENS = {"⁻": Tok("~", "⁻"), "ᴇ": Tok("|E", "ᴇ"), "𝑖": Tok("[i]", "𝑖"),
                "-": Tok("-", "-"), "+": Tok("+", "+"), ".": Tok(".", ".")}


def text_to_string(text: str) -> "TIString":
    return TIString(tuple(GLYPH_TOKENS.get(ch) or Tok(ch, ch) for ch in text))


def is_num(v):
    return isinstance(v, (int, float, complex)) and not isinstance(v, bool)


def fmt(v) -> str:
    """Show a value the way Normal/Float mode does on the home screen."""
    if isinstance(v, TIString):
        return str(v)
    if isinstance(v, list):
        return "{" + " ".join(fmt(x) for x in v) + "}"
    if isinstance(v, complex):
        re_, im = v.real, v.imag
        s = "" if re_ == 0 else fmt(re_)
        sign = ("-" if s else "⁻") if im < 0 else ("+" if s else "")
        mag = "" if abs(im) == 1 else fmt(abs(im))
        return s + sign + mag + "𝑖"
    x = float(v)
    if x == 0:
        return "0"
    neg = x < 0
    x = abs(x)
    if 1e-3 <= x < 1e10:
        s = f"{x:.10g}"
        if "e" in s:  # .10g can still choose exponent form near 1e10
            s = f"{x:.0f}"
        if "." in s:
            s = s.rstrip("0").rstrip(".")
        if s.startswith("0."):
            s = s[1:]
    else:
        mant, exp = f"{x:.9e}".split("e")
        mant = mant.rstrip("0").rstrip(".")
        e = int(exp)
        s = f"{mant}ᴇ{'⁻' if e < 0 else ''}{abs(e)}"
    return ("⁻" if neg else "") + s


# -- the screen ---------------------------------------------------------------

class Screen:
    def __init__(self):
        self.clear()

    def clear(self):
        self.cells = [[" "] * COLS for _ in range(ROWS)]
        self.row = 0  # where the next Disp line goes

    def _newline_row(self) -> int:
        if self.row >= ROWS:
            self.cells.pop(0)
            self.cells.append([" "] * COLS)
            self.row = ROWS - 1
        r = self.row
        self.row += 1
        return r

    def disp(self, text: str, right=False):
        """One Disp line: strings on the left, numbers on the right."""
        text = text[:COLS]
        r = self._newline_row()
        start = COLS - len(text) if right else 0
        for i, ch in enumerate(text):
            self.cells[r][start + i] = ch

    def output(self, row: int, col: int, text: str):
        if not (1 <= row <= ROWS and 1 <= col <= COLS):
            raise TIError("DOMAIN")
        r, c = row - 1, col - 1
        for ch in text:
            if r >= ROWS:
                break
            self.cells[r][c] = ch
            c += 1
            if c >= COLS:
                r, c = r + 1, 0

    def input_line(self, prompt: str) -> int:
        r = self._newline_row()
        for i, ch in enumerate(prompt[:COLS]):
            self.cells[r][i] = ch
        return r

    def put_after(self, r: int, start: int, text: str):
        c = start
        for ch in text:
            if c >= COLS:
                r = self._newline_row()
                c = 0
            self.cells[r][c] = ch
            c += 1

    def lines(self) -> list[str]:
        return ["".join(row) for row in self.cells]

    def text(self) -> str:
        return "\n".join(self.lines())

    def snapshot(self):
        return [row[:] for row in self.cells], self.row

    def restore(self, snap):
        cells, row = snap
        self.cells = [r[:] for r in cells]
        self.row = row


# -- waiting for the user -----------------------------------------------------

@dataclass
class Wait:
    kind: str                 # menu | input | pause | done | error
    prompt: str = ""
    title: str = ""
    options: list = field(default_factory=list)
    message: str = ""
    line: int = 0

    def __repr__(self):
        extra = self.message or self.prompt or self.title
        return f"Wait({self.kind}{', ' + repr(extra) if extra else ''})"


# -- the machine --------------------------------------------------------------

OPENERS = {"Then", "While ", "Repeat ", "For("}
LETTERS = set("ABCDEFGHIJKLMNOPQRSTUVWXYZ") | {"theta"}
LABEL_TOKENS = LETTERS | set("0123456789")
STR_VARS = {f"Str{i}" for i in range(10)}
Y_VARS = {f"{{Y{i}}}" for i in range(10)}
MAT_VARS = {f"[{c}]" for c in "ABCDEFGHIJ"}
COMMANDS = {"ClrHome", "Disp ", "Output(", "Input ", "Prompt ", "Pause ", "Menu(", "Lbl ",
            "Goto ", "If ", "Then", "Else", "End", "While ", "Repeat ", "For(", "Stop",
            "Return", "DelVar ", "a+bi", "Real", "SortA(", "SortD("}


class Machine:
    def __init__(self, tokens: list[Tok], *, max_steps=2_000_000):
        self.stmts = split_statements(tokens)
        self.labels = {}
        for i, st in enumerate(self.stmts):
            if st and st[0].key == "Lbl ":
                try:
                    self.labels.setdefault(label_name(st[1:]), i)
                except TIError:
                    pass                # reported when the program reaches it
        self.reals = {}
        self.strings = {}
        self.yvars = {}
        self.lists = {}
        self.mats = {}
        self.ans = 0.0
        self.complex_mode = False
        self.radians = True
        self.screen = Screen()
        self.pc = 0
        self.blocks = []
        self.leaks = 0           # Gotos taken from inside a block
        self.max_depth = 0
        self.steps = 0
        self.ops = 0             # arithmetic done, list elements counted one by one
        self.max_steps = max_steps
        self.wait = None
        self._finish = None      # completes the statement we're waiting on
        self._cache = {}

    @classmethod
    def from_file(cls, path, **kw):
        return cls(tokens_from_bytes(TIProgram.open(str(path)).data), **kw)

    @classmethod
    def from_text(cls, text, **kw):
        return cls(tokens_from_text(text), **kw)

    # -- running -------------------------------------------------------------

    def run(self) -> Wait:
        if self.wait and self.wait.kind in ("done", "error"):
            return self.wait
        try:
            while self.pc < len(self.stmts):
                self.steps += 1
                if self.steps > self.max_steps:
                    raise TIError("STEP LIMIT (infinite loop?)")
                w = self.execute(self.pc)
                if w:
                    self.wait = w
                    return w
            self.wait = Wait("done")
        except TIError as e:
            self.wait = Wait("error", message=f"ERR:{e}", line=self.pc + 1)
        return self.wait

    def statement_text(self, pc=None) -> str:
        """The statement at pc (default: where it stopped), as the calculator shows it."""
        pc = self.pc if pc is None else pc
        return glyphs(self.stmts[pc]) if 0 <= pc < len(self.stmts) else ""

    def answer(self, value: str = "") -> Wait:
        """Respond to what the program is waiting for, then keep running."""
        if not self.wait or self.wait.kind not in ("menu", "input", "pause"):
            raise RuntimeError(f"program is not waiting for input: {self.wait}")
        finish, waiting = self._finish, self.wait
        self._finish = self.wait = None
        try:
            finish(value)
        except RuntimeError:      # not a valid answer (no such menu item): ask again
            self._finish, self.wait = finish, waiting
            raise
        except TIError as e:
            self.wait = Wait("error", message=f"ERR:{e}", line=self.pc + 1)
            return self.wait
        if self._finish:          # e.g. Prompt A,B asking for B next
            return self.wait
        return self.run()

    # -- statements ----------------------------------------------------------

    def execute(self, pc) -> Wait | None:
        st = self.stmts[pc]
        if not st:
            self.pc += 1
            return None
        head = st[0].key
        rest = st[1:]

        if head == "ClrHome":
            self.screen.clear()
        elif head == "Disp ":
            for arg in self.parse_args(pc, rest):
                v = self.eval(arg)
                self.screen.disp(fmt(v), right=not isinstance(v, TIString))
        elif head == "Output(":
            row, col, val = [self.eval(a) for a in self.parse_args(pc, rest, 3)]
            self.screen.output(int(self.real(row)), int(self.real(col)), fmt(val))
        elif head == "Input ":
            return self.do_input(pc, rest)
        elif head == "Prompt ":
            return self.do_prompt(rest, 0)
        elif head == "Pause ":
            if rest:
                self.screen.disp(fmt(self.eval(self.parse_expr(pc, rest))))

            def finish(_):
                self.pc += 1
            self._finish = finish
            return Wait("pause")
        elif head == "Menu(":
            return self.do_menu(pc, rest)
        elif head == "Lbl ":
            label_name(rest)            # ERR:SYNTAX on a label like FV
        elif head == "Goto ":
            self.goto(label_name(rest))
            return None
        elif head == "If ":
            cond = self.truth(self.eval(self.parse_expr(pc, rest)))
            nxt = self.stmts[pc + 1] if pc + 1 < len(self.stmts) else []
            if nxt and nxt[0].key == "Then":
                if cond:
                    self.push(("if", pc))
                    self.pc = pc + 2
                else:
                    self.pc = self.skip_block(pc + 2, stop_at_else=True)
            else:
                self.pc = pc + 1 if cond else pc + 2
            return None
        elif head == "Else":
            self.pop("if")
            self.pc = self.skip_block(pc + 1, stop_at_else=False)
            return None
        elif head == "End":
            return self.do_end(pc)
        elif head == "While ":
            if self.truth(self.eval(self.parse_expr(pc, rest))):
                self.push(("while", pc))
                self.pc = pc + 1
            else:
                self.pc = self.skip_block(pc + 1, stop_at_else=False)
            return None
        elif head == "Repeat ":
            self.push(("repeat", pc))
        elif head == "For(":
            return self.do_for(pc, rest)
        elif head in ("Stop", "Return"):
            self.pc = len(self.stmts)
            return None
        elif head == "DelVar ":
            self.delvar(rest)
        elif head == "a+bi":
            self.complex_mode = True
        elif head == "Real":
            self.complex_mode = False
        elif head in ("SortA(", "SortD("):
            self.sort(rest, descending=head == "SortD(")
        elif head == "Fill(":
            value, target = self.parse_args(pc, rest, 2)
            v = self.number(self.eval(value))
            name = target[1]
            if target[0] == "matvar":
                if name not in self.mats:
                    raise TIError("UNDEFINED")
                rows, cols = self.mats[name].dims
                self.mats[name] = TIMatrix([[v] * cols for _ in range(rows)])
            elif target[0] == "listvar":
                if name not in self.lists:
                    raise TIError("UNDEFINED")
                self.lists[name] = [v] * len(self.lists[name])
            else:
                raise TIError("SYNTAX")
        elif head == "List>matr(":
            args = self.parse_args(pc, rest)
            cols = [self.eval(a) for a in args[:-1]]
            if args[-1][0] != "matvar" or not all(isinstance(c, list) for c in cols):
                raise TIError("SYNTAX")
            n = max(len(c) for c in cols)
            self.mats[args[-1][1]] = TIMatrix(
                [[c[i] if i < len(c) else 0.0 for c in cols] for i in range(n)])
        elif head == "Matr>list(":
            args = self.parse_args(pc, rest)
            mat = self.eval(args[0])
            for j, target in enumerate(args[1:]):
                if target[0] != "listvar":
                    raise TIError("SYNTAX")
                self.lists[target[1]] = [row[j] for row in mat.rows]
        else:
            self.statement_expr(pc, st)
        self.pc += 1
        return None

    def statement_expr(self, pc, st):
        key = ("stmt", pc)
        if key not in self._cache:
            p = Parser(st)
            node = p.expr()
            target = None
            if p.peek() == "->":
                p.next()
                target = p.target()
            p.end()
            self._cache[key] = (node, target)
        node, target = self._cache[key]
        val = self.eval(node)
        if target is not None:
            self.store(target, val)
        if not isinstance(val, TIString) or target is None:
            self.ans = val

    # -- blocks ----------------------------------------------------------------

    def push(self, block):
        self.blocks.append(block)
        self.max_depth = max(self.max_depth, len(self.blocks))

    def pop(self, kind=None):
        if not self.blocks:
            raise TIError("SYNTAX (End/Else without a block)")
        return self.blocks.pop()

    def skip_block(self, i, stop_at_else) -> int:
        """Index just past the End (or Else) that closes the block opening before i."""
        depth = 0
        while i < len(self.stmts):
            st = self.stmts[i]
            head = st[0].key if st else ""
            if head in OPENERS:
                depth += 1
            elif head == "End":
                if depth == 0:
                    return i + 1
                depth -= 1
            elif head == "Else" and depth == 0 and stop_at_else:
                self.push(("if", i))
                return i + 1
            i += 1
        raise TIError("SYNTAX (missing End)")

    def do_end(self, pc):
        block = self.pop()
        kind = block[0]
        if kind == "while":
            self.pc = block[1]          # re-test the While line
        elif kind == "repeat":
            start = block[1]
            cond = self.truth(self.eval(self.parse_expr(start, self.stmts[start][1:])))
            if cond:
                self.pc = pc + 1
            else:
                self.push(block)
                self.pc = start + 1
        elif kind == "for":
            _, start, var, end, step = block
            value = self.real(self.reals.get(var, 0.0)) + step
            self.reals[var] = value
            if (step > 0 and value <= end) or (step < 0 and value >= end):
                self.push(block)
                self.pc = start + 1
            else:
                self.pc = pc + 1
        else:
            self.pc = pc + 1
        return None

    def do_for(self, pc, rest):
        args = self.parse_args(pc, rest)
        var = args[0]
        if var[0] != "var":
            raise TIError("SYNTAX")
        start, end = self.real(self.eval(args[1])), self.real(self.eval(args[2]))
        step = self.real(self.eval(args[3])) if len(args) > 3 else 1.0
        if step == 0:
            raise TIError("INCREMENT")
        self.reals[var[1]] = start
        if (step > 0 and start > end) or (step < 0 and start < end):
            self.pc = self.skip_block(pc + 1, stop_at_else=False)
        else:
            self.push(("for", pc, var[1], end, step))
            self.pc = pc + 1
        return None

    def goto(self, name):
        if name not in self.labels:
            raise TIError("LABEL")
        if self.blocks:
            self.leaks += 1
        self.pc = self.labels[name] + 1

    # -- input / menus -------------------------------------------------------

    def do_input(self, pc, rest):
        args = self.parse_args(pc, rest)
        if len(args) == 2:
            prompt = fmt(self.eval(args[0]))
            target = args[1]
        else:
            target = args[0]
            prompt = "?"
        row = self.screen.input_line(prompt)

        def finish(text):
            toks = keyboard_to_tokens(text) if text.strip() else []
            self.screen.put_after(row, len(prompt), glyphs(toks))
            if target[0] == "strvar":
                if not toks:
                    raise TIError("INVALID DIM (blank Input: what the calculator does "
                                  "with it is unknown, so Brainiac must not rely on it)")
                self.strings[target[1]] = TIString(tuple(toks))
            else:
                self.store(target, self.eval(Parser(toks).whole()))
            self.pc += 1

        self._finish = finish
        return Wait("input", prompt=prompt)

    def do_prompt(self, rest, index):
        names = [label_name(group) for group in split_commas(rest)]
        name = names[index]
        prompt = ("θ" if name == "theta" else name) + "=?"
        row = self.screen.input_line(prompt)

        def finish(text):
            toks = keyboard_to_tokens(text)
            self.screen.put_after(row, len(prompt), glyphs(toks))
            self.reals[name] = self.eval(Parser(toks).whole())
            if index + 1 < len(names):
                w = self.do_prompt(rest, index + 1)
                self.wait = w
            else:
                self.pc += 1

        self._finish = finish
        return Wait("input", prompt=prompt)

    def do_menu(self, pc, rest):
        groups = split_commas(rest)
        title = fmt(self.eval(Parser(groups[0]).whole()))
        options = []
        for i in range(1, len(groups) - 1, 2):
            text = fmt(self.eval(Parser(groups[i]).whole()))
            options.append((text, label_name(groups[i + 1])))
        if len(options) > 7:
            raise TIError("SYNTAX (Menu( takes at most 7 options)")
        snap = self.screen.snapshot()

        def finish(choice):
            choice = str(choice).strip()
            if choice.isdigit() and 1 <= int(choice) <= len(options):
                label = options[int(choice) - 1][1]
            else:
                matches = [lbl for text, lbl in options if text == choice.upper()]
                if not matches:
                    raise RuntimeError(f"no menu option {choice!r} in {options}")
                label = matches[0]
            self.screen.restore(snap)
            self.goto(label)

        self._finish = finish
        return Wait("menu", title=title, options=[t for t, _ in options])

    # -- storage ---------------------------------------------------------------

    def store(self, target, val):
        kind = target[0]
        if kind == "var":
            if isinstance(val, (TIString, list)):
                raise TIError("DATA TYPE")
            self.reals[target[1]] = self.number(val)
        elif kind == "strvar":
            if not isinstance(val, TIString):
                raise TIError("DATA TYPE")
            if not val.tokens:
                raise TIError("INVALID DIM (the calculator has no empty strings)")
            self.strings[target[1]] = val
        elif kind == "yvar":
            if not isinstance(val, TIString):
                raise TIError("DATA TYPE")
            self.yvars[target[1]] = val.tokens
        elif kind == "listvar":
            if not isinstance(val, list):
                raise TIError("DATA TYPE")
            self.lists[target[1]] = list(val)
        elif kind == "listelem":
            lst = self.lists.setdefault(target[1], [])
            i = self.index(self.eval(target[2]))
            if i > len(lst) + 1:
                raise TIError("INVALID DIM")
            v = self.number(val)
            # a list is all real or all complex; what storing a complex number
            # into one element of a real list does is unknown, so never do it
            if isinstance(v, complex) != any(isinstance(x, complex) for x in lst) and lst:
                raise TIError("DATA TYPE (complex element into a real list, or the "
                              "reverse: unknown on the calculator)")
            if i == len(lst) + 1:
                lst.append(v)
            else:
                lst[i - 1] = v
        elif kind == "matvar":
            if not isinstance(val, TIMatrix):
                raise TIError("DATA TYPE")
            self.mats[target[1]] = TIMatrix(val.rows)
        elif kind == "matelem":
            mat = self.mats.get(target[1])
            if mat is None:
                raise TIError("UNDEFINED")
            r, c = self.index(self.eval(target[2])), self.index(self.eval(target[3]))
            rows, cols = mat.dims
            if r > rows or c > cols:
                raise TIError("INVALID DIM")
            mat.rows[r - 1][c - 1] = self.number(val)
        elif kind == "mdim":
            if not isinstance(val, list) or len(val) != 2:
                raise TIError("DATA TYPE")
            r, c = self.index(val[0]), self.index(val[1])
            old = self.mats.get(target[1], TIMatrix([]))
            self.mats[target[1]] = TIMatrix(
                [[old.rows[i][j] if i < len(old.rows) and j < len(old.rows[i]) else 0.0
                  for j in range(c)] for i in range(r)])
        elif kind == "dim":
            n = self.index(val, allow_zero=True)
            lst = self.lists.setdefault(target[1], [])
            del lst[n:]
            lst.extend([0.0] * (n - len(lst)))
        else:
            raise TIError("SYNTAX")

    def delvar(self, rest):
        node = Parser(rest).target()
        kind = node[0]
        if kind == "listvar":
            self.lists.pop(node[1], None)
        elif kind == "var":
            self.reals.pop(node[1], None)
        elif kind == "strvar":
            self.strings.pop(node[1], None)
        elif kind == "yvar":
            self.yvars.pop(node[1], None)
        elif kind == "matvar":
            self.mats.pop(node[1], None)

    def sort(self, rest, descending):
        names = [Parser(g).target() for g in split_commas(rest)]
        lists = [self.lists[n[1]] for n in names]
        # Brainiac only sorts real lists; insist on it, in case the calculator does
        if any(isinstance(x, complex) for l in lists for x in l):
            raise TIError("DATA TYPE")
        if any(len(l) != len(lists[0]) for l in lists):
            raise TIError("DIM MISMATCH")
        order = sorted(range(len(lists[0])), key=lambda i: self.real(lists[0][i]),
                       reverse=descending)
        for n, l in zip(names, lists):
            self.lists[n[1]] = [l[i] for i in order]

    # -- parsing helpers -----------------------------------------------------

    def parse_expr(self, pc, toks):
        key = ("expr", pc)
        if key not in self._cache:
            self._cache[key] = Parser(toks).whole()
        return self._cache[key]

    def parse_args(self, pc, toks, n=None):
        key = ("args", pc)
        if key not in self._cache:
            p = Parser(toks)
            args = p.arglist()
            p.end()
            self._cache[key] = args
        args = self._cache[key]
        if n is not None and len(args) != n:
            raise TIError("ARGUMENT")
        return args

    # -- numbers ---------------------------------------------------------------

    def number(self, v):
        if isinstance(v, complex):
            if v.imag == 0:
                v = v.real
            elif not self.complex_mode:
                raise TIError("NONREAL ANS")
            else:
                return complex(check_range(v.real), check_range(v.imag))
        if not is_num(v):
            raise TIError("DATA TYPE")
        return check_range(float(v))

    def real(self, v) -> float:
        v = self.number(v)
        if isinstance(v, complex):
            raise TIError("DATA TYPE")
        return v

    def truth(self, v) -> bool:
        return self.real(v) != 0

    def index(self, v, allow_zero=False) -> int:
        x = self.real(v)
        if x != int(x) or x < (0 if allow_zero else 1) or x > 999:
            raise TIError("INVALID DIM")
        return int(x)

    # -- evaluation ----------------------------------------------------------

    def eval(self, node):
        return Evaluator(self).eval(node)


def check_range(x: float) -> float:
    if math.isnan(x):
        raise TIError("DOMAIN")
    if abs(x) >= 1e100 or math.isinf(x):
        raise TIError("OVERFLOW")
    if abs(x) < 1e-99:
        return 0.0
    # The calculator keeps 14 significant digits (in decimal), so 3*.1 is
    # exactly .3 there. Rounding every result the same way keeps the
    # simulator's arithmetic close to the calculator's.
    return float(f"{x:.14g}")


# -- statements and labels ----------------------------------------------------

def split_statements(tokens: list[Tok]) -> list[list[Tok]]:
    """Break the program at newlines and colons (colons inside quotes stay)."""
    out, cur, in_str = [], [], False
    for t in tokens:
        if t.key == "\n":
            out.append(cur)
            cur, in_str = [], False
            continue
        if t.key == "->":
            in_str = False
        elif t.key == '"':
            in_str = not in_str
        elif t.key == ":" and not in_str:
            out.append(cur)
            cur = []
            continue
        cur.append(t)
    out.append(cur)
    return out


def label_name(toks) -> str:
    """A label is one or two letters, digits or θ, each its own token.

    "Lbl FV" fails on the calculator: FV is one token (a finance variable),
    not the letters F and V.
    """
    toks = [t for t in toks if t.key != ")"]
    if not 1 <= len(toks) <= 2 or any(t.key not in LABEL_TOKENS for t in toks):
        raise TIError(f"SYNTAX (bad label {glyphs(toks)!r})")
    return "".join(t.key for t in toks)


def split_commas(toks):
    groups, cur, depth, in_str = [], [], 0, False
    for t in toks:
        if t.key == '"':
            in_str = not in_str
        elif not in_str:
            if t.key.endswith("(") or t.key in ("{", "["):
                depth += 1
            elif t.key in (")", "}", "]"):
                depth -= 1
                if depth < 0:
                    break
            elif t.key == "," and depth == 0:
                groups.append(cur)
                cur = []
                continue
        cur.append(t)
    groups.append(cur)
    return groups


# -- expressions ----------------------------------------------------------------

FUNCS1 = {
    "abs(", "sqrt(", "cuberoot(", "ln(", "log(", "e^^(", "10^^(", "sin(", "cos(", "tan(",
    "sin^-1(", "cos^-1(", "tan^-1(", "sinh(", "cosh(", "tanh(", "int(", "iPart(", "fPart(",
    "not(", "real(", "imag(", "length(", "expr(", "dim(", "sum(", "prod(", "DeltaList(",
    "conj(", "angle(", "rref(", "toString(", "cumSum(",
}
FUNCSN = {"round(", "max(", "min(", "inString(", "sub(", "gcd(", "lcm(", "seq(", "augment(",
          "remainder("}
RELOPS = {"=", "!=", "<", ">", "<=", ">="}
POSTFIX = {"^^2", "^^3", "^^-1", "!"}


class Parser:
    """Turns a statement's tokens into a little tree."""

    def __init__(self, toks):
        self.t = list(toks)
        self.i = 0

    def peek(self, k=0):
        j = self.i + k
        return self.t[j].key if j < len(self.t) else None

    def next(self):
        tok = self.t[self.i]
        self.i += 1
        return tok

    def end(self):
        if self.peek() == ")":   # a stray close paren at the very end is fine
            self.i += 1
        if self.peek() is not None:
            raise TIError(f"SYNTAX (unexpected {self.t[self.i].glyph!r})")

    def whole(self):
        node = self.expr()
        self.end()
        return node

    def arglist(self):
        args = [self.expr()]
        while self.peek() == ",":
            self.next()
            args.append(self.expr())
        return args

    def close(self):
        """Eat a ")" if there is one; TI lets you leave them off at the end."""
        if self.peek() == ")":
            self.next()
        elif self.peek() not in (None, "->", ",", ":"):
            raise TIError(f"SYNTAX (expected ')' before {self.t[self.i].glyph!r})")

    # precedence, loosest first: or/xor, and, relations, + -, * / implicit,
    # negation, ^, postfix (² ³ ⁻¹ !), atoms
    def expr(self):
        node = self.and_()
        while self.peek() in (" or ", " xor "):
            op = self.next().key.strip()
            node = (op, node, self.and_())
        return node

    def and_(self):
        node = self.rel()
        while self.peek() == " and ":
            self.next()
            node = ("and", node, self.rel())
        return node

    def rel(self):
        node = self.add()
        while self.peek() in RELOPS:
            op = self.next().key
            node = ("rel", op, node, self.add())
        return node

    def add(self):
        node = self.mul()
        while self.peek() in ("+", "-"):
            op = self.next().key
            node = ("bin", op, node, self.mul())
        return node

    def mul(self):
        node = self.neg()
        while True:
            k = self.peek()
            if k in ("*", "/"):
                self.next()
                node = ("bin", k, node, self.neg())
            elif self.starts_atom(k) or k == "~":
                node = ("bin", "*", node, self.neg())
            else:
                return node

    def neg(self):
        if self.peek() == "~":
            self.next()
            return ("neg", self.neg())
        return self.power()

    def power(self):
        node = self.postfix()
        while self.peek() == "^":
            self.next()
            if self.peek() == "~":
                self.next()
                rhs = ("neg", self.postfix())
            else:
                rhs = self.postfix()
            node = ("bin", "^", node, rhs)
        return node

    def postfix(self):
        node = self.atom()
        while self.peek() in POSTFIX:
            node = ("post", self.next().key, node)
        return node

    def starts_atom(self, k):
        if k is None:
            return False
        return ((len(k) == 1 and k.isdigit()) or k in (".", "|E", "(", '"', "{", "|L", "Ans", "pi", "[e]",
                                         "[i]", "rand")
                or k in LETTERS or k in STR_VARS or k in Y_VARS or k in MAT_VARS
                or k in FUNCS1 or k in FUNCSN)

    def atom(self):
        k = self.peek()
        if k is None:
            raise TIError("SYNTAX (expression ends early)")
        if (len(k) == 1 and k.isdigit()) or k in (".", "|E"):
            return ("num", self.number())
        if k == "(":
            self.next()
            node = self.expr()
            self.close()
            return node
        if k == '"':
            self.next()
            chars = []
            while self.peek() not in (None, '"', "->"):
                chars.append(self.next())
            if self.peek() == '"':
                self.next()
            return ("str", tuple(chars))
        if k == "{":
            self.next()
            items = self.arglist()
            if self.peek() == "}":
                self.next()
            return ("listlit", items)
        if k == "|L":
            self.next()
            name = self.list_name()
            if self.peek() == "(":
                self.next()
                idx = self.expr()
                self.close()
                return ("listelem", name, idx)
            return ("listvar", name)
        if k in Y_VARS:
            self.next()
            n = k[2]
            if self.peek() == "(":
                self.next()
                arg = self.expr()
                self.close()
                return ("ycall", n, arg)
            return ("yvar", n)
        if k in STR_VARS:
            self.next()
            return ("strvar", k)
        if k in MAT_VARS:
            self.next()
            if self.peek() == "(":
                self.next()
                r = self.expr()
                if self.peek() != ",":
                    raise TIError("SYNTAX")
                self.next()
                c = self.expr()
                self.close()
                return ("matelem", k[1], r, c)
            return ("matvar", k[1])
        if k in LETTERS:
            self.next()
            return ("var", k)
        if k == "Ans":
            self.next()
            return ("ans",)
        if k == "pi":
            self.next()
            return ("num", math.pi)
        if k == "[e]":
            self.next()
            return ("num", math.e)
        if k == "[i]":
            self.next()
            return ("num", 1j)
        if k in FUNCS1 or k in FUNCSN:
            self.next()
            args = self.arglist()
            self.close()
            return ("call", k, args)
        raise TIError(f"SYNTAX (can't use {self.t[self.i].glyph!r} here)")

    def number(self):
        text = ""
        while self.peek() is not None and (self.peek() in "0123456789." and len(self.peek()) == 1):
            text += self.next().key
        if self.peek() == "|E":
            self.next()
            mant = float(text) if text else 1.0
            sign = 1
            if self.peek() == "~":
                self.next()
                sign = -1
            exp = ""
            while self.peek() is not None and self.peek() in "0123456789" and len(self.peek()) == 1:
                exp += self.next().key
            if not exp:
                raise TIError("SYNTAX")
            return mant * 10 ** (sign * int(exp))
        if text.count(".") > 1 or text == ".":
            raise TIError("SYNTAX")
        return float(text)

    def list_name(self):
        name = ""
        while len(name) < 5 and (self.peek() in LETTERS or
                                  (name and self.peek() is not None and self.peek().isdigit())):
            name += self.next().key
        if not name:
            raise TIError("SYNTAX")
        return name

    def target(self):
        k = self.peek()
        if k == "dim(":
            self.next()
            tok = self.next().key
            if tok in MAT_VARS:
                self.close()
                return ("mdim", tok[1])
            if tok != "|L":
                raise TIError("SYNTAX")
            name = self.list_name()
            self.close()
            return ("dim", name)
        node = self.atom()
        if node[0] not in ("var", "strvar", "yvar", "listvar", "listelem", "matvar", "matelem"):
            raise TIError("SYNTAX (can't store there)")
        self.end()
        return node


class Evaluator:
    def __init__(self, m: Machine):
        self.m = m

    def eval(self, node):
        kind = node[0]
        m = self.m
        if kind == "num":
            return node[1]
        if kind == "var":
            return m.reals.get(node[1], 0.0)
        if kind == "ans":
            return m.ans
        if kind == "str":
            return TIString(node[1])
        if kind == "strvar":
            if node[1] not in m.strings:
                raise TIError("UNDEFINED")
            return m.strings[node[1]]
        if kind == "listlit":
            return [m.number(self.eval(a)) for a in node[1]]
        if kind == "listvar":
            if node[1] not in m.lists:
                raise TIError("UNDEFINED")
            return list(m.lists[node[1]])
        if kind == "listelem":
            if node[1] not in m.lists:
                raise TIError("UNDEFINED")
            lst = m.lists[node[1]]
            i = m.index(self.eval(node[2]))
            if i > len(lst):
                raise TIError("INVALID DIM")
            return lst[i - 1]
        if kind == "matvar":
            if node[1] not in m.mats:
                raise TIError("UNDEFINED")
            return TIMatrix(m.mats[node[1]].rows)
        if kind == "matelem":
            mat = m.mats.get(node[1])
            if mat is None:
                raise TIError("UNDEFINED")
            r, c = m.index(self.eval(node[2])), m.index(self.eval(node[3]))
            rows, cols = mat.dims
            if r > rows or c > cols:
                raise TIError("INVALID DIM")
            return mat.rows[r - 1][c - 1]
        if kind == "yvar":
            return self.equation(node[1])
        if kind == "ycall":
            arg = self.eval(node[2])
            saved = m.reals.get("X", 0.0)
            m.reals["X"] = m.number(arg)
            try:
                return self.equation(node[1])
            finally:
                m.reals["X"] = saved
        if kind == "neg":
            return self.arith("*", -1.0, self.eval(node[1]))
        if kind == "bin":
            return self.arith(node[1], self.eval(node[2]), self.eval(node[3]))
        if kind == "post":
            return self.post(node[1], self.eval(node[2]))
        if kind == "rel":
            return self.relation(node[1], self.eval(node[2]), self.eval(node[3]))
        if kind in ("and", "or", "xor"):
            return self.logic(kind, self.eval(node[1]), self.eval(node[2]))
        if kind == "call":
            return self.call(node[1], node[2])
        raise TIError(f"SYNTAX ({kind})")

    def logic(self, kind, a, b):
        m = self.m
        if isinstance(a, list) or isinstance(b, list):
            n = len(a) if isinstance(a, list) else len(b)
            if isinstance(a, list) and isinstance(b, list) and len(a) != len(b):
                raise TIError("DIM MISMATCH")
            aa = a if isinstance(a, list) else [a] * n
            bb = b if isinstance(b, list) else [b] * n
            return [self.logic(kind, x, y) for x, y in zip(aa, bb)]
        x, y = m.truth(a), m.truth(b)
        return float({"and": x and y, "or": x or y, "xor": x != y}[kind])

    def equation(self, n):
        toks = self.m.yvars.get(n)
        if not toks:
            raise TIError("UNDEFINED")
        key = ("Y", toks)
        if key not in self.m._cache:
            self.m._cache[key] = Parser(toks).whole()
        return self.m.number(self.eval(self.m._cache[key]))

    # -- arithmetic ------------------------------------------------------------

    def arith(self, op, a, b):
        m = self.m
        m.ops += 1
        if isinstance(a, TIString) or isinstance(b, TIString):
            if op == "+" and isinstance(a, TIString) and isinstance(b, TIString):
                if not a.tokens or not b.tokens:
                    raise TIError("INVALID DIM (the calculator has no empty strings)")
                return TIString(a.tokens + b.tokens)
            raise TIError("DATA TYPE")
        if isinstance(a, list) or isinstance(b, list):
            n = len(a) if isinstance(a, list) else len(b)
            if isinstance(a, list) and isinstance(b, list) and len(a) != len(b):
                raise TIError("DIM MISMATCH")
            aa = a if isinstance(a, list) else [a] * n
            bb = b if isinstance(b, list) else [b] * n
            return [self.arith(op, x, y) for x, y in zip(aa, bb)]
        a, b = m.number(a), m.number(b)
        try:
            if op == "+":
                r = a + b
            elif op == "-":
                r = a - b
            elif op == "*":
                r = a * b
            elif op == "/":
                if b == 0:
                    raise TIError("DIVIDE BY 0")
                r = a / b
            elif op == "^":
                r = self.power(a, b)
            else:
                raise TIError("SYNTAX")
        except OverflowError:
            raise TIError("OVERFLOW")
        return m.number(r)

    def power(self, a, b):
        if a == 0:
            if b == 0:
                raise TIError("DOMAIN")
            if (b.real if isinstance(b, complex) else b) < 0:
                raise TIError("DIVIDE BY 0")
            return 0.0
        if isinstance(a, complex) or isinstance(b, complex):
            return self.complex_result(cmath.exp(b * cmath.log(a)))
        if a < 0 and b != int(b):
            # Real mode takes odd roots of negatives: (-8)^(1/3) = -2.
            if not self.m.complex_mode:
                inv = 1 / b
                if abs(inv - round(inv)) < 1e-12 and round(inv) % 2:
                    return -((-a) ** b)
                raise TIError("NONREAL ANS")
            return self.complex_result(cmath.exp(b * cmath.log(a)))
        return a ** b

    def complex_result(self, z):
        z = complex(round_tiny(z.real, z), round_tiny(z.imag, z))
        if z.imag == 0:
            return z.real
        if not self.m.complex_mode:
            raise TIError("NONREAL ANS")
        return z

    def post(self, op, v):
        if isinstance(v, list):
            return [self.post(op, x) for x in v]
        if op == "^^2":
            return self.arith("*", v, v)
        if op == "^^3":
            return self.arith("*", self.arith("*", v, v), v)
        if op == "^^-1":
            return self.arith("/", 1.0, v)
        if op == "!":
            x = self.m.real(v)
            if x != int(x) or x < 0 or x > 69:
                raise TIError("DOMAIN")
            return float(math.factorial(int(x)))
        raise TIError("SYNTAX")

    def relation(self, op, a, b):
        if isinstance(a, list) or isinstance(b, list):
            n = len(a) if isinstance(a, list) else len(b)
            aa = a if isinstance(a, list) else [a] * n
            bb = b if isinstance(b, list) else [b] * n
            return [self.relation(op, x, y) for x, y in zip(aa, bb)]
        if isinstance(a, TIString) or isinstance(b, TIString):
            if op in ("=", "!=") and isinstance(a, TIString) and isinstance(b, TIString):
                if not a.tokens or not b.tokens:
                    raise TIError("INVALID DIM (the calculator has no empty strings)")
                return float((a.tokens == b.tokens) == (op == "="))
            raise TIError("DATA TYPE")
        a, b = self.m.number(a), self.m.number(b)
        if isinstance(a, complex) or isinstance(b, complex):
            # ERR:DATA TYPE on the calculator, even for = and ≠
            raise TIError("DATA TYPE (comparing complex numbers)")
        if op in ("=", "!="):
            return float((a == b) == (op == "="))
        return float({"<": a < b, ">": a > b, "<=": a <= b, ">=": a >= b}[op])

    # -- functions -------------------------------------------------------------

    def call(self, name, arg_nodes):
        m = self.m
        if name == "dim(":
            node = arg_nodes[0]
            v = self.eval(node)
            if isinstance(v, TIMatrix):
                return [float(x) for x in v.dims]
            if not isinstance(v, list):
                raise TIError("DATA TYPE")
            return float(len(v))
        if name == "seq(":
            return self.seq(arg_nodes)
        args = [self.eval(a) for a in arg_nodes]
        if name in FUNCS1 and len(args) != 1:
            raise TIError("ARGUMENT")

        if name in ("length(", "expr(", "inString(", "sub(", "toString("):
            return self.string_func(name, args)
        if name in ("prod(", "DeltaList(", "cumSum("):
            v = args[0]
            if not isinstance(v, list):
                raise TIError("DATA TYPE")
            if name == "prod(":
                r = 1.0
                for x in v:
                    r = self.arith("*", r, x)
                return r
            if name == "DeltaList(":
                if len(v) < 2:
                    raise TIError("INVALID DIM")
                return [self.arith("-", v[i + 1], v[i]) for i in range(len(v) - 1)]
            out, total = [], 0.0
            for x in v:
                total = self.arith("+", total, x)
                out.append(total)
            return out
        if name == "augment(":
            a, b = args
            if isinstance(a, list) and isinstance(b, list):
                if any(isinstance(x, complex) for x in a + b):
                    raise TIError("DATA TYPE (augment( of complex lists: unknown on the "
                                  "calculator)")
                if len(a) + len(b) > 999:
                    raise TIError("INVALID DIM")
                return a + b
            raise TIError("DATA TYPE")
        if name == "rref(":
            return rref(m, args[0])
        if name == "remainder(":
            a, b = int(m.real(args[0])), int(m.real(args[1]))
            if b == 0:
                raise TIError("DIVIDE BY 0")
            return float(a % b)
        if name in ("max(", "min("):
            pick = max if name == "max(" else min
            if len(args) == 2 and (isinstance(args[0], list) or isinstance(args[1], list)):
                a, b = args
                n = len(a) if isinstance(a, list) else len(b)
                aa = a if isinstance(a, list) else [a] * n
                bb = b if isinstance(b, list) else [b] * n
                if len(aa) != len(bb):
                    raise TIError("DIM MISMATCH")
                return [pick(m.real(x), m.real(y)) for x, y in zip(aa, bb)]
            if len(args) == 1:
                if not isinstance(args[0], list) or not args[0]:
                    raise TIError("DATA TYPE" if not isinstance(args[0], list) else "INVALID DIM")
                return pick(m.real(x) for x in args[0])
            if len(args) != 2:
                raise TIError("ARGUMENT")
            return pick(m.real(args[0]), m.real(args[1]))
        if name == "round(":
            if len(args) == 1:
                args.append(9.0)
            v, n = args[0], int(m.real(args[1]))

            def one(x):
                x = m.number(x)
                if isinstance(x, complex):
                    return m.number(complex(round_ti(x.real, n), round_ti(x.imag, n)))
                return round_ti(x, n)
            if isinstance(v, list):
                return [one(x) for x in v]
            return one(v)
        if name == "sum(":
            if not isinstance(args[0], list):
                raise TIError("DATA TYPE")
            return m.number(sum(args[0]))
        if name in ("gcd(", "lcm("):
            def one(x, y):
                x, y = m.real(x), m.real(y)
                # the calculator wants whole numbers that aren't negative
                if x != int(x) or y != int(y) or x < 0 or y < 0 or max(x, y) >= 1e12:
                    raise TIError("DOMAIN")
                return float(math.gcd(int(x), int(y)) if name == "gcd(" else math.lcm(int(x), int(y)))
            a, b = args
            if isinstance(a, list) or isinstance(b, list):
                n = len(a) if isinstance(a, list) else len(b)
                aa = a if isinstance(a, list) else [a] * n
                bb = b if isinstance(b, list) else [b] * n
                return [one(x, y) for x, y in zip(aa, bb)]
            return one(a, b)

        v = args[0]
        if isinstance(v, list):
            return [self.unary(name, x) for x in v]
        return self.unary(name, v)

    def unary(self, name, v):
        m = self.m
        m.ops += 1
        v = m.number(v)
        z = isinstance(v, complex)
        try:
            if name == "abs(":
                return abs(v)
            if name == "real(":
                return v.real if z else v
            if name == "imag(":
                return v.imag if z else 0.0
            if name == "conj(":
                return v.conjugate() if z else v
            if name == "angle(":
                return cmath.phase(v) if z else (0.0 if v >= 0 else math.pi)
            if name == "not(":
                return float(not m.truth(v))
            if name == "int(":
                return float(math.floor(m.real(v)))
            if name == "iPart(":
                return float(math.trunc(m.real(v)))
            if name == "fPart(":
                x = m.real(v)
                return x - math.trunc(x)
            if name == "sqrt(":
                if not z and v >= 0:
                    return math.sqrt(v)
                return self.complex_result(cmath.sqrt(v))
            if name == "cuberoot(":
                if z:
                    return self.complex_result(cmath.exp(cmath.log(v) / 3))
                return math.copysign(abs(v) ** (1 / 3), v)
            if name in ("ln(", "log("):
                if v == 0:
                    raise TIError("DOMAIN")
                if not z and v > 0:
                    return math.log(v) if name == "ln(" else math.log10(v)
                w = cmath.log(v) if name == "ln(" else cmath.log10(v)
                return self.complex_result(w)
            if name == "e^^(":
                return self.complex_result(cmath.exp(v)) if z else math.exp(v)
            if name == "10^^(":
                return self.power(10.0, v)
            if z:
                raise TIError("DATA TYPE")   # TI's trig works on real numbers only
            k = 1.0 if m.radians else math.pi / 180
            if name in ("sin(", "cos(", "tan("):
                if name == "tan(" and abs(math.cos(v * k)) < 1e-15:
                    raise TIError("DOMAIN")
                return {"sin(": math.sin, "cos(": math.cos, "tan(": math.tan}[name](v * k)
            if name in ("sin^-1(", "cos^-1("):
                if abs(v) > 1:
                    raise TIError("DOMAIN")
                f = math.asin if name == "sin^-1(" else math.acos
                return f(v) / k
            if name == "tan^-1(":
                return math.atan(v) / k
            if name in ("sinh(", "cosh(", "tanh("):
                return {"sinh(": math.sinh, "cosh(": math.cosh, "tanh(": math.tanh}[name](v)
        except OverflowError:
            raise TIError("OVERFLOW")
        except ValueError:
            raise TIError("DOMAIN")
        raise TIError(f"SYNTAX ({name})")

    def seq(self, nodes):
        m = self.m
        if len(nodes) not in (4, 5) or nodes[1][0] != "var":
            raise TIError("ARGUMENT")
        var = nodes[1][1]
        start, end = m.real(self.eval(nodes[2])), m.real(self.eval(nodes[3]))
        step = m.real(self.eval(nodes[4])) if len(nodes) == 5 else 1.0
        if step == 0:
            raise TIError("INCREMENT")
        saved = m.reals.get(var, 0.0)
        out, k = [], start
        try:
            while (step > 0 and k <= end + 1e-12) or (step < 0 and k >= end - 1e-12):
                m.reals[var] = k
                v = self.eval(nodes[0])
                if isinstance(v, (list, TIString, TIMatrix)):
                    raise TIError("DATA TYPE")
                v = m.number(v)
                if isinstance(v, complex):
                    raise TIError("DATA TYPE (seq( of complex values: unknown on the "
                                  "calculator, so Brainiac must not rely on it)")
                out.append(v)
                if len(out) > 999:
                    raise TIError("INVALID DIM")
                k = m.number(k + step)
        finally:
            m.reals[var] = saved
        return out

    def string_func(self, name, args):
        m = self.m
        if name == "length(":
            s = args[0]
            if not isinstance(s, TIString):
                raise TIError("DATA TYPE")
            if not s.tokens:
                raise TIError("INVALID DIM (the calculator has no empty strings)")
            return float(len(s.tokens))
        if name == "expr(":
            s = args[0]
            if not isinstance(s, TIString):
                raise TIError("DATA TYPE")
            if not s.tokens:
                raise TIError("SYNTAX")
            return self.eval(Parser(s.tokens).whole())
        if name == "toString(":
            if isinstance(args[0], (TIString, list, TIMatrix)):
                raise TIError("DATA TYPE")
            return text_to_string(fmt(m.number(args[0])))
        if name == "inString(":
            s, sub_ = args[0], args[1]
            start = int(m.real(args[2])) if len(args) > 2 else 1
            if not isinstance(s, TIString) or not isinstance(sub_, TIString):
                raise TIError("DATA TYPE")
            hay, needle = s.tokens, sub_.tokens
            if not hay or not needle:
                raise TIError("INVALID DIM (the calculator has no empty strings)")
            for i in range(start - 1, len(hay) - len(needle) + 1):
                if hay[i:i + len(needle)] == needle:
                    return float(i + 1)
            return 0.0
        if name == "sub(":
            s = args[0]
            if not isinstance(s, TIString) or len(args) != 3:
                raise TIError("ARGUMENT")
            if not s.tokens:
                raise TIError("INVALID DIM (the calculator has no empty strings)")
            start, length = int(m.real(args[1])), int(m.real(args[2]))
            if start < 1 or length < 1 or start + length - 1 > len(s.tokens):
                raise TIError("DOMAIN")
            return TIString(s.tokens[start - 1:start - 1 + length])
        raise TIError("SYNTAX")


def rref(m: "Machine", mat) -> "TIMatrix":
    """Reduced row echelon form, by Gauss-Jordan with partial pivoting (as TI does)."""
    if not isinstance(mat, TIMatrix):
        raise TIError("DATA TYPE")
    a = [[m.number(x) for x in row] for row in mat.rows]
    rows, cols = mat.dims
    if cols < rows:
        raise TIError("INVALID DIM")
    r = 0
    for c in range(cols):
        if r >= rows:
            break
        p = max(range(r, rows), key=lambda i: abs(a[i][c]))
        if abs(a[p][c]) < 1e-13 * max(1.0, max(abs(x) for row in a for x in row)):
            continue
        a[r], a[p] = a[p], a[r]
        piv = a[r][c]
        a[r] = [m.number(x / piv) for x in a[r]]
        for i in range(rows):
            if i != r and a[i][c] != 0:
                f = a[i][c]
                a[i] = [m.number(x - f * y) for x, y in zip(a[i], a[r])]
        r += 1
    return TIMatrix(a)


def round_ti(x: float, n: int) -> float:
    if not 0 <= n <= 9:
        raise TIError("DOMAIN")
    return float(round(x, n))


def round_tiny(part: float, z: complex) -> float:
    """cmath leaves crumbs like 1e-17 where TI shows an exact 0."""
    return 0.0 if abs(part) < 1e-13 * max(1.0, abs(z)) else part


def load_program(path=None) -> Machine:
    """Brainiac, freshly built from source (so tests never run a stale .8xp)."""
    from brainiac.build import program_bytes
    if path is None:
        return Machine(tokens_from_bytes(program_bytes()))
    return Machine.from_file(Path(path))
