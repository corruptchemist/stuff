"""The simulator must be at least as strict as a real TI-84 Plus CE (OS 5.x).

Every Brainiac bug found on the real calculator so far was the simulator
being more forgiving than the calculator. Each test here pins one rule, for
a command or function the three programs use, with where the rule comes from:

  tibasicdev  = TI-Basic Developer wiki (tibasicdev.wikidot.com), the page
                named after the command, "Error Conditions" section
  guidebook   = TI-84 Plus CE guidebook / eGuide, "Error Messages" table
  TI KB       = TI's customer-support knowledge base
"""

import math

import pytest

from tivars.tokenizer import encode

from brainiac.screen import view
from brainiac.tibasic import COLS, Machine, fmt, tokens_from_bytes


def run(text, complex_mode=False, radians=True, tokens_in_strings=False):
    """Run a little program. tokens_in_strings: "seq(" inside quotes is the
    seq( token, as typed on the calculator, not the letters s, e, q, (."""
    if tokens_in_strings:
        m = Machine(tokens_from_bytes(encode(text, mode="max")[0]))
    else:
        m = Machine.from_text(text)
    m.complex_mode = complex_mode
    m.radians = radians
    m.run()
    return m


def error(text, **kw):
    """The error a program stops with ("" if it finishes)."""
    m = run(text, **kw)
    assert m.wait.kind in ("done", "error"), m.wait
    return m.wait.message.removeprefix("ERR:").split(" (")[0] if m.wait.kind == "error" else ""


# -- Output( --------------------------------------------------------------------------------

def test_output_needs_whole_number_row_and_column():
    # tibasicdev, Output(: ERR:DOMAIN if the row or column is not an integer
    # in range; the wiki adds that a value a hair off from round-off fails too
    assert error('Output(1.5,1,"A")') == "DOMAIN"
    assert error('Output(1,2.0000001,"A")') == "DOMAIN"
    assert error('Output(11,1,"A")') == "DOMAIN"
    assert error('Output(1,27,"A")') == "DOMAIN"
    assert error('Output(10,26,"A")') == ""


def test_output_wraps_and_is_cut_off_at_the_bottom_right():
    # tibasicdev, Output(: text wraps to the next row, and past the last
    # column of the last row it is cut off (no error, no scrolling)
    m = run('Output(10,20,"ABCDEFGHIJ")')
    assert m.wait.kind == "done"
    assert m.screen.lines()[9].endswith("ABCDEFG")


# -- Disp -------------------------------------------------------------------------------------

def test_disp_cuts_a_long_line_with_an_ellipsis():
    # tibasicdev, Disp: Disp never wraps; text too long for the line is cut
    # off with an ellipsis at the end (26 columns on the CE)
    text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123"
    m = run(f'Disp "{text}"')
    assert m.screen.lines()[0] == text[:COLS - 1] + "…"
    m = run(f'Disp "{text[:COLS]}"')                  # exactly 26 fits
    assert m.screen.lines()[0] == text[:COLS]


# -- strings ----------------------------------------------------------------------------------

@pytest.mark.parametrize("text, err", [
    ('sub("ABC",1.5,1)', "DOMAIN"),      # start not an integer
    ('sub("ABC",1,1.5)', "DOMAIN"),      # length not an integer
    ('sub("ABC",0,1)', "DOMAIN"),        # start below 1
    ('sub("ABC",2,0)', "DOMAIN"),        # length 0: there are no empty strings
    ('sub("ABC",3,2)', "INVALID DIM"),   # past the end
    ('sub("ABC",3,1)', ""),
])
def test_sub(text, err):
    # tibasicdev, sub(: ERR:DOMAIN if the start or length is below 1 or not an
    # integer; ERR:INVALID DIM if they go past the end of the string
    assert error(text + "→Str1") == err


@pytest.mark.parametrize("text, err, value", [
    ('inString("ABCA","A",1.5)', "DOMAIN", None),
    ('inString("ABCA","A",0)', "DOMAIN", None),
    ('inString("ABCA","A",2)', "", 4),
    ('inString("ABCA","A",9)', "", 0),          # a start past the end is fine
    ('inString("AB","ABC")', "", 0),
])
def test_instring(text, err, value):
    # tibasicdev, inString(: ERR:DOMAIN if the start is not a positive
    # integer ("starting point may be longer than the length of the source
    # string, though")
    m = run(text + "→A")
    assert (m.wait.message.removeprefix("ERR:").split(" (")[0] if m.wait.kind == "error"
            else "") == err
    if value is not None:
        assert m.reals["A"] == value


def test_expr_cannot_nest():
    # tibasicdev, expr(: ERR:ILLEGAL NEST if the string contains expr( itself;
    # ERR:INVALID for an empty string (which Brainiac can't make anyway)
    assert error('"expr(Str2)"→Str1\n"2"→Str2\nexpr(Str1)→A',
                 tokens_in_strings=True) == "ILLEGAL NEST"
    assert error('"2"→Str2\nexpr("1+2")+expr(Str2)→A') == ""     # one after another is fine


def test_toString_of_complex_in_real_mode():
    # tibasicdev, toString(: ERR:NONREAL ANSWERS for complex input in Real mode
    assert error("a+bi\n2+𝑖→A\nReal\ntoString(A)→Str1") == "NONREAL ANS"


# -- number display (Disp, Output(, toString() --------------------------------------------------

@pytest.mark.parametrize("value, shown", [
    (0.001, ".001"),                    # Normal mode: .001 is still plain...
    (0.00099, "9.9ᴇ⁻4"),                # ...below that is scientific
    (9999999999.0, "9999999999"),       # 10 digits fit...
    (9999999999.5, "1ᴇ10"),             # ...but this rounds to 11 digits
    (0.12345678905, ".1234567891"),     # rounds half up on the decimal digits
    (-2.5e-50, "⁻2.5ᴇ⁻50"),
    (complex(3, -2), "3-2𝑖"),           # subtraction sign inside a+bi...
    (complex(0, -2), "⁻2𝑖"),            # ...negative sign at the front
])
def test_number_display(value, shown):
    # guidebook, Normal/Sci/Eng mode: Normal switches to scientific notation
    # "If the answer cannot be displayed in 10 digits or the absolute value
    # is less than .001"; the 14-digit value is rounded to 10 for display
    assert fmt(value) == shown


# -- lists ----------------------------------------------------------------------------------

def test_augment_of_an_empty_list():
    # tibasicdev, augment(: ERR:INVALID DIM if one of the arguments is a list
    # with dimension 0
    assert error("0→dim(ʟA)\naugment(ʟA,{1})→ʟB") == "INVALID DIM"
    assert error("{1}→ʟA\naugment(ʟA,{2})→ʟB") == ""


def test_an_empty_list_can_only_be_measured_or_stored_to():
    # tibasicdev, dim(: a list made empty still exists and dim( gives 0, but
    # using it any other way errors "just as for a deleted list"; recalling
    # an empty list is ERR:INVALID DIM
    assert error("0→dim(ʟA)\ndim(ʟA)→N") == ""
    assert error("0→dim(ʟA)\n5→ʟA(1+dim(ʟA))\nʟA(1)→N") == ""   # appending is fine
    for use in ["sum(ʟA)→N", "ʟA+1→ʟB", "max(ʟA)→N", "ʟA→ʟB", "SortA(ʟA)",
                "If ʟA=1:1→N"]:
        assert error("0→dim(ʟA)\n" + use) == "INVALID DIM", use


def test_sort_needs_a_list_that_exists():
    assert error("DelVar ʟA\nSortA(ʟA)") == "UNDEFINED"
    assert error("{3,1,2}→ʟA\nSortA(ʟA)\nʟA(1)→N") == ""


@pytest.mark.parametrize("text, err", [
    ("seq(I,I,1,0)→ʟA", "INCREMENT"),       # start past the end, step 1
    ("seq(I,I,1,5,⁻1)→ʟA", "INCREMENT"),    # step of the wrong sign
    ("seq(I,I,1,5,0)→ʟA", "INCREMENT"),
    ("seq(I,I,5,1,⁻1)→ʟA", ""),
    ("seq(I,I,1,1)→ʟA", ""),                # one element is fine
])
def test_seq_never_makes_an_empty_list(text, err):
    # guidebook, error messages, INCREMENT: "The increment, step, in seq( is 0
    # or has the wrong sign"
    assert error(text) == err


def test_seq_cannot_nest():
    # guidebook, error messages, ILLEGAL NEST: "You attempted to use an invalid
    # function in an argument to a function, such as seq( within expression
    # for seq(." The check is made as it runs, so expr( can't hide it.
    assert error("seq(sum(seq(J,J,1,I)),I,1,3)→ʟA") == "ILLEGAL NEST"
    assert error('"seq(J,J,1,2)"→Str1\nseq(sum(expr(Str1)),I,1,3)→ʟA',
                 tokens_in_strings=True) == "ILLEGAL NEST"
    assert error('"I+1"→Str1\nseq(expr(Str1),I,1,3)→ʟA') == ""   # expr( in seq( is fine


def test_comparing_lists_of_different_sizes():
    # guidebook, error messages, DIM MISMATCH: an operation on two lists
    # whose dimensions don't match (= < > included, like + - * /)
    assert error("{1,2}={1,2,3}→ʟA") == "DIM MISMATCH"
    assert error("{1,2}<{1,2,3}→ʟA") == "DIM MISMATCH"
    assert error("{1,2}={1,3}→ʟA") == ""


# -- round( ---------------------------------------------------------------------------------

@pytest.mark.parametrize("text, err", [
    ("round(1.2345,2.5)", "DOMAIN"),
    ("round(1.2345,10)", "DOMAIN"),
    ("round(1.2345,⁻1)", "DOMAIN"),
    ("round(1.2345,9)", ""),
])
def test_round_places(text, err):
    # tibasicdev, round(: ERR:DOMAIN if the number of places is not an
    # integer 0 through 9
    assert error(text + "→A") == err


# -- matrices -------------------------------------------------------------------------------

def test_matrices_are_real_and_at_most_99_by_99():
    # TI KB 34843: the TI-83 Plus / TI-84 Plus family does not allow complex
    # numbers in matrices (ERR:DATA TYPE). guidebook, INVALID DIM: a matrix
    # dimension outside 1-99.
    assert error("a+bi\n{2,2}→dim([J])\n𝑖→[J](1,1)", complex_mode=True) == "DATA TYPE"
    assert error("a+bi\n{2,2}→dim([J])\nFill(𝑖,[J])", complex_mode=True) == "DATA TYPE"
    assert error("{100,2}→dim([J])") == "INVALID DIM"
    assert error("{99,2}→dim([J])") == ""


# -- syntax ---------------------------------------------------------------------------------

@pytest.mark.parametrize("text, err", [
    ("1+2)→A", "SYNTAX"),                   # an unmatched ) is a syntax error
    ("Disp 1)", "SYNTAX"),
    ("If 1):1→A", "SYNTAX"),
    ('expr("1+2)")→A', "SYNTAX"),
    ("(1+2→A", ""),                         # but leaving ) off at the end is fine
    ('Output(1,1,"A")', ""),                # and a command's own ) is fine
    ("abs(⁻2)→A", ""),
])
def test_unmatched_close_paren(text, err):
    # TI-BASIC: closing parentheses may be left off before → or the end of a
    # line, but a ) with nothing to close is ERR:SYNTAX
    assert error(text) == err


@pytest.mark.parametrize("text", ["augment({1},{2},{3})→ʟA", "sub(\"ABC\",1)→Str1",
                                  "gcd(4,6,8)→A", "round(1,2,3)→A"])
def test_wrong_number_of_arguments(text):
    assert error(text) == "ARGUMENT"


# -- angle mode -------------------------------------------------------------------------------

def test_angle_follows_the_angle_mode():
    # angle( gives its answer in the current angle mode, like tan⁻¹(
    m = run("a+bi\nangle(1+𝑖)→A", complex_mode=True, radians=False)
    assert m.reals["A"] == pytest.approx(45)
    m = run("a+bi\nangle(1+𝑖)→A", complex_mode=True, radians=True)
    assert m.reals["A"] == pytest.approx(math.pi / 4)


# -- the screen as shown ------------------------------------------------------------------------

def test_view_shows_the_ellipsis():
    m = run('Disp "' + "A" * 30 + '"')
    assert view(m)[0] == "A" * 25 + "…"


def test_text_is_cut_off_at_the_right_edge():
    # tibasicdev, Text(: text never wraps; the calculator stops at the last
    # character that fits on the screen whole. Widths: Cemetech forum thread
    # "Character Pixel Widths" (color calculators): digits 6 px + 2 blank.
    m = run('Text(10,240,"12345")')
    assert m.wait.kind == "done"
    assert [t for _, _, t, _ in m.graph.texts] == ["123"]       # 240..263 holds 3 digits
    assert m.graph.clipped == [(10, 240, "12345", "123")]
    m = run('Text(10,2,"12345")')
    assert [t for _, _, t, _ in m.graph.texts] == ["12345"] and m.graph.clipped == []


def test_a_line_far_off_the_screen_is_clipped_not_drawn_forever():
    m = run("Line(0,0,ᴇ50,0)")
    assert m.wait.kind == "done"
    assert all(0 <= x < 265 and 0 <= y < 165 for x, y in m.graph.pixels)
