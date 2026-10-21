package com.bloodsky.hmi;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Device-bound encrypted storage for imported Craft ID28 email credentials. */
public final class CraftEmailCredentialStore {
    private static final String ALIAS = "bloodsky.craft.email.credentials.v1";
    private static final String PREFS = "bloodsky_craft_email_credentials";

    private CraftEmailCredentialStore() {}

    public static void save(Context context, String key, String login, String password) throws Exception {
        requireKey(key);
        String safeLogin = login == null ? "" : login;
        String safePassword = password == null ? "" : password;
        if (safeLogin.length() > 512 || safePassword.length() > 4096)
            throw new IllegalArgumentException("Craft email credential exceeds the secure storage limit");
        byte[] plain = new JSONObject().put("login", safeLogin).put("password", safePassword)
                .toString().getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plain);
            ByteBuffer packed = ByteBuffer.allocate(1 + iv.length + encrypted.length);
            packed.put((byte) iv.length).put(iv).put(encrypted);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(key, Base64.encodeToString(packed.array(), Base64.NO_WRAP)).apply();
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    public static JSONObject read(Context context, String key) throws Exception {
        if (key == null || !key.matches("[A-Fa-f0-9-]{36}")) return new JSONObject();
        String encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null);
        if (encoded == null) return new JSONObject();
        ByteBuffer packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
        int ivLength = packed.get() & 255;
        if (ivLength < 12 || ivLength > 16 || packed.remaining() <= ivLength)
            throw new IllegalStateException("Некорректные сохранённые данные email-компонента");
        byte[] iv = new byte[ivLength];
        packed.get(iv);
        byte[] encrypted = new byte[packed.remaining()];
        packed.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(encrypted);
        try { return new JSONObject(new String(plain, StandardCharsets.UTF_8)); }
        finally { java.util.Arrays.fill(plain, (byte) 0); }
    }

    public static void clear(Context context, String key) {
        if (key != null && key.matches("[A-Fa-f0-9-]{36}"))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply();
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[A-Fa-f0-9-]{36}"))
            throw new IllegalArgumentException("Некорректный ключ email credentials");
    }

    private static SecretKey getKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
