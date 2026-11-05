# KosherFlip

An open-source, filtered **Android 16** system for flip phones, in the spirit of
kosher phones like the Pom, Fig and Wonder: calls, texts, approved apps
(e.g. Google Maps, Waze, Gmail), a clean AI assistant, and **no open internet**.

```
┌──────────────────────────────────────────────────────────────┐
│  FlipLauncher   keypad home screen · Hebrew date · T9 dial   │
│  KosherAI       "Ask AI" – modest, filtered Claude assistant │
│  KosherGuard    the filter (device owner):                   │
│                 · only approved apps are visible             │
│                 · DNS filter VPN, always on, can't be off    │
│                 · no installs, no factory reset, no bypass   │
│                 · admin PIN for the supervisor               │
├──────────────────────────────────────────────────────────────┤
│  Android 16 GSI (AOSP)  + optional Google apps               │
│  ── the phone's own kernel & vendor drivers (Project Treble) │
└──────────────────────────────────────────────────────────────┘
```

## Two ways to install

| | **A. Apps only** (easiest) | **B. Full ROM** |
|---|---|---|
| What | Install the 3 apps on a phone's existing Android | Replace the phone's system with the KosherFlip Android 16 image |
| Phone needs | Android 9+ flip phone, USB debugging | arm64 **Treble** phone with an **unlockable bootloader** |
| Android version | whatever the phone has | **Android 16** |
| Filter strength | strong (device owner) | strongest (built into the system, browser removed, self-provisions at first boot) |
| Effort | 5 minutes with a computer | build the image (big Linux machine) + flash |

### A. Apps only

1. Download `KosherGuard.apk`, `FlipLauncher.apk`, `KosherAI.apk` and
   `install-on-phone.sh` from the **Releases** page (or the latest
   *Build apps* workflow run → artifact `kosherflip-apks`).
2. Factory reset the phone (Android only accepts a filter "device owner" when no accounts exist).
3. Turn on USB debugging, plug it in, and run:
   ```sh
   ./install-on-phone.sh ./   # folder with the APKs
   ```
4. Choose the admin PIN on the phone. Done.

### B. Full ROM (Android 16 GSI)

```sh
# on a Linux machine with ~400 GB free disk and 32–64 GB RAM
docker build -t kosherflip-builder rom/
docker run --rm -it -v $PWD:/src -v $HOME/aosp:/aosp -e AOSP_DIR=/aosp \
    -e WITH_GMS=true kosherflip-builder /src/rom/build-rom.sh

# then, with the phone connected and its bootloader unlocked
scripts/flash-gsi.sh out/kosherflip-android16-arm64-user-gms-*-system.img.xz \
                     out/kosherflip-android16-arm64-user-gms-*-vbmeta.img
```

See [docs/BUILDING.md](docs/BUILDING.md) and [docs/DEVICES.md](docs/DEVICES.md).

## Repository layout

```
apps/                     Gradle project with the three apps (Java, no AndroidX – small and fast)
  KosherGuard/            filter: device-owner policy, DNS-filter VPN, PIN admin console
  FlipLauncher/           keypad home screen + app list
  KosherAI/               AI assistant (official Anthropic Java SDK)
rom/
  vendor/kosher/          AOSP vendor tree: product kosher_gsi_arm64, app imports, permissions, policy
  local_manifests/        optional Google apps (MindTheGapps, Android 16 "baklava")
  build-rom.sh            sync AOSP + build + package the GSI
  Dockerfile              reproducible build environment
scripts/
  install-on-phone.sh     path A: apps + device owner via adb
  flash-gsi.sh            path B: flash the GSI with fastboot
docs/                     architecture, filter/admin guide, building, devices
.github/workflows/        CI: build + test apps on every push; ROM build on a self-hosted runner
```

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) – how the pieces fit together
- [docs/FILTER.md](docs/FILTER.md) – what is blocked, the admin console, customising the policy
- [docs/BUILDING.md](docs/BUILDING.md) – building the apps and the ROM
- [docs/DEVICES.md](docs/DEVICES.md) – choosing a flip phone and porting

## Status and honest limits

- The apps build and are unit-tested in CI. They have **not yet been tested on
  real flip-phone hardware** – please test on a spare phone first.
- The ROM is a **GSI**: it boots only on arm64 phones with Project Treble and an
  unlockable bootloader. Many carrier flip phones are locked or 32-bit (Android
  Go); for those use path A, or a device-specific port (see DEVICES.md).
- Google apps are proprietary. `WITH_GMS=true` is fine for building your own
  phone; distributing images that include them needs a licence from Google.
- No filter is perfect. KosherGuard closes the common holes (browsers, app
  stores, VPN/DNS changes, factory reset, safe mode); keep the admin PIN private
  and turn on the USB-debugging lock once set up.
