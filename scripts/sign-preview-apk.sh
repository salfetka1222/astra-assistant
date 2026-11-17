#!/data/data/com.termux/files/usr/bin/bash
# Sign Astra Preview APKs with the persistent, private on-device development key.
# Usage: bash scripts/sign-preview-apk.sh INPUT.apk OUTPUT.apk
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

# Never sign the stable application by mistake: previews must use their own package.
package="$(aapt dump badging "$source_apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
if [[ "$package" != "com.astra.assistant.preview" ]]; then
    echo "Refusing to sign unexpected package: ${package:-unknown}" >&2
    exit 4
fi

output_dir="$(dirname "$output_apk")"
mkdir -p "$output_dir"
workdir="$(mktemp -d "$output_dir/.astra-sign.XXXXXXXX")"
trap 'rm -rf "$workdir"' EXIT
zipalign -f 4 "$source_apk" "$workdir/aligned.apk"
apksigner sign \
    --ks "$keystore" \
    --ks-key-alias astra-preview \
    --ks-pass "file:$passfile" \
    --out "$workdir/signed.apk" "$workdir/aligned.apk"
apksigner verify --verbose "$workdir/signed.apk" >/dev/null

# Protect the existing Preview installation from a silent signing-key change.
if [[ -s "$output_apk" ]]; then
    old_cert="$(apksigner verify --print-certs "$output_apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
    new_cert="$(apksigner verify --print-certs "$workdir/signed.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
    if [[ -z "$old_cert" || -z "$new_cert" || "$old_cert" != "$new_cert" ]]; then
        echo "Refusing to replace existing APK with a different or unverifiable signing identity" >&2
        exit 5
    fi
fi
mv -f "$workdir/signed.apk" "$output_apk"
echo "Signed Preview APK: $output_apk"
sha256sum "$output_apk"
