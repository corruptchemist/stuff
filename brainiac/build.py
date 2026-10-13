"""Turn the TI-BASIC source into a .8xp file you can send to the calculator.

    python -m brainiac.build

Reads brainiac/src/BRAINIAC.txt, drops the // note lines, tokenizes the rest
with the TI-Toolkit token tables (the same tables TI Connect CE agrees with)
and writes brainiac/dist/BRAINIAC.8xp.
"""

from pathlib import Path

from tivars.models import TI_84PCE
from tivars.tokenizer import encode
from tivars.tokenizer.decoder import decode
from tivars.types import TIProgram

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "src" / "BRAINIAC.txt"
OUTPUT = HERE / "dist" / "BRAINIAC.8xp"
NAME = "BRAINIAC"


def source_lines(path: Path = SOURCE) -> list[str]:
    """The program's lines as the calculator will see them."""
    lines = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("//") or not line.strip():
            continue
        # The Pause token is "Pause " with a trailing space, which editors
        # love to strip; without it the line tokenizes as P,a,u,s,e.
        if line.rstrip() == "Pause":
            line = "Pause "
        lines.append(line)
    return lines


def program_bytes(path: Path = SOURCE) -> bytes:
    data, _ = encode("\n".join(source_lines(path)))
    check(data)
    return data


def check(data: bytes):
    """Refuse to ship tokens Brainiac never means to use.

    The program is written entirely in capitals, so a lowercase-letter token
    means a command name was mistyped and got spelled out letter by letter.
    """
    tokens, _ = decode(data)
    for i, token in enumerate(tokens):
        if token.bits[:1] == b"\xbb" and 0xB0 <= token.bits[1] <= 0xCA:
            context = "".join(t.display for t in tokens[max(0, i - 8):i + 4])
            raise ValueError(f"lowercase token {token.display!r} near {context!r}")
        if getattr(token, "attrs", {}).get("illegal"):
            raise ValueError(f"illegal token {token.escape}")


def build(path: Path = SOURCE, out: Path = OUTPUT) -> Path:
    program = TIProgram(name=NAME)
    program.data = program_bytes(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    program.save(str(out), model=TI_84PCE)
    return out


def main():
    out = build()
    size = len(program_bytes())
    print(f"wrote {out.relative_to(HERE.parent)}  ({size} bytes of program, "
          f"{len(source_lines())} lines)")


if __name__ == "__main__":
    main()
