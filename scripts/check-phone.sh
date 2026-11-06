#!/usr/bin/env bash
#
# Is this phone compatible with KosherFlip? Connect it with USB debugging on and run:
#   ./check-phone.sh
#
set -uo pipefail
command -v adb >/dev/null || { echo "adb not found - install Android platform-tools"; exit 1; }
adb wait-for-device
p() { adb shell getprop "$1" | tr -d '\r'; }

MODEL="$(p ro.product.manufacturer) $(p ro.product.model)"
SDK="$(p ro.build.version.sdk)"; REL="$(p ro.build.version.release)"
ABI="$(p ro.product.cpu.abilist)"; TREBLE="$(p ro.treble.enabled)"; GO="$(p ro.config.low_ram)"
VNDK="$(p ro.vndk.version)"; DYN="$(p ro.boot.dynamic_partitions)"
UNLOCK="$(p ro.oem_unlock_supported)"; FLASHLOCK="$(p ro.boot.flash.locked)"
KEYPAD="$(adb shell dumpsys input 2>/dev/null | tr -d '\r' | grep -ciE 'KeyboardType: (non-)?alphabetic|keypad|gpio-keys' || true)"
OWNER="$(adb shell dumpsys device_policy 2>/dev/null | tr -d '\r' | grep -iA2 'Device Owner' | grep -oE 'package=[^ ]+|admin=[^ ]+' | head -1)"
ACCTS="$(adb shell dumpsys account 2>/dev/null | grep -c 'Account {' || true)"
IMS="$(p persist.dbg.volte_avail_ovr)$(p persist.vendor.radio.enable_volte)"

echo "Phone:            $MODEL"
echo "Android:          $REL (API $SDK)$([[ $GO == true ]] && echo '  - Android Go')"
echo "CPU ABIs:         $ABI"
echo "Treble / VNDK:    ${TREBLE:-no} / ${VNDK:-?}   dynamic partitions: ${DYN:-no}"
echo "Bootloader:       unlock supported: ${UNLOCK:-unknown}, locked: ${FLASHLOCK:-unknown}"
echo "Device owner now: ${OWNER:-none}"
echo "Accounts:         ${ACCTS:-0}"
echo
echo "== Apps-only install (recommended: keeps the phone's own calling/VoLTE, works on every carrier) =="
ok=1
[[ "$SDK" -ge 26 ]] && echo "  OK   Android 8+" || { echo "  FAIL needs Android 8+"; ok=0; }
[[ -z "$OWNER" || "$OWNER" == *kosherflip* ]] && echo "  OK   no other filter is device owner" \
  || { echo "  FAIL another filter owns the phone ($OWNER) - factory reset; if it persists it is built into the firmware"; ok=0; }
[[ "${ACCTS:-0}" -eq 0 ]] && echo "  OK   no accounts" || echo "  TODO remove the ${ACCTS} account(s) or factory reset before installing"
[[ $ok == 1 ]] && echo "  => Compatible. Run install-on-phone.sh (or .bat on Windows), or use the setup QR code."

echo
echo "== Full Android 16 ROM (GSI) =="
rok=1
[[ "$ABI" == *arm64-v8a* ]] && echo "  OK   64-bit (arm64)" || { echo "  FAIL 32-bit only - the arm64 GSI will not boot"; rok=0; }
[[ "$TREBLE" == true ]] && echo "  OK   Project Treble" || { echo "  FAIL not Treble - needs a device-specific port"; rok=0; }
[[ "$UNLOCK" == 1 ]] && echo "  OK   bootloader unlock supported" || { echo "  ??   bootloader unlock not advertised - try 'fastboot flashing unlock'; many carrier/kosher phones refuse"; rok=0; }
[[ "$SDK" -ge 30 ]] || echo "  WARN vendor older than Android 11 - many things may break on a GSI"
echo "  NOTE on US carriers calls need VoLTE; a GSI may lose VoLTE on some phones. Test calls before relying on it."
[[ $rok == 1 ]] && echo "  => The ROM may work. Keep the stock firmware to go back." || echo "  => Use the apps-only install on this phone."
