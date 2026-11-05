# The filter and the admin console

## What is enforced

| Area | How |
|---|---|
| Apps | Every app with a launcher icon that is not on `allowedApps` is **hidden** (cannot be opened, even by links). |
| Websites | Always-on DNS-filter VPN. **Standard mode**: family-safe upstream DNS (CleanBrowsing Family – adult content, proxies/VPNs, SafeSearch) **plus** the `blockedDomains` list. **Strict mode**: only `allowedDomains` resolve. |
| No browser | The ROM ships no browser; on path A any browser is hidden. |
| Bypass | VPN settings, Private DNS, factory reset, safe mode, extra users, unknown sources and app installs/uninstalls are locked. KosherGuard cannot be uninstalled or disabled. Optional: lock USB debugging. |
| AI | `KosherAI` with a fixed modest system prompt; can be switched off by the admin. |

Default allowed apps: phone, contacts, messages, camera, clock, calculator,
calendar, settings, SIM toolkit, recorder, FM radio, gallery, **Google Maps,
Waze, Gmail**, KosherAI. Blocked by default (hidden unless the admin allows
them): Chrome and every other browser, Play Store, YouTube, Google search app,
Gemini, social media, etc.

## Admin console

Open **KosherGuard** from the app list and enter the PIN. Everything is a list
you can drive with the D-pad (OK = select, BACK = menu):

- **Status** – device-owner state and current mode
- **Allowed apps** – tick the apps that should be visible
- **Filter mode** – Standard ↔ Strict
- **Always-allowed websites / Blocked websites** – one domain per line;
  `example.com` covers subdomains, `=example.com` matches only that name
- **AI assistant** – Claude API key, enable/disable
- **App installs** – temporarily allow installing/updating apps (e.g. to update
  Waze from Play), then turn it off again
- **USB debugging lock**
- **Apply now**, **Change PIN**, **Reset to defaults**
- **Remove KosherGuard** – lifts every restriction so the phone can be returned to normal

Five wrong PINs lock the console for 5 minutes.

## Customising the defaults

- Apps path: edit `apps/KosherGuard/src/main/assets/default_policy.json`.
- ROM path: `rom/vendor/kosher/prebuilt/policy.json` is installed as
  `/product/etc/kosherflip/policy.json` and takes priority over the APK's copy.
  (CI checks that both files are identical; change both.)

Keys: `filterMode` (`blocklist`/`allowlist`), `upstreamDns`, `allowedApps`,
`allowedDomains`, `blockedDomains`, `allowAppInstalls`, `lockUsbDebugging`,
`aiEnabled`, `aiApiKey`.

## Known gaps

- An app on the allowed list that embeds a web view or uses its own encrypted
  DNS can show content the DNS filter does not see – only allow apps you trust.
- `googleapis.com`/`gstatic.com` are always allowed because Maps, Gmail and
  Play services need them.
