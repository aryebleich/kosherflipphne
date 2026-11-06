# Android 16 on the CAT S22 Flip

The CAT S22 Flip shipped with Android 11 (Go) and will never get an official update
(its maker, Bullitt, closed in 2024). KosherFlip publishes an **Android 16 image with
KosherFlip built in** that you can put on the phone yourself.

> ⚠️ **Read first**
> - This **erases the phone** and unlocks its bootloader. Warranty, if any, is gone.
> - It is based on a community Android 16 image (TrebleDroid GSI). Some things may not work on
>   the S22: **test calls, texts, mobile data, the outer screen, camera and the keypad** before
>   you rely on it. Calls on US carriers need VoLTE, which a GSI may not support on this phone.
> - If anything is wrong, go back to the stock firmware (see *Going back*).
> - If you only want the filter, you don't need this: the [phone-only install](INSTALL.md)
>   works on the S22's own Android 11 and keeps calling fully working.

## What you need

- The CAT S22 Flip on stock firmware **V29 or V30** (Settings → About phone → Build number).
  The S22 community reports the bootloader unlock works on these versions.
- A computer (Windows, Mac or Linux) with **platform-tools** (adb + fastboot), see INSTALL.md step B1.
  Flashing a whole system can't be done from the phone itself.
- A USB cable, the phone charged above 60 %.
- The **stock firmware** for going back (see the XDA "revert to stock" thread below).

## 1. Download

From the repository's **Releases**, open the newest release called
**"KosherFlip Android 16 image … (CAT S22 Flip / arm64 GSI)"** and download:
- `kosherflip-android16-cat-s22-….img.xz` (a 32-bit "arm32_binder64" image: the S22 runs 32-bit
  Android on a 64-bit kernel, so normal arm64 images don't boot on it)
- `vbmeta-disabled.img`
- `flash-s22.sh` (Mac/Linux). On Windows run the same commands by hand (step 3).

## 2. Prepare the phone

1. **Back up your authenticator apps** (Google Authenticator, Microsoft Authenticator, Authy, 2FAS…)
   and check the codes on the backup: [BACKUP-AUTHENTICATORS.md](BACKUP-AUTHENTICATORS.md).
   Flashing erases them. Also back up contacts and photos.
2. Settings → About phone → tap **Build number** 7 times.
3. Settings → System → Advanced → Developer options → turn on **OEM unlocking** (if shown) and **USB debugging**.
4. Connect the USB cable and accept *Allow USB debugging*.

## 3. Flash

**Mac / Linux**: in the download folder run
```sh
chmod +x flash-s22.sh
./flash-s22.sh kosherflip-android16-arm64-*.img.xz vbmeta-disabled.img
```

**Windows** (in the platform-tools folder, files copied there; first unpack the `.img.xz` with 7-Zip):
```bat
adb reboot bootloader
fastboot flashing unlock
:: (if that fails: fastboot oem unlock) - confirm on the phone with the volume keys + power key
fastboot --disable-verity --disable-verification flash vbmeta vbmeta-disabled.img
fastboot reboot fastboot
fastboot delete-logical-partition product_a
fastboot delete-logical-partition product
fastboot erase system
fastboot flash system kosherflip-android16-cat-s22-vanilla-XXXXXXXX.img
fastboot -w
fastboot reboot
```
If `fastboot flash system` fails in fastbootd, run `fastboot reboot bootloader` and flash it from there
(S22 owners report both ways, depending on firmware).

## 4. First boot

The first boot takes 5–10 minutes. KosherGuard is built into the system: on the first screen it makes
itself the phone's filter (**full protection**, nothing to install) and opens setup. Choose the admin PIN
and profile as in [INSTALL.md → First setup](INSTALL.md#first-setup-in-kosherguard-all-methods).

Then **test**: a call, a text, mobile data, Wi-Fi, the keypad, the camera.

## 5. Make calls work (VoLTE)

US carriers (and more and more others) only carry calls over **VoLTE**. The image already contains
the VoLTE switches, the IMS feature and the S22's radio connections, and the red end-call key hangs up.
Android still needs Qualcomm's **IMS app**, which TrebleDroid installs for you:

1. Turn on **mobile data** (or Wi-Fi) once.
2. **Settings → Treble settings (Phh Treble Settings) → IMS features → Install IMS APK for Qualcomm vendor.**
   Then turn on **Request IMS network** and **Force the presence of 4G calling**.
3. Settings → Network & internet → SIM → turn on **VoLTE / 4G calling**; preferred network type **LTE**.
4. If calls still don't connect, add the carrier's IMS APN: Settings → Network → SIM → Access Point Names →
   ➕ → Name `IMS`, APN `ims`, APN type `ims` → save (keep the normal internet APN too).
5. If you can call out but **incoming calls fail**: Treble settings → Misc features →
   **Allow binder threads on incoming calls**.
6. Reboot. Test an **outgoing and an incoming call** and a text.

### Carriers

The image works with **any carrier**, not only one. It contains the full LineageOS carrier database
(about 3,900 APN entries worldwide), so internet, MMS and the VoLTE "ims" connection set themselves up
from the SIM card. The VoLTE / video calling / Wi-Fi calling switches apply to every carrier.

| Carrier (and the brands that use its network) | What to know |
|---|---|
| **Verizon** (Visible, Total by Verizon, Straight Talk/Tracfone on Verizon, US Mobile Warp) | Calls need VoLTE. Verizon only allows phones on its approved list: an S22 bought for Verizon or unlocked is listed; check the IMEI (`*#06#`) on verizon.com "Bring your own device". |
| **AT&T** (Cricket, H2O, Red Pocket, Consumer Cellular, Straight Talk on AT&T) | Calls need VoLTE and AT&T's approved-device list. Check the IMEI at att.com "Bring your own phone". If calls fail, ask AT&T to turn on "HD Voice". |
| **T-Mobile** (Metro, Mint, Google Fi, US Mobile Light Speed, Tello, Ultra) | Works with its IMS APN from the database; Wi-Fi calling may need turning on in SIM settings. |
| **Israel** (Pelephone, Cellcom, Partner, HOT Mobile, Golan, kosher lines) | Internet and MMS APNs are included. If VoLTE doesn't register, add the IMS APN from step 4, or ask the carrier; calls may also fall back to 3G where it still exists. |
| **UK / Europe** (EE, O2, Vodafone, Three, …) | APNs included. VoLTE depends on the carrier allowing this phone model. |
| **Others / MVNOs** | Use the APNs of the network they run on (the database has most of them). If the internet doesn't work, copy the APN settings from the carrier's website. |

Kosher / talk-only / no-data plans: calls and texts don't need mobile data, but **step 2 needs internet
once** (Wi-Fi or a data SIM) to download the IMS app.

Don't install a second (Pixel) IMS app next to it: S22 owners report it locks the SIM settings.

**If VoLTE still doesn't register**, the Android 16 base image may not support calling on the S22 yet
(S22 owners report IMS problems on Android 14+ GSIs). Go back to the stock Android 11 (*Going back*):
calls always work there, and KosherFlip runs on it too (phone-only install).

## Going back to stock

Follow the XDA thread **"Cat S22 Flip – revert to Stock"** (it links the stock firmware and the flashing
tool). Keep the bootloader unlocked until you are sure the stock firmware works: S22 owners report that
after re-locking it may not unlock again.

## Community threads (XDA)

- CAT S22 Flip, installing GSI rom – https://xdaforums.com/t/cat-s22-flip-installing-gsi-rom.4660690/
- Cat S22 Flip – revert to Stock – https://xdaforums.com/t/cat-s22-flip-revert-to-stock.4626603/
- How to root Cat S22 Flip on Version 30 – https://xdaforums.com/t/tut-root-how-to-root-cat-s22-flip-on-version-30.4626971/

## How the image is made (for maintainers)

GitHub → Actions → **Build KosherFlip Android 16 image (GSI)** → Run workflow. It takes the newest
TrebleDroid Android 16 image for the device (CAT S22 Flip: arm32_binder64), or the URL you give, adds the KosherFlip apps from the latest app
release (`rom/gsi/inject.sh`): KosherGuard as a privileged system app (re-signed with Android's public test
keys when the base image uses them, so it can set itself up as device owner), the other apps, permissions
and the filter policy, removes AOSP's setup app, makes `vbmeta-disabled.img`, and publishes a pre-release.
