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

    def test_workspace_summary_counts_python_without_reading_content(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "main.py").write_text("private data", encoding="utf-8")
            (root / "notes.txt").write_text("secret", encoding="utf-8")
            (root / "subfolder").mkdir()
            with patch.object(agent, "WORKSPACE", root):
                result = agent.tool_workspace_summary({"path": "."})
                self.assertEqual(result["python_files"], 1)
                self.assertEqual(result["files"], 2)
                self.assertEqual(result["directories"], 1)
                self.assertNotIn("private data", str(result))
                with self.assertRaises(ValueError):
                    agent.tool_workspace_summary({"path": "../"})

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

    def test_local_memory_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(agent, "MEMORY_DB", Path(tmp) / "memory.sqlite3"):
                saved = agent.tool_memory_add({"content": "Предпочитаю русский язык"})
                found = agent.tool_memory_search({"query": "русский"})
                self.assertTrue(saved["ok"])
                self.assertEqual(found["count"], 1)
                self.assertIn("русский", found["memories"][0]["content"])

    def test_app_launch_allowlist_rejects_unknown_package(self):
        with self.assertRaises(ValueError):
            agent.tool_launch_app({"package": "com.example.not-installed"})


if __name__ == "__main__":
    unittest.main()
