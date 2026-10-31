"""Audit log privacy and decision tests."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import agent
import audit
from security import Invocation, RequestSource, create_request_context


class AuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "audit.jsonl"
        p = patch.object(audit, "AUDIT_PATH", self.path)
        p.start()
        self.addCleanup(p.stop)

    def entries(self):
        return [json.loads(line) for line in self.path.read_text().splitlines()]

    def context(self, source=RequestSource.CLI, invocation=Invocation.DIRECT):
        return create_request_context(source=source, invocation=invocation,
                                      principal="private-principal", session_id="secret-session")

    def test_denied_tool_audited_without_private_data(self):
        secret = "PRIVATE_TOKEN_123"
        with self.assertRaises(ValueError):
            agent.run_tool("clipboard_set", {"text": secret}, context=self.context(RequestSource.TELEGRAM))
        raw = self.path.read_text()
        self.assertNotIn(secret, raw)
        self.assertNotIn("private-principal", raw)
        self.assertNotIn("secret-session", raw)
        self.assertEqual(self.entries()[0]["outcome"], "denied")

    def test_allowed_read_only_logged(self):
        with patch.dict(agent.TOOLS, {"list_files": lambda _: {"ok": True}}):
            agent.run_tool("list_files", context=self.context())
        self.assertEqual(self.entries()[0]["outcome"], "allowed")

    def test_execution_failure_logged(self):
        def fail(_):
            raise RuntimeError("SECRET_EXECUTION_DETAIL")
        with patch.dict(agent.TOOLS, {"list_files": fail}):
            with self.assertRaises(RuntimeError):
                agent.run_tool("list_files", context=self.context())
        self.assertNotIn("SECRET_EXECUTION_DETAIL", self.path.read_text())
        self.assertEqual(self.entries()[0]["outcome"], "error")

    def test_reject_unknown_metadata(self):
        audit.record_event(tool="token=SECRET", source="user-secret",
                           invocation="user-secret", outcome="user-secret", reason="token=SECRET")
        raw = self.path.read_text()
        self.assertNotIn("SECRET", raw)
        self.assertEqual(self.entries()[0]["tool"], "unknown")

    def test_multiple_events_append(self):
        for _ in range(2):
            audit.record_event(tool="list_files", source="cli", invocation="direct",
                               outcome="allowed", reason="executed")
        self.assertEqual(len(self.entries()), 2)


if __name__ == "__main__":
    unittest.main()
