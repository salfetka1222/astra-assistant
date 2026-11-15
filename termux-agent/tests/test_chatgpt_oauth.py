"""Offline security tests; never make network OAuth or inference calls."""
import base64
import json
import os
import stat
import tempfile
import threading
import time
import unittest
import urllib.parse
from pathlib import Path
from unittest.mock import patch

import chatgpt_oauth as oauth
import bridge


class SignInTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.connection = oauth.ChatGPTConnection(
            Path(self.folder.name) / "private" / "chatgpt.json"
        )

    def tearDown(self):
        self.connection._close_listener()
        self.folder.cleanup()

    def test_first_signin_uses_real_openai_and_pkce(self):
        url = self.connection.start()
        parsed = urllib.parse.urlsplit(url)
        qs = urllib.parse.parse_qs(parsed.query)
        self.assertEqual(parsed.scheme, "https")
        self.assertEqual(parsed.netloc, "auth.openai.com")
        self.assertEqual(parsed.path, "/api/accounts/authorize")
        self.assertEqual(qs["client_id"], ["dynamic_agent_client"])
        self.assertEqual(qs["agent_name_hint"], ["Astra Evolution"])
        self.assertEqual(qs["code_challenge_method"], ["S256"])
        self.assertEqual(qs["resource"], [oauth.RESOURCE])
        self.assertIn(oauth.PERMISSION, qs["scope"][0].split())
        self.assertEqual(qs["redirect_uri"][0].split("/auth/callback")[0].split(":")[0],
                         "http")
        self.assertTrue(qs["redirect_uri"][0].startswith("http://127.0.0.1:"))
        self.assertEqual(qs["ext_agent_host_id"][0], self.connection._host_id())
        expected = base64.urlsafe_b64encode(
            __import__("hashlib").sha256(self.connection.pending["verifier"].encode()).digest()
        ).decode().rstrip("=")
        self.assertEqual(qs["code_challenge"], [expected])
        self.assertEqual(stat.S_IMODE(self.connection.storage.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(self.connection.storage.parent.stat().st_mode), 0o700)
        self.assertNotIn("access_token", self.connection.status())

    def test_retry_has_new_csrf_state_but_same_host(self):
        first = self.connection.start()
        second = self.connection.start()
        one = urllib.parse.parse_qs(urllib.parse.urlsplit(first).query)
        two = urllib.parse.parse_qs(urllib.parse.urlsplit(second).query)
        self.assertNotEqual(one["state"], two["state"])
        self.assertNotEqual(one["nonce"], two["nonce"])
        self.assertNotEqual(one["code_challenge"], two["code_challenge"])
        self.assertEqual(one["ext_agent_host_id"], two["ext_agent_host_id"])

    def test_callback_refuses_wrong_state_and_missing_dynamic_client(self):
        self.connection.start()
        self.assertFalse(self.connection.receive({
            "state": ["incorrect"], "code": ["code"], "client_id": ["oaiapp_123"]
        }))
        self.assertEqual(self.connection.status()["state"], "waiting")
        self.assertFalse(self.connection.receive({
            "state": [self.connection.pending["state"]], "code": ["code"]
        }))
        self.assertEqual(self.connection.status()["state"], "error")

    def test_callback_is_single_use(self):
        self.connection.start()
        state = self.connection.pending["state"]
        event = threading.Event()
        with patch.object(self.connection, "_finish", side_effect=lambda *args: event.set()) as finish:
            payload = {"state": [state], "code": ["one_time"], "client_id": ["oaiapp_valid"]}
            self.assertTrue(self.connection.receive(payload))
            self.assertTrue(event.wait(timeout=2))
            self.assertFalse(self.connection.receive(payload))
            finish.assert_called_once()

    def test_signed_identity_required_before_credential_storage(self):
        self.connection.start()
        pending = dict(self.connection.pending)
        with patch.object(oauth, "_request_json", return_value={
            "id_token": "unsigned.invalid", "access_token": "secret",
            "refresh_token": "refresh", "scope": oauth.SCOPES, "expires_in": 3600,
        }), patch.object(oauth, "_verify_id_token", side_effect=oauth.ChatGPTAuthError("invalid_signature")):
            self.connection._finish(pending, "oaiapp_test", "test-code")
        self.assertEqual(self.connection.status()["state"], "error")
        self.assertNotIn("access_token", self.connection._load())

    def test_valid_signed_identity_and_granted_scopes_are_required(self):
        self.connection.start()
        pending = dict(self.connection.pending)
        with patch.object(oauth, "_request_json", return_value={
            "id_token": "signed", "access_token": "private-access",
            "refresh_token": "private-refresh",
            "scope": "openid email profile offline_access resource.invoke", "expires_in": 3600,
        }), patch.object(oauth, "_verify_id_token", return_value={
            "sub": "account-subject", "email": "example@example.com"
        }):
            self.connection._finish(pending, "oaiapp_test", "test-code")
        status = self.connection.status()
        self.assertTrue(status["connected"])
        self.assertFalse(status["usage_enabled"])
        self.assertNotIn("private-access", json.dumps(status))
        self.assertNotIn("private-refresh", json.dumps(status))
        with self.assertRaises(oauth.ChatGPTAuthError):
            self.connection.access_token()

    def test_no_unverified_jwt_alg_is_accepted(self):
        header = base64.urlsafe_b64encode(json.dumps({"alg": "none", "kid": "x"}).encode()).decode().rstrip("=")
        payload = base64.urlsafe_b64encode(b'{"sub":"forged"}').decode().rstrip("=")
        with patch.object(oauth, "_request_json") as remote:
            with self.assertRaises(oauth.ChatGPTAuthError):
                oauth._verify_id_token(f"{header}.{payload}.AAAA", "oaiapp_test", "nonce")
            remote.assert_not_called()

    def test_refresh_rotates_token_and_keeps_permission(self):
        self.connection._save({
            "host_id": "urn:uuid:00000000-0000-4000-8000-000000000000",
            "client_id": "oaiapp_client", "sub": "subject",
            "scopes": oauth.SCOPES.split(), "access_token": "expired",
            "refresh_token": "old-refresh", "expires_at": 1,
        })
        with patch.object(oauth, "_request_json", return_value={
            "access_token": "new-access", "refresh_token": "new-refresh",
            "expires_in": 3600, "scope": oauth.SCOPES,
        }) as request:
            self.assertEqual(self.connection.access_token(), "new-access")
            self.assertEqual(request.call_args.args[0], oauth.TOKEN_URL)
            self.assertEqual(request.call_args.args[1]["grant_type"], "refresh_token")
        self.assertEqual(self.connection._load()["refresh_token"], "new-refresh")

    def test_streaming_requires_completed_and_has_no_tools(self):
        self.connection._save({
            "host_id": "urn:uuid:00000000-0000-4000-8000-000000000000",
            "client_id": "oaiapp_client", "sub": "subject",
            "scopes": oauth.SCOPES.split(), "access_token": "secret",
            "refresh_token": "refresh", "expires_at": time.time() + 2000,
        })
        class FakeStream:
            def __init__(self):
                self.request = None
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return False
            def __iter__(self):
                return iter([
                    'data: {"type":"response.output_text.delta","delta":"Привет!"}\n'.encode("utf-8"),
                    b'data: {"type":"response.completed"}\n',
                    b'data: [DONE]\n',
                ])
        stream = FakeStream()
        def opener(request, timeout):
            stream.request = request
            return stream
        with patch.object(self.connection, "models", return_value=[
            {"slug": "gpt-test", "display_name": "Test"}
        ]), patch.object(oauth, "_urlopen", side_effect=opener):
            self.assertEqual(self.connection.reply("Привет"), "Привет!")
        body = json.loads(stream.request.data)
        self.assertEqual(body["model"], "gpt-test")
        self.assertFalse(body["store"])
        self.assertTrue(body["stream"])
        self.assertNotIn("tools", body)
        self.assertIn("instructions", body)
        self.assertEqual(body["input"][0]["role"], "user")


class BridgeOAuthTests(unittest.TestCase):
    def setUp(self):
        self.token = "test-" + ("x" * 40)
        self.server = bridge.AstraBridgeServer((bridge.HOST, 0), self.token)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=2)

    def request(self, method, path, body=None, authorized=True):
        import http.client
        c = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        headers = {"Authorization": "Bearer " + self.token} if authorized else {}
        if body is not None:
            body = json.dumps(body)
            headers["Content-Type"] = "application/json"
        c.request(method, path, body=body, headers=headers)
        r = c.getresponse()
        result = (r.status, json.loads(r.read()))
        c.close()
        return result

    def test_start_requires_bearer_and_real_click(self):
        with patch.object(bridge.chatgpt_connection, "start",
                          return_value="https://auth.openai.com/api/accounts/authorize?state=foo") as start:
            self.assertEqual(self.request("POST", "/v1/auth/start", {}, authorized=False)[0], 401)
            self.assertEqual(self.request("POST", "/v1/auth/start", {"tool": "secret"})[0], 400)
            self.assertEqual(start.call_count, 0)
            status, result = self.request("POST", "/v1/auth/start", {})
            self.assertEqual(status, 200)
            self.assertTrue(result["authorize_url"].startswith("https://auth.openai.com/"))
            self.assertEqual(start.call_count, 1)

    def test_status_never_returns_tokens(self):
        with patch.object(bridge.chatgpt_connection, "status", return_value={
            "state": "connected", "connected": True, "usage_enabled": True,
            "account": "example@example.com", "error": "",
        }):
            self.assertEqual(self.request("GET", "/v1/auth/status", authorized=False)[0], 401)
            status, result = self.request("GET", "/v1/auth/status")
            self.assertEqual(status, 200)
            self.assertTrue(result["usage_enabled"])
            self.assertNotIn("access_token", json.dumps(result))

    def test_no_silent_paid_api_fallback(self):
        with patch.dict(os.environ, {"ASTRA_ALLOW_PAID_API": ""}), \
             patch.object(bridge.chatgpt_connection, "status", return_value={
                 "state": "disconnected", "connected": False,
                 "usage_enabled": False, "account": "", "error": "",
             }), \
             patch.object(bridge, "chat_reply") as paid:
            status, result = self.request("POST", "/v1/chat", {"message": "Привет"})
            self.assertEqual(status, 503)
            self.assertEqual(result["error"], "chatgpt_sign_in_required")
            paid.assert_not_called()

    def test_oauth_text_never_dispatches_shell_tools(self):
        with patch.object(bridge.chatgpt_connection, "status", return_value={
            "state": "connected", "connected": True,
            "usage_enabled": True, "account": "", "error": "",
        }), patch.object(bridge.chatgpt_connection, "reply",
                         return_value="Это только текст") as reply, \
             patch.object(bridge, "run_tool") as tool:
            status, result = self.request("POST", "/v1/chat", {"message": "Запусти shell"})
            self.assertEqual(status, 200)
            self.assertEqual(result["reply"], "Это только текст")
            reply.assert_called_once()
            tool.assert_not_called()


if __name__ == "__main__":
    unittest.main()
