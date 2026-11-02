# Astra Evolution 1.0 — release readiness (not yet published)

- [x] Debug Android build and Termux bridge tests passed for e071cbc.
- [ ] Choose the final Android `versionCode` (>13) and `versionName` (1.0.0).
- [ ] Update the release workflow: it currently publishes only `astra-v9` from `feature/in-app-updates`.
- [ ] Ensure release tag number matches APK versionCode; never reuse an existing tag.
- [ ] Verify signing secrets and that a signed candidate upgrades the existing installed app without losing data.
- [ ] Generate SHA-256 and APK size from the actual signed release artifact.
- [ ] Publish matching `latest.json` to the update feed used by UpdateClient (currently `feature/in-app-updates/updates/latest.json`) only after the signed release is available.
- [ ] Verify in-app update download, signature check, Android install prompt and persistence after upgrade on a device.
- [ ] Test onboarding on a clean install with Termux, token connection, permission setup and offline/error states.
- [ ] Confirm the chat is clearly described as a demo engine, not a connected language model.
- [ ] Obtain explicit approval before publishing the public release.

Do not enable `publish_release` until the old hard-coded v9 workflow and feed are updated and tested.
