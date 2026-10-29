import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import agent


class AgentSafetyTests(unittest.TestCase):
    def test_path_traversal_is_blocked(self):
        with self.assertRaises(ValueError):
            agent.safe_path("../outside.txt")

    def test_write_file_creates_new_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(agent, "WORKSPACE", Path(tmp)):
                result = agent.tool_write_file({"path": "notes/hello.txt", "content": "привет"})
                self.assertTrue(result["ok"])
                self.assertEqual((Path(tmp) / "notes" / "hello.txt").read_text(encoding="utf-8"), "привет")

    def test_write_file_does_not_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "existing.txt"
            path.write_text("keep", encoding="utf-8")
            with patch.object(agent, "WORKSPACE", Path(tmp)):
                with self.assertRaises(ValueError):
                    agent.tool_write_file({"path": "existing.txt", "content": "replace"})
            self.assertEqual(path.read_text(encoding="utf-8"), "keep")

    def test_shell_command_allowlist(self):
        with self.assertRaises(ValueError):
            agent.tool_safe_command({"command": "rm -rf ~"})

    def test_tool_name_allowlist(self):
        with self.assertRaises(ValueError):
            agent.run_tool("send_telegram_message", {"chat_id": 1, "text": "hi"})

    def test_non_http_url_rejected(self):
        with self.assertRaises(ValueError):
            agent.tool_open_url({"url": "file:///etc/passwd"})


if __name__ == "__main__":
    unittest.main()
