# KosherFlip

An open-source, filtered **Android 16** system for flip phones, in the spirit of
kosher phones like the Pom, Fig, Wonder and Mind: calls, texts, approved apps
(Google Maps, Waze, Gmail…), a **filtered browser for shopping, browsing and
downloads**, a filter that checks **everything on the screen**, and a free AI assistant.

```
┌──────────────────────────────────────────────────────────────┐
│  FlipLauncher   keypad home · Hebrew date · zmanim · T9 dial │
│  KosherBrowser  filtered web: shop, browse, download         │
│  KosherAI       "Ask AI" – free Gemini, modest & filtered    │
│  KosherGuard    the filter (device owner):                   │
│   · profiles: Kosher / Heimish / Open + protected            │
│   · website categories + family-safe DNS, cached = fast      │
│   · screen filter: reads every app's screen, covers bad text │
│   · approved apps only · request access · insights           │
│   · talk-only · Shabbos mode · camera/hotspot/USB locks      │
│   · no installs, no factory reset, no bypass · admin PIN     │
├──────────────────────────────────────────────────────────────┤
│  Android 16 GSI (AOSP)  + optional Google apps               │
│  ── the phone's own kernel & vendor drivers (Project Treble) │
└──────────────────────────────────────────────────────────────┘
```

## Features

**Filtering ("heimish filter" style, but usable)**
- **Profiles**: *Filtered* (default: **all apps and browsers allowed, the content in them filtered**),
  *Kosher* (calls, texts, approved apps, no browser), *Heimish* (filtered browsing,
  shopping & downloads; pictures only on trusted sites), *Open + protected* (most sites, smart filtering).
- **Website categories**: social media, video, streaming, news & gossip, games, dating, gambling,
  unfiltered AI chat, unfiltered search, proxies/VPNs, web chat – each on/off. Trusted categories
  (shopping & payments, Torah & Jewish sites, banks/travel/government) always work and may show pictures.
- **Family-safe DNS** (CleanBrowsing Family: adult, proxies, SafeSearch) on top, through an always-on,
  DNS-only local VPN with an answer cache – normal data never goes through it, so no slowdown.
- **Screen filter for every app**: reads the text on screen (and notifications) on the phone itself
  and covers anything inappropriate, then goes back. Admin can add words in any language.
- **Browser**: every page's text checked; pictures *none / trusted sites / blurred / all*; video blocked
  (unless allowed); Google SafeSearch forced; image & video search blocked; ads & trackers blocked
  (faster pages); downloads limited to safe types (PDF, Office, MP3, photos…), apps only when allowed.
- **Smart & personal**: *Insights* (most used & most blocked sites), *Suggestions* (blocked sites the
  phone keeps trying), *Requests* (the user asks for a site or an app; the admin approves with one key),
  browser home page shows **your own most-visited sites**.

**Kosher-phone features** (see the comparison in [docs/FEATURES.md](docs/FEATURES.md))
- Talk-only mode: messaging hidden, polite auto-reply, **verification codes still shown**.
- Shabbos & Yom Tov mode (auto from candle lighting to nightfall, by location) – phone & contacts only.
- Video blocked by default; camera on/off; hotspot block; no sideloading (USB, Bluetooth, SD card).
- Only approved apps; no app store unless the admin opens installs; factory reset & safe mode blocked.
- Keypad-first: home with Hebrew date and zmanim, T9 dialing, numbered app list, keypad pointer in the browser.
- **KosherKeys T9 keyboard**: English, Hebrew and Yiddish (װ ױ ײ אַ אָ פּ פֿ) multi-tap typing.
- **Install without a computer or QR code**: download KosherGuard on the phone (or receive it by
  Bluetooth / Nearby Share), follow the checklist; KosherGuard installs and updates the other apps.
- **Free AI** ("Ask AI"): Google Gemini free key, strictest Google safety settings + our modest prompt +
  our word filter on questions and answers. Other free OpenAI-compatible services or Claude also supported.

## Install

👉 **[Installation guide](docs/INSTALL.md)**. Easiest: **on the phone alone** – open
*aryebleich.github.io/kosherflipphne* in the phone's browser (or receive it from another KosherFlip phone
by Bluetooth), install KosherGuard and follow its checklist. Also: QR code, Windows/Mac/Linux installer,
phone notes (CAT S22 Flip, Pom) and carrier notes.

**Android 16 on the CAT S22 Flip**: ready-made image + flashing script, see
[docs/S22-ANDROID16.md](docs/S22-ANDROID16.md).

## Two ways to install

| | **A. Apps only** (easiest) | **B. Full ROM** |
|---|---|---|
| What | Install the 3 apps on a phone's existing Android | Replace the phone's system with the KosherFlip Android 16 image |
| Phone needs | Android 8+ phone; QR code or USB debugging | arm64 **Treble** phone with an **unlockable bootloader** |
| Android version | whatever the phone has | **Android 16** |
| Filter strength | strong (device owner) | strongest (built into the system, browser removed, self-provisions at first boot) |
| Effort | 5 minutes with a computer | build the image (big Linux machine) + flash |

### A. Apps only

1. Download `KosherGuard.apk`, `FlipLauncher.apk`, `KosherBrowser.apk`, `KosherAI.apk` and
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
apps/                     Gradle project (Java, no AndroidX – small and fast; Android 8+)
  common/                 shared word filter + zmanim (pure Java, unit tested)
  KosherGuard/            filter: device owner, DNS VPN, screen filter, profiles, requests, admin console
  FlipLauncher/           keypad home screen + app list
  KosherBrowser/          filtered browser with keypad pointer
  KosherAI/               AI assistant (Gemini free / OpenAI-compatible / Claude)
  KosherKeys/             T9 keyboard: English, Hebrew, Yiddish
rom/
  vendor/kosher/          AOSP vendor tree: product kosher_gsi_arm64, app imports, permissions, policy
  local_manifests/        optional Google apps (MindTheGapps, Android 16 "baklava")
  build-rom.sh            sync AOSP + build + package the GSI
  Dockerfile              reproducible build environment
scripts/
  install-on-phone.sh/.bat  apps + device owner via adb (Mac/Linux / Windows)
  check-phone.sh          compatibility report for a connected phone
  make-provisioning-qr.py setup QR code (no-computer install)
  flash-gsi.sh            path B: flash the GSI with fastboot
docs/                     architecture, filter/admin guide, building, devices
.github/workflows/        CI: build + test apps on every push; ROM build on a self-hosted runner
```

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) – how the pieces fit together
- [docs/FEATURES.md](docs/FEATURES.md) – feature list and comparison with Pom, Fig, Mind, Wonder
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
- No filter is perfect. The screen filter reads **text**; pictures are controlled
  in KosherBrowser (none / trusted / blur) and by only allowing trusted apps –
  there is no on-device image recognition yet. Keep the admin PIN private and
  turn on the USB-debugging lock once set up.
- Zmanim are calculated on the phone (±1–2 minutes); follow your community's luach.
- Kosher certification (e.g. by a Vaad) is a rabbinic decision about a specific setup – this
  project gives you the tools, not a hechsher.
