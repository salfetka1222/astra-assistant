# Astra Evolution 1.5.0-alpha.3 — Sign in with ChatGPT

Implementation: `feature/astra-1.5-chatgpt-integration`. Experimental Preview only; stable release untouched.

## What is implemented

- Android button **Продолжить с ChatGPT** in **Инструменты → Мост Termux**.
- Official OAuth authorization with `client_id=dynamic_agent_client` for first-time registration and a stable `ext_agent_host_id` in Termux private storage.
- One-time loopback callback bound to `127.0.0.1` on an ephemeral port, with a five-minute deadline, PKCE S256, random state and OIDC nonce.
- Token exchange with OpenAI's token endpoint. OIDC ID token signature verified using OpenAI's JWKS, with checks for issuer, audience, expiry, nonce, and subject.
- Access/refresh/ID tokens stored under `~/.config/astra-evolution/chatgpt.json` with owner-only permissions. They never appear in Android API responses.
- Only a validated, explicitly granted `chatgpt.tokens.use.direct` scope allows ChatGPT plan inference.
- Selected model comes from the user's `GET /v1/models` catalog, with text-only `POST /v1/responses` using `store:false`, `stream:true`; model output is never used as an Android tool command.
- Explicit status and disconnect controls. No silent paid API-key fallback (`ASTRA_ALLOW_PAID_API=1` is an opt-in development override, not required).
- Existing Android system actions still require per-action confirmation.

Official docs: https://developers.openai.com/siwc/token-sharing-open-source

## Test on the phone

1. Run `cd ~/astra-assistant/termux-agent && python bridge.py`. Keep it running.
2. Open **Astra Preview → Инструменты → Мост Termux**. Enter the random Termux bridge token shown on the phone (if it changed), then check the bridge.
3. Tap **Продолжить с ChatGPT**. A system browser opens the official `auth.openai.com` flow. Sign in there, choose your workspace and grant ChatGPT plan usage, if the consent is offered.
4. After the callback says authorization is received, return to Astra and tap **Обновить статус**.
5. Look for **ChatGPT подключён: использование плана разрешено**. Send a normal chat message in Astra to test Responses API.
6. To disconnect, use **Отключить ChatGPT**. Local credentials are removed even if revoking the remote session is temporarily unavailable; check ChatGPT Settings if remote revocation wasn't confirmed.

Eligibility, regional policies and plan usage are controlled by OpenAI. Successful identity sign-in alone does not guarantee plan-powered inference. ChatGPT account sign-in **does not** transfer this ChatGPT conversation, memories, voice session or private data to Astra.

## Security and limitations

- Never paste ChatGPT passwords, callback codes, session cookies or OAuth access/refresh tokens into chat or GitHub.
- An existing OpenAI API key in Termux is not used by this flow unless the user separately opts into the paid-API development override.
- The OAuth feature is text-only; ChatGPT Android voice cannot directly invoke Astra through this mechanism.
- A single saved account/registration is implemented in this Preview; multi-account picker and model picker remain future work.
- Full real-account login and model inference must be tested interactively by the device owner. Unit tests use mocked tokens and no live account access.
- Android preview APKs are delivered as standalone `.apk` in Android **Downloads**, never as ZIP archives to the user.
- GitHub Actions creates a fresh ephemeral debug signing identity on each run. For reproducible local testing, sign each downloaded debug APK with the persistent Termux-only key using `bash scripts/sign-preview-apk.sh INPUT.apk OUTPUT.apk`.
- The permanent Preview signing key is in `~/.astra-preview-signing/astra-preview.jks` with its owner-only local password file; **never commit, export or disclose them publicly**. Its identity must be preserved to install later Preview builds over this one.
- The previously installed alpha.2 used a different ephemeral certificate, so **the old Astra Preview must be uninstalled once** to install alpha.3. This deletes only Preview app data, not production Astra 1.4.0. Do not remove that preview automatically without the user's approval.
- Do not install the unsigned/ephemerally signed GitHub APK over the permanent-key Preview: sign it locally using the script first.
- No public release without user approval.
