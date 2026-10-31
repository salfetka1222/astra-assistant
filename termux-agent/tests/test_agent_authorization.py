"""Regression tests for the agent's authorization enforcement boundary."""
import json
import unittest
from unittest.mock import patch

import agent
from security import Invocation, RequestSource, create_request_context


def context(source=RequestSource.CLI, invocation=Invocation.DIRECT):
    return create_request_context(
        source=source, invocation=invocation,
        principal="test-principal", session_id="test-session",
    )


class AgentAuthorizationTests(unittest.TestCase):
    def test_missing_context_does_not_execute_tool(self):
        with patch.dict(agent.TOOLS, {"clipboard_get": lambda _: self.fail("executed")}):
            with self.assertRaisesRegex(ValueError, "политикой доступа"):
                agent.run_tool("clipboard_get")

    def test_model_cannot_read_clipboard(self):
        with patch.dict(agent.TOOLS, {"clipboard_get": lambda _: self.fail("executed")}):
            with self.assertRaises(ValueError):
                agent.run_tool("clipboard_get", context=context(invocation=Invocation.MODEL))

    def test_model_cannot_write_file_or_launch_app(self):
        for name in ("write_file", "launch_app", "open_url", "memory_add"):
            with self.subTest(name=name):
                with patch.dict(agent.TOOLS, {name: lambda _: self.fail("executed")}):
                    with self.assertRaises(ValueError):
                        agent.run_tool(name, context=context(invocation=Invocation.MODEL))

    def test_remote_direct_cannot_read_private_data(self):
        for source in (RequestSource.TELEGRAM, RequestSource.BRIDGE):
            with self.subTest(source=source):
                with patch.dict(agent.TOOLS, {"read_file": lambda _: self.fail("executed")}):
                    with self.assertRaises(ValueError):
                        agent.run_tool("read_file", context=context(source=source))

    def test_cli_direct_list_files_reaches_executor(self):
        with patch.dict(agent.TOOLS, {"list_files": lambda _: {"entries": []}}):
            self.assertEqual(
                agent.run_tool("list_files", context=context()), {"entries": []}
            )

    def test_model_json_cannot_upgrade_to_direct(self):
        reply = {"choices": [{"message": {"content": json.dumps({
            "tool": "clipboard_get", "arguments": {}
        })}}]}
        with patch.dict(agent.os.environ, {
            "LOCAL_MODEL": "test-model", "CLOUD_API_KEY": "",
        }):
            with patch.object(agent, "_request_json", return_value=reply):
                with patch.dict(agent.TOOLS, {
                    "clipboard_get": lambda _: self.fail("executed")
                }):
                    answer = agent.ask_model("read clipboard", context=context())
        self.assertIn("Не удалось безопасно выполнить инструмент", answer)

    def test_model_can_use_allowed_read_only_tool(self):
        reply = {"choices": [{"message": {"content": json.dumps({
            "tool": "list_files", "arguments": {"path": "."}
        })}}]}
        with patch.dict(agent.os.environ, {
            "LOCAL_MODEL": "test-model", "CLOUD_API_KEY": "",
        }):
            with patch.object(agent, "_request_json", return_value=reply):
                with patch.dict(agent.TOOLS, {
                    "list_files": lambda _: {"entries": ["ok"]}
                }):
                    answer = agent.ask_model("list files", context=context())
        self.assertEqual(json.loads(answer), {"entries": ["ok"]})


if __name__ == "__main__":
    unittest.main()
