# wordlebot

A Wordle solver that has never lost. Across all 2315 official answers it solves
**2315/2315**, averaging **3.43 guesses**, and has never needed more than **5** —
so on a six-guess board it always finishes with at least one guess to spare.

```
solved        2315/2315  (100.00%)
mean guesses  3.4307
worst case    5 guesses

  2 guesses:    94  ##
  3 guesses:  1231  ################################
  4 guesses:   889  #######################
  5 guesses:   101  ###
```

Reproduce it yourself with `python -m wordlebot.benchmark` (about 25 seconds).

## Install (Windows: just double-click)

Double-click **`setup.bat`** once, then **`play-browser.bat`** to watch it play or
**`play-manual.bat`** to have it coach you. That is the whole thing; the rest of
this section is what those scripts do, for anyone not on Windows.

## Install (manually)

First get the code -- every command below must be run from inside the repo
folder, not from your home directory.

```bash
git clone https://github.com/corruptchemist/stuff.git
cd stuff
git checkout claude/hello-47k5l8
```

No git? Download
[the ZIP](https://github.com/corruptchemist/stuff/archive/refs/heads/claude/hello-47k5l8.zip),
extract it, and `cd` into the extracted folder (`stuff-claude-hello-47k5l8`).

Then:

```bash
pip install -r requirements.txt
python -m wordlebot.benchmark --limit 200     # builds the pattern table once (~30s)
```

The first run precomputes a 14855 x 2315 table of every guess/answer colouring
and caches it in `.cache/` (33 MB). It prints a progress bar and takes roughly
30 seconds. **Let it finish** -- it only happens once, and every run afterwards
starts instantly.

Only if you want the browser driver, also download the browser itself -- pip
installs the Playwright library but not the Chromium it drives:

```bash
python -m playwright install chromium
```

On Windows use `python` (not `python3`), and run these from `cmd` or PowerShell
in the repo folder. Do not paste the trailing `# comments` from this README --
`cmd` passes them to the program as arguments instead of ignoring them.

## Use it

**Playing on a site yourself** -- the bot tells you what to type, you tell it
what colours came back:

```bash
python -m wordlebot.cli
```

```
--- guess 1/6 --- 2315 possible answers left
    runners-up: tarse (5.95 bits), tiare (5.93 bits), soare (5.89 bits)
  >> GUESS:  SALET
  colours (or :cmd) > bbybb

--- guess 2/6 --- 99 possible answers left
    runners-up: courb (4.83 bits), crony (4.80 bits), could (4.79 bits)
  >> GUESS:  COURD
  colours (or :cmd) >
```

Type colours as five characters: `g` green, `y` yellow, `b` grey (`0/1/2` and
`.?*` also work). Also `:list`, `:undo`, `:new`, `:word WORD` to overrule it.

**Letting it play by itself** in a real browser:

```bash
python -m wordlebot.browser "https://your-wordle-site.example/"
python -m wordlebot.browser "URL" --games 10       # play ten in a row
python -m wordlebot.browser "URL" --no-headless    # watch it, for debugging
```

By default no window opens and keystrokes go straight into the page over the
debug protocol, never through the OS -- so it will not steal focus or your
typing, and you can keep using the machine while it plays. Add `--no-headless`
only when you want to watch. If the board is not found, the run writes
`wordlebot-debug.png` and `wordlebot-debug.html` so you can see what the page
actually looked like.

## How it works

Wordle is small enough to solve properly, so this does not learn anything -- it
computes.

1. **Precompute every colouring.** All 14855 x 2315 guess/answer patterns, packed
   one per byte (a pattern is five base-3 digits, so it fits in 0-242). Built
   vectorised in numpy, ~28s once, then cached.
2. **Pick the guess that learns the most.** Each guess splits the remaining
   candidates into buckets by the colouring it would produce. A guess that
   leaves many small buckets tells you more than one that leaves a few big
   ones, which is exactly Shannon entropy over the bucket sizes -- so score
   every legal word by it and take the best. This is the "information theory"
   approach; it is why the bot opens with `SALET` rather than a word you would
   pick.
3. **Stop maximising information near the end and start counting.** Entropy is a
   proxy. With few candidates and few turns left the proxy leaks: the classic
   trap is being left with `hover / cover / rover / joker / poker ...`, all
   equally likely, one letter apart, and not enough turns to try them. So once
   the candidate set is small (or turns get short) the solver switches to exact
   minimax-style search over the real game tree, scoring lines by expected
   guesses and discarding any that cannot finish in the turns remaining.
4. **Prefer to finish early when it is free.** Before searching to the full turn
   budget it looks for a line that finishes a turn sooner, and only relaxes if
   none exists.

### What the measurements decided

Two knobs were set by running the full 2315-answer benchmark, not by intuition:

- **Opener.** `salet` won: mean 3.4307, worst case 5. Compare `trace` 3.4311,
  `crate` 3.4320, `slate` 3.4359 (and `slate` needs six guesses on one word).
  `python -m wordlebot.benchmark --openers salet crate trace raise` re-runs it.
- **Probe breadth in the endgame.** Letting the exact search consider only the
  14 highest-entropy probe words left four answers (`hover`, `joker`, `rower`,
  `woody`) needing all six guesses. Widening it to 120 removed them entirely,
  costing 0.002 guesses on the mean. A hard five-guess ceiling is worth more
  than a third decimal place on the average.

For reference, exhaustive search over the whole game tree (Selby, 2022) proves
the true optimum for this word list is 3.4212 with `salet`. This lands within
0.01 of that at a fraction of the compute.

## Playing on a real site

`wordlebot.browser` drives an actual browser. There is no single "Wordle
Unlimited" -- the clones share a look but not their markup -- so rather than
hardcode one site's selectors it finds the board the way you do:

- **Finds the grid geometrically.** Scans for square-ish elements holding at most
  one letter, groups them into rows by vertical position, and keeps the rows of
  five. No dependency on class names or DOM shape.
- **Reads the colours.** Uses `data-state` / class names (`correct`, `present`,
  `absent`, `green`, `misplaced`, ...) when a site exposes them, and otherwise
  classifies the tile's computed background colour. Works in light and dark
  themes, and on clones with their own palette.
- **Waits out the flip animation.** A row is only read once all five tiles have
  left their empty-tile colour, so a half-revealed row is never misread.
- **Survives a different dictionary.** If a site rejects a word, the row does not
  reveal; the bot notices, drops that word, and tries the next-best one without
  burning a turn. If the answer turns out to be outside the curated 2315-word
  list, the solver falls back to the full 14855-word list instead of failing.

If the board is not found, run with `--no-headless` to watch: the usual cause is
a how-to-play or cookie dialog covering the grid.

### Honest caveat

The sandbox this was built in blocks the public Wordle Unlimited domains, so the
driver was developed and tested against `tests/fixtures/wordle_clone.html`, a
local clone with the standard markup, staggered reveal animation, light and dark
themes, and a configurable dictionary — **not** against a live site. It goes
10/10 there. The detection is deliberately site-agnostic for that reason, but the
first real site you point it at is still the first real site it has seen.

## Layout

```
wordlebot/
  words.py        word lists and letter encoding
  patterns.py     colouring rules + vectorised precompute
  solver.py       entropy search, exact endgame, candidate tracking
  game.py         self-play
  benchmark.py    the numbers at the top of this file
  cli.py          interactive assistant
  browser.py      Playwright driver
  data/           2315 answers, 14855 accepted guesses
tests/            35 tests, incl. scorer vs vectorised-builder agreement
```

Run the tests with `python -m pytest tests/ -q`.

## Word lists

`data/answers.txt` is the 2315-word official Wordle solution list;
`data/allowed.txt` is the 14855 words Wordle accepts as guesses (a superset).
Sourced from the public mirrors of the original game's lists.
