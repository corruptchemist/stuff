"""Solver behaviour: it must narrow correctly, and it must always finish."""

import random

import pytest

from wordlebot.game import play, won
from wordlebot.patterns import ALL_GREEN, score
from wordlebot.solver import Solver, Unsolvable
from wordlebot.words import allowed, answers


@pytest.fixture(scope="module")
def solver():
    return Solver()


def test_opening_state(solver):
    solver.reset()
    assert len(solver.candidates) == len(answers())
    assert solver.suggest() == solver.opener
    assert solver.turns_left == 6


def test_observe_narrows_to_consistent_words(solver):
    solver.reset()
    solver.observe("salet", score("salet", "pilot"))
    cands = solver.candidates
    assert "pilot" in cands
    # Every survivor must reproduce the feedback we actually saw.
    for word in cands:
        assert score("salet", word) == score("salet", "pilot")


def test_suggestion_is_always_a_legal_word(solver):
    rng = random.Random(3)
    pool = set(allowed())
    for answer in rng.sample(answers(), 15):
        solver.reset()
        for _ in range(5):
            guess = solver.suggest()
            assert guess in pool
            pattern = score(guess, answer)
            if pattern == ALL_GREEN:
                break
            solver.observe(guess, pattern)


def test_contradictory_feedback_is_reported(solver):
    """Mistyped colours should say so, not silently suggest nonsense."""
    solver.reset()
    solver.observe("salet", 0)  # every letter absent...
    with pytest.raises(Unsolvable):
        solver.observe("salet", ALL_GREEN)  # ...and now every letter correct


def test_falls_back_when_answer_is_outside_the_curated_pool():
    """Clones use wider answer lists; being outside ours must not be fatal."""
    off_pool = next(w for w in allowed() if w not in set(answers()))
    solver = Solver()
    for _ in range(6):
        guess = solver.suggest()
        pattern = score(guess, off_pool)
        if pattern == ALL_GREEN:
            break
        solver.observe(guess, pattern)
    else:
        pytest.fail(f"never solved off-pool word {off_pool}")
    assert off_pool in solver.candidates or guess == off_pool


def test_solves_a_sample_within_six(solver):
    rng = random.Random(11)
    for answer in rng.sample(answers(), 60):
        played = play(answer, solver)
        assert won(played), f"failed on {answer}"
        assert len(played) <= 6
        assert played[-1][0] == answer


def test_hard_words_still_finish_in_five():
    """The -OVER/-OKER family is the classic six-guess trap."""
    solver = Solver()
    for answer in ("hover", "joker", "rover", "poker", "woody", "boxer"):
        played = play(answer, solver)
        assert won(played) and len(played) <= 5, f"{answer} took {len(played)}"
