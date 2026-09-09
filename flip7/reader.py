"""Read a live BGA Flip 7 table and keep a Table in sync.

Read-only throughout: it seeds from the snapshot the client already holds, tees
the notification stream the client already receives, and never sends a move,
a message, or any input.
"""

from __future__ import annotations

import time
from pathlib import Path

from . import cdp
from .deck import CARDS
from .recon import PREAMBLE, TAP_JS
from .state import Table

GAME_URL = "https://boardgamearena.com/gamepanel?game=flipseven"

# Pull the real card art out of the running game: the spritesheet URL and each
# card's offset within it, so the overlay shows actual Flip 7 cards rather than
# an approximation. Read live, so a BGA art update follows automatically.
ATLAS_JS = """
const classes = %s;
const probe = document.createElement('div');
probe.style.cssText = 'position:absolute;left:-9999px;top:-9999px';
D.body.appendChild(probe);
const out = {};
for (const cls of classes) {
  // Wear the same classes a real card does, so the probe reports the card box
  // BGA's own stylesheet gives it -- that is the scale the sprite is cut for.
  probe.className = 'sprite flippable-front ' + cls;
  const cs = GW.getComputedStyle(probe);
  out[cls] = {image: cs.backgroundImage, position: cs.backgroundPosition,
              size: cs.backgroundSize, width: cs.width, height: cs.height};
}
probe.remove();
const sample = D.querySelector('.f7_token_card');
const sr = sample ? sample.getBoundingClientRect() : null;
return {sprites: out, cardBox: sr ? {w: sr.width, h: sr.height} : null};
"""

SEED_JS = """
const g = GW.gameui && GW.gameui.gamedatas;
if (!g) return null;
return {board: g.board, players: g.players, gamestate: g.gamestate,
        playerId: GW.gameui.player_id, tableId: GW.gameui.table_id};
"""

# BGA keeps board.cards current for this game, so this is read every tick and
# treated as the truth, rather than rebuilding state from the event stream.
STATE_JS = """
const g = GW.gameui && GW.gameui.gamedatas;
if (!g) return null;
const deckEl = D.querySelector('.f7_card_count') || D.querySelector('.f7_deck');
return {board: g.board, players: g.players,
        gamestate: g.gamestate && g.gamestate.name,
        deckShown: deckEl ? parseInt((deckEl.textContent||'').trim(), 10) : null};
"""

# Drain: hand back everything captured so far and clear it, so each event is
# processed exactly once even if polling is slow.
DRAIN_JS = """
const t = window.__flip7_tap;
if (!t) return null;
const taken = t.events.splice(0, t.events.length);
return {events: taken, installed: t.installed, errors: t.errors.splice(0)};
"""


class Reader:
    """Keeps a Table synchronised with a live table page."""

    def __init__(self, port: int = cdp.DEFAULT_PORT, match: str = "boardgamearena",
                 player_name: str | None = None):
        self.port, self.match = port, match
        self.player_name = player_name
        self.page: cdp.Page | None = None
        self.ctx: int | None = None
        self.table = Table()
        self.atlas: dict = {}
        self.card_box = None
        self.connected = False
        self.table_id = None
        self.deck_shown = None
        self.status = "starting"
        self._seeded = False

    # -- connection ----------------------------------------------------------

    def connect(self, wait: float = 2.0) -> bool:
        """Find the tab, then the frame inside it that is actually running the game."""
        try:
            tabs = cdp.list_targets(self.port)
        except Exception as exc:
            self.status = f"Chrome not reachable on port {self.port}: {str(exc)[:60]}"
            self.connected = False
            return False

        # BGA's browser tab is .../tableview?table=..., and only the inner frame's
        # URL says "flipseven" -- so match the site, then look for the game inside.
        game_tabs = [t for t in tabs
                     if self.match.lower() in (t.get("url", "") + t.get("title", "")).lower()]
        if not game_tabs:
            self.status = ("no Board Game Arena tab open in the bot's Chrome"
                           + (f" (saw: {', '.join(self._tab_names(tabs))})" if tabs else ""))
            self.connected = False
            return False

        for tab in game_tabs:
            try:
                page = cdp.Page(tab["webSocketDebuggerUrl"])
                page.discover_contexts()
                ctx = page.find_context(wait=wait)
                if ctx is not None:
                    self.page, self.ctx = page, ctx["id"]
                    self.connected = True
                    self.status = "connected"
                    return True
                page.close()
            except cdp.CDPError:
                continue

        self.status = ("BGA is open but no game is running yet — "
                       "start or join a Flip 7 table and wait for it to load "
                       f"(tab: {self._tab_names(game_tabs)[0]})")
        self.connected = False
        return False

    @staticmethod
    def _tab_names(tabs) -> list[str]:
        out = []
        for t in tabs:
            u = (t.get("url") or "").split("?")[0]
            out.append((t.get("title") or u or "?")[:44])
        return out or ["none"]

    def dump(self) -> str:
        """One poll, then everything the tracker believes -- for diagnosing drift."""
        from collections import Counter
        lines = []
        if not self.connect():
            return "not connected: " + self.status
        self.setup()
        raw = self._eval(STATE_JS)
        if raw is None:
            return "gamedatas unreadable in the game frame"
        cards = (raw.get("board") or {}).get("cards") or []
        lines.append(f"gamedatas.board.cards      : {len(cards)} entries")
        lines.append(f"  by location              : "
                     f"{dict(Counter(c.get('location') for c in cards))}")
        lines.append(f"  with a known materialId  : "
                     f"{sum(1 for c in cards if c.get('materialId') is not None)}")
        lines.append(f"  deck counter on screen   : {raw.get('deckShown')}")
        lines.append(f"  sample                   : {cards[:2]}")
        self.table.sync(raw)
        t = self.table
        lines.append(f"\ntracker view")
        lines.append(f"  identities learned       : {len(t.identity)} / 94")
        lines.append(f"  deck_size()              : {t.deck_size()}")
        lines.append(f"  remaining() total        : {sum(t.remaining().values())}")
        lines.append(f"  me                       : {t.me} "
                     f"({t.players[t.me].name if t.me in t.players else '??'})")
        rem = t.remaining()
        from .deck import CARDS, COUNTS
        left = " ".join(f"{CARDS[m].label}:{rem.get(m,0)}/{COUNTS[m]}" for m in sorted(CARDS))
        lines.append(f"  remaining by card        : {left}")
        for no, p in sorted(t.players.items()):
            lines.append(f"    seat {no} {p.name:16} {str(p.status):8} "
                         f"{[c.label for c in t.hand(no)]}")
        return "\n".join(lines)

    def diagnose(self) -> str:
        """Human-readable dump of what the tool can actually see."""
        lines = []
        try:
            tabs = cdp.list_targets(self.port)
        except Exception as exc:
            return f"Chrome not reachable on port {self.port}: {exc}"
        lines.append(f"{len(tabs)} tab(s) open in the bot's Chrome:")
        for t in tabs:
            lines.append(f"   - {(t.get('title') or '')[:60]}")
            lines.append(f"     {(t.get('url') or '')[:100]}")
            try:
                pg = cdp.Page(t["webSocketDebuggerUrl"])
                ctxs = pg.discover_contexts()
                for c in ctxs:
                    try:
                        has = pg.evaluate("return typeof window.gameui", context_id=c["id"])
                        href = pg.evaluate("return location.href", context_id=c["id"])
                    except cdp.CDPError:
                        has, href = "?", "?"
                    lines.append(f"       ctx {c['id']}: gameui={has}  {str(href)[:78]}")
                pg.close()
            except Exception as exc:
                lines.append(f"       (could not inspect: {str(exc)[:60]})")
        return "\n".join(lines)

    def _eval(self, js: str):
        return self.page.evaluate(PREAMBLE + js, context_id=self.ctx)

    def setup(self) -> None:
        """Seed state, grab the card art, and install the tap."""
        seed = self._eval(SEED_JS)
        if seed:
            self.table = Table()
            self.table.seed(seed, seed.get("playerId"))
            self.table_id = seed.get("tableId")
            if self.player_name:  # explicit override wins over auto-detect
                for p in self.table.players.values():
                    if p.name.lower() == self.player_name.lower():
                        self.table.me = p.no
            self._seeded = True
        classes = [c.sprite for c in CARDS.values()] + ["sprite-cback"]
        art = self._eval(ATLAS_JS % (classes,))
        if art:
            self.atlas = art.get("sprites") or {}
            self.card_box = art.get("cardBox")
        self._eval(TAP_JS)

    def poll(self) -> int:
        """Drain pending notifications into the table. Returns how many."""
        if not self.connected and not self.connect():
            return 0
        try:
            if not self._seeded:
                self.setup()
            snap = self._eval(STATE_JS)
            if snap is None:  # page reloaded and took the game with it
                self.status = "page reloaded — reseeding"
                self._seeded = False
                self.setup()
                return 0
            self.table.sync(snap)
            self.deck_shown = snap.get("deckShown")

            drained = self._eval(DRAIN_JS)
            if drained is None:
                self._eval(TAP_JS)  # tap lost; state is still correct from sync
                return 0
            events = drained.get("events") or []
            # Both taps observe the same notifications, so exactly one source
            # must be consumed or every card is counted twice. On a live table
            # dojo.publish is the one that actually fires; notifqueue is kept
            # only as a fallback in case that ever changes.
            sources = {e.get("src", "") for e in events}
            preferred = next((s for s in sources if s.endswith("dojo.publish")), None)
            if preferred is None:
                preferred = next(iter(sources), None)
            used = [e for e in events if e.get("src") == preferred]
            # State already came from the snapshot above; events are used only
            # to count round ends and reshuffles, which a snapshot cannot show.
            for e in used:
                if e.get("type") == "moveTokens":
                    self.table.note_transitions(e.get("args") or {})
            self.status = "live"
            return len(used)
        except cdp.CDPError as exc:
            self.connected = False
            self.status = f"lost connection: {str(exc)[:80]}"
            return 0

    def close(self) -> None:
        if self.page:
            self.page.close()
