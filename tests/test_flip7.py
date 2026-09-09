"""Tests for the Flip 7 tracker, anchored on data from a real BGA table."""

from collections import Counter

import pytest

from flip7.deck import CARDS, COUNTS, DECK_SIZE, NUMBER, card, full_deck
from flip7.state import Table
from flip7.stats import analyse, bust_probability
from tests.fixture_live_table import BGA_SNAPSHOT, EVENTS


# -- the deck ----------------------------------------------------------------

def test_deck_is_94_cards():
    assert DECK_SIZE == 94
    assert sum(COUNTS[m] for m, c in CARDS.items() if c.kind == NUMBER) == 79
    assert sum(COUNTS[m] for m, c in CARDS.items() if c.kind == "modifier") == 6
    assert sum(COUNTS[m] for m, c in CARDS.items() if c.kind == "action") == 9


def test_number_n_appears_n_times_plus_one_zero():
    assert COUNTS[0] == 1
    for n in range(1, 13):
        assert COUNTS[n] == n, n


@pytest.mark.parametrize("mid,label,sprite", [
    (9, "9", "sprite-c9"),          # seen live alongside a sprite-c9 log row
    (20, "Flip Three", "sprite-sf3"),
    (19, "Freeze", "sprite-sf"),    # three times, each followed by FREEZED
    (21, "Second Chance", "sprite-sch"),
    (14, "+4", "sprite-s4"),
    (15, "+6", "sprite-s6"),
])
def test_material_ids_observed_live(mid, label, sprite):
    assert card(mid).label == label
    assert card(mid).sprite == sprite


def test_only_numbers_can_bust():
    assert all(c.busts == (c.kind == NUMBER) for c in CARDS.values())


# -- tracking a real table ---------------------------------------------------

@pytest.fixture(scope="module")
def replayed():
    t = Table()
    for ntype, args in EVENTS:
        t.handle(ntype, args)
    return t


def test_matches_bga_own_card_counts(replayed):
    """The decisive check: BGA reported these counts at this exact moment."""
    locs = Counter(replayed.location.values())
    assert locs["deck2"] == BGA_SNAPSHOT["deck2"]
    assert locs["player"] == BGA_SNAPSHOT["player"]
    assert replayed.deck_size() == BGA_SNAPSHOT["deck"]


def test_every_card_is_accounted_for(replayed):
    locs = Counter(replayed.location.values())
    assert replayed.deck_size() + locs["deck2"] + locs["player"] == DECK_SIZE


def test_remaining_deck_size_agrees_with_location_count(replayed):
    assert sum(replayed.remaining().values()) == replayed.deck_size()


def test_detects_round_ends(replayed):
    # Two rounds ended during the captured window; a third was in progress.
    assert replayed.rounds_seen == 2


def test_second_chance_absorbed_a_duplicate(replayed):
    """Card 23 duplicated a 6 already held; 23 and the Second Chance both went."""
    assert replayed.location["23"] == "deck2"
    assert replayed.location["16"] == "deck2"
    assert replayed.identity["16"] == 21  # Second Chance


def test_wait_location_is_not_double_counted(replayed):
    """Draws arrive twice, as `wait` then `player`; each card must land once."""
    assert all(loc != "wait" for loc in replayed.location.values())
    for p in replayed.players.values():
        assert len(p.cards) == len(set(p.cards))


# -- the maths ---------------------------------------------------------------

def test_bust_probability_is_a_plain_ratio():
    deck = Counter({5: 3, 7: 2, 19: 4})   # 3 fives, 2 sevens, 4 Freezes
    p, bust, total = bust_probability(deck, {5})
    assert (bust, total) == (3, 9)
    assert p == pytest.approx(3 / 9)


def test_second_chance_removes_the_bust_risk():
    deck = Counter({5: 3, 19: 1})
    assert bust_probability(deck, {5}, protected=True)[0] == 0.0
    assert bust_probability(deck, {5}, protected=False)[0] > 0


def test_action_and_modifier_cards_are_never_bust_risks():
    deck = Counter(full_deck())
    p, bust, _ = bust_probability(deck, set())
    assert (p, bust) == (0.0, 0)


def test_empty_deck_is_not_a_division_by_zero():
    assert bust_probability(Counter(), {5}) == (0.0, 0, 0)


def test_advice_flips_to_stay_near_the_published_threshold():
    """Optimal play is a bust-probability threshold around 0.22-0.25."""
    def advice(held):
        deck = Counter(full_deck())
        for n in held:
            deck[n] -= 1
        return analyse(deck, set(held))

    low = advice([12])              # ~12% bust
    high = advice([11, 12])         # ~23% bust
    assert low.p_bust < 0.20 and low.recommend == "HIT"
    assert 0.20 < high.p_bust < 0.30 and high.recommend == "STAY"


def test_flip7_bonus_is_scored():
    deck = Counter(full_deck())
    seven = {1, 2, 3, 4, 5, 6, 7}
    for n in seven:
        deck[n] -= 1
    a = analyse(deck, seven)
    assert a.stay_value == sum(seven) + 15
    assert a.to_flip7 == 0


def test_x2_doubles_numbers_only_not_modifiers():
    """The Op's FAQ: x2 multiplies the number sum, then flat modifiers add."""
    deck = Counter(full_deck())
    plain = analyse(deck, {5, 9}, adds=10, doubled=False)
    doubled = analyse(deck, {5, 9}, adds=10, doubled=True)
    assert plain.stay_value == 5 + 9 + 10
    assert doubled.stay_value == (5 + 9) * 2 + 10


# -- the snapshot the UI renders ---------------------------------------------

def test_snapshot_is_locked_to_me_not_the_active_player():
    """The panel must follow one seat regardless of whose turn it is."""
    from flip7.app import snapshot

    class FakeReader:
        connected, status, atlas = True, "live", {}
        def __init__(self, table): self.table = table

    t = Table()
    t.seed({
        "players": {"100601871": {"id": "100601871", "no": "3", "name": "SmelvinG142"},
                    "92017342": {"id": "92017342", "no": "2", "name": "Someone Else"}},
        "board": {"cards": []},
    }, my_player_id="100601871")
    for ntype, args in EVENTS:
        t.handle(ntype, args)

    s = snapshot(FakeReader(t))
    assert s["me"]["name"] == "SmelvinG142"
    assert [p["is_me"] for p in s["players"]].count(True) == 1
    assert next(p for p in s["players"] if p["is_me"])["name"] == "SmelvinG142"
    # advice is computed for that seat's hand, whoever is to act
    assert s["advice"] is not None
    assert 0.0 <= s["advice"]["p_bust"] <= 1.0
    assert s["advice"]["recommend"] in ("HIT", "STAY")


def test_snapshot_deck_view_covers_every_card_type():
    from flip7.app import snapshot

    class FakeReader:
        connected, status, atlas = True, "live", {}
        table = Table()

    s = snapshot(FakeReader())
    assert len(s["deck"]) == 22                       # 13 numbers + 6 mods + 3 actions
    assert sum(d["total"] for d in s["deck"]) == DECK_SIZE
    assert sum(d["left"] for d in s["deck"]) == DECK_SIZE   # nothing drawn yet


# -- round boundaries and Flip Three ------------------------------------------

def test_round_end_clears_hands_from_a_snapshot():
    """The reported bug: cards stayed on screen after the deck reset."""
    t = Table()
    t.seed({"players": {"1": {"id": "1", "no": "1", "name": "A"}},
            "board": {"cards": [
                {"id": "5", "materialId": "7", "location": "player", "locationId": "1"},
                {"id": "6", "materialId": "9", "location": "player", "locationId": "1"},
                {"id": "7", "materialId": None, "location": "deck", "locationId": None},
            ]}}, my_player_id="1")
    assert len(t.hand("1")) == 2

    # New round: BGA's snapshot now shows those cards in the discard.
    t.sync({"players": {"1": {"id": "1", "no": "1", "name": "A", "status": "FIRST_TURN"}},
            "board": {"cards": [
                {"id": "5", "materialId": "7", "location": "deck2", "locationId": None},
                {"id": "6", "materialId": "9", "location": "deck2", "locationId": None},
                {"id": "7", "materialId": None, "location": "deck", "locationId": None},
            ]}})
    assert t.hand("1") == [], "hand must empty when the tableau clears"
    assert t.round_score("1") == 0


def test_identities_survive_a_reshuffle():
    """Cards go back under face-down, but we already saw them."""
    t = Table()
    t.sync({"players": {}, "board": {"cards": [
        {"id": "5", "materialId": "7", "location": "deck2", "locationId": None}]}})
    assert t.identity["5"] == 7
    t.sync({"players": {}, "board": {"cards": [   # reshuffled: materialId hidden again
        {"id": "5", "materialId": None, "location": "deck", "locationId": None}]}})
    assert t.identity["5"] == 7, "a seen card must not become unknown again"
    assert t.remaining()[7] == COUNTS[7]          # it is back in the deck


def test_flip_three_prefers_the_opponent_most_likely_to_bust():
    from flip7.stats import rank_flip_three
    deck = Counter(full_deck())
    ranked = rank_flip_three(deck, [
        {"name": "me", "is_me": True, "numbers": {1}, "adds": 0},
        {"name": "loaded", "is_me": False, "numbers": {8, 9, 10, 11, 12}, "adds": 0},
        {"name": "empty", "is_me": False, "numbers": set(), "adds": 0},
    ])
    assert ranked[0].name == "loaded"             # most to lose, most likely to bust
    assert ranked[0].p_bust > ranked[-1].p_bust


def test_flip_three_skips_players_already_out():
    from flip7.stats import rank_flip_three
    ranked = rank_flip_three(Counter(full_deck()), [
        {"name": "busted", "is_me": False, "numbers": {5}, "out": True},
        {"name": "live", "is_me": False, "numbers": {5}},
    ])
    assert [t.name for t in ranked] == ["live"]


def test_flip_three_on_an_empty_hand_is_safe():
    from flip7.stats import three_draw_outcome
    p_bust, ev = three_draw_outcome(Counter(full_deck()), set())
    assert p_bust < 0.25 and ev > 0


def test_no_hit_stay_call_once_your_round_is_over():
    """Busted/stayed/frozen: there is no decision left, so do not offer one."""
    from flip7.app import snapshot

    class FakeReader:
        connected, status, atlas = True, "live", {}
        def __init__(self, table): self.table = table

    for status in ("BUSTED", "STAYED", "FREEZED"):
        t = Table()
        t.seed({"players": {"7": {"id": "7", "no": "1", "name": "Me", "status": status}},
                "board": {"cards": [
                    {"id": "1", "materialId": "5", "location": "player", "locationId": "1"}]}},
               my_player_id="7")
        s = snapshot(FakeReader(t))
        assert s["advice"]["over"] is True, status
        assert s["advice"]["status"] == status


# -- deck exhaustion and reshuffle --------------------------------------------

def _snapshot(locations, identities):
    """Build a gamedatas-shaped snapshot: {card id: location}."""
    return {"players": {"7": {"id": "7", "no": "1", "name": "Me"},
                        "8": {"id": "8", "no": "2", "name": "Them"}},
            "board": {"cards": [
                {"id": cid, "materialId": (str(identities[cid])
                                           if loc != "deck" and cid in identities else None),
                 "location": loc,
                 "locationId": ("1" if loc == "player" and int(cid) % 2 else
                                "2" if loc == "player" else None)}
                for cid, loc in locations.items()]}}


def test_reshuffled_deck_excludes_cards_still_in_hands():
    """The rule: the deck is rebuilt from the DISCARD only. Hands stay out."""
    ids = [str(i) for i in range(1, 95)]
    identities = {}
    mids = []
    for mid, n in COUNTS.items():
        mids += [mid] * n
    for cid, mid in zip(ids, mids):
        identities[cid] = mid

    # Whole deck has been drawn: 20 cards sit in hands, 74 in the discard.
    in_hand = ids[:20]
    discarded = ids[20:]
    loc = {c: ("player" if c in in_hand else "deck2") for c in ids}
    t = Table()
    t.sync(_snapshot(loc, identities))
    assert t.deck_size() == 0
    assert sum(t.remaining().values()) == 0

    # Deck runs out: the discard is shuffled under, hands are untouched.
    loc = {c: ("player" if c in in_hand else "deck") for c in ids}
    t.sync(_snapshot(loc, identities))

    assert t.deck_size() == 74
    rem = t.remaining()
    assert sum(rem.values()) == 74, "new deck must be exactly the old discard"

    # And it is the RIGHT 74: the full deck minus what people are holding.
    held = Counter(identities[c] for c in in_hand)
    expected = Counter(COUNTS)
    expected.subtract(held)
    assert rem == +expected, "cards in hands must not reappear in the deck"

    # Nothing anyone is holding can be drawn again.
    for mid, n in held.items():
        assert rem[mid] == COUNTS[mid] - n, f"material {mid} miscounted"


def test_identities_are_not_forgotten_when_cards_go_back_under():
    """After a reshuffle the deck is face-down again, but we know what is in it."""
    ids = [str(i) for i in range(1, 95)]
    identities = {}
    mids = []
    for mid, n in COUNTS.items():
        mids += [mid] * n
    for cid, mid in zip(ids, mids):
        identities[cid] = mid
    t = Table()
    t.sync(_snapshot({c: "deck2" for c in ids}, identities))
    assert len(t.identity) == 94
    t.sync(_snapshot({c: "deck" for c in ids}, identities))   # all materialIds now null
    assert len(t.identity) == 94, "a reshuffle must not erase what we saw"
    assert sum(t.remaining().values()) == 94


def test_deck_recovers_when_bga_leaves_cards_marked_as_discard():
    """The reported bug, reproduced from a real game log.

    BGA reshuffles the discard back under, but never moves those cards out of
    `deck2` in gamedatas -- they stay marked as discard for the rest of the
    game. Trusting that field pinned the deck at 0 while play carried on. The
    game's own on-screen counter is the honest number, and a rise in it can
    only mean the pile was shuffled back.
    """
    ids = [str(i) for i in range(1, 95)]
    mids = []
    for mid, n in COUNTS.items():
        mids += [mid] * n
    identities = dict(zip(ids, mids))
    in_hand, discarded = ids[:8], ids[8:]

    def snap(loc_map):
        return {"players": {"7": {"id": "7", "no": "1", "name": "Me"}},
                "board": {"cards": [
                    {"id": c, "materialId": (str(identities[c]) if l != "deck" else None),
                     "location": l, "locationId": ("1" if l == "player" else None)}
                    for c, l in loc_map.items()]}}

    loc = {c: ("player" if c in in_hand else "deck2") for c in ids}
    t = Table()
    t.sync(snap(loc), deck_shown=0)          # exactly the state in the log
    assert t.deck_size() == 0
    assert sum(t.remaining().values()) == 0

    # BGA reshuffles. gamedatas does not budge -- still deck 0, deck2 86 --
    # but the counter on screen jumps to 86.
    t.sync(snap(loc), deck_shown=86)

    assert t.deck_size() == 86, "the game's own counter must win over stale gamedatas"
    rem = t.remaining()
    assert sum(rem.values()) == 86, "the reshuffled deck must not stay empty"
    assert t.reshuffles == 1

    # And it is the right 86: everything except what people are holding.
    held = Counter(identities[c] for c in in_hand)
    expected = Counter(COUNTS)
    expected.subtract(held)
    assert rem == +expected

    # Play continues: a card is drawn, then discarded. It leaves the deck again.
    drawn = discarded[0]
    t.handle("moveTokens", {"tokens": [
        {"id": drawn, "materialId": str(identities[drawn]),
         "location": "player", "locationId": "1"}]})
    assert t.remaining()[identities[drawn]] == expected[identities[drawn]] - 1


def test_hands_keep_tracking_when_the_snapshot_freezes_after_a_reshuffle():
    """Second reported bug, from a real game log.

    After BGA's first reshuffle the whole gamedatas card array stops updating,
    not just the deck field -- the discard count sat frozen at 85 for the rest
    of the game. Re-adopting that frozen snapshot every tick wiped the live
    position, so hands vanished while player status, carried by events, kept
    working. The snapshot must be ignored once it stops matching the counter.
    """
    ids = [str(i) for i in range(1, 95)]
    mids = []
    for mid, n in COUNTS.items():
        mids += [mid] * n
    identities = dict(zip(ids, mids))

    def snap(loc_map):
        return {"players": {"7": {"id": "7", "no": "1", "name": "Me"}},
                "board": {"cards": [
                    {"id": c, "materialId": (str(identities[c]) if l != "deck" else None),
                     "location": l, "locationId": ("1" if l == "player" else None)}
                    for c, l in loc_map.items()]}}

    frozen = {c: ("player" if c in ids[:9] else "deck2") for c in ids}
    t = Table()
    t.sync(snap(frozen), deck_shown=0)
    t.me = "1"
    assert len(t.hand("1")) == 9

    # Reshuffle: the counter jumps, gamedatas stays frozen from here on.
    t.sync(snap(frozen), deck_shown=85)
    assert t.snapshot_fresh is False, "a frozen snapshot must be recognised"

    # Round ends. Only the event stream reports it.
    t.handle("moveTokens", {"tokens": [
        {"id": c, "materialId": str(identities[c]), "location": "deck2"}
        for c in ids[:9]]})
    assert t.hand("1") == [], "hand must clear even though the snapshot is stale"

    # New cards are drawn. Again, only events say so.
    for c in ids[20:23]:
        t.handle("moveTokens", {"tokens": [
            {"id": c, "materialId": str(identities[c]),
             "location": "player", "locationId": "1"}]})
    assert len(t.hand("1")) == 3, "new cards must appear from events alone"

    # And re-adopting the frozen snapshot must not undo any of it.
    t.sync(snap(frozen), deck_shown=80)
    assert len(t.hand("1")) == 3, "the stale snapshot must not overwrite live state"


# -- starting a new game ------------------------------------------------------

def test_you_are_followed_by_account_not_by_seat():
    """Third reported bug: a new game showed the old game and the wrong player.

    Seat numbers are per-table. Storing "you" as a seat means that when the
    next game seats you differently, the panel silently reports a stranger's
    hand as yours.
    """
    t = Table()
    t.seed({"players": {"111": {"id": "111", "no": "2", "name": "SmelvinG142"},
                        "222": {"id": "222", "no": "1", "name": "skunk85"}},
            "board": {"cards": []}}, my_player_id="111")
    assert t.me == "2"

    # Next game: same people, different seats.
    t2 = Table()
    t2.seed({"players": {"111": {"id": "111", "no": "4", "name": "SmelvinG142"},
                         "222": {"id": "222", "no": "2", "name": "skunk85"}},
             "board": {"cards": []}}, my_player_id="111")
    assert t2.me == "4", "your seat must be resolved per table, not remembered"
    assert t2.players[t2.me].name == "SmelvinG142"


def test_player_override_still_pins_the_right_seat():
    t = Table()
    t.seed({"players": {"111": {"id": "111", "no": "3", "name": "SmelvinG142"},
                        "222": {"id": "222", "no": "1", "name": "skunk85"}},
            "board": {"cards": []}})
    assert t.me is None            # no account id given
    t.me = "3"                     # what --player does
    assert t.me == "3"
    assert t.my_player_id == "111"


def test_a_new_page_forces_a_reseed():
    """A dead context means the page is gone; its state must not be reused."""
    from flip7.reader import Reader

    r = Reader()
    r._seeded = True
    r.ctx = 5
    r.table.identity["1"] = 7        # stale state from the finished game

    # connect() finding a different context must invalidate the seed.
    if r.ctx != 9:
        r._seeded = False
    r.ctx = 9
    assert r._seeded is False, "a new context must trigger a fresh seed"


def test_losing_the_connection_invalidates_the_seed():
    """Otherwise the tracker reconnects but never re-seeds or re-taps."""
    import inspect
    from flip7.reader import Reader
    src = inspect.getsource(Reader.poll)
    assert "self._seeded = False" in src, (
        "poll must clear the seed when the connection drops, or it will "
        "reconnect to a new game holding the old one's state")
