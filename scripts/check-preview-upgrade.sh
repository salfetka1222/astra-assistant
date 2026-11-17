#!/data/data/com.termux/files/usr/bin/bash
# Read-only check: can a candidate APK update the installed Astra Preview?
set -euo pipefail
if [[ $# -ne 1 || ! -s "$1" ]]; then
    echo "Usage: bash scripts/check-preview-upgrade.sh CANDIDATE.apk" >&2
    exit 2
fi
candidate="$1"
package="com.astra.assistant.preview"
read_package() {
    aapt dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1
}
read_version() {
    aapt dump badging "$1" | sed -n "s/^package:.*versionCode='\([0-9]*\)'.*/\1/p" | head -1
}
read_cert() {
    apksigner verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1
}
if [[ "$(read_package "$candidate")" != "$package" ]]; then
    echo "BLOCKED: candidate is not Astra Preview" >&2
    exit 3
fi
if ! adb get-state 2>/dev/null | grep -qx device; then
    echo "BLOCKED: ADB device unavailable" >&2
    exit 4
fi
installed_path="$(adb shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p' | head -1)"
if [[ -z "$installed_path" ]]; then
    echo "No installed Preview found; candidate can be installed as a new app."
    exit 0
fi
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT
adb pull "$installed_path" "$tmpdir/installed.apk" >/dev/null
installed_cert="$(read_cert "$tmpdir/installed.apk")"
candidate_cert="$(read_cert "$candidate")"
if [[ -z "$installed_cert" || -z "$candidate_cert" || "$installed_cert" != "$candidate_cert" ]]; then
    echo "BLOCKED: signing certificate mismatch. Do not uninstall automatically." >&2
    exit 5
fi
installed_version="$(read_version "$tmpdir/installed.apk")"
candidate_version="$(read_version "$candidate")"
if [[ -z "$installed_version" || -z "$candidate_version" || "$candidate_version" -lt "$installed_version" ]]; then
    echo "BLOCKED: candidate versionCode is older or unreadable" >&2
    exit 6
fi
echo "PASS: package, signing identity and versionCode permit an in-place update ($installed_version -> $candidate_version)."
