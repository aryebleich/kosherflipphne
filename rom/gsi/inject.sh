#!/usr/bin/env bash
#
# Builds KosherFlip into an existing Android 16 GSI (system.img), without
# compiling Android. Used by .github/workflows/gsi.yml; also runs on any Linux PC.
#
#   sudo rom/gsi/inject.sh <base-system.img> <apk-dir> <out.img> [platform-key-dir]
#
# <apk-dir> holds KosherGuard.apk FlipLauncher.apk KosherBrowser.apk KosherAI.apk KosherKeys.apk.
# [platform-key-dir] holds platform.pk8 + platform.x509.pem: when the GSI is built
# with Android's public test keys, KosherGuard is re-signed with them and becomes a
# system app that can make itself device owner at first boot (full protection).
#
# Handles raw or sparse images, ext4 or EROFS.
#
# DEVICE=cat-s22 adds the CAT S22 Flip calling settings from rom/gsi/cat-s22/
# (VoLTE switches, IMS feature, radio HAL matrix). The Qualcomm IMS app itself is
# not bundled: install it on the phone from Treble settings (docs/S22-ANDROID16.md).
set -euo pipefail
BASE="$1"; APKS="$2"; OUT="$3"; KEYS="${4:-}"
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'sudo umount "$WORK/mnt" 2>/dev/null || true; sudo rm -rf "$WORK"' EXIT
log() { printf '==> %s\n' "$*"; }

IMG="$WORK/system.img"
if file "$BASE" | grep -qi "sparse"; then
  log "Converting sparse image"; simg2img "$BASE" "$IMG"
else
  cp --reflink=auto "$BASE" "$IMG"
fi
FS="$(blkid -o value -s TYPE "$IMG" || true)"
log "Filesystem: ${FS:-unknown}"
mkdir -p "$WORK/mnt" "$WORK/tree"

case "$FS" in
  ext4)
    e2fsck -fy "$IMG" >/dev/null 2>&1 || true
    if debugfs -R features "$IMG" 2>/dev/null | grep -q shared_blocks; then
      # AOSP builds de-duplicate blocks (read-only); undo that so it can be edited.
      log "Un-sharing de-duplicated blocks"
      truncate -s +2G "$IMG"; resize2fs "$IMG" >/dev/null 2>&1
      e2fsck -fy -E unshare_blocks "$IMG" >/dev/null 2>&1 || true
    fi
    # Room for our apps (~15 MB) plus slack.
    truncate -s +128M "$IMG"
    resize2fs "$IMG" >/dev/null 2>&1
    sudo mount -o loop,rw "$IMG" "$WORK/mnt"
    ROOT="$WORK/mnt"
    ;;
  erofs)
    sudo mount -t erofs -o loop,ro "$IMG" "$WORK/mnt"
    log "Copying EROFS contents (with SELinux labels)"
    sudo cp -a "$WORK/mnt/." "$WORK/tree/"
    sudo umount "$WORK/mnt"
    ROOT="$WORK/tree"
    ;;
  *) echo "Unsupported filesystem: $FS"; exit 1 ;;
esac

# System-as-root GSIs keep Android's /system inside the image at /system.
SYS="$ROOT/system"; [[ -d "$SYS/app" ]] || SYS="$ROOT"
[[ -f "$SYS/build.prop" ]] || { echo "No system/build.prop - not an Android system image"; exit 1; }
TAGS="$(grep -m1 '^ro.system.build.tags=\|^ro.build.tags=' "$SYS/build.prop" | cut -d= -f2 || true)"
log "Base: $(grep -m1 '^ro.system.build.version.release=\|^ro.build.version.release=' "$SYS/build.prop" | cut -d= -f2) ($TAGS)"

LABEL="$(getfattr -n security.selinux --only-values "$SYS/app" 2>/dev/null || true)"
LABEL="${LABEL:-u:object_r:system_file:s0}"
put() { # put <src> <dest-relative-to-SYS> <mode>
  local dst="$SYS/$2"
  sudo mkdir -p "$(dirname "$dst")"
  sudo install -o root -g root -m "$3" "$1" "$dst"
  sudo setfattr -n security.selinux -v "$LABEL" "$dst" "$(dirname "$dst")" 2>/dev/null || true
}

GUARD="$APKS/KosherGuard.apk"
if [[ -n "$KEYS" && "$TAGS" == *test-keys* && -f "$KEYS/platform.pk8" ]]; then
  log "GSI uses public test keys: platform-signing KosherGuard (full protection)"
  apksigner sign --min-sdk-version 26 --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" \
    --out "$WORK/KosherGuard-platform.apk" "$GUARD"
  put "$WORK/KosherGuard-platform.apk" priv-app/KosherGuard/KosherGuard.apk 0644
else
  log "GSI not test-keys signed: KosherGuard installed as a normal privileged app (standard protection)"
  put "$GUARD" priv-app/KosherGuard/KosherGuard.apk 0644
fi
for a in FlipLauncher KosherBrowser KosherAI KosherKeys; do
  put "$APKS/$a.apk" "app/$a/$a.apk" 0644
done
put "$REPO/rom/vendor/kosher/prebuilt/privapp-permissions-kosherflip.xml" etc/permissions/privapp-permissions-kosherflip.xml 0644
put "$REPO/rom/vendor/kosher/prebuilt/policy.json" etc/kosherflip/policy.json 0644

# ---- Carrier settings (all carriers) ---------------------------------------------
# APNS_FILE: a full APN database (LineageOS apns-conf.xml): internet, MMS and the
# VoLTE "ims" APN set themselves up on carriers worldwide.
if [[ -n "${APNS_FILE:-}" && -f "$APNS_FILE" ]]; then
  log "Installing the carrier APN database ($(grep -c '<apn ' "$APNS_FILE") APN entries)"
  put "$APNS_FILE" etc/apns-conf.xml 0644
fi

# ---- Device settings for calls --------------------------------------------------
if [[ "${DEVICE:-}" == cat-s22 ]]; then
  D="$REPO/rom/gsi/cat-s22"
  log "CAT S22 Flip: VoLTE settings, IMS feature and radio HAL matrix"
  put "$D/android.hardware.telephony.ims.xml" etc/permissions/android.hardware.telephony.ims.xml 0644
  # Android reads the device-specific framework matrix from this exact name.
  M="$SYS/etc/vintf/compatibility_matrix.device.xml"
  if [[ -f "$M" ]]; then
    log "Merging radio HALs into the existing compatibility_matrix.device.xml"
    HALS="$(sed -n '/<hal /,/<\/hal>/p' "$D/compatibility_matrix.device.xml")"
    python3 - "$M" "$HALS" > "$WORK/matrix.xml" <<'PY'
import sys
p, hals = sys.argv[1], sys.argv[2]
s = open(p).read()
if "vendor.qti.hardware.radio.ims" not in s:
    s = s.replace("</compatibility-matrix>", hals + "\n</compatibility-matrix>")
sys.stdout.write(s)
PY
    sudo tee "$M" < "$WORK/matrix.xml" >/dev/null   # keeps the file's SELinux label
  else
    put "$D/compatibility_matrix.device.xml" etc/vintf/compatibility_matrix.device.xml 0644
  fi
  sudo tee -a "$SYS/build.prop" < "$D/volte.prop" >/dev/null
fi

# KosherGuard's first-boot setup replaces AOSP "Provision".
for d in $(sudo find "$SYS" -maxdepth 3 -type d -name Provision); do log "Removing $d"; sudo rm -rf "$d"; done

if ! grep -q '^ro.kosherflip.rom=' "$SYS/build.prop"; then
  printf '\n# KosherFlip\nro.kosherflip.rom=1\nro.kosherflip.version=%s\n' "${KOSHERFLIP_VERSION:-dev}" | sudo tee -a "$SYS/build.prop" >/dev/null
fi

case "$FS" in
  ext4)
    sync; sudo umount "$WORK/mnt"
    e2fsck -fy "$IMG" >/dev/null 2>&1 || true
    resize2fs -M "$IMG" >/dev/null 2>&1 || true   # shrink back to fit small phones (e.g. CAT S22 Flip)
    # Shrink the file to the filesystem size.
    BLOCKS="$(dumpe2fs -h "$IMG" 2>/dev/null | awk -F: '/^Block count/{gsub(/ /,"",$2);print $2}')"
    BSIZE="$(dumpe2fs -h "$IMG" 2>/dev/null | awk -F: '/^Block size/{gsub(/ /,"",$2);print $2}')"
    [[ -n "$BLOCKS" && -n "$BSIZE" ]] && truncate -s $((BLOCKS * BSIZE)) "$IMG"
    cp "$IMG" "$OUT"
    ;;
  erofs)
    log "Rebuilding EROFS image"
    sudo mkfs.erofs -zlz4hc "$OUT.tmp" "$ROOT" >/dev/null
    sudo mv "$OUT.tmp" "$OUT"; sudo chown "$(id -u):$(id -g)" "$OUT"
    ;;
esac
log "Wrote $OUT ($(du -h "$OUT" | cut -f1))"
