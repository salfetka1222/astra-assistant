# Android Accessibility Bridge — design and setup notes

The Termux agent now has bounded local tools, but it does not yet control arbitrary app screens. Full screen interaction belongs in the existing Android app (android/, package com.astra.assistant) using Android's user-enabled AccessibilityService.

## Security requirements for the bridge

1. The user enables the service manually in Android Accessibility settings. The app must explain exactly what access means.
2. Never expose a listener on 0.0.0.0. The first-stage bridge binds only to 127.0.0.1, uses a random bearer token, validates every request, limits body size, and rejects unknown actions.
3. Use a strict action allowlist: inspect current app, open an allowlisted app, tap a visible element by text only after the user requested it, scroll, and type text only into the focused field.
4. Require a visible confirmation for external side effects: sending messages, posting, purchasing, deleting, changing security settings, or sharing private data.
5. Do not continuously capture screen text or screenshots. Read only the minimum UI information needed for the active task, and keep sensitive content out of logs.
6. Include a stop switch in Astra and expire bridge requests quickly.
7. Accessibility permissions do not grant root. Android, app-specific restrictions, and secure screens may prevent actions.

## Current status

- Implemented in Termux: local SQLite memory, restricted workspace file access, a small shell-command allowlist, clipboard tools (only when explicitly invoked), and launching a few allowlisted Android packages.
- Not implemented yet: the Android AccessibilityService Kotlin class and authenticated bridge transport. Do not assume the service is active just because the Termux tools work.
- Next development step: add the service to the existing Android project on this feature branch, build an APK through GitHub Actions, install it, and manually enable the service. Then add the loopback bridge with an installation-specific token and tests.

## Current Termux tool smoke tests

Run from termux-agent/:

    python -m compileall -q .
    python -m unittest discover -s tests -v
    python agent.py --tool memory_add --args '{"content":"Astra local memory test"}'
    python agent.py --tool memory_search --args '{"query":"Astra local memory test"}'
    python agent.py --tool launch_app --args '{"package":"com.android.settings"}'

The last command may fail if Android does not permit launching the package from this shell. It is intentionally limited to a hard-coded package allowlist. The memory database is stored locally in memory.sqlite3 and is ignored by Git.