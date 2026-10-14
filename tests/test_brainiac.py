"""Tests for Brainiac, run on the simulated TI-84 Plus CE.

Every test runs the exact tokens that go into BRAINIAC.8xp, so a passing
test means the program on the calculator takes the same path.
"""

import math
import re

import pytest

from brainiac.build import (OUTPUT, BuildError, check_list_brackets, check_structure,
                            min_os, program_bytes, source_lines)
from brainiac.screen import view
from brainiac.tibasic import (Machine, TIError, fmt, glyphs, keyboard_to_tokens, load_program,
                              tokens_from_text)
from tivars.types import TIProgram

SOLVE, ZEROS, POLY, SYSTEM, REARRANGE, INVERSE, HELP, QUIT = "12345678"
ROOTS, QUAD, SIMPLIFY, FACTOR, DIVIDE = "12345"


# -- helpers -------------------------------------------------------------------

def run(*answers):
    """Start Brainiac, give it these answers, and read every results page.

    Returns the machine and the screen lines (all pages, minus ENTER prompts).
    """
    m = load_program()
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
        return m, lines


def answers_for(*answers):
    """Just the lines after the header (the answers themselves)."""
    m, lines = run(*answers)
    assert_clean(m)
    return lines[1:]


def values(lines, letter="X"):
    """The numbers in lines like X=3/5=.6 or X=⁻2 (DOUBLE)."""
    out = []
    for ln in lines:
        if ln.startswith(letter + "="):
            text = ln[2:].split(" (")[0].split("=")[-1].replace("⁻", "-").replace("ᴇ", "e")
            top, _, bottom = text.partition("/")
            out.append(float(top) / float(bottom or 1))
    return out


def assert_clean(m):
    """No block left open by a Goto: on the calculator that leaks memory."""
    assert m.leaks == 0
    assert m.blocks == []


# -- the build -----------------------------------------------------------------------

def test_shipped_8xp_matches_source():
    """dist/BRAINIAC.8xp must be rebuilt after every edit to src/."""
    shipped = TIProgram.open(str(OUTPUT))
    assert shipped.name == "BRAINIAC"
    assert shipped.data == program_bytes(), "run: python -m brainiac.build"


def test_program_size_and_os():
    assert len(program_bytes()) < 40_000            # the CE has ~150 KB of RAM
    assert min_os().version == "5.2.0"              # toString( needs OS 5.2


def test_build_refuses_a_goto_inside_a_block():
    with pytest.raises(BuildError, match="leaks memory"):
        check_structure([(1, "If X:Then"), (2, "Goto A0"), (3, "End"), (4, "Lbl A0")])
    check_structure([(1, "If X"), (2, "Goto A0"), (3, "Lbl A0")])


@pytest.mark.parametrize("line", [r"ʟBY(2\xACS/K+.1)→ʟBC", "seq(I,I,1,9)ʟZV(ʟZF=0)",
                                  "ʟDQ(abs(ʟDQ)>1)→ʟDQ"])
def test_build_refuses_a_list_bracket_meant_as_multiplication(line):
    with pytest.raises(BuildError, match="element lookup"):
        check_list_brackets([(1, line)])


def test_every_displayed_text_fits_the_screen():
    for line in source_lines():
        for lit in re.findall(r'(?:Disp |Menu\()(.*)', line):
            for text in re.findall(r'"([^"]*)"', lit):
                width = len(glyphs(tokens_from_text(f'"{text}"'))) - 2
                assert width <= 26, text


# -- solve for X ------------------------------------------------------------------------

@pytest.mark.parametrize("equation, expected", [
    ("5X=30", ["X=6"]),
    ("5X+20-3=20", ["X=3/5=.6"]),
    ("X^2=5X-6", ["FACTORED: (X-2)(X-3)", "X=2", "X=3"]),
    ("X^2+6X+9=0", ["FACTORED: (X+3)²", "X=⁻3 (DOUBLE)"]),
    ("X^2+5X+20=0", ["X=(⁻5+𝑖√(55))/2", " =⁻2.5+3.708099244𝑖",
                     "X=(⁻5-𝑖√(55))/2", " =⁻2.5-3.708099244𝑖"]),
    ("X^2-2=0", ["X=√(2)", " =1.414213562", "X=⁻√(2)", " =⁻1.414213562"]),
    ("1/X=4", ["X=1/4=.25"]),
    ("X^3=6X^2-11X+6", ["FACTORED: (X-1)(X-2)(X-3)", "X=1", "X=2", "X=3"]),
    ("2X+1=2X+1", ["EVERY X WORKS! BOTH SIDES", "ARE ALWAYS EQUAL."]),
    ("X+1=X+2", ["NO ANSWER: THE TWO SIDES", "ARE NEVER EQUAL."]),
    ("X^2-4", ["FACTORED: (X+2)(X-2)", "X=⁻2", "X=2"]),          # no = means =0
])
def test_solve_polynomials_exactly(equation, expected):
    assert answers_for(SOLVE, equation) == expected


@pytest.mark.parametrize("equation, expected", [
    ("√(X)=3", [9]),
    ("e^(X)=10", [math.log(10)]),
    ("abs(X-3)=2", [1, 5]),
    ("(2X+1)/(X-3)=5", [16 / 3]),
    ("e^(X^2)=5", [-math.sqrt(math.log(5)), math.sqrt(math.log(5))]),   # no overflow
])
def test_solve_everything_else_by_searching(equation, expected):
    lines = answers_for(SOLVE, equation)
    assert lines[0].startswith("(SEARCHED")
    assert values(lines) == pytest.approx(expected, abs=1e-8)


def test_solve_with_other_letters_rearranges_for_x():
    assert answers_for(SOLVE, "AX+B=C") == ["SO", "X=(C-B)/A"]


def test_solve_asks_which_letter_when_there_is_no_x():
    assert answers_for(SOLVE, "AB=C", "B") == ["SO", "B=C/A"]


@pytest.mark.parametrize("equation, verdict", [("2=3", "NEVER"), ("2=2", "ALWAYS")])
def test_solve_with_no_letters(equation, verdict):
    assert verdict in answers_for(SOLVE, equation)[0]


def test_solve_rejects_a_missing_side():
    m, lines = run(SOLVE, "2X=")
    assert "EQUATION NEEDS ONE =" in lines[0]
    m.answer("")
    assert m.wait.kind == "input"                 # straight back to typing


def test_blank_input_goes_back_to_the_menu():
    m, _ = run(SOLVE, "")
    assert m.wait.kind == "menu" and m.wait.title == "BRAINIAC 0.2"


# -- find zeros ----------------------------------------------------------------------------

def test_zeros_needs_no_range():
    m, lines = run(ZEROS, "X^3-4X")
    assert lines == ["ROOTS OF X^3-4X", "FACTORED: X(X+2)(X-2)", "X=⁻2", "X=0", "X=2"]


@pytest.mark.parametrize("f, expected", [
    ("sin(X)", [k * math.pi for k in range(-31, 32)]),        # 63 zeros in -99..99
    ("tan(X)", [k * math.pi for k in range(-31, 32)]),        # and no asymptotes
    ("ln(X)", [1]),                                            # no ERR:DOMAIN for X<0
    ("sqrt(X)-2", [4]),
])
def test_zeros_by_search(f, expected):
    lines = answers_for(ZEROS, f)
    assert values(lines) == pytest.approx(expected, abs=1e-8)


def test_zeros_marks_touches():
    lines = answers_for(ZEROS, "cos(X)+1")
    assert all(ln.endswith("(DOUBLE)") for ln in lines[1:])
    assert values(lines) == pytest.approx([-3 * math.pi, -math.pi, math.pi, 3 * math.pi],
                                          abs=1e-5)


def test_zeros_of_1_over_x():
    assert answers_for(ZEROS, "1/X") == ["NO ZEROS: IT IS NEVER 0."]


def test_search_a_range_is_optional():
    m, lines = run(ZEROS, "sin(X)", *[""] * 8, "2", "0", "7")
    assert lines == ["ZEROS OF sin(X)", "(SEARCHED 0 TO 7)",
                     "X=0", "X=3.141592654", "X=6.283185307"]


def test_fast_growers_stop_short_of_overflow():
    lines = answers_for(ZEROS, "e^(X^3)-2")
    assert lines[0].startswith("(SEARCHED ⁻99 TO 5.")
    assert values(lines) == pytest.approx([math.log(2) ** (1 / 3)])


# -- polynomials ------------------------------------------------------------------------------

@pytest.mark.parametrize("p, expected", [
    ("X^4-1", ["FACTORED: (X+1)(X-1)(X²+1)", "X=⁻1", "X=1", "X=𝑖", "X=⁻𝑖"]),
    ("2X^2+3X-5", ["FACTORED: (2X+5)(X-1)", "X=⁻5/2=⁻2.5", "X=1"]),
    ("X^5+X+1", ["FACTORED: (X²+X+1)(X³-X²+1", ")",
                 "X=(⁻1+𝑖√(3))/2", " =⁻.5+.8660254038𝑖", "X=(⁻1-𝑖√(3))/2",
                 " =⁻.5-.8660254038𝑖", "X=⁻.7548776662",
                 "X=.8774388331-.7448617666𝑖", "X=.8774388331+.7448617666𝑖"]),
])
def test_polynomial_roots(p, expected):
    assert answers_for(POLY, ROOTS, p) == expected


def test_quadratic_steps():
    lines = answers_for(POLY, QUAD, "2X^2+3X-5")
    for step in ["A=2, B=3, C=⁻5", " =(3)²-4(2)(⁻5)", " =9+40", " =49",
                 "D>0: TWO REAL ROOTS.", "X=1", "X=⁻5/2", "(2X+5)(X-1)",
                 "VERTEX: (⁻3/4,⁻49/8)", " 2(X+3/4)²-49/8"]:
        assert step in lines, step


def test_quadratic_steps_clear_fractions_and_handle_complex_roots():
    lines = answers_for(POLY, QUAD, "X^2/2+X+3")
    assert "TIMES 2 FOR WHOLE NUMBERS:" in lines
    assert "D<0: TWO COMPLEX ROOTS." in lines
    assert "X=⁻1+𝑖√(5)" in lines and "NONE WITH REAL NUMBERS" in lines
    assert " (1/2)(X+1)²+5/2" in lines


@pytest.mark.parametrize("expr, result", [
    ("3X(X-2)+(X+1)^2", "=4X²-4X+1"),
    ("2A+3B-A", "=A+3B"),
    ("(X^3-1)/(X-1)", "=X²+X+1"),
    ("(X+1)^3", "=X³+3X²+3X+1"),
    ("1/X+1/X^2", "=(X+1)/X²"),
    ("(A+B)^2-(A-B)^2", "=4AB"),
])
def test_simplify(expr, result):
    assert answers_for(POLY, SIMPLIFY, expr) == [result]


@pytest.mark.parametrize("expr, result", [
    ("X^2-9", "=(X+3)(X-3)"),
    ("6X^2+X-2", "=(3X+2)(2X-1)"),
    ("4X^2-12X+9", "=(2X-3)²"),
    ("X^5-X", "=X(X+1)(X-1)(X²+1)"),
    ("X^3-8", "=(X-2)(X²+2X+4)"),
    ("X^4+5X^2+6", "=(X²+2)(X²+3)"),
    ("X^2/2-2", "=(X+2)(X-2)/2"),
    ("6AB+9A", "=3A(2B+3)"),
    ("12X^2Y+18XY^2", "=6XY(2X+3Y)"),
    ("2piR^2+2piRH", "=2πR(H+R)"),
])
def test_factor(expr, result):
    assert answers_for(POLY, FACTOR, expr)[0] == result


def test_factor_says_when_it_cannot():
    lines = answers_for(POLY, FACTOR, "X^2+1")
    assert lines[0] == "=X²+1" and "DOES NOT FACTOR" in lines[1]


@pytest.mark.parametrize("top, bottom, quotient, remainder", [
    ("X^3-1", "X-1", "QUOTIENT: X²+X+1", "REMAINDER: 0"),
    ("X^3+2X+5", "X^2+1", "QUOTIENT: X", "REMAINDER: X+5"),
])
def test_divide(top, bottom, quotient, remainder):
    lines = answers_for(POLY, DIVIDE, top, bottom)
    assert lines[:2] == [quotient, remainder]


# -- systems ------------------------------------------------------------------------------------

@pytest.mark.parametrize("equations, expected", [
    (["X+Y=10", "X-Y=2"], ["X=6", "Y=4"]),
    (["2X+3Y-Z=1", "X-Y+2Z=3", "3X+Y+Z=6"], ["X=3", "Y=⁻2", "Z=⁻1"]),
    (["X/2+Y/3=1", "X-Y=0"], ["X=6/5", "Y=6/5"]),
    (["A+B=3", "A-B=1", "2A+B=5"], ["A=2", "B=1"]),               # one too many: fine
    (["X+Y=1", "X+Y=2"], ["NO SOLUTION: THE EQUATIONS", "CONTRADICT EACH OTHER."]),
    (["X+Y+Z=6", "X-Z=0"], ["INFINITELY MANY ANSWERS:", "X-Z=0", "Y+2Z=6",
                            "Z CAN BE ANY NUMBER."]),
])
def test_systems(equations, expected):
    m, lines = run(SYSTEM, *equations, "")
    assert_clean(m)
    assert lines == expected


def test_systems_reject_nonlinear():
    m, lines = run(SYSTEM, "X^2+Y=3")
    assert "LINEAR" in lines[1]


# -- rearranging -------------------------------------------------------------------------------

@pytest.mark.parametrize("formula, letter, expected", [
    ("A=BH/2", "H", ["H=2A/B"]),
    ("PV=NRT", "T", ["T=V/(NR)"]),
    ("Y=MX+B", "X", ["X=(Y-B)/M"]),
    ("A=P+PRT", "P", ["P=A/(RT+1)"]),
    ("1/F=1/A+1/B", "F", ["F=AB/(A+B)"]),
    ("R=V/I", "I", ["I=V/R"]),
    ("P=2L+2W", "L", ["L=(P-2W)/2"]),
    ("A=(B+C)H/2", "B", ["B=(2A-CH)/H"]),
    ("F=9C/5+32", "C", ["C=(5F-160)/9"]),
    ("V=piR²H", "H", ["H=V/(πR²)"]),
    ("V=piR²H", "R", ["R=√(V/(πH))", "OR R=⁻√(V/(πH))"]),
    ("T=2pi√(L/G)", "L", ["L=GT²/(4π²)"]),
    ("T=2pi√(L/G)", "T", ["T=2π√(L/G)", "OR T=⁻2π√(L/G)"]),
    ("C=√(A²+B²)", "A", ["A=√(C²-B²)", "OR A=⁻√(C²-B²)"]),
    ("V=4piR³/3", "R", ["R=³√(3V/(4π))"]),
    ("S=4piR²", "R", ["R=√(S/π)/2", "OR R=⁻√(S/π)/2"]),
    ("X²=4Y²", "X", ["X=2Y", "OR X=⁻2Y"]),
])
def test_rearrange(formula, letter, expected):
    assert answers_for(REARRANGE, formula, letter) == ["SO"] + expected


@pytest.mark.parametrize("formula, letter, message", [
    ("A=P(1+R)^T", "P", "CAN'T REARRANGE"),          # letters in powers: not yet
    ("Y=AX²+BX+C", "X", "TWO DIFFERENT POWERS"),
    ("A=BH/2", "Q", "PICK ONE LETTER"),
])
def test_rearrange_explains_what_it_cannot_do(formula, letter, message):
    m, lines = run(REARRANGE, formula, letter)
    assert message in " ".join(lines)


@pytest.mark.parametrize("f, expected", [
    ("2X+3", ["F⁻¹(X)=(X-3)/2"]),
    ("(X+1)/(X-2)", ["F⁻¹(X)=(2X+1)/(X-1)"]),
    ("X^3", ["F⁻¹(X)=³√(X)"]),
    ("sqrt(X-2)", ["F⁻¹(X)=X²+2"]),
    ("1/X", ["F⁻¹(X)=1/X"]),
])
def test_inverse(f, expected):
    assert answers_for(INVERSE, f)[1:] == expected


def test_inverse_of_x_squared_gives_both_branches():
    lines = answers_for(INVERSE, "X^2")
    assert lines[1:3] == ["F⁻¹(X)=√(X)", "OR F⁻¹(X)=⁻√(X)"]


def test_no_inverse_when_not_one_to_one():
    m, lines = run(INVERSE, "X^2+2X")
    assert "NO INVERSE" in lines[0]


# -- the program as a whole -----------------------------------------------------------------------

def test_many_rounds_do_not_leak():
    """Go round the menus like a student would; nothing may stay open."""
    m = load_program()
    m.run()
    for mode, inputs in [(SOLVE, ["X^2=9"]), (SOLVE, ["sin(X)=2"]), (REARRANGE, ["A=BH", "B"]),
                         (INVERSE, ["2X"]), (SYSTEM, ["X+Y=2", "X-Y=0", ""])]:
        m.answer(mode)
        for text in inputs:
            m.answer(text)
        while m.wait.kind == "pause":
            m.answer("")
        assert m.wait.kind == "menu" and m.wait.title == "WHAT NEXT?", m.wait
        m.answer("3" if len(m.wait.options) == 4 else "2")      # main menu
        assert_clean(m)
        assert m.lists["BS"] == []                              # subroutine stack empty
    m.answer(HELP)
    m.answer("")
    m.answer("")
    assert m.wait.title == "BRAINIAC 0.2"
    assert_clean(m)


def test_again_repeats_the_same_mode():
    m, _ = run(INVERSE, "2X+3")
    m.answer("")
    m.answer("1")                          # AGAIN
    assert m.wait.kind == "input" and m.wait.prompt == "F(X)="


def test_quit_tidies_up():
    m, _ = run(SOLVE, "X^2=4", "", "3")
    assert m.wait.kind == "done"
    assert m.lists == {} and m.mats == {}
    assert m.complex_mode is False


# -- the simulator agrees with the calculator ---------------------------------------------------

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
    ("3*.1", 0.3),          # 14 decimal digits, like the calculator
])
def test_arithmetic_matches_ti(text, value):
    assert run_expr(text) == pytest.approx(value)


@pytest.mark.parametrize("text, error", [
    ("√(⁻4)", "NONREAL ANS"),
    ("1/0", "DIVIDE BY 0"),
    ("ᴇ99*100", "OVERFLOW"),
    ("ln(0)", "DOMAIN"),
    ("gcd(⁻4,6)", "DOMAIN"),
])
def test_errors_match_ti(text, error):
    with pytest.raises(TIError, match=error):
        run_expr(text)


def test_list_bracket_is_a_lookup_not_a_multiplication():
    """The bug class the build lints for: ʟL(2) is element 2 of L."""
    m = Machine.from_text("{5,6,7}→ʟL\nʟL(2)→A")
    m.run()
    assert m.reals["A"] == 6


def test_and_checks_both_sides():
    """TI's "and" doesn't stop early, so a guard on the left doesn't help."""
    m = Machine.from_text('"AB"→Str1\n3→K\nIf K<length(Str1) and sub(Str1,K+1,1)="B":1→A')
    assert m.run().kind == "error"


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
    (-1j, "⁻𝑖"),
])
def test_number_display(value, shown):
    assert fmt(value) == shown


def test_typed_text_becomes_calculator_tokens():
    assert glyphs(keyboard_to_tokens("x^2-4=-5x")) == "X^2-4=⁻5X"
    assert glyphs(keyboard_to_tokens("sqrt(x)+e^(x)+pi")) == "√(X)+𝑒^(X)+π"
    assert [t.key for t in keyboard_to_tokens("PV=NRT")][:2] == ["P", "V"]   # not finance PV
    assert [t.key for t in keyboard_to_tokens("R²")] == ["R", "^^2"]         # not statistic R²
    assert [t.key for t in tokens_from_text("X²")] == ["X", "^^2"]
