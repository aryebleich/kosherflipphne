# Choosing a flip phone / porting

## Path A (apps only) works on

Any Android 9+ phone, flip or not, as long as you can turn on USB debugging and
factory reset it. This is how most existing Android flip phones can become a
KosherFlip phone today.

## Path B (Android 16 GSI) needs

1. **arm64 CPU running a 64-bit Android** – check `adb shell getprop ro.product.cpu.abilist`
   contains `arm64-v8a`. Many low-end Android Go flip phones are 32-bit only; this GSI will not boot on them.
2. **Project Treble** – `adb shell getprop ro.treble.enabled` → `true`.
3. **An unlockable bootloader** – Developer options shows *OEM unlocking*, and
   `fastboot flashing unlock` works. Most US carrier-locked flip phones do not allow this.
4. **A recent vendor image** – the phone should ship with Android 11 or newer; the
   older the vendor, the more likely something (camera, calls, Wi-Fi calling) breaks on a GSI.

Quick check with the phone connected:

```sh
for p in ro.product.model ro.product.cpu.abilist ro.treble.enabled \
         ro.vndk.version ro.boot.dynamic_partitions ro.build.version.release; do
  printf '%-28s %s\n' $p "$(adb shell getprop $p)"; done
```

## Keypad-only phones

The launcher, app list, admin console and AI chat are all built to be driven
with D-pad, OK, soft keys and number keys. Third-party apps (Maps, Gmail) expect
touch; on keypad-only phones they may need the phone maker's "virtual mouse"
mode, which comes from the vendor and is not part of a GSI.

## Porting to a specific phone

For a phone that is not Treble-compatible, or to fix hardware quirks, create a
device product that reuses the KosherFlip layer:

```make
# device/<maker>/<phone>/kosher_<phone>.mk
$(call inherit-product, device/<maker>/<phone>/device.mk)   # from the phone's LineageOS/AOSP tree
$(call inherit-product, vendor/kosher/config/common.mk)
PRODUCT_NAME := kosher_<phone>
```
and add it to `rom/vendor/kosher/AndroidProducts.mk`. Everything KosherFlip-specific
lives in `vendor/kosher/config/common.mk`, so it works with any AOSP-based device tree
(including LineageOS 23, which is Android 16).
