#!/data/data/com.termux/files/usr/bin/bash
# Sign Astra Preview builds with the persistent, private on-device development key.
# Usage: bash scripts/sign-preview-apk.sh build.apk /path/to/Astra-Preview.apk
set -euo pipefail
umask 077

if [[ $# -ne 2 ]]; then
    echo "Usage: bash scripts/sign-preview-apk.sh INPUT.apk OUTPUT.apk" >&2
    exit 2
fi
source_apk="$1"
output_apk="$2"
key_dir="$HOME/.astra-preview-signing"
keystore="$key_dir/astra-preview.jks"
passfile="$key_dir/signing.pass"

if [[ ! -s "$source_apk" ]]; then
    echo "Input APK is missing or empty" >&2
    exit 2
fi
if [[ ! -s "$keystore" || ! -s "$passfile" ]]; then
    echo "Missing permanent Preview key. Refusing to create a new identity silently." >&2
    exit 3
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
zipalign -f 4 "$source_apk" "$workdir/aligned.apk"
apksigner sign \
    --ks "$keystore" \
    --ks-key-alias astra-preview \
    --ks-pass "file:$passfile" \
    --out "$workdir/signed.apk" "$workdir/aligned.apk"
apksigner verify --verbose "$workdir/signed.apk" >/dev/null
cp "$workdir/signed.apk" "$output_apk"
echo "Signed Preview APK: $output_apk"
sha256sum "$output_apk"
