# KosherFlip installation guide

This guide turns an Android flip phone (or any Android phone) into a filtered
KosherFlip phone. Pick **one** method:

| Method | You need | Best for |
|---|---|---|
| **A. QR code** – no computer | Wi-Fi, a second screen to show the QR code | Most phones with Android 10+ and Google services (e.g. CAT S22 Flip) |
| **B. Computer + USB cable** | Windows, Mac or Linux computer | Every Android 8+ phone; use it if Method A doesn't start |
| **C. Full Android 16 system (ROM)** | Unlockable 64-bit phone, Linux build machine | Advanced users only |

Methods A and B keep the phone's own system (firmware), so **calls, texts,
VoLTE, Wi-Fi calling and emergency calls keep working on every carrier** exactly
as before. KosherFlip only adds the filter and the apps. This is the recommended way.

> ⚠️ Methods A and B start with a **factory reset**, which erases the phone.
> Save contacts and photos first (copy contacts to the SIM card or to a Google account).

---

## Before you start

- Phone charged above 50 %, SIM card inside (or Wi-Fi available).
- The phone runs **Android 8 or newer**: *Settings → About phone → Android version*.
- Your Google account password, if you want Gmail/Maps/Play on the phone (optional).
- About 15 minutes.

Not sure whether your phone is compatible? Do Method B steps B1–B3, then run
`check-phone.sh` (Mac/Linux). It prints a full report for both the apps-only
install and the ROM.

---

## Method A – QR code (no computer)

1. **Factory reset**: *Settings → System → Reset options → Erase all data (factory reset)*.
2. On the very first **Welcome / Hi there** screen, **tap the same spot on the screen 6 times**.
   A QR scanner opens. (If nothing happens, your phone doesn't support QR setup: use Method B.)
3. Connect to Wi-Fi when it asks.
4. On another phone or a computer, open `kosherflip-setup-qr.png` from the latest
   **Release** on GitHub (github.com/aryebleich/kosherflipphne/releases/latest) and scan it.
5. The phone downloads **KosherGuard**, makes it the phone's manager, and KosherGuard
   downloads and installs the other KosherFlip apps (launcher, browser, AI, T9 keyboard).
   Finish the setup screens.
6. Continue with **First setup in KosherGuard** below.

The QR code points at `https://github.com/aryebleich/kosherflipphne/releases/latest/download/KosherGuard.apk`,
so a release must exist (see *Publishing a release* at the end).

> The screen filter and the automatic T9 keyboard need one extra permission that
> only a computer (or the ROM) can give. After a QR install, turn the screen filter
> on by hand: *Settings → Accessibility → KosherGuard screen filter → On*, and pick the
> keyboard in *Settings → System → Keyboard*. Or do step B5 once with a computer.

---

## Method B – computer and USB cable

### B1. Install adb (Android platform-tools) on the computer

- **Windows**: download *SDK Platform-Tools for Windows* from developer.android.com/tools/releases/platform-tools
  and unzip it (for example to `C:\platform-tools`).
- **Mac**: `brew install android-platform-tools` (or download *Platform-Tools for Mac* from the same page).
- **Linux**: `sudo apt install adb` (Ubuntu/Debian) or the download above.

### B2. Download KosherFlip

From the repository's **Releases** page download: `KosherGuard.apk`, `FlipLauncher.apk`,
`KosherBrowser.apk`, `KosherAI.apk`, `KosherKeys.apk`, and `install-on-phone.bat` (Windows)
or `install-on-phone.sh` (Mac/Linux).
Windows: put all of them **inside the platform-tools folder** (next to `adb.exe`).
(Mac/Linux: `install-on-phone.sh` can also download the APKs by itself.)

### B3. Prepare the phone

1. **Factory reset** it (or remove every account in *Settings → Accounts*; Android does not
   allow a phone manager while accounts exist).
2. Go through setup **without adding a Google account** (skip it; you can add it later).
3. *Settings → About phone* → tap **Build number** 7 times ("You are now a developer").
4. *Settings → System → Developer options* → turn on **USB debugging**.
5. Connect the USB cable. On the phone, tap **Allow** on *Allow USB debugging?* (tick *Always allow*).

### B4. Run the installer

- **Windows**: double-click `install-on-phone.bat`.
- **Mac/Linux**: open Terminal in the download folder and run
  `chmod +x install-on-phone.sh && ./install-on-phone.sh .`

It installs the five apps, makes KosherGuard the device owner, turns on the screen
filter and the T9 keyboard, and opens KosherGuard on the phone.

### B5. (Only after a QR install) give the screen-filter permission

```sh
adb shell pm grant com.kosherflip.guard android.permission.WRITE_SECURE_SETTINGS
```
Then open KosherGuard → *Apply now*.

---

## First setup in KosherGuard (all methods)

1. **Choose an admin PIN** (4+ digits). Give it only to the person supervising the phone.
2. **Profile**: *Kosher* (calls, texts and approved apps, no browser) · *Heimish* (filtered
   browsing, shopping and downloads) · *Open + protected*.
3. **AI assistant** (optional, free): on a computer go to **aistudio.google.com → Get API key →
   Create API key**, then in KosherGuard → *AI assistant* paste the key → *Save*.
4. **Google account** (optional): add it now in *Settings → Accounts* for Gmail, Maps and Play services.
5. **Shabbos mode** (optional): *Shabbos mode* → *Use the phone's location* → *Save* → mode *auto*.
6. **Talk-only** (optional): for a phone without texting.
7. **Last step: USB debugging lock → ON.** From now on nobody can remove the filter with a computer.
   (Turn it off in KosherGuard before you update with a computer.)

The phone is ready. Press **OK** on the home screen for the app list, the number keys to dial,
→ for the AI assistant.

---

## Phone-specific notes

### CAT S22 Flip
- Android 11 (Go edition), Snapdragon 215, 2 GB RAM, 2.8" touch screen + keypad.
  Sold unlocked and by US carriers (T-Mobile, Verizon and others).
- **Use Method B** (works on every S22). Method A may also work (Android 11 Go with Google
  setup); if 6 taps do nothing, use B.
- Developer options: *Settings → About phone → Build number* ×7; USB debugging in
  *Settings → System → Advanced → Developer options*.
- KosherKeys becomes the default keyboard because the S22 has a number keypad
  (#: abc/Abc/ABC/עברית/ייִדיש/123, 0: space, hold a key for its digit).
- The ROM (Method C) is **not recommended** on the S22: it is an Android Go phone, the
  maker (Bullitt) has closed, and there is no confirmed bootloader unlock. Keep its own firmware.
- 2 GB RAM is enough: KosherFlip apps are small and the filter uses almost no battery.

### Pom phones (Pom Kosher, Pom Classic, "P63-X1" and other Pom models)
- Pom phones come **pre-filtered and certified** (e.g. by the Vaad Hakehilos); their filter is part
  of the phone's firmware. I found no public specs for a model called "P63-X1", so check it with
  `check-phone.sh`.
- KosherFlip can only be installed if the phone lets you turn on **USB debugging** and **no other
  filter is the device owner** after a factory reset. If the Pom's own filter stays after a reset,
  it is built in and only Pom/its seller can unlock the phone. That is by design.
- Replacing or adding software on a certified phone **ends that certification**. Ask your rav
  or the certifying organisation before changing a certified phone.
- If the phone allows it, use **Method B** (Pom models run Android 11; the ROM is not recommended).
  KosherKeys gives the same Yiddish T9 typing the Pom is known for.

### Other flip phones (TCL, Kyocera, Sonim, Nokia, Orbic, Alcatel, Qin/Duoqin, etc.)
- Any **Android 8+** model works with Method B. Older KaiOS/feature-phone systems
  (non-Android) cannot run Android apps.
- Phones without a touch screen: everything in KosherFlip works with the keypad; the browser has a
  pointer (key **5**). Third-party apps such as Maps may need touch.

---

## Carriers and service providers

KosherFlip does **not** change the radio, modem, APN or carrier settings, so with
Methods A and B the phone works with any carrier it worked with before:
Verizon, AT&T, T-Mobile, US Cellular, Visible, Mint, Cricket, Metro, Boost, Tracfone/Straight
Talk, US Mobile, Google Fi, Consumer Cellular, kosher providers and MVNOs; in Israel
Pelephone, Cellcom, Partner, HOT Mobile, Golan and kosher lines; in the UK EE, O2, Vodafone, Three.

Things that depend on the phone, not on KosherFlip:
- **The phone must be compatible with the carrier's network** (bands, and in the US VoLTE
  certification / IMEI approval). Check with the carrier using the phone's IMEI (`*#06#`).
- **No-data / kosher talk-and-text plans**: the filter, calls, texts, talk-only and Shabbos mode work
  without data. The browser, AI, Maps and app updates need data or Wi-Fi.
- **Talk-only mode** sends a free auto-reply SMS; on plans without texting the reply is simply not sent.
- **Hotspot** is blocked by KosherGuard by default (so a second device can't bypass the filter).
- Method C (ROM) can lose **VoLTE / Wi-Fi calling** on some phones, which means no calls on
  4G-only US networks. Test calls before relying on a ROM phone.

---

## Updating

- Apps: KosherGuard → **Install / update KosherFlip apps** (downloads the newest release; no computer).
- Or run the installer again with the new APKs (turn off the USB-debugging lock first).

## Removing KosherFlip

KosherGuard → **Remove KosherGuard** (PIN required) lifts every restriction; then uninstall the
apps in Settings, or factory reset.

## Troubleshooting

| Problem | Fix |
|---|---|
| `Not allowed to set the device owner because there are already some accounts` | Remove all accounts or factory reset, then run again |
| `already provisioned` / `device owner is already set` | Factory reset. If another filter returns after a reset, it is built into the phone |
| `adb: no devices` / `unauthorized` | Use a data cable, accept *Allow USB debugging* on the phone, try another USB port; Windows may need the maker's USB driver |
| 6 taps on the Welcome screen do nothing | The phone doesn't support QR setup – use Method B |
| QR setup says it can't download | Phone needs internet; a release must exist on GitHub (see below) |
| Screen filter off after a QR install | Settings → Accessibility → KosherGuard screen filter, or step B5 |
| A needed website is blocked | On the phone: browser → *Request access*. Admin: KosherGuard → *Requests* → Approve |
| An app is missing | App list → *Request an app…*; admin approves in *Requests*, or ticks it in *Allowed apps* |
| AI says the key is invalid | Re-create the free key at aistudio.google.com and paste it again |
| Forgot the admin PIN | Factory reset the phone and install again (by design there is no back door) |

## Method C – full Android 16 system (ROM)

For phones that pass the ROM section of `check-phone.sh` (64-bit, Treble, unlockable bootloader):
see [BUILDING.md](BUILDING.md) to build the image and `scripts/flash-gsi.sh` to flash it. Keep the
phone's original firmware so you can go back. The ROM includes all KosherFlip apps and sets itself up
on first boot.

## Publishing a release (for the project owner)

Phones (QR code and *Install / update apps*) download from the repository's **latest release**.
**First, once:** create the release signing key and add it as GitHub secrets
(`scripts/create-signing-key.sh`, see `apps/signing/README.md`). Then publish: GitHub →
**Actions → Build apps → Run workflow** (keep *Publish* ticked), or merge into `main`, or push a
tag like `v1.1.0`. The release contains the APKs, the installers, `versions.json` and the
setup QR code made for your key.
