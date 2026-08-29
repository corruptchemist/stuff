"""Interactive solver: you play, the bot tells you what to guess."""

from __future__ import annotations

import argparse
import sys

from .game import MAX_GUESSES
from .patterns import ALL_GREEN, parse_pattern, pattern_to_string
from .solver import Solver, Unsolvable

HELP = """
Type the colours Wordle gave your guess, as five characters:
  g = green    y = yellow    b = grey/black   (0/1/2 and .?* also work)
e.g.  bygbb

Other commands:
  :word WORD   use WORD as your guess instead of the suggestion
  :list        show remaining candidate words
  :undo        take back the last guess
  :new         start over
  :quit        exit
"""


def _prompt(text: str) -> str:
    try:
        return input(text).strip()
    except (EOFError, KeyboardInterrupt):
        print()
        sys.exit(0)


def run(solver: Solver, max_guesses: int = MAX_GUESSES) -> None:
    print("Wordle bot. Type ':help' for commands, Ctrl-C to quit.")
    turn = 1
    undo_stack: list = []
    while turn <= max_guesses:
        cands = solver.candidates
        ranked = solver.ranked(5)
        guess = ranked[0][0]
        print(f"\n--- guess {turn}/{max_guesses} --- {len(cands)} possible answer"
              f"{'' if len(cands) == 1 else 's'} left")
        if len(cands) <= 8:
            print(f"    could be: {', '.join(cands)}")
        elif len(ranked) > 1:
            alts = ", ".join(f"{w} ({e:.2f} bits)" for w, e in ranked[1:4])
            print(f"    runners-up: {alts}")
        print(f"  >> GUESS:  {guess.upper()}")

        while True:
            reply = _prompt("  colours (or :cmd) > ")
            low = reply.lower()
            if low in (":q", ":quit", ":exit"):
                return
            if low in (":h", ":help", "help", "?"):
                print(HELP)
                continue
            if low in (":l", ":list"):
                print(f"    {len(cands)} candidates: {', '.join(cands[:200])}"
                      f"{' ...' if len(cands) > 200 else ''}")
                continue
            if low in (":n", ":new"):
                solver.reset()
                undo_stack.clear()
                turn = 1
                break
            if low in (":u", ":undo"):
                if not undo_stack:
                    print("    nothing to undo")
                    continue
                history = undo_stack.pop()
                solver.reset()
                for g, p in history:
                    solver.observe(g, p)
                turn = len(history) + 1
                break
            if low.startswith(":word ") or low.startswith(":w "):
                word = low.split(None, 1)[1].strip()
                if word not in solver.guess_pool:
                    print(f"    {word!r} is not a word Wordle accepts")
                    continue
                guess = word
                print(f"  >> using your word: {guess.upper()}")
                continue
            try:
                pattern = parse_pattern(reply)
            except ValueError as exc:
                print(f"    {exc}. Try 'g/y/b' x5, or ':help'.")
                continue
            if pattern == ALL_GREEN:
                print(f"\n  Solved: {guess.upper()} in {turn} guess"
                      f"{'' if turn == 1 else 'es'}.")
                return
            undo_stack.append(list(solver.history))
            try:
                solver.observe(guess, pattern)
            except Unsolvable:
                print("\n  No word matches that feedback. Check the colours you typed"
                      " (':undo' to take one back).")
                return
            turn += 1
            break
    print("\n  Out of guesses.")


def main() -> None:
    ap = argparse.ArgumentParser(description="Interactive Wordle solver.")
    ap.add_argument("--opener", default=None, help="force a specific first guess")
    ap.add_argument("--max-guesses", type=int, default=MAX_GUESSES)
    ap.add_argument(
        "--full-pool", action="store_true",
        help="treat every accepted word as a possible answer, not just the curated "
             "Wordle solution list (slower first run, builds a bigger table)",
    )
    args = ap.parse_args()

    kwargs = {}
    if args.opener is not None:
        kwargs["opener"] = args.opener.lower()
    if args.full_pool:
        from .words import allowed
        kwargs["answer_pool"] = allowed()
        print("Building the full-pool table; this takes a few minutes the first time...")
    run(Solver(**kwargs), args.max_guesses)


if __name__ == "__main__":
    main()
