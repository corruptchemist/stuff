"""Self-play: run the solver against a known answer."""

from __future__ import annotations

from .patterns import ALL_GREEN, score
from .solver import Solver

MAX_GUESSES = 6

# Measured over all 2315 official answers: `python -m wordlebot.benchmark`.
# Used to rate a finished game against how the solver normally does.
SOLVE_DISTRIBUTION = {2: 94, 3: 1231, 4: 889, 5: 101}
MEAN_GUESSES = 3.4307
TOTAL_BITS = 11.1766  # log2(2315): the information a full game must uncover


def beat_fraction(guesses: int) -> float:
    """Fraction of games the solver finishes in MORE guesses than this one."""
    total = sum(SOLVE_DISTRIBUTION.values())
    return sum(v for k, v in SOLVE_DISTRIBUTION.items() if k > guesses) / total


def play(answer: str, solver: Solver | None = None, max_guesses: int = MAX_GUESSES,
         guess_cache: dict | None = None) -> list[tuple[str, int]]:
    """Play one game; return the [(guess, pattern), ...] actually played.

    The game is won when the final pattern is ALL_GREEN. `guess_cache` memoises
    recommendations by history path -- the solver is deterministic, so replaying
    thousands of games shares almost all of its work.
    """
    solver = solver or Solver()
    solver.reset()
    played: list[tuple[str, int]] = []
    for _ in range(max_guesses):
        path = tuple(played)
        if guess_cache is not None and path in guess_cache:
            guess = guess_cache[path]
        else:
            guess = solver.suggest()
            if guess_cache is not None:
                guess_cache[path] = guess
        pattern = score(guess, answer)
        played.append((guess, pattern))
        if pattern == ALL_GREEN:
            return played
        solver.observe(guess, pattern)
    return played


def won(played: list[tuple[str, int]]) -> bool:
    return bool(played) and played[-1][1] == ALL_GREEN
