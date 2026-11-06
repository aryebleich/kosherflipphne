package com.kosherflip.guard;

import java.util.ArrayList;
import java.util.List;

/** Works out why calls don't work, from what Android reports (pure logic, unit tested). */
final class CallCheck {
    // Values of TelephonyManager.SIM_STATE_* and ServiceState.STATE_*
    static final int SIM_UNKNOWN = 0, SIM_ABSENT = 1, SIM_PIN = 2, SIM_PUK = 3, SIM_NETWORK_LOCKED = 4,
            SIM_READY = 5, SIM_NOT_READY = 6, SIM_PERM_DISABLED = 7, SIM_IO_ERROR = 8, SIM_RESTRICTED = 9;
    static final int SVC_UNKNOWN = -1, SVC_IN = 0, SVC_OUT = 1, SVC_EMERGENCY = 2, SVC_POWER_OFF = 3;

    static final class Input {
        boolean airplane;
        int simState = SIM_UNKNOWN;
        int voice = SVC_UNKNOWN;
        String simCarrier = "", networkCarrier = "";
        boolean lockdownVpn;
        boolean dialerAllowed = true;
    }

    static final class Finding {
        final boolean problem;
        final String title, fix;
        Finding(boolean problem, String title, String fix) { this.problem = problem; this.title = title; this.fix = fix; }
    }

    static List<Finding> diagnose(Input in) {
        List<Finding> out = new ArrayList<>();
        if (in.airplane) {
            out.add(new Finding(true, "Airplane mode is on", "Turn Airplane mode off."));
            return out;
        }
        switch (in.simState) {
            case SIM_ABSENT:
                out.add(new Finding(true, "No SIM card found",
                        "Put the SIM card in (gold contacts down), restart the phone. eSIM: the S22 Flip has no eSIM."));
                return out;
            case SIM_PIN:
            case SIM_PUK:
                out.add(new Finding(true, "The SIM card is locked with a PIN",
                        "Enter the SIM PIN on the lock screen. PUK code: ask your carrier."));
                return out;
            case SIM_NETWORK_LOCKED:
                out.add(new Finding(true, "This phone is locked to another carrier (carrier lock)",
                        "The phone only accepts SIM cards from the carrier it was sold by. Ask THAT carrier to "
                        + "unlock it (free once the phone is paid off):\n"
                        + "- T-Mobile / Metro: Settings → Device Unlock app, or call 611 / t-mobile.com/unlock\n"
                        + "- Verizon: unlocks automatically 60 days after activation; else call *611\n"
                        + "- AT&T / Cricket: att.com/deviceunlock\n"
                        + "- Others: ask the seller for the unlock code\n"
                        + "Once unlocked, any carrier's SIM works."));
                return out;
            case SIM_PERM_DISABLED:
            case SIM_IO_ERROR:
            case SIM_RESTRICTED:
                out.add(new Finding(true, "The SIM card isn't working",
                        "Take it out and put it back, restart. If it stays, ask your carrier for a new SIM."));
                return out;
            case SIM_NOT_READY:
            case SIM_UNKNOWN:
                out.add(new Finding(true, "The SIM card isn't ready yet", "Wait a minute or restart the phone."));
                return out;
            default:
                out.add(new Finding(false, "SIM card OK" + (in.simCarrier.isEmpty() ? "" : " (" + in.simCarrier + ")"), ""));
        }
        switch (in.voice) {
            case SVC_EMERGENCY:
                out.add(new Finding(true, "Emergency calls only: the carrier doesn't accept this phone",
                        "Usually the carrier hasn't approved this phone (IMEI) or the line isn't active. Call the "
                        + "carrier from another phone, give them the IMEI (dial *#06#) and ask them to allow the phone "
                        + "and turn on VoLTE / HD Voice. Verizon and AT&T only allow approved phones."));
                break;
            case SVC_OUT:
            case SVC_POWER_OFF:
                out.add(new Finding(true, "No mobile service",
                        "Mobile network settings: set Preferred network type to 4G/LTE (or LTE/3G/2G auto), "
                        + "Network operators: Automatic, turn on VoLTE / 4G Calling. Check that your carrier's "
                        + "network bands are supported by this phone."));
                break;
            case SVC_IN:
                out.add(new Finding(false, "Connected to " + (in.networkCarrier.isEmpty() ? "the mobile network" : in.networkCarrier), ""));
                out.add(new Finding(false, "If calls still fail",
                        "Most carriers now carry calls only over 4G (VoLTE): Mobile network settings → turn on "
                        + "VoLTE / 4G Calling / HD Voice. Open the Phone info menu (dial *#*#4636#*#*) and check "
                        + "\"VoLTE provisioned\" is on. Ask the carrier to enable VoLTE for your line."));
                break;
            default:
                out.add(new Finding(false, "Mobile service unknown",
                        "Allow \"Phone\" permission for KosherGuard to see more, or check the signal bars."));
        }
        if (in.lockdownVpn) {
            out.add(new Finding(true, "\"Block connections without VPN\" is on",
                    "It can stop Wi-Fi calling and picture messages on some phones. VPN settings → gear next to "
                    + "KosherGuard → turn \"Block connections without VPN\" OFF (keep Always-on VPN on)."));
        }
        if (!in.dialerAllowed) {
            out.add(new Finding(true, "The phone app is blocked", "KosherGuard → Apps: allow the Phone app."));
        }
        return out;
    }
}
