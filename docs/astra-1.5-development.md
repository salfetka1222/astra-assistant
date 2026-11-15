# Astra Evolution 1.5 — development plan

Branch: `feature/astra-1.5-safe-actions`. This is a development branch, **not a release**.

## Milestone 1 — confirmed Android actions (implemented in this branch)

- Chat recognizes only a small, exact allowlist of Russian phrases.
- Supported: Android settings, Wi-Fi settings, display settings, sound settings, application settings.
- Every action shows an explicit confirmation dialog **before** launching the Android Intent.
- Cancellation and launch result are recorded in the local chat history.
- No shell execution, AccessibilityService actions, contacting people, or model-generated action sequences.
- The pending action is intentionally not restored after process recreation.
- Version displayed under Settings is read from the installed APK rather than hardcoded.

## Milestone 2 — bridge reliability (not implemented yet)

- Explicit connection diagnostics and recovery when the loopback bridge is unavailable.
- Keep the HTTP tool allowlist read-only, token-protected and loopback-only.
- Clear recovery messaging for stopped Termux, wrong token, and tool disabled by policy.

## Milestone 3 — phone integration (not implemented yet)

- Safely route explicit voice requests to the same confirmation flow.
- Plan contact resolution for `Позвони [контакт]` using Android runtime contacts/call permissions.
- Require confirmation of both contact identity and telephone number before initiating any call.
- Ensure multi-match contact resolution and an always-visible cancel option.
- Confirm feature availability on a real Android device before promising hands-free control.

## Validation

- `cd termux-agent && python -m unittest discover -s tests -q`
- `gradle -p android :app:testDebugUnitTest :app:assembleDebug` on CI
- Device test: type `Открой настройки`, verify nothing opens before confirmation; then confirm.
- Device test: cancel, rotate/kill the app, try malformed or multi-step instructions; no action must launch.
- Do not publish signed releases without explicit approval.
