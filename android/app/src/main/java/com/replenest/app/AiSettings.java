package com.replenest.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.net.URI;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class AiSettings {
    private static final String PREFS_NAME = "replenest_ai_settings";
    private static final String KEY_ALIAS = "replenest_ai_api_key_v1";
    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_ENDPOINT = "endpoint";
    private static final String PREF_MODEL = "model";
    private static final String PREF_ENCRYPTED_KEY = "encrypted_key";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final int GCM_TAG_BITS = 128;

    private AiSettings() {}

    public static boolean enabled(Context context) {
        return preferences(context).getBoolean(PREF_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) throws Exception {
        save(context, enabled, enabled ? endpoint(context) : null,
                enabled ? model(context) : null, "");
    }

    public static String endpoint(Context context) {
        return preferences(context).getString(PREF_ENDPOINT, DEFAULT_ENDPOINT);
    }

    public static String model(Context context) {
        return preferences(context).getString(PREF_MODEL, "");
    }

    public static boolean hasKey(Context context) {
        String encrypted = preferences(context).getString(PREF_ENCRYPTED_KEY, null);
        return encrypted != null && !encrypted.isEmpty();
    }

    public static void save(
            Context context, boolean enabled, String baseUrl, String model, String newKey)
            throws Exception {
        SharedPreferences preferences = preferences(context);
        String normalizedEndpoint = null;
        String normalizedModel = null;
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            normalizedEndpoint = normalizeEndpoint(baseUrl);
        }
        if (model != null && !model.trim().isEmpty()) {
            normalizedModel = model.trim();
            if (normalizedModel.length() > 100) {
                throw new IllegalArgumentException("model is too long");
            }
        }

        String normalizedKey = newKey == null ? "" : newKey.trim();
        if (normalizedEndpoint != null
                && !normalizedEndpoint.equals(endpoint(context))
                && normalizedKey.isEmpty()
                && hasKey(context)) {
            throw new IllegalArgumentException("更换服务地址时必须重新填写 API 密钥");
        }
        if (enabled) {
            if (normalizedEndpoint == null) {
                throw new IllegalArgumentException("A valid HTTPS endpoint is required");
            }
            if (normalizedModel == null) {
                throw new IllegalArgumentException("A model is required");
            }
            if (normalizedKey.isEmpty() && !hasKey(context)) {
                throw new IllegalArgumentException("An API key is required");
            }
        }

        SharedPreferences.Editor editor = preferences.edit().putBoolean(PREF_ENABLED, enabled);
        if (normalizedEndpoint != null) {
            editor.putString(PREF_ENDPOINT, normalizedEndpoint);
        }
        if (normalizedModel != null) {
            editor.putString(PREF_MODEL, normalizedModel);
        }
        if (!normalizedKey.isEmpty()) {
            editor.putString(PREF_ENCRYPTED_KEY, encrypt(normalizedKey));
        }
        if (!editor.commit()) {
            throw new IllegalStateException("Could not save AI settings");
        }
    }

    public static String key(Context context) throws Exception {
        String encrypted = preferences(context).getString(PREF_ENCRYPTED_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) {
            throw new IllegalStateException("No API key is configured");
        }
        try {
            byte[] packed = Base64.decode(encrypted, Base64.NO_WRAP);
            int ivLength = 12;
            if (packed.length <= ivLength) {
                throw new IllegalStateException("Invalid encrypted API key");
            }
            byte[] iv = Arrays.copyOfRange(packed, 0, ivLength);
            byte[] ciphertext = Arrays.copyOfRange(packed, ivLength, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt the stored API key");
        }
    }

    public static void clear(Context context) {
        if (!preferences(context).edit().clear().commit()) {
            throw new IllegalStateException("Could not clear AI settings");
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not clear the stored encryption key");
        }
    }

    private static SharedPreferences preferences(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String normalizeEndpoint(String baseUrl) throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint must not be empty");
        }
        String value = baseUrl.trim();
        if (value.length() > 2048) {
            throw new IllegalArgumentException("Endpoint is too long");
        }
        URI uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Endpoint must be an HTTPS URL without credentials, query, or fragment");
        }
        String normalized = uri.toASCIIString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.endsWith("/chat/completions")) {
            normalized += "/chat/completions";
        }
        return normalized;
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] packed = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, packed, 0, iv.length);
        System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
        return Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    private static SecretKey getOrCreateEncryptionKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((SecretKey) keyStore.getKey(KEY_ALIAS, null));
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
