#!/usr/bin/env bash
#
# Flash the KosherFlip Android 16 image on a CAT S22 Flip.
# Follows the steps the S22 community uses (XDA "CAT S22 Flip, installing GSI rom").
#
#   ./flash-s22.sh kosherflip-android16-arm64-*.img.xz vbmeta-disabled.img
#
# ERASES THE PHONE. Read docs/S22-ANDROID16.md first. You need: a computer with
# adb/fastboot (platform-tools), the phone on firmware V29 or V30, USB debugging on,
# and the stock firmware saved so you can go back.
set -euo pipefail
IMG="${1:?usage: flash-s22.sh <kosherflip-...img.xz> <vbmeta-disabled.img>}"
VBMETA="${2:-vbmeta-disabled.img}"
say() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
ask() { read -r -p "$1 [y/N] " a; [[ "$a" == y || "$a" == Y ]]; }

command -v fastboot >/dev/null && command -v adb >/dev/null || { echo "Install Android platform-tools first"; exit 1; }
[[ -f "$VBMETA" ]] || { echo "Missing $VBMETA"; exit 1; }
if [[ "$IMG" == *.xz ]]; then say "Decompressing $IMG"; xz -dkf "$IMG"; IMG="${IMG%.xz}"; fi

if adb get-state >/dev/null 2>&1; then
  MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
  BUILD="$(adb shell getprop ro.build.display.id | tr -d '\r')"
  echo "Phone: $MODEL   firmware: $BUILD"
  [[ "$MODEL" == *S22* ]] || ask "This doesn't look like a CAT S22 Flip. Continue anyway?" || exit 1
  ask "This ERASES the phone and installs KosherFlip Android 16. Continue?" || exit 1
  adb reboot bootloader
else
  echo "No phone over adb. If it is already in fastboot mode, continue."
  ask "This ERASES the phone and installs KosherFlip Android 16. Continue?" || exit 1
fi
fastboot wait-for-device 2>/dev/null || sleep 8

if ! fastboot getvar unlocked 2>&1 | grep -q "unlocked: *yes"; then
  say "Unlocking the bootloader - confirm on the phone with the volume keys, then power key"
  fastboot flashing unlock || fastboot oem unlock || true
  echo "If the phone restarted, put it back in fastboot mode (adb reboot bootloader) and run this script again."
  fastboot getvar unlocked 2>&1 | grep -q "unlocked: *yes" || exit 1
fi

say "Disabling verified boot"
fastboot --disable-verity --disable-verification flash vbmeta "$VBMETA"

say "Switching to fastbootd (needed for the system partition)"
fastboot reboot fastboot || true
fastboot wait-for-device 2>/dev/null || sleep 10

say "Making room: removing the product partition"
fastboot delete-logical-partition product_a 2>/dev/null || true
fastboot delete-logical-partition product 2>/dev/null || true
fastboot delete-logical-partition scratch 2>/dev/null || true

say "Flashing KosherFlip Android 16"
fastboot erase system || true
if ! fastboot flash system "$IMG"; then
  echo "Flashing from fastbootd failed; trying from the bootloader (some S22 firmwares need this)."
  fastboot reboot bootloader; fastboot wait-for-device 2>/dev/null || sleep 8
  fastboot flash system "$IMG"
fi

say "Wiping data (required for a new system)"
fastboot -w
fastboot reboot
cat <<'MSG'

Done. The first boot can take 5-10 minutes. KosherGuard sets itself up as the phone's
filter on the first screen. Then: choose the admin PIN, make a test call and send a test
text. If calls don't work on your carrier, go back to the stock firmware
(docs/S22-ANDROID16.md, "Going back").
MSG
