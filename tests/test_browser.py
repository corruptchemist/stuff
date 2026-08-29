"""Board reading is colour-based, so the colour classifier carries the risk."""

import pytest

from wordlebot.browser import _pattern, colour_of
from wordlebot.patterns import GREEN, GREY, YELLOW, pattern_to_string

LIGHT_EMPTY = [255, 255, 255]
DARK_EMPTY = [18, 18, 19]


@pytest.mark.parametrize("rgb,baseline,expected", [
    ([106, 170, 100], LIGHT_EMPTY, GREEN),    # classic light green
    ([201, 180, 88], LIGHT_EMPTY, YELLOW),    # classic light yellow
    ([120, 124, 126], LIGHT_EMPTY, GREY),     # classic light grey
    ([83, 141, 78], DARK_EMPTY, GREEN),       # dark mode green
    ([181, 159, 59], DARK_EMPTY, YELLOW),     # dark mode yellow
    ([58, 58, 60], DARK_EMPTY, GREY),         # dark mode grey
])
def test_colour_classification(rgb, baseline, expected):
    assert colour_of({"bg": rgb, "marks": ""}, baseline) == expected


def test_unrevealed_tile_reads_as_none():
    assert colour_of({"bg": list(DARK_EMPTY), "marks": ""}, DARK_EMPTY) is None
    assert colour_of({"bg": list(LIGHT_EMPTY), "marks": ""}, LIGHT_EMPTY) is None


@pytest.mark.parametrize("marks,expected", [
    ("tile correct", GREEN),
    ("tile present", YELLOW),
    ("tile absent", GREY),
    ("letter green reveal", GREEN),
    ("box misplaced", YELLOW),
    ("cell wrong", GREY),
])
def test_class_and_data_attributes_win_over_colour(marks, expected):
    # Colour says grey; the markup says otherwise and should be believed.
    assert colour_of({"bg": [128, 128, 128], "marks": marks}, None) == expected


def test_row_pattern_assembly():
    row = [
        {"bg": [106, 170, 100], "marks": ""},   # green
        {"bg": [120, 124, 126], "marks": ""},   # grey
        {"bg": [201, 180, 88], "marks": ""},    # yellow
        {"bg": [120, 124, 126], "marks": ""},   # grey
        {"bg": [106, 170, 100], "marks": ""},   # green
    ]
    assert pattern_to_string(_pattern(row, LIGHT_EMPTY)) == "gbybg"


def test_partially_revealed_row_is_not_read_early():
    """Tiles flip one at a time; reading mid-animation would corrupt the state."""
    row = [{"bg": [106, 170, 100], "marks": ""}] * 3 + [{"bg": LIGHT_EMPTY, "marks": ""}] * 2
    assert _pattern(row, LIGHT_EMPTY) is None
