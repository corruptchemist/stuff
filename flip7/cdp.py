"""Minimal Chrome DevTools Protocol client.

Deliberately not Playwright: CDP is just JSON over a WebSocket, and keeping the
dependency to one small pure-Python package makes the PyInstaller build simple
and means we attach to a Chrome that is already running rather than shipping a
second browser.
"""

from __future__ import annotations

import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

import websocket  # websocket-client

DEFAULT_PORT = 9222
# Chrome 136+ refuses --remote-debugging-port on the default profile directory,
# so the bot always drives a profile of its own. You log into BGA once in it.
DEFAULT_PROFILE = Path.home() / ".flip7-chrome"

CHROME_PATHS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    "/opt/pw-browsers/chromium",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
]


class CDPError(RuntimeError):
    pass


def find_chrome(explicit: str | None = None) -> str:
    for path in ([explicit] if explicit else []) + CHROME_PATHS:
        if path and Path(path).exists():
            return path
    raise CDPError(
        "Could not find Chrome. Pass --chrome with the full path to chrome.exe."
    )


def launch_chrome(url: str = "about:blank", port: int = DEFAULT_PORT,
                  profile: Path = DEFAULT_PROFILE, chrome: str | None = None,
                  quiet: bool = True, headless: bool = False,
                  extra_args: list[str] | None = None) -> subprocess.Popen | None:
    """Start Chrome with remote debugging, or open a tab in the one already running."""
    if _debugger_alive(port):
        if url and url != "about:blank":
            open_tab(url, port)
        return None
    profile.mkdir(parents=True, exist_ok=True)
    args = [
        find_chrome(chrome),
        f"--remote-debugging-port={port}",
        f"--user-data-dir={profile}",
        "--no-first-run",
        "--no-default-browser-check",
    ]
    if quiet:
        args += ["--disable-background-networking", "--disable-component-update",
                 "--disable-sync", "--disable-default-apps"]
    if headless:
        args.append("--headless=new")
    args += extra_args or []
    args.append(url)
    proc = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(80):  # up to ~20s for the debugger socket to come up
        if _debugger_alive(port):
            return proc
        time.sleep(0.25)
    raise CDPError(f"Chrome started but no debugger appeared on port {port}.")


def _debugger_alive(port: int) -> bool:
    try:
        urllib.request.urlopen(f"http://127.0.0.1:{port}/json/version", timeout=1)
        return True
    except (urllib.error.URLError, OSError):
        return False


def open_tab(url: str, port: int = DEFAULT_PORT) -> dict | None:
    """Open a new tab in the already-running Chrome, if it isn't open already."""
    for t in list_targets(port):
        if t.get("url", "").startswith(url.split("?")[0]):
            return t
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}/json/new?{urllib.parse.quote(url, safe='')}",
        method="PUT")
    try:
        with urllib.request.urlopen(req, timeout=5) as fh:
            return json.load(fh)
    except urllib.error.HTTPError:
        return None  # older Chrome wants GET; the tab may still have opened


def list_targets(port: int = DEFAULT_PORT) -> list[dict]:
    with urllib.request.urlopen(f"http://127.0.0.1:{port}/json", timeout=5) as fh:
        return [t for t in json.load(fh) if t.get("type") == "page"]


def find_target(match: str, port: int = DEFAULT_PORT) -> dict:
    """First page tab whose URL or title contains `match` (case-insensitive)."""
    needle = match.lower()
    for t in list_targets(port):
        if needle in (t.get("url", "") + " " + t.get("title", "")).lower():
            return t
    raise CDPError(
        f"No open tab matching {match!r}. Open the game in the Chrome this tool "
        f"launched (the one using the {DEFAULT_PROFILE} profile), then retry."
    )


class Page:
    """A CDP connection to one tab. Only needs Runtime.evaluate."""

    def __init__(self, ws_url: str, timeout: float = 20.0):
        # Chrome 111+ rejects a CDP socket that carries an Origin header unless
        # started with --remote-allow-origins. Sending none sidesteps it, and
        # works whether we launched this Chrome or attached to an existing one.
        self.ws = websocket.create_connection(ws_url, timeout=timeout,
                                              suppress_origin=True,
                                              max_size=64 * 1024 * 1024)
        self._id = 0

    @classmethod
    def attach(cls, match: str, port: int = DEFAULT_PORT) -> "Page":
        return cls(find_target(match, port)["webSocketDebuggerUrl"])

    def send(self, method: str, params: dict | None = None) -> dict:
        self._id += 1
        self.ws.send(json.dumps({"id": self._id, "method": method,
                                 "params": params or {}}))
        while True:  # skip unsolicited events until our reply arrives
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self._id:
                if "error" in msg:
                    raise CDPError(f"{method}: {msg['error'].get('message')}")
                return msg.get("result", {})

    def evaluate(self, expression: str):
        """Run JS in the page and return the value, JSON round-tripped.

        Wrapped in an IIFE returning JSON text: returnByValue chokes on DOM
        nodes and cyclic objects, and stringifying in-page sidesteps both.
        """
        wrapped = f"JSON.stringify((() => {{ {expression} }})())"
        result = self.send("Runtime.evaluate", {
            "expression": wrapped, "returnByValue": True, "awaitPromise": True,
        })
        if result.get("exceptionDetails"):
            exc = result["exceptionDetails"]
            raise CDPError(exc.get("exception", {}).get("description") or exc.get("text"))
        raw = result.get("result", {}).get("value")
        return json.loads(raw) if raw is not None else None

    def close(self) -> None:
        try:
            self.ws.close()
        except Exception:
            pass
