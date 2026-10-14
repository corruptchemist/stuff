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
    ("16-geometry-menu", "Geometry menu", ["5", "1"]),
    ("17-geo-pythagorean-ref", "Pythagorean: the letters first...", ["5", "1", "1", "2"]),
    ("18-geo-pythagorean", "...then the figure with your numbers", ["5", "1", "1", "2", "", "3", "4", "0"]),
    ("19-geo-pythagorean-working", "...and the working", ["5", "1", "1", "2", "", "3", "4", "0", ""]),
    ("20-geo-30-60-90", "30-60-90 from the hypotenuse", ["5", "1", "1", "3", "2", "", "0", "0", "10"]),
    ("21-geo-trig-ref", "Trig: which side is which", ["5", "1", "1", "4"]),
    ("22-geo-trig", "Trig: angle 35, hypotenuse 10", ["5", "1", "1", "4", "", "35", "0", "0", "10"]),
    ("23-geo-similar-ref", "Similar triangles: the letters", ["5", "1", "1", "5"]),
    ("24-geo-similar", "Similar triangles, solved", ["5", "1", "1", "5", "", "3", "4", "5", "6"]),
    ("25-geo-parallel-ref", "Parallel lines: the eight angles", ["5", "1", "2", "3"]),
    ("26-geo-parallel", "Parallel lines with angle 1 = 60", ["5", "1", "2", "3", "", "60"]),
    ("27-geo-intersecting", "Intersecting lines", ["5", "1", "2", "2", "", "50"]),
    ("28-geo-trapezoid-ref", "Trapezoid: the letters", ["5", "1", "3", "4"]),
    ("29-geo-trapezoid", "Trapezoid area", ["5", "1", "3", "4", "", "10", "6", "4", "5", "5"]),
    ("30-geo-box-ref", "Box: the letters", ["5", "1", "4", "1"]),
    ("31-geo-cylinder", "Cylinder volume", ["5", "1", "4", "2", "", "3", "7"]),
    ("32-geo-cone", "Cone", ["5", "1", "4", "3", "", "3", "4"]),
    ("33-geo-pyramid", "Square pyramid", ["5", "1", "4", "5", "", "6", "4"]),
    ("34-geo-arc", "Arc and sector", ["5", "1", "5", "2", "", "6", "120"]),
    ("35-geo-circle-equation", "Circle equation from center and radius", ["5", "1", "5", "3", "", "1", "2", "-3", "5"]),
    ("36-geo-circle-equation-working", "...and both forms", ["5", "1", "5", "3", "", "1", "2", "-3", "5", ""]),
    ("37-geo-scale", "Scale factors", ["5", "1", "6", "", "3"]),
    ("38-help-me-choose", "Help me choose: questions that find the tool", ["6"]),
    ("39-help-me-choose-expression", "...no = sign: what do you need?", ["6", "3"]),
    ("40-inequality-number-line", "X²-4≥0: the number line first", ["1", "X^2-4≥0"]),
    ("41-inequality-working", "...then the working", ["1", "X^2-4≥0", ""]),
    ("42-inequality-abs", "|X-1|<3", ["1", "abs(X-1)<3"]),
    ("43-inequality-log", "log(X)<2: logs only exist for X>0", ["1", "log(X)<2"]),
    ("44-inequality-rational", "(X-1)/(X+2)≤0: open and filled dots", ["1", "(X-1)/(X+2)≤0"]),
    ("45-exponential", "2^(X+1)=8^(X-1)", ["1", "2^(X+1)=8^(X-1)"]),
    ("46-exponential-alone", "5(2^X)=40: get the power alone...", ["1", "5(2^X)=40"]),
    ("47-exponential-alone-2", "...then take logs", ["1", "5(2^X)=40", ""]),
    ("48-e-alone", "3e^X-2=10, second page", ["1", "3e^(X)-2=10", ""]),
    ("49-log-equation", "log(X)+log(X-3)=1, with the check", ["1", "log(X)+log(X-3)=1"]),
    ("50-ln-alone", "3ln(X)=6, second page", ["1", "3ln(X)=6", ""]),
    ("51-degree-simplify", "The bug report, fixed: Simplify in DEGREE mode", ["2", "3", "(3X^3-X^2-6)(X-5)"], True),
    ("52-degree-trig", "sin(X)=.5 in DEGREE mode", ["1", "sin(X)=.5"], True),
    ("53-degree-trig-inequality", "cos(X)≤0 in DEGREE mode", ["1", "cos(X)≤0"], True),
    ("54-help-angles", "How to use, page 3", ["6", "7", "", ""]),
    ("55-units-menu", "Unit conversion (main menu 5, then 2)", ["5", "2"]),
    ("56-units-pick", "Pick the units by number", ["5", "2", "1"]),
    ("57-units-mi-km", "5 MI to KM, one fraction at a time", ["5", "2", "1", "8", "4", "5"]),
    ("58-units-rate", "60 MI/HR to FT/SEC", ["5", "2", "6", "1", "8", "6", "1", "3", "1", "60"]),
    ("59-units-mass", "70 KG to LB", ["5", "2", "2", "3", "5", "70"]),
    ("60-units-temperature", "98.6 °F to °C and K", ["5", "2", "7", "1", "1", "3", "98.6"]),
    ("61-trig-of-a-root", "sin(√(X))=.5 (was ERR:DATA TYPE)", ["1", "sin(√(X))=.5"]),
    ("62-inverse-trig-inequality", "sin⁻¹(X)≥0: X stops at 1", ["1", "sin⁻¹(X)≥0"]),
    ("63-geometry-refuses-i", "Geometry: 3+4i typed for a length", ["5", "1", "3", "2", "", "3+4𝑖", "4"]),
]


def main():
    for name, caption, answers, *degree in TOURS:
        m = load_program()
        m.radians = not degree
        m.run()
        for a in answers:
            m.answer(a)
        to_png(m, OUT / f"{name}.png", caption=caption)
        print(f"{name}.png  ({m.wait.kind})")


if __name__ == "__main__":
    main()
