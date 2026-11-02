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

    def test_disconnected_client_does_not_raise_or_log_response(self):
        from unittest.mock import Mock
        handler = object.__new__(bridge.AstraBridgeHandler)
        handler.send_response = Mock()
        handler.send_header = Mock()
        handler.end_headers = Mock()
        handler.wfile = Mock()
        for error in (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            with self.subTest(error=error.__name__):
                handler.close_connection = False
                handler.wfile.write.side_effect = error("client disconnected")
                handler._send_json(401, {"ok": False, "error": "unauthorized"})
                self.assertTrue(handler.close_connection)

    def test_browser_origin_is_rejected_even_with_valid_token(self):
        with patch.object(bridge, "run_tool") as runner:
            connection = http.client.HTTPConnection(bridge.HOST, self.port, timeout=3)
            connection.request("POST", "/v1/tool", body=b'{}', headers={
                "Authorization": "Bearer " + self.token,
                "Origin": "https://untrusted.example",
                "Content-Length": "2",
            })
            response = connection.getresponse()
            self.assertEqual(response.status, 403)
            self.assertEqual(json.loads(response.read())["error"], "origin_not_allowed")
            connection.close()
            runner.assert_not_called()

    def test_connection_slots_reject_excess_and_recover(self):
        import socket
        held = []
        try:
            for _ in range(bridge.MAX_CONCURRENT_CONNECTIONS):
                sock = socket.create_connection((bridge.HOST, self.port), timeout=2)
                sock.sendall(b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\n")
                held.append(sock)
            # Wait until all partial requests have been accepted by workers.
            import time
            deadline = time.monotonic() + 2
            while self.server._connection_slots._value != 0 and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertEqual(self.server._connection_slots._value, 0)
            extra = socket.create_connection((bridge.HOST, self.port), timeout=2)
            extra.settimeout(2)
            try:
                extra.sendall(b"GET /v1/health HTTP/1.1\r\nHost: localhost\r\n\r\n")
                self.assertEqual(extra.recv(1), b"")
            finally:
                extra.close()
        finally:
            for sock in held:
                sock.close()
        deadline = time.monotonic() + 2
        while self.server._connection_slots._value != bridge.MAX_CONCURRENT_CONNECTIONS and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertEqual(self.request("GET", "/v1/health", token=self.token)[0], 200)

    def test_rate_limit_returns_429_and_recovers(self):
        with patch.object(bridge, "RATE_MAX_REQUESTS", 2):
            self.assertEqual(self.request("GET", "/v1/health", token=self.token)[0], 200)
            self.assertEqual(self.request("GET", "/v1/health", token=self.token)[0], 200)
            status, payload = self.request("GET", "/v1/health", token=self.token)
            self.assertEqual(status, 429)
            self.assertEqual(payload["error"], "rate_limited")
            with self.server._rate_lock:
                self.server._recent_requests.clear()
            self.assertEqual(self.request("GET", "/v1/health", token=self.token)[0], 200)

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
        self.assertEqual(payload["tools"], ["list_files", "workspace_summary"])
        self.assertNotIn("token", payload)

    def test_workspace_summary_dispatches(self):
        with patch.object(bridge, "run_tool", return_value={"python_files": 2}) as runner:
            status, payload = self.request("POST", "/v1/tool",
                {"tool": "workspace_summary", "arguments": {"path": "."}}, token=self.token)
        self.assertEqual(status, 200)
        self.assertEqual(payload["result"]["python_files"], 2)
        runner.assert_called_once()

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

    def test_unexpected_tool_exception_is_sanitized(self):
        with patch.object(bridge, "run_tool", side_effect=OSError("SECRET_INTERNAL_PATH")):
            status, payload = self.request("POST", "/v1/tool",
                {"tool": "list_files", "arguments": {}}, token=self.token)
            self.assertEqual(status, 500)
            self.assertEqual(payload["error"], "internal_error")
            self.assertNotIn("SECRET_INTERNAL_PATH", json.dumps(payload))

    def test_executor_failure_has_generic_error(self):
        with patch.object(bridge, "run_tool", side_effect=ValueError("PRIVATE_SECRET")):
            status, payload = self.request("POST", "/v1/tool",
                {"tool": "list_files", "arguments": {}}, token=self.token)
            self.assertEqual(status, 403)
            self.assertNotIn("PRIVATE_SECRET", json.dumps(payload))


if __name__ == "__main__":
    unittest.main()
