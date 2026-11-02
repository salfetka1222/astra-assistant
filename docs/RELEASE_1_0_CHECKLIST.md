# Astra Evolution 1.0.0 — final release checklist

Stable release is **not published**. Public release requires separate explicit user approval.

## Confirmed on Android device

- [x] Signed v15 (`1.0.0-rc.3`) installed; settings, chat history, Termux and Android controls retained.
- [x] Opt-in test channel discovered public GitHub prerelease `astra-v16`.
- [x] In-app v15 → v16 download, checksum, APK identity verification and Android installation completed without errors.
- [x] After in-app upgrade, v16 version display, settings/history, Termux connection, Android buttons and update center confirmed.
- [x] Public v16 is marked prerelease, not stable.

## Final stable candidate

- [x] Set `versionCode=18`, `versionName=1.0.0`, and stable tag `astra-v18` (higher than installed v17).
- [ ] Verify Android unit tests, Termux bridge tests and signed v17 build.
- [ ] Verify APK version, signing identity and checksum on final artifact.
- [ ] Test clean-install onboarding and failure/offline states on device.
- [ ] Verify stable GitHub Releases metadata and update flow after explicitly authorized publication.
- [ ] Obtain separate explicit user approval for publishing public stable `astra-v18`.

## Notes

- `publish_release=false` is mandatory for candidate builds; workflow checks version and unused tag before stable publication.
- The test channel currently points to the pinned `astra-v16` prerelease and should be redesigned or hidden before general availability; it does not affect stable checks.
- Older versions before v15 use the legacy `updates/latest.json` feed and may need manual upgrade or a separately reviewed feed update.
- Chat currently uses a clearly labeled demo engine; do not market it as an active cloud LLM.

- [ ] Verify one-time 1.0 welcome dialog on upgraded v17 → v18 install and that it stays dismissed after restart.
