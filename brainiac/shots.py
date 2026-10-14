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
    ("02-polynomials-menu", "Polynomials menu", ["3"]),
    ("03-solve-complex", "X²+5X+20=0: exact complex roots", ["1", "X^2+5X+20=0"]),
    ("04-solve-cubic", "A cubic, factored and solved", ["1", "X^3=6X^2-11X+6"]),
    ("05-solve-search", "Not a polynomial: searched -99 to 99", ["1", "e^(X^2)=5"]),
    ("06-roots-degree5", "Any degree: X⁵+X+1, real and complex", ["3", "1", "X^5+X+1"]),
    ("07-quadratic-steps-1", "Quadratic step by step, page 1", ["3", "2", "2X^2+3X-5"]),
    ("08-quadratic-steps-2", "...page 2", ["3", "2", "2X^2+3X-5", ""]),
    ("09-quadratic-steps-3", "...page 3", ["3", "2", "2X^2+3X-5", "", ""]),
    ("10-factor", "Factor", ["3", "4", "6X^2+X-2"]),
    ("11-simplify", "Simplify / expand", ["3", "3", "(A+B)^2-(A-B)^2"]),
    ("12-divide", "Polynomial division", ["3", "5", "X^3+2X+5", "X^2+1"]),
    ("13-system", "A 3x3 system", ["4", "2X+3Y-Z=1", "X-Y+2Z=3", "3X+Y+Z=6", ""]),
    ("14-rearrange-pendulum", "Rearrange T=2π√(L/G) for L", ["5", "T=2pi√(L/G)", "L"]),
    ("15-rearrange-gas", "Rearrange PV=NRT for T", ["5", "PV=NRT", "T"]),
    ("16-inverse", "Inverse of (X+1)/(X-2)", ["6", "(X+1)/(X-2)"]),
    ("17-help", "How to use, page 1", ["7"]),
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
