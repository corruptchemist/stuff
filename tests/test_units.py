"""Tests for UNITS (unit conversion), run on the simulated TI-84 Plus CE."""

import pytest

from brainiac.build import TEST_NAMES, split_args, min_os, output_path, program_bytes, source_lines
from brainiac.screen import view
from brainiac.tibasic import Machine, TIError, load_program
from tivars.types import TIProgram

LENGTH, MASS, VOLUME, AREA, TIME, RATE, MORE = "1234567"


def run(*answers, program="UNITS"):
    m = load_program(program)
    m.run()
    for a in answers:
        assert m.wait.kind in ("menu", "input", "pause"), (answers, m.wait, m.statement_text())
        m.answer(a)
    assert m.wait.kind != "error", f"{m.wait.message} at: {m.statement_text()}"
    lines = []
    while True:
        lines += [ln.rstrip() for ln in view(m) if ln.strip() and "ENTER:" not in ln]
        if m.wait.kind == "pause" and "MORE" in m.screen.lines()[9]:
            m.answer("")
            continue
        break
    assert m.leaks == 0 and m.blocks == []
    return m, lines


CANCEL = ["EACH FRACTION EQUALS 1;", "MATCHING UNITS ON TOP AND", "BOTTOM CANCEL."]


def test_shipped_8xp_matches_source():
    """The test build is what gets sent next; the stable build stays as it was."""
    shipped = TIProgram.open(str(output_path("UNITS", test=True)))
    assert shipped.name == TEST_NAMES["UNITS"]
    assert shipped.data == program_bytes("UNITS", test=True), "run: python -m brainiac.build"
    assert TIProgram.open(str(output_path("UNITS"))).name == "UNITS"
    assert min_os("UNITS").version == "5.2.0"


def test_menus_fit_the_calculator():
    for line in source_lines("UNITS"):
        if line.startswith("Menu("):
            args = split_args(line[5:].rstrip(")"))
            assert len(args) <= 15, line                  # a title and 7 options
            assert all(len(a.strip('"')) <= 24 for a in args[1::2]), line


def test_the_units_are_listed_by_number():
    m, lines = run(LENGTH)
    assert lines[:5] == ["LENGTH UNITS:", "1:MM         5:IN", "2:CM         6:FT",
                         "3:M          7:YD", "4:KM         8:MI"]
    assert m.wait.prompt == "FROM (NUMBER):"


@pytest.mark.parametrize("answers, expected", [
    ((LENGTH, "8", "6", "5"), ["5 MI", "×5280 FT/1 MI", "=26400 FT"]),
    ((LENGTH, "6", "8", "26400"), ["26400 FT", "×1 MI/5280 FT", "=5 MI"]),
    # every factor one a student knows, crossing once at 1 IN = 2.54 CM
    ((LENGTH, "8", "4", "5"), ["5 MI", "×5280 FT/1 MI", "×12 IN/1 FT", "×2.54 CM/1 IN",
                               "×1 M/100 CM", "×1 KM/1000 M", "=8.04672 KM"]),
    ((LENGTH, "3", "6", "10"), ["10 M", "×100 CM/1 M", "×1 IN/2.54 CM", "×1 FT/12 IN",
                                "=32.80839895 FT"]),
    ((LENGTH, "4", "2", "3"), ["3 KM", "×1000 M/1 KM", "×100 CM/1 M", "=300000 CM"]),
    ((LENGTH, "8", "7", "1"), ["1 MI", "×5280 FT/1 MI", "×1 YD/3 FT", "=1760 YD"]),
    ((MASS, "3", "5", "70"), ["70 KG", "×1000 G/1 KG", "×1 LB/453.59237 G", "=154.3235835 LB"]),
    ((MASS, "4", "5", "40"), ["40 OZ", "×1 LB/16 OZ", "=2.5 LB"]),
    ((VOLUME, "11", "2", "2"), ["2 GAL", "×3.785411784 L/1 GAL", "=7.570823568 L"]),
    ((VOLUME, "8", "7", "3"), ["3 CUP", "×8 FL OZ/1 CUP", "=24 FL OZ"]),
    ((VOLUME, "3", "1", "5"), ["5 CM³", "×1 ML/1 CM³", "=5 ML"]),
    ((VOLUME, "11", "5", "1"), ["1 GAL", "×4 QT/1 GAL", "×2 PT/1 QT", "×2 CUP/1 PT",
                                "×8 FL OZ/1 CUP", "×2 TBSP/1 FL OZ", "×3 TSP/1 TBSP",
                                "=768 TSP"]),
    ((AREA, "7", "6", "2"), ["2 FT²", "×144 IN²/1 FT²", "=288 IN²"]),
    ((AREA, "9", "3", "1"), ["1 ACRE", "×43560 FT²/1 ACRE", "×144 IN²/1 FT²",
                             "×6.4516 CM²/1 IN²", "×1 M²/10000 CM²", "=4046.856422 M²"]),
    ((AREA, "5", "4", "1"), ["1 KM²", "×100 HA/1 KM²", "=100 HA"]),
    ((TIME, "4", "1", "2"), ["2 DAY", "×24 HR/1 DAY", "×60 MIN/1 HR", "×60 SEC/1 MIN",
                             "=172800 SEC"]),
    ((TIME, "2", "3", "90"), ["90 MIN", "×1 HR/60 MIN", "=1.5 HR"]),
    # no rounded fraction like 52.14285714 WEEK/1 YEAR: through DAY instead
    ((TIME, "5", "6", "1"), ["1 WEEK", "×7 DAY/1 WEEK", "×1 YEAR/365 DAY", "=.01917808219 YEAR"]),
])
def test_dimensional_analysis(answers, expected):
    m, lines = run(*answers)
    assert lines == expected + CANCEL


def test_same_unit_has_nothing_to_cancel():
    m, lines = run(LENGTH, "3", "3", "4")
    assert lines == ["4 M", "=4 M"]


@pytest.mark.parametrize("answers, expected", [
    # 60 MI/HR = 88 FT/SEC: the classic
    ((RATE, "1", "8", "6", "1", "3", "1", "60"),
     ["60 MI/HR", "×5280 FT/1 MI", "×1 HR/60 MIN", "×1 MIN/60 SEC", "=88 FT/SEC"]),
    ((RATE, "1", "3", "4", "1", "1", "3", "10"),
     ["10 M/SEC", "×1 KM/1000 M", "×60 SEC/1 MIN", "×60 MIN/1 HR", "=36 KM/HR"]),
    ((RATE, "2", "2", "3", "4", "1", "2", "1"),                      # density
     ["1 G/ML", "×1 KG/1000 G", "×1000 ML/1 L", "=1 KG/L"]),
    ((RATE, "1", "8", "4", "4", "11", "2", "30"),                    # fuel economy
     ["30 MI/GAL", "×5280 FT/1 MI", "×12 IN/1 FT", "×2.54 CM/1 IN", "×1 M/100 CM",
      "×1 KM/1000 M", "×1 GAL/3.785411784 L", "=12.75431122 KM/L"]),
])
def test_rates_convert_top_and_bottom(answers, expected):
    m, lines = run(*answers)
    assert lines == expected + CANCEL


@pytest.mark.parametrize("answers, expected", [
    (("1", "2", "98.6"), ["98.6 °F", "C=(F-32)×5/9", "C=(98.6-32)×5/9", " =37 °C"]),
    (("2", "1", "100"), ["100 °C", "F=C×9/5+32", "F=100×9/5+32", " =212 °F"]),
    (("2", "3", "25"), ["25 °C", "K=C+273.15", "K=25+273.15", " =298.15 K"]),
    (("3", "1", "0"), ["0 K", "C=K-273.15", "C=0-273.15", " =⁻273.15 °C",
                       "F=C×9/5+32", "F=⁻273.15×9/5+32", " =⁻459.67 °F"]),
    (("1", "1", "50"), ["50 °F", " =50 °F (THE SAME UNIT)"]),
    (("1", "3", "⁻500"), ["⁻500 °F", "C=(F-32)×5/9", "C=(⁻500-32)×5/9", " =⁻295.5555556 °C",
                          "K=C+273.15", "K=⁻295.5555556+273.15", " =⁻22.40555556 K",
                          "BELOW ABSOLUTE ZERO (0 K):", "NOTHING IS THAT COLD."]),
])
def test_temperature(answers, expected):
    m, lines = run(MORE, "1", *answers)
    assert lines == expected


def test_bad_unit_numbers_are_asked_again():
    m, _ = run(LENGTH, "9")
    assert m.wait.prompt == "FROM (NUMBER):"
    assert "PICK 1 TO 8" in [ln.strip() for ln in m.screen.lines()]
    m.answer("2.5")
    assert m.wait.prompt == "FROM (NUMBER):"
    m.answer("1")
    m.answer("0")
    assert m.wait.prompt == "TO (NUMBER):"


def test_again_and_quit():
    m, _ = run(TIME, "2", "3", "90")
    m.answer("")
    assert m.wait.title == "WHAT NEXT?"
    m.answer("1")                                   # AGAIN: same category
    assert m.wait.kind == "input" and "TIME UNITS:" in [ln.strip() for ln in m.screen.lines()]
    m.answer("1")
    m.answer("1")
    m.answer("5")
    m.answer("")
    m.answer("3")                                   # QUIT
    assert m.wait.kind == "done" and m.lists == {}


def test_brainiac_runs_units_and_comes_back():
    b = load_program()
    b.radians = False
    b.run()
    b.answer("5")
    assert b.wait.title == "GEOMETRY & UNITS"
    b.answer("2")
    assert b.wait.title == "UNIT CONVERSION"
    b.answer(MORE)
    b.answer("4")                                   # quit units
    assert b.wait.title == "BRAINIAC 0.5" and b.leaks == 0
    b.answer("1")
    b.answer("5X=30")
    assert "X=6" in [ln.strip() for ln in b.screen.lines()]


def test_text_rows_stop_at_150():
    """Row 152 was ERR:DOMAIN on the calculator (the 0.4 number line)."""
    m = Machine.from_text('Text(152,2,"HI")')
    assert m.run().kind == "error" and m.wait.message.startswith("ERR:DOMAIN")
    m = Machine.from_text('Text(150,2,"HI")')
    assert m.run().kind == "done"


@pytest.mark.parametrize("answers", [
    (LENGTH, "8", "1", "1ᴇ70"),                      # could overflow: asked again
    (RATE, "4", "10", "1", "5", "1", "10", "2ᴇ75"),
    (MORE, "1", "1", "2", "2ᴇ99"),
])
def test_huge_amounts_are_asked_again(answers):
    m, _ = run(*answers)
    assert m.wait.kind == "input" and m.wait.prompt == "HOW MANY? "


def test_a_complex_unit_number_is_asked_again():
    """From Brainiac the calculator is in a+bi mode, so 𝑖 can be typed."""
    b = load_program()
    b.run()
    for a in ("5", "2", LENGTH, "𝑖"):
        b.answer(a)
    assert b.wait.kind == "input" and b.wait.prompt == "FROM (NUMBER):"


def test_every_conversion_path_is_familiar_factors():
    """Every fraction shown is a whole number of one unit in the other."""
    for cat, n in [(LENGTH, 8), (MASS, 6), (VOLUME, 11), (AREA, 10), (TIME, 6)]:
        for f in range(1, n + 1):
            for t in range(1, n + 1):
                m, lines = run(cat, str(f), str(t), "1")
                for ln in lines:
                    if ln.startswith("×"):
                        top, bottom = ln[1:].split("/")
                        k = top.split(" ")[0] if not top.startswith("1 ") else bottom.split(" ")[0]
                        assert float(k) == round(float(k)) or k in ("2.54", "453.59237",
                                                                    "3.785411784", "6.4516"), ln
