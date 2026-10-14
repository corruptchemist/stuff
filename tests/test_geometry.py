"""Tests for GEOMETRY, run on the simulated TI-84 Plus CE (graph screen too)."""

import math

import pytest

from brainiac.build import min_os, output_path, program_bytes, source_lines
from brainiac.screen import view
from brainiac.tibasic import GH, GW, load_program
from tivars.types import TIProgram

TRI, ANG, AREA, SOLID, CIRC, SCALE, BACK = "1234567"


def run(*answers):
    """Start Geometry, answer, and return the machine, the report lines, and the
    figure's labels (what Text( drew, in reading order)."""
    m = load_program("GEOMETRY")
    m.run()
    labels = []
    for a in answers:
        assert m.wait.kind in ("menu", "input", "pause"), (answers, m.wait, m.statement_text())
        m.answer(a)
        if m.showing_graph and m.wait.kind == "pause":
            labels = [t for _, _, t, _ in sorted(m.graph.texts)]
            assert m.graph.pixels, "a figure should have been drawn"
            assert all(0 <= x < GW and 0 <= y < GH for x, y in m.graph.pixels)
    assert m.wait.kind != "error", f"{m.wait.message} at: {m.statement_text()}"
    lines = []
    while True:
        lines += [ln.rstrip() for ln in view(m) if ln.strip() and "ENTER:" not in ln]
        if m.wait.kind == "pause" and "MORE" in m.screen.lines()[9]:
            m.answer("")
            continue
        break
    assert m.leaks == 0 and m.blocks == []
    return m, lines, labels


def test_shipped_8xp_matches_source():
    shipped = TIProgram.open(str(output_path("GEOMETRY")))
    assert shipped.name == "GEOMETRY"
    assert shipped.data == program_bytes("GEOMETRY"), "run: python -m brainiac.build"
    assert min_os("GEOMETRY").version == "5.2.0"


def test_no_menu_has_more_than_seven_options():
    for line in source_lines("GEOMETRY"):
        if line.startswith("Menu("):
            assert line.count(",") <= 14, line


def test_no_source_line_stores_an_empty_string():
    for line in source_lines("GEOMETRY"):
        assert '""→' not in line, line


# -- triangles ------------------------------------------------------------------

def test_triangle_angles():
    m, lines, labels = run(TRI, "1", "50", "60", "")
    assert lines[-2:] == ["C=180-50-60", " =70"]
    assert {"A=50", "B=60", "C=70"} <= set(labels)


@pytest.mark.parametrize("a, b, c, expected", [
    ("3", "4", "0", ["9+16=25", "C=√(9+16)", "A=3", "B=4", "C=5"]),
    ("0", "12", "13", ["25+144=169", "A=√(169-144)", "A=5", "B=12", "C=13"]),
    ("1", "1", "0", ["1+1=2", "C=√(1+1)", "A=1", "B=1", "C=1.414"]),
])
def test_pythagorean(a, b, c, expected):
    m, lines, labels = run(TRI, "2", a, b, c, "")
    assert lines[1:] == expected
    assert "90" in labels


def test_pythagorean_rejects_a_short_hypotenuse():
    m, lines, _ = run(TRI, "2", "0", "4", "2")
    assert "HYPOTENUSE MUST BE" in lines[0]


def test_special_45():
    m, lines, labels = run(TRI, "3", "1", "5", "0", "")
    assert lines == ["LEG=5", "HYP=LEG×√(2)=5×√(2)", " =7.071"]
    assert {"45", "90", "HYP=7.071"} <= set(labels)


def test_special_30_from_hypotenuse():
    m, lines, labels = run(TRI, "3", "2", "0", "0", "10", "")
    assert lines == ["SHORT LEG S=5", "LONG LEG=S√(3)=5×√(3)", " =8.66", "HYPOTENUSE=2S=10"]
    assert {"30", "60", "90"} <= set(labels)


def test_trig_find_sides():
    m, lines, labels = run(TRI, "4", "35", "0", "0", "10", "")
    assert " OPP=5.736" in lines and " ADJ=8.192" in lines and "OTHER ANGLE=90-A=55" in lines
    assert math.isclose(m.reals["O"], 10 * math.sin(math.radians(35)))


def test_trig_find_angle():
    m, lines, labels = run(TRI, "4", "0", "3", "4", "0", "")
    assert lines[0] == "TAN(A)=OPP/ADJ"
    assert "ANGLE A=36.87" in lines and " HYP=5" in lines


def test_similar_triangles():
    m, lines, labels = run(TRI, "5", "3", "4", "5", "6", "")
    assert lines[:3] == ["K=NEW A/OLD A=2", "NEW B=B×K=8", "NEW C=C×K=10"]
    assert {"3", "4", "5", "6", "8", "10"} <= set(labels)


def test_impossible_triangle_is_refused():
    m, lines, _ = run(TRI, "5", "1", "2", "5")
    assert "DON'T MAKE A" in lines[0]


# -- angles and lines ------------------------------------------------------------

def test_intersecting_lines():
    m, lines, labels = run(ANG, "2", "50", "")
    assert "ARE 180-50=130." in lines
    assert sorted(labels)[:4] == ["130", "130", "50", "50"]


def test_parallel_lines_label_the_right_corners():
    m, lines, labels = run(ANG, "3", "60", "")
    assert "60 AND 180-60=120." in lines
    # top-left and bottom-right of each crossing are 60, the other two 120
    labels = [t for t in labels if t.isdigit()]
    top, bottom = labels[0:4], labels[4:8]
    assert top == ["60", "120", "120", "60"] and bottom == ["60", "120", "120", "60"]


# -- area ----------------------------------------------------------------------

def test_triangle_area_heron():
    m, lines, _ = run(AREA, "1", "1", "5", "6", "7", "")
    assert lines[0] == "PERIMETER=A+B+C=18" and lines[-1] == " =14.697"


def test_triangle_area_base_height():
    m, lines, _ = run(AREA, "1", "2", "10", "4", "")
    assert lines == ["AREA=BASE×HEIGHT/2", " =20"]


def test_rectangle():
    m, lines, labels = run(AREA, "2", "8", "5", "")
    assert lines == ["AREA=L×W=40", "PERIMETER=2(L+W)=26", "DIAGONAL=√(L²+W²)=9.434"]
    assert "D=9.434" in labels


def test_trapezoid():
    m, lines, _ = run(AREA, "4", "10", "6", "4", "5", "5", "")
    assert " =32" in lines and " =26" in lines


def test_combined_shape():
    m, lines, _ = run(AREA, "5", "", "1", "4", "3", "", "5", "3", "1", "", "6")
    assert lines == ["COMBINED SHAPE: 2 PARTS", "TOTAL AREA=8.858"]   # 12 - π


# -- solids ----------------------------------------------------------------------

@pytest.mark.parametrize("item, inputs, expected", [
    ("1", ["4", "3", "2"], ["VOLUME=L×W×H=24", "SURFACE=2(LW+LH+WH)", " =52",
                            "SPACE DIAGONAL=5.385"]),
    ("2", ["3", "7"], ["VOLUME=πR²H=197.92", "SURFACE=2πR²+2πRH", " =188.496",
                       "CURVED PART ONLY=131.947"]),
    ("3", ["3", "4"], ["VOLUME=πR²H/3=37.699", "SLANT L=√(R²+H²)=5", "SURFACE=πR²+πRL=75.398"]),
    ("4", ["3"], ["VOLUME=4πR³/3=113.097", "SURFACE=4πR²=113.097"]),
    ("5", ["6", "4"], ["VOLUME=S²H/3=48", "SLANT=√(H²+(S/2)²)=5", "SURFACE=S²+2S×SLANT", " =96"]),
])
def test_solids(item, inputs, expected):
    m, lines, labels = run(SOLID, item, *inputs, "")
    assert lines == expected
    assert labels[0].startswith("VOLUME=")


# -- circles -------------------------------------------------------------------------

@pytest.mark.parametrize("inputs, radius", [
    (["5", "0", "0", "0"], "5"), (["0", "10", "0", "0"], "5"),
    (["0", "0", "31.4159", "0"], "5"), (["0", "0", "0", "78.5398"], "5"),
])
def test_circle_solver(inputs, radius):
    m, lines, _ = run(CIRC, "1", *inputs, "")
    assert lines[0] == f"RADIUS R={radius}"
    assert lines[3] == "AREA=πR²=78.54"


def test_arc_and_sector():
    m, lines, labels = run(CIRC, "2", "6", "120", "")
    assert lines == ["FRACTION=T/360=.333", "ARC=FRACTION×2πR=12.566",
                     "SECTOR=FRACTION×πR²=37.699", "CHORD=2R×SIN(T/2)=10.392"]


def test_circle_equation_both_ways():
    m, lines, _ = run(CIRC, "3", "1", "2", "-3", "5", "")
    assert lines == ["CENTER (2,⁻3)", "RADIUS 5", "STANDARD FORM:", "(X-2)²+(Y+3)²=25",
                     "GENERAL FORM:", "X²+Y²-4X+6Y-12=0"]
    m, lines, _ = run(CIRC, "3", "2", "-4", "6", "-12", "")
    assert lines[:2] == ["CENTER (2,⁻3)", "RADIUS 5"]


def test_not_a_circle():
    m, lines, _ = run(CIRC, "3", "2", "0", "0", "4")
    assert "ISN'T A CIRCLE" in lines[0]


def test_scale_factors():
    m, lines, _ = run(SCALE, "3", "")
    assert lines == ["SCALE FACTOR K=3", "LENGTHS ×K=×3", "AREA ×K²=×9", "VOLUME ×K³=×27"]


# -- the program as a whole ----------------------------------------------------------

def test_leaving_restores_the_calculator():
    m = load_program("GEOMETRY")
    m.window["Xmax"] = 7.5
    m.run()
    m.answer(BACK)
    assert m.wait.kind == "done"
    assert m.window["Xmax"] == 7.5 and m.radians and m.graph.axes
    assert m.lists == {}


def test_brainiac_runs_geometry_and_comes_back():
    b = load_program()
    b.run()
    b.answer("5")
    assert b.wait.title == "GEOMETRY" and not b.radians
    b.answer(BACK)
    assert b.wait.title == "BRAINIAC 0.3" and b.radians and b.leaks == 0
    b.answer("1")
    b.answer("5X=30")
    assert "X=6" in [ln.strip() for ln in b.screen.lines()]


def test_again_repeats_the_same_solver():
    m, _, _ = run(SOLID, "4", "3", "")
    m.answer("")
    m.answer("1")
    assert m.wait.kind == "input" and m.wait.prompt == "RADIUS="
