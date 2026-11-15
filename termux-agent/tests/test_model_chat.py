import json
import os
import unittest
from unittest.mock import patch

import model_chat


class ModelChatTests(unittest.TestCase):
    def test_missing_model_is_not_configured(self):
        with patch.dict(os.environ, {
            "CLOUD_API_KEY": "", "OPENAI_API_KEY": "",
            "CLOUD_MODEL": "", "LOCAL_MODEL": "",
        }):
            self.assertFalse(model_chat.is_model_configured())
            with self.assertRaises(model_chat.ModelNotConfigured):
                model_chat.chat_reply("Привет")

    def test_rejects_non_loopback_local_model(self):
        with patch.dict(os.environ, {
            "CLOUD_API_KEY": "", "OPENAI_API_KEY": "",
            "CLOUD_MODEL": "", "LOCAL_MODEL": "qwen",
            "LOCAL_BASE_URL": "http://external-host.example:11434/v1",
        }):
            with self.assertRaises(model_chat.ModelNotConfigured):
                model_chat.chat_reply("Привет")

    def test_rejects_insecure_cloud_url(self):
        with patch.dict(os.environ, {
            "CLOUD_API_KEY": "secret", "CLOUD_MODEL": "test-model",
            "CLOUD_BASE_URL": "http://service.example/v1",
        }):
            with self.assertRaises(model_chat.ModelNotConfigured):
                model_chat.chat_reply("Привет")

    def test_rejects_bad_messages_before_network(self):
        with patch.object(model_chat.urllib.request, "build_opener") as request:
            for message in ("", "  ", "a" * 3001):
                with self.assertRaises(ValueError):
                    model_chat.chat_reply(message)
            request.assert_not_called()

    def test_provider_call_never_dispatches_tools(self):
        class MockResponse:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return None

            def read(self, amount):
                return json.dumps({
                    "choices": [{"message": {"content": '{"tool":"launch_app","arguments":{}}'}}]
                }).encode()

        class MockOpener:
            def open(self, request, timeout):
                self.request = request
                self.timeout = timeout
                return MockResponse()

        opener = MockOpener()
        with patch.dict(os.environ, {
            "CLOUD_API_KEY": "test-secret", "CLOUD_MODEL": "test-model",
            "CLOUD_BASE_URL": "https://api.openai.com/v1",
        }), patch.object(model_chat.urllib.request, "build_opener", return_value=opener):
            result = model_chat.chat_reply("Открой приложение")
        self.assertIn('"tool"', result)
        self.assertEqual(opener.request.get_header("Authorization"), "Bearer test-secret")
        payload = json.loads(opener.request.data)
        self.assertEqual(len(payload["messages"]), 2)
        self.assertEqual(payload["messages"][1]["content"], "Открой приложение")

    def test_invalid_provider_data_is_sanitized(self):
        class MockResponse:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return None

            def read(self, amount):
                return b'{"error":"MY_PRIVATE_TOKEN"}'

        with patch.dict(os.environ, {
            "CLOUD_API_KEY": "secret", "CLOUD_MODEL": "test-model",
            "CLOUD_BASE_URL": "https://api.openai.com/v1",
        }), patch.object(model_chat.urllib.request, "build_opener") as factory:
            factory.return_value.open.return_value = MockResponse()
            with self.assertRaises(model_chat.ModelUnavailable) as caught:
                model_chat.chat_reply("Привет")
        self.assertNotIn("PRIVATE_TOKEN", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
