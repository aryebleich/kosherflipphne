package com.kosherflip.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talk-only mode (like talk-only kosher phones): the messaging apps are hidden,
 * every sender gets one polite auto-reply a day, and verification codes
 * (banks, Google, Uber...) are still shown as a notification.
 */
public class SmsReceiver extends BroadcastReceiver {
    static final String REPLY = "This phone does not receive text messages. Please call instead. Thank you!";
    private static final Pattern CODE = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)");
    private static final Pattern CODE_WORDS = Pattern.compile(
            "(?i)(code|verif|otp|passcode|pin|one[- ]time|login|sign[- ]in|קוד)");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        Policy p = Policy.load(context);
        if (!p.talkOnly) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;
        String from = msgs[0].getOriginatingAddress();
        StringBuilder body = new StringBuilder();
        for (SmsMessage m : msgs) body.append(m.getMessageBody());

        String code = verificationCode(body.toString());
        if (code != null) {
            showCode(context, from, code);
            return; // never auto-reply to short-code senders
        }
        autoReply(context, from);
    }

    static String verificationCode(String body) {
        if (!CODE_WORDS.matcher(body).find()) return null;
        Matcher m = CODE.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static void showCode(Context ctx, String from, String code) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("codes", "Verification codes",
                NotificationManager.IMPORTANCE_HIGH));
        Notification n = new Notification.Builder(ctx, "codes")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Verification code: " + code)
                .setContentText("From " + from)
                .setAutoCancel(true)
                .build();
        nm.notify(code.hashCode(), n);
    }

    private static void autoReply(Context ctx, String to) {
        if (to == null || to.replaceAll("\\D", "").length() < 7) return; // skip short codes
        SharedPreferences sp = ctx.getSharedPreferences("autoreply", Context.MODE_PRIVATE);
        long day = System.currentTimeMillis() / 86_400_000L;
        if (sp.getLong(to, -1) == day) return;
        sp.edit().putLong(to, day).apply();
        try {
            SmsManager sms = ctx.getSystemService(SmsManager.class);
            if (sms != null) sms.sendTextMessage(to, null, REPLY, null, null);
        } catch (RuntimeException ignored) {
            // No SMS permission or no service.
        }
    }
}
