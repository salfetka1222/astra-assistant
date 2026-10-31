"""One-time executor permission regression tests; no real device actions."""
import unittest
from unittest.mock import patch
import agent
from security import Invocation, RequestSource, create_request_context


def ctx(source=RequestSource.CLI, invocation=Invocation.DIRECT):
    return create_request_context(source=source, invocation=invocation, principal="test", session_id="session")


class OneTimePermitTests(unittest.TestCase):
    def setUp(self):
        self.context = ctx()

    def permit(self, name, args, context=None):
        with patch.object(agent, "confirm_cli_tool", return_value=True):
            return agent.issue_cli_permit(name, args, context or self.context)

    def test_sensitive_cli_denied_without_permit(self):
        with patch.dict(agent.TOOLS, {"clipboard_get": lambda _: self.fail("executed")}):
            with self.assertRaises(ValueError):
                agent.run_tool("clipboard_get", context=self.context)

    def test_one_time_permit_executes_once(self):
        calls = []
        args = {"text": "example"}
        permit = self.permit("clipboard_set", args)
        with patch.dict(agent.TOOLS, {"clipboard_set": lambda a: calls.append(a) or {"ok": True}}):
            self.assertEqual(agent.run_tool("clipboard_set", args, context=self.context, permit=permit), {"ok": True})
            with self.assertRaises(ValueError):
                agent.run_tool("clipboard_set", args, context=self.context, permit=permit)
        self.assertEqual(len(calls), 1)

    def test_permit_rejects_changed_arguments(self):
        permit = self.permit("clipboard_set", {"text": "original"})
        with patch.dict(agent.TOOLS, {"clipboard_set": lambda _: self.fail("executed")}):
            with self.assertRaises(ValueError):
                agent.run_tool("clipboard_set", {"text": "changed"}, context=self.context, permit=permit)
            with self.assertRaises(ValueError):
                agent.run_tool("clipboard_set", {"text": "original"}, context=self.context, permit=permit)

    def test_permit_rejects_different_tool(self):
        permit = self.permit("clipboard_get", {})
        with patch.dict(agent.TOOLS, {"clipboard_set": lambda _: self.fail("executed")}):
            with self.assertRaises(ValueError):
                agent.run_tool("clipboard_set", {}, context=self.context, permit=permit)

    def test_permit_rejects_different_context(self):
        permit = self.permit("clipboard_get", {})
        with patch.dict(agent.TOOLS, {"clipboard_get": lambda _: self.fail("executed")}):
            with self.assertRaises(ValueError):
                agent.run_tool("clipboard_get", {}, context=ctx(), permit=permit)

    def test_remote_and_model_cannot_obtain_permit(self):
        for context in (ctx(RequestSource.TELEGRAM), ctx(RequestSource.BRIDGE),
                        ctx(RequestSource.CLI, Invocation.MODEL)):
            with self.subTest(source=context.source, invocation=context.invocation):
                with patch.object(agent, "confirm_cli_tool", return_value=True) as confirm:
                    self.assertIsNone(agent.issue_cli_permit("clipboard_get", {}, context))
                    confirm.assert_not_called()

    def test_no_confirmation_means_no_permit(self):
        with patch.object(agent, "confirm_cli_tool", return_value=False):
            self.assertIsNone(agent.issue_cli_permit("clipboard_get", {}, self.context))

    def test_read_only_remains_available_without_permit(self):
        with patch.dict(agent.TOOLS, {"list_files": lambda _: {"entries": []}}):
            self.assertEqual(agent.run_tool("list_files", context=self.context), {"entries": []})


if __name__ == "__main__":
    unittest.main()
