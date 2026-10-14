"""Tests for Brainiac, run on the simulated TI-84 Plus CE.

Every test runs the exact tokens that go into BRAINIAC.8xp, so a passing
test means the program on the calculator takes the same path.
"""

import math
import re

import pytest

from brainiac.build import (OUTPUT, TEST_NAMES, BuildError, output_path, split_args, check_list_brackets, check_structure,
                            min_os, program_bytes, source_lines)
from brainiac.screen import view
from brainiac.tibasic import (GH, GW, Machine, TIError, fmt, glyphs, keyboard_to_tokens,
                              load_program, tokens_from_text)
from tivars.types import TIProgram

SOLVE, POLY, SYSTEM, FORMULAS, GEOMETRY, HELP, QUIT = "1234567"
ROOTS, QUAD, SIMPLIFY, FACTOR, DIVIDE = "12345"
ZEROS = (POLY, "6")
REARRANGE, INVERSE = (FORMULAS, "1"), (FORMULAS, "2")


# -- helpers -------------------------------------------------------------------

def run(*answers):
    """Start Brainiac, give it these answers, and read every results page.

    Returns the machine and the screen lines (all pages, minus ENTER prompts).
    """
    m = load_program()
    m.run()
    answers = [x for a in answers for x in (a if isinstance(a, tuple) else (a,))]
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
    """dist/BRAINUNS.8xp (the test build) must be rebuilt after every edit to
    src/. dist/BRAINIAC.8xp is the stable build the user keeps as a fallback."""
    shipped = TIProgram.open(str(output_path("BRAINIAC", test=True)))
    assert shipped.name == "BRAINUNS"
    assert shipped.data == program_bytes("BRAINIAC", test=True), "run: python -m brainiac.build"
    assert TIProgram.open(str(OUTPUT)).name == "BRAINIAC"


def test_test_build_only_renames():
    """The test build is the same program, running the other test builds."""
    plain, test = source_lines("BRAINIAC"), source_lines("BRAINIAC", test=True)
    changed = [(a, b) for a, b in zip(plain, test) if a != b]
    assert len(plain) == len(test)
    assert sorted(b for _, b in changed if b.startswith("prgm")) == ["prgmGEOMUNS", "prgmUNITSUNS"]
    assert [b[:24] for _, b in changed if b.startswith("Menu(")] == ['Menu("BRAINIAC 0.5 TEST"']
    assert len(changed) == 3
    for name in TEST_NAMES:
        assert len(TEST_NAMES[name]) <= 8 and TEST_NAMES[name].isalpha()   # TI's name rules


def test_test_builds_call_each_other():
    b = load_program(test=True)
    b.run()
    assert b.wait.title == "BRAINIAC 0.5 TEST"
    b.answer("5")
    b.answer("1")
    assert b.wait.title == "GEOMETRY"
    b.answer("7")
    b.answer("5")
    b.answer("2")
    assert b.wait.title == "UNIT CONVERSION"
    assert set(b.programs) == {"GEOMUNS", "UNITSUNS"}


def test_program_size_and_os():
    assert len(program_bytes()) < 60_000            # the CE has ~150 KB of RAM
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


def test_labels_are_plain_letters_on_the_calculator():
    """Every label must be one or two separate letter/digit tokens.

    The first 0.2 build named a label FV, which the calculator reads as the
    finance variable FV: ERR:SYNTAX at "Lbl FV".
    """
    from tivars.tokenizer import encode
    from tivars.tokenizer.decoder import decode
    for line in source_lines():
        if line.startswith(("Lbl ", "Goto ")):
            tokens, _ = decode(encode(line)[0])
            assert len(tokens) <= 3 and all(len(t.accessible) == 1 for t in tokens[1:]), line


def test_no_menu_has_more_than_seven_options():
    for line in source_lines():
        if line.startswith("Menu("):
            args = split_args(line[5:].rstrip(")"))        # commas in quotes don't count
            assert len(args) <= 15, line                   # a title and 7 options
            assert all(len(a.strip('"')) <= 24 for a in args[1::2]), line   # fits the screen


def test_simulator_refuses_what_the_calculator_might_not_do():
    """Rules Brainiac can't verify from here, so it must not depend on them."""
    for text in ['seq(𝑖I,I,1,3)→ʟA', '{1,2}→ʟA\n𝑖→ʟA(1)', 'augment({𝑖},{2})→ʟA',
                 'Menu("T","1",A,"2",A,"3",A,"4",A,"5",A,"6",A,"7",A,"8",A)\nLbl A']:
        m = Machine.from_text(text)
        m.complex_mode = True
        assert m.run().kind == "error", text


def test_build_refuses_letters_that_fuse_into_one_token():
    from brainiac.build import check
    from tivars.tokenizer import encode
    with pytest.raises(BuildError, match="fused"):
        check(encode("Lbl FV")[0])


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
    ("1/X=4", ["TIMES (X)", "⁻4X+1=0", "X=1/4=.25"]),
    ("X^3=6X^2-11X+6", ["FACTORED: (X-1)(X-2)(X-3)", "X=1", "X=2", "X=3"]),
    ("2X+1=2X+1", ["EVERY X WORKS! BOTH SIDES", "ARE ALWAYS EQUAL."]),
    ("X+1=X+2", ["NO ANSWER: THE TWO SIDES", "ARE NEVER EQUAL."]),
    ("X^2-4", ["FACTORED: (X+2)(X-2)", "X=⁻2", "X=2"]),          # no = means =0
])
def test_solve_polynomials_exactly(equation, expected):
    assert answers_for(SOLVE, equation) == expected


@pytest.mark.parametrize("equation, expected", [
    ("2^X=X+3", [-2.8625003712202988, 2.444907554610207]),
    ("abs(X)+1=X^2", [-(1 + math.sqrt(5)) / 2, (1 + math.sqrt(5)) / 2]),
    ("√(X)+1=X", [(3 + math.sqrt(5)) / 2]),
    ("cos(X)=X", [0.7390851332151607]),
    ("e^(X^2)=X+5", [-1.1599505372, 1.3601671430]),                    # no overflow
])
def test_solve_everything_else_by_searching(equation, expected):
    lines = answers_for(SOLVE, equation)
    assert lines[0].startswith("(SEARCHED")
    assert values(lines) == pytest.approx(expected, abs=1e-8)


@pytest.mark.parametrize("equation, expected", [
    ("abs(2X-3)=7", ["CASE 1: 2X-3=7", "X=5", "CASE 2: 2X-3=⁻(7)", "X=⁻2"]),
    ("7=abs(2X-3)", ["CASE 1: 2X-3=7", "X=5", "CASE 2: 2X-3=⁻(7)", "X=⁻2"]),
    ("abs(X-1)=2X", ["CASE 1: X-1=2X", "X=⁻1 NO: FAILS CHECK",
                     "CASE 2: X-1=⁻(2X)", "X=1/3=.3333333333"]),
])
def test_solve_absolute_value(equation, expected):
    assert answers_for(SOLVE, equation) == expected


@pytest.mark.parametrize("equation, expected", [
    ("√(X+5)=X-1", ["SQUARE BOTH SIDES:", "X+5=(X-1)²", "FACTORED: ⁻(X+1)(X-4)",
                    "X=⁻1 NO: FAILS CHECK", "X=4"]),
    ("√(2X+3)=X", ["SQUARE BOTH SIDES:", "2X+3=(X)²", "FACTORED: ⁻(X+1)(X-3)",
                   "X=⁻1 NO: FAILS CHECK", "X=3"]),
    ("√(X)=3", ["SQUARE BOTH SIDES:", "X=(3)²", "X=9"]),
    ("³√(X+1)=2", ["CUBE BOTH SIDES:", "X+1=(2)³", "X=7"]),
])
def test_solve_radical(equation, expected):
    assert answers_for(SOLVE, equation) == expected


@pytest.mark.parametrize("equation, expected", [
    ("3/(X-2)+1=X/(X-2)", ["TIMES (X-2)", "1=0", "NO ANSWER: THE TWO SIDES", "ARE NEVER EQUAL."]),
    ("(X^2-4)/(X-2)=4", ["TIMES (X-2)", "X²-4X+4=0", "FACTORED: (X-2)²",
                         "X=2 (DOUBLE) NO: BOTTOM=0"]),
    ("1/(X-1)+1/(X+1)=1", ["TIMES (X-1)(X+1)", "⁻X²+2X+1=0", "X=1+√(2)", " =2.414213562",
                           "X=1-√(2)", " =⁻.4142135624"]),
    ("(2X+1)/(X-3)=5", ["TIMES (X-3)", "⁻3X+16=0", "X=16/3=5.333333333"]),
])
def test_solve_rational(equation, expected):
    assert answers_for(SOLVE, equation) == expected


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
    assert m.wait.title == "WHAT NEXT?"           # MAIN MENU leaves the prompt...
    m.answer("1")
    assert m.wait.kind == "input"                 # ...AGAIN types it again


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
    for step in ["STANDARD: 2X²+3X-5=0", "A=2, B=3, C=⁻5", " =(3)²-4(2)(⁻5)", " =9+40", " =49",
                 "D>0: TWO REAL ROOTS.", "X=1", "X=⁻5/2", "(2X+5)(X-1)",
                 "VERTEX: (⁻3/4,⁻49/8)", " 2(X+3/4)²-49/8",
                 "ROOT SUM=⁻B/A=⁻3/2", "ROOT PRODUCT=C/A=⁻5/2"]:
        assert step in lines, step


@pytest.mark.parametrize("form, standard, factored, vertex", [
    ("(X-1)(X+3)", "STANDARD: X²+2X-3=0", "(X+3)(X-1)", " (X+1)²-4"),
    ("3(X-2)^2+1", "STANDARD: 3X²-12X+13=0", "NONE WITH REAL NUMBERS", " 3(X-2)²+1"),
    ("X^2-6X+9", "STANDARD: X²-6X+9=0", "(X-3)²", " (X-3)²"),
])
def test_quadratic_forms_convert_from_any_form(form, standard, factored, vertex):
    lines = answers_for(POLY, QUAD, form)
    assert standard in lines and factored in lines and vertex in lines


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
    m, lines = run(SYSTEM, str(len(equations)), *equations)
    assert_clean(m)
    assert lines == expected


def test_systems_reject_nonlinear():
    m, lines = run(SYSTEM, "2", "X^2+Y=3")
    assert "LINEAR" in lines[1]


# -- rearranging -------------------------------------------------------------------------------

@pytest.mark.parametrize("formula, letter, expected", [
    ("A=BH/2", "H", ["H=2A/B"]),
    ("PV=NRT", "T", ["T=PV/(NR)"]),        # (0.4 showed V/(NR): the simulator lost the P)
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
    ("T=2pi√(L/G)", "T", ["T=2π√(L/G)"]),          # (no ⁻ root: squaring added it)
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


# -- inequalities (0.4) ---------------------------------------------------------------------

def inequality(text, degree=False):
    """Solve an inequality: the number line comes first, then the working.
    Returns the machine, the number line's text (left to right, top to
    bottom) and the working's lines."""
    m = load_program()
    m.radians = not degree
    m.run()
    m.answer(SOLVE)
    m.answer(text)
    assert m.wait.kind == "pause" and m.showing_graph, (m.wait, m.statement_text())
    assert all(0 <= x < GW and 0 <= y < GH for x, y in m.graph.pixels)
    picture = [t for _, _, t, _ in sorted(m.graph.texts)]
    m.number_line = list(m.graph.texts), set(m.graph.pixels)
    m.answer("")
    assert not m.showing_graph
    lines = []
    while True:
        assert m.wait.kind != "error", f"{m.wait.message} at: {m.statement_text()}"
        lines += [ln.rstrip() for ln in view(m) if ln.strip() and "ENTER:" not in ln]
        if m.wait.kind == "pause" and "MORE" in m.screen.lines()[9]:
            m.answer("")
            continue
        break
    assert_clean(m)
    return m, picture, lines


@pytest.mark.parametrize("text, answer, plain, marks", [
    ("2X+3>7", "(2,INF)", " SO X>2", ["2"]),
    ("X^2-4<0", "(⁻2,2)", " SO ⁻2<X<2", ["⁻2", "2"]),
    ("abs(X-1)<3", "(⁻2,4)", " SO ⁻2<X<4", ["⁻2", "4"]),
    ("X^2-4≥0", "(-INF,⁻2]U[2,INF)", None, ["⁻2", "2"]),
    ("(X-2)^2≤0", "{2}", None, ["2"]),
    ("(X-2)^2>0", "(-INF,2)U(2,INF)", None, ["2"]),
    ("1/(X-1)>0", "(1,INF)", " SO X>1", ["1"]),
    ("X^2+1>0", "(-INF,INF)", " SO EVERY X WORKS", []),
    ("X^2+1<0", "NO SOLUTION", None, []),
    ("(X-1)/(X+2)≤0", "(⁻2,1]", " SO ⁻2<X≤1", ["⁻2", "1"]),
    ("X^3-X>0", "(⁻1,0)U(1,INF)", None, ["⁻1", "0", "1"]),
    ("⁻2X+1≥5", "(-INF,⁻2]", " SO X≤⁻2", ["⁻2"]),
    ("2^X>8", "(3,INF)", " SO X>3", ["3"]),
    ("5(2^X)≤40", "(-INF,3]", " SO X≤3", ["3"]),
    # where a log or square root starts is a boundary too
    ("log(X)<2", "(0,100)", " SO 0<X<100", ["0", "100"]),
    ("√(X)<2", "[0,4)", " SO 0≤X<4", ["0", "4"]),
    ("√(X)>0", "(0,INF)", " SO X>0", ["0"]),
    ("ln(X)≥0", "[1,INF)", " SO X≥1", ["0", "1"]),
    ("√(X+5)≤X-1", "[4,INF)", " SO X≥4", ["⁻5", "4"]),
    ("log(X)+log(X-3)≤1", "(3,5]", " SO 3<X≤5", ["3", "5"]),
    ("√(4-X^2)≥1", "[⁻1.732050808,1.732050808]", None, ["⁻2", "⁻1.732", "1.732", "2"]),
])
def test_inequalities(text, answer, plain, marks):
    m, picture, lines = inequality(text)
    assert picture[0] == text and picture[-1] == "ENTER FOR THE WORKING"
    assert picture[-2] == answer
    assert picture[1:-2] == marks                     # under the dots, in order
    assert lines[0] == text
    assert "ANSWER: " + answer in "".join(lines)
    if plain:
        assert plain in lines
    assert lines[-1] == "( ) EXCLUDED, [ ] INCLUDED"


def test_inequality_shows_the_working():
    m, picture, lines = inequality("X^2-4≥0")
    assert lines == ["X^2-4≥0", "BOUNDARIES (WHERE THE TWO", "SIDES ARE EQUAL, OR A",
                     "BOTTOM IS 0):", "⁻2 2", "TEST A POINT IN EACH PART:",
                     "⁻3 YES, 0 NO, 3 YES", "ANSWER: (-INF,⁻2]U[2,INF)",
                     "( ) EXCLUDED, [ ] INCLUDED"]
    m, picture, lines = inequality("log(X)<2")
    assert lines[1:6] == ["BOUNDARIES (WHERE THE TWO", "SIDES ARE EQUAL, A BOTTOM",
                          "IS 0, OR A LOG OR SQUARE", "ROOT STARTS):", "0 100"]
    assert "⁻1 NO, 50 YES, 101 NO" in lines          # no log of ⁻1: not a real number


def test_number_line_dots_and_labels_line_up():
    m, picture, lines = inequality("(X-1)/(X+2)≤0")
    texts, pixels = m.number_line
    labels = sorted((col, t) for row, col, t, _ in texts if row == 90)
    assert [t for _, t in labels] == ["⁻2", "1"]
    # each label sits under its dot: dots at 20+224I/(N+1)
    for (col, t), x in zip(labels, [20 + 224 / 3, 20 + 448 / 3]):
        assert abs(col + 4 * len(t) - x) < 4, (t, col)
    # ⁻2 is left open, 1 filled in: look just above the line, in the middle
    assert (int(20 + 448 / 3), 80) in pixels and (int(20 + 224 / 3), 80) not in pixels


def test_inequality_again_asks_for_another():
    m, _, _ = inequality("2X+3>7")
    m.answer("")
    m.answer("1")                                   # AGAIN
    assert m.wait.kind == "input" and m.wait.prompt == "EQ:"


def test_inequality_with_two_signs_is_refused():
    m, lines = run(SOLVE, "X<2=Y")
    assert m.wait.kind != "error"


# -- exponential and log equations (0.4) ------------------------------------------------------

@pytest.mark.parametrize("equation, expected", [
    ("2^X=16", ["TAKE LOG BASE 2 OF EACH:", "X=LOG2(16)", "X-4=0", "X=4"]),
    ("3^(2X-1)=27", ["TAKE LOG BASE 3 OF EACH:", "(2X-1)=LOG3(27)", "2X-4=0", "X=2"]),
    ("2^(X+1)=8^(X-1)", ["TAKE LOG BASE 2 OF EACH:", "(X+1)=(X-1)×LOG2(8)", "⁻2X+4=0", "X=2"]),
    ("4^X=2^(X+3)", ["TAKE LOG BASE 4 OF EACH:", "X=(X+3)×LOG4(2)", "X/2-3/2=0", "X=3"]),
    ("(1/2)^X=8", ["LOG BASE (1/2) OF EACH:", "X=LOG(1/2)(8)", "X+3=0", "X=⁻3"]),
    ("e^(X)=10", ["TAKE LN OF BOTH SIDES:", "X=LN(10)", "X-2.302585093=0", "X=2.302585093"]),
    ("e^(2X)=7", ["TAKE LN OF BOTH SIDES:", "2X=LN(7)", "2X-1.945910149=0", "X=.9729550745"]),
    ("100=10^(X)", ["TAKE LOG BASE 10 OF EACH:", "(X)=LOG10(100)", "X-2=0", "X=2"]),
    (r"\xC1X)=1000", ["TAKE LOG BASE 10 OF EACH:", "X=LOG10(1000)", "X-3=0", "X=3"]),  # 2ND LOG
    ("log(X)+log(X-3)=1", ["RAISE 10 TO BOTH SIDES:", "X²-3X-10=0", "FACTORED: (X+2)(X-5)",
                           "X=⁻2 NO: FAILS CHECK", "X=5"]),
    ("ln(X)+ln(X+1)=ln(6)", ["6)", "RAISE E TO BOTH SIDES:", "X²+X-6=0", "FACTORED: (X+3)(X-2)",
                             "X=⁻3 NO: FAILS CHECK", "X=2"]),
    ("log(2X+4)=1", ["RAISE 10 TO BOTH SIDES:", "2X-6=0", "X=3"]),
    ("log(X)=log(2X-5)", ["RAISE 10 TO BOTH SIDES:", "⁻X+5=0", "X=5"]),
    ("log(X^2)=2", ["RAISE 10 TO BOTH SIDES:", "X²-100=0", "FACTORED: (X+10)(X-10)", "X=⁻10", "X=10"]),
    ("log(X^3)=6", ["RAISE 10 TO BOTH SIDES:", "X³-1000000=0", "FACTORED: (X-100)(X²+100X+",
                    "10000)", "X=100", "X=⁻50+50𝑖√(3) NOT REAL", "X=⁻50-50𝑖√(3) NOT REAL"]),
])
def test_exponential_and_log_equations(equation, expected):
    assert answers_for(SOLVE, equation) == expected


@pytest.mark.parametrize("equation, alone, rest", [
    ("5(2^X)=40", "2^X=8", ["TAKE LOG BASE 2 OF EACH:", "X=LOG2(8)", "X-3=0", "X=3"]),
    ("2^X+1=9", "2^X=8", ["TAKE LOG BASE 2 OF EACH:", "X=LOG2(8)", "X-3=0", "X=3"]),
    ("10=2^X+2", "2^X=8", ["TAKE LOG BASE 2 OF EACH:", "X=LOG2(8)", "X-3=0", "X=3"]),
    ("3^(X+1)-5=76", "3^(X+1)=81", ["TAKE LOG BASE 3 OF EACH:", "(X+1)=LOG3(81)", "X-3=0", "X=3"]),
    ("3e^(X)-2=10", "𝑒^(X)=4", ["TAKE LN OF BOTH SIDES:", "X=LN(4)", "X-1.386294361=0",
                                "X=1.386294361"]),
    ("4e^(2X)=20", "𝑒^(2X)=5", ["TAKE LN OF BOTH SIDES:", "2X=LN(5)", "2X-1.609437912=0",
                                "X=.8047189562"]),
    ("3ln(X)=6", "ln(X)=2", ["RAISE E TO BOTH SIDES:", "X-7.389056099=0", "X=7.389056099"]),
    ("ln(X)/2=3", "ln(X)=6", ["RAISE E TO BOTH SIDES:", "X-403.4287935=0", "X=403.4287935"]),
    ("2log(X)+1=5", "log(X)=2", ["RAISE 10 TO BOTH SIDES:", "X-100=0", "X=100"]),
    ("2abs(X-1)+3=7", "abs(X-1)=2", ["CASE 1: X-1=2", "X=3", "CASE 2: X-1=⁻(2)", "X=⁻1"]),
    ("√(X)+3=5", "√(X)=2", ["SQUARE BOTH SIDES:", "X=(2)²", "X=4"]),
])
def test_get_the_hard_part_alone_first(equation, alone, rest):
    lines = answers_for(SOLVE, equation)
    term = alone.split("=")[0]
    assert lines[:3] == [f"GET {term} ALONE:", alone, "SOLVING: " + alone]
    assert lines[3:] == rest


def test_a_power_is_never_negative():
    assert answers_for(SOLVE, "2^X+5=3") == ["GET 2^X ALONE:", "2^X=⁻2", "NO ANSWER: A POWER LIKE",
                                              "THAT IS NEVER 0 OR LESS."]


@pytest.mark.parametrize("equation", ["2^X=X+3", "2^X+2^(X+1)=12", "1/2^X=4", "X+ln(X)=1"])
def test_get_alone_only_when_it_is_alone(equation):
    lines = answers_for(SOLVE, equation)
    assert "ALONE" not in " ".join(lines)


# -- help me choose (0.4) ---------------------------------------------------------------------

@pytest.mark.parametrize("picks, where", [
    (["1", "1", "1"], ("input", "EQ:")),             # an equation, one letter, the answer
    (["1", "1", "2"], ("input", "EQ:")),             # quadratic steps
    (["1", "1", "3"], ("input", "P(X)=")),           # every root
    (["1", "2", "1"], ("input", "FORMULA:")),        # several letters: rearrange
    (["2"], ("input", "EQ:")),                       # an inequality
    (["3", "1"], ("input", "EXPR:")),                # an expression: factor
    (["3", "2"], ("input", "EXPR:")),                # expand
    (["3", "3"], ("input", "F(X)=")),                # where it equals 0
    (["3", "4"], ("input", "TOP:")),                 # divide
    (["4", "1"], ("input", "FORMULA:")),
    (["4", "2"], ("input", "F(X)=")),                # inverse
    (["5"], ("input", "HOW MANY:")),                 # a system
])
def test_help_me_choose_leads_to_the_right_tool(picks, where):
    m = load_program()
    m.run()
    m.answer(HELP)
    for p in picks:
        assert m.wait.kind == "menu", (picks, m.wait)
        m.answer(p)
    assert (m.wait.kind, m.wait.prompt) == where


def test_help_me_choose_back_buttons():
    m = load_program()
    m.run()
    m.answer(HELP)
    for back in (["1", "3"], ["1", "1", "4", "3"], ["3", "5"], ["4", "3"]):
        for p in back:
            m.answer(p)
        assert m.wait.title == "WHAT DO YOU HAVE?", back
    m.answer("6")                                    # a shape, or units
    assert m.wait.title == "GEOMETRY & UNITS"
    m.answer("2")
    assert m.wait.title == "UNIT CONVERSION"


# -- degree mode (0.4) --------------------------------------------------------------------------

def degree_run(*answers):
    m = load_program()
    m.radians = False
    m.run()
    for a in answers:
        assert m.wait.kind in ("menu", "input", "pause"), (answers, m.wait, m.statement_text())
        m.answer(a)
    assert m.wait.kind != "error", f"{m.wait.message} at: {m.statement_text()}"
    lines = [ln.rstrip() for ln in view(m) if ln.strip() and "ENTER:" not in ln]
    return m, lines


def test_simulator_refuses_complex_e_in_degree_mode():
    """What the calculator said: ERR:DOMAIN, "DEGREE MODE e^(π/2*i)"."""
    m = Machine.from_text("Degree\na+bi\n𝑒^(\\xAC/2*𝑖)→A")
    assert m.run().kind == "error" and m.wait.message == "ERR:DOMAIN"
    m = Machine.from_text("Radian\na+bi\n𝑒^(\\xAC/2*𝑖)→A")
    assert m.run().kind == "done"


def test_simplify_works_in_degree_mode():
    """The bug report: Simplify (3X³-X²-6)(X-5) gave ERR:DOMAIN in Degree."""
    m, lines = degree_run(POLY, SIMPLIFY, "(3X^3-X^2-6)(X-5)")
    assert lines == ["(3X^3-X^2-6)(X-5)", "=3X^4-16X³+5X²-6X+30"]


@pytest.mark.parametrize("answers, expected", [
    ((SOLVE, "X^2+5X+20=0"), ["SOLVING: X^2+5X+20=0", "X=(⁻5+𝑖√(55))/2", " =⁻2.5+3.708099244𝑖",
                              "X=(⁻5-𝑖√(55))/2", " =⁻2.5-3.708099244𝑖"]),
    ((POLY, ROOTS, "X^5+X+1"), None),
    ((POLY, FACTOR, "X^3-8"), ["X^3-8", "=(X-2)(X²+2X+4)"]),
    ((POLY, QUAD, "2X^2+3X-5"), None),
    (REARRANGE + ("A=BH/2", "H"), None),
    (INVERSE + ("2X+3",), None),
    ((SYSTEM, "2", "X+Y=10", "X-Y=2"), ["X=6", "Y=4"]),
    ((SOLVE, "2^X=16"), None),
])
def test_everything_works_in_degree_mode(answers, expected):
    m, lines = degree_run(*[x for a in answers for x in (a if isinstance(a, tuple) else (a,))])
    if expected:
        assert lines == expected


def test_trig_uses_the_mode_you_were_in():
    m, lines = degree_run(SOLVE, "sin(X)=.5")
    assert lines == ["SOLVING: sin(X)=.5", "(SEARCHED 0 TO 360)", "X=30", "X=150"]
    m, lines = degree_run(SOLVE, "2sin(30)X=4")          # sin(30°)=1/2, so X=4
    assert "X=4" in lines
    lines = answers_for(SOLVE, "sin(X)=.5")              # Radian: radians
    assert lines[0] == "(SEARCHED ⁻99 TO 99)" and values(lines)[31] == pytest.approx(math.pi / 6)


def test_trig_inequality_in_degree_mode():
    m, picture, lines = inequality("sin(X)>.5", degree=True)
    assert picture[1:-1] == ["30", "150", "(30,150)"]
    assert " SO 30<X<150" in lines and "(FOR X FROM 0 TO 360)" in lines


@pytest.mark.parametrize("degree", [True, False])
def test_quit_puts_the_angle_mode_back(degree):
    m = load_program()
    m.radians = not degree
    m.run()
    assert m.radians                                  # its own math runs in Radian
    m.answer(SOLVE)
    m.answer("X^2=4")
    m.answer("")
    m.answer("3")                                     # quit
    assert m.wait.kind == "done"
    assert m.radians is (not degree)
    assert m.lists == {}


# -- the program as a whole -----------------------------------------------------------------------

def test_many_rounds_do_not_leak():
    """Go round the menus like a student would; nothing may stay open."""
    m = load_program()
    m.run()
    for mode, inputs in [(SOLVE, ["X^2=9"]), (SOLVE, ["sin(X)=2"]), (REARRANGE, ["A=BH", "B"]),
                         (INVERSE, ["2X"]), (SYSTEM, ["2", "X+Y=2", "X-Y=0"])]:
        for text in (mode if isinstance(mode, tuple) else (mode,)):
            m.answer(text)
        for text in inputs:
            m.answer(text)
        while m.wait.kind == "pause":
            m.answer("")
        assert m.wait.kind == "menu" and m.wait.title == "WHAT NEXT?", m.wait
        m.answer("3" if len(m.wait.options) == 4 else "2")      # main menu
        assert_clean(m)
        assert m.lists["BS"] == []                              # subroutine stack empty
    m.answer(HELP)                                              # help me choose
    assert m.wait.title == "WHAT DO YOU HAVE?"
    m.answer("7")                                               # how to use
    for _ in range(3):
        assert m.wait.kind == "pause"
        m.answer("")
    assert m.wait.title == "BRAINIAC 0.5"
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


def test_there_are_no_empty_strings():
    """""→Str1 is ERR:INVALID DIM on the calculator (the 0.2 bug)."""
    for text in ['""→Str1', '"A"→Str1\n""+Str1→Str2', 'length("")→A']:
        assert Machine.from_text(text).run().kind == "error", text
    m = Machine.from_text('Disp ""')          # a blank line is fine, though
    assert m.run().kind == "done"


def test_no_source_line_stores_an_empty_string():
    for line in source_lines():
        assert '""→' not in line, line


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


# -- crashes found by the 0.5 stress tests ---------------------------------------------------------

@pytest.mark.parametrize("degree, answers, shows", [
    (True, (SOLVE, "tan(X)=1"), "X=225"),                    # a search point landed on tan(90)
    (False, (POLY, ROOTS, "sin(X)"), "THAT ISN'T A POLYNOMIAL"),   # list BM read before made
    (False, (POLY, FACTOR, "X-X"), "=0"),                    # dividing by a 0 common factor
    (False, (HELP, "3", "4", "5", "X^2"), "QUOTIENT: 0"),     # a constant top: list BM again
    (False, (SOLVE, "e^(X)=0"), "THAT IS NEVER 0 OR LESS."),  # ln(0)
    (False, (SOLVE, "3^X=⁻9"), "THAT IS NEVER 0 OR LESS."),
])
def test_stress_test_crashes_are_fixed(degree, answers, shows):
    m = load_program()
    m.radians = not degree
    m.run()
    for a in [x for a in answers for x in (a if isinstance(a, tuple) else (a,))]:
        m.answer(a)
    assert m.wait.kind == "pause", (m.wait, m.statement_text())
    assert shows in [ln.strip() for ln in m.screen.lines()]
    assert_clean(m)


def test_a_long_inequality_answer_stays_on_the_picture():
    m, picture, lines = inequality("sin(X)>.5")                # Radian: many intervals
    assert "ANSWER: SEE THE WORKING" in picture
    texts, _ = m.number_line
    assert all(len(t) <= 32 for row, col, t, _ in texts if row == 136)


TYPO = ["BRAINIAC CAN'T READ THAT.", "CHECK FOR A TYPO, LIKE", "X^ OR *2 OR AN EXTRA ).",
        "USE (-) FOR NEGATIVES."]


@pytest.mark.parametrize("answers, message", [
    ((SOLVE, "2X+3=7)"), TYPO),                       # was ERR:SYNTAX at expr(
    ((SOLVE, "X^=4"), TYPO),
    ((SOLVE, "X..2=1"), TYPO),
    ((SOLVE, "*X=2"), TYPO),
    ((SOLVE, "X/0=1"), ["THAT DIVIDES BY 0.", "CHECK WHAT YOU TYPED."]),   # ERR:DIVIDE BY 0
    ((ZEROS, "X^2-1)"), TYPO),
    ((POLY, ROOTS, "X)"), TYPO),
    ((POLY, SIMPLIFY, "(X+1))^2"), TYPO),
    ((POLY, DIVIDE, "X^2", "X)"), TYPO),
    (REARRANGE + ("A=BH/2)",), TYPO),
    (INVERSE + ("2X)",), TYPO),
    ((SYSTEM, "2", "X+Y=10)"), TYPO),
])
def test_typing_mistakes_get_a_message_not_an_error(answers, message):
    m, lines = run(*answers)
    assert lines[:len(message)] == message
    m.answer("")
    assert m.wait.title == "WHAT NEXT?"
    m.answer("1")                                     # AGAIN
    assert m.wait.kind == "input"


@pytest.mark.parametrize("answers, shows", [
    # the minus key where (-) was meant: "take away" with nothing before it
    ((SOLVE, r"2X+3=\x717"), "X=⁻5"),
    ((SOLVE, r"\x71X=5"), "X=⁻5"),
    ((POLY, ROOTS, r"\x71X^2+1"), "X=1"),
    ((SYSTEM, "2", r"X+Y=\x711", "X-Y=3"), "X=1"),
])
def test_the_minus_key_works_like_negative(answers, shows):
    m, lines = run(*answers)
    assert shows in lines


@pytest.mark.parametrize("text", ["X/0.5=1", "((X+1)=2", "(X+1=2", "2/(X-1)=1"])
def test_the_input_check_lets_good_input_through(text):
    m, lines = run(SOLVE, text)
    assert lines[0].startswith("SOLVING:")


@pytest.mark.parametrize("text, degree, answer", [
    ("(X+2)^2(X+1)≥0", False, "{⁻2}U[⁻1,INF)"),        # was {⁻2}U,INF): numstr changed S
    ("X(X-4)^2≥0", False, "[0,INF)"),
    ("X-X≤0", False, "(-INF,INF)"),                    # was NO SOLUTION
    ("3(X+1)≥3X+3", False, "(-INF,INF)"),
    ("1/X>2", False, "(0,1/2)"),                       # was (7.337035038ᴇ⁻11,1/2)
    ("1/(X^2+1)>.5", False, "(⁻1,1)"),                 # was ERR:INVALID DIM
    ("1/(X-200)>0", False, "(200,INF)"),               # hole outside ⁻99..99
    ("log(X^2)>0", False, "(-INF,⁻1)U(1,INF)"),        # test point 0 was ERR:DOMAIN
    ("log(X)-log(X-3)≤1", False, "[10/3,INF)"),        # was (-INF,0)U[10/3,INF)
    ("2^X<5^X", False, "(0,INF)"),                     # was NO SOLUTION
    ("tan(X)>1", True, "(45,90)U(225,270)"),           # tan's poles are boundaries
    ("tan(X)≥0", True, "[0,90)U[180,270)U{360}"),      # was ERR:DOMAIN at tan(90)
    ("sin(X)<.5", True, "[0,30)U(150,360]"),           # 0 to 360, not -INF to INF
    ("cos(X)≤1", True, "[0,360]"),
])
def test_inequality_stress_test_fixes(text, degree, answer):
    m, picture, lines = inequality(text, degree)
    assert picture[-2] == answer


def test_a_double_inequality_is_explained_not_misread():
    m, lines = run(SOLVE, "⁻3<2X+1<7")                # was "EVERY X WORKS"
    assert lines[0] == "ONE SIGN AT A TIME. FOR"


@pytest.mark.parametrize("answers, shows", [
    ((FORMULAS, "1", "X^10=1024", "X"), "X=(1024)^(1/10)"),   # said THAT LETTER CANCELS OUT
    ((FORMULAS, "1", "X^11=2048", "X"), "X=(2048)^(1/11)"),   # said X=2048/X^10
    ((POLY, SIMPLIFY, "X^200"), "THAT POWER IS TOO BIG."),     # was ERR:OVERFLOW
])
def test_big_powers(answers, shows):
    m, lines = run(*answers)
    assert shows in lines


@pytest.mark.parametrize("equation, shows", [
    ("1.49X^2=7000069", " =2167.49461"),            # was 2167.643064: 1.49 snapped to a fraction
    ("(X+1)^2=X^2+2X+1", "EVERY X WORKS! BOTH SIDES"),   # was 382 "answers"
    ("X^2=1000000000", " =31622.7766"),              # was NO ANSWER
    ("X^2=10000000000", "X=100000"),
    ("(X-5)^5=0", "X=5 (5 TIMES)"),                  # was five complex roots
    ("(X^2-2)^2=0", "FACTORED: (X²-2)²"),
    ("πX^3=X^2", "X=0 (DOUBLE)"),
])
def test_solve_poly_stress_fixes(equation, shows):
    lines = answers_for(SOLVE, equation)
    assert shows in lines


def test_a_number_under_a_root_is_not_a_radical_equation():
    lines = answers_for(SOLVE, "X^2=√(2)")         # had X=±1.189𝑖 as answers too
    assert lines == ["X=1.189207115", "X=⁻1.189207115"]


@pytest.mark.parametrize("equation, degree, shows", [
    ("tan(2X)=1", True, "X=45/2"),                    # a halving midpoint hit tan(90)
    ("ln(X)=ln(2X)", False, "X=0 NO: FAILS CHECK"),   # was ERR:DOMAIN (log(0) in the check)
    ("√(X-5)=√(2X-3)", False, "X=⁻2 NO: FAILS CHECK"),  # was "X=⁻2": both sides non-real
    ("abs(X)=X", False, "TRUE FOR EVERY X WITH"),     # was "EVERY X WORKS"
    ("abs(X^2+6)=⁻2", False, "X=2𝑖 NOT REAL"),
    ("(X^2-4)/(X-2)=X+2", False, "EXCEPT WHERE A BOTTOM IS 0"),
    ("-log(X)=10", False, "X=9.999495676ᴇ⁻11"),       # pH 10: no FAILS CHECK, no π
])
def test_solve_nonpoly_stress_fixes(equation, degree, shows):
    m = load_program()
    m.radians = not degree
    m.run()
    m.answer(SOLVE)
    m.answer(equation)
    lines = []
    while True:
        assert m.wait.kind != "error", (m.wait, m.statement_text())
        lines += [ln.strip() for ln in view(m) if ln.strip()]
        if m.wait.kind == "pause" and "MORE" in m.screen.lines()[9]:
            m.answer("")
            continue
        break
    assert shows in lines
    assert_clean(m)


def test_trig_of_a_number_is_just_a_number_in_degree():
    m, lines = degree_run(SOLVE, "sin(30)X^2=2")      # was "0 TO 360": missed X=⁻2
    assert lines[1:4] == ["(SEARCHED ⁻99 TO 99)", "X=⁻2", "X=2"]
