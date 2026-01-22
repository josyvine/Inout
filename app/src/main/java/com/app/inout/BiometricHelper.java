package com.inout.app.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * Helper class for Biometric Authentication (Fingerprint / Face).
 * 
 * CRITICAL LOGIC:
 * 1. Calls Android OS BiometricPrompt.
 * 2. OS handles the scanning and matching.
 * 3. App receives ONLY a Boolean result (Success/Fail).
 * 
 * The app NEVER accesses the actual fingerprint data/images.
 */
public class BiometricHelper {

    private static final String TAG = "BiometricHelper";

    public interface BiometricCallback {
        void onAuthenticationSuccess();
        void onAuthenticationError(String errorMsg);
        void onAuthenticationFailed();
    }

    /**
     * Checks if the device has biometric hardware and if the user has enrolled.
     */
    public static boolean isBiometricAvailable(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                Log.d(TAG, "App can authenticate with biometrics.");
                return true;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Log.e(TAG, "No biometric features available on this device.");
                return false;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Log.e(TAG, "Biometric features are currently unavailable.");
                return false;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Log.e(TAG, "The user has not enrolled any biometrics.");
                return false;
            default:
                return false;
        }
    }

    /**
     * Triggers the system Biometric Prompt.
     *
     * @param activity The calling activity (must be FragmentActivity).
     * @param callback The interface to receive the result.
     */
    public static void authenticate(FragmentActivity activity, final BiometricCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.e(TAG, "Authentication error: " + errString);
                // Error = canceled by user, too many attempts, or hardware error
                callback.onAuthenticationError(errString.toString());
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Log.d(TAG, "Authentication succeeded!");
                // OS verified the user. We trust this signal.
                callback.onAuthenticationSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.w(TAG, "Authentication failed (Fingerprint not recognized).");
                // Failed = Fingerprint scanned but did not match any enrolled finger
                callback.onAuthenticationFailed();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Identity")
                .setSubtitle("Scan your fingerprint to check in/out")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}