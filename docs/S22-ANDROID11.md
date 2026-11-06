# CAT S22 Flip – KosherFlip on its own Android 11 (recommended)

This is the **easy, safe version for the CAT S22 Flip**:

- **No computer, no QR code, no factory reset, no unlocking.** Everything is done on the phone.
- **Calls work on every carrier** exactly as before: the phone keeps its own carrier-certified
  Android 11, so VoLTE (HD calls), Wi-Fi calling, texts, MMS and 911 are untouched – Verizon, AT&T,
  T-Mobile, and every brand that runs on them (Visible, Cricket, Metro, Mint, Straight Talk, US Mobile,
  Google Fi …), Israeli, UK and other carriers. KosherGuard's filter never touches the phone app, the
  calling service or MMS, and always lets carrier servers through.
- **Only content is filtered.** All apps and all browsers stay available (Chrome, the Play Store,
  WhatsApp, Maps, Waze, YouTube, AI apps …). What they *show* is filtered:
  - the **screen filter** reads the text on the screen in every app and browser and covers anything
    inappropriate;
  - **Google, Bing and YouTube** are forced into SafeSearch / Restricted Mode, adult sites are blocked
    in every app;
  - **AI**: the built-in *Ask AI* filters every question and answer; other AI apps go through the
    screen filter;
  - proxy/VPN apps and sites stay blocked so the filter can't be switched off.

(Want Android 16 instead? See [S22-ANDROID16.md](S22-ANDROID16.md) – it needs a computer once and
calling is not guaranteed.)

## Install (about 15 minutes)

1. **Connect to the internet** (Wi-Fi or mobile data).
2. Open the phone's browser and go to
   **github.com/aryebleich/kosherflipphne/releases/latest/download/KosherGuard.apk**
   (or receive `KosherGuard.apk` from another KosherFlip phone by Bluetooth: KosherGuard → *Share KosherFlip*).
3. Open the downloaded file. Android asks to allow the browser to **install unknown apps** → allow,
   press Back, open the file again → **Install** → **Open**.
4. Follow the **KosherFlip setup** checklist (each step opens the right screen; press Back to return):
   1. **Admin PIN** – for the person who supervises the phone.
   2. **Profile** – choose **Filtered – all apps & browsers, content filtered** (the first one).
   3. **Allow KosherGuard to install the KosherFlip apps** → *Allow from this source* → Back.
   4. **Install the KosherFlip apps** → press **Install** for each one.
   5. **Website filter** → **OK**.
   6. **Screen filter** → Settings → Accessibility → **KosherGuard screen filter** → **On** → Allow.
   7. **Protect KosherGuard from removal** → **Activate**.
   8. **Home screen** → *Flip Home* → *Always* (or keep the CAT home screen – both work).
   9. **T9 keyboard** (optional) – KosherKeys adds Hebrew and Yiddish T9; the CAT keyboard works too.
   10. **Always-on filter** → ⚙ next to KosherGuard → **Always-on VPN** on (leave *Block connections without VPN* **off** – it can stop Wi-Fi calling and picture messages) → Back → *I did it*.
   11. **Finish – lock the phone.**
5. Optional: KosherGuard → *AI assistant* → paste a free Gemini key (aistudio.google.com).

That's it. The phone, contacts, messages and all your apps (including your authenticator apps) stay as
they were: nothing is erased.

## Check calls (one minute)

Make a call, receive a call, send a text and a picture message. Everything uses the phone's own system,
so it works as before. If you use Wi-Fi calling, turn Wi-Fi on and make one more call.

## Calls don't work? (also if they didn't work before KosherFlip)

Open **KosherGuard → Check calls** (also at the bottom of the setup checklist). It reads what the phone
reports and tells you the cause and the fix:

| It says | What it means / what to do |
|---|---|
| **Locked to another carrier** | The S22 was sold by one carrier (often T-Mobile in the US) and only accepts its SIM cards. Ask **that** carrier to unlock it – free when the phone is paid off: T-Mobile/Metro: *Device Unlock* app or 611 · Verizon: automatic 60 days after activation · AT&T/Cricket: att.com/deviceunlock. After unlocking, every carrier's SIM works. |
| **Emergency calls only** | The carrier doesn't accept this phone yet: call them, give the **IMEI** (dial `*#06#`), ask them to approve the phone and turn on **VoLTE / HD Voice**. Verizon and AT&T only allow approved phones. |
| **No mobile service** | Mobile network settings → Preferred network type **4G/LTE**, Network operators **Automatic**, **VoLTE / 4G Calling** on. |
| **Connected, but calls fail** | Turn on **VoLTE / 4G Calling** (button in Check calls). In the Phone info menu (`*#*#4636#*#*`) check *VoLTE provisioned*. Ask the carrier to enable VoLTE for your line. |
| **"Block connections without VPN" is on** | Turn it off (keep Always-on VPN on). |

KosherFlip itself never blocks calls: the phone app, the in-call screen, the calling service and MMS are
always allowed and never filtered.

## Changing settings later

KosherGuard → PIN → for example *Apps* (block a single app), *Blocked categories*, *Filter words*,
*Requests* (sites the user asked for), *Insights*. *Settings unlocked for 10 minutes* lets the admin use
Android's own Settings freely.

## Notes

- The screen filter reads **text**; pictures in other apps are limited by SafeSearch, YouTube
  Restricted Mode and adult-site blocking. For stricter control choose the *Heimish* or *Kosher* profile.
- This is *Standard protection* (no factory reset): Android's own Settings screens that could switch off
  KosherFlip are locked, but someone determined could still use the phone's hardware buttons (safe mode /
  recovery). For *Full protection* use Method B of [INSTALL.md](INSTALL.md) once with a computer.
- Updates: KosherGuard → *Install / update KosherFlip apps*.
