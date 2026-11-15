"""Sign in with ChatGPT for a local, user-controlled Astra Termux host.

The browser handles credentials. The loopback receiver accepts only a single
short-lived state-bound authorization response; no OAuth credentials reach Android.
Python standard library only. No API-key or paid-API fallback.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import secrets
import stat
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

AUTH_URL = "https://auth.openai.com/api/accounts/authorize"
TOKEN_URL = "https://auth.openai.com/api/accounts/oauth/token"
REVOKE_URL = "https://auth.openai.com/api/accounts/oauth/revoke"
JWKS_URL = "https://auth.openai.com/.well-known/jwks.json"
RESOURCE = "https://api.openai.com/v1"
ISSUER = "https://auth.openai.com"
SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
PERMISSION = "chatgpt.tokens.use.direct"
MAX_PROMPT_CHARS = 3000
MAX_REPLY_CHARS = 5000


class ChatGPTAuthError(Exception):
    """A safe, non-secret error for the local app."""


class _NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _urlopen(request, timeout=12):
    return urllib.request.build_opener(_NoRedirects()).open(request, timeout=timeout)


def _b64url(value: str) -> bytes:
    if not value or len(value) > 12000:
        raise ChatGPTAuthError("invalid_token")
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def _request_json(url: str, data: dict | None = None, token: str | None = None,
                  limit: int = 524288, timeout: int = 12) -> dict:
    if not url.startswith("https://"):
        raise ChatGPTAuthError("invalid_endpoint")
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=body, headers=headers,
                                 method="POST" if data is not None else "GET")
    try:
        with _urlopen(req, timeout=timeout) as r:
            raw = r.read(limit + 1)
            if len(raw) > limit:
                raise ChatGPTAuthError("response_too_large")
            result = json.loads(raw)
            if not isinstance(result, dict):
                raise ChatGPTAuthError("invalid_response")
            return result
    except ChatGPTAuthError:
        raise
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError,
            ValueError, TypeError, json.JSONDecodeError) as exc:
        raise ChatGPTAuthError("provider_unavailable") from exc


def _verify_id_token(id_token: str, client_id: str, nonce: str) -> dict:
    """Verify OpenAI OIDC RS256 JWT using its public JWKS; no untrusted alg."""
    try:
        encoded_header, encoded_claims, encoded_signature = id_token.split(".")
        header = json.loads(_b64url(encoded_header))
        claims = json.loads(_b64url(encoded_claims))
        signature = _b64url(encoded_signature)
        if not isinstance(header, dict) or header.get("alg") != "RS256":
            raise ChatGPTAuthError("unsupported_signature")
        if not isinstance(claims, dict):
            raise ChatGPTAuthError("invalid_identity")
        key_id = header.get("kid")
        if not isinstance(key_id, str) or not key_id:
            raise ChatGPTAuthError("missing_signing_key")
        keys = _request_json(JWKS_URL).get("keys", [])
        matches = [k for k in keys if isinstance(k, dict) and
                   k.get("kid") == key_id and k.get("kty") == "RSA" and
                   k.get("use", "sig") == "sig"]
        if len(matches) != 1:
            raise ChatGPTAuthError("unknown_signing_key")
        n = int.from_bytes(_b64url(matches[0]["n"]), "big")
        e = int.from_bytes(_b64url(matches[0]["e"]), "big")
        if n.bit_length() < 2048 or e != 65537:
            raise ChatGPTAuthError("invalid_signing_key")
        size = (n.bit_length() + 7) // 8
        if len(signature) != size:
            raise ChatGPTAuthError("invalid_signature")
        digest = hashlib.sha256((encoded_header + "." + encoded_claims).encode("ascii")).digest()
        digest_info = bytes.fromhex("3031300d060960864801650304020105000420") + digest
        padding_len = size - len(digest_info) - 3
        if padding_len < 8:
            raise ChatGPTAuthError("invalid_signature")
        expected = b"\x00\x01" + b"\xff" * padding_len + b"\x00" + digest_info
        recovered = pow(int.from_bytes(signature, "big"), e, n).to_bytes(size, "big")
        if not hmac.compare_digest(expected, recovered):
            raise ChatGPTAuthError("invalid_signature")
        now = int(time.time())
        aud = claims.get("aud")
        audience_ok = aud == client_id if isinstance(aud, str) else (
            isinstance(aud, list) and client_id in aud and
            (len(aud) == 1 or claims.get("azp") == client_id))
        if (claims.get("iss") != ISSUER or not audience_ok or
                not isinstance(claims.get("sub"), str) or not claims["sub"] or
                not isinstance(claims.get("exp"), int) or claims["exp"] <= now or
                not isinstance(claims.get("iat"), int) or claims["iat"] > now + 60 or
                claims.get("nonce") != nonce):
            raise ChatGPTAuthError("invalid_identity")
        if isinstance(claims.get("nbf"), int) and claims["nbf"] > now + 60:
            raise ChatGPTAuthError("invalid_identity")
        return claims
    except ChatGPTAuthError:
        raise
    except (ValueError, TypeError, KeyError, OverflowError, UnicodeError,
            json.JSONDecodeError) as exc:
        raise ChatGPTAuthError("invalid_identity") from exc


class ChatGPTConnection:
    def __init__(self, storage: Path | None = None):
        self.storage = storage or Path.home() / ".config" / "astra-evolution" / "chatgpt.json"
        self.lock = threading.RLock()
        self.pending = None
        self.listener = None
        self.attempt_id = None
        self.phase = "idle"
        self.error = ""

    def _load(self) -> dict:
        try:
            if self.storage.is_symlink():
                raise ChatGPTAuthError("unsafe_profile")
            with self.storage.open("r", encoding="utf-8") as f:
                value = json.load(f)
            return value if isinstance(value, dict) else {}
        except FileNotFoundError:
            return {}
        except (ValueError, OSError) as exc:
            raise ChatGPTAuthError("profile_unavailable") from exc

    def _save(self, profile: dict) -> None:
        folder = self.storage.parent
        folder.mkdir(parents=True, exist_ok=True, mode=0o700)
        folder.chmod(0o700)
        if self.storage.is_symlink():
            raise ChatGPTAuthError("unsafe_profile")
        path = folder / (".chatgpt-" + secrets.token_hex(8) + ".tmp")
        try:
            fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as out:
                json.dump(profile, out, ensure_ascii=False)
                out.flush()
                os.fsync(out.fileno())
            os.replace(path, self.storage)
            self.storage.chmod(0o600)
        finally:
            path.unlink(missing_ok=True)

    def _host_id(self) -> str:
        profile = self._load()
        host_id = profile.get("host_id")
        if isinstance(host_id, str) and host_id.startswith("urn:uuid:"):
            return host_id
        host_id = "urn:uuid:" + str(uuid.uuid4())
        profile["host_id"] = host_id
        self._save(profile)
        return host_id

    def status(self) -> dict:
        with self.lock:
            profile = self._load()
            connected = bool(profile.get("access_token") and profile.get("refresh_token"))
            granted = PERMISSION in profile.get("scopes", []) if connected else False
            state = self.phase if self.phase in ("waiting", "connecting", "error") else (
                "connected" if connected else "disconnected")
            return {
                "state": state,
                "connected": connected,
                "usage_enabled": granted,
                "account": profile.get("email", "") if connected else "",
                "error": self.error if state == "error" else "",
            }

    def start(self) -> str:
        with self.lock:
            self._close_listener()
            host_id = self._host_id()
            profile = self._load()
            client_id = profile.get("client_id") or "dynamic_agent_client"
            state, nonce = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
            self.attempt_id = secrets.token_hex(16)
            verifier = secrets.token_urlsafe(64)
            challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
            parent = self
            class Callback(BaseHTTPRequestHandler):
                def log_message(self, *args):
                    pass

                def do_GET(self):
                    if len(self.path) > 4096 or self.path.split("?")[0] != "/auth/callback":
                        self.send_error(404)
                        return
                    if self.headers.get("Host") != f"127.0.0.1:{self.server.server_port}":
                        self.send_error(403)
                        return
                    params = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query,
                                                   keep_blank_values=True)
                    accepted = parent.receive(params)
                    html = ("Авторизация получена. Вернись в Astra и нажми «Обновить статус»."
                            if accepted else
                            "Запрос отклонён или устарел. Вернись в Astra и повтори подключение.")
                    encoded = html.encode("utf-8")
                    self.send_response(200 if accepted else 400)
                    self.send_header("Content-Type", "text/plain; charset=utf-8")
                    self.send_header("Content-Length", str(len(encoded)))
                    self.send_header("Cache-Control", "no-store")
                    self.send_header("Content-Security-Policy", "default-src 'none'")
                    self.end_headers()
                    self.wfile.write(encoded)

            listener = ThreadingHTTPServer(("127.0.0.1", 0), Callback)
            listener.daemon_threads = True
            redirect = f"http://127.0.0.1:{listener.server_port}/auth/callback"
            self.pending = {
                "state": state, "nonce": nonce, "verifier": verifier,
                "client_id": client_id, "redirect": redirect,
                "created": time.monotonic(),
                "attempt_id": self.attempt_id,
                "prior_sub": profile.get("sub"),
            }
            self.listener = listener
            threading.Thread(target=listener.serve_forever, daemon=True).start()
            query = {
                "client_id": client_id, "response_type": "code",
                "redirect_uri": redirect, "scope": SCOPES, "resource": RESOURCE,
                "state": state, "nonce": nonce,
                "code_challenge_method": "S256", "code_challenge": challenge,
                "ext_agent_host_id": host_id,
            }
            if client_id == "dynamic_agent_client":
                query["agent_name_hint"] = "Astra Evolution"
            elif profile.get("id_token"):
                query["id_token_hint"] = profile["id_token"]
            self.phase, self.error = "waiting", ""
            expiry_timer = threading.Timer(300, self._expire, args=(state,))
            expiry_timer.daemon = True
            expiry_timer.start()
            return AUTH_URL + "?" + urllib.parse.urlencode(query)

    def _expire(self, state: str) -> None:
        with self.lock:
            if self.pending and self.pending["state"] == state:
                self.pending = None
                self._close_listener()
                self.phase, self.error = "error", "login_timed_out"

    def _close_listener(self) -> None:
        current = self.listener
        self.listener = None
        if current is not None:
            threading.Thread(target=lambda: (current.shutdown(), current.server_close()),
                             daemon=True).start()

    def receive(self, params: dict) -> bool:
        with self.lock:
            pending = self.pending
            if (not pending or time.monotonic() - pending["created"] > 300 or
                    len(params.get("state", [])) != 1 or
                    not hmac.compare_digest(params["state"][0], pending["state"])):
                return False
            self.pending = None  # one-time code and CSRF state
            self._close_listener()
            if params.get("error"):
                self.phase, self.error = "error", "access_denied"
                return True
            if (len(params.get("code", [])) != 1 or not params["code"][0] or
                    len(params["code"][0]) > 2048):
                self.phase, self.error = "error", "invalid_callback"
                return False
            returned_id = params.get("client_id", [])
            if len(returned_id) > 1:
                self.phase, self.error = "error", "invalid_client_id"
                return False
            client_id = pending["client_id"]
            if client_id == "dynamic_agent_client":
                if not returned_id or not returned_id[0].startswith("oaiapp_"):
                    self.phase, self.error = "error", "missing_client_id"
                    return False
                client_id = returned_id[0]
            elif returned_id and returned_id[0] != client_id:
                self.phase, self.error = "error", "invalid_client_id"
                return False
            self.phase, self.error = "connecting", ""
            threading.Thread(target=self._finish, args=(pending, client_id, params["code"][0]),
                             daemon=True).start()
            return True

    def _finish(self, pending: dict, client_id: str, code: str) -> None:
        try:
            result = _request_json(TOKEN_URL, {
                "grant_type": "authorization_code", "client_id": client_id,
                "code": code, "code_verifier": pending["verifier"],
                "redirect_uri": pending["redirect"], "resource": RESOURCE,
            })
            identity = _verify_id_token(result["id_token"], client_id, pending["nonce"])
            if pending["prior_sub"] and identity["sub"] != pending["prior_sub"]:
                raise ChatGPTAuthError("account_mismatch")
            if not isinstance(result.get("scope"), str):
                raise ChatGPTAuthError("invalid_scope")
            scopes = result["scope"].split()
            if not isinstance(result.get("access_token"), str) or not result["access_token"]:
                raise ChatGPTAuthError("missing_access_token")
            if not isinstance(result.get("refresh_token"), str) or not result["refresh_token"]:
                raise ChatGPTAuthError("missing_refresh_token")
            with self.lock:
                # A newer sign-in or an explicit disconnect invalidates this attempt.
                if pending["attempt_id"] != self.attempt_id:
                    return
                existing = self._load()
                profile = {
                    "host_id": existing["host_id"],
                    "client_id": client_id,
                    "sub": identity["sub"],
                    "email": identity.get("email", ""),
                    "id_token": result["id_token"],
                    "access_token": result["access_token"],
                    "refresh_token": result["refresh_token"],
                    "scopes": scopes,
                    "expires_at": time.time() + int(result.get("expires_in", 3600)),
                }
                self._save(profile)
                self.phase, self.error = "idle", ""
        except (ChatGPTAuthError, KeyError, TypeError, ValueError):
            with self.lock:
                if pending["attempt_id"] == self.attempt_id:
                    self.phase, self.error = "error", "sign_in_failed"

    def disconnect(self) -> bool:
        with self.lock:
            self.pending = None
            self.attempt_id = None
            self._close_listener()
            profile = self._load()
            refresh = profile.pop("refresh_token", None)
            client = profile.get("client_id")
            for key in ("access_token", "id_token", "expires_at", "scopes"):
                profile.pop(key, None)
            self._save(profile)
            self.phase, self.error = "idle", ""
        if refresh and client:
            request = urllib.request.Request(
                REVOKE_URL,
                data=urllib.parse.urlencode({
                    "token": refresh, "token_type_hint": "refresh_token",
                    "client_id": client,
                }).encode(),
                headers={"Content-Type": "application/x-www-form-urlencoded"},
                method="POST",
            )
            try:
                with _urlopen(request, timeout=5) as response:
                    return response.status == 200
            except (urllib.error.HTTPError, urllib.error.URLError,
                    OSError, TimeoutError):
                # The credential is gone locally, but remote revocation is unconfirmed.
                return False
        return True

    def access_token(self) -> str:
        with self.lock:
            profile = self._load()
            if PERMISSION not in profile.get("scopes", []):
                raise ChatGPTAuthError("permission_not_granted")
            token = profile.get("access_token")
            if not token or not profile.get("refresh_token"):
                raise ChatGPTAuthError("not_connected")
            if profile.get("expires_at", 0) < time.time() + 90:
                result = _request_json(TOKEN_URL, {
                    "grant_type": "refresh_token", "client_id": profile["client_id"],
                    "refresh_token": profile["refresh_token"], "resource": RESOURCE,
                })
                if not result.get("access_token") or not result.get("refresh_token"):
                    raise ChatGPTAuthError("refresh_failed")
                profile["access_token"] = result["access_token"]
                profile["refresh_token"] = result["refresh_token"]
                profile["expires_at"] = time.time() + int(result.get("expires_in", 3600))
                if "scope" in result:
                    if not isinstance(result["scope"], str):
                        raise ChatGPTAuthError("invalid_scope")
                    profile["scopes"] = result["scope"].split()
                self._save(profile)
                if PERMISSION not in profile.get("scopes", []):
                    raise ChatGPTAuthError("permission_not_granted")
                token = profile["access_token"]
            return token

    def models(self) -> list[dict]:
        token = self.access_token()
        payload = _request_json(RESOURCE + "/models", token=token, limit=2_000_000)
        models = payload.get("models", [])
        if not isinstance(models, list):
            raise ChatGPTAuthError("invalid_models")
        return [
            {"slug": m["slug"], "display_name": m.get("display_name", m["slug"])}
            for m in models
            if isinstance(m, dict) and m.get("visibility") == "list"
            and isinstance(m.get("slug"), str) and m["slug"]
        ][:40]

    def reply(self, message: str, model: str = "") -> str:
        if not isinstance(message, str) or not message.strip() or len(message) > MAX_PROMPT_CHARS:
            raise ChatGPTAuthError("invalid_message")
        models = self.models()
        allowed = [m["slug"] for m in models]
        if not allowed:
            raise ChatGPTAuthError("no_available_models")
        selected = model if model in allowed else allowed[0]
        payload = json.dumps({
            "model": selected,
            "instructions": (
                "Ты — Astra Evolution, персональный Android-ассистент. "
                "Отвечай по-русски. Не утверждай, что управляешь телефоном. "
                "Запуск приложений и изменения на Android требуют отдельного подтверждения "
                "в Astra; вывод модели никогда не выполняется как команда."
            ),
            "input": [{"role": "user", "content": message.strip()}],
            "store": False, "stream": True,
        }, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            RESOURCE + "/responses", data=payload,
            headers={"Authorization": "Bearer " + self.access_token(),
                     "Content-Type": "application/json", "Accept": "text/event-stream"},
            method="POST",
        )
        pieces = []
        total_chars = 0
        completed = False
        try:
            with _urlopen(request, timeout=55) as stream:
                for raw in stream:
                    if len(raw) > 131072:
                        raise ChatGPTAuthError("event_too_large")
                    line = raw.decode("utf-8").strip()
                    if not line.startswith("data: "):
                        continue
                    data = line[6:]
                    if data == "[DONE]":
                        break
                    event = json.loads(data)
                    kind = event.get("type")
                    if kind == "response.output_text.delta":
                        fragment = event.get("delta", "")
                        if not isinstance(fragment, str):
                            raise ChatGPTAuthError("invalid_model_output")
                        total_chars += len(fragment)
                        if total_chars > MAX_REPLY_CHARS:
                            raise ChatGPTAuthError("reply_too_large")
                        pieces.append(fragment)
                    elif kind == "response.completed":
                        completed = True
                    elif kind in ("response.failed", "response.incomplete", "error"):
                        raise ChatGPTAuthError("response_failed")
            if not completed or not "".join(pieces).strip():
                raise ChatGPTAuthError("response_incomplete")
            return "".join(pieces).strip()
        except ChatGPTAuthError:
            raise
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError,
                OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
            raise ChatGPTAuthError("model_unavailable") from exc


chatgpt_connection = ChatGPTConnection()
