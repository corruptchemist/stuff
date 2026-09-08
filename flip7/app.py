"""Flip 7 tracker: watch a live BGA table and show the odds.

Read-only. It reads state the browser already has and never sends input.
"""

from __future__ import annotations

import argparse
import http.server
import json
import socketserver
import threading
import time
from collections import Counter
from pathlib import Path

from . import cdp
from .deck import CARDS, COUNTS, NUMBER, SECOND_CHANCE, card
from .reader import GAME_URL, Reader
from .stats import analyse

WEB_DIR = Path(__file__).parent / "web"


def snapshot(reader: Reader) -> dict:
    """Everything the page needs, computed from the tracked table."""
    t = reader.table
    remaining = t.remaining()
    me_no = t.me
    banner = None
    if reader.connected and me_no is None:
        banner = ("Could not tell which player is you. Pass --player \"YourName\" "
                  "to lock the panel to your seat.")

    advice = None
    bust_list: list[dict] = []
    my_numbers: set[int] = set()
    if me_no is not None and me_no in t.players:
        hand = t.hand(me_no)
        my_numbers = {c.value for c in hand if c.kind == NUMBER}
        adds = sum(c.value for c in hand if c.kind == "modifier" and c.multiplier == 1)
        doubled = any(c.multiplier == 2 for c in hand)
        protected = t.has_second_chance(me_no)
        a = analyse(remaining, my_numbers, adds, doubled, protected)
        advice = {
            "p_bust": a.p_bust, "recommend": a.recommend, "stay_value": a.stay_value,
            "ev_hit": a.ev_hit, "bust_cards": a.bust_cards, "safe_cards": a.safe_cards,
            "to_flip7": a.to_flip7, "protected": a.protected, "edge": a.edge,
            "margin_txt": f"{a.margin:+.1f} pts vs staying",
            "p_flip7_next": a.p_flip7_next,
        }
        for n in sorted(my_numbers):
            if remaining.get(n):
                bust_list.append({"sprite": card(n).sprite, "label": str(n),
                                  "count": remaining[n]})

    deck_view = []
    for mid in sorted(CARDS):
        c = CARDS[mid]
        deck_view.append({
            "sprite": c.sprite, "label": c.label, "left": remaining.get(mid, 0),
            "total": COUNTS[mid], "bust": c.kind == NUMBER and c.value in my_numbers,
        })

    players = []
    for no in sorted(t.players, key=lambda x: int(x) if str(x).isdigit() else 99):
        p = t.players[no]
        players.append({
            "name": p.name or f"seat {no}", "status": p.status, "score": p.score,
            "round_score": t.round_score(no), "is_me": no == me_no,
            "cards": [{"sprite": c.sprite, "label": c.label} for c in t.hand(no)],
        })

    locs = Counter(t.location.values())
    return {
        "live": reader.connected, "status": reader.status, "banner": banner,
        "atlas": reader.atlas,
        "me": ({"name": t.players[me_no].name,
                "cards": [{"sprite": c.sprite, "label": c.label} for c in t.hand(me_no)]}
               if me_no in t.players else None),
        "advice": advice, "bust_list": bust_list, "deck": deck_view,
        "players": players, "deck_size": t.deck_size(), "discard": locs.get("deck2", 0),
        "round": t.rounds_seen + 1, "reshuffles": t.reshuffles, "events": t.events,
    }


class Handler(http.server.SimpleHTTPRequestHandler):
    reader: Reader = None  # set on the class before serving

    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(WEB_DIR), **kw)

    def do_GET(self):
        if self.path.split("?")[0].rstrip("/") in ("/state", "state"):
            body = json.dumps(snapshot(self.reader)).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def log_message(self, *a):
        pass


class _Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> None:
    ap = argparse.ArgumentParser(description="Live Flip 7 tracker for Board Game Arena.")
    ap.add_argument("--player", default=None,
                    help="lock the panel to this player name (default: auto-detect you)")
    ap.add_argument("--port", type=int, default=8777, help="port for the tracker UI")
    ap.add_argument("--cdp-port", type=int, default=cdp.DEFAULT_PORT)
    ap.add_argument("--profile", default=str(cdp.DEFAULT_PROFILE))
    ap.add_argument("--chrome", default=None)
    ap.add_argument("--no-launch", action="store_true",
                    help="attach to a Chrome already started with remote debugging")
    ap.add_argument("--interval", type=float, default=0.5, help="poll seconds")
    args = ap.parse_args()

    print("=" * 66)
    print(" Flip 7 tracker — reads your table, never plays it")
    print("=" * 66)

    if not args.no_launch:
        print(f"\nLaunching Chrome (profile: {args.profile})")
        try:
            cdp.launch_chrome(GAME_URL, args.cdp_port, Path(args.profile), args.chrome)
        except cdp.CDPError as exc:
            print(f"ERROR: {exc}")
            raise SystemExit(1)
        print("Log into BGA in that window if needed, then open a Flip 7 table.")

    reader = Reader(args.cdp_port, "flipseven", args.player)
    Handler.reader = reader
    httpd = _Server(("127.0.0.1", args.port), Handler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{args.port}/"
    print(f"\nTracker UI: {url}")
    print("Open that in any window and put it beside the game. Ctrl-C to stop.\n")

    try:
        cdp.open_tab(url, args.cdp_port)
    except Exception:
        pass

    last = None
    try:
        while True:
            n = reader.poll()
            if reader.status != last:
                print(f"  [{time.strftime('%H:%M:%S')}] {reader.status}")
                last = reader.status
            if n:
                t = reader.table
                who = t.players[t.me].name if t.me in t.players else "?"
                print(f"  +{n:3d} events · deck {t.deck_size():2d} · you: {who}")
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\nstopped")
    finally:
        reader.close()
        httpd.shutdown()


if __name__ == "__main__":
    main()
