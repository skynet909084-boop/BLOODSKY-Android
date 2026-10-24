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

/** Encrypts the Home Assistant token with an Android Keystore AES-GCM key. */
public final class HomeAssistantTokenStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "bloodsky.homeassistant.token.v1";
    private static final String PREFS = "bloodsky_homeassistant_secure";
    private static final String TOKEN = "access_token_gcm";
    private HomeAssistantTokenStore() {}

    public static void save(Context context, String token) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new IllegalArgumentException("Пустой access token");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(token.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteBuffer packed = ByteBuffer.allocate(1 + iv.length + encrypted.length);
        packed.put((byte)iv.length).put(iv).put(encrypted);
        String value = Base64.encodeToString(packed.array(), Base64.NO_WRAP);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN, value).apply();
    }

    public static String read(Context context) throws Exception {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null);
        if (value == null) return "";
        byte[] packed = Base64.decode(value, Base64.NO_WRAP);
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        int ivLength = buffer.get() & 0xff;
        if (ivLength < 12 || ivLength > 16 || buffer.remaining() <= ivLength) throw new IllegalStateException("Некорректные сохранённые данные");
        byte[] iv = new byte[ivLength]; buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).apply();
    }

    private static SecretKey getKey() throws Exception {
        KeyStore store = KeyStore.getInstance(ANDROID_KEY_STORE);
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey)existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
