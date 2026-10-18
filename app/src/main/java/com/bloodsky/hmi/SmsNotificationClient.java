package com.bloodsky.hmi;

import android.content.Context;
import android.telephony.SmsManager;
import java.util.ArrayList;

/** Optional SIM-based SMS alert sender; the caller owns permission and edge-trigger policy. */
public final class SmsNotificationClient {
    private SmsNotificationClient() {}

    public static boolean isValidPhone(String phone) {
        if (phone == null) return false;
        String normalized=phone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
        return normalized.matches("\\+?[0-9]{6,20}");
    }

    public static String message(String template, String value) {
        String text=template==null?"":template.trim();
        if(text.isEmpty()) text="Событие BLOODSKY";
        text=text.replace("{value}", value==null?"":value);
        if(text.length()>1200) text=text.substring(0,1200);
        return text;
    }

    public static void send(Context context, String phone, String text) {
        if(!isValidPhone(phone)) throw new IllegalArgumentException("Некорректный номер телефона");
        if(text==null||text.trim().isEmpty()) throw new IllegalArgumentException("Пустой текст SMS");
        if(!context.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY))
            throw new IllegalStateException("В устройстве нет телефонии для отправки SMS");
        SmsManager manager;
        if(android.os.Build.VERSION.SDK_INT>=31) manager=context.getSystemService(SmsManager.class);
        else manager=SmsManager.getDefault();
        if(manager==null) throw new IllegalStateException("SMS недоступно на этом устройстве");
        ArrayList<String> parts=manager.divideMessage(text);
        if(parts.size()<=1) manager.sendTextMessage(phone.trim(),null,text,null,null);
        else manager.sendMultipartTextMessage(phone.trim(),null,parts,null,null);
    }
}
