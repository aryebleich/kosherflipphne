#!/usr/bin/env bash
#
# Build the KosherFlip Android 16 GSI.
#
#   rom/build-rom.sh            # sync AOSP (first time) and build
#
# Environment:
#   AOSP_DIR      where the Android source lives      (default: $HOME/aosp)
#   AOSP_BRANCH   AOSP manifest branch or tag         (default: android-latest-release)
#   VARIANT       user | userdebug                    (default: user)
#   WITH_GMS      true to include Google apps         (default: false)
#   APKS_DIR      use already built APKs from here    (default: build them with Gradle)
#   JOBS          parallel jobs                       (default: nproc)
#
# Needs: Linux x86_64, ~400 GB free disk, 32 GB+ RAM (64 GB recommended),
#        repo, git, python3 and the AOSP build dependencies (see rom/Dockerfile).
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp}"
AOSP_BRANCH="${AOSP_BRANCH:-android-latest-release}"
VARIANT="${VARIANT:-user}"
WITH_GMS="${WITH_GMS:-false}"
JOBS="${JOBS:-$(nproc)}"
PRODUCT=kosher_gsi_arm64

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }

# 1. KosherFlip apps ---------------------------------------------------------
PREBUILT="$REPO_ROOT/rom/vendor/kosher/prebuilt/apps"
mkdir -p "$PREBUILT"
if [[ -n "${APKS_DIR:-}" ]]; then
  log "Using APKs from $APKS_DIR"
  for a in KosherGuard FlipLauncher KosherBrowser KosherAI KosherKeys; do cp "$APKS_DIR/$a.apk" "$PREBUILT/$a.apk"; done
else
  log "Building KosherFlip apps (Gradle)"
  (cd "$REPO_ROOT/apps" && ./gradlew --no-daemon assembleRelease)
  for a in KosherGuard FlipLauncher KosherBrowser KosherAI KosherKeys; do
    cp "$REPO_ROOT/apps/$a/build/outputs/apk/release/$a-release.apk" "$PREBUILT/$a.apk"
  done
fi

# 2. Android source ------------------------------------------------------------
mkdir -p "$AOSP_DIR"
cd "$AOSP_DIR"
if [[ ! -d .repo ]]; then
  log "Initialising AOSP ($AOSP_BRANCH) in $AOSP_DIR"
  repo init --partial-clone --no-use-superproject \
    -u https://android.googlesource.com/platform/manifest -b "$AOSP_BRANCH"
fi

mkdir -p .repo/local_manifests
if [[ "$WITH_GMS" == "true" ]]; then
  cp "$REPO_ROOT/rom/local_manifests/gapps.xml" .repo/local_manifests/kosherflip-gapps.xml
else
  rm -f .repo/local_manifests/kosherflip-gapps.xml
fi

log "Syncing source (this takes a long time the first time)"
repo sync -c -j"$JOBS" --no-tags --no-clone-bundle --optimized-fetch --prune

# Our vendor tree comes straight from this repository (copied, because Soong
# does not follow symlinked directories).
mkdir -p vendor/kosher
rsync -a --delete "$REPO_ROOT/rom/vendor/kosher/" vendor/kosher/

# 3. Build -------------------------------------------------------------------
# Android 16 lunch targets look like <product>-<release>-<variant>. Pick the
# newest release config shipped in this tree (e.g. bp2a), else trunk_staging.
RELEASE="${TARGET_RELEASE_OVERRIDE:-}"
if [[ -z "$RELEASE" ]]; then
  RELEASE="$(ls build/release/release_configs/ 2>/dev/null | sed -n 's/^\(b[a-z][0-9][a-z]\)\.textproto$/\1/p' | sort | tail -1)"
  RELEASE="${RELEASE:-trunk_staging}"
fi

log "Building $PRODUCT-$RELEASE-$VARIANT (WITH_GMS=$WITH_GMS)"
set +u
# shellcheck disable=SC1091
source build/envsetup.sh
lunch "$PRODUCT-$RELEASE-$VARIANT"
set -u
export WITH_GMS
m -j"$JOBS" systemimage vbmetaimage

# 4. Package -----------------------------------------------------------------
OUT="$AOSP_DIR/out/target/product/generic_arm64"
DIST="$REPO_ROOT/out"
mkdir -p "$DIST"
NAME="kosherflip-android16-arm64-$VARIANT$([[ $WITH_GMS == true ]] && echo -gms)-$(date +%Y%m%d)"
log "Packaging $DIST/$NAME"
cp "$OUT/system.img" "$DIST/$NAME-system.img"
cp "$OUT/vbmeta.img" "$DIST/$NAME-vbmeta.img"
xz -T0 -k -f "$DIST/$NAME-system.img"
(cd "$DIST" && sha256sum "$NAME"-* > "$NAME.sha256")
cp "$REPO_ROOT/scripts/flash-gsi.sh" "$DIST/"
log "Done. Flash with: scripts/flash-gsi.sh $DIST/$NAME-system.img $DIST/$NAME-vbmeta.img"
