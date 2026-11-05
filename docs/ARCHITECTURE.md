# Architecture

## Layers

1. **Android 16 (AOSP) GSI** – `rom/vendor/kosher/products/kosher_gsi_arm64.mk`
   inherits AOSP's `aosp_arm64` generic system image and adds `config/common.mk`.
   A GSI replaces only the `system` partition; the phone keeps its own kernel,
   modem firmware and vendor drivers (Project Treble), so one image fits many phones.
2. **KosherFlip apps** – built by Gradle in `apps/` and imported into the ROM by
   `rom/vendor/kosher/prebuilt/Android.bp`:
   - `KosherGuard` – privileged, **platform-signed**; overrides AOSP `Provision`,
     `Browser2` and `QuickSearchBox`.
   - `FlipLauncher` – overrides `Launcher3`/`Launcher3QuickStep`.
   - `KosherAI`.
3. **Optional Google apps** – MindTheGapps (`WITH_GMS=true`).

## First boot (ROM)

```
boot → SetupActivity (KosherGuard, HOME + SETUP_WIZARD, replaces Provision)
     → setActiveAdmin + setDeviceOwner (system APIs, allowed by platform signature)
     → PolicyEnforcer.apply()
     → mark DEVICE_PROVISIONED / user_setup_complete
     → disable SetupActivity → FlipLauncher
```
If Google's setup wizard is present (GMS builds), `BootReceiver` takes device
ownership at `BOOT_COMPLETED`, while setup is still running and before any
account exists.

On a non-ROM install the same APK detects it lacks the system permissions,
disables `SetupActivity`, and is made device owner by `adb shell dpm set-device-owner`.

## KosherGuard

| Piece | File | Job |
|---|---|---|
| Policy | `Policy.java`, `assets/default_policy.json`, `/product/etc/kosherflip/policy.json` | allowed apps, website lists, mode, AI key, switches |
| Enforcer | `PolicyEnforcer.java` | user restrictions, hides non-approved apps, always-on VPN, home screen, AI managed config |
| DNS filter | `FilterVpnService.java`, `DnsPacket.java`, `DnsFilter.java` | DNS-only local VPN: NXDOMAIN for blocked names, forward the rest to CleanBrowsing Family |
| Admin | `AdminActivity.java`, `Pin.java` | PIN console (salted hash, lock-out after 5 tries) |
| Triggers | `BootReceiver.java`, `GuardAdminReceiver.java` | re-apply at boot, on package install/update, on becoming owner |

Only DNS packets enter the VPN tunnel (a route to the virtual resolver
`10.111.222.1`), so normal data is not slowed down.

## KosherAI

`Assistant.java` calls the Claude Messages API through the official Anthropic
Java SDK (`com.anthropic:anthropic-java`) with a fixed modesty/“kosher phone”
system prompt, low effort for quick short answers, and server-side refusal
fallbacks. The API key is set by the admin in KosherGuard and delivered as an
Android *managed configuration* (`setApplicationRestrictions`), so the user never
sees it. `api.anthropic.com` is on the always-allowed list.

## FlipLauncher

Feature-phone home: clock, civil + Hebrew date (`android.icu.util.HebrewCalendar`),
carrier and battery; digits start a call, CALL opens the dialer, OK/left soft
key opens the numbered app list, D-pad shortcuts (↑ messages, ↓ contacts,
← camera, → AI). Apps hidden by KosherGuard are invisible to every launcher.
