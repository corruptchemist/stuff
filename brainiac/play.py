"""Try Brainiac on your computer, no calculator needed.

    python -m brainiac.play                 # the program you'd send (dist/BRAINIAC.8xp)
    python -m brainiac.play --source        # rebuilt from src/ first, for testing edits
    python -m brainiac.play --png shots/    # also save a picture of every screen

At a menu, type the option's number. At a prompt, type like you would on a
computer: x^2-4=0, sqrt(x), pi, e^(x), -5. At a pause, just press Enter.
Type "quit" at any time to stop.
"""

import argparse
import sys

from brainiac.build import OUTPUT
from brainiac.screen import to_png, to_text
from brainiac.tibasic import Machine, load_program


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", action="store_true", help="run src/BRAINIAC.txt instead of the .8xp")
    ap.add_argument("--png", metavar="DIR", help="save a PNG of each screen into DIR")
    args = ap.parse_args(argv)

    if sys.platform == "win32":
        import os
        os.system("")  # turn on ANSI colours in the Windows console
        sys.stdout.reconfigure(encoding="utf-8")

    m = load_program() if args.source else Machine.from_file(OUTPUT)
    wait = m.run()
    shot = 0
    while True:
        print()
        print(to_text(m))
        if args.png:
            shot += 1
            to_png(m, f"{args.png}/screen{shot:03}.png")
        if wait.kind == "done":
            print("(program finished)")
            return 0
        if wait.kind == "error":
            print(f"*** {wait.message}  (program line {wait.line}) ***")
            return 1
        hint = {"menu": "pick a number", "input": "type, then Enter",
                "pause": "press Enter"}[wait.kind]
        try:
            text = input(f"[{hint}] > ")
        except EOFError:
            return 0
        if text.strip().lower() == "quit":
            return 0
        try:
            wait = m.answer(text)
        except RuntimeError as e:  # e.g. a menu number that isn't there
            print(f"  ({e})")
            wait = m.wait


if __name__ == "__main__":
    sys.exit(main())
