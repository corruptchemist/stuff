"""Measure the solver against every official answer."""

from __future__ import annotations

import argparse
import time
from collections import Counter

from .game import MAX_GUESSES, play, won
from .solver import Solver
from .words import answers


def evaluate(opener: str, targets, max_guesses: int = MAX_GUESSES, quiet: bool = False):
    """Play every target and return (distribution, failures, mean guesses)."""
    solver = Solver(opener=opener)
    cache: dict = {}
    dist: Counter = Counter()
    failures: list[str] = []
    start = time.time()
    for i, answer in enumerate(targets, 1):
        played = play(answer, solver, max_guesses, guess_cache=cache)
        if won(played):
            dist[len(played)] += 1
        else:
            failures.append(answer)
        if not quiet and i % 250 == 0:
            print(f"  {i}/{len(targets)}  ({time.time() - start:.0f}s)", flush=True)
    mean = sum(k * v for k, v in dist.items()) / max(sum(dist.values()), 1)
    return dist, failures, mean


def report(opener: str, dist: Counter, failures: list[str], mean: float, total: int) -> None:
    solved = sum(dist.values())
    print(f"\nopener        {opener}")
    print(f"solved        {solved}/{total}  ({solved / total:.2%})")
    print(f"mean guesses  {mean:.4f}")
    print(f"worst case    {max(dist) if dist else '-'} guesses")
    print("distribution")
    for k in sorted(dist):
        bar = "#" * round(60 * dist[k] / total)
        print(f"  {k}: {dist[k]:5d}  {bar}")
    if failures:
        print(f"FAILURES ({len(failures)}): {', '.join(failures[:20])}")


def main() -> None:
    ap = argparse.ArgumentParser(description="Benchmark the Wordle solver.")
    ap.add_argument("--openers", nargs="*", help="compare these openers instead of the default")
    ap.add_argument("--limit", type=int, help="only play the first N answers (quick check)")
    ap.add_argument("--max-guesses", type=int, default=MAX_GUESSES)
    args = ap.parse_args()

    targets = answers()[: args.limit] if args.limit else answers()
    openers = args.openers or [Solver().opener]
    results = []
    for opener in openers:
        print(f"\n=== {opener} over {len(targets)} answers ===", flush=True)
        dist, failures, mean = evaluate(opener, targets, args.max_guesses)
        report(opener, dist, failures, mean, len(targets))
        results.append((mean, len(failures), opener, max(dist) if dist else 0))
    if len(results) > 1:
        print("\n=== ranking (fewest failures, then lowest worst case, then mean) ===")
        for mean, nfail, opener, worst in sorted(results, key=lambda r: (r[1], r[3], r[0])):
            print(f"  {opener:8s} mean {mean:.4f}  worst {worst}  failures {nfail}")


if __name__ == "__main__":
    main()
