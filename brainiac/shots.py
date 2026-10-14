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
    ("02-polynomials-menu", "Polynomials menu", ["2"]),
    ("03-solve-complex", "X²+5X+20=0: exact complex roots", ["1", "X^2+5X+20=0"]),
    ("04-solve-abs", "|2X-3|=7, both cases", ["1", "abs(2X-3)=7"]),
    ("05-solve-radical", "√(X+5)=X-1, with the extraneous root caught", ["1", "√(X+5)=X-1"]),
    ("06-solve-rational", "1/(X-1)+1/(X+1)=1", ["1", "1/(X-1)+1/(X+1)=1"]),
    ("07-roots-degree5", "Any degree: X⁵+X+1, real and complex", ["2", "1", "X^5+X+1"]),
    ("08-quadratic-steps-1", "Quadratic steps from factored form, page 1", ["2", "2", "(X-1)(X+3)"]),
    ("09-quadratic-steps-3", "...page 3: vertex form, sum and product", ["2", "2", "(X-1)(X+3)", "", ""]),
    ("10-factor", "Factor", ["2", "4", "6X^2+X-2"]),
    ("11-simplify", "Simplify / expand", ["2", "3", "(A+B)^2-(A-B)^2"]),
    ("12-divide", "Polynomial division", ["2", "5", "X^3+2X+5", "X^2+1"]),
    ("13-system", "A 3x3 system", ["3", "3", "2X+3Y-Z=1", "X-Y+2Z=3", "3X+Y+Z=6"]),
    ("14-rearrange-pendulum", "Rearrange T=2π√(L/G) for L", ["4", "1", "T=2pi√(L/G)", "L"]),
    ("15-inverse", "Inverse of (X+1)/(X-2)", ["4", "2", "(X+1)/(X-2)"]),
    ("16-geometry-menu", "Geometry menu", ["5"]),
    ("17-geo-pythagorean", "Pythagorean theorem", ["5", "1", "2", "3", "4", "0"]),
    ("18-geo-pythagorean-working", "...and the working", ["5", "1", "2", "3", "4", "0", ""]),
    ("19-geo-30-60-90", "30-60-90 from the hypotenuse", ["5", "1", "3", "2", "0", "0", "10"]),
    ("20-geo-trig", "Trig: angle 35, hypotenuse 10", ["5", "1", "4", "35", "0", "0", "10"]),
    ("21-geo-similar", "Similar triangles", ["5", "1", "5", "3", "4", "5", "6"]),
    ("22-geo-parallel", "Parallel lines cut by a transversal", ["5", "2", "3", "60"]),
    ("23-geo-intersecting", "Intersecting lines", ["5", "2", "2", "50"]),
    ("24-geo-trapezoid", "Trapezoid area", ["5", "3", "4", "10", "6", "4", "5", "5"]),
    ("25-geo-cylinder", "Cylinder volume", ["5", "4", "2", "3", "7"]),
    ("26-geo-cone", "Cone", ["5", "4", "3", "3", "4"]),
    ("27-geo-pyramid", "Square pyramid", ["5", "4", "5", "6", "4"]),
    ("28-geo-arc", "Arc and sector", ["5", "5", "2", "6", "120"]),
    ("29-geo-circle-equation", "Circle equation from center and radius", ["5", "5", "3", "1", "2", "-3", "5"]),
    ("30-geo-circle-equation-working", "...and both forms", ["5", "5", "3", "1", "2", "-3", "5", ""]),
    ("31-geo-scale", "Scale factors", ["5", "6", "3"]),
    ("32-help", "How to use", ["6"]),
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
