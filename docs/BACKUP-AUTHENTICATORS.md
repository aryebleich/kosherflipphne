# Backing up authenticator apps (before flashing or factory reset)

Installing Android 16 (or any factory reset) **erases every app's data**, including your
authenticator codes. If you lose them you can be locked out of those accounts. Do this first.

**Why there is no "back up everything" button.** Android locks each app's secret keys inside that
app. No other app, no computer and no adb backup can copy them out (most authenticator apps block
backups on purpose). So the only safe way is **each app's own export or transfer**. The steps below
need **no Wi-Fi**.

## What you need

- **Best: a second phone** (any Android or iPhone, even an old one with no SIM). Codes go
  phone-to-phone by QR code, offline.
- Or a **microSD card** (the CAT S22 Flip has a slot) or a **USB cable to a computer**, for apps
  that export to a file.
- 20 minutes, and **paper** for recovery codes.

## Step 1 – Write down recovery codes (do this for every account)

For each account in your authenticators (Google, Microsoft, bank, email, …) open the website/app
security settings and save its **backup / recovery codes** on paper. This is your safety net if a
transfer fails.

## Step 2 – Move each app's codes

| App | Offline method (no Wi-Fi) |
|---|---|
| **Google Authenticator** | Old phone: ☰/⋮ → **Transfer accounts → Export accounts** → select all → it shows one or more QR codes. Second phone (Google Authenticator or **Aegis**): **+ → Scan a QR code / Import**. Repeat for each QR code shown. |
| **2FAS** | Settings → **Backup → Export to file**, choose a password → save the `.2fas` file to the **SD card** or copy it to a computer by USB. Restore: 2FAS → Backup → Import. |
| **Aegis** | Settings → **Import & Export → Export**, keep **encryption on** → save the vault file to the **SD card** or computer. Restore: Aegis → Import. |
| **FreeOTP+** | ⋮ → **Export** (JSON or URI list) → SD card / computer. These files are **not encrypted**: delete them right after restoring. |
| **Microsoft Authenticator** | Only a cloud backup exists: Settings → **Backup → Cloud backup** (needs a personal Microsoft account and internet – **mobile data works**, no Wi-Fi needed). Restore by signing in on the new system. Or move single accounts by setting up the authenticator again on each website. |
| **Authy (Twilio)** | Only cloud: turn on **Backups** (choose a backup password) and/or add the second phone via **Devices** – uses mobile data. Restore by registering your phone number on the new system. |
| **Duo Mobile** | **Duo Restore** (Google account, internet/mobile data). |
| **LastPass / Bitwarden / 1Password authenticator** | Stored in your password-manager account – just sign in again afterwards. |

Not on the list? Look in the app's settings for *Export*, *Transfer* or *Backup*. If there is none,
set up that account again on a second device from the website before you wipe.

## Step 3 – Check before you wipe (most important)

On the second phone (or after importing the file into Aegis/2FAS on another device):
1. Every account is there.
2. For each account the **6-digit code is the same** as on the old phone at the same moment.

Only then continue with the flashing. `flash-s22.sh` asks you to confirm this.

## Step 4 – Keep it safe

- **Encrypt exports** (2FAS/Aegis passwords) and use a strong password.
- **Don't photograph or screenshot the QR codes**, and don't upload export files to email/cloud –
  anyone with them can make your codes.
- Take the **SD card out** before flashing (the flash doesn't erase it, but it's safer).
- After restoring on the new system, **delete** the export files.

## Step 5 – Restore on KosherFlip Android 16

After first setup (profile *Filtered* allows all apps), install your authenticator apps from the Play
Store (Google apps image) or as APK files (Bluetooth/SD card), then import as in Step 2 (scan the QR
codes from the second phone, or open the export file from the SD card). Check a code on one website
before deleting anything from the second phone.
