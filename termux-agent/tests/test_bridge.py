import http.client
import json
import threading
import unittest
from unittest.mock import patch

import bridge


class BridgeSafetyTests(unittest.TestCase):
    def setUp(self):
        self.token = "test-token-" + ("x" * 40)
        self.server = bridge.AstraBridgeServer((bridge.HOST, 0), self.token)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.port = self.server.server_address[1]

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def request(self, method, path, body=None, token=None):
        connection = http.client.HTTPConnection(bridge.HOST, self.port, timeout=3)
        headers = {}
        encoded = None
        if body is not None:
            encoded = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
            headers["Content-Length"] = str(len(encoded))
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        connection.request(method, path, body=encoded, headers=headers)
        response = connection.getresponse()
        result = response.status, json.loads(response.read().decode("utf-8"))
        connection.close()
        return result

    def test_http_response_has_security_headers(self):
        connection = http.client.HTTPConnection(bridge.HOST, self.port, timeout=3)
        connection.request("GET", "/v1/health", headers={"Authorization": "Bearer " + self.token})
        response = connection.getresponse()
        self.assertEqual(response.getheader("Cache-Control"), "no-store")
        self.assertEqual(response.getheader("X-Content-Type-Options"), "nosniff")
        self.assertEqual(response.getheader("X-Frame-Options"), "DENY")
        response.read()
        connection.close()

    def test_requires_token_for_health(self):
        status, payload = self.request("GET", "/v1/health")
        self.assertEqual(status, 401)
        self.assertFalse(payload["ok"])

    def test_health_with_token(self):
        status, payload = self.request("GET", "/v1/health", token=self.token)
        self.assertEqual(status, 200)
        self.assertEqual(payload["service"], "Astra Termux Bridge")
        self.assertEqual(payload["status"], "online")
        self.assertEqual(payload["transport"], "loopback")
        self.assertIsInstance(payload["uptime_seconds"], int)
        self.assertGreaterEqual(payload["uptime_seconds"], 0)
        self.assertEqual(payload["tools"], ["list_files"])
        self.assertNotIn("token", payload)

    def test_write_tool_is_blocked_before_execution(self):
        with patch.object(bridge, "run_tool") as run_tool:
            status, payload = self.request(
                "POST",
                "/v1/tool",
                {"tool": "write_file", "arguments": {"path": "x.txt", "content": "no"}},
                token=self.token,
            )
        self.assertEqual(status, 403)
        self.assertEqual(payload["error"], "tool_not_allowed")
        run_tool.assert_not_called()

    def test_read_only_tool_dispatches(self):
        with patch.object(bridge, "run_tool", return_value={"entries": []}) as run_tool:
            status, payload = self.request(
                "POST",
                "/v1/tool",
                {"tool": "list_files", "arguments": {}},
                token=self.token,
            )
        self.assertEqual(status, 200)
        self.assertTrue(payload["ok"])
        run_tool.assert_called_once()
        args, kwargs = run_tool.call_args
        self.assertEqual(args, ("list_files", {}))
        self.assertEqual(kwargs["context"].source, bridge.RequestSource.BRIDGE)
        self.assertEqual(kwargs["context"].invocation, bridge.Invocation.DIRECT)

    def test_sensitive_reads_and_commands_blocked(self):
        for name in ("read_file", "memory_search", "device_status", "safe_command", "clipboard_get"):
            with self.subTest(name=name):
                with patch.object(bridge, "run_tool") as runner:
                    status, payload = self.request("POST", "/v1/tool",
                        {"tool": name, "arguments": {}}, token=self.token)
                    self.assertEqual(status, 403)
                    self.assertEqual(payload["error"], "tool_not_allowed")
                    runner.assert_not_called()

    def test_wrong_token_rejected_before_dispatch(self):
        with patch.object(bridge, "run_tool") as runner:
            status, _ = self.request("POST", "/v1/tool",
                {"tool": "list_files", "arguments": {}}, token="incorrect")
            self.assertEqual(status, 401)
            runner.assert_not_called()

    def test_rejects_non_object_arguments(self):
        with patch.object(bridge, "run_tool") as runner:
            status, payload = self.request("POST", "/v1/tool",
                {"tool": "list_files", "arguments": ["unexpected"]}, token=self.token)
            self.assertEqual(status, 400)
            self.assertEqual(payload["error"], "arguments_must_be_object")
            runner.assert_not_called()

    def test_executor_failure_has_generic_error(self):
        with patch.object(bridge, "run_tool", side_effect=ValueError("PRIVATE_SECRET")):
            status, payload = self.request("POST", "/v1/tool",
                {"tool": "list_files", "arguments": {}}, token=self.token)
            self.assertEqual(status, 403)
            self.assertNotIn("PRIVATE_SECRET", json.dumps(payload))


if __name__ == "__main__":
    unittest.main()
