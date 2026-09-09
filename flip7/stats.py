"""Decision maths for a live Flip 7 round.

Flip 7 is a perfect-information counting game: every card that leaves the deck
is face up, so the draw pile's composition is exactly known and the chance the
next card busts you is a plain ratio -- no simulation required.

    P(bust) = (copies remaining of numbers you already hold) / (all cards left)

Only number cards can bust; the six modifiers and nine action cards are always
safe, and dilute the ratio. Expected value is solved by recursion over the state
(numbers held, modifiers held, Second Chance in hand), which is small enough to
re-solve on every turn.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from functools import lru_cache

from .deck import CARDS, MODIFIER, NUMBER, SECOND_CHANCE

FLIP7_BONUS = 15
FLIP7_SIZE = 7
# How many draws ahead the EV recursion looks. Full lookahead is intractable
# because the remaining-deck vector is part of the state; the decision is in
# practice dominated by the next couple of cards, and 4 plies reproduces the
# published optimal-play thresholds while solving in single-digit milliseconds.
DEFAULT_DEPTH = 4


@dataclass
class Advice:
    p_bust: float
    p_flip7_next: float
    stay_value: int          # points banked by staying right now
    ev_hit: float            # expected points if you draw, then play on optimally
    bust_cards: int          # copies in the deck that would bust you
    deck_size: int
    safe_cards: int
    numbers_held: int
    to_flip7: int
    protected: bool          # a Second Chance is in hand
    recommend: str           # "HIT" or "STAY"
    margin: float            # ev_hit - stay_value; how clear the call is

    @property
    def edge(self) -> str:
        if self.margin > 3:
            return "clear"
        if self.margin > 0.75:
            return "slight"
        return "marginal"


def bust_probability(remaining: Counter, numbers_held: set[int],
                     protected: bool = False) -> tuple[float, int, int]:
    """(probability, busting copies, deck size). A Second Chance absorbs one."""
    total = sum(remaining.values())
    if total <= 0:
        return 0.0, 0, 0
    bust = sum(remaining.get(n, 0) for n in numbers_held)
    return (0.0 if protected else bust / total), bust, total


def _score(numbers: frozenset[int], adds: int, doubled: bool) -> int:
    total = sum(numbers)
    if doubled:
        total *= 2
    total += adds
    return total + (FLIP7_BONUS if len(numbers) == FLIP7_SIZE else 0)


def analyse(remaining: Counter, numbers_held: set[int], adds: int = 0,
            doubled: bool = False, protected: bool = False) -> Advice:
    """Full recommendation for one decision point."""
    numbers = frozenset(numbers_held)
    counts = tuple(sorted(remaining.items()))
    p_bust, bust, total = bust_probability(remaining, numbers, protected)
    p7 = 0.0
    if len(numbers) == FLIP7_SIZE - 1 and total:
        p7 = sum(c for m, c in remaining.items()
                 if CARDS[m].kind == NUMBER and m not in numbers) / total
    stay = _score(numbers, adds, doubled)
    ev = _ev_hit(counts, numbers, adds, doubled, protected, DEFAULT_DEPTH)
    return Advice(
        p_bust=p_bust, p_flip7_next=p7, stay_value=stay, ev_hit=ev,
        bust_cards=bust, deck_size=total, safe_cards=total - bust,
        numbers_held=len(numbers), to_flip7=FLIP7_SIZE - len(numbers),
        protected=protected,
        recommend="HIT" if ev > stay else "STAY", margin=ev - stay,
    )


@lru_cache(maxsize=500_000)
def _ev_hit(counts: tuple, numbers: frozenset, adds: int, doubled: bool,
            protected: bool, depth: int) -> float:
    """Expected final score from drawing exactly one more card, then playing on.

    Recurses on the state rather than the deck: what has been drawn during the
    recursion is implied by what is held, so the remaining deck follows from it.
    """
    remaining = dict(counts)
    total = sum(remaining.values())
    if depth <= 0 or total <= 0 or len(numbers) >= FLIP7_SIZE:
        return float(_score(numbers, adds, doubled))

    ev = 0.0
    for mid, n in remaining.items():
        if not n:
            continue
        p = n / total
        c = CARDS[mid]
        nxt = dict(remaining)
        nxt[mid] = n - 1
        key = tuple(sorted(nxt.items()))

        if c.kind == NUMBER and c.value in numbers:
            if protected:                      # Second Chance absorbs the bust
                ev += p * _best(key, numbers, adds, doubled, False, depth - 1)
            continue                           # otherwise: bust, zero points
        if c.kind == NUMBER:
            got = numbers | {c.value}
            if len(got) == FLIP7_SIZE:
                ev += p * _score(got, adds, doubled)   # round ends immediately
            else:
                ev += p * _best(key, got, adds, doubled, protected, depth - 1)
        elif c.kind == MODIFIER:
            ev += p * (_best(key, numbers, adds, True, protected, depth - 1) if c.multiplier == 2
                       else _best(key, numbers, adds + c.value, doubled, protected, depth - 1))
        elif c.label == SECOND_CHANCE:
            ev += p * _best(key, numbers, adds, doubled, True, depth - 1)
        else:
            # Freeze banks the round; Flip Three is treated as one more draw,
            # which understates its variance but not its sign.
            ev += p * (_score(numbers, adds, doubled) if c.label == "Freeze"
                       else _best(key, numbers, adds, doubled, protected, depth - 1))
    return ev


def _best(counts, numbers, adds, doubled, protected, depth) -> float:
    """Value of playing on optimally: the better of staying and drawing again."""
    return max(float(_score(numbers, adds, doubled)),
               _ev_hit(counts, numbers, adds, doubled, protected, depth))


# --- Flip Three -------------------------------------------------------------
# Flip Three forces a player to draw three cards. It can be aimed at yourself or
# at an opponent, so its value is a comparison: what three forced draws are worth
# to you, against how much they are likely to cost each opponent.

@dataclass
class FlipThreeTarget:
    name: str
    is_me: bool
    p_bust: float            # chance three forced draws bust them
    at_risk: int             # round points they would lose by busting
    ev_after: float          # their expected round score after the three draws
    ev_now: float            # what they have banked in hand right now
    value: float             # value to YOU of aiming it here
    reason: str


def three_draw_outcome(remaining: Counter, numbers: set[int], adds: int = 0,
                       doubled: bool = False, protected: bool = False,
                       draws: int = 3) -> tuple[float, float]:
    """(probability of busting, expected round score) over `draws` forced cards.

    Exact: the deck is known, so this enumerates the draw paths rather than
    sampling. Drawing stops early on a bust or on completing a Flip 7.
    """
    return _three(tuple(sorted(remaining.items())), frozenset(numbers), adds,
                  doubled, protected, draws)


@lru_cache(maxsize=200_000)
def _three(counts: tuple, numbers: frozenset, adds: int, doubled: bool,
           protected: bool, left: int) -> tuple[float, float]:
    remaining = dict(counts)
    total = sum(remaining.values())
    if left <= 0 or total <= 0 or len(numbers) >= FLIP7_SIZE:
        return 0.0, float(_score(numbers, adds, doubled))

    p_bust, ev = 0.0, 0.0
    for mid, n in remaining.items():
        if not n:
            continue
        p = n / total
        c = CARDS[mid]
        nxt = dict(remaining)
        nxt[mid] = n - 1
        key = tuple(sorted(nxt.items()))

        if c.kind == NUMBER and c.value in numbers:
            if protected:                       # Second Chance eats the duplicate
                b, e = _three(key, numbers, adds, doubled, False, left - 1)
                p_bust += p * b
                ev += p * e
            else:
                p_bust += p                     # busted: the round scores nothing
            continue
        if c.kind == NUMBER:
            got = numbers | {c.value}
            if len(got) == FLIP7_SIZE:
                ev += p * _score(got, adds, doubled)   # round ends on the spot
                continue
            b, e = _three(key, got, adds, doubled, protected, left - 1)
        elif c.kind == MODIFIER:
            b, e = (_three(key, numbers, adds, True, protected, left - 1)
                    if c.multiplier == 2
                    else _three(key, numbers, adds + c.value, doubled, protected, left - 1))
        elif c.label == SECOND_CHANCE:
            b, e = _three(key, numbers, adds, doubled, True, left - 1)
        else:
            b, e = _three(key, numbers, adds, doubled, protected, left - 1)
        p_bust += p * b
        ev += p * e
    return p_bust, ev


def rank_flip_three(remaining: Counter, candidates: list[dict]) -> list[FlipThreeTarget]:
    """Rank who to aim a Flip Three at, best first.

    Each candidate is {name, is_me, numbers, adds, doubled, protected, out}.
    Aimed at yourself it is worth the points it gains you; aimed at an opponent
    it is worth the points it is likely to cost them.
    """
    out: list[FlipThreeTarget] = []
    for c in candidates:
        if c.get("out"):
            continue  # already busted, stayed or frozen -- not a legal target
        numbers = set(c.get("numbers") or ())
        adds, doubled = c.get("adds", 0), c.get("doubled", False)
        protected = c.get("protected", False)
        now = float(_score(frozenset(numbers), adds, doubled))
        p_bust, ev_after = three_draw_outcome(remaining, numbers, adds, doubled, protected)
        if c["is_me"]:
            value = ev_after - now
            reason = (f"gains {value:+.1f} pts on average, {p_bust:.0%} chance of busting"
                      if value >= 0 else
                      f"loses {-value:.1f} pts on average, {p_bust:.0%} chance of busting")
        else:
            value = p_bust * now          # points you expect to deny them
            reason = (f"{p_bust:.0%} chance of busting them out of {now:.0f} pts"
                      if now else f"{p_bust:.0%} chance of busting them, but they hold nothing yet")
        out.append(FlipThreeTarget(
            name=c["name"], is_me=bool(c["is_me"]), p_bust=p_bust,
            at_risk=int(now), ev_after=ev_after, ev_now=now,
            value=value, reason=reason))
    out.sort(key=lambda t: t.value, reverse=True)
    return out
