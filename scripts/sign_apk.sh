#!/usr/bin/env bash
# Usage: scripts/sign_apk.sh <unsigned.apk> <out.apk>
#
# With RELEASE_KEYSTORE_B64 and RELEASE_KEYSTORE_PASSWORD (GitHub Secrets) set, signs with key
# rotation (APK Signature Scheme v3 lineage) from the old public debug key to the private release
# key: devices that installed a version signed with the old key update in place, and afterwards
# only the private key can sign updates. Without the secrets (forks), signs with the debug key only
# and reports official=false so the release job does not publish it.
set -euo pipefail
IN="$1"; OUT="$2"
BT="${ANDROID_HOME:-/usr/local/lib/android/sdk}/build-tools/35.0.0"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
"$BT/zipalign" -f -p 4 "$IN" "$WORK/aligned.apk"
OLD=(--ks keystore/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android)
if [ -n "${RELEASE_KEYSTORE_B64:-}" ] && [ -n "${RELEASE_KEYSTORE_PASSWORD:-}" ]; then
  printf '%s' "$RELEASE_KEYSTORE_B64" | base64 -d > "$WORK/release.p12"
  NEW=(--ks "$WORK/release.p12" --ks-key-alias release
       --ks-pass env:RELEASE_KEYSTORE_PASSWORD --key-pass env:RELEASE_KEYSTORE_PASSWORD)
  "$BT/apksigner" rotate --out "$WORK/lineage" --old-signer "${OLD[@]}" --new-signer "${NEW[@]}"
  "$BT/apksigner" sign "${OLD[@]}" --next-signer "${NEW[@]}" --lineage "$WORK/lineage" \
    --rotation-min-sdk-version 28 --out "$OUT" "$WORK/aligned.apk"
  OFFICIAL=true
else
  echo "::warning::Release signing secrets not set; signed with the public debug key only (not published)."
  "$BT/apksigner" sign "${OLD[@]}" --out "$OUT" "$WORK/aligned.apk"
  OFFICIAL=false
fi
"$BT/apksigner" verify --verbose --print-certs "$OUT" | grep -E "Verified using|Signer #|certificate SHA-256|lineage" || true
echo "official=$OFFICIAL"
if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "official=$OFFICIAL" >> "$GITHUB_OUTPUT"; fi
