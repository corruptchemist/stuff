"""Tests for Brainiac, run on the simulated TI-84 Plus CE.

Every test runs the exact tokens that go into BRAINIAC.8xp, so a passing
test means the program on the calculator takes the same path.
"""

import math

import pytest

from brainiac.build import OUTPUT, program_bytes
from brainiac.tibasic import Machine, TIError, fmt, load_program, tokens_from_text
from tivars.types import TIProgram


# -- helpers -------------------------------------------------------------------

def solve(equation):
    m = load_program()
    m.run()
    m.answer("1")
    m.answer(equation)
    return m


def zeros(f, start="", end=""):
    m = load_program()
    m.run()
    m.answer("2")
    m.answer(f)
    m.answer(start)
    m.answer(end)
    return m


def answers(m):
    """Every X= line on every results page, as (number, touches?) pairs."""
    assert m.wait.kind == "pause", m.wait
    found = []
    while True:
        lines = m.screen.lines()
        for line in lines[3:9]:
            if line.startswith("X="):
                text = line[2:].split()[0]
                found.append((parse_ti(text), "TOUCH" in line))
        if "MORE" not in lines[9]:
            return found
        m.answer("")


def parse_ti(text):
    return float(text.replace("⁻", "-").replace("ᴇ", "e"))


def xs(m):
    return [x for x, _ in answers(m)]


def assert_clean(m):
    """No block left open by a Goto: on the calculator that leaks memory."""
    assert m.leaks == 0
    assert m.blocks == []


# -- the .8xp itself -------------------------------------------------------------

def test_shipped_8xp_matches_source():
    """dist/BRAINIAC.8xp must be rebuilt after every edit to src/."""
    shipped = TIProgram.open(str(OUTPUT))
    assert shipped.name == "BRAINIAC"
    assert shipped.data == program_bytes(), "run: python -m brainiac.build"


def test_program_fits_comfortably_in_ram():
    assert len(program_bytes()) < 8000


# -- solve for X -----------------------------------------------------------------

@pytest.mark.parametrize("equation, expected", [
    ("2X+3=7", [2]),
    ("X^2=5X-6", [2, 3]),
    ("X²=5X-6", [2, 3]),
    ("1/X=4", [0.25]),
    ("sqrt(X)=3", [9]),
    ("abs(X-3)=2", [1, 5]),
    ("e^(X)=10", [math.log(10)]),
    ("X^3-2X=5", [2.0945514815423265]),
    ("X^2-X=110", [-10, 11]),
    ("X=5", [5]),
    ("X^2-4", [-2, 2]),            # no "=" means "= 0"
    ("-X=3", [-3]),
])
def test_solve(equation, expected):
    m = solve(equation)
    assert xs(m) == pytest.approx(expected, abs=1e-9)
    assert_clean(m)


def test_solve_also_searches_far_out():
    m = solve("X^2=400")
    assert xs(m) == [-20, 20]
    assert m.screen.lines()[2].split() == ["X", "FROM", "⁻99", "TO", "99"]


def test_solve_finds_near_and_far_answers_together():
    assert xs(solve("(X-1)(X-50)=0")) == [1, 50]


def test_fast_growing_functions_do_not_overflow():
    """e^(X²) passes 1E100 near X=15; the sweep must stop before it gets there."""
    m = solve("e^(X^2)=5")
    r = math.sqrt(math.log(5))
    assert xs(m) == pytest.approx([-r, r], abs=1e-9)
    assert xs(zeros("e^(X^2)-5", "-100", "100")) == pytest.approx([-r, r], abs=1e-9)


def test_solve_reports_no_answer():
    m = solve("X^2+1=0")
    assert answers(m) == []
    assert "NO ANSWERS" in m.screen.text()


def test_solve_spots_an_identity():
    m = solve("X+1=1+X")
    assert "EVERY X WORKS" in m.screen.text()


def test_solve_double_root_is_a_touch():
    m = solve("(X-2)^2=0")
    assert answers(m) == [(2, True)]


def test_solve_rejects_a_missing_side():
    m = solve("2X=")
    assert m.wait.kind == "pause"
    assert "MISSING" in m.screen.text()
    m.answer("")
    assert m.wait.kind == "input"          # straight back to typing


def test_blank_equation_goes_back_to_the_menu():
    m = solve("")
    assert m.wait.kind == "menu" and m.wait.title == "BRAINIAC V0.1"


# -- find all zeros ----------------------------------------------------------------

@pytest.mark.parametrize("f, start, end, expected", [
    ("X^3-4X", "", "", [-2, 0, 2]),
    ("X^4-5X^2+4", "", "", [-2, -1, 1, 2]),
    ("sin(X)", "", "", [k * math.pi for k in range(-3, 4)]),
    ("sqrt(X)-2", "", "", [4]),                       # complex for X<0: skipped
    ("ln(X)", "", "", [1]),
    ("X^2-.0001", "", "", [-0.01, 0.01]),             # two roots between samples
    ("tan(X)", "0", "6", [0, math.pi]),               # asymptote at pi/2 is not a zero
    ("X^3-3X^2+3X-1", "", "", [1]),                   # flat triple root
    ("sin(1/X)", ".05", "1", [1 / (k * math.pi) for k in range(6, 0, -1)]),
    ("X^2-2", "5", "-5", [-math.sqrt(2), math.sqrt(2)]),  # range typed backwards
    ("X-1000", "990", "1010", [1000]),
])
def test_zeros(f, start, end, expected):
    m = zeros(f, start, end)
    assert xs(m) == pytest.approx(expected, abs=1e-8)
    assert_clean(m)


def test_zeros_right_on_the_ends_of_the_range_count():
    m = zeros("sin(X)", "-2pi", "3pi")
    assert xs(m) == pytest.approx([k * math.pi for k in range(-2, 4)], abs=1e-9)
    assert m.screen.lines()[2].split() == ["X", "FROM", "⁻6.283", "TO", "9.425"]


def test_zeros_marks_touches():
    m = zeros("(X-2)^2(X+1)")
    assert answers(m) == [(-1, False), (2, True)]


def test_zeros_touch_on_a_curve():
    m = zeros("cos(X)+1")
    found = answers(m)
    assert all(t for _, t in found)
    assert [x for x, _ in found] == pytest.approx([-3 * math.pi, -math.pi, math.pi, 3 * math.pi],
                                                  abs=1e-5)


def test_zeros_skip_vertical_asymptotes():
    m = zeros("1/X")
    assert answers(m) == []


def test_zeros_many_answers_page_through():
    m = zeros("sin(X)", "-20", "20")
    assert xs(m) == pytest.approx([k * math.pi for k in range(-6, 7)], rel=1e-9, abs=1e-9)


def test_zeros_empty_range_is_explained():
    m = zeros("X", "3", "3")
    assert "RANGE IS EMPTY" in m.screen.text()


# -- the program as a whole --------------------------------------------------------

def test_many_rounds_do_not_leak():
    """Go round the loop like a student would; the block stack must stay empty."""
    m = load_program()
    m.run()
    for eq in ["2X=8", "X^2=9", "X+1=X+1", "X^2=-1", "2X="]:
        m.answer("1")
        m.answer(eq)
        while m.wait.kind == "pause":
            m.answer("")
        if m.wait.kind == "input":        # the "missing a side" detour
            m.answer("X=1")
            m.answer("")
        assert m.wait.kind == "menu" and m.wait.title == "WHAT NEXT?"
        m.answer("2")                      # main menu
        assert_clean(m)
    m.answer("3")                          # help
    m.answer("")
    m.answer("")
    assert m.wait.title == "BRAINIAC V0.1"
    assert_clean(m)


def test_again_repeats_the_same_mode():
    m = zeros("X-1")
    m.answer("")
    m.answer("1")                          # AGAIN
    assert m.wait.kind == "input" and m.wait.prompt == "F(X)="


def test_quit_tidies_up():
    m = solve("2X=4")
    m.answer("")
    m.answer("3")
    assert m.wait.kind == "done"
    assert not any(name.startswith("Z") for name in m.lists)
    assert m.yvars["0"] == ()
    assert m.complex_mode is False


# -- the simulator agrees with the calculator ---------------------------------------

def run_expr(text, complex_mode=False):
    m = Machine.from_text(text + "→A" if "→" not in text else text)
    m.complex_mode = complex_mode
    w = m.run()
    if w.kind == "error":
        raise TIError(w.message)
    return m.reals["A"]


@pytest.mark.parametrize("text, value", [
    ("⁻2²", -4),            # negation binds looser than powers
    ("2^3^2", 64),          # TI's ^ goes left to right
    ("2(3+4)", 14),         # implied multiplication
    ("6/2(3)", 9),          # ...at the same level as / on the 84 Plus CE
    ("2^⁻1", 0.5),
    ("ᴇ3", 1000),
    ("3ᴇ⁻2", 0.03),
    ("(1+2", 3),            # closing parens are optional at the end of a line
    ("not(0) and 1 or 0", 1),
    ("³√(⁻8)", -2),
    ("(⁻8)^(1/3)", -2),     # Real mode gives the real cube root
    ("iPart(⁻2.5)", -2),
    ("int(⁻2.5)", -3),
])
def test_arithmetic_matches_ti(text, value):
    assert run_expr(text) == pytest.approx(value)


@pytest.mark.parametrize("text, error", [
    ("√(⁻4)", "NONREAL ANS"),
    ("1/0", "DIVIDE BY 0"),
    ("ᴇ99*100", "OVERFLOW"),
    ("ln(0)", "DOMAIN"),
])
def test_errors_match_ti(text, error):
    with pytest.raises(TIError, match=error):
        run_expr(text)


def test_complex_mode_gives_complex_instead_of_stopping():
    m = Machine.from_text("a+bi\n√(⁻4)→A\nimag(A)→B")
    m.run()
    assert m.reals["B"] == 2


def test_if_without_then_guards_one_statement():
    m = Machine.from_text("0→A\nIf 0\n1→A\n2→B")
    m.run()
    assert m.reals["A"] == 0 and m.reals["B"] == 2


def test_goto_out_of_a_block_counts_as_a_leak():
    m = Machine.from_text("Lbl A\nIf 1:Then\nGoto B\nEnd\nLbl B")
    m.run()
    assert m.leaks == 1


@pytest.mark.parametrize("value, shown", [
    (2.0, "2"), (-3.0, "⁻3"), (0.25, ".25"), (math.pi, "3.141592654"),
    (1e-4, "1ᴇ⁻4"), (12345678901.0, "1.23456789ᴇ10"), (2j, "2𝑖"), (1 - 1j, "1-𝑖"),
])
def test_number_display(value, shown):
    assert fmt(value) == shown


def test_typed_text_becomes_calculator_tokens():
    from brainiac.tibasic import keyboard_to_tokens, glyphs
    assert glyphs(keyboard_to_tokens("x^2-4=-5x")) == "X^2-4=⁻5X"
    assert glyphs(keyboard_to_tokens("sqrt(x)+e^(x)+pi")) == "√(X)+𝑒^(X)+π"
    assert [t.key for t in tokens_from_text("X²")] == ["X", "^^2"]
