# Astra Device Intelligence — progressive, owner-controlled discovery

Status: approved direction, staged implementation. This design does not claim that Android grants universal access. **No public release without the owner's approval.**

## Goal

Astra should develop a useful map of the device, discover where permitted actions live, learn successful user-approved workflows, and reduce repeated navigation. Local-first personalization is the aim; unrestricted surveillance is not a capability requirement.

## Non-negotiable constraints

- Android 11+ limits package visibility; normal third-party apps cannot read all other apps' private data. Do not promise a literal scan of everything.
- No silent collection of messages, credentials, OTPs, financial information, health information, media contents, browser history, or private app databases.
- Every data category has a separate visible opt-in, explanation, retention policy, and delete/disable control. Permission denial must keep core functions working.
- By default, data stays in app-private local storage. Do not send the device map, app catalog, or behavioral summaries to AI endpoints or telemetry. Any future transmission must require an explicit and scope-limited user action.
- No root requirement; Shizuku remains optional, does not bypass user permission policy. Do not include QUERY_ALL_PACKAGES without assessing necessity and store policy.
- Revoke and erase personal memory independently from chat history. Protect stored data as personal; app backups must not silently leak it.
- Verify actions through approved system APIs, bounded Accessibility observations, or explicit user confirmation. Keep confirmation for risky actions, rather than blindly replaying learned actions.

## Roadmap

1. **2.1a — Launcher application catalog.** Scan only launchable app names and package IDs on request using narrow <queries> visibility. Store locally after an in-app disclosure, support search and manual erase. No app content, usage stats, or cloud access. Treat results as partial when Android hides apps.
2. **2.1b — User-approved usage patterns.** Offer a separate settings flow for Android Usage Access, then aggregate app frequency and approximate active times on-device. Explain the OS limits and make this optional; never log text input or what users say inside apps.
3. **2.1c — Device settings and capability graph.** Add explicit allowlisted settings/intents and mark availability, observed success/failure, permissions, and safe fallback paths. Do not present failed Intent dispatch as successful execution.
4. **2.1d — Selected files and media.** Use Android's system document and photo pickers for user-chosen items or folders only. Index filenames and metadata where consented; do not crawl private app directories.
5. **2.1e — Optional contacts, calendar, notifications.** Each requires dedicated permissions and a purpose-specific workflow; use the minimal data needed to complete the chosen command.
6. **2.2 — Adaptive local memory.** Store user-edited aliases (e.g. "музыка" -> chosen app), preferred settings screens, task successes, and failure patterns; support view, edit, export and erase.
7. **2.3 — Supervised multi-step autonomy.** Understand -> plan -> show permissions -> confirm -> act -> verify; stop on ambiguities and risky boundaries, with recovery where reversible.

## Preview validation

- 2.0.0-alpha.8 (35): owner confirmed catalog launch on the iQOO Neo 10.
- 2.0.0-alpha.9 (36): manual 24-hour usage summary for saved launcher entries. Counts foreground transitions, merges consecutive activities for the same package, and shows the latest activity time. Results live only in memory while the Tools screen is active. Device validation pending.

## Scope note

The initial catalog stores **only** names/identifiers of app launchers. A full application list or its contents is neither promised nor needed to fulfill early discovery and quick-launch workflows. The user decides when to expand categories.
