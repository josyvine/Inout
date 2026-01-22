package com.inout.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles security operations:
 * 1. Secure local storage of Firebase Config and User Roles.
 * 2. AES Encryption/Decryption for QR Code payloads.
 */
public class EncryptionHelper {

    private static final String TAG = "EncryptionHelper";
    private static final String PREFS_FILENAME = "secure_app_prefs";
    
    // Keys for SharedPreferences
    private static final String KEY_USER_ROLE = "key_user_role"; // "admin" or "employee"
    private static final String KEY_FIREBASE_CONFIG = "key_firebase_config";
    private static final String KEY_COMPANY_NAME = "key_company_name";
    private static final String KEY_PROJECT_ID = "key_project_id";
    private static final String KEY_IS_SETUP_DONE = "key_is_setup_done";

    // Hardcoded Key for QR Code Encryption (Shared between Admin & Employee logic)
    private static final String QR_ENCRYPTION_KEY = "InOutAppSuperSecretKey2026";
    private static final String AES_ALGORITHM = "AES";

    private final SharedPreferences sharedPreferences;
    private static EncryptionHelper instance;

    private EncryptionHelper(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            sharedPreferences = EncryptedSharedPreferences.create(
                    PREFS_FILENAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new RuntimeException("Failed to initialize encrypted storage", e);
        }
    }

    public static synchronized EncryptionHelper getInstance(Context context) {
        if (instance == null) {
            instance = new EncryptionHelper(context.getApplicationContext());
        }
        return instance;
    }

    public void saveUserRole(String role) {
        sharedPreferences.edit().putString(KEY_USER_ROLE, role).apply();
    }

    public String getUserRole() {
        return sharedPreferences.getString(KEY_USER_ROLE, null);
    }

    public void saveFirebaseConfig(String jsonConfig, String companyName, String projectId) {
        sharedPreferences.edit()
                .putString(KEY_FIREBASE_CONFIG, jsonConfig)
                .putString(KEY_COMPANY_NAME, companyName)
                .putString(KEY_PROJECT_ID, projectId)
                .putBoolean(KEY_IS_SETUP_DONE, true)
                .apply();
    }

    public String getFirebaseConfig() {
        return sharedPreferences.getString(KEY_FIREBASE_CONFIG, null);
    }

    public String getCompanyName() {
        return sharedPreferences.getString(KEY_COMPANY_NAME, "Unknown Company");
    }
    
    public String getProjectId() {
        return sharedPreferences.getString(KEY_PROJECT_ID, null);
    }

    public boolean isSetupDone() {
        return sharedPreferences.getBoolean(KEY_IS_SETUP_DONE, false);
    }
    
    public void clearAllData() {
        sharedPreferences.edit().clear().apply();
    }

    private SecretKeySpec generateKey() throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = QR_ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();
        return new SecretKeySpec(key, AES_ALGORITHM);
    }

    public String encryptQrPayload(String plainText) {
        try {
            SecretKeySpec key = generateKey();
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encVal = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encVal, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "QR Encryption failed", e);
            return null;
        }
    }

    public String decryptQrPayload(String encryptedText) {
        try {
            SecretKeySpec key = generateKey();
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedValue = Base64.decode(encryptedText, Base64.NO_WRAP);
            byte[] decValue = cipher.doFinal(decodedValue);
            return new String(decValue, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "QR Decryption failed", e);
            return null;
        }
    }

    /**
     * Extracts the Web Client ID (Type 3) from the saved Firebase JSON.
     * This is required for Google Sign-In to work dynamically.
     */
    public String getWebClientId() {
        String jsonConfig = getFirebaseConfig();
        if (jsonConfig == null) return null;

        try {
            JSONObject root = new JSONObject(jsonConfig);
            JSONArray clientArray = root.getJSONArray("client");
            if (clientArray.length() > 0) {
                JSONObject client = clientArray.getJSONObject(0);
                JSONArray oauthClientArray = client.getJSONArray("oauth_client");

                for (int i = 0; i < oauthClientArray.length(); i++) {
                    JSONObject oauthClient = oauthClientArray.getJSONObject(i);
                    int clientType = oauthClient.getInt("client_type");
                    // Type 3 is the Web Client ID required for requestIdToken
                    if (clientType == 3) {
                        return oauthClient.getString("client_id");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting Web Client ID", e);
        }
        return null; // Return null if not found
    }
}