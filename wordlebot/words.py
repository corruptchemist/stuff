"""Word lists and letter encoding."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

import numpy as np

DATA_DIR = Path(__file__).parent / "data"
WORD_LEN = 5
NUM_PATTERNS = 3**WORD_LEN  # 243 possible colourings of a five-letter guess


def _load(name: str) -> tuple[str, ...]:
    with open(DATA_DIR / name) as fh:
        return tuple(w for w in (line.strip().lower() for line in fh) if len(w) == WORD_LEN)


@lru_cache(maxsize=None)
def answers() -> tuple[str, ...]:
    """The 2315 words Wordle actually uses as solutions."""
    return _load("answers.txt")


@lru_cache(maxsize=None)
def allowed() -> tuple[str, ...]:
    """Every word Wordle accepts as a guess (a superset of answers())."""
    return _load("allowed.txt")


def encode(words) -> np.ndarray:
    """Pack words into an (n, WORD_LEN) uint8 array of 0-25 letter indices."""
    words = tuple(words)
    if not words:
        return np.empty((0, WORD_LEN), dtype=np.uint8)
    flat = np.frombuffer("".join(words).encode("ascii"), dtype=np.uint8)
    return (flat - ord("a")).reshape(len(words), WORD_LEN)
