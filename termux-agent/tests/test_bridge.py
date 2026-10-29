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

    def test_requires_token_for_health(self):
        status, payload = self.request("GET", "/v1/health")
        self.assertEqual(status, 401)
        self.assertFalse(payload["ok"])

    def test_health_with_token(self):
        status, payload = self.request("GET", "/v1/health", token=self.token)
        self.assertEqual(status, 200)
        self.assertEqual(payload["service"], "Astra Termux Bridge")

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
        with patch.object(bridge, "run_tool", return_value={"count": 0, "memories": []}) as run_tool:
            status, payload = self.request(
                "POST",
                "/v1/tool",
                {"tool": "memory_search", "arguments": {"query": "demo"}},
                token=self.token,
            )
        self.assertEqual(status, 200)
        self.assertTrue(payload["ok"])
        run_tool.assert_called_once_with("memory_search", {"query": "demo"})


if __name__ == "__main__":
    unittest.main()
