package com.bloodsky.hmi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

/** Parses only the foreground SMS_RECEIVED broadcast; the Activity validates commands. */
public final class SmsIncomingBroadcastReceiver extends BroadcastReceiver {
    public interface Listener { void onMessage(String sender, String body); }

    private final Listener listener;

    public SmsIncomingBroadcastReceiver(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener is required");
        this.listener = listener;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
        Bundle extras = intent.getExtras();
        if (extras == null) return;
        Object rawPdus = extras.get("pdus");
        if (!(rawPdus instanceof Object[])) return;
        Object[] pdus = (Object[]) rawPdus;
        if (pdus.length == 0 || pdus.length > 10) return;
        String format = extras.getString("format");
        String sender = null;
        StringBuilder body = new StringBuilder();
        try {
            for (Object raw : pdus) {
                if (!(raw instanceof byte[])) return;
                SmsMessage part = Build.VERSION.SDK_INT >= 23
                        ? SmsMessage.createFromPdu((byte[]) raw, format)
                        : SmsMessage.createFromPdu((byte[]) raw);
                if (part == null || part.getOriginatingAddress() == null || part.getMessageBody() == null) return;
                String partSender = part.getOriginatingAddress();
                if (sender == null) sender = partSender;
                else if (!sender.equals(partSender)) return;
                body.append(part.getMessageBody());
                if (body.length() > 512) return;
            }
            if (sender != null && body.length() > 0) listener.onMessage(sender, body.toString());
        } catch (RuntimeException malformed) {
            // Ignore malformed/unsupported PDUs; never partially execute a command.
        }
    }
}
