"""Track a live Flip 7 table from BGA's notification stream.

The whole design turns on one observation from a live table: every physical card
carries a stable `id` (1..94), and BGA reveals its `materialId` the moment it
leaves the deck. So identities, once learned, are known forever -- including
after the discard pile is shuffled back in. That is what makes counting exact
across a reshuffle rather than merely approximate.

Deck composition is therefore never guessed:

    remaining = (full 94-card deck  -  every identity ever revealed)
              +  identities of revealed cards that are back in the deck

The first term covers cards never yet seen; the second covers reshuffled ones.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field

from .deck import CARDS, DECK_SIZE, NUMBER, SECOND_CHANCE, card, full_deck

# BGA's `location` values, observed live.
DECK, DISCARD, PLAYER, WAIT = "deck", "deck2", "player", "wait"
# A card mid-animation is already revealed but not yet placed.
OUT_OF_DECK = {DISCARD, PLAYER, WAIT}

BUSTED, STAYED, FROZEN, FIRST_TURN = "BUSTED", "STAYED", "FREEZED", "FIRST_TURN"


@dataclass
class Player:
    no: str
    player_id: str = ""
    name: str = ""
    score: int = 0
    status: str | None = None
    cards: list[int] = field(default_factory=list)  # card ids, in draw order

    @property
    def out(self) -> bool:
        return self.status in (BUSTED, STAYED, FROZEN)


class Table:
    """Mirror of the table, fed by moveTokens / updatePlayers notifications."""

    def __init__(self):
        self.identity: dict[str, int] = {}          # card id -> material id
        self.location: dict[str, str] = {}          # card id -> location
        self.location_id: dict[str, str] = {}       # card id -> whose, if a player
        self.players: dict[str, Player] = {}        # player "no" -> Player
        self.by_id: dict[str, Player] = {}          # BGA player id -> Player
        self.me: str | None = None                  # my player "no"
        self.rounds_seen = 0
        self.reshuffles = 0
        self.events = 0
        self._last_seen: dict[str, str] = {}

    # -- ingest --------------------------------------------------------------

    def seed(self, gamedatas: dict, my_player_id: str | None = None) -> None:
        """Seed from gameui.gamedatas, and remember who we are."""
        self.sync(gamedatas)
        if my_player_id is not None and str(my_player_id) in self.by_id:
            self.me = self.by_id[str(my_player_id)].no

    def sync(self, gamedatas: dict) -> None:
        """Adopt a gamedatas snapshot wholesale as the authoritative state.

        BGA keeps `board.cards` current for this game, so re-reading it each
        tick beats accumulating notifications: nothing can drift, a missed or
        duplicated event cannot corrupt the count, and a round boundary or a
        reshuffle needs no special handling -- the tableau simply empties.

        Identities are never unlearned. A card returning to the deck has its
        materialId hidden again, but we already saw it, and remembering that is
        exactly what makes the count exact after a reshuffle.
        """
        cards = (gamedatas.get("board") or {}).get("cards") or []
        if cards:
            self.location.clear()
            self.location_id.clear()
        for c in cards:
            cid = str(c["id"])
            self.location[cid] = c.get("location") or DECK
            if c.get("materialId") is not None:
                self.identity[cid] = int(c["materialId"])
            if c.get("location") == PLAYER and c.get("locationId") is not None:
                self.location_id[cid] = str(c["locationId"])
        for pid, info in (gamedatas.get("players") or {}).items():
            p = self._player(str(info.get("no")), str(pid), info.get("name", ""))
            if "status" in info:
                p.status = info.get("status")
            try:
                p.score = int(info.get("score") or 0)
            except (TypeError, ValueError):
                pass
        self._rebuild_hands()

    def _player(self, no: str, pid: str = "", name: str = "") -> Player:
        p = self.players.get(no)
        if p is None:
            p = self.players[no] = Player(no=no, player_id=pid, name=name)
        if pid:
            p.player_id, self.by_id[pid] = pid, p
        if name:
            p.name = name
        return p

    def handle(self, ntype: str, args: dict) -> None:
        """Feed one notification. Unknown types are ignored."""
        self.events += 1
        if ntype == "moveTokens":
            self._move_tokens(args or {})
        elif ntype == "updatePlayers":
            self._update_players(args or {})

    def _move_tokens(self, args: dict) -> None:
        tokens = args.get("tokens") or []
        # A whole round's cards sweeping to the discard at once is the round end;
        # single discards happen mid-round when a Second Chance is spent.
        bulk_discard = len(tokens) > 1 and all(
            t.get("location") == DISCARD for t in tokens)
        for t in tokens:
            cid, loc = str(t.get("id")), t.get("location")
            if t.get("materialId") is not None:
                self.identity[cid] = int(t["materialId"])
            prev = self.location.get(cid)
            self.location[cid] = loc
            if loc == PLAYER and t.get("locationId") is not None:
                self.location_id[cid] = str(t["locationId"])
            elif loc != PLAYER:
                self.location_id.pop(cid, None)
            # Discard -> deck is the reshuffle: the pile goes back under.
            if prev == DISCARD and loc == DECK:
                self.reshuffles += 1
        if bulk_discard:
            self.rounds_seen += 1
        self._rebuild_hands()

    def note_transitions(self, args: dict) -> None:
        """Count round ends and reshuffles from a moveTokens event.

        The snapshot shows where every card *is*, never how it got there, so
        these two transitions are read from the event stream instead: a whole
        tableau sweeping to the discard at once is a round ending, and a card
        coming back out of the discard is the pile being shuffled under.
        """
        tokens = args.get("tokens") or []
        self.events += 1
        if len(tokens) > 1 and all(t.get("location") == DISCARD for t in tokens):
            self.rounds_seen += 1
        for t in tokens:
            cid = str(t.get("id"))
            if t.get("location") == DECK and self._last_seen.get(cid) == DISCARD:
                self.reshuffles += 1
            self._last_seen[cid] = t.get("location")

    def _update_players(self, args: dict) -> None:
        for pid, info in (args.get("players") or {}).items():
            p = self._player(str(info.get("no")), str(pid), info.get("name", ""))
            p.status = info.get("status")
            try:
                p.score = int(info.get("score") or 0)
            except (TypeError, ValueError):
                pass

    def _rebuild_hands(self) -> None:
        for p in self.players.values():
            p.cards = []
        for cid, loc in self.location.items():
            if loc != PLAYER:
                continue
            no = self.location_id.get(cid)
            if no is not None:
                self._player(no).cards.append(cid)
        for p in self.players.values():
            p.cards.sort(key=lambda c: int(c))

    # -- derived state -------------------------------------------------------

    def remaining(self) -> Counter:
        """Exact composition of the draw pile, as {material_id: count}."""
        counts = Counter(full_deck())
        counts.subtract(Counter(self.identity.values()))  # everything ever revealed
        for cid, mid in self.identity.items():            # ...but reshuffled ones return
            if self.location.get(cid) == DECK:
                counts[mid] += 1
        return +counts  # drop zero/negative entries

    def deck_size(self) -> int:
        known = sum(1 for c in self.location.values() if c == DECK)
        # Cards never mentioned at all are still down there too.
        return known + max(0, DECK_SIZE - len(self.location))

    def hand(self, no: str) -> list:
        return [card(self.identity[c]) for c in self.players[no].cards
                if c in self.identity]

    def numbers_held(self, no: str) -> set[int]:
        return {c.value for c in self.hand(no) if c.kind == NUMBER}

    def has_second_chance(self, no: str) -> bool:
        return any(c.label == SECOND_CHANCE for c in self.hand(no))

    def round_score(self, no: str) -> int:
        """Score if this player stayed now: numbers x2 (if held) then + modifiers."""
        hand = self.hand(no)
        total = sum(c.value for c in hand if c.kind == NUMBER)
        if any(c.multiplier == 2 for c in hand):
            total *= 2
        total += sum(c.value for c in hand if c.kind == "modifier" and c.multiplier == 1)
        if len({c.value for c in hand if c.kind == NUMBER}) == 7:
            total += 15
        return total
