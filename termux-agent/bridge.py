#!/usr/bin/env python3
"""Authenticated loopback HTTP bridge for Astra Android <-> Termux.

Only read-only tools are exposed in this first stage. The server is hard-bound
to 127.0.0.1 and requires a high-entropy bearer token for every endpoint.
"""
from __future__ import annotations

import hmac
import json
import os
import secrets
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from agent import run_tool
from security import Invocation, RequestSource, create_request_context

HOST = "127.0.0.1"
DEFAULT_PORT = 8765
MAX_BODY_BYTES = 8192
READ_ONLY_TOOLS = frozenset({"list_files"})


class AstraBridgeServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, server_address: tuple[str, int], token: str):
        if server_address[0] != HOST:
            raise ValueError("Astra bridge may bind only to 127.0.0.1.")
        if len(token) < 32:
            raise ValueError("Bridge token must be at least 32 characters.")
        self.token = token
        self.started_at = time.monotonic()
        super().__init__(server_address, AstraBridgeHandler)


class AstraBridgeHandler(BaseHTTPRequestHandler):
    server: AstraBridgeServer
    protocol_version = "HTTP/1.1"

    def setup(self) -> None:
        super().setup()
        # Bound stalled clients so they cannot hold server threads indefinitely.
        self.connection.settimeout(8)

    def log_message(self, format: str, *args: Any) -> None:
        # Do not log request bodies, tokens, paths or tool output.
        return

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(encoded)
        self.close_connection = True

    def _trusted_request(self) -> bool:
        # The Android app sends no Origin header. Reject browser-originated
        # requests, even if a future endpoint accidentally relaxes auth.
        return self.headers.get("Origin") is None and self.headers.get("Sec-Fetch-Site") not in ("cross-site", "same-site")

    def _authorized(self) -> bool:
        value = self.headers.get("Authorization", "")
        prefix = "Bearer "
        if not value.startswith(prefix):
            return False
        return hmac.compare_digest(value[len(prefix):], self.server.token)

    def do_GET(self) -> None:
        if not self._trusted_request():
            self._send_json(403, {"ok": False, "error": "origin_not_allowed"})
            return
        if not self._authorized():
            self._send_json(401, {"ok": False, "error": "unauthorized"})
            return
        if self.path != "/v1/health":
            self._send_json(404, {"ok": False, "error": "not_found"})
            return
        self._send_json(200, {
            "ok": True,
            "service": "Astra Termux Bridge",
            "version": 1,
            "status": "online",
            "transport": "loopback",
            "uptime_seconds": max(0, int(time.monotonic() - self.server.started_at)),
            "tools": sorted(READ_ONLY_TOOLS),
        })

    def do_POST(self) -> None:
        if not self._trusted_request():
            self._send_json(403, {"ok": False, "error": "origin_not_allowed"})
            return
        if not self._authorized():
            self._send_json(401, {"ok": False, "error": "unauthorized"})
            return
        if self.path != "/v1/tool":
            self._send_json(404, {"ok": False, "error": "not_found"})
            return

        raw_length = self.headers.get("Content-Length", "")
        try:
            length = int(raw_length)
        except ValueError:
            self._send_json(411, {"ok": False, "error": "content_length_required"})
            return
        if length < 1 or length > MAX_BODY_BYTES:
            self._send_json(413, {"ok": False, "error": "request_too_large_or_empty"})
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._send_json(400, {"ok": False, "error": "invalid_json"})
            return
        if not isinstance(payload, dict):
            self._send_json(400, {"ok": False, "error": "object_required"})
            return

        name = payload.get("tool")
        arguments = payload.get("arguments", {})
        if not isinstance(name, str) or name not in READ_ONLY_TOOLS:
            self._send_json(403, {"ok": False, "error": "tool_not_allowed"})
            return
        if not isinstance(arguments, dict):
            self._send_json(400, {"ok": False, "error": "arguments_must_be_object"})
            return
        try:
            context = create_request_context(source=RequestSource.BRIDGE, invocation=Invocation.DIRECT, principal="authenticated_local_bridge", session_id="bridge_http_request")
            result = run_tool(name, arguments, context=context)
        except (ValueError, RuntimeError):
            self._send_json(403, {"ok": False, "error": "tool_rejected"})
            return
        self._send_json(200, {"ok": True, "tool": name, "result": result})


def main() -> int:
    token = os.environ.get("ASTRA_BRIDGE_TOKEN", "").strip() or secrets.token_urlsafe(32)
    try:
        port = int(os.environ.get("ASTRA_BRIDGE_PORT", str(DEFAULT_PORT)))
        if not 1024 <= port <= 65535:
            raise ValueError
    except ValueError:
        print("ASTRA_BRIDGE_PORT должен быть числом от 1024 до 65535.")
        return 2

    server = AstraBridgeServer((HOST, port), token)
    print("Astra Termux Bridge запущен только на 127.0.0.1.")
    print(f"Порт: {server.server_address[1]}")
    print("Токен подключения (вставь в Astra на этом же телефоне; не публикуй):")
    print(token, flush=True)
    print("Остановка: Ctrl+C")
    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        print("\nОстанавливаю мост...")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
