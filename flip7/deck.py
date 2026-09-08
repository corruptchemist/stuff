"""The Flip 7 deck, and BGA's encoding of it.

materialId is BGA's card-type id. The mapping below was derived from a live
table, not from documentation:

  * materialId 9 arrived in the same event as a log row showing `sprite-c9`,
    and 20 alongside `sprite-sf3` -- fixing numbers at 0-12 and Flip Three at 20.
  * A card with materialId 19 was followed immediately by that player's status
    turning FREEZED, three separate times -> 19 is Freeze.
  * A player holding materialId 6 drew a second materialId 6; instead of
    busting, that card AND their materialId 21 both moved to the discard in the
    same instant -> 21 is Second Chance, spent to survive a duplicate.

13-18 (the modifiers) follow the same contiguous ordering and are marked
CONFIRMED only where actually observed; see MODIFIER_CONFIDENCE.
"""

from __future__ import annotations

from dataclasses import dataclass

NUMBER, MODIFIER, ACTION = "number", "modifier", "action"

FREEZE, FLIP_THREE, SECOND_CHANCE = "Freeze", "Flip Three", "Second Chance"


@dataclass(frozen=True)
class Card:
    material_id: int
    kind: str
    label: str
    value: int = 0        # pip value for numbers, added value for + modifiers
    multiplier: int = 1   # 2 for the x2 card
    sprite: str = ""

    @property
    def is_number(self) -> bool:
        return self.kind == NUMBER

    @property
    def busts(self) -> bool:
        """Only a duplicate number can bust you; nothing else ever does."""
        return self.kind == NUMBER


def _build() -> dict[int, Card]:
    cards: dict[int, Card] = {}
    for n in range(13):  # 0..12 -> numbers, materialId == the number itself
        cards[n] = Card(n, NUMBER, str(n), value=n, sprite=f"sprite-c{n}")
    for mid, add in ((13, 2), (14, 4), (15, 6), (16, 8), (17, 10)):
        cards[mid] = Card(mid, MODIFIER, f"+{add}", value=add, sprite=f"sprite-s{add}")
    cards[18] = Card(18, MODIFIER, "x2", multiplier=2, sprite="sprite-sx2")
    cards[19] = Card(19, ACTION, FREEZE, sprite="sprite-sf")
    cards[20] = Card(20, ACTION, FLIP_THREE, sprite="sprite-sf3")
    cards[21] = Card(21, ACTION, SECOND_CHANCE, sprite="sprite-sch")
    return cards


CARDS: dict[int, Card] = _build()

# How many of each material id the 94-card deck holds: one 0, N copies of N,
# one of each modifier, three of each action.
COUNTS: dict[int, int] = (
    {0: 1} | {n: n for n in range(1, 13)} | {m: 1 for m in range(13, 19)}
    | {a: 3 for a in (19, 20, 21)}
)
DECK_SIZE = sum(COUNTS.values())  # 94

SPRITE_TO_MATERIAL: dict[str, int] = {c.sprite: mid for mid, c in CARDS.items()}

# Observed on a live table; the rest are inferred from the contiguous ordering.
CONFIRMED_MATERIAL_IDS = {1, 3, 4, 6, 7, 8, 9, 10, 11, 12, 14, 15, 19, 20, 21}


def card(material_id) -> Card:
    return CARDS[int(material_id)]


def full_deck() -> dict[int, int]:
    """A fresh 94-card deck as {material_id: count}."""
    return dict(COUNTS)


assert DECK_SIZE == 94, DECK_SIZE
assert sum(1 for c in CARDS.values() if c.kind == NUMBER) == 13
assert sum(COUNTS[m] for m, c in CARDS.items() if c.kind == NUMBER) == 79
