# Astra Evolution 1.0 — release readiness (not yet published)

- [x] Debug Android build and Termux bridge tests passed for e071cbc.
- [x] Set candidate `versionCode=15` and `versionName=1.0.0-rc.3`; switch to `1.0.0` only after device acceptance.
- [x] Prepare release workflow for `astra-v15` from `feature/astra-1.0`; publishing is blocked until `versionName=1.0.0`.
- [x] Check versionCode 14 before release; verify tag is unused.
- [x] Verified v14 signed candidate upgraded the installed app without losing settings or chat history; verify v15 separately.
- [x] Signed v14 artifact and SHA-256 generated successfully; v15 verification pending.
- [x] Switch UpdateClient to GitHub Releases latest API, reading asset size and download URLs directly from the published release.
- [ ] Verify the public GitHub latest release resolves to `astra-v15` only after final publication.
- [ ] Verify in-app update download, signature check, Android install prompt and persistence after upgrade on a device.
- [ ] Test onboarding on a clean install with Termux, token connection, permission setup and offline/error states.
- [x] UI already labels chat as a demo engine, not a connected language model.
- [ ] Obtain explicit approval before publishing the public release.

Do not enable `publish_release` until candidate upgrade and updater flow are tested, and versionName is final. The legacy latest.json is no longer used by new builds; older installed versions may still read it.
