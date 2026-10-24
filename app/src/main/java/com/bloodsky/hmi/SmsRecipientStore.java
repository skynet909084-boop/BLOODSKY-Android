package com.bloodsky.hmi;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts per-widget SMS recipients; projects contain only a random key reference. */
public final class SmsRecipientStore {
    private static final String ALIAS="bloodsky.sms.recipient.v1";
    private static final String PREFS="bloodsky_sms_recipients";
    private SmsRecipientStore() {}
    public static void save(Context context,String key,String phone)throws Exception {
        if(key==null||!key.matches("[A-Fa-f0-9-]{36}"))throw new IllegalArgumentException("Некорректный ключ SMS-получателя");
        if(!SmsNotificationClient.isValidPhone(phone))throw new IllegalArgumentException("Некорректный номер получателя");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,getKey());
        byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(normalize(phone).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteBuffer packed=ByteBuffer.allocate(1+iv.length+encrypted.length);packed.put((byte)iv.length).put(iv).put(encrypted);
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(key,Base64.encodeToString(packed.array(),Base64.NO_WRAP)).apply();
    }
    public static String read(Context context,String key)throws Exception {
        if(key==null||!key.matches("[A-Fa-f0-9-]{36}"))return "";
        String encoded=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key,null);if(encoded==null)return "";
        ByteBuffer packed=ByteBuffer.wrap(Base64.decode(encoded,Base64.NO_WRAP));int size=packed.get()&255;
        if(size<12||size>16||packed.remaining()<=size)throw new IllegalStateException("Некорректные сохранённые данные");
        byte[] iv=new byte[size];packed.get(iv);byte[] encrypted=new byte[packed.remaining()];packed.get(encrypted);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,getKey(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(encrypted),java.nio.charset.StandardCharsets.UTF_8);
    }
    private static String normalize(String phone){return phone.trim().replace(" ","").replace("-","").replace("(","").replace(")","");}
    private static SecretKey getKey()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);java.security.Key found=store.getKey(ALIAS,null);
        if(found instanceof SecretKey)return (SecretKey)found;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());return generator.generateKey();
    }
}
