# Astra Termux Agent — MVP

Python-based Android assistant prototype for Termux. It supports terminal commands, optional cloud/local OpenAI-compatible models, optional Telegram polling, one-shot voice input via Termux:API, and a strictly limited set of local tools.

## Safety limits in this MVP

- No arbitrary shell execution from AI output.
- File read/write operations are confined to `workspace/`; file writes create new files and do not overwrite existing ones.
- Only a small fixed list of diagnostic shell commands is available.
- Telegram polling accepts commands only from numeric IDs listed in `ALLOWED_CHAT_IDS`.
- Sending messages to other people is intentionally disabled in this MVP. Telegram mode only replies to the allowed operator chat. A later messaging module should use an explicit recipient allowlist and per-scenario rules.
- Android screen taps and accessibility actions require a separate Android `AccessibilityService` module; Termux alone cannot reliably control arbitrary app interfaces without root.

## Install on Android

Install Termux and Termux:API from the same trusted source (preferably F-Droid or the official Termux GitHub releases; avoid mixing sources). Install the Termux:API companion app if you need battery/Wi-Fi/voice functions.

In Termux:

```sh
pkg update && pkg upgrade -y
pkg install -y python git termux-api
mkdir -p ~/astra-agent
cd ~/astra-agent
```

Download the project folder from the repository branch `feature/termux-agent-mvp`, or clone the repository and copy `termux-agent`:

```sh
git clone -b feature/termux-agent-mvp https://github.com/salfetka1222/astra-assistant.git
cp -r astra-assistant/termux-agent/. ~/astra-agent/
cd ~/astra-agent
mkdir -p workspace
python -m unittest discover -s tests -v
```

If you already have the repository cloned, update it with `git fetch origin` and `git checkout feature/termux-agent-mvp`, then copy the folder as above.

## First run (no API key required)

```sh
python agent.py --tool safe_command --args '{"command":"python --version"}'
python agent.py --tool list_files
python agent.py --tool device_status
```

The basic local tools work without an AI key. To use the voice command:

```sh
python agent.py --voice
```

Grant microphone permission when Android asks. Voice requires the Termux:API companion app and its Android permission.

## Configure a model

Choose one path.

### Cloud model

Set environment variables in the current Termux session (replace values locally; do not paste API keys into chat or commit them):

```sh
export CLOUD_BASE_URL="https://api.openai.com/v1"
export CLOUD_MODEL="YOUR_SUPPORTED_MODEL"
export CLOUD_API_KEY="YOUR_SECRET_KEY"
python agent.py --ask "Покажи файлы рабочего пространства"
```

Use a model name actually available to your API account. The API key remains only in your Termux environment.

### Local Ollama endpoint

If an Ollama server is running and reachable from the phone, configure:

```sh
export LOCAL_BASE_URL="http://127.0.0.1:11434/v1"
export LOCAL_MODEL="YOUR_INSTALLED_MODEL"
python agent.py --ask "Какие инструменты у тебя есть?"
```

The local model is optional; running large models directly on Android may be slow or impractical. The agent falls back to local-only mode when no model is configured.

## Telegram control (optional)

1. Create a bot with BotFather and keep its token private.
2. Find your own numeric Telegram chat ID using a trusted method.
3. In Termux set:

```sh
export TELEGRAM_BOT_TOKEN="YOUR_BOT_TOKEN"
export ALLOWED_CHAT_IDS="YOUR_NUMERIC_CHAT_ID"
python agent.py --telegram
```

Open your bot and send `/start`. Available commands: `/help`, `/status`, `/files`, `/ask your question`.

Never use a bot token from a public repo or allow unknown chat IDs. In this MVP the bot does not send messages to third parties.

## Additional local tools

- memory_add and memory_search: private local SQLite memory (file memory.sqlite3).
- clipboard_get and clipboard_set: use Termux:API; clipboard reading should only be invoked when you explicitly want it.
- launch_app: attempts to open only a small hard-coded package allowlist. Android may reject the command on some devices.

Examples:

    python agent.py --tool memory_add --args '{"content":"Astra local memory test"}'
    python agent.py --tool memory_search --args '{"query":"Astra local memory test"}'
    python agent.py --tool launch_app --args '{"package":"com.android.settings"}'
    python agent.py --tool clipboard_set --args '{"text":"Text copied by Astra"}'

## Android ↔ Termux bridge (first stage)

The bridge exposes only read-only tools to the Astra Android app. It listens on `127.0.0.1:8765` only, requires a random bearer token, limits request bodies, and rejects write/launch/clipboard tools. It is not exposed to Wi-Fi or the public internet.

Start it in Termux from this folder:

```sh
python bridge.py
```

Copy the one-time token printed in Termux into Astra → **Termux** → **Проверить соединение**. Keep the token private. The app stores it in its private preferences. Stop the bridge with Ctrl+C in Termux. The first-stage buttons can request the health status, device status, and workspace listing. The bridge must be running while using these features.

Only these tools are currently exposed: `device_status`, `list_files`, `read_file`, `memory_search`, and `safe_command`. The existing fixed command allowlist and workspace path checks still apply. Do not bind this server to `0.0.0.0`, do not forward its port, and do not use a short or public token.

Tests:

```sh
python -m unittest discover -s tests -v
```

See ANDROID_BRIDGE.md for security boundaries and the next development stage.

## Tests

```sh
python -m unittest discover -s tests -v
```

This is a minimal safe starting point, not yet a fully autonomous phone controller. The next stage is the separate Android accessibility bridge, explicit per-recipient messaging rules, and an on-device confirmation/stop control.
