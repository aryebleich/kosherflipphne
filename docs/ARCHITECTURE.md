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
   - `KosherBrowser` – filtered WebView browser.
   - `KosherAI`.
   All of them use `apps/common` (word filter, zmanim).
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
| Profiles & categories | `Profiles.java`, `Categories.java`, `assets/categories.json` | presets and 14 website categories |
| Screen filter | `ScreenGuardService.java` | accessibility service: reads on-screen text in every app, covers & leaves bad content |
| Learning | `Stats.java`, `Requests.java`, `RequestReceiver.java` | insights, suggestions, request/approve queue |
| Phone modes | `SmsReceiver.java`, `JewishCalendar.java`, `PolicyJob.java` | talk-only, Shabbos/Yom Tov mode, 15-minute re-apply |
| Speed | `DnsCache.java` | TTL-aware answer cache inside the VPN |
| Triggers | `BootReceiver.java`, `GuardAdminReceiver.java` | re-apply at boot, on package install/update, on becoming owner |

Only DNS packets enter the VPN tunnel (a route to the virtual resolver
`10.111.222.1`), so normal data is not slowed down.

## KosherBrowser

WebView browser configured by KosherGuard (managed configuration). Per page:
`setBlockNetworkImage` + CSS for picture modes, request interception for ads and
video, an injected script that sends the page text (and every change, debounced)
to the word filter, SafeSearch rewriting, and a block page whose "Request access"
button sends a request to KosherGuard. DNS errors (sites KosherGuard blocks) show
the same block page. A D-pad pointer dispatches taps for keypad-only phones.

## KosherAI

`Assistant.java` keeps the history and the fixed modest system prompt and runs
the word filter on each question (bad questions are never sent) and answer.
Providers: `GeminiProvider` (default, free API key, Google safety settings at
`BLOCK_LOW_AND_ABOVE`), `OpenAiProvider` (any OpenAI-compatible endpoint, e.g. free
models), `ClaudeProvider` (official Anthropic Java SDK). Provider, key and model are
set by the admin in KosherGuard and delivered as managed configuration.

## FlipLauncher

Feature-phone home: clock, civil + Hebrew date (`android.icu.util.HebrewCalendar`),
carrier and battery; digits start a call, CALL opens the dialer, OK/left soft
key opens the numbered app list, D-pad shortcuts (↑ messages, ↓ contacts,
← camera, → AI). Apps hidden by KosherGuard are invisible to every launcher.
