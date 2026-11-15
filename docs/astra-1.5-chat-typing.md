# Astra Evolution 1.5.0-alpha.4 — typing indicator

Preview-only feature on `feature/astra-1.5-chatgpt-integration`; not a public release.

- An Astra message bubble with three softly rising, fading dots appears immediately after a non-empty message is sent, including when the model request is still pending.
- The three dots cycle in a staggered ~1.2s wave using native Compose animation, with existing Material theme colors.
- The user's pixel font is unchanged; no typography, accent palette, or screen layout redesign was made.
- The bubble remains visible while any requests are unfinished, disappears after responses are persisted, and is also removed upon a failed/cancelled request.
- Chat scrolls to the indicator only when already near the bottom, so older messages can be read uninterrupted.
- The bubble has the accessibility label `Astra печатает`; it is never persisted as a chat message.
- The existing exact-phrase action confirmations are unchanged; animation never dispatches tools.
- This patch **does not** implement Markdown rendering, conversational memory, or voice controls.

## Validation
- Android CI: `gradle -p android :app:testDebugUnitTest :app:assembleDebug`.
- Install locally signed standalone `.apk` on top of existing permanently signed `com.astra.assistant.preview`.
- Send a normal chat prompt with a non-trivial wait time: dots should appear immediately, animate while awaiting the reply and disappear when the reply appears.
- Repeat with a connection failure and with two rapidly sent messages to verify no stuck indicator.
- Stable production `com.astra.assistant` remains untouched.
