"""Text-only, no-tools AI responses for Astra's authenticated loopback bridge.

No model output from this module may dispatch Android, filesystem, or shell tools.
Provider credentials are loaded only from the Termux process environment.
"""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

MAX_PROMPT_CHARS = 3000
MAX_REPLY_CHARS = 5000
MAX_PROVIDER_RESPONSE_BYTES = 64 * 1024

SYSTEM_MESSAGE = (
    "Ты — Astra Evolution, персональный Android-ассистент. "
    "Отвечай по-русски, понятно и без выдуманных действий. "
    "Ты не управляешь устройством напрямую и не имеешь доступа к файлам, "
    "контактам, экрану или настройкам. Если пользователь попросил действие "
    "на Android, объясни, что оно требует отдельного подтверждения в Astra. "
    "Не проси пароли, секреты или API-ключи."
)


class ModelNotConfigured(Exception):
    pass


class ModelUnavailable(Exception):
    pass


class _NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req: Any, fp: Any, code: Any, msg: Any,
                         headers: Any, newurl: Any) -> None:
        return None


def _configured_backend() -> tuple[str, str, str | None]:
    cloud_key = (os.environ.get("CLOUD_API_KEY") or os.environ.get("OPENAI_API_KEY") or "").strip()
    cloud_model = os.environ.get("CLOUD_MODEL", "").strip()
    cloud_url = os.environ.get("CLOUD_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    if cloud_key and cloud_model:
        parsed = urllib.parse.urlsplit(cloud_url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
            raise ModelNotConfigured("Invalid cloud endpoint")
        return cloud_url + "/chat/completions", cloud_model, cloud_key

    local_model = os.environ.get("LOCAL_MODEL", "").strip()
    if local_model:
        local_url = os.environ.get("LOCAL_BASE_URL", "http://127.0.0.1:11434/v1").rstrip("/")
        parsed = urllib.parse.urlsplit(local_url)
        if (parsed.scheme not in ("http", "https")
                or parsed.hostname not in ("127.0.0.1", "localhost", "::1")
                or parsed.username or parsed.password or parsed.fragment):
            raise ModelNotConfigured("Local endpoint must be loopback")
        return local_url + "/chat/completions", local_model, None
    raise ModelNotConfigured("No configured model")


def is_model_configured() -> bool:
    try:
        _configured_backend()
    except ModelNotConfigured:
        return False
    return True


def chat_reply(message: str) -> str:
    if not isinstance(message, str) or not message.strip() or len(message) > MAX_PROMPT_CHARS:
        raise ValueError("Invalid chat message")
    endpoint, model, key = _configured_backend()
    payload = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_MESSAGE},
            {"role": "user", "content": message.strip()},
        ],
    }, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if key:
        headers["Authorization"] = "Bearer " + key
    request = urllib.request.Request(endpoint, data=payload, headers=headers, method="POST")
    opener = urllib.request.build_opener(_NoRedirects())
    try:
        with opener.open(request, timeout=50) as response:
            raw = response.read(MAX_PROVIDER_RESPONSE_BYTES + 1)
        if len(raw) > MAX_PROVIDER_RESPONSE_BYTES:
            raise ModelUnavailable("Provider reply too large")
        data = json.loads(raw)
        answer = data["choices"][0]["message"]["content"]
        if not isinstance(answer, str) or not answer.strip():
            raise ModelUnavailable("Empty provider reply")
        return answer.strip()[:MAX_REPLY_CHARS]
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError,
            ValueError, TypeError, KeyError, IndexError, json.JSONDecodeError) as exc:
        # The API response may contain private account data; never include it in errors.
        raise ModelUnavailable("Upstream model unavailable") from exc
