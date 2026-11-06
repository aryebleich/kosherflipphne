#!/usr/bin/env bash
#
# Install KosherFlip on an EXISTING Android phone (Android 8+, incl. Android Go
# flip phones such as the CAT S22 Flip) without replacing its system. The
# phone keeps its own firmware, so calls, texts and VoLTE keep working on
# every carrier.
#
#   ./install-on-phone.sh [folder-with-apks]     (downloads the latest release if no folder)
#
# Before running (see docs/INSTALL.md):
#   1. Factory reset the phone (or remove EVERY account in Settings > Accounts).
#   2. Settings > About phone > tap "Build number" 7 times; Developer options > USB debugging ON.
#   3. Connect by USB and accept "Allow USB debugging".
#
set -euo pipefail
REPO="${KOSHERFLIP_REPO:-aryebleich/kosherflipphne}"
APPS="KosherGuard FlipLauncher KosherBrowser KosherAI KosherKeys"
DIR="${1:-}"

say() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*"; exit 1; }
sh_() { adb shell "$@" | tr -d '\r'; }

command -v adb >/dev/null || die "adb not found. Install Android platform-tools (see docs/INSTALL.md, step 1)."

if [[ -z "$DIR" ]]; then
  DIR="$(mktemp -d)"
  say "Downloading the latest KosherFlip apps from github.com/$REPO"
  for a in $APPS; do
    curl -fL --retry 3 -o "$DIR/$a.apk" "https://github.com/$REPO/releases/latest/download/$a.apk" \
      || die "Could not download $a.apk. Download the release files by hand and pass the folder."
  done
fi

say "Waiting for the phone (accept the USB debugging prompt on the phone)"
adb wait-for-device
SDK="$(sh_ getprop ro.build.version.sdk)"
echo "Phone: $(sh_ getprop ro.product.manufacturer) $(sh_ getprop ro.product.model) - Android $(sh_ getprop ro.build.version.release) (API $SDK)$( [[ "$(sh_ getprop ro.config.low_ram)" == true ]] && echo ', Android Go')"
[[ "$SDK" -ge 26 ]] || die "Android 8 or newer is needed (this phone has API $SDK)."

if sh_ dumpsys device_policy 2>/dev/null | grep -qiE "Device Owner|DeviceOwner" \
   && ! sh_ dumpsys device_policy 2>/dev/null | grep -q "com.kosherflip.guard"; then
  die "Another filter/management app is already the device owner (common on pre-filtered kosher phones).
Factory reset the phone first; if the phone's own filter comes back after a reset, it is built into the
firmware and only the phone's vendor can remove it."
fi
ACCOUNTS="$(sh_ dumpsys account 2>/dev/null | grep -c 'Account {' || true)"
if [[ "${ACCOUNTS:-0}" -gt 0 ]]; then
  echo "Warning: $ACCOUNTS account(s) found on the phone. Android refuses a new device owner while accounts exist."
  echo "Remove them in Settings > Accounts (or factory reset), then run this again."
fi

for a in $APPS; do
  f="$DIR/$a.apk"
  [[ -f "$f" ]] || f="$(ls "$DIR"/$a*.apk 2>/dev/null | head -1 || true)"
  [[ -f "$f" ]] || die "Missing $a.apk in $DIR"
  say "Installing $a"
  adb install -r -g "$f"
done

adb shell pm disable com.kosherflip.guard/.SetupActivity >/dev/null 2>&1 || true
# Screen filter + keyboard: let KosherGuard turn its accessibility service and the T9 keyboard on.
adb shell pm grant com.kosherflip.guard android.permission.WRITE_SECURE_SETTINGS \
  || echo "Note: could not grant WRITE_SECURE_SETTINGS; turn on Settings > Accessibility > KosherGuard by hand."

say "Making KosherGuard the device owner (the filter)"
if ! adb shell dpm set-device-owner com.kosherflip.guard/.GuardAdminReceiver; then
  die "Android did not accept the device owner. Usually an account is still on the phone:
remove all accounts (or factory reset) and run this script again."
fi

adb shell cmd package set-home-activity com.kosherflip.launcher/.HomeActivity >/dev/null 2>&1 || true
adb shell ime enable com.kosherflip.keys/.T9Service >/dev/null 2>&1 || true
adb shell am start -n com.kosherflip.guard/.AdminActivity >/dev/null

cat <<'MSG'

Done! Now, on the phone:
  1. KosherGuard opens: choose an admin PIN (keep it private).
  2. Profile: Kosher / Heimish / Open + protected.
  3. Optional: AI assistant -> paste a FREE Gemini key (aistudio.google.com).
  4. Optional: add a Google account now (Gmail, Maps, Play services).
  5. Last: turn on "USB debugging lock" so the filter can't be removed with a computer.
MSG
