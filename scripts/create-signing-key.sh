#!/usr/bin/env bash
# Creates the private release signing key for KosherFlip and prints the GitHub secrets.
# Keep the .jks file and password safe and private. See apps/signing/README.md.
set -euo pipefail
OUT="${1:-$HOME/kosherflip-release.jks}"
[[ -e "$OUT" ]] && { echo "$OUT already exists - keep using it."; exit 1; }
command -v keytool >/dev/null || { echo "keytool not found - install a Java JDK"; exit 1; }
PASS="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
keytool -genkeypair -keystore "$OUT" -alias kosherflip -keyalg RSA -keysize 3072 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=KosherFlip release" >/dev/null
chmod 600 "$OUT"
B64="$(base64 < "$OUT" | tr -d '\n')"
cat <<MSG
Created $OUT  (back it up, and keep it private)

Add these repository secrets (GitHub -> Settings -> Secrets and variables -> Actions):
  KOSHERFLIP_KEYSTORE_B64       (the long value below)
  KOSHERFLIP_KEYSTORE_PASSWORD  $PASS
  KOSHERFLIP_KEY_ALIAS          kosherflip
  KOSHERFLIP_KEY_PASSWORD       $PASS

KOSHERFLIP_KEYSTORE_B64:
$B64
MSG
