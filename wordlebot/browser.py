"""Drive a Wordle clone in a real browser.

Wordle clones disagree about markup but agree about how the game looks: a grid
of roughly square tiles, five per row, that turn green/yellow/grey. So the board
is found by geometry and read by colour, with data attributes and class names
used as a shortcut when a site happens to expose them. That works on sites this
code has never seen, which matters because there is no single "Wordle Unlimited".
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

from .game import MAX_GUESSES
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

  // Square-ish, small, holding at most one letter -- that is a Wordle tile.
  const cands = [];
  for (const el of document.querySelectorAll('*')) {
    const r = el.getBoundingClientRect();
    if (r.width < 18 || r.height < 18 || r.width > 220) continue;
    if (Math.abs(r.width - r.height) > Math.max(r.width, r.height) * 0.4) continue;
    if (el.children.length > 2) continue;
    const txt = (el.textContent || '').trim();
    if (txt.length > 1 || (txt && !/^[a-zA-Z]$/.test(txt))) continue;
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
  const grid = rows.filter(r => r.length >= 5)
                   .map(r => r.sort((a, b) => a.r.left - b.r.left).slice(0, 5));
  if (grid.length < 2) return null;
  return grid.map(row => row.map(c => ({
    letter: c.txt.toLowerCase(), bg: bgOf(c.el), marks: marksOf(c.el),
  })));
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
        try:
            self.page.keyboard.press("Escape")
        except Exception:
            pass

    def type_word(self, word: str) -> None:
        for ch in word:
            self.page.keyboard.press(ch)
            self.page.wait_for_timeout(35)
        self.page.keyboard.press("Enter")

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


def play_url(url: str, headless: bool = True, opener: str | None = None,
             max_guesses: int = MAX_GUESSES, screenshot: str | None = None,
             verbose: bool = True, reveal_timeout: float = 4.0) -> dict:
    """Play one game at `url`. Returns a summary dict."""
    from playwright.sync_api import sync_playwright

    solver = Solver(**({"opener": opener} if opener else {}))
    rejected: set[str] = set()
    played: list[tuple[str, int]] = []

    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=headless, **_launch_kwargs())
        page = browser.new_page(viewport={"width": 1280, "height": 1000})
        try:
            page.goto(url, wait_until="domcontentloaded", timeout=45000)
            page.wait_for_timeout(1200)
            game = BrowserGame(page, verbose)
            game.dismiss_overlays()
            try:
                rows = game.calibrate()
            except RuntimeError as exc:
                # Headless leaves nothing to look at, so leave evidence behind.
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

            # A refused word leaves the row untouched, so it costs an attempt but
            # not a turn; row and attempt counters have to move independently.
            row, attempts, choices = 0, 0, None
            while row < max_guesses and attempts < max_guesses + 25:
                attempts += 1
                if choices is None:  # ranking is expensive; reuse it across refusals
                    choices = _ranking(solver)
                guess = next((w for w in choices if w not in rejected), None)
                if guess is None:
                    game.log("no guess left that this site accepts")
                    break
                game.log(f"guess {row + 1}: {guess.upper()}  "
                         f"({len(solver.candidates)} candidates)")
                game.type_word(guess)
                pattern = game.read_row(row, timeout=reveal_timeout)
                if pattern is None:
                    game.log(f"  '{guess}' rejected by this site; trying another")
                    rejected.add(guess)
                    game.clear_row()
                    continue
                game.log(f"  -> {pattern_to_string(pattern)}")
                played.append((guess, pattern))
                row += 1
                choices = None  # state changed; re-rank
                if pattern == ALL_GREEN:
                    game.log(f"\nSolved '{guess.upper()}' in {len(played)} guesses.")
                    break
                try:
                    solver.observe(guess, pattern)
                except Unsolvable:
                    game.log("  feedback matches no known word -- this site uses a "
                             "wider dictionary than the bundled list")
                    break
            if screenshot:
                page.screenshot(path=screenshot, full_page=True)
                game.log(f"screenshot: {screenshot}")
        finally:
            browser.close()

    solved = bool(played) and played[-1][1] == ALL_GREEN
    return {"solved": solved, "guesses": len(played),
            "word": played[-1][0] if solved else None, "played": played}


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
            return {"executable_path": path}
    return {}


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
    ap.add_argument("url", help="page with a Wordle board on it")
    ap.add_argument("--no-headless", action="store_true", help="show the browser")
    ap.add_argument("--opener", default=None)
    ap.add_argument("--screenshot", default=None, help="save a PNG when finished")
    ap.add_argument("--games", type=int, default=1, help="play this many games")
    args = ap.parse_args()

    wins = 0
    totals = []
    for i in range(args.games):
        if args.games > 1:
            print(f"\n===== game {i + 1}/{args.games} =====")
        result = play_url(args.url, headless=not args.no_headless, opener=args.opener,
                          screenshot=args.screenshot)
        wins += result["solved"]
        if result["solved"]:
            totals.append(result["guesses"])
    if args.games > 1:
        avg = sum(totals) / len(totals) if totals else 0
        print(f"\nsolved {wins}/{args.games}   mean {avg:.2f} guesses")
    sys.exit(0 if wins else 1)


if __name__ == "__main__":
    main()
