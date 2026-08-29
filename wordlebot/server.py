"""Serve the bundled Wordle game on localhost.

No dependencies beyond the standard library: the page is static and the word
lists are read straight out of the package.
"""

from __future__ import annotations

import argparse
import http.server
import socketserver
import threading
import webbrowser
from pathlib import Path

from .words import DATA_DIR

SITE_DIR = Path(__file__).parent / "site"
WORD_FILES = {"answers.txt", "allowed.txt"}


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(SITE_DIR), **kwargs)

    def do_GET(self):  # noqa: N802 -- name fixed by the base class
        if self.path.startswith("/words/"):
            name = self.path.rsplit("/", 1)[-1]
            if name not in WORD_FILES:
                self.send_error(404)
                return
            body = (DATA_DIR / name).read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def log_message(self, *args):
        pass  # keep the console for the bot's own output


class _Server(socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def serve_background(port: int = 0) -> tuple[str, _Server]:
    """Start the server on a background thread; return its URL and handle."""
    httpd = _Server(("127.0.0.1", port), Handler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return f"http://127.0.0.1:{httpd.server_address[1]}/", httpd


def main() -> None:
    ap = argparse.ArgumentParser(description="Serve the local Wordle game.")
    ap.add_argument("--port", type=int, default=8000)
    ap.add_argument("--no-open", action="store_true", help="don't open a browser")
    args = ap.parse_args()

    url, httpd = serve_background(args.port)
    print(f"Wordle is running at {url}")
    print("Play it yourself, or point the bot at it:")
    print(f'  python -m wordlebot.browser "{url}" --no-headless')
    print("Press Ctrl-C to stop.")
    if not args.no_open:
        webbrowser.open(url)
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        httpd.shutdown()
        print("\nstopped")


if __name__ == "__main__":
    main()
