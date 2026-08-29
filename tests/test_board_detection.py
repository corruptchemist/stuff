"""Board detection against a real page.

These cover the failure that broke a live site: an on-screen keyboard is also a
grid of single-letter squares, and a ">= 5 tiles per row" rule happily reports
its three rows as the board.
"""

import pytest

pytest.importorskip("playwright.sync_api", reason="playwright not installed")

from playwright.sync_api import sync_playwright  # noqa: E402

from wordlebot.browser import FIND_BOARD_JS, _launch_kwargs, play_page  # noqa: E402
from wordlebot.server import serve_background  # noqa: E402


@pytest.fixture(scope="module")
def site():
    url, httpd = serve_background()
    yield url
    httpd.shutdown()


@pytest.fixture(scope="module")
def page(site):
    with sync_playwright() as pw:
        try:
            browser = pw.chromium.launch(headless=True, **_launch_kwargs())
        except Exception as exc:  # no chromium available in this environment
            pytest.skip(f"cannot launch chromium: {exc}")
        pg = browser.new_page(viewport={"width": 1280, "height": 1000})
        pg.goto(site, wait_until="domcontentloaded")
        pg.wait_for_timeout(1500)
        yield pg
        browser.close()


def test_finds_the_board_not_the_keyboard(page):
    grid = page.evaluate(FIND_BOARD_JS)
    assert grid is not None, "no board detected"
    # Six rows of exactly five. The keyboard's three rows of 9-10 keys, and the
    # header buttons, must not appear.
    assert len(grid) == 6, f"expected 6 rows, got {len(grid)}"
    assert all(len(r) == 5 for r in grid), [len(r) for r in grid]


def test_board_starts_empty(page):
    grid = page.evaluate(FIND_BOARD_JS)
    assert all(t["letter"] == "" for row in grid for t in row)


def test_the_keyboard_is_present_and_still_ignored(page):
    """Guard against the test passing because the page has no keyboard at all."""
    keys = page.locator("#keyboard .key")
    assert keys.count() >= 26, "fixture lost its on-screen keyboard"


def test_plays_a_full_game_end_to_end(page):
    result = play_page(page, verbose=False, hud=False, linger=0)
    assert result["solved"], "did not solve the local game"
    assert 1 <= result["guesses"] <= 6
