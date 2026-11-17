#!/data/data/com.termux/files/usr/bin/bash
# Download a successful private CI artifact, sign locally and check upgrade safety.
# Does NOT install, uninstall, publish or modify device data.
set -euo pipefail
umask 077
if [[ $# -ne 2 || ! "$1" =~ ^[0-9]+$ ]]; then
    echo 'Usage: bash scripts/prepare-preview-apk.sh WORKFLOW_RUN_ID OUTPUT.apk' >&2
    exit 2
fi
run_id="$1"
output_apk="$2"
repo='salfetka1222/astra-assistant'
status="$(gh run view "$run_id" -R "$repo" --json status,conclusion,headBranch --jq '[.status,.conclusion,.headBranch] | join(":")')"
if [[ "$status" != 'completed:success:feature/astra-2.0-native-foundation' ]]; then
    echo "Refusing unverified workflow: $status" >&2
    exit 3
fi
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
gh run download "$run_id" -R "$repo" --name astra-debug-apk --dir "$workdir"
if [[ ! -s "$workdir/app-debug.apk" ]]; then
    echo 'Missing APK in CI artifact' >&2
    exit 4
fi
bash "$(dirname "$0")/sign-preview-apk.sh" "$workdir/app-debug.apk" "$output_apk"
echo 'APK prepared. This script never installs it.'
if adb get-state >/dev/null 2>&1; then
    if bash "$(dirname "$0")/check-preview-upgrade.sh" "$output_apk"; then
        echo 'Device upgrade check passed; installation remains a separate explicit step.'
    else
        echo 'Device upgrade blocked. Leave the installed Preview untouched.' >&2
    fi
fi
