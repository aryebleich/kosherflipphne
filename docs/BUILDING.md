# Building

## Apps

Requirements: JDK 17+ and the Android SDK (platform 36, build-tools 36.0.0).

```sh
cd apps
./gradlew testReleaseUnitTest assembleRelease
# → */build/outputs/apk/release/*-release.apk
```

Without a keystore the release APKs are signed with the debug key (fine for
personal phones). For real releases add these repository secrets and CI signs
with them: `KOSHERFLIP_KEYSTORE_B64` (base64 of a .jks), `KOSHERFLIP_KEYSTORE_PASSWORD`,
`KOSHERFLIP_KEY_ALIAS`, `KOSHERFLIP_KEY_PASSWORD`. Keep the same key forever –
Android only accepts updates signed with the original key.

CI (`.github/workflows/apps.yml`) runs on every push; pushing a tag `v1.0.0`
publishes the APKs as a GitHub release.

## ROM (Android 16 GSI)

Requirements: Linux x86_64, ~400 GB disk, 32 GB RAM minimum (64 GB recommended),
several hours for the first build. Easiest is the Docker image:

```sh
docker build -t kosherflip-builder rom/
docker run --rm -it -v $PWD:/src -v $HOME/aosp:/aosp -e AOSP_DIR=/aosp \
    [-e WITH_GMS=true] [-e VARIANT=userdebug] kosherflip-builder /src/rom/build-rom.sh
```

`build-rom.sh`:
1. builds the three APKs (or copies them from `APKS_DIR`) into `rom/vendor/kosher/prebuilt/apps/`
2. `repo init` AOSP (`AOSP_BRANCH`, default `android-latest-release`) and `repo sync`
3. adds MindTheGapps when `WITH_GMS=true`
4. copies `rom/vendor/kosher` to `vendor/kosher`
5. `lunch kosher_gsi_arm64-<release>-<variant>` (the newest release config in the tree, e.g. `bp2a`;
   override with `TARGET_RELEASE_OVERRIDE`) and `m systemimage vbmetaimage`
6. writes `out/kosherflip-android16-arm64-…-system.img(.xz)`, `…-vbmeta.img` and checksums

`user` builds are what you put on a kosher phone; `userdebug` is for development.

To build in GitHub Actions, add a self-hosted runner labelled `aosp` and run the
*Build ROM* workflow manually.

## Flashing

```sh
scripts/flash-gsi.sh <system.img.xz> <vbmeta.img>
```
Unlocks are device-specific; the script checks that the bootloader is unlocked,
disables verified boot with the vbmeta image, flashes `system` from fastbootd and
wipes data.
