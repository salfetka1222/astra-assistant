"""Tests for interactive confirmation of explicit CLI commands."""
import unittest
from unittest.mock import patch

import agent


class CLIConfirmationTests(unittest.TestCase):
    def test_read_only_needs_no_confirmation(self):
        with patch.object(agent.sys.stdin, "isatty", return_value=False):
            self.assertTrue(agent.confirm_cli_tool("list_files", {}))

    def test_sensitive_denied_without_tty(self):
        with patch.object(agent.sys.stdin, "isatty", return_value=False):
            with patch("builtins.input") as ask:
                self.assertFalse(agent.confirm_cli_tool("clipboard_get", {}))
                ask.assert_not_called()

    def test_sensitive_denied_without_exact_confirmation(self):
        with patch.object(agent.sys.stdin, "isatty", return_value=True):
            for response in ("", "yes", "да", "ДА " + "нет"):
                with self.subTest(response=response):
                    with patch("builtins.input", return_value=response):
                        self.assertFalse(agent.confirm_cli_tool("write_file", {"path": "x"}))

    def test_sensitive_accepts_exact_confirmation(self):
        with patch.object(agent.sys.stdin, "isatty", return_value=True):
            with patch("builtins.input", return_value="ДА"):
                self.assertTrue(agent.confirm_cli_tool("launch_app", {"package": "com.android.settings"}))

    def test_unknown_tool_denied(self):
        self.assertFalse(agent.confirm_cli_tool("unknown_tool", {}))

    def test_eof_denies(self):
        with patch.object(agent.sys.stdin, "isatty", return_value=True):
            with patch("builtins.input", side_effect=EOFError):
                self.assertFalse(agent.confirm_cli_tool("clipboard_get", {}))


if __name__ == "__main__":
    unittest.main()
