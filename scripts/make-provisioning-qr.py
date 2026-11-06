#!/usr/bin/env python3
"""
Make the KosherFlip setup QR code (Android "QR code provisioning").

On a factory-reset phone, tap the first setup screen 6 times, connect to
Wi-Fi when asked, and scan this code: the phone downloads KosherGuard, makes it
the device owner, and KosherGuard installs the other KosherFlip apps.

  scripts/make-provisioning-qr.py --cert-sha256 <hex> [--url URL] [--out DIR]
  scripts/make-provisioning-qr.py --apk KosherGuard.apk   (needs apksigner on PATH)

Optional Wi-Fi in the code: --wifi-ssid NAME --wifi-password PASS [--wifi-security WPA]
"""
import argparse, base64, json, os, re, subprocess, sys

DEFAULT_URL = "https://github.com/aryebleich/kosherflipphne/releases/latest/download/KosherGuard.apk"


def cert_from_apk(apk):
    out = subprocess.run(["apksigner", "verify", "--print-certs", apk], check=True,
                         capture_output=True, text=True).stdout
    m = re.search(r"certificate SHA-256 digest: ([0-9a-f]{64})", out)
    if not m:
        sys.exit("could not read the signing certificate from " + apk)
    return m.group(1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk")
    ap.add_argument("--cert-sha256", default=None)
    ap.add_argument("--url", default=DEFAULT_URL)
    ap.add_argument("--out", default="out/provisioning")
    ap.add_argument("--wifi-ssid")
    ap.add_argument("--wifi-password")
    ap.add_argument("--wifi-security", default="WPA")
    a = ap.parse_args()

    if not a.cert_sha256 and not a.apk:
        sys.exit("give --apk KosherGuard.apk or --cert-sha256 HEX")
    hexdigest = a.cert_sha256 or cert_from_apk(a.apk)
    checksum = base64.urlsafe_b64encode(bytes.fromhex(hexdigest)).decode().rstrip("=")
    data = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.kosherflip.guard/.GuardAdminReceiver",
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": a.url,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": checksum,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": False,
    }
    if a.wifi_ssid:
        data["android.app.extra.PROVISIONING_WIFI_SSID"] = a.wifi_ssid
        data["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = a.wifi_security
        if a.wifi_password:
            data["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = a.wifi_password

    os.makedirs(a.out, exist_ok=True)
    text = json.dumps(data, separators=(",", ":"))
    with open(os.path.join(a.out, "provisioning.json"), "w") as f:
        json.dump(data, f, indent=2)
    try:
        import qrcode
    except ImportError:
        print("pip install qrcode[pil] to also write the PNG; JSON written to", a.out)
        return
    img = qrcode.make(text, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=4)
    img.save(os.path.join(a.out, "kosherflip-setup-qr.png"))
    print("wrote", os.path.join(a.out, "kosherflip-setup-qr.png"))


if __name__ == "__main__":
    main()
