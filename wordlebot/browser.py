"""Drive a Wordle clone in a real browser.

Wordle clones disagree about markup but agree about how the game looks: a grid
of roughly square tiles, five per row, that turn green/yellow/grey. So the board
is found by geometry and read by colour, with data attributes and class names
used as a shortcut when a site happens to expose them. That works on sites this
code has never seen, which matters because there is no single "Wordle Unlimited".
"""

from __future__ import annotations

import argparse
import math
from collections import Counter
import os
import sys
import time
from pathlib import Path

from .game import MAX_GUESSES, MEAN_GUESSES, TOTAL_BITS, beat_fraction
from .patterns import ALL_GREEN, GREEN, GREY, YELLOW, pattern_to_string
from .solver import Solver, Unsolvable

# Injected into the page: return the tile grid as rows of {letter, bg, marks}.
FIND_BOARD_JS = r"""
() => {
  const bgOf = (el) => {
    for (let cur = el; cur; cur = cur.parentElement) {
      const m = getComputedStyle(cur).backgroundColor.match(/rgba?\(([^)]+)\)/);
      if (!m) continue;
      const p = m[1].split(',').map(Number);
      if ((p.length > 3 ? p[3] : 1) > 0.1) return [p[0], p[1], p[2]];
    }
    return [0, 0, 0];
  };
  const marksOf = (el) => {
    const bits = [];
    for (let cur = el, d = 0; cur && d < 3; cur = cur.parentElement, d++) {
      if (cur.className && typeof cur.className === 'string') bits.push(cur.className);
      for (const k of ['state', 'evaluation', 'letter', 'status'])
        if (cur.dataset && cur.dataset[k]) bits.push(cur.dataset[k]);
    }
    return bits.join(' ').toLowerCase();
  };

  // An on-screen keyboard is also a grid of single-letter squares, so it has
  // to be excluded explicitly or it gets mistaken for the board.
  const isKeyboard = (el) => {
    for (let cur = el, d = 0; cur && d < 4; cur = cur.parentElement, d++) {
      if (cur.tagName === 'BUTTON' || cur.getAttribute?.('role') === 'button') return true;
      const id = ((cur.className && typeof cur.className === 'string' ? cur.className : '')
                  + ' ' + (cur.id || '')).toLowerCase();
      if (/keyboard|\bkey\b|\bkeys\b|keypad/.test(id)) return true;
    }
    return false;
  };

  // Square-ish, small, holding at most one letter -- that is a Wordle tile.
  const cands = [];
  for (const el of document.querySelectorAll('*')) {
    const r = el.getBoundingClientRect();
    if (r.width < 18 || r.height < 18 || r.width > 220) continue;
    if (Math.abs(r.width - r.height) > Math.max(r.width, r.height) * 0.4) continue;
    if (el.children.length > 2) continue;
    const txt = (el.textContent || '').trim();
    if (txt.length > 1 || (txt && !/^[a-zA-Z]$/.test(txt))) continue;
    if (isKeyboard(el)) continue;
    cands.push({ el, r, txt });
  }
  // Drop inner wrappers: same box as an ancestor already counted.
  const kept = cands.filter(c => !cands.some(o =>
    o !== c && o.el.contains(c.el) &&
    Math.abs(o.r.width - c.r.width) < 4 && Math.abs(o.r.height - c.r.height) < 4));

  // Group into rows by vertical position.
  kept.sort((a, b) => a.r.top - b.r.top || a.r.left - b.r.left);
  const rows = [];
  for (const c of kept) {
    const row = rows.find(r => Math.abs(r[0].r.top - c.r.top) < c.r.height * 0.6);
    if (row) row.push(c); else rows.push([c]);
  }
  // A Wordle row is exactly five wide. Keyboard rows are nine or ten, so
  // requiring an exact five throws them out; only fall back to a looser rule
  // if nothing matches at all.
  rows.forEach(r => r.sort((a, b) => a.r.left - b.r.left));
  let grid = rows.filter(r => r.length === 5);
  if (grid.length < 2) grid = rows.filter(r => r.length >= 5).map(r => r.slice(0, 5));
  // Board rows line up with each other; stray five-square groups elsewhere on
  // the page do not. Keep the largest set sharing a left edge and width.
  if (grid.length > 1) {
    const key = (r) => Math.round(r[0].r.left / 4) + ':' + Math.round(r[0].r.width / 4);
    const groups = {};
    for (const r of grid) (groups[key(r)] ||= []).push(r);
    grid = Object.values(groups).sort((a, b) => b.length - a.length)[0];
  }
  if (grid.length < 2) return null;
  return grid.map(row => row.map(c => ({
    letter: c.txt.toLowerCase(), bg: bgOf(c.el), marks: marksOf(c.el),
  })));
}
"""

HUD_JS = r"""
(d) => {
  let el = document.getElementById('wordlebot-hud');
  if (!el) {
    el = document.createElement('div');
    el.id = 'wordlebot-hud';
    el.style.cssText = 'position:fixed;top:12px;right:12px;z-index:2147483647;' +
      'width:290px;padding:14px 16px;border-radius:12px;background:#11131a;' +
      'color:#e8eaf0;font:12px/1.45 ui-monospace,Consolas,monospace;' +
      'box-shadow:0 8px 32px rgba(0,0,0,.55);border:1px solid #2a2f3d';
    document.body.appendChild(el);
  }
  const bar = (frac, colour) => {
    const n = Math.max(0, Math.min(20, Math.round(frac * 20)));
    return '<span style="color:' + colour + '">' + '\u2588'.repeat(n) +
           '</span><span style="color:#2a2f3d">' + '\u2588'.repeat(20 - n) + '</span>';
  };
  const dots = (p) => p.split('').map(c => {
    const col = c === 'g' ? '#4f9d55' : (c === 'y' ? '#b8992f' : '#3a3f4d');
    return '<span style="color:' + col + '">\u25a0</span>';
  }).join('');

  const rows = d.turns.map(t =>
    '<tr>' +
    '<td style="color:#7d8598">' + t.n + '</td>' +
    '<td style="letter-spacing:.5px"><b>' + t.guess.toUpperCase() + '</b></td>' +
    '<td>' + dots(t.pattern) + '</td>' +
    '<td style="text-align:right;color:#8fb8ff">' + t.actual.toFixed(1) + 'b</td>' +
    '<td style="text-align:right;color:' + (t.luck >= 0 ? '#4f9d55' : '#c05c5c') + '">' +
      (t.luck >= 0 ? '+' : '') + t.luck.toFixed(1) + '</td>' +
    '</tr>').join('');

  el.innerHTML =
    '<div style="display:flex;justify-content:space-between;align-items:baseline;' +
      'border-bottom:1px solid #2a2f3d;padding-bottom:8px;margin-bottom:10px">' +
      '<b style="letter-spacing:1.5px;font-size:13px">WORDLEBOT</b>' +
      '<span style="color:#7d8598">' + d.candidates.toLocaleString() + ' left</span></div>' +
    '<div style="color:#7d8598">solved</div>' +
    '<div style="margin:2px 0 10px">' + bar(d.progress, '#4f9d55') +
      ' <span style="color:#e8eaf0">' + Math.round(d.progress * 100) + '%</span></div>' +
    (rows ? '<table style="width:100%;border-collapse:collapse;margin-bottom:10px">' +
      '<tr style="color:#5c6478;font-size:10px"><td></td><td>GUESS</td><td></td>' +
      '<td style="text-align:right">INFO</td><td style="text-align:right">LUCK</td></tr>' +
      rows + '</table>' : '') +
    '<div style="border-top:1px solid #2a2f3d;padding-top:9px">' +
      d.stats.map(x =>
        '<div style="display:flex;justify-content:space-between;margin:3px 0">' +
        '<span style="color:#7d8598">' + x[0] + '</span>' +
        '<span style="color:' + (x[2] || '#e8eaf0') + '">' + x[1] + '</span></div>').join('') +
    '</div>' +
    (d.verdict ? '<div style="margin-top:10px;padding:9px;border-radius:7px;' +
      'background:#1a2a1c;color:#7ede86;text-align:center">' + d.verdict + '</div>' : '');
}
"""


_GREEN_WORDS = ("correct", "green", "exact", "right-position", "match")
_YELLOW_WORDS = ("present", "yellow", "misplaced", "wrong-position", "close", "partial")
_GREY_WORDS = ("absent", "gray", "grey", "wrong", "miss", "none")


def colour_of(tile: dict, baseline: list[int] | None) -> int | None:
    """Classify one tile as GREEN/YELLOW/GREY, or None if not yet revealed."""
    marks = tile.get("marks", "")
    for words, colour in ((_GREEN_WORDS, GREEN), (_YELLOW_WORDS, YELLOW), (_GREY_WORDS, GREY)):
        if any(w in marks for w in words):
            return colour
    r, g, b = tile["bg"]
    if baseline is not None and max(abs(r - baseline[0]), abs(g - baseline[1]),
                                   abs(b - baseline[2])) < 12:
        return None  # still the empty-tile colour
    spread = (max(r, g, b) - min(r, g, b)) / 255
    if spread < 0.06:
        return GREY
    if g >= r and g >= b:
        return GREEN
    if r >= g >= b:
        return YELLOW
    return GREY


def _pattern(row: list[dict], baseline) -> int | None:
    colours = [colour_of(t, baseline) for t in row]
    if any(c is None for c in colours):
        return None
    return sum(c * 3**i for i, c in enumerate(colours))


class BrowserGame:
    """A live Wordle board driven through Playwright."""

    def __init__(self, page, verbose: bool = True):
        self.page = page
        self.verbose = verbose
        self.baseline: list[int] | None = None

    def log(self, msg: str) -> None:
        if self.verbose:
            print(msg, flush=True)

    def board(self):
        return self.page.evaluate(FIND_BOARD_JS)

    def calibrate(self) -> int:
        """Learn the empty-tile colour and return the number of rows found."""
        grid = self.board()
        if not grid:
            raise RuntimeError(
                "could not find a Wordle board on this page -- is the game visible? "
                "(try --no-headless to watch, and dismiss any cookie/how-to dialog)"
            )
        self.baseline = grid[-1][-1]["bg"]  # last tile of last row: never played
        return len(grid)

    def dismiss_overlays(self) -> None:
        """Close the how-to-play / cookie dialogs clones tend to open with."""
        for sel in ("button:has-text('Play')", "button:has-text('Accept')",
                    "button:has-text('Got it')", "button:has-text('Continue')",
                    "[aria-label='Close']", ".close", "#close"):
            try:
                loc = self.page.locator(sel).first
                if loc.is_visible(timeout=400):
                    loc.click(timeout=1000)
                    self.page.wait_for_timeout(250)
            except Exception:
                pass
        self.blur()

    def blur(self) -> None:
        """Drop focus from any button we clicked.

        A focused button swallows Enter: the key both submits the guess and
        re-activates the button, which on most boards starts a new game
        underneath us mid-play.
        """
        try:
            self.page.keyboard.press("Escape")
            self.page.evaluate("() => document.activeElement && document.activeElement.blur()")
        except Exception:
            pass

    def row_letters(self, index: int) -> str:
        grid = self.board()
        if not grid or index >= len(grid):
            return ""
        return "".join(t["letter"] for t in grid[index])

    def type_word(self, word: str, index: int, tries: int = 3) -> bool:
        """Type `word` into row `index` and submit it.

        Sites lock input while the previous row is still flipping, which
        silently eats the first keystrokes. So confirm the row really holds the
        word before pressing Enter -- otherwise a dropped letter looks exactly
        like the site rejecting a perfectly good guess.
        """
        for attempt in range(tries):
            for ch in word:
                self.page.keyboard.press(ch)
                self.page.wait_for_timeout(35)
            if self.row_letters(index) == word:
                self.page.keyboard.press("Enter")
                return True
            self.clear_row(len(word) + 2)
            self.page.wait_for_timeout(250 * (attempt + 1))
        return False

    def clear_row(self, n: int = 5) -> None:
        for _ in range(n):
            self.page.keyboard.press("Backspace")
            self.page.wait_for_timeout(30)

    def read_row(self, index: int, timeout: float = 6.0) -> int | None:
        """Wait for row `index` to finish revealing; None if it never does."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            grid = self.board()
            if grid and index < len(grid):
                pattern = _pattern(grid[index], self.baseline)
                if pattern is not None:
                    return pattern
            self.page.wait_for_timeout(150)
        return None



def play_page(page, opener: str | None = None, max_guesses: int = MAX_GUESSES,
              verbose: bool = True, reveal_timeout: float = 4.0,
              hud: bool = True, linger: int = 2500, settle: int = 400) -> dict:
    """Play one game on an already-loaded board. Returns a summary dict."""
    solver = Solver(**({"opener": opener} if opener else {}))
    rejected: set[str] = set()
    played: list[tuple[str, int]] = []
    turns: list[dict] = []

    game = BrowserGame(page, verbose)
    game.dismiss_overlays()
    try:
        rows = game.calibrate()
    except RuntimeError as exc:
        shot, html = Path("wordlebot-debug.png"), Path("wordlebot-debug.html")
        try:
            page.screenshot(path=str(shot), full_page=True)
            html.write_text(page.content(), encoding="utf-8")
        except Exception:
            pass
        raise RuntimeError(
            f"{exc}\n\nSaved {shot} and {html} so the page can be inspected."
            "\nA cookie or how-to-play dialog covering the grid is the usual"
            " cause; re-run with --no-headless to watch it happen."
        ) from None
    game.log(f"board found: {rows} rows")
    max_guesses = min(max_guesses, rows)

    def draw(verdict=None):
        if not hud:
            return
        # On a win the solver is never told the answer, so its candidate count
        # is one turn stale; the board is in fact fully resolved.
        n = 1 if verdict else len(solver.candidates)
        try:
            page.evaluate(HUD_JS, {
                "candidates": n,
                "progress": min(1.0, (TOTAL_BITS - math.log2(max(n, 1))) / TOTAL_BITS),
                "turns": [{"n": t["n"], "guess": t["guess"], "pattern": t["pattern"],
                           "actual": t["actual"], "luck": t["actual"] - t["expected"]}
                          for t in turns],
                "stats": _stats(turns, n, verdict is not None),
                "verdict": verdict,
            })
        except Exception:
            pass  # a HUD is never worth failing a game over

    draw()
    # A refused word leaves the row untouched, so it costs an attempt but not a
    # turn; row and attempt counters have to move independently.
    row, attempts, choices, ranked, best_bits = 0, 0, None, [], 0.0
    while row < max_guesses and attempts < max_guesses + 25:
        attempts += 1
        if choices is None:  # ranking is expensive; reuse it across refusals
            ranked = solver.ranked(6)
            best_bits = max((b for _, b in ranked), default=0.0)
            choices = [w for w, _ in ranked]
        guess = next((w for w in choices if w not in rejected), None)
        if guess is None:
            choices = _ranking(solver)  # everything shortlisted was refused
            guess = next((w for w in choices if w not in rejected), None)
        if guess is None:
            game.log("no guess left that this site accepts")
            break

        expected = next((b for w, b in ranked if w == guess), 0.0)
        before = len(solver.candidates)
        game.log(f"guess {row + 1}: {guess.upper()}  ({before} candidates)")
        if not game.type_word(guess, row):
            game.log(f"  could not enter '{guess}' -- the board is not accepting input")
            break
        pattern = game.read_row(row, timeout=reveal_timeout)
        if pattern is None:
            game.log(f"  '{guess}' rejected by this site; trying another")
            rejected.add(guess)
            game.clear_row()
            continue
        game.log(f"  -> {pattern_to_string(pattern)}")
        # The row is coloured but the site may still be animating and refusing
        # input; let it settle before the next word.
        page.wait_for_timeout(settle)
        played.append((guess, pattern))
        row += 1

        solved = pattern == ALL_GREEN
        if not solved:
            try:
                solver.observe(guess, pattern)
            except Unsolvable:
                game.log("  feedback matches no known word -- this site uses a "
                         "wider dictionary than the bundled list")
                break
        after = 1 if solved else max(len(solver.candidates), 1)
        turns.append({
            "n": row, "guess": guess, "pattern": pattern_to_string(pattern),
            "expected": expected, "actual": math.log2(before / after),
            # 1.0 when it played the most informative guess available to it.
            "quality": (expected / best_bits) if best_bits > 0 else 1.0,
        })
        choices = None  # state changed; re-rank
        if solved:
            game.log(f"\nSolved '{guess.upper()}' in {len(played)} guesses.")
            draw(f"SOLVED {guess.upper()} in {len(played)}")
            page.wait_for_timeout(linger)
            break
        draw()
    else:
        draw()

    solved = bool(played) and played[-1][1] == ALL_GREEN
    return {"solved": solved, "guesses": len(played),
            "word": played[-1][0] if solved else None, "played": played}


def start_next_game(page) -> None:
    """Ask the board for a fresh puzzle, falling back to a reload."""
    for sel in ("button:has-text('New game')", "button:has-text('New Game')",
                "button:has-text('Play again')", "button:has-text('Next')",
                "#new", ".new-game"):
        try:
            loc = page.locator(sel).first
            if loc.is_visible(timeout=500):
                loc.click(timeout=1500)
                page.wait_for_timeout(700)
                page.evaluate(
                    "() => { document.getElementById('wordlebot-hud')?.remove();"
                    "  document.activeElement && document.activeElement.blur(); }"
                )
                return
        except Exception:
            pass
    page.reload(wait_until="domcontentloaded")
    page.wait_for_timeout(1000)


def play_session(url: str, games: int = 1, headless: bool = True, **kw) -> list[dict]:
    """Play `games` rounds in a single browser window."""
    from playwright.sync_api import sync_playwright

    screenshot = kw.pop("screenshot", None)
    results = []
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=headless, **_launch_kwargs())
        page = browser.new_page(viewport={"width": 1280, "height": 1000})
        try:
            page.goto(url, wait_until="domcontentloaded", timeout=45000)
            page.wait_for_timeout(1200)
            for i in range(games):
                if i:
                    if games > 1:
                        print(f"\n===== game {i + 1}/{games} =====")
                    start_next_game(page)
                results.append(play_page(page, **kw))
            if screenshot:
                page.screenshot(path=screenshot, full_page=True)
                print(f"screenshot: {screenshot}")
        finally:
            browser.close()
    return results


def play_url(url: str, headless: bool = True, **kw) -> dict:
    """Play a single game at `url` (kept for callers that want just one)."""
    return play_session(url, games=1, headless=headless, **kw)


def _stats(turns: list[dict], candidates: int, solved: bool) -> list:
    """Summary rows for the HUD. Every number here is measured, not cosmetic."""
    exp = sum(t["expected"] for t in turns)
    act = sum(t["actual"] for t in turns)
    rows = []
    if turns:
        # >100% means the splits fell better than the guesses predicted: luck.
        rows.append(("info gained", f"{act:.1f} of {TOTAL_BITS:.1f} bits", None))
        rows.append(("info rate", f"{act / len(turns):.1f} bits/guess", None))
        # >100% means the splits landed better than the guesses predicted: luck,
        # not skill. The solver's choices are already the best available to it.
        luck = act / exp if exp else 1.0
        rows.append(("luck", f"{luck:.0%} of expected",
                     "#4f9d55" if luck >= 1 else "#c9a227"))
        # <100% is usually the solver correctly preferring a word that can win
        # outright over a probe that would reveal more, so it is not a demerit.
        quality = sum(t["quality"] for t in turns) / len(turns)
        rows.append(("vs best info", f"{quality:.0%}", None))
    if solved:
        n = len(turns)
        beat = beat_fraction(n)
        rows.append(("guesses", f"{n}  (avg {MEAN_GUESSES:.2f})", None))
        rows.append(("faster than", f"{beat:.0%} of games",
                     "#4f9d55" if beat >= 0.4 else "#c9a227"))
    return rows


# Chromium phones home on startup (sync, component updates, safe browsing).
# None of it helps here, and on a restricted network each attempt stalls.
_QUIET_ARGS = [
    "--disable-background-networking", "--disable-component-update",
    "--disable-sync", "--disable-default-apps", "--no-first-run",
    "--no-default-browser-check", "--disable-domain-reliability",
    "--disable-client-side-phishing-detection",
]


def _launch_kwargs() -> dict:
    """Use a preinstalled Chromium when Playwright's own build is missing.

    Sandboxes often ship one Chromium that does not match the pip package's
    pinned build; pointing at it beats failing or trying to download.
    """
    override = os.environ.get("WORDLEBOT_CHROME")
    candidates = [override] if override else []
    candidates += ["/opt/pw-browsers/chromium", "/usr/bin/chromium",
                   "/usr/bin/chromium-browser", "/usr/bin/google-chrome"]
    for path in candidates:
        if path and Path(path).exists():
            return {"executable_path": path, "args": _QUIET_ARGS}
    return {"args": _QUIET_ARGS}


def _ranking(solver: Solver) -> list[str]:
    """Guesses worth trying for this position, best first.

    Every clone ships its own dictionary, so the tail matters: if a site refuses
    the whole ranked list we still want to walk the rest of the word list rather
    than give up on the game.
    """
    best = [w for w, _ in solver.ranked(400)]
    seen = set(best)
    tail = [w for w in list(solver.candidates) + list(solver.guess_pool) if w not in seen]
    return best + tail


def main() -> None:
    ap = argparse.ArgumentParser(description="Play Wordle in a browser.")
    ap.add_argument("url", nargs="?", help="page with a Wordle board on it")
    ap.add_argument("--local", action="store_true",
                    help="serve the bundled Wordle on localhost and play that")
    ap.add_argument("--no-headless", action="store_true", help="show the browser")
    ap.add_argument("--opener", default=None)
    ap.add_argument("--screenshot", default=None, help="save a PNG when finished")
    ap.add_argument("--games", type=int, default=1, help="play this many games")
    ap.add_argument("--no-hud", action="store_true", help="don't draw the stats overlay")
    ap.add_argument("--linger", type=int, default=2500,
                    help="ms to hold on the finished board so you can read it")
    args = ap.parse_args()

    url = args.url
    if args.local or not url:
        from .server import serve_background
        url, _httpd = serve_background()
        print(f"serving the bundled Wordle at {url}")
    elif args.local and url:
        ap.error("pass a URL or --local, not both")

    results = play_session(url, games=args.games, headless=not args.no_headless,
                           opener=args.opener, screenshot=args.screenshot,
                           hud=not args.no_hud, linger=args.linger)
    wins = sum(r["solved"] for r in results)
    totals = [r["guesses"] for r in results if r["solved"]]
    if args.games > 1:
        avg = sum(totals) / len(totals) if totals else 0
        dist = Counter(totals)
        print(f"\nsolved {wins}/{args.games}   mean {avg:.2f} guesses")
        print("  " + "  ".join(f"{k}:{dist[k]}" for k in sorted(dist)))
    sys.exit(0 if wins else 1)


if __name__ == "__main__":
    main()
