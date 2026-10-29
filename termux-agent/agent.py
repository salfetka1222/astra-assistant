#!/usr/bin/env python3
"""Astra Termux Agent MVP: bounded tools, optional OpenAI-compatible brain and Telegram."""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

BASE_DIR = Path(__file__).resolve().parent
WORKSPACE = Path(os.environ.get("AGENT_WORKSPACE", str(BASE_DIR / "workspace"))).resolve()
MAX_READ_BYTES = 20_000
ALLOWED_TOOLS = {"device_status", "list_files", "read_file", "write_file", "safe_command", "open_url"}


def _request_json(url: str, payload: dict[str, Any] | None = None, headers: dict[str, str] | None = None, timeout: int = 30) -> Any:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers=headers or {}, method="GET" if payload is None else "POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")[:1000]
        raise RuntimeError(f"HTTP {exc.code}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Не удалось подключиться: {exc.reason}") from exc


def ensure_workspace() -> None:
    WORKSPACE.mkdir(parents=True, exist_ok=True)


def safe_path(value: str) -> Path:
    ensure_workspace()
    candidate = (WORKSPACE / value).resolve()
    if candidate != WORKSPACE and WORKSPACE not in candidate.parents:
        raise ValueError("Доступ разрешён только внутри папки workspace.")
    return candidate


def tool_device_status(_: dict[str, Any]) -> dict[str, Any]:
    info: dict[str, Any] = {"workspace": str(WORKSPACE), "python": sys.version.split()[0]}
    for name, command in (
        ("battery", ["termux-battery-status"]),
        ("wifi", ["termux-wifi-connectioninfo"]),
    ):
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=5, check=False)
            if result.returncode == 0:
                try:
                    info[name] = json.loads(result.stdout)
                except json.JSONDecodeError:
                    info[name] = result.stdout[:500]
            else:
                info[name] = "Недоступно: установи Termux:API и выдай разрешения."
        except (FileNotFoundError, subprocess.TimeoutExpired):
            info[name] = "Недоступно: установи Termux:API и приложение-компаньон."
    return info


def tool_list_files(args: dict[str, Any]) -> dict[str, Any]:
    folder = safe_path(str(args.get("path", ".")))
    if not folder.exists() or not folder.is_dir():
        raise ValueError("Папка не найдена.")
    entries = []
    for item in sorted(folder.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))[:100]:
        entries.append({"name": item.name, "type": "directory" if item.is_dir() else "file"})
    return {"path": str(folder.relative_to(WORKSPACE)), "entries": entries}


def tool_read_file(args: dict[str, Any]) -> dict[str, Any]:
    path = safe_path(str(args.get("path", "")))
    if not path.is_file():
        raise ValueError("Файл не найден.")
    if path.stat().st_size > MAX_READ_BYTES:
        raise ValueError(f"Файл больше лимита {MAX_READ_BYTES} байт.")
    return {"path": str(path.relative_to(WORKSPACE)), "content": path.read_text(encoding="utf-8")}


def tool_write_file(args: dict[str, Any]) -> dict[str, Any]:
    relative = str(args.get("path", "")).strip()
    content = args.get("content")
    if not relative or not isinstance(content, str):
        raise ValueError("Нужны path и строковый content.")
    if len(content.encode("utf-8")) > MAX_READ_BYTES:
        raise ValueError(f"Размер файла ограничен {MAX_READ_BYTES} байт.")
    path = safe_path(relative)
    if path.exists():
        raise ValueError("Файл уже существует. MVP не перезаписывает файлы.")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return {"ok": True, "path": str(path.relative_to(WORKSPACE)), "bytes": path.stat().st_size}


SAFE_COMMANDS: dict[str, list[str]] = {
    "pwd": ["pwd"],
    "date": ["date"],
    "python --version": ["python", "--version"],
    "git status": ["git", "-C", str(BASE_DIR), "status", "--short"],
    "disk": ["df", "-h", str(BASE_DIR)],
    "list workspace": ["ls", "-la", str(WORKSPACE)],
}


def tool_safe_command(args: dict[str, Any]) -> dict[str, Any]:
    name = str(args.get("command", "")).strip()
    command = SAFE_COMMANDS.get(name)
    if command is None:
        raise ValueError("Команда не разрешена. Доступно: " + ", ".join(SAFE_COMMANDS))
    result = subprocess.run(command, capture_output=True, text=True, timeout=10, check=False)
    return {"command": name, "returncode": result.returncode, "stdout": result.stdout[-4000:], "stderr": result.stderr[-1000:]}


def tool_open_url(args: dict[str, Any]) -> dict[str, Any]:
    url = str(args.get("url", "")).strip()
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in {"https", "http"} or not parsed.netloc:
        raise ValueError("Разрешены только полные http/https URL.")
    try:
        subprocess.Popen(["termux-open-url", url], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except FileNotFoundError as exc:
        raise RuntimeError("Команда termux-open-url не найдена. Установи Termux:API.") from exc
    return {"ok": True, "opened": url}


TOOLS = {
    "device_status": tool_device_status,
    "list_files": tool_list_files,
    "read_file": tool_read_file,
    "write_file": tool_write_file,
    "safe_command": tool_safe_command,
    "open_url": tool_open_url,
}


def run_tool(name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
    if name not in ALLOWED_TOOLS or name not in TOOLS:
        raise ValueError("Такой инструмент не разрешён.")
    if arguments is not None and not isinstance(arguments, dict):
        raise ValueError("Аргументы инструмента должны быть объектом.")
    return TOOLS[name](arguments or {})


def ask_model(text: str) -> str:
    """Ask configured cloud or local OpenAI-compatible endpoint for a reply/tool JSON."""
    api_key = os.environ.get("CLOUD_API_KEY", "").strip()
    cloud_url = os.environ.get("CLOUD_BASE_URL", "").rstrip("/")
    cloud_model = os.environ.get("CLOUD_MODEL", "").strip()
    local_url = os.environ.get("LOCAL_BASE_URL", "http://127.0.0.1:11434/v1").rstrip("/")
    local_model = os.environ.get("LOCAL_MODEL", "").strip()

    if api_key and cloud_url and cloud_model:
        url, model, headers = cloud_url + "/chat/completions", cloud_model, {"Authorization": f"Bearer {api_key}"}
    elif local_model:
        url, model, headers = local_url + "/chat/completions", local_model, {}
    else:
        return "ИИ-модель пока не настроена. Локальные инструменты уже доступны: /status, /files, /help. Заполни .env или экспортируй переменные окружения."

    system = (
        "Ты Astra, безопасный Android-агент. Отвечай по-русски. "
        "Ты не можешь выполнять произвольные shell-команды и не должен просить секреты. "
        "Если требуется один из инструментов, верни только JSON вида "
        '{"tool":"list_files","arguments":{"path":"."}}. '
        "Доступные инструменты: device_status, list_files, read_file, write_file, safe_command, open_url. "
        "safe_command принимает только pwd, date, python --version, git status, disk, list workspace. "
        "write_file создаёт только новый файл в workspace и не перезаписывает существующие. "
        "Не используй инструменты для отправки сообщений; этой возможности у модели нет. "
        "Если инструмент не нужен, отвечай обычным текстом."
    )
    payload = {"model": model, "messages": [{"role": "system", "content": system}, {"role": "user", "content": text}], "temperature": 0.2}
    result = _request_json(url, payload, {"Content-Type": "application/json", **headers}, timeout=60)
    try:
        answer = result["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError("Модель вернула ответ в неожиданном формате.") from exc
    if not isinstance(answer, str):
        return str(answer)
    stripped = answer.strip()
    if stripped.startswith("{") and stripped.endswith("}"):
        try:
            decision = json.loads(stripped)
            tool = decision.get("tool")
            if tool in ALLOWED_TOOLS:
                output = run_tool(tool, decision.get("arguments", {}))
                return json.dumps(output, ensure_ascii=False, indent=2)
        except (json.JSONDecodeError, ValueError, RuntimeError) as exc:
            return f"Не удалось безопасно выполнить инструмент: {exc}"
    return answer


def telegram_api(token: str, method: str, payload: dict[str, Any]) -> Any:
    url = f"https://api.telegram.org/bot{token}/{method}"
    return _request_json(url, payload, {"Content-Type": "application/json"}, timeout=35)


def allowed_chat_ids() -> set[int]:
    raw = os.environ.get("ALLOWED_CHAT_IDS", "")
    ids: set[int] = set()
    for part in raw.split(","):
        part = part.strip()
        if part:
            try:
                ids.add(int(part))
            except ValueError:
                continue
    return ids


def telegram_loop() -> None:
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    if not token:
        raise RuntimeError("Укажи TELEGRAM_BOT_TOKEN в .env.")
    allowed = allowed_chat_ids()
    if not allowed:
        raise RuntimeError("Сначала добавь свой Telegram chat ID в ALLOWED_CHAT_IDS.")
    offset = 0
    print("Telegram-агент запущен. Остановка: Ctrl+C")
    while True:
        try:
            updates = telegram_api(token, "getUpdates", {"timeout": 25, "offset": offset, "allowed_updates": ["message"]})
            for update in updates.get("result", []):
                offset = max(offset, int(update.get("update_id", 0)) + 1)
                message = update.get("message", {})
                chat = message.get("chat", {})
                chat_id = chat.get("id")
                text = str(message.get("text", "")).strip()
                if not isinstance(chat_id, int) or chat_id not in allowed or not text:
                    continue
                if text == "/start":
                    answer = "Привет! Я Astra Termux Agent. Команды: /help, /status, /files, /ask текст. Отправка сообщений другим людям выключена в MVP."
                elif text == "/help":
                    answer = "Команды:\n/status — состояние устройства\n/files — файлы workspace\n/ask текст — вопрос ИИ\n/help — помощь"
                elif text == "/status":
                    answer = json.dumps(run_tool("device_status"), ensure_ascii=False, indent=2)
                elif text == "/files":
                    answer = json.dumps(run_tool("list_files"), ensure_ascii=False, indent=2)
                elif text.startswith("/ask "):
                    answer = ask_model(text[5:].strip())
                else:
                    answer = "Неизвестная команда. Напиши /help."
                telegram_api(token, "sendMessage", {"chat_id": chat_id, "text": answer[:4000]})
        except KeyboardInterrupt:
            print("\nОстановлено.")
            return
        except Exception as exc:
            print(f"Telegram polling error: {exc}", file=sys.stderr)
            time.sleep(3)


def main() -> int:
    parser = argparse.ArgumentParser(description="Astra Termux Agent MVP")
    parser.add_argument("--telegram", action="store_true", help="Запустить Telegram-бота")
    parser.add_argument("--voice", action="store_true", help="Распознать одну голосовую команду через Termux:API")
    parser.add_argument("--ask", help="Передать текстовую задачу ИИ")
    parser.add_argument("--tool", choices=sorted(ALLOWED_TOOLS), help="Запустить локальный инструмент")
    parser.add_argument("--args", default="{}", help="Аргументы инструмента в JSON")
    args = parser.parse_args()
    ensure_workspace()
    try:
        if args.telegram:
            telegram_loop()
        elif args.voice:
            result = subprocess.run(["termux-speech-to-text"], capture_output=True, text=True, timeout=90, check=False)
            if result.returncode:
                print(result.stderr or "Не удалось распознать речь.", file=sys.stderr)
                return 1
            spoken = result.stdout.strip()
            print("Распознано:", spoken)
            print(ask_model(spoken))
        elif args.ask is not None:
            print(ask_model(args.ask))
        elif args.tool:
            parsed = json.loads(args.args)
            print(json.dumps(run_tool(args.tool, parsed), ensure_ascii=False, indent=2))
        else:
            parser.print_help()
    except (ValueError, RuntimeError, json.JSONDecodeError, subprocess.TimeoutExpired) as exc:
        print(f"Ошибка: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
