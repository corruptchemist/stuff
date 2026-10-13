"""Regenerate the screenshots in brainiac/screenshots/ (used by the README).

    python -m brainiac.shots
"""

from pathlib import Path

from brainiac.screen import to_png
from brainiac.tibasic import load_program

OUT = Path(__file__).resolve().parent / "screenshots"

# (file name, caption, answers to give, in order)
TOURS = [
    ("01-menu", "Main menu", []),
    ("02-solve-prompt", "Solve for X: type an equation", ["1"]),
    ("03-solve-quadratic", "X²=5X-6 has two answers", ["1", "X²=5X-6"]),
    ("04-solve-cubic", "X³-2X=5: no algebra needed", ["1", "X^3-2X=5"]),
    ("05-zeros-prompt", "Find all zeros: function, then range", ["2", "X^3-4X", "-3"]),
    ("06-zeros-cubic", "Every zero of X³-4X", ["2", "X^3-4X", "", ""]),
    ("07-zeros-touch", "(X-2)²(X+1): crosses at -1, touches at 2", ["2", "(X-2)^2(X+1)", "", ""]),
    ("08-zeros-sine", "sin(X) from -20 to 20: page 1 of 3", ["2", "sin(X)", "-20", "20"]),
    ("09-zeros-sine-p2", "...press ENTER for the next page", ["2", "sin(X)", "-20", "20", ""]),
    ("10-no-answers", "1/X never reaches zero (asymptote skipped)", ["2", "1/X", "", ""]),
    ("11-identity", "X+1=1+X is true for every X", ["1", "X+1=1+X"]),
    ("12-help", "How to use, page 1", ["3"]),
    ("13-what-next", "After the answers", ["1", "2X+3=7", ""]),
]


def main():
    for name, caption, answers in TOURS:
        m = load_program()
        m.run()
        for a in answers:
            m.answer(a)
        to_png(m, OUT / f"{name}.png", caption=caption)
        print(f"{name}.png  ({m.wait.kind})")


if __name__ == "__main__":
    main()
