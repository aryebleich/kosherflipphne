#!/usr/bin/env bash
#
# Turn an EXISTING Android flip phone (Android 9+) into a filtered KosherFlip
# phone without flashing a ROM: installs the apps and makes KosherGuard the
# device owner, which lets it enforce the filter.
#
#   scripts/install-on-phone.sh [folder-with-apks]
#
# Before running:
#   1. Factory reset the phone (or remove EVERY account in Settings > Accounts).
#      Android only allows a new device owner on a phone with no accounts.
#   2. Enable Developer options (tap "Build number" 7 times) and USB debugging.
#   3. Connect it with USB and accept the "Allow USB debugging" prompt.
#
set -euo pipefail
DIR="${1:-$(cd "$(dirname "$0")/.." && pwd)/out/apks}"

command -v adb >/dev/null || { echo "adb not found - install Android platform-tools"; exit 1; }
adb wait-for-device
echo "Phone: $(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r'), Android $(adb shell getprop ro.build.version.release | tr -d '\r')"

for a in KosherGuard FlipLauncher KosherAI; do
  f="$DIR/$a.apk"
  [[ -f "$f" ]] || f="$(ls "$DIR"/$a*.apk 2>/dev/null | head -1 || true)"
  [[ -f "$f" ]] || { echo "Missing $a APK in $DIR (download them from the GitHub release)"; exit 1; }
  echo "Installing $a ..."
  adb install -r -g "$f"
done

adb shell pm disable com.kosherflip.guard/.SetupActivity >/dev/null 2>&1 || true

echo "Making KosherGuard the device owner ..."
if ! adb shell dpm set-device-owner com.kosherflip.guard/.GuardAdminReceiver; then
  cat <<'MSG'

Could not set the device owner. Usually this means an account is still on
the phone. Remove all accounts (or factory reset), then run this script again.
MSG
  exit 1
fi

adb shell cmd package set-home-activity com.kosherflip.launcher/.HomeActivity >/dev/null 2>&1 || true
adb shell am start -n com.kosherflip.guard/.AdminActivity >/dev/null

cat <<'MSG'

Done. On the phone:
  1. Choose an admin PIN in KosherGuard.
  2. Optional: KosherGuard > AI assistant > enter a Claude API key.
  3. Optional: add a Google account (Gmail / Maps / Play services) now.
  4. Optional: KosherGuard > USB debugging lock, so the filter can't be removed by computer.
MSG
