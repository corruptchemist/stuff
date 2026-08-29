"""Wordle feedback patterns.

A pattern is packed into one base-3 integer: digit i (least significant first)
holds the colour of position i, using GREY/YELLOW/GREEN below. That makes a
pattern a number in [0, 243) which indexes an array directly.
"""

from __future__ import annotations

from collections import Counter
from pathlib import Path

import numpy as np

from .words import NUM_PATTERNS, WORD_LEN, encode

GREY, YELLOW, GREEN = 0, 1, 2
ALL_GREEN = NUM_PATTERNS - 1  # 242 -- five greens

_POW3 = (3 ** np.arange(WORD_LEN)).astype(np.uint16)

# Characters accepted when a human types a pattern in.
_CHAR_TO_COLOUR = {
    "g": GREEN, "G": GREEN, "2": GREEN, "*": GREEN,
    "y": YELLOW, "Y": YELLOW, "1": YELLOW, "?": YELLOW,
    "b": GREY, "B": GREY, "x": GREY, "X": GREY, "0": GREY, "-": GREY,
    ".": GREY, "_": GREY, "w": GREY, "W": GREY,
}
_COLOUR_TO_CHAR = {GREY: "b", YELLOW: "y", GREEN: "g"}


def score(guess: str, answer: str) -> int:
    """Colour `guess` against `answer`, exactly as Wordle does.

    Greens are assigned first, then yellows draw from whatever letters are
    left over. That two-pass order is what makes duplicate letters behave:
    guessing "geese" against "elder" yields one yellow e, not three.
    """
    colours = [GREY] * WORD_LEN
    remaining = Counter(answer)
    for i, (g, a) in enumerate(zip(guess, answer)):
        if g == a:
            colours[i] = GREEN
            remaining[g] -= 1
    for i, g in enumerate(guess):
        if colours[i] == GREY and remaining[g] > 0:
            colours[i] = YELLOW
            remaining[g] -= 1
    return sum(c * 3**i for i, c in enumerate(colours))


def pattern_to_string(pattern: int) -> str:
    """Render a packed pattern as five characters, e.g. 'bygbg'."""
    return "".join(_COLOUR_TO_CHAR[(pattern // 3**i) % 3] for i in range(WORD_LEN))


def parse_pattern(text: str) -> int:
    """Parse a typed pattern such as 'bygbg', '01201' or '..y.g'."""
    cleaned = "".join(text.split())
    if len(cleaned) != WORD_LEN:
        raise ValueError(f"pattern must be {WORD_LEN} characters, got {len(cleaned)!r}")
    try:
        colours = [_CHAR_TO_COLOUR[c] for c in cleaned]
    except KeyError as exc:
        raise ValueError(f"unrecognised colour character {exc.args[0]!r}") from None
    return sum(c * 3**i for i, c in enumerate(colours))


def build_matrix(guess_words, answer_words, chunk: int = 512, progress=None) -> np.ndarray:
    """Return a (len(guess_words), len(answer_words)) uint8 array of patterns.

    Vectorised over a chunk of guesses at a time; the intermediate equality
    tensor is (chunk, answers, 5, 5) booleans, so `chunk` trades memory for
    speed. Mirrors `score` exactly -- tests assert that on random pairs.
    """
    guesses = encode(guess_words)
    targets = encode(answer_words)
    n_guess, n_answer = len(guesses), len(targets)
    out = np.empty((n_guess, n_answer), dtype=np.uint8)

    for start in range(0, n_guess, chunk):
        block = guesses[start : start + chunk]
        # eq[g, a, i, j] -- does guess position i match answer position j?
        eq = block[:, None, :, None] == targets[None, :, None, :]
        colours = np.zeros((block.shape[0], n_answer, WORD_LEN), dtype=np.uint8)

        for i in range(WORD_LEN):  # greens consume both their positions
            hit = eq[:, :, i, i].copy()
            colours[:, :, i] = hit.astype(np.uint8) * GREEN
            eq[:, :, i, :] &= ~hit[:, :, None]
            eq[:, :, :, i] &= ~hit[:, :, None]

        for i in range(WORD_LEN):  # then yellows, left to right
            for j in range(WORD_LEN):
                hit = eq[:, :, i, j].copy()
                if not hit.any():
                    continue
                colours[:, :, i] += hit.astype(np.uint8) * YELLOW
                eq[:, :, i, :] &= ~hit[:, :, None]
                eq[:, :, :, j] &= ~hit[:, :, None]

        out[start : start + chunk] = (colours * _POW3).sum(axis=2).astype(np.uint8)
        if progress:
            progress(min(start + chunk, n_guess), n_guess)
    return out


def load_matrix(guess_words, answer_words, cache: Path | None, progress=None) -> np.ndarray:
    """Load the pattern matrix from `cache`, building and saving it if absent."""
    if cache is not None and cache.exists():
        matrix = np.load(cache)
        if matrix.shape == (len(guess_words), len(answer_words)):
            return matrix
    matrix = build_matrix(guess_words, answer_words, progress=progress)
    if cache is not None:
        cache.parent.mkdir(parents=True, exist_ok=True)
        np.save(cache, matrix)
    return matrix
