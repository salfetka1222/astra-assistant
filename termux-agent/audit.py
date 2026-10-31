"""Minimal local security audit log. Never stores arguments or identities."""
from __future__ import annotations

import json
import os
from pathlib import Path
from datetime import datetime, timezone

AUDIT_PATH = Path(__file__).resolve().parent / "security-audit.jsonl"
ALLOWED_EVENTS = frozenset({"allowed", "denied", "error"})
ALLOWED_SOURCES = frozenset({"cli", "voice", "telegram", "bridge", "unknown"})
ALLOWED_INVOCATIONS = frozenset({"direct", "model", "unknown"})


def record_event(*, tool: str, source: str, invocation: str, outcome: str, reason: str) -> None:
    """Append metadata only. A log failure must not grant permission."""
    # Tool and reason are restricted to predictable identifiers, never user data.
    def identifier(value: str) -> str:
        if type(value) is not str or not value or len(value) > 80:
            return "unknown"
        if not all(c.isascii() and (c.isalnum() or c == "_") for c in value):
            return "unknown"
        return value

    event = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "tool": identifier(tool),
        "source": source if source in ALLOWED_SOURCES else "unknown",
        "invocation": invocation if invocation in ALLOWED_INVOCATIONS else "unknown",
        "outcome": outcome if outcome in ALLOWED_EVENTS else "error",
        "reason": identifier(reason),
    }
    flags = os.O_WRONLY | os.O_APPEND | os.O_CREAT
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    fd = os.open(AUDIT_PATH, flags, 0o600)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "a", encoding="utf-8") as stream:
            fd = -1
            stream.write(json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n")
    finally:
        if fd != -1:
            os.close(fd)
