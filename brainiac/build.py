"""Turn the TI-BASIC source into a .8xp file you can send to the calculator.

    python -m brainiac.build

Reads brainiac/src/BRAINIAC.txt, expands its macros, tokenizes the result
with the TI-Toolkit token tables (the same tables TI Connect CE agrees with)
and writes brainiac/dist/BRAINIAC.8xp.

The source is TI-BASIC plus a few conveniences, all expanded here:

  // note          a line for humans; dropped
  @name           a label with a readable name; becomes a 2-character TI label
  #sub name       starts a subroutine (it ends at #return)
  #call name      runs a subroutine and carries on after it
  π               the pi token (the token tables can't type it directly)

TI-BASIC has no subroutines, so #call pushes a return number onto the list
BS and jumps; #return pops it and jumps back to the matching call.

The build also refuses the classic way TI-BASIC programs break: a Goto (or
Menu(, or #call) inside an If-Then, For, While or Repeat block. The
calculator never closes a block it jumps out of, so each such jump leaks a
little memory until the program dies with ERR:MEMORY.
"""

import re
from pathlib import Path

from tivars.models import TI_84PCE
from tivars.tokenizer import encode
from tivars.tokenizer.decoder import decode
from tivars.types import TIProgram

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "src" / "BRAINIAC.txt"
OUTPUT = HERE / "dist" / "BRAINIAC.8xp"
NAME = "BRAINIAC"

LABEL_REF = re.compile(r"@([a-z][a-z0-9_]*)")
LABEL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"


class BuildError(ValueError):
    pass


def raw_lines(path: Path = SOURCE) -> list[tuple[int, str]]:
    out = []
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line.lstrip().startswith("//") or not line.strip():
            continue
        # The Pause token is "Pause " with a trailing space, which editors
        # love to strip; without it the line tokenizes as P,a,u,s,e.
        if line.rstrip() == "Pause":
            line = "Pause "
        out.append((n, line.replace("π", r"\xAC")))
    return out


def expand(lines: list[tuple[int, str]]) -> list[tuple[int, str]]:
    """Expand #sub/#call/#return and @labels into plain TI-BASIC."""
    calls: dict[str, list[int]] = {}
    for _, line in lines:
        if line.startswith("#call "):
            calls.setdefault(line.split()[1], []).append(len(sum(calls.values(), [])) + 1)

    codes: dict[str, str] = {}

    def code(name: str) -> str:
        if name not in codes:
            # A0..A9, AA..AZ, B0.. : a letter then a digit or letter (936 in all)
            i = len(codes)
            if i >= 26 * 36:
                raise BuildError("out of 2-character labels")
            second = LABEL_CHARS[26:] + LABEL_CHARS[:26]
            codes[name] = LABEL_CHARS[i // 36] + second[i % 36]
        return codes[name]

    out = []
    current_sub = None
    site = 0
    subs = set()
    for n, line in lines:
        if line.startswith("#sub "):
            current_sub = line.split()[1]
            if current_sub in subs:
                raise BuildError(f"line {n}: subroutine {current_sub} defined twice")
            subs.add(current_sub)
            out.append((n, f"Lbl {code('sub_' + current_sub)}"))
        elif line.strip() == "#return":
            if current_sub is None:
                raise BuildError(f"line {n}: #return outside a #sub")
            out.append((n, "ʟBS(dim(ʟBS))→θ"))
            out.append((n, "dim(ʟBS)-1→dim(ʟBS)"))
            for k in calls.get(current_sub, []):
                out.append((n, f"If θ={k}"))
                out.append((n, f"Goto {code(f'ret_{k}')}"))
            current_sub = None
        elif line.startswith("#call "):
            name = line.split()[1]
            site += 1
            out.append((n, f"{site}→ʟBS(1+dim(ʟBS))"))
            out.append((n, f"Goto {code('sub_' + name)}"))
            out.append((n, f"Lbl {code(f'ret_{site}')}"))
        elif line.startswith("#"):
            raise BuildError(f"line {n}: unknown macro {line.split()[0]}")
        else:
            out.append((n, LABEL_REF.sub(lambda m: code(m.group(1)), line)))
    missing = set(calls) - subs
    if missing:
        raise BuildError(f"#call of undefined subroutine(s): {', '.join(sorted(missing))}")
    return out


def statements(line: str) -> list[str]:
    """Split a line at colons that aren't inside a string."""
    parts, cur, in_str = [], "", False
    for ch in line:
        if ch == '"':
            in_str = not in_str
        elif ch == "→":
            in_str = False
        elif ch == ":" and not in_str:
            parts.append(cur)
            cur = ""
            continue
        cur += ch
    parts.append(cur)
    return parts


LIST_THEN_PAREN = re.compile(r"ʟ[A-Zθ][A-Z0-9θ]*\(([^()]*(?:\([^()]*\)[^()]*)*)\)")


def check_list_brackets(lines: list[tuple[int, str]]):
    """Catch ʟX(...) meant as multiplication.

    To the calculator ʟBY(2πS) means element number 2πS of list BY, never
    BY times 2πS. Element numbers in Brainiac are simple (I+1, dim(ʟX)), so
    a comparison, π or abs( inside the brackets means a missing *.
    """
    for n, line in lines:
        for m in LIST_THEN_PAREN.finditer(line):
            if re.search(r"[<>=≠≤≥]|\\xAC|abs\(", m.group(1)):
                raise BuildError(f"line {n}: {m.group(0)!r} is an element lookup; "
                                 f"for multiplication write ʟ...*(...)")


def check_structure(lines: list[tuple[int, str]]):
    """Blocks balance, labels exist, and no jump ever leaves a block."""
    depth = 0
    labels, targets = {}, []
    for n, line in lines:
        for st in statements(line):
            if st == "Then" or st.startswith(("While ", "Repeat ", "For(")):
                depth += 1
            elif st == "End":
                depth -= 1
                if depth < 0:
                    raise BuildError(f"line {n}: End without a block")
            elif st.startswith(("Goto ", "Menu(", "Lbl ")):
                if depth:
                    raise BuildError(f"line {n}: {st.split('(')[0].split()[0]} inside a block "
                                     f"(depth {depth}) leaks memory on the calculator")
                if st.startswith("Lbl "):
                    name = st[4:]
                    if name in labels:
                        raise BuildError(f"line {n}: label {name} defined twice")
                    labels[name] = n
                elif st.startswith("Goto "):
                    targets.append((n, st[5:]))
                else:
                    args = st[len("Menu("):].rstrip(")").split(",")
                    targets += [(n, a) for a in args[2::2]]
    if depth:
        raise BuildError(f"{depth} block(s) never closed with End")
    for n, name in targets:
        if name not in labels:
            raise BuildError(f"line {n}: jump to missing label {name}")


def source_lines(path: Path = SOURCE) -> list[str]:
    """The program's lines as the calculator will see them."""
    lines = expand(raw_lines(path))
    check_structure(lines)
    check_list_brackets(lines)
    return [line for _, line in lines]


def program_bytes(path: Path = SOURCE) -> bytes:
    data, _ = encode("\n".join(source_lines(path)))
    check(data)
    return data


def min_os(path: Path = SOURCE):
    _, since = encode("\n".join(source_lines(path)))
    return since


def check(data: bytes):
    """Refuse to ship tokens Brainiac never means to use.

    The program is written entirely in capitals, so a lowercase-letter token
    means a command name was mistyped and got spelled out letter by letter.
    """
    tokens, _ = decode(data)
    for i, token in enumerate(tokens):
        if token.bits[:1] == b"\xbb" and 0xB0 <= token.bits[1] <= 0xCA:
            context = "".join(t.display for t in tokens[max(0, i - 8):i + 4])
            raise BuildError(f"lowercase token {token.display!r} near {context!r}")
        if getattr(token, "attrs", {}).get("illegal"):
            raise BuildError(f"illegal token {token.escape}")


def build(path: Path = SOURCE, out: Path = OUTPUT) -> Path:
    program = TIProgram(name=NAME)
    program.data = program_bytes(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    program.save(str(out), model=TI_84PCE)
    return out


def main():
    out = build()
    size = len(program_bytes())
    os = min_os()
    print(f"wrote {out.relative_to(HERE.parent)}  ({size} bytes of program, "
          f"{len(source_lines())} lines, needs {os.model} OS {os.version}+)")


if __name__ == "__main__":
    main()
