# Astra Evolution 1.5 — ChatGPT integration (experimental)

This development build has **two independent connection paths**.

## 1. ChatGPT Android app → Astra (manual, free)

Astra receives Android `ACTION_SEND` intents with `text/plain`.

- In ChatGPT, use its available text sharing function, select Astra in the Android share sheet.
- Astra opens its chat and puts the shared text into the input field.
- The user reviews and presses Send; nothing runs or gets sent automatically.
- If the exact text is an allowlisted Android settings command, Astra still requires confirmation.
- The share feature depends on whether the source application exposes Android text sharing. It does **not** automate ChatGPT voice mode or copy its conversation history.

For test automation over a **previously authorized ADB session**, the same entry point accepts:
```sh
adb shell am start -a android.intent.action.SEND -t text/plain \
  -n com.astra.assistant/.MainActivity \
  --es android.intent.extra.TEXT 'Открой настройки'
```
This prepares a draft only and never performs the action without the user.

## 2. Astra chat → model using Termux Bridge (optional)

The existing authenticated loopback bridge exposes a separate `POST /v1/chat` endpoint. The provider credentials stay in the Termux process; the Android application receives only the reply.

- `CLOUD_API_KEY` or `OPENAI_API_KEY`: a separately created provider API key.
- `CLOUD_BASE_URL`: an HTTPS OpenAI-compatible API endpoint, default `https://api.openai.com/v1`.
- `CLOUD_MODEL`: a model ID supported by that API key.
- Alternatively `LOCAL_MODEL` and `LOCAL_BASE_URL`, if a compatible model server runs on this **same Android device** at loopback.
- Restart `python bridge.py` after configuring the variables in the Termux shell.
- In Astra → Tools → Termux, check the health endpoint to confirm `Диалог: ИИ подключён`.

**Key distinction:** ChatGPT subscription and OpenAI API billing are separate; this does not connect an existing ChatGPT chat or import its memory, tools, or identity. A local model can work without OpenAI API charges if you provide and run the model yourself.

The `/v1/chat` response is text-only. No model output can call `run_tool` through this endpoint. File, shell, accessibility, and phone-call actions remain inaccessible to remote model output. Existing safe command confirmation is unchanged.

Security: Do not commit API keys to GitHub or paste them into chats. The bridge listens only on `127.0.0.1:8765`, requires its high-entropy token, limits message length, rejects cross-origin browser calls, and returns redacted provider errors.

## Limitations / future milestones

- No automatic ChatGPT-app-to-Astra calls from ChatGPT voice yet; a platform-supported external-action interface is needed for that.
- No shared conversational context yet: each model request is independent.
- Call contacts only after explicit contact matching and per-call confirmation in a future release.
- Only this development branch is changed; no public signed release without approval.
- Development debug builds use application ID `com.astra.assistant.preview` and label **Astra Preview**. They can install alongside the stable app even if the signing certificates differ.
- Test APKs should be placed as a **standalone `.apk`** in Android Downloads, not distributed to the user as ZIP archives.
- Do not uninstall stable Astra to install a mismatched-signature test build.
