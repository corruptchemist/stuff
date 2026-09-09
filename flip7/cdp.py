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
    """Open a new tab in the already-running Chrome, if it isn't open already.

    Chrome has moved this endpoint between verbs across versions -- newer
    builds require PUT, older ones only answer GET -- so try both rather than
    silently failing and leaving the user with no window.
    """
    target = f"http://127.0.0.1:{port}/json/new?{urllib.parse.quote(url, safe='')}"
    for t in list_targets(port):
        if t.get("url", "").startswith(url.split("?")[0]):
            return t
    for method in ("PUT", "GET"):
        try:
            req = urllib.request.Request(target, method=method)
            with urllib.request.urlopen(req, timeout=5) as fh:
                return json.load(fh)
        except Exception:
            continue
    return None


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
        self._timeout = timeout
        self.contexts: list[dict] = []
        self._events: list[dict] = []

    @classmethod
    def attach(cls, match: str, port: int = DEFAULT_PORT) -> "Page":
        return cls(find_target(match, port)["webSocketDebuggerUrl"])

    def send(self, method: str, params: dict | None = None) -> dict:
        self._id += 1
        self.ws.send(json.dumps({"id": self._id, "method": method,
                                 "params": params or {}}))
        while True:
            msg = json.loads(self.ws.recv())
            if "method" in msg:
                # Buffer, never discard: executionContextCreated events arrive
                # BEFORE the Runtime.enable reply, and dropping them here was
                # why context discovery came back empty.
                self._events.append(msg)
                continue
            if msg.get("id") == self._id:
                if "error" in msg:
                    raise CDPError(f"{method}: {msg['error'].get('message')}")
                return msg.get("result", {})

    def discover_contexts(self, settle: float = 1.2) -> list[dict]:
        """List every JS execution context, main page and iframes alike.

        Reaching into an iframe from the parent breaks the moment the frame is
        cross-origin. Evaluating directly in the frame's own context does not,
        so this is how the game frame gets addressed regardless of origin.
        """
        self.contexts = []
        self._events = []
        self.send("Page.enable")
        self.send("Runtime.enable")
        self.ws.settimeout(0.25)
        deadline = time.time() + settle
        try:
            while time.time() < deadline:
                try:
                    self._events.append(json.loads(self.ws.recv()))
                except Exception:
                    continue
        finally:
            self.ws.settimeout(self._timeout)
        seen = set()
        for msg in self._events:
            if msg.get("method") == "Runtime.executionContextCreated":
                ctx = msg["params"]["context"]
                if ctx["id"] not in seen:
                    seen.add(ctx["id"])
                    self.contexts.append(ctx)
        return self.contexts

    def find_context(self, probe: str = "typeof window.gameui !== 'undefined'",
                     wait: float = 20.0):
        """The context where `probe` is true -- i.e. where the game actually lives.

        Polls, because the game frame boots asynchronously: checking once can
        easily run before the frame's own scripts have defined anything.
        """
        deadline = time.time() + wait
        while True:
            if not self.contexts:
                self.discover_contexts()
            for ctx in self.contexts:
                try:
                    if self.evaluate(f"return !!({probe})", context_id=ctx["id"]):
                        return ctx
                except CDPError:
                    continue
            if time.time() >= deadline:
                return None
            time.sleep(1.0)
            self.contexts = []  # re-enumerate: frames may have appeared since

    def evaluate(self, expression: str, context_id: int | None = None):
        """Run JS in the page and return the value, JSON round-tripped.

        Wrapped in an IIFE returning JSON text: returnByValue chokes on DOM
        nodes and cyclic objects, and stringifying in-page sidesteps both.
        """
        wrapped = f"JSON.stringify((() => {{ {expression} }})())"
        params = {"expression": wrapped, "returnByValue": True, "awaitPromise": True}
        if context_id is not None:
            params["contextId"] = context_id
        result = self.send("Runtime.evaluate", params)
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
