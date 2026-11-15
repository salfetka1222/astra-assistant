# Astra Evolution — official Sign in with ChatGPT integration plan

Status: **design only; not implemented in 1.5.0-alpha.2**.

Official developer reference:
https://developers.openai.com/siwc/token-sharing-open-source

The open-source / local-host ChatGPT plan-usage OAuth flow is a better default than requiring everyone to supply an API key. It is distinct from the regular OpenAI API-key billing path, and it **does not share ChatGPT conversations, memories, or private files**.

## Proposed secure architecture

1. Keep Astra's existing Android UI, Room chat history and local, token-authenticated Termux Bridge. Preserve the production app's signing/data.
2. On user tap **«Подключить ChatGPT»**, create and persist a stable per-device `ext_agent_host_id` (e.g. `urn:uuid:<UUIDv4>`). Store in Termux private data, never in Git.
3. Start a one-shot loopback OAuth callback listener in Termux. Generate fresh state, nonce, PKCE verifier and SHA-256 challenge for every attempt.
4. Open the official authorization flow in the system browser. Dynamically register/reuse the per-user `client_id` according to the current official Sign in with ChatGPT docs. Request ChatGPT plan usage scope only with clear, user-initiated opt-in.
5. Validate OAuth callback state and ID token; check the granted scope `chatgpt.tokens.use.direct`. Absence of consent must leave plan-powered inference disabled.
6. Protect access/refresh tokens in the local user-controlled runtime; never return them over `/v1/health` or `/v1/chat`, and never include tokens in URLs, logs or crash traces.
7. Use the supported Responses API with `store:false`, `stream:true` and authorized access token. Add proper token refresh, expiry and revocation handling. No silent fallback to separately paid API calls.
8. In Astra, show connected identity, consent state, usage management, reconnect, and disconnect. Disconnect must clear local credentials.
9. Keep model text separate from Android actions. The existing exact-phrase allowlist and explicit on-device confirmation remain mandatory; never let AI output become shell commands, contact calls or Accessibility operations.
10. Before release: integration tests for declined/revoked consent, wrong state/nonce, callback reuse, failed refresh, unavailable usage, rate limits, network losses and app backgrounding.

## Scope limitations

- ChatGPT app voice cannot be assumed to invoke Astra automatically; Android text sharing and the device's secure command bridge are separate features.
- Sign-in does not replicate the exact model state, conversation, memory, or tools of the current ChatGPT app.
- Live ChatGPT plan requests require the user's interactive browser consent and availability under current eligibility/usage policies.
- Do not silently create an API key, spend credits, or publish a release.
- Preview builds use a separate package identifier for testing, because the existing installed app has a different signing certificate from the CI release signer.
