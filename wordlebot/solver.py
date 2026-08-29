"""Wordle solver: greedy information gain, with exact search for the endgame."""

from __future__ import annotations

import math
import sys
import time
from pathlib import Path

import numpy as np

from .patterns import ALL_GREEN, build_matrix, load_matrix, score
from .words import NUM_PATTERNS, allowed, answers

CACHE_DIR = Path(__file__).parent.parent / ".cache"


def cache_path(n_guess: int, n_answer: int) -> Path:
    """Pattern matrices are cached per pool-size pair."""
    return CACHE_DIR / f"patterns_{n_guess}x{n_answer}.npy"


# Chosen by measurement, not folklore: `python -m wordlebot.benchmark --openers`.
DEFAULT_OPENER = "salet"

# Below this many candidates we stop trusting entropy and search exactly.
EXACT_THRESHOLD = 12
# When guesses are running out, search exactly over a much larger candidate set:
# entropy maximises information, which is not the same as guaranteeing a finish.
ENDGAME_THRESHOLD = 26
ENDGAME_TURNS = 4
# How many high-entropy non-candidate probe words the exact search may use.
# Measured: 14 probes leaves four answers needing all six guesses; 120 removes
# them entirely, for 0.002 guesses on the mean. A hard ceiling is worth more.
EXACT_PROBES = 120
INF = float("inf")


def _progress_printer():
    """Report table-building progress, so a 30s wait does not look like a hang."""
    start = time.time()

    def report(done: int, total: int) -> None:
        frac = done / total
        filled = int(frac * 28)
        elapsed = time.time() - start
        if done >= total:
            sys.stderr.write(f"\r  [{'#' * 28}]  done in {elapsed:.0f}s" + " " * 14 + "\n")
        else:
            eta = elapsed / frac - elapsed if frac else 0
            sys.stderr.write(
                f"\r  [{'#' * filled}{'.' * (28 - filled)}] {frac:4.0%}"
                f"  about {eta:.0f}s left "
            )
        sys.stderr.flush()

    return report


def entropies(block: np.ndarray) -> np.ndarray:
    """Expected bits of information for each guess, given a candidate block.

    `block[r, c]` is the pattern guess r produces against candidate c. A guess
    is good when it splits the candidates into many, evenly sized buckets.
    """
    n_guess, n_cand = block.shape
    if n_cand == 0:
        return np.zeros(n_guess)
    out = np.empty(n_guess)
    # Chunked so the opening move (14855 x 2315 patterns) does not spike memory.
    step = max(1, 4_000_000 // max(n_cand, 1))
    for lo in range(0, n_guess, step):
        chunk = block[lo : lo + step]
        rows = chunk.shape[0]
        # One bincount per chunk: offset each row into its own 243 bins.
        offsets = chunk.astype(np.int32) + NUM_PATTERNS * np.arange(rows, dtype=np.int32)[:, None]
        counts = np.bincount(offsets.ravel(), minlength=NUM_PATTERNS * rows)
        counts = counts.reshape(rows, NUM_PATTERNS)
        probs = counts / n_cand
        with np.errstate(divide="ignore", invalid="ignore"):
            terms = np.where(counts > 0, probs * np.log2(probs), 0.0)
        out[lo : lo + step] = -terms.sum(axis=1)
    return out


class Unsolvable(RuntimeError):
    """Raised when the feedback given is inconsistent with every known word."""


class Solver:
    """Tracks the candidate set and recommends guesses.

    The answer pool starts as the curated Wordle solution list because that
    makes for much sharper guesses. If feedback ever rules out every word in
    it -- a clone using a wider pool -- the solver silently falls back to the
    full accepted-guess list rather than giving up.
    """

    def __init__(
        self,
        answer_pool=None,
        guess_pool=None,
        opener: str | None = DEFAULT_OPENER,
        exact_threshold: int = EXACT_THRESHOLD,
        matrix: np.ndarray | None = None,
        max_guesses: int = 6,
    ):
        self.answer_pool = tuple(answer_pool) if answer_pool else answers()
        self.guess_pool = tuple(guess_pool) if guess_pool else allowed()
        self.opener = opener
        self.exact_threshold = exact_threshold
        self.max_guesses = max_guesses
        self._row_of = {w: i for i, w in enumerate(self.guess_pool)}
        if matrix is None:
            path = cache_path(len(self.guess_pool), len(self.answer_pool))
            progress = None
            if not path.exists():
                print(
                    f"First run: building the {len(self.guess_pool)} x "
                    f"{len(self.answer_pool)} guess/answer table.\n"
                    "This happens once -- it is cached afterwards, so every later "
                    "run starts instantly.\nDon't interrupt it.",
                    file=sys.stderr, flush=True,
                )
                progress = _progress_printer()
            matrix = load_matrix(self.guess_pool, self.answer_pool, path, progress=progress)
        self.matrix = matrix
        # Row in the guess pool for each word in the answer pool.
        self._answer_row = np.array([self._row_of[w] for w in self.answer_pool], dtype=np.int32)
        self.reset()

    # -- state ---------------------------------------------------------------

    def reset(self) -> None:
        self.history: list[tuple[str, int]] = []
        self._cols = np.arange(len(self.answer_pool), dtype=np.int32)
        self._words: list[str] | None = None  # set only after falling back

    @property
    def turns_left(self) -> int:
        return self.max_guesses - len(self.history)

    @property
    def candidates(self) -> list[str]:
        if self._words is not None:
            return list(self._words)
        return [self.answer_pool[c] for c in self._cols]

    def _block(self) -> np.ndarray:
        """Patterns of every allowed guess against every live candidate."""
        if self._words is not None:
            return build_matrix(self.guess_pool, self._words)
        return self.matrix[:, self._cols]

    def observe(self, guess: str, pattern: int) -> None:
        """Record real feedback and narrow the candidate set."""
        guess = guess.lower()
        self.history.append((guess, pattern))
        if self._words is None:
            row = self._row_of.get(guess)
            if row is not None:
                self._cols = self._cols[self.matrix[row, self._cols] == pattern]
                if len(self._cols):
                    return
            self._fall_back()
        else:
            self._words = [w for w in self._words if score(guess, w) == pattern]
            if not self._words:
                raise Unsolvable("no word matches the feedback given so far")

    def _fall_back(self) -> None:
        """Re-derive candidates from the full accepted-guess list."""
        self._words = [
            w for w in self.guess_pool if all(score(g, w) == p for g, p in self.history)
        ]
        self._cols = np.empty(0, dtype=np.int32)
        if not self._words:
            raise Unsolvable("no word matches the feedback given so far")

    # -- guess selection -----------------------------------------------------

    def suggest(self) -> str:
        """The single best next guess."""
        return self.ranked(1)[0][0]

    def ranked(self, top: int = 5) -> list[tuple[str, float]]:
        """Top guesses as (word, expected bits) pairs, best first."""
        cands = self.candidates
        n = len(cands)
        if n == 0:
            raise Unsolvable("no candidates remain")
        if n <= 2:
            return [(w, 0.0) for w in cands[:top]]
        if not self.history and self.opener and top <= 1:
            return [(self.opener, 0.0)]

        block = self._block()
        ent = entropies(block)

        if not self.history and self.opener:
            # Opener first, then real alternatives in case a site rejects it.
            rest = [r for r in self._by_entropy(ent, top + 1) if r[0] != self.opener]
            return [(self.opener, float(ent[self._row_of[self.opener]]))] + rest[: top - 1]

        # With plenty of turns in hand, entropy is both faster and near-optimal.
        # With few, a line that maximises information can still strand us, so we
        # widen the exact search and let it prune anything that cannot finish.
        turns = max(self.turns_left, 1)
        threshold = self.exact_threshold
        if turns <= ENDGAME_TURNS:
            threshold = max(threshold, ENDGAME_THRESHOLD)
        if n <= threshold:
            best = self._exact_best(block, ent, turns)
            if best is not None:
                rest = [(w, e) for w, e in self._by_entropy(ent, top + 1) if w != best]
                return [(best, float(ent[self._row_of[best]]))] + rest[: top - 1]
        return self._by_entropy(ent, top, prefer=set(cands))

    def _by_entropy(self, ent, top, prefer=None):
        order = np.argsort(-ent)[: max(top, 40)]
        scored = [(self.guess_pool[i], float(ent[i])) for i in order]
        if prefer:
            # A candidate might win outright; break near-ties in its favour.
            best = scored[0][1]
            for i, (w, e) in enumerate(scored):
                if w in prefer and best - e <= 0.02 and i > 0:
                    scored.insert(0, scored.pop(i))
                    break
        return scored[:top]

    def _exact_best(self, block, ent, turns: int) -> str | None:
        """Minimise expected guesses over a small candidate set, exactly.

        `turns` bounds the search depth, so a line that cannot finish inside the
        remaining guesses scores INF and loses to one that can.
        """
        cols = self._live_cols()
        if cols is None:
            return None
        probes = tuple(int(i) for i in np.argsort(-ent)[:EXACT_PROBES])
        cache: dict = {}
        # Prefer a line that finishes with a turn still in hand -- but only if one
        # exists, so this never trades away a game we could otherwise have won.
        if turns >= 2:
            expected, row = self._search(cols, min(turns - 1, 5), probes, cache)
            if row is not None and expected != INF:
                return self.guess_pool[row]
        expected, row = self._search(cols, min(turns, 5), probes, cache)
        return None if row is None or expected == INF else self.guess_pool[row]

    def _live_cols(self):
        """Candidate columns into the pattern matrix, if we are still on-pool."""
        return tuple(int(c) for c in self._cols) if self._words is None else None

    def _search(self, cols: tuple, depth: int, probes: tuple, cache: dict):
        """Return (expected guesses to finish, guess row) for this candidate set."""
        n = len(cols)
        if n == 1:
            return (1.0, int(self._answer_row[cols[0]])) if depth >= 1 else (INF, None)
        if n == 2:
            # One guess picks a side; the loser needs a second. Two turns minimum.
            return (1.5, int(self._answer_row[cols[0]])) if depth >= 2 else (INF, None)
        key = (cols, depth)
        if key in cache:
            return cache[key]
        if depth <= 1:
            return INF, None  # cannot separate 3+ candidates with one guess left

        col_arr = np.array(cols, dtype=np.int32)
        # Try the candidates first (they can win immediately), then pure probes.
        tries = list(dict.fromkeys([int(self._answer_row[c]) for c in cols] + list(probes)))

        best, best_row = INF, None
        for row in tries:
            pats = self.matrix[row, col_arr]
            total, feasible = 0.0, True
            for pat in np.unique(pats):
                if pat == ALL_GREEN:
                    continue  # solved by this guess; costs nothing further
                sub = tuple(int(c) for c in col_arr[pats == pat])
                if len(sub) == n:
                    feasible = False  # this guess learns nothing here
                    break
                sub_exp, _ = self._search(sub, depth - 1, probes, cache)
                if sub_exp == INF:
                    feasible = False
                    break
                total += len(sub) * sub_exp
                if 1.0 + total / n >= best:
                    feasible = False  # already worse than the best so far
                    break
            if not feasible:
                continue
            expected = 1.0 + total / n
            if expected < best:
                best, best_row = expected, row
                if math.isclose(best, 1.0 + (n - 1) / n):
                    break  # provably optimal: solve now or next guess
        cache[key] = (best, best_row)
        return best, best_row
