"""Discover how BGA's Flip 7 table actually exposes its game state.

Everything here is read-only observation of a page you already have open: it
reads variables and DOM that the browser has rendered for you, and taps the
notification stream the client already receives. It never sends a move, a
message, or any input to the server.

Run it once with a live table open; it writes a JSON report describing what is
really there, so the parser can be written against fact rather than guesswork.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

from . import cdp

BGA_URL = "https://boardgamearena.com/gamepanel?game=flipseven"


# Each probe is independent JS returning a small JSON-able object. One probe
# failing (a global that does not exist, a selector that matches nothing) must
# never abort the run, so every one is wrapped in try/catch by _run.
PREAMBLE = """
// BGA's tableview page is a wrapper; the game itself lives in an iframe. Resolve
// the real game window once, and probe THAT. Falls back to the top document so
// the probes still work if BGA ever stops using the iframe.
const W = (() => {
  try { if (window.gameui) return window; } catch (e) {}
  for (const f of document.querySelectorAll('iframe')) {
    try { if (f.contentWindow && f.contentWindow.gameui) return f.contentWindow; } catch (e) {}
  }
  for (const f of document.querySelectorAll('iframe')) {   // gameui not up yet?
    try {
      const d = f.contentDocument;
      if (d && (d.querySelector('#logs') || d.querySelector('[class*="f7_"]'))) return f.contentWindow;
    } catch (e) {}
  }
  return null;
})();
const GW = W || window;
const D = GW.document;
"""

PROBES: dict[str, str] = {
    "page": """
        return {url: location.href, title: document.title,
                isTop: window.top === window, frames: window.frames.length,
                gameWindowFound: GW !== window,
                iframes: [...document.querySelectorAll('iframe')].map(f => {
                    let reach = 'blocked', hasGameui = null, bodyCls = null;
                    try { reach = f.contentDocument ? 'same-origin' : 'cross-origin';
                          hasGameui = typeof f.contentWindow.gameui;
                          bodyCls = f.contentDocument.body ? f.contentDocument.body.className.slice(0,80) : null;
                    } catch (e) {}
                    return {id: f.id, name: f.name, src: (f.src||'').slice(0,120),
                            cls: f.className, reach, hasGameui, bodyCls};
                })};
    """,
    "globals": """
        const names = ['gameui','dojo','dijit','ebg','bgagame','g_gamedatas',
                       'g_gamethemeurl','g_themeurl','g_archive_mode','g_replayFrom',
                       'g_gamelogs','centrifuge','centrifugeConfiguration','io'];
        const out = {};
        for (const n of names) { try { out[n] = typeof GW[n]; } catch(e) { out[n] = 'ERR'; } }
        out['top.gameui'] = (() => { try { return typeof window.gameui; } catch(e){ return 'blocked'; } })();
        out['__resolvedFromIframe'] = GW !== window;
        return out;
    """,
    "framework": """
        const gameui = GW.gameui;
        if (!gameui) return {gameui: false};
        const dojo = GW.dojo;
        return {
            gameui: true,
            dojoVersion: (dojo && dojo.version) ? String(dojo.version) : null,
            gameName: gameui.game_name || null,
            tableId: gameui.table_id || null,
            playerId: gameui.player_id || null,
            hasNotifqueue: typeof gameui.notifqueue,
            notifqueueKeys: gameui.notifqueue ? Object.keys(gameui.notifqueue).slice(0,40) : null,
            hasOnNotification: gameui.notifqueue ? typeof gameui.notifqueue.onNotification : null,
            hasGameModule: typeof gameui.GameModule,
            hasBga: typeof gameui.bga,
            hasSocket: typeof gameui.socket,
            hasAjaxcall: typeof gameui.ajaxcall,
            gameuiKeys: Object.keys(gameui).slice(0, 80),
            notifHandlers: Object.keys(gameui).filter(k => k.startsWith('notif_')),
        };
    """,
    "gamedatas": """
        const gameui = GW.gameui;
        if (!gameui || !gameui.gamedatas) return null;
        const g = gameui.gamedatas;
        const out = {keys: Object.keys(g), types: {}};
        for (const k of Object.keys(g)) {
            const v = g[k];
            out.types[k] = Array.isArray(v) ? `array[${v.length}]` : (v === null ? 'null' : typeof v);
        }
        // The single claim the research could not verify: does board.cards exist?
        out.hasBoard = !!g.board;
        out.boardKeys = g.board ? Object.keys(g.board) : null;
        if (g.board && Array.isArray(g.board.cards)) {
            const c = g.board.cards;
            out.cardsLength = c.length;
            out.cardsSample = c.slice(0, 6);
            const byLoc = {};
            for (const x of c) byLoc[x.location] = (byLoc[x.location] || 0) + 1;
            out.cardsByLocation = byLoc;
            out.distinctMaterialIds = [...new Set(c.map(x => x.materialId))].slice(0, 30);
        }
        out.playersSample = g.players ? Object.entries(g.players).slice(0,2).map(
            ([k,v]) => [k, Object.keys(v)]) : null;
        return out;
    """,
    "logs": """
        const el = D.querySelector('#logs');
        if (!el) {
            const guesses = [...D.querySelectorAll('[id*="log" i], [class*="log" i]')]
                .slice(0, 12).map(e => ({tag: e.tagName, id: e.id, cls: e.className}));
            return {found: false, candidates: guesses};
        }
        const rows = [...el.querySelectorAll('div[id^="log_"]')];
        return {
            found: true, rowCount: rows.length,
            containerClass: el.className,
            lastRows: rows.slice(-6).map(r => ({
                id: r.id,
                text: (r.textContent || '').trim().slice(0, 160),
                html: r.innerHTML.slice(0, 700),
                innerClasses: [...new Set([...r.querySelectorAll('*')]
                    .flatMap(n => [...n.classList]))],
            })),
        };
    """,
    "sprites": """
        // Card identity is expected to live in CSS classes like sprite-c7 / sprite-sf3.
        const all = [...D.querySelectorAll('*')];
        const cls = {};
        for (const el of all) for (const c of el.classList)
            if (/^sprite[-_]/i.test(c) || /^f7[-_]/i.test(c)) cls[c] = (cls[c]||0)+1;
        const flip = D.querySelectorAll('.flippable-front').length;
        return {
            classCounts: Object.fromEntries(Object.entries(cls).sort((a,b)=>b[1]-a[1]).slice(0,80)),
            flippableFrontCount: flip,
            sampleCardEl: (() => {
                const e = D.querySelector('.flippable-front');
                if (!e) return null;
                const cs = GW.getComputedStyle(e);
                return {cls: e.className, html: e.outerHTML.slice(0,400),
                        bgImage: cs.backgroundImage.slice(0,120),
                        bgPosition: cs.backgroundPosition};
            })(),
        };
    """,
    "deck_counter": """
        const el = D.querySelector('.f7_deck');
        const alts = [...D.querySelectorAll('[class*="deck" i]')].slice(0,10)
            .map(e => ({cls: e.className, text: (e.textContent||'').trim().slice(0,40)}));
        return {found: !!el, text: el ? (el.textContent||'').trim() : null,
                html: el ? el.outerHTML.slice(0,300) : null, alternatives: alts};
    """,
    "players_area": """
        const c = D.querySelector('.f7_players_container') ||
                  D.querySelector('[class*="players_container" i]');
        if (!c) return {found: false,
            appChildren: (D.querySelector('#app')
                ? [...D.querySelector('#app').children].map(e => e.className).slice(0,15)
                : null)};
        return {found: true, cls: c.className, childCount: c.children.length,
                childClasses: [...c.children].map(e => e.className).slice(0, 12),
                firstChildHtml: c.children[0] ? c.children[0].outerHTML.slice(0, 1200) : null};
    """,
}

TAP_JS = r"""
// Read-only tee on the notification stream the client already receives.
// Records what arrives; forwards every packet untouched. Installs into the game
// iframe as well as the wrapper, because BGA publishes through both.
const safe = (o, d) => {
  d = d || 0;
  if (o === null || o === undefined) return o;
  const t = typeof o;
  if (t === 'string') return o.length > 400 ? o.slice(0, 400) + '\u2026' : o;
  if (t !== 'object') return o;
  if (d > 5) return '\u2026';
  if (Array.isArray(o)) return o.slice(0, 40).map(x => safe(x, d + 1));
  const out = {};
  let n = 0;
  for (const k in o) {
    if (n++ > 50) { out['\u2026'] = 'truncated'; break; }
    try { out[k] = safe(o[k], d + 1); } catch (e) {}
  }
  return out;
};

const install = (win, tag) => {
  const T = window.__flip7_tap;
  try {
    if (win.gameui && win.gameui.notifqueue && win.gameui.notifqueue.onNotification) {
      const nq = win.gameui.notifqueue;
      if (!nq.__f7_tapped) {
        const orig = nq.onNotification.bind(nq);
        nq.onNotification = function (packet) {
          try {
            ((packet && packet.data) ? packet.data : []).forEach(n => T.events.push({
              src: tag + ':notifqueue', type: n.type, args: safe(n.args),
              log: typeof n.log === 'string' ? n.log.slice(0, 300) : null,
            }));
          } catch (e) { T.errors.push('notif: ' + e.message); }
          return orig(packet);
        };
        nq.__f7_tapped = 1;
        T.installed.push(tag + ':notifqueue.onNotification');
      }
    }
  } catch (e) { T.errors.push('install notif ' + tag + ': ' + e.message); }

  try {
    if (win.dojo && win.dojo.publish && !win.dojo.__f7_tapped) {
      const origPub = win.dojo.publish;
      win.dojo.publish = function (topic, args) {
        // THE payload we need: dojo.publish carries the notification itself as
        // args[0], which the first version of this tap discarded.
        try {
          const n = (args && args[0]) || null;
          T.events.push({
            src: tag + ':dojo.publish', type: String(topic),
            args: safe(n && n.args !== undefined ? n.args : n),
            log: (n && typeof n.log === 'string') ? n.log.slice(0, 300) : null,
          });
        } catch (e) { T.errors.push('pub: ' + e.message); }
        return origPub.apply(this, arguments);
      };
      win.dojo.__f7_tapped = 1;
      T.installed.push(tag + ':dojo.publish');
    }
  } catch (e) { T.errors.push('install publish ' + tag + ': ' + e.message); }
};

if (!window.__flip7_tap) window.__flip7_tap = {events: [], installed: [], errors: []};
install(window, 'top');
for (const f of document.querySelectorAll('iframe')) {
  try { if (f.contentWindow) install(f.contentWindow, 'iframe'); }
  catch (e) { window.__flip7_tap.errors.push('iframe unreachable: ' + e.message); }
}
return {installed: window.__flip7_tap.installed, errors: window.__flip7_tap.errors};
"""


def _run(page: cdp.Page, name: str, js: str, context_id: int | None = None):
    try:
        return page.evaluate(PREAMBLE + js, context_id=context_id)
    except Exception as exc:  # a dead probe must not kill the report
        return {"__probe_error__": str(exc)[:300]}


def main() -> None:
    ap = argparse.ArgumentParser(description="Recon a live BGA Flip 7 table.")
    ap.add_argument("--out", default="flip7-recon.json")
    ap.add_argument("--port", type=int, default=cdp.DEFAULT_PORT)
    ap.add_argument("--chrome", default=None, help="path to chrome.exe")
    ap.add_argument("--profile", default=str(cdp.DEFAULT_PROFILE))
    ap.add_argument("--match", default="boardgamearena",
                    help="substring identifying the game tab")
    ap.add_argument("--capture", type=int, default=0,
                    help="after probing, tap notifications for N seconds while you play")
    ap.add_argument("--no-launch", action="store_true",
                    help="attach to a Chrome already running with --remote-debugging-port")
    args = ap.parse_args()

    print("=" * 72)
    print(" Flip 7 recon — read-only inspection of a live Board Game Arena table")
    print("=" * 72)

    if not args.no_launch:
        print(f"\nLaunching Chrome (profile: {args.profile})")
        try:
            cdp.launch_chrome(BGA_URL, args.port, Path(args.profile), args.chrome)
        except cdp.CDPError as exc:
            print(f"\nERROR: {exc}")
            sys.exit(1)
        print("\nThis is a SEPARATE Chrome profile, so you will need to log into BGA")
        print("in it once. Then start or join a Flip 7 table.")

    input("\n>>> With a Flip 7 table open and a round in progress, press Enter... ")

    try:
        page = cdp.Page.attach(args.match, args.port)
    except cdp.CDPError as exc:
        print(f"\nERROR: {exc}")
        sys.exit(1)

    # The game runs in an iframe on BGA's tableview page, so address its context
    # directly rather than reaching through the parent (which cross-origin blocks).
    print("  locating the game frame ...", end=" ", flush=True)
    page.discover_contexts()
    ctx = page.find_context()
    ctx_id = ctx["id"] if ctx else None
    print(f"found in {ctx.get('origin') or ctx.get('name') or 'context'}" if ctx
          else "NOT FOUND (probing top document instead)")

    report: dict = {"probes": {},
                    "contexts": [{k: c.get(k) for k in ("id", "origin", "name")}
                                 for c in page.contexts],
                    "gameContextId": ctx_id}
    for name, js in PROBES.items():
        print(f"  probing {name} ...", end=" ", flush=True)
        report["probes"][name] = _run(page, name, js, ctx_id)
        print("ok")

    print("  installing notification tap ...", end=" ", flush=True)
    report["tap_install"] = _run(page, "tap", TAP_JS, ctx_id)
    if ctx_id is not None:  # also tap the wrapper: BGA publishes through both
        report["tap_install_top"] = _run(page, "tap", TAP_JS, None)
    print("ok")

    if args.capture:
        print(f"\n>>> Now PLAY for about {args.capture}s — flip cards, bust, stay, let a")
        print("    round end if you can. Every server message will be recorded.")
        for left in range(args.capture, 0, -5):
            print(f"    capturing... {left}s left ", end="\r", flush=True)
            time.sleep(min(5, left))
        print(" " * 50, end="\r")
        report["captured"] = _run(page, "collect", """
            const t = window.__flip7_tap || {events: [], errors: []};
            const byType = {};
            for (const e of t.events) byType[e.src + ':' + e.type] = (byType[e.src+':'+e.type]||0)+1;
            const interesting = t.events.filter(e =>
                /moveTokens|card|deck|shuffle|round|bust|stay|freeze|flip|score|player/i.test(e.type));
            return {count: t.events.length, byType, errors: t.errors,
                    events: interesting.slice(0, 300),
                    otherSample: t.events.filter(e => !interesting.includes(e)).slice(0, 40)};
        """)
        # Re-probe now that cards are on the table: the empty-board snapshot above
        # tells us far less than one taken mid-round.
        for name in ("gamedatas", "logs", "sprites", "deck_counter", "players_area"):
            report["probes"][name + "_after"] = _run(page, name, PROBES[name], ctx_id)

    page.close()

    out = Path(args.out)
    out.write_text(json.dumps(report, indent=2, default=str), encoding="utf-8")
    _summarise(report, out)


def _summarise(report: dict, out: Path) -> None:
    p = report.get("probes", {})
    g = p.get("globals", {}) or {}
    fw = p.get("framework", {}) or {}
    gd = p.get("gamedatas") or {}
    lg = p.get("logs", {}) or {}
    sp = p.get("sprites", {}) or {}
    dk = p.get("deck_counter", {}) or {}

    print("\n" + "=" * 72)
    print(" SUMMARY")
    print("=" * 72)
    print(f"  window.gameui           : {g.get('gameui')}")
    print(f"  dojo / ebg              : {g.get('dojo')} / {g.get('ebg')}")
    print(f"  notifqueue.onNotification: {fw.get('hasOnNotification')}")
    print(f"  notif_* handlers        : {len(fw.get('notifHandlers') or [])}")
    print(f"  gamedatas.board.cards   : "
          f"{gd.get('cardsLength') if isinstance(gd, dict) else 'n/a'}"
          f"   (the claim research could not verify)")
    if isinstance(gd, dict) and gd.get("cardsByLocation"):
        print(f"    by location           : {gd['cardsByLocation']}")
    print(f"  #logs found             : {lg.get('found')}  rows={lg.get('rowCount')}")
    print(f"  .flippable-front nodes  : {sp.get('flippableFrontCount')}")
    print(f"  .f7_deck counter        : {dk.get('found')}  text={dk.get('text')!r}")
    cap = report.get("captured")
    if cap:
        print(f"  notifications captured  : {cap.get('count')}")
        for k, v in sorted((cap.get("byType") or {}).items(), key=lambda x: -x[1])[:15]:
            print(f"      {v:4d}  {k}")
    print(f"\n  Report written to: {out.resolve()}")
    print("\n  NOTE: this file contains player names from the table. Skim it before")
    print("  sharing if that matters to you.")


if __name__ == "__main__":
    main()
