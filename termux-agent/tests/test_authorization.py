"""Authorization policy tests; no tools or external services are executed.

Run with unittest discovery from termux-agent, like the existing test suite.
Remote DIRECT denial is a required policy, even if the implementation currently
allows it. These tests intentionally use ordinary assertions, not expectedFailure.
"""
import unittest

from security import (
    Invocation,
    RequestSource,
    authorize_tool,
    create_request_context,
)


# Keep expectations independent of TOOL_SPECS so policy regressions are visible.
SENSITIVE_TOOLS = (
    "device_status",
    "read_file",
    "write_file",
    "safe_command",
    "open_url",
    "memory_add",
    "memory_search",
    "clipboard_get",
    "clipboard_set",
    "launch_app",
)
CLI_ALLOWED_TOOLS = ("list_files",) + SENSITIVE_TOOLS
MODEL_FORBIDDEN_TOOLS = (
    "clipboard_get",
    "write_file",
    "open_url",
    "launch_app",
)


class AuthorizationTests(unittest.TestCase):
    def make_context(
        self,
        source=RequestSource.CLI,
        invocation=Invocation.DIRECT,
    ):
        # Stand in for a trusted handler after caller authentication.
        return create_request_context(
            source=source,
            invocation=invocation,
            principal="test-user",
            session_id="test-session",
        )

    def test_unknown_tool_is_denied(self):
        context = self.make_context()
        for tool_name in ("unknown_tool", "", "send_telegram_message"):
            with self.subTest(tool=tool_name):
                decision = authorize_tool(tool_name, context)
                self.assertIs(decision.allowed, False)

    def test_missing_context_is_denied(self):
        # Known tools isolate context validation from unknown-tool rejection.
        for tool_name in CLI_ALLOWED_TOOLS:
            with self.subTest(tool=tool_name):
                self.assertIs(authorize_tool(tool_name).allowed, False)
                self.assertIs(authorize_tool(tool_name, None).allowed, False)

    def test_model_cannot_call_sensitive_tools(self):
        for source in RequestSource:
            context = self.make_context(source, Invocation.MODEL)
            for tool_name in MODEL_FORBIDDEN_TOOLS:
                with self.subTest(source=source, tool=tool_name):
                    decision = authorize_tool(tool_name, context)
                    self.assertIs(decision.allowed, False)

    def test_telegram_direct_cannot_call_sensitive_tools(self):
        context = self.make_context(RequestSource.TELEGRAM)
        for tool_name in SENSITIVE_TOOLS:
            with self.subTest(tool=tool_name):
                decision = authorize_tool(tool_name, context)
                self.assertIs(decision.allowed, False)

    def test_bridge_direct_cannot_call_sensitive_tools(self):
        context = self.make_context(RequestSource.BRIDGE)
        for tool_name in SENSITIVE_TOOLS:
            with self.subTest(tool=tool_name):
                decision = authorize_tool(tool_name, context)
                self.assertIs(decision.allowed, False)

    def test_cli_direct_retains_access_to_allowed_tools(self):
        context = self.make_context(RequestSource.CLI, Invocation.DIRECT)
        for tool_name in CLI_ALLOWED_TOOLS:
            with self.subTest(tool=tool_name):
                decision = authorize_tool(tool_name, context)
                self.assertIs(decision.allowed, True)

    def test_context_factory_rejects_invalid_source(self):
        # Even a known source's string value must not substitute for the enum.
        for source in (None, "", "cli", "unknown", 0, {}, Invocation.DIRECT):
            with self.subTest(source=source):
                with self.assertRaises(ValueError):
                    self.make_context(source=source)

    def test_context_factory_rejects_invalid_invocation(self):
        for invocation in (
            None, "", "direct", "model", "unknown", 0, {}, RequestSource.CLI
        ):
            with self.subTest(invocation=invocation):
                with self.assertRaises(ValueError):
                    self.make_context(invocation=invocation)

    def test_authorizer_denies_invalid_source(self):
        # Deliberately bypass the frozen dataclass in this test only to exercise
        # authorize_tool's defensive checks independently of factory validation.
        for source in (None, "", "cli", "unknown", 0, {}, Invocation.DIRECT):
            with self.subTest(source=source):
                context = self.make_context()
                object.__setattr__(context, "source", source)
                self.assertIs(authorize_tool("list_files", context).allowed, False)

    def test_authorizer_denies_invalid_invocation(self):
        for invocation in (
            None, "", "direct", "model", "unknown", 0, {}, RequestSource.CLI
        ):
            with self.subTest(invocation=invocation):
                context = self.make_context()
                object.__setattr__(context, "invocation", invocation)
                self.assertIs(authorize_tool("list_files", context).allowed, False)


if __name__ == "__main__":
    unittest.main()
