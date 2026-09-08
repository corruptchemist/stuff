"""A real notification sequence captured from a live BGA Flip 7 table.

Transcribed verbatim from a recon run (table 912630570). Each entry is
(notification type, args). Cards are (id, materialId, location, locationId).
The sequence stops exactly where that run's gamedatas snapshot was taken, so
the tracker's output can be checked against BGA's own numbers: at that instant
BGA reported deck=63, deck2=23, player=8.
"""


def mv(*cards, player=""):
    return ("moveTokens", {
        "tokens": [{"id": str(i), "materialId": str(m), "location": loc,
                    "locationId": (str(l) if l is not None else None), "visible": "1"}
                   for i, m, loc, l in cards],
        "player_name": player,
    })


def draw(cid, mid, pno):
    """A draw shows up twice: mid-animation as `wait`, then landing on `player`."""
    return [mv((cid, mid, "wait", pno)), mv((cid, mid, "player", pno))]


def players(**status):
    ids = {"1": "100504754", "2": "92017342", "3": "100601871"}
    names = {"1": "relaxed-bison7", "2": "Pas ton tour", "3": "SmelvinG142"}
    return ("updatePlayers", {"players": {
        ids[no]: {"id": ids[no], "no": no, "name": names[no],
                  "score": str(sc), "status": st}
        for no, (st, sc) in status.items()}})


EVENTS: list = []
# --- round 1 -----------------------------------------------------------------
EVENTS += draw("7", 10, "1") + draw("8", 11, "2")
EVENTS += [players(**{"1": (None, 0), "2": ("BUSTED", 0), "3": (None, 0)})]
EVENTS += [mv(("9", 19, "wait", "3")), mv(("9", 19, "player", "1"))]  # Freeze given away
EVENTS += [players(**{"1": ("FREEZED", 29), "2": ("BUSTED", 0), "3": (None, 0)})]
EVENTS += draw("10", 7, "3")
EVENTS += [players(**{"1": ("FREEZED", 29), "2": ("BUSTED", 0), "3": ("BUSTED", 0)})]
# round ends: the whole tableau sweeps to the discard in one event
EVENTS += [mv(*[(str(i), m, "deck2", None) for i, m in
                zip(range(1, 11), [20, 9, 3, 7, 11, 7, 10, 11, 19, 7])])]
# --- round 2 -----------------------------------------------------------------
for cid, mid, pno in [("11", 12, "2"), ("12", 6, "3"), ("13", 11, "1"),
                      ("14", 12, "2"), ("15", 10, "3"), ("16", 21, "1"),
                      ("17", 12, "3"), ("18", 14, "1"), ("19", 11, "3"),
                      ("20", 6, "1"), ("21", 8, "1"), ("22", 12, "1"),
                      ("23", 6, "1")]:
    EVENTS += draw(cid, mid, pno)
# card 23 duplicates the 6 already held; Second Chance (16) is spent instead of busting
EVENTS += [mv(("23", 6, "deck2", "1")), mv(("16", 21, "deck2", "1"))]
EVENTS += [mv(*[(str(i), m, "deck2", None) for i, m in
                zip([11, 12, 13, 14, 15, 17, 18, 19, 20, 21, 22],
                    [12, 6, 11, 12, 10, 12, 14, 11, 6, 8, 12])])]
# --- round 3, still in progress when the snapshot was taken -------------------
for cid, mid, pno in [("24", 15, "3"), ("25", 11, "1"), ("26", 9, "2"),
                      ("27", 11, "3"), ("28", 6, "2"), ("29", 12, "3")]:
    EVENTS += draw(cid, mid, pno)
EVENTS += [mv(("30", 19, "wait", "2")), mv(("30", 19, "player", "3"))]
EVENTS += [mv(("31", 19, "wait", "2")), mv(("31", 19, "player", "2"))]

# BGA's own counts at this exact point in the game.
BGA_SNAPSHOT = {"deck": 63, "deck2": 23, "player": 8}
