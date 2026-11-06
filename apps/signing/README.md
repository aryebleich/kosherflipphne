# Release signing key (do this once)

Android only installs an update if it is signed with the **same key** as the
installed app, and the setup QR code pins that key. So releases must be signed
with one private key that you keep forever and never publish.

1. Create the key (needs Java's `keytool`) and its GitHub secrets:
   ```sh
   scripts/create-signing-key.sh        # writes ~/kosherflip-release.jks and prints the secrets
   ```
2. GitHub → your repository → **Settings → Secrets and variables → Actions → New repository secret**,
   and add the four values it prints: `KOSHERFLIP_KEYSTORE_B64`, `KOSHERFLIP_KEYSTORE_PASSWORD`,
   `KOSHERFLIP_KEY_ALIAS`, `KOSHERFLIP_KEY_PASSWORD`.
3. Back up `~/kosherflip-release.jks` and the password somewhere safe (not in the repository).
   If it is lost, phones can't be updated anymore without reinstalling.

Without these secrets CI still builds, but with a temporary debug key that changes
on every build: good for testing, not for phones you hand out.

Keep **USB debugging lock** on in KosherGuard so nobody can side-load a replacement over adb.
