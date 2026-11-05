# The filter and the admin console

## Profiles

| | Kosher | Heimish (default) | Open + protected |
|---|---|---|---|
| Browser | off | on | on |
| Websites | only trusted categories + always-allowed list (strict) | everything except blocked categories | everything except harmful categories |
| Blocked categories | all | social, video, streaming, news, games, dating, gambling, AI chat, unfiltered search, proxies, web chat | dating, gambling, AI chat, unfiltered search, proxies |
| Pictures in browser | none | trusted sites only | all |
| Video | blocked | blocked | allowed |
| Screen filter | on | on | on |

Family-safe DNS (adult content, proxies, SafeSearch) applies in every profile.

## What is enforced

| Area | How |
|---|---|
| Apps | Every app with a launcher icon that is not on `allowedApps` is **hidden** (cannot be opened, even by links). |
| Screen | The **screen filter** (an accessibility service inside KosherGuard) reads the text every app shows and the notifications, scores it with the word filter, and covers + leaves anything inappropriate. Only KosherGuard may be an accessibility service. |
| Browser | KosherBrowser checks every page's text, controls pictures and video, forces SafeSearch, limits downloads and blocks ads. |
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

- **Status** – device-owner state, profile, screen filter, Shabbos mode
- **Profile** – Kosher / Heimish / Open + protected (then fine-tune anything)
- **Requests** – sites and apps the user asked for: Approve / Decline / Later
- **Insights & suggestions** – most used and most blocked sites; one-key "allow" for sites the phone keeps trying
- **Blocked / Trusted categories**
- **Pictures in browser**, **Video**, **Browser**, **Screen filter**, **Filter words**
- **Talk-only**, **Camera**, **Hotspot block**, **USB / Bluetooth / SD file transfer block**
- **Shabbos mode** – off / auto / on, location, Israel or diaspora, 42/50/72-minute nightfall
- **Allowed apps** – tick the apps that should be visible
- **Filter mode** – Standard ↔ Strict
- **Always-allowed websites / Blocked websites** – one domain per line;
  `example.com` covers subdomains, `=example.com` matches only that name
- **AI assistant** – provider (Gemini free / OpenAI-compatible / Claude), key, model, on/off
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

Keys: `profile`, `filterMode` (`blocklist`/`allowlist`), `upstreamDns`, `allowedApps`,
`allowedDomains`, `blockedDomains`, `blockedCategories`, `trustedCategories`, `imageMode`
(`none`/`trusted`/`blur`/`all`), `blockVideo`, `browserEnabled`, `screenFilter`, `customWords`,
`talkOnly`, `cameraEnabled`, `blockTethering`, `blockUsbTransfer`, `shabbosMode` (`off`/`auto`/`on`),
`latitude`, `longitude`, `inIsrael`, `candleMinutes`, `endMinutes`, `allowedDownloadTypes`,
`allowAppInstalls`, `lockUsbDebugging`, `aiEnabled`, `aiProvider`, `aiModel`, `aiBaseUrl`, `aiApiKey`.
Category domain lists live in `apps/KosherGuard/src/main/assets/categories.json`.

## Free AI setup

1. On any computer, open aistudio.google.com, sign in, click **Get API key** → **Create API key** (free).
2. KosherGuard → AI assistant → provider *gemini* → paste the key → Save.

Free OpenAI-compatible services also work: provider *openai*, base URL (e.g.
`https://api.groq.com/openai/v1` or `https://openrouter.ai/api/v1`), a free model name and its key.
The host is added to the always-allowed websites automatically. Free tiers have daily limits.

## Known gaps

- The screen filter reads text, not pictures. Pictures are controlled in the browser;
  in other apps (e.g. a shopping app) rely on allowing only trusted apps.
- An app on the allowed list that uses its own encrypted DNS can reach sites the DNS
  filter does not see (the screen filter still reads its text) – only allow apps you trust.
- The screen filter needs Android's accessibility permission: automatic in the ROM; on the
  apps-only install the script grants it with adb.
- `googleapis.com`/`gstatic.com` are always allowed because Maps, Gmail and
  Play services need them.
