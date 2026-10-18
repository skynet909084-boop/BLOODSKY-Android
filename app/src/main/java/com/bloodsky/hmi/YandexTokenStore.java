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

/** Encrypts the Yandex OAuth token with a provider-specific Android Keystore key. */
public final class YandexTokenStore {
    private static final String ALIAS="bloodsky.yandex.token.v1";
    private static final String PREFS="bloodsky_yandex_secure";
    private static final String VALUE="oauth_token_gcm";
    private YandexTokenStore() {}
    public static void save(Context context,String token)throws Exception {
        if(token==null||token.trim().isEmpty())throw new IllegalArgumentException("Пустой OAuth-токен");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV(), encrypted=cipher.doFinal(token.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteBuffer packed=ByteBuffer.allocate(1+iv.length+encrypted.length);packed.put((byte)iv.length).put(iv).put(encrypted);
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(VALUE,Base64.encodeToString(packed.array(),Base64.NO_WRAP)).apply();
    }
    public static String read(Context context)throws Exception {
        String encoded=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(VALUE,null);if(encoded==null)return "";
        ByteBuffer packed=ByteBuffer.wrap(Base64.decode(encoded,Base64.NO_WRAP));int size=packed.get()&255;
        if(size<12||size>16||packed.remaining()<=size)throw new IllegalStateException("Некорректные сохранённые данные");
        byte[] iv=new byte[size];packed.get(iv);byte[] encrypted=new byte[packed.remaining()];packed.get(encrypted);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(encrypted),java.nio.charset.StandardCharsets.UTF_8);
    }
    public static void clear(Context context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(VALUE).apply();}
    private static SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);java.security.Key found=store.getKey(ALIAS,null);
        if(found instanceof SecretKey)return (SecretKey)found;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());return generator.generateKey();
    }
}
