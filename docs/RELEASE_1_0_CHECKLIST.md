# Astra Evolution 1.0 — release readiness (not yet published)

- [x] Debug Android build and Termux bridge tests passed for e071cbc.
- [x] Set candidate `versionCode=14` and `versionName=1.0.0-rc.2`; switch to `1.0.0` only after device acceptance.
- [x] Prepare release workflow for `astra-v14` from `feature/astra-1.0`; publishing is blocked until `versionName=1.0.0`.
- [x] Check versionCode 14 before release; verify tag is unused.
- [ ] Verify signing secrets and that a signed candidate upgrades the existing installed app without losing data.
- [ ] Generate SHA-256 and APK size from the actual signed release artifact.
- [ ] Publish matching `latest.json` to the update feed used by UpdateClient (currently `feature/in-app-updates/updates/latest.json`) only after the signed release is available.
- [ ] Verify in-app update download, signature check, Android install prompt and persistence after upgrade on a device.
- [ ] Test onboarding on a clean install with Termux, token connection, permission setup and offline/error states.
- [ ] Confirm the chat is clearly described as a demo engine, not a connected language model.
- [ ] Obtain explicit approval before publishing the public release.

Do not enable `publish_release` until candidate upgrade is tested, versionName is final, and the legacy updater feed is ready to point at the new release.
