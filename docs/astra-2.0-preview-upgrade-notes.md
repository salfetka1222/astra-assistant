# Astra 2.0 Preview: safe upgrade notes

As of 2026-11-17, the installed `com.astra.assistant.preview` is `2.0.0-alpha.2` (versionCode 29). It is a debuggable app, and ADB `run-as` works. Its signing identity does **not** match the persistent Termux Preview signing key. Android will reject an in-place update signed with a different key.

## Safe defaults

- Never automatically uninstall, clear data, downgrade, or overwrite the existing Preview.
- Stable Astra `com.astra.assistant` is a separate package and must not be modified in this migration.
- GitHub Actions debug artifacts use ephemeral signatures. Always re-sign APKs locally with the persistent Preview key; never commit or upload the private key or its password.
- `bash scripts/check-preview-upgrade.sh NEW.apk` performs read-only checks of package name, signer and versionCode against the installed APK. A passing check does not replace Android's installation verification.
- `bash scripts/prepare-preview-apk.sh RUN_ID OUTPUT.apk` downloads an already-successful CI build, signs it, and runs the read-only check. It never installs or publishes anything.
- If signatures differ, leave the app in place; testing in a separate canary package or an owner-approved migration are the only non-root options.

## Data preservation

A private, mode-0600 backup of Alpha 2's debug app data was made under Termux `~/.local/share/astra-preview-backups/`. Archive integrity and SQLite integrity were checked, without opening any chat messages. Never place this backup in shared Android storage or GitHub; it may contain private app data.

The data backup is **not** a guarantee that all Android Keystore-backed secrets, login sessions, or permissions survive uninstall/reinstall. Restoration must be designed and tested separately. Do not uninstall the installed Preview without explicit permission from the device owner, even if a backup exists.

## Release boundary

Alpha 3 is a private development build. Do not publish a public release or tag without explicit authorization. Existing Preview data and Stable Astra remain untouched until the migration approach is confirmed.
