#!/usr/bin/env bash
#
# Flash the KosherFlip GSI on an arm64 Project Treble flip phone.
#
#   scripts/flash-gsi.sh <system.img[.xz]> [vbmeta.img]
#
# WARNING: erases all data on the phone. Requires an UNLOCKED bootloader
# (fastboot flashing unlock - some carriers/makers do not allow this).
#
set -euo pipefail
SYSTEM="${1:?usage: flash-gsi.sh system.img[.xz] [vbmeta.img]}"
VBMETA="${2:-}"

command -v fastboot >/dev/null || { echo "fastboot not found - install Android platform-tools"; exit 1; }

if [[ "$SYSTEM" == *.xz ]]; then
  echo "Decompressing $SYSTEM ..."
  xz -dkf "$SYSTEM"
  SYSTEM="${SYSTEM%.xz}"
fi

read -r -p "This ERASES the phone and installs KosherFlip. Type YES to continue: " ok
[[ "$ok" == "YES" ]] || exit 1

if adb get-state >/dev/null 2>&1; then adb reboot bootloader; fi
fastboot wait-for-device 2>/dev/null || sleep 5

if [[ "$(fastboot getvar unlocked 2>&1 | grep -o 'unlocked: *yes' || true)" == "" ]]; then
  echo "The bootloader looks LOCKED. Unlock it first (fastboot flashing unlock), then re-run."
  exit 1
fi

# Disable verified boot so the phone accepts a new system image.
if [[ -n "$VBMETA" ]]; then
  fastboot --disable-verity --disable-verification flash vbmeta "$VBMETA"
fi

# Dynamic-partition phones (Android 10+) flash system from fastbootd.
if fastboot getvar is-userspace 2>&1 | grep -q "is-userspace: no"; then
  fastboot reboot fastboot
fi
fastboot erase system || true
# If flashing fails with "Not enough space", free room inside the super
# partition first:  fastboot delete-logical-partition product_a  (or product)
fastboot flash system "$SYSTEM"
fastboot -w
fastboot reboot
echo "Flashed. First boot can take a few minutes; KosherGuard sets itself up automatically."
