"""The scorer is the foundation: if it is wrong, every guess after it is wrong."""

import random

import pytest

from wordlebot.patterns import (ALL_GREEN, build_matrix, parse_pattern,
                                pattern_to_string, score)
from wordlebot.words import allowed, answers


@pytest.mark.parametrize("guess,answer,expected", [
    ("crane", "crane", "ggggg"),
    ("crane", "brine", "bgbgg"),
    # Duplicate letters: yellows may only draw on letters greens did not consume.
    ("geese", "elder", "byybb"),   # answer has two e's -> two marks, not three
    ("speed", "abide", "bbyby"),   # one e in answer -> only the first e marks
    ("array", "radar", "yyygb"),
    ("sassy", "stars", "gyybb"),   # green s consumes one of the answer's two
    ("kayak", "kayak", "ggggg"),
    ("erase", "reeds", "yybyy"),   # answer has two e's, so both of the guess's e's mark
])
def test_known_scores(guess, answer, expected):
    assert pattern_to_string(score(guess, answer)) == expected


def test_self_score_is_all_green():
    for word in random.Random(1).sample(answers(), 200):
        assert score(word, word) == ALL_GREEN


def test_pattern_string_roundtrip():
    for pattern in range(243):
        assert parse_pattern(pattern_to_string(pattern)) == pattern


def test_pattern_parsing_accepts_common_notations():
    assert parse_pattern("bygbg") == parse_pattern("01202") == parse_pattern(".?g.g")
    with pytest.raises(ValueError):
        parse_pattern("gggg")     # too short
    with pytest.raises(ValueError):
        parse_pattern("ggggq")    # unknown colour


def test_matrix_matches_reference_scorer():
    """The vectorised builder is an optimisation; it must not change answers."""
    rng = random.Random(0)
    gs = rng.sample(allowed(), 120)
    ans = rng.sample(answers(), 120)
    matrix = build_matrix(gs, ans)
    for i, g in enumerate(gs):
        for j, a in enumerate(ans):
            assert matrix[i, j] == score(g, a), f"{g} vs {a}"


def test_matrix_matches_reference_on_repeated_letters():
    """Repeated letters are where a vectorised scorer is most likely to drift."""
    repeats = [w for w in allowed() if len(set(w)) < len(w)][:150]
    matrix = build_matrix(repeats, repeats)
    for i, g in enumerate(repeats):
        for j, a in enumerate(repeats):
            assert matrix[i, j] == score(g, a), f"{g} vs {a}"
